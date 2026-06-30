package com.opencamera.app.camera

import android.graphics.Bitmap
import android.os.SystemClock
import com.opencamera.core.media.AlgorithmJobClass
import com.opencamera.core.media.ContentSavedPhotoAdaptationProfile
import com.opencamera.core.media.ContentSubjectHint
import com.opencamera.core.media.ContentUnderstandingCandidateLabels
import com.opencamera.core.media.ContentUnderstandingFamilyMetadataLabels
import com.opencamera.core.media.ContentUnderstandingPipelineNotes
import com.opencamera.core.media.ContentUnderstandingSnapshot
import com.opencamera.core.media.MediaPostProcessor
import com.opencamera.core.media.ProcessorTarget
import com.opencamera.core.media.ShotResult
import com.opencamera.core.media.addPipelineNotes
import com.opencamera.core.media.toProcessorTargetOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class ContentUnderstandingPostProcessor(
    private val analyzer: SavedPhotoContentAnalyzer,
    private val bitmapSource: SavedPhotoBitmapSource,
    private val elapsedRealtimeMillis: () -> Long = { SystemClock.elapsedRealtime() }
) : MediaPostProcessor {
    override fun jobClass(result: ShotResult): AlgorithmJobClass =
        AlgorithmJobClass.CAPTURE_OPTIONAL

    override suspend fun process(result: ShotResult): ShotResult {
        when (result.photoJpegInput()) {
            PhotoJpegInput.NOT_PHOTO -> return result
            PhotoJpegInput.UNSUPPORTED_MIME -> return result.addPipelineNotes(
                "content-understanding:skipped:unsupported-mime"
            )
            PhotoJpegInput.EDITABLE -> Unit
        }
        if (result.contentUnderstanding != null) {
            return result.addPipelineNotes("content-understanding:skipped:already-present")
        }
        val target = result.outputHandle.toProcessorTargetOrNull()
            ?: return result.addPipelineNotes("content-understanding:skipped:missing-output-handle")
        val bitmap = withContext(Dispatchers.Default) {
            bitmapSource.decode(target)
        } ?: return result.addPipelineNotes("content-understanding:skipped:bitmap-unavailable")

        return try {
            val snapshot = analyzer.analyze(
                bitmap = bitmap,
                request = SavedPhotoContentAnalysisRequest(
                    shotId = result.shotId,
                    outputHandleTag = result.outputHandle.displayPath,
                    timestampMillis = elapsedRealtimeMillis()
                )
            )
            result.copy(
                contentUnderstanding = snapshot,
                metadata = result.metadata.copy(
                    customTags = result.metadata.customTags + snapshot.toMetadataTags()
                )
            )
                .addPipelineNotes(*snapshot.toPipelineNotes().toTypedArray())
        } catch (throwable: Throwable) {
            throwable.rethrowIfCancellationOrFatal()
            result.addPipelineNotes(
                "content-understanding:failed:${throwable::class.java.simpleName}"
            )
        }
    }
}

internal interface SavedPhotoBitmapSource {
    suspend fun decode(target: ProcessorTarget): Bitmap?
}

private fun ContentUnderstandingSnapshot.toPipelineNotes(): List<String> {
    val notes = buildList {
        add(ContentUnderstandingPipelineNotes.availability(isAvailable))
        add("content-understanding:quality=${quality.name.lowercase()}")
        addAll(ContentUnderstandingPipelineNotes.snapshotCapabilityNotes(this@toPipelineNotes))
        if (regions.isNotEmpty()) {
            add("content-understanding:regions=${regions.size}")
        }
        if (tags.isNotEmpty()) {
            add("content-understanding:tags=${tags.size}")
        }
        addAll(ContentUnderstandingPipelineNotes.semanticSummaryNotes(this@toPipelineNotes))
        addAll(ContentUnderstandingPipelineNotes.savedPhotoAdaptationDecisionNotes(this@toPipelineNotes))
    }
    val sanitizedDiagnostics = diagnostics
        .filterNot { isAvailable && it == ContentUnderstandingPipelineNotes.unavailable() }
        .filterNot { it.startsWith("content-understanding:reason=") && notes.contains(it) }
    return (notes + sanitizedDiagnostics).distinct()
}

private fun ContentUnderstandingSnapshot.toMetadataTags(): Map<String, String> = buildMap {
    put("contentQuality", quality.name.lowercase())
    put("contentBackend", backendId)
    put("contentAvailable", isAvailable.toString())
    val capability = capabilitySummary()
    putAll(ContentUnderstandingFamilyMetadataLabels.supportTags(capability))
    capability.reason?.let { put("contentCapabilityReason", it) }
    primarySceneHint()?.let { scene ->
        put("contentPrimaryScene", ContentUnderstandingCandidateLabels.sceneHint(scene))
        put("contentPrimarySceneSource", ContentUnderstandingCandidateLabels.sceneSource(scene))
        put("contentPrimarySceneConfidence", ContentUnderstandingCandidateLabels.sceneConfidence(scene))
    }
    primarySubjectHint(
        ContentSubjectHint.FACE,
        ContentSubjectHint.PERSON,
        ContentSubjectHint.FOREGROUND
    )?.let { people ->
        put("contentPrimaryPeople", ContentUnderstandingCandidateLabels.subjectHint(people))
        put("contentPrimaryPeopleSource", ContentUnderstandingCandidateLabels.subjectSource(people))
        put("contentPrimaryPeopleConfidence", ContentUnderstandingCandidateLabels.subjectConfidence(people))
    }
    primarySubjectHint(ContentSubjectHint.OBJECT)?.let { obj ->
        put("contentPrimaryObject", ContentUnderstandingCandidateLabels.subjectHint(obj))
        put("contentPrimaryObjectSource", ContentUnderstandingCandidateLabels.subjectSource(obj))
        put("contentPrimaryObjectConfidence", ContentUnderstandingCandidateLabels.subjectConfidence(obj))
    }
    val semanticRegionCandidates = semanticRegionCandidates(ContentSavedPhotoAdaptationProfile.DEFAULT)
    if (semanticRegionCandidates.isNotEmpty()) {
        put(
            "contentSemanticRegionCandidates",
            ContentUnderstandingCandidateLabels.semanticRegions(semanticRegionCandidates)
        )
        ContentUnderstandingCandidateLabels.semanticRegionBounds(semanticRegionCandidates)
            .takeIf { it.isNotBlank() }
            ?.let { put("contentSemanticRegionBounds", it) }
    }
    val placementAvoidanceCandidates = placementAvoidanceCandidates(ContentSavedPhotoAdaptationProfile.DEFAULT)
    if (placementAvoidanceCandidates.isNotEmpty()) {
        put(
            "contentPlacementAvoidance",
            ContentUnderstandingCandidateLabels.placementAvoidance(placementAvoidanceCandidates)
        )
    }
    val adaptationReady = isReadyForSavedPhotoAdaptation()
    put("contentAdaptationReady", adaptationReady.toString())
    if (!adaptationReady) {
        put("contentAdaptationNotReadyQuality", quality.name.lowercase())
        capability.reason?.let { put("contentAdaptationNotReadyReason", it) }
    }
    val decisions = savedPhotoAdaptationDecisions()
    decisions.styleScene?.let { scene ->
        put("contentAdaptationScene", ContentUnderstandingCandidateLabels.sceneHint(scene))
        put("contentAdaptationSceneSource", ContentUnderstandingCandidateLabels.sceneSource(scene))
        put("contentAdaptationSceneConfidence", ContentUnderstandingCandidateLabels.sceneConfidence(scene))
    }
    if (decisions.styleSceneCandidates.isNotEmpty()) {
        put(
            "contentAdaptationSceneCandidates",
            ContentUnderstandingCandidateLabels.sceneCandidates(decisions.styleSceneCandidates)
        )
    }
    decisions.portraitSubject?.let { subject ->
        put("contentAdaptationPortraitSubject", ContentUnderstandingCandidateLabels.subjectHint(subject))
        put("contentAdaptationPortraitSubjectSource", ContentUnderstandingCandidateLabels.subjectSource(subject))
        put("contentAdaptationPortraitSubjectConfidence", ContentUnderstandingCandidateLabels.subjectConfidence(subject))
    }
    if (decisions.portraitSubjectCandidates.isNotEmpty()) {
        put(
            "contentAdaptationPortraitSubjectCandidates",
            ContentUnderstandingCandidateLabels.subjectCandidates(decisions.portraitSubjectCandidates)
        )
    }
    decisions.checkInScenario?.let { checkIn ->
        put("contentAdaptationCheckInScenario", ContentUnderstandingCandidateLabels.checkInScenario(checkIn))
        put("contentAdaptationCheckInSource", ContentUnderstandingCandidateLabels.checkInSource(checkIn))
        put("contentAdaptationCheckInConfidence", ContentUnderstandingCandidateLabels.checkInConfidence(checkIn))
    }
    if (decisions.checkInScenarioCandidates.isNotEmpty()) {
        put(
            "contentAdaptationCheckInCandidates",
            ContentUnderstandingCandidateLabels.checkInCandidates(decisions.checkInScenarioCandidates)
        )
    }
}
