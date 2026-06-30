package com.opencamera.app.camera

import android.graphics.Bitmap
import com.opencamera.core.media.ContentUnderstandingFamily
import com.opencamera.core.media.ContentUnderstandingPipelineNotes
import com.opencamera.core.media.ContentUnderstandingSnapshot
import com.opencamera.core.media.SceneMaskQuality
import com.opencamera.core.media.SceneMaskSupport

internal object MlKitSavedPhotoContentFamilies {
    val ALL: List<ContentUnderstandingFamily> = listOf(
        ContentUnderstandingFamily.SCENE_TAGS,
        ContentUnderstandingFamily.OBJECT_TAGS,
        ContentUnderstandingFamily.FACE_LANDMARKS
    )
}

internal interface MlKitImageLabelClient {
    suspend fun detect(bitmap: Bitmap): List<RawMlKitImageLabel>
}

internal interface MlKitObjectDetectionClient {
    suspend fun detect(bitmap: Bitmap): List<RawMlKitDetectedObject>
}

internal interface MlKitFaceDetectionClient {
    suspend fun detect(bitmap: Bitmap): List<RawMlKitFace>
}

internal class MlKitSavedPhotoContentAnalyzer(
    private val imageLabelClient: MlKitImageLabelClient,
    private val objectClient: MlKitObjectDetectionClient,
    private val faceClient: MlKitFaceDetectionClient
) : SavedPhotoContentAnalyzer {
    override val backendId: String = "mlkit-content"

    override val contentFamilies: Set<ContentUnderstandingFamily> =
        MlKitSavedPhotoContentFamilies.ALL.toSet()

    override suspend fun analyze(
        bitmap: Bitmap,
        request: SavedPhotoContentAnalysisRequest
    ): ContentUnderstandingSnapshot {
        val failures = mutableListOf<String>()
        val failedFamilies = mutableListOf<ContentUnderstandingFamily>()
        val labels = runFamily(ContentUnderstandingFamily.SCENE_TAGS, failures, failedFamilies) {
            imageLabelClient.detect(bitmap)
        }
        val objects = runFamily(ContentUnderstandingFamily.OBJECT_TAGS, failures, failedFamilies) {
            objectClient.detect(bitmap)
        }
        val faces = runFamily(ContentUnderstandingFamily.FACE_LANDMARKS, failures, failedFamilies) {
            faceClient.detect(bitmap)
        }

        val mapped = MlKitSavedPhotoContentMapper.map(
            request = request,
            sourceWidth = bitmap.width,
            sourceHeight = bitmap.height,
            imageLabels = labels,
            objects = objects,
            faces = faces
        )
        if (failures.isEmpty()) return mapped
        return mapped.copy(
            quality = if (mapped.isAvailable) SceneMaskQuality.DEGRADED else mapped.quality,
            diagnostics = (
                mapped.diagnostics +
                    failedFamilies.map {
                        ContentUnderstandingPipelineNotes.familySupport(it, SceneMaskSupport.DEGRADED)
                    } +
                    ContentUnderstandingPipelineNotes.partialFailureReason(
                        prefix = MLKIT_CONTENT_PARTIAL_FAILURE_REASON,
                        failedFamilies = failedFamilies
                    ) +
                    failures
                ).distinct()
        )
    }

    private suspend fun <T> runFamily(
        family: ContentUnderstandingFamily,
        failures: MutableList<String>,
        failedFamilies: MutableList<ContentUnderstandingFamily>,
        block: suspend () -> List<T>
    ): List<T> {
        return try {
            block()
        } catch (e: Exception) {
            failedFamilies += family
            failures += "mlkit-content:${family.key}=failed:${e::class.java.simpleName}"
            emptyList()
        }
    }
    private companion object {
        const val MLKIT_CONTENT_PARTIAL_FAILURE_REASON = "mlkit-content-partial-failure"
    }
}
