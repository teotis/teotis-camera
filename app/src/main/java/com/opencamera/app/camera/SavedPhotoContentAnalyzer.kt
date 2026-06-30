package com.opencamera.app.camera

import android.graphics.Bitmap
import com.opencamera.core.media.ContentUnderstandingFamily
import com.opencamera.core.media.ContentUnderstandingPipelineNotes
import com.opencamera.core.media.ContentUnderstandingSnapshot
import com.opencamera.core.media.SceneMaskQuality
import com.opencamera.core.media.SceneMaskSupport

internal data class SavedPhotoContentAnalysisRequest(
    val shotId: String,
    val outputHandleTag: String,
    val timestampMillis: Long
)

internal interface SavedPhotoContentBackend {
    val backendId: String
        get() = this::class.java.simpleName.ifBlank { "saved-photo-content-backend" }

    val contentFamilies: Set<ContentUnderstandingFamily>
        get() = emptySet()

    val contentFamilyKeys: Set<String>
        get() = contentFamilies.mapTo(linkedSetOf()) { it.key }

    suspend fun analyze(
        bitmap: Bitmap,
        request: SavedPhotoContentAnalysisRequest
    ): ContentUnderstandingSnapshot
}

internal interface SavedPhotoContentAnalyzer : SavedPhotoContentBackend

internal class CompositeSavedPhotoContentAnalyzer(
    private val backends: List<SavedPhotoContentBackend>
) : SavedPhotoContentAnalyzer {
    override suspend fun analyze(
        bitmap: Bitmap,
        request: SavedPhotoContentAnalysisRequest
    ): ContentUnderstandingSnapshot {
        val snapshots = backends.map { backend ->
            try {
                backend.analyze(bitmap, request)
            } catch (e: Exception) {
                val base = ContentUnderstandingSnapshot.unavailable(
                    timestampMillis = request.timestampMillis,
                    backendId = backend.backendId,
                    reason = "backend-exception:${e::class.java.simpleName}"
                )
                base.copy(
                    diagnostics = (
                        base.diagnostics +
                            ContentUnderstandingPipelineNotes.backend(backend.backendId) +
                            backend.contentFamilies.map {
                                ContentUnderstandingPipelineNotes.familySupport(it, SceneMaskSupport.DEGRADED)
                            }
                        ).distinct()
                )
            }
        }
        return ContentUnderstandingSnapshot.combine(
            snapshotId = "content-${request.shotId}",
            timestampMillis = request.timestampMillis,
            backendId = "saved-photo-composite",
            snapshots = snapshots
        )
    }
}

internal class SceneMaskSavedPhotoContentAnalyzer(
    private val provider: SavedPhotoSceneMaskProvider
) : SavedPhotoContentBackend {
    override val backendId: String = "mlkit-selfie"
    override val contentFamilies: Set<ContentUnderstandingFamily> = setOf(ContentUnderstandingFamily.SUBJECT)

    override suspend fun analyze(
        bitmap: Bitmap,
        request: SavedPhotoContentAnalysisRequest
    ): ContentUnderstandingSnapshot {
        return when (val result = provider.createSubjectMask(
            bitmap = bitmap,
            request = SavedPhotoSceneMaskRequest(
                shotId = request.shotId,
                outputHandleTag = request.outputHandleTag
            )
        )) {
            is SceneMaskResult.Available -> result.mask.toContentUnderstandingSnapshot(
                maskId = "${request.shotId}-subject",
                sourceWidth = bitmap.width,
                sourceHeight = bitmap.height,
                timestampMillis = request.timestampMillis
            )

            is SceneMaskResult.Unavailable -> ContentUnderstandingSnapshot.unavailable(
                timestampMillis = request.timestampMillis,
                backendId = "mlkit-selfie",
                reason = result.reason
            )

            is SceneMaskResult.Failed -> ContentUnderstandingSnapshot(
                snapshotId = "content-${request.shotId}-subject-failed",
                timestampMillis = request.timestampMillis,
                quality = SceneMaskQuality.DEGRADED,
                backendId = "mlkit-selfie",
                regions = emptyList(),
                diagnostics = listOf(
                    ContentUnderstandingPipelineNotes.unavailable(),
                    ContentUnderstandingPipelineNotes.familySupport(
                        ContentUnderstandingFamily.SUBJECT,
                        SceneMaskSupport.DEGRADED
                    ),
                    ContentUnderstandingPipelineNotes.reason(result.reason)
                )
            )
        }
    }
}

internal class NoOpSavedPhotoContentAnalyzer : SavedPhotoContentAnalyzer {
    override val backendId: String = "none"

    override suspend fun analyze(
        bitmap: Bitmap,
        request: SavedPhotoContentAnalysisRequest
    ): ContentUnderstandingSnapshot = ContentUnderstandingSnapshot.unavailable(
        timestampMillis = request.timestampMillis,
        backendId = "none",
        reason = "no-op-provider"
    )
}
