package com.opencamera.app.camera

import com.opencamera.core.media.ContentRegionBounds
import com.opencamera.core.media.ContentRegionDescriptor
import com.opencamera.core.media.ContentRegionRole
import com.opencamera.core.media.ContentRecognitionLabelClassifier
import com.opencamera.core.media.ContentTagDescriptor
import com.opencamera.core.media.ContentTagFamily
import com.opencamera.core.media.ContentUnderstandingPipelineNotes
import com.opencamera.core.media.ContentUnderstandingSnapshot
import com.opencamera.core.media.SceneMaskQuality
import com.opencamera.core.media.SceneMaskSupport
import com.opencamera.core.media.SceneMaskTransform

internal data class RawMlKitImageLabel(
    val label: String,
    val confidence: Float,
    val index: Int
)

internal data class RawMlKitObjectLabel(
    val label: String,
    val confidence: Float,
    val index: Int
)

internal data class RawMlKitBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
)

internal data class RawMlKitDetectedObject(
    val objectIndex: Int,
    val bounds: RawMlKitBounds,
    val labels: List<RawMlKitObjectLabel>
)

internal data class RawMlKitFace(
    val faceIndex: Int,
    val bounds: RawMlKitBounds,
    val smilingProbability: Float?,
    val leftEyeOpenProbability: Float?,
    val rightEyeOpenProbability: Float?
)

internal object MlKitSavedPhotoContentMapper {
    private const val BACKEND_ID = "mlkit-content"

    fun map(
        request: SavedPhotoContentAnalysisRequest,
        sourceWidth: Int,
        sourceHeight: Int,
        imageLabels: List<RawMlKitImageLabel>,
        objects: List<RawMlKitDetectedObject>,
        faces: List<RawMlKitFace>
    ): ContentUnderstandingSnapshot {
        if (imageLabels.isEmpty() && objects.isEmpty() && faces.isEmpty()) {
            val emptySnapshot = ContentUnderstandingSnapshot.unavailable(
                timestampMillis = request.timestampMillis,
                backendId = BACKEND_ID,
                reason = "mlkit-content-empty"
            )
            return emptySnapshot.copy(
                diagnostics = emptySnapshot.diagnostics + successfulFamilySupportDiagnostics()
            )
        }

        val transform = SceneMaskTransform(
            sourceWidth = sourceWidth,
            sourceHeight = sourceHeight,
            maskWidth = sourceWidth,
            maskHeight = sourceHeight,
            rotationDegrees = 0
        )
        val rankedImageLabels = imageLabels.rankedByConfidence(
            limit = MAX_IMAGE_LABELS,
            confidence = { confidence },
            tieBreaker = { index }
        )
        val rankedObjects = objects.rankedByConfidence(
            limit = MAX_OBJECTS,
            confidence = { regionConfidence() },
            tieBreaker = { objectIndex }
        )
        val rankedFaces = faces.rankedByConfidence(
            limit = MAX_FACES,
            confidence = { regionConfidence() },
            tieBreaker = { faceIndex }
        )

        val objectRegions = rankedObjects.items.map { detected ->
            ContentRegionDescriptor(
                regionId = "object-${detected.objectIndex}",
                role = ContentRegionRole.OBJECT,
                quality = SceneMaskQuality.SAVED_PHOTO,
                backendId = BACKEND_ID,
                confidence = detected.regionConfidence(),
                transform = transform,
                bounds = detected.bounds.toContentBounds(sourceWidth, sourceHeight)
            )
        }
        val faceRegions = rankedFaces.items.map { face ->
            ContentRegionDescriptor(
                regionId = "face-${face.faceIndex}",
                role = ContentRegionRole.FACE,
                quality = SceneMaskQuality.SAVED_PHOTO,
                backendId = BACKEND_ID,
                confidence = face.regionConfidence(),
                transform = transform,
                bounds = face.bounds.toContentBounds(sourceWidth, sourceHeight)
            )
        }
        val sceneTags = rankedImageLabels.items.map { label ->
            ContentTagDescriptor(
                tagId = "scene-${label.index}-${label.label.normalizedTagSuffix()}",
                label = label.label,
                family = ContentTagFamily.SCENE,
                confidence = label.confidence.coerceIn(0f, 1f),
                backendId = BACKEND_ID
            )
        }
        var droppedObjectLabels = 0
        val objectTags = rankedObjects.items.flatMap { detected ->
            val rankedLabels = detected.labels.rankedByConfidence(
                limit = MAX_OBJECT_LABELS_PER_OBJECT,
                confidence = { confidence },
                tieBreaker = { index }
            )
            droppedObjectLabels += rankedLabels.dropped
            rankedLabels.items.map { label ->
                ContentTagDescriptor(
                    tagId = "object-${detected.objectIndex}-${label.index}-${label.label.normalizedTagSuffix()}",
                    label = label.label,
                    family = ContentTagFamily.OBJECT,
                    confidence = label.confidence.coerceIn(0f, 1f),
                    backendId = BACKEND_ID,
                    sourceRegionId = "object-${detected.objectIndex}"
                )
            }
        }
        val semanticRegions = rankedObjects.items.mapNotNull { detected ->
            val semanticLabel = detected.labels.rankedByConfidence(
                limit = MAX_OBJECT_LABELS_PER_OBJECT,
                confidence = { confidence },
                tieBreaker = { index }
            ).items.firstNotNullOfOrNull { label ->
                label.toSemanticRegionRole()?.let { role -> label to role }
            }
            semanticLabel?.let { (label, role) ->
                ContentRegionDescriptor(
                    regionId = "semantic-object-${detected.objectIndex}-${role.toRegionIdSuffix()}",
                    role = role,
                    quality = SceneMaskQuality.SAVED_PHOTO,
                    backendId = BACKEND_ID,
                    confidence = label.confidence.coerceIn(0f, 1f),
                    transform = transform,
                    bounds = detected.bounds.toContentBounds(sourceWidth, sourceHeight)
                )
            }
        }
        val faceTags = rankedFaces.items.flatMap { face ->
            listOfNotNull(
                face.smilingProbability?.toFaceAttributeTag(face.faceIndex, "smiling"),
                face.leftEyeOpenProbability?.toFaceAttributeTag(face.faceIndex, "left-eye-open"),
                face.rightEyeOpenProbability?.toFaceAttributeTag(face.faceIndex, "right-eye-open")
            )
        }

        return ContentUnderstandingSnapshot(
            snapshotId = "content-${request.shotId}-mlkit",
            timestampMillis = request.timestampMillis,
            quality = SceneMaskQuality.SAVED_PHOTO,
            backendId = BACKEND_ID,
            regions = objectRegions + semanticRegions + faceRegions,
            tags = sceneTags + objectTags + faceTags,
            diagnostics = listOfNotNull(
                *successfulFamilySupportDiagnostics().toTypedArray(),
                "mlkit-content:image-labels=${imageLabels.size}",
                "mlkit-content:objects=${objects.size}",
                "mlkit-content:faces=${faces.size}",
                "mlkit-content:semantic-regions=${semanticRegions.size}",
                rankedImageLabels.dropped.toDroppedDiagnostic("image-labels"),
                rankedObjects.dropped.toDroppedDiagnostic("objects"),
                rankedFaces.dropped.toDroppedDiagnostic("faces"),
                droppedObjectLabels.toDroppedDiagnostic("object-labels")
            )
        )
    }

    private data class RankedItems<T>(
        val items: List<T>,
        val dropped: Int
    )

    private fun <T> List<T>.rankedByConfidence(
        limit: Int,
        confidence: T.() -> Float,
        tieBreaker: T.() -> Int
    ): RankedItems<T> {
        val ranked = sortedWith(
            compareByDescending<T> { it.confidence() }
                .thenBy { it.tieBreaker() }
        )
        return RankedItems(
            items = ranked.take(limit),
            dropped = (size - limit).coerceAtLeast(0)
        )
    }

    private fun Int.toDroppedDiagnostic(family: String): String? =
        takeIf { it > 0 }?.let { "mlkit-content:$family-dropped=$it" }

    private fun successfulFamilySupportDiagnostics(): List<String> =
        MlKitSavedPhotoContentFamilies.ALL.map { family ->
            ContentUnderstandingPipelineNotes.familySupport(family, SceneMaskSupport.SUPPORTED)
        }

    private fun RawMlKitBounds.toContentBounds(sourceWidth: Int, sourceHeight: Int): ContentRegionBounds =
        ContentRegionBounds(
            left = (left.toFloat() / sourceWidth).coerceIn(0f, 1f),
            top = (top.toFloat() / sourceHeight).coerceIn(0f, 1f),
            right = (right.toFloat() / sourceWidth).coerceIn(0f, 1f),
            bottom = (bottom.toFloat() / sourceHeight).coerceIn(0f, 1f)
        )

    private fun Float.toFaceAttributeTag(faceIndex: Int, label: String): ContentTagDescriptor =
        ContentTagDescriptor(
            tagId = "face-$faceIndex-$label",
            label = label,
            family = ContentTagFamily.FACE_ATTRIBUTE,
            confidence = coerceIn(0f, 1f),
            backendId = BACKEND_ID,
            sourceRegionId = "face-$faceIndex"
        )

    private fun RawMlKitObjectLabel.toSemanticRegionRole(): ContentRegionRole? =
        ContentRecognitionLabelClassifier.semanticRegionRole(label = label)

    private fun ContentRegionRole.toRegionIdSuffix(): String =
        name.lowercase().replace('_', '-')

    private fun RawMlKitFace.regionConfidence(): Float =
        maxOf(
            FACE_DETECTION_CONFIDENCE_FLOOR,
            listOfNotNull(
                smilingProbability,
                leftEyeOpenProbability,
                rightEyeOpenProbability
            ).maxOrNull() ?: 0f
        ).coerceIn(0f, 1f)

    private fun RawMlKitDetectedObject.regionConfidence(): Float =
        maxOf(
            OBJECT_DETECTION_CONFIDENCE_FLOOR,
            labels.maxOfOrNull { it.confidence } ?: 0f
        ).coerceIn(0f, 1f)

    private fun String.normalizedTagSuffix(): String =
        lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .ifBlank { "unknown" }

    private const val FACE_DETECTION_CONFIDENCE_FLOOR = 0.82f
    private const val OBJECT_DETECTION_CONFIDENCE_FLOOR = 0.76f
    private const val MAX_IMAGE_LABELS = 6
    private const val MAX_OBJECTS = 5
    private const val MAX_OBJECT_LABELS_PER_OBJECT = 2
    private const val MAX_FACES = 5
}
