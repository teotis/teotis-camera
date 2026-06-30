package com.opencamera.app.camera

import com.opencamera.core.media.ContentCheckInScenarioHint
import com.opencamera.core.media.ContentUnderstandingCandidateLabels
import com.opencamera.core.media.ContentUnderstandingConsumerFamilyLabels
import com.opencamera.core.media.ContentUnderstandingConsumerSkipLabels
import com.opencamera.core.media.ContentUnderstandingFamily
import com.opencamera.core.media.ContentPlacementAvoidanceCandidate
import com.opencamera.core.media.ContentRegionRole
import com.opencamera.core.media.ContentSavedPhotoAdaptationProfile
import com.opencamera.core.media.ContentUnderstandingSnapshot
import com.opencamera.core.media.MediaPostProcessor
import com.opencamera.core.media.MediaType
import com.opencamera.core.media.ShotResult
import com.opencamera.core.media.addPipelineNotes
import com.opencamera.core.settings.WatermarkTextPlacement

internal class CheckInContentDecisionPostProcessor : MediaPostProcessor {
    override fun isApplicable(result: ShotResult): Boolean =
        result.mediaType == MediaType.PHOTO

    override suspend fun process(result: ShotResult): ShotResult {
        if (result.mediaType != MediaType.PHOTO) return result
        val tags = result.metadata.customTags
        if (tags["mode"] != "check-in") return result

        val snapshot = result.contentUnderstanding
            ?: return result.copy(
                metadata = result.metadata.copy(
                    customTags = tags + mapOf(
                        "checkInContentSkipped" to ContentUnderstandingConsumerSkipLabels.MISSING_CONTENT
                    )
                )
            ).addPipelineNotes("checkin-content:skipped:${ContentUnderstandingConsumerSkipLabels.MISSING_CONTENT}")
        if (!snapshot.isReadyForSavedPhotoAdaptation()) {
            val capability = snapshot.capabilitySummary()
            return result.copy(
                metadata = result.metadata.copy(
                    customTags = tags + snapshot.toContentNotReadyMetadataTags()
                )
            ).addPipelineNotes(
                *buildList {
                    add("checkin-content:skipped:${ContentUnderstandingConsumerSkipLabels.NOT_READY}")
                    add("checkin-content:quality=${snapshot.quality.name.lowercase()}")
                    capability.reason?.let { add("checkin-content:reason=$it") }
                }.toTypedArray()
            )
        }
        val decisions = snapshot.savedPhotoAdaptationDecisions()
        val scenarioDecision = decisions.checkInScenario
            ?: return result.copy(
                metadata = result.metadata.copy(
                    customTags = tags + snapshot.toNoScenarioMetadataTags()
                )
            ).addPipelineNotes(*snapshot.toNoScenarioNotes().toTypedArray())
        val sceneDecision = decisions.styleScene
        val placementSuggestion = snapshot.checkInWatermarkPlacement(scenarioDecision.scenario)
        val watermarkDensity = scenarioDecision.scenario.watermarkDensity()

        val currentScenario = tags["checkInScenario"]
        val decisionTags = buildMap {
            val scenarioId = ContentUnderstandingCandidateLabels.checkInScenario(scenarioDecision)
            put("checkInContentScenario", scenarioId)
            put("checkInContentSource", ContentUnderstandingCandidateLabels.checkInSource(scenarioDecision))
            put("checkInContentConfidence", ContentUnderstandingCandidateLabels.checkInConfidence(scenarioDecision))
            put("checkInContentWatermarkDensity", watermarkDensity)
            currentScenario?.let { put("checkInOriginalScenario", it) }
            sceneDecision?.let { scene ->
                put("checkInContentScene", ContentUnderstandingCandidateLabels.sceneHint(scene))
                put("checkInContentSceneSource", ContentUnderstandingCandidateLabels.sceneSource(scene))
                put("checkInContentSceneConfidence", ContentUnderstandingCandidateLabels.sceneConfidence(scene))
            }
            placementSuggestion?.let { suggestion ->
                val candidate = suggestion.candidate
                put("checkInContentWatermarkPlacement", suggestion.placement.storageKey)
                put("checkInContentPlacementSource", ContentUnderstandingCandidateLabels.placementSource(candidate))
                put("checkInContentPlacementRole", ContentUnderstandingCandidateLabels.placementRole(candidate))
                put(
                    "checkInContentPlacementConfidence",
                    ContentUnderstandingCandidateLabels.placementConfidence(candidate)
                )
                put(
                    "checkInContentPlacementCenter",
                    ContentUnderstandingCandidateLabels.placementCenter(candidate)
                )
                put("checkInContentPlacementArea", ContentUnderstandingCandidateLabels.placementArea(candidate))
            }
        }
        val notes = buildList {
            val scenarioId = ContentUnderstandingCandidateLabels.checkInScenario(scenarioDecision)
            add("checkin-content:scenario=$scenarioId")
            add("checkin-content:source=${ContentUnderstandingCandidateLabels.checkInSource(scenarioDecision)}")
            add("checkin-content:confidence=${ContentUnderstandingCandidateLabels.checkInConfidence(scenarioDecision)}")
            add("checkin-content:watermark-density=$watermarkDensity")
            if (currentScenario != null && currentScenario != scenarioId) {
                add("checkin-content:suggested=$scenarioId:current=$currentScenario")
            }
            sceneDecision?.let { scene ->
                add("checkin-content:scene=${ContentUnderstandingCandidateLabels.sceneHint(scene)}")
                add("checkin-content:scene-source=${ContentUnderstandingCandidateLabels.sceneSource(scene)}")
                add("checkin-content:scene-confidence=${ContentUnderstandingCandidateLabels.sceneConfidence(scene)}")
            }
            placementSuggestion?.let { suggestion ->
                val candidate = suggestion.candidate
                add("checkin-content:watermark-placement=${suggestion.placement.storageKey}")
                add("checkin-content:placement-source=${ContentUnderstandingCandidateLabels.placementSource(candidate)}")
                add("checkin-content:placement-role=${ContentUnderstandingCandidateLabels.placementRole(candidate)}")
                add(
                    "checkin-content:placement-confidence=" +
                        ContentUnderstandingCandidateLabels.placementConfidence(candidate)
                )
                add("checkin-content:placement-center=${ContentUnderstandingCandidateLabels.placementCenter(candidate)}")
                add("checkin-content:placement-area=${ContentUnderstandingCandidateLabels.placementArea(candidate)}")
            }
        }
        return result.copy(
            metadata = result.metadata.copy(
                customTags = tags + decisionTags
            )
        ).addPipelineNotes(*notes.toTypedArray())
    }
}

private data class CheckInWatermarkPlacementSuggestion(
    val placement: WatermarkTextPlacement,
    val candidate: ContentPlacementAvoidanceCandidate
)

private fun ContentUnderstandingSnapshot.checkInWatermarkPlacement(
    scenario: ContentCheckInScenarioHint
): CheckInWatermarkPlacementSuggestion? {
    val candidate = placementAvoidanceCandidates(ContentSavedPhotoAdaptationProfile.DEFAULT)
        .firstOrNull { it.role in scenario.placementRoles() }
        ?: return null
    val centerX = ((candidate.bounds.left + candidate.bounds.right) / 2f).coerceIn(0f, 1f)
    val placement = when {
        centerX < LEFT_SUBJECT_WATERMARK_THRESHOLD -> WatermarkTextPlacement.BOTTOM_RIGHT
        centerX > RIGHT_SUBJECT_WATERMARK_THRESHOLD -> WatermarkTextPlacement.BOTTOM_LEFT
        else -> return null
    }
    return CheckInWatermarkPlacementSuggestion(placement, candidate)
}

private fun ContentCheckInScenarioHint.placementRoles(): Set<ContentRegionRole> = when (this) {
    ContentCheckInScenarioHint.PEOPLE_PLACE -> setOf(
        ContentRegionRole.FACE,
        ContentRegionRole.PERSON_SUBJECT,
        ContentRegionRole.FOREGROUND
    )

    ContentCheckInScenarioHint.OBJECT_PLACE -> setOf(
        ContentRegionRole.OBJECT,
        ContentRegionRole.FOOD,
        ContentRegionRole.BUILDING,
        ContentRegionRole.WATER,
        ContentRegionRole.VEGETATION
    )

    ContentCheckInScenarioHint.CLARITY -> setOf(ContentRegionRole.DOCUMENT)
}

private fun ContentCheckInScenarioHint.watermarkDensity(): String = when (this) {
    ContentCheckInScenarioHint.PEOPLE_PLACE -> "compact"
    ContentCheckInScenarioHint.OBJECT_PLACE -> "balanced"
    ContentCheckInScenarioHint.CLARITY -> "detailed"
}

private fun ContentUnderstandingSnapshot.toContentNotReadyMetadataTags(): Map<String, String> = buildMap {
    put("checkInContentSkipped", ContentUnderstandingConsumerSkipLabels.NOT_READY)
    put("checkInContentSkippedQuality", quality.name.lowercase())
    capabilitySummary().reason?.let { put("checkInContentSkippedReason", it) }
}

private fun ContentUnderstandingSnapshot.toNoScenarioNotes(): List<String> {
    val lowConfidenceScenario = savedPhotoAdaptationDecisions(
        ContentSavedPhotoAdaptationProfile.LOW_CONFIDENCE_REVIEW
    ).checkInScenario
    if (lowConfidenceScenario != null) {
        return listOf(
            "checkin-content:skipped:${ContentUnderstandingConsumerSkipLabels.LOW_CONFIDENCE_SCENARIO}",
            "checkin-content:source=${ContentUnderstandingCandidateLabels.checkInSource(lowConfidenceScenario)}",
            "checkin-content:confidence=${ContentUnderstandingCandidateLabels.checkInConfidence(lowConfidenceScenario)}"
        )
    }
    val capability = capabilitySummary()
    return listOf("checkin-content:skipped:${ContentUnderstandingConsumerSkipLabels.NO_SCENARIO}") +
        ContentUnderstandingConsumerFamilyLabels.supportNotes(
            prefix = "checkin-content",
            capability = capability,
            families = listOf(ContentUnderstandingFamily.SUBJECT, ContentUnderstandingFamily.OBJECT_TAGS)
        )
}

private fun ContentUnderstandingSnapshot.toNoScenarioMetadataTags(): Map<String, String> {
    val lowConfidenceScenario = savedPhotoAdaptationDecisions(
        ContentSavedPhotoAdaptationProfile.LOW_CONFIDENCE_REVIEW
    ).checkInScenario
    if (lowConfidenceScenario != null) {
        return mapOf(
            "checkInContentSkipped" to ContentUnderstandingConsumerSkipLabels.LOW_CONFIDENCE_SCENARIO,
            "checkInContentSkippedScenario" to ContentUnderstandingCandidateLabels.checkInScenario(lowConfidenceScenario),
            "checkInContentSkippedSource" to ContentUnderstandingCandidateLabels.checkInSource(lowConfidenceScenario),
            "checkInContentSkippedConfidence" to ContentUnderstandingCandidateLabels.checkInConfidence(lowConfidenceScenario)
        )
    }
    val capability = capabilitySummary()
    return mapOf(
        "checkInContentSkipped" to ContentUnderstandingConsumerSkipLabels.NO_SCENARIO
    ) + ContentUnderstandingConsumerFamilyLabels.supportMetadataTags(
        capability = capability,
        keysByFamily = mapOf(
            ContentUnderstandingFamily.SUBJECT to "checkInContentSkippedSubjectRegions",
            ContentUnderstandingFamily.OBJECT_TAGS to "checkInContentSkippedObjectTags"
        )
    )
}

private const val LEFT_SUBJECT_WATERMARK_THRESHOLD = 0.45f
private const val RIGHT_SUBJECT_WATERMARK_THRESHOLD = 0.55f
