package com.opencamera.core.media

import java.util.Locale

enum class ContentRegionRole {
    PERSON_SUBJECT,
    FOREGROUND,
    BACKGROUND,
    DEPTH_APPROXIMATION,
    SEMANTIC_REGION,
    FACE,
    SKIN,
    HAIR,
    SKY,
    VEGETATION,
    BUILDING,
    DOCUMENT,
    FOOD,
    WATER,
    NIGHT_REGION,
    OBJECT
}

enum class ContentTagFamily {
    SCENE,
    OBJECT,
    FACE_ATTRIBUTE
}

enum class ContentSceneHint(val storageKey: String) {
    FOOD("food"),
    SKY_WATER("sky-water"),
    LOW_LIGHT("low-light")
}

enum class ContentSceneHintSource {
    TAG,
    REGION
}

data class ContentSceneHintMatch(
    val hint: ContentSceneHint,
    val source: ContentSceneHintSource,
    val sourceKey: String,
    val sourceId: String,
    val confidence: Float
)

enum class ContentSubjectHint(val sourceKey: String) {
    FACE("face"),
    PERSON("person-subject"),
    FOREGROUND("foreground"),
    OBJECT("object")
}

enum class ContentSubjectHintSource {
    REGION,
    TAG
}

data class ContentSubjectHintMatch(
    val hint: ContentSubjectHint,
    val source: ContentSubjectHintSource,
    val sourceKey: String,
    val sourceId: String,
    val confidence: Float
)

enum class ContentCheckInScenarioHint(val storageKey: String) {
    PEOPLE_PLACE("people-place"),
    OBJECT_PLACE("object-place"),
    CLARITY("clarity")
}

data class ContentCheckInScenarioDecision(
    val scenario: ContentCheckInScenarioHint,
    val source: String,
    val confidence: Float
)

enum class ContentUnderstandingFamily(val key: String) {
    SUBJECT("subject"),
    SEMANTIC_REGIONS("semantic-regions"),
    FACE_LANDMARKS("face-landmarks"),
    OBJECT_TAGS("object-tags"),
    SCENE_TAGS("scene-tags");

    companion object {
        fun fromKey(key: String): ContentUnderstandingFamily? =
            entries.firstOrNull { it.key == key }
    }
}

object ContentUnderstandingFamilyKeys {
    const val SUBJECT: String = "subject"
    const val SEMANTIC_REGIONS: String = "semantic-regions"
    const val FACE_LANDMARKS: String = "face-landmarks"
    const val OBJECT_TAGS: String = "object-tags"
    const val SCENE_TAGS: String = "scene-tags"
}

data class ContentUnderstandingFamilySupport(
    val family: ContentUnderstandingFamily,
    val support: SceneMaskSupport
)

data class ContentSemanticRegionCandidate(
    val role: ContentRegionRole,
    val source: String,
    val confidence: Float,
    val bounds: ContentRegionBounds? = null
)

data class ContentPlacementAvoidanceCandidate(
    val role: ContentRegionRole,
    val source: String,
    val confidence: Float,
    val bounds: ContentRegionBounds
)

data class ContentSavedPhotoAdaptationDecisions(
    val styleScene: ContentSceneHintMatch? = null,
    val portraitSubject: ContentSubjectHintMatch? = null,
    val checkInScenario: ContentCheckInScenarioDecision? = null,
    val styleSceneCandidates: List<ContentSceneHintMatch> = emptyList(),
    val portraitSubjectCandidates: List<ContentSubjectHintMatch> = emptyList(),
    val checkInScenarioCandidates: List<ContentCheckInScenarioDecision> = emptyList()
) {
    companion object {
        val EMPTY = ContentSavedPhotoAdaptationDecisions()
    }
}

data class ContentSavedPhotoAdaptationProfile(
    val minConfidence: Float,
    val semanticRegionMinConfidence: Float = minConfidence,
    val semanticRegionLimit: Int = MAX_SEMANTIC_REGION_CANDIDATES,
    val placementAvoidanceMinConfidence: Float = minConfidence,
    val placementAvoidanceLimit: Int = MAX_PLACEMENT_AVOIDANCE_CANDIDATES
) {
    companion object {
        val DEFAULT = ContentSavedPhotoAdaptationProfile(minConfidence = 0.7f)
        val LOW_CONFIDENCE_REVIEW = ContentSavedPhotoAdaptationProfile(minConfidence = 0f)
    }
}

data class ContentRegionBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

data class ContentTagDescriptor(
    val tagId: String,
    val label: String,
    val family: ContentTagFamily,
    val confidence: Float,
    val backendId: String,
    val sourceRegionId: String? = null
)

data class ContentRegionDescriptor(
    val regionId: String,
    val role: ContentRegionRole,
    val quality: SceneMaskQuality,
    val backendId: String,
    val confidence: Float,
    val transform: SceneMaskTransform,
    val sourceMask: SceneMaskDescriptor? = null,
    val bounds: ContentRegionBounds? = null
)

data class ContentUnderstandingSnapshot(
    val snapshotId: String,
    val timestampMillis: Long,
    val quality: SceneMaskQuality,
    val backendId: String,
    val regions: List<ContentRegionDescriptor>,
    val tags: List<ContentTagDescriptor> = emptyList(),
    val diagnostics: List<String> = emptyList()
) {
    val isAvailable: Boolean
        get() = quality != SceneMaskQuality.UNAVAILABLE && (regions.isNotEmpty() || tags.isNotEmpty())

    fun isReadyForSavedPhotoAdaptation(): Boolean =
        isAvailable && quality in setOf(SceneMaskQuality.SAVED_PHOTO, SceneMaskQuality.DEGRADED)

    fun hasRegion(role: ContentRegionRole): Boolean =
        regions.any { it.role == role }

    fun primaryRegion(role: ContentRegionRole): ContentRegionDescriptor? =
        regions
            .filter { it.role == role }
            .maxByOrNull { it.confidence }

    fun hasTagFamily(family: ContentTagFamily): Boolean =
        tags.any { it.family == family }

    fun topTags(family: ContentTagFamily, limit: Int = Int.MAX_VALUE): List<ContentTagDescriptor> =
        tags
            .filter { it.family == family }
            .sortedByDescending { it.confidence }
            .take(limit)

    fun semanticRegionCandidates(
        minConfidence: Float = 0.7f,
        limit: Int = MAX_SEMANTIC_REGION_CANDIDATES
    ): List<ContentSemanticRegionCandidate> =
        regions
            .asSequence()
            .filter { it.role in SEMANTIC_REGION_ROLES }
            .filter { it.confidence >= minConfidence }
            .map { region ->
                ContentSemanticRegionCandidate(
                    role = region.role,
                    source = "region:${region.regionId}",
                    confidence = region.confidence,
                    bounds = region.bounds
                )
            }
            .sortedByStableCandidateRank { it.confidence to it.source }
            .take(limit.coerceAtLeast(0))

    fun semanticRegionCandidates(
        profile: ContentSavedPhotoAdaptationProfile
    ): List<ContentSemanticRegionCandidate> =
        semanticRegionCandidates(
            minConfidence = profile.semanticRegionMinConfidence.coerceIn(0f, 1f),
            limit = profile.semanticRegionLimit
        )

    fun placementAvoidanceCandidates(
        minConfidence: Float = 0.7f,
        limit: Int = MAX_PLACEMENT_AVOIDANCE_CANDIDATES
    ): List<ContentPlacementAvoidanceCandidate> =
        regions
            .asSequence()
            .filter { it.role in PLACEMENT_AVOIDANCE_REGION_ROLES }
            .filter { it.confidence >= minConfidence }
            .mapNotNull { region ->
                region.bounds?.let { bounds ->
                    ContentPlacementAvoidanceCandidate(
                        role = region.role,
                        source = "region:${region.regionId}",
                        confidence = region.confidence,
                        bounds = bounds
                    )
                }
            }
            .sortedByStableCandidateRank { it.confidence to it.source }
            .take(limit.coerceAtLeast(0))

    fun placementAvoidanceCandidates(
        profile: ContentSavedPhotoAdaptationProfile
    ): List<ContentPlacementAvoidanceCandidate> =
        placementAvoidanceCandidates(
            minConfidence = profile.placementAvoidanceMinConfidence.coerceIn(0f, 1f),
            limit = profile.placementAvoidanceLimit
        )

    fun primarySceneHint(minConfidence: Float = 0.7f): ContentSceneHintMatch? {
        return sceneHintCandidates(minConfidence = minConfidence, limit = 1)
            .firstOrNull()
    }

    fun sceneHintCandidates(
        minConfidence: Float = 0.7f,
        limit: Int = Int.MAX_VALUE
    ): List<ContentSceneHintMatch> {
        val tagHints = tags
            .asSequence()
            .filter { it.family in SCENE_HINT_TAG_FAMILIES }
            .filter { it.confidence >= minConfidence }
            .mapNotNull { tag ->
                tag.toSceneHint()?.let { hint ->
                    ContentSceneHintMatch(
                        hint = hint,
                        source = ContentSceneHintSource.TAG,
                        sourceKey = tag.family.toSceneSourceKey(),
                        sourceId = tag.tagId,
                        confidence = tag.confidence
                    )
                }
            }

        val regionHints = regions
            .asSequence()
            .filter { it.confidence >= minConfidence }
            .mapNotNull { region ->
                region.role.toSceneHint()?.let { hint ->
                    ContentSceneHintMatch(
                        hint = hint,
                        source = ContentSceneHintSource.REGION,
                        sourceKey = region.role.toStableKey(),
                        sourceId = region.regionId,
                        confidence = region.confidence
                    )
                }
            }

        return (tagHints + regionHints)
            .sortedByStableCandidateRank { it.confidence to it.toStableSourceLabel() }
            .take(limit.coerceAtLeast(0))
    }

    fun primarySubjectHint(
        vararg requestedHints: ContentSubjectHint,
        minConfidence: Float = 0.7f
    ): ContentSubjectHintMatch? {
        return subjectHintCandidates(
            *requestedHints,
            minConfidence = minConfidence,
            limit = 1
        ).firstOrNull()
    }

    fun subjectHintCandidates(
        vararg requestedHints: ContentSubjectHint,
        minConfidence: Float = 0.7f,
        limit: Int = Int.MAX_VALUE
    ): List<ContentSubjectHintMatch> {
        val allowedHints = requestedHints.toSet()
        val regionHints = regions
            .asSequence()
            .filter { it.confidence >= minConfidence }
            .mapNotNull { region -> region.toSubjectHintMatch() }
            .filter { it.hint in allowedHints }

        val tagHints = tags
            .asSequence()
            .filter { it.confidence >= minConfidence }
            .mapNotNull { tag -> tag.toSubjectHintMatch() }
            .filter { it.hint in allowedHints }

        return (regionHints + tagHints)
            .sortedByStableCandidateRank { it.confidence to it.toStableSourceLabel() }
            .take(limit.coerceAtLeast(0))
    }

    fun savedPhotoAdaptationDecisions(
        minConfidence: Float = ContentSavedPhotoAdaptationProfile.DEFAULT.minConfidence
    ): ContentSavedPhotoAdaptationDecisions =
        savedPhotoAdaptationDecisions(ContentSavedPhotoAdaptationProfile(minConfidence = minConfidence))

    fun savedPhotoAdaptationDecisions(
        profile: ContentSavedPhotoAdaptationProfile
    ): ContentSavedPhotoAdaptationDecisions {
        if (!isReadyForSavedPhotoAdaptation()) return ContentSavedPhotoAdaptationDecisions.EMPTY
        val minConfidence = profile.minConfidence.coerceIn(0f, 1f)
        val styleSceneCandidates = sceneHintCandidates(minConfidence = minConfidence)
        val portraitSubjectCandidates = subjectHintCandidates(
            ContentSubjectHint.FACE,
            ContentSubjectHint.PERSON,
            ContentSubjectHint.FOREGROUND,
            minConfidence = minConfidence
        )
        val checkInScenarioCandidates = checkInScenarioCandidates(minConfidence = minConfidence)
        return ContentSavedPhotoAdaptationDecisions(
            styleScene = styleSceneCandidates.firstOrNull(),
            portraitSubject = portraitSubjectCandidates.firstOrNull(),
            checkInScenario = checkInScenarioDecision(minConfidence = minConfidence),
            styleSceneCandidates = styleSceneCandidates,
            portraitSubjectCandidates = portraitSubjectCandidates,
            checkInScenarioCandidates = checkInScenarioCandidates
        )
    }

    fun capabilitySummary(): ContentUnderstandingCapability = ContentUnderstandingCapability(
        subjectRegions = familySupport(
            hasEvidence = regions.any { it.role in SUBJECT_REGION_ROLES },
            hasDegradedEvidence = regions.any { it.role in SUBJECT_REGION_ROLES && it.quality == SceneMaskQuality.DEGRADED },
            diagnosticSupport = diagnostics.contentFamilySupport(ContentUnderstandingFamily.SUBJECT)
        ),
        semanticRegions = familySupport(
            hasEvidence = regions.any { it.role in SEMANTIC_REGION_ROLES },
            hasDegradedEvidence = regions.any { it.role in SEMANTIC_REGION_ROLES && it.quality == SceneMaskQuality.DEGRADED },
            diagnosticSupport = diagnostics.contentFamilySupport(ContentUnderstandingFamily.SEMANTIC_REGIONS)
        ),
        faceLandmarks = familySupport(
            hasEvidence = regions.any { it.role in FACE_REGION_ROLES } ||
                tags.any { it.family == ContentTagFamily.FACE_ATTRIBUTE },
            hasDegradedEvidence = regions.any { it.role in FACE_REGION_ROLES && it.quality == SceneMaskQuality.DEGRADED },
            diagnosticSupport = diagnostics.contentFamilySupport(ContentUnderstandingFamily.FACE_LANDMARKS)
        ),
        objectTags = familySupport(
            hasEvidence = tags.any { it.family == ContentTagFamily.OBJECT } ||
                regions.any { it.role == ContentRegionRole.OBJECT },
            hasDegradedEvidence = regions.any { it.role == ContentRegionRole.OBJECT && it.quality == SceneMaskQuality.DEGRADED },
            diagnosticSupport = diagnostics.contentFamilySupport(ContentUnderstandingFamily.OBJECT_TAGS)
        ),
        sceneTags = familySupport(
            hasEvidence = tags.any { it.family == ContentTagFamily.SCENE },
            hasDegradedEvidence = false,
            diagnosticSupport = diagnostics.contentFamilySupport(ContentUnderstandingFamily.SCENE_TAGS)
        ),
        backendId = backendId,
        reason = diagnostics.primaryContentReason()
    )

    private fun checkInScenarioDecision(
        minConfidence: Float
    ): ContentCheckInScenarioDecision? =
        checkInScenarioCandidates(minConfidence = minConfidence)
            .firstOrNull()

    private fun checkInScenarioCandidates(
        minConfidence: Float
    ): List<ContentCheckInScenarioDecision> =
        listOfNotNull(
            primaryDocumentRegion(minConfidence = minConfidence)?.let { document ->
                ContentCheckInScenarioDecision(
                    scenario = ContentCheckInScenarioHint.CLARITY,
                    source = "region:${document.role.toStableKey()}",
                    confidence = document.confidence
                )
            },
            primarySubjectHint(
                ContentSubjectHint.FACE,
                ContentSubjectHint.PERSON,
                minConfidence = minConfidence
            )?.let { subject ->
                ContentCheckInScenarioDecision(
                    scenario = ContentCheckInScenarioHint.PEOPLE_PLACE,
                    source = subject.toStableSourceLabel(),
                    confidence = subject.confidence
                )
            },
            primarySubjectHint(
                ContentSubjectHint.OBJECT,
                minConfidence = minConfidence
            )?.let { subject ->
                ContentCheckInScenarioDecision(
                    scenario = ContentCheckInScenarioHint.OBJECT_PLACE,
                    source = subject.toStableSourceLabel(),
                    confidence = subject.confidence
                )
            }
        ).sortedByStableCandidateRank { it.confidence to it.source }

    private fun primaryDocumentRegion(
        minConfidence: Float
    ): ContentRegionDescriptor? =
        regions
            .filter { it.role == ContentRegionRole.DOCUMENT && it.confidence >= minConfidence }
            .maxByOrNull { it.confidence }

    companion object {
        fun fromSceneMask(
            descriptor: SceneMaskDescriptor,
            timestampMillis: Long,
            tags: List<ContentTagDescriptor> = emptyList()
        ): ContentUnderstandingSnapshot {
            val region = ContentRegionDescriptor(
                regionId = descriptor.maskId,
                role = descriptor.role.toContentRegionRole(),
                quality = descriptor.quality,
                backendId = descriptor.backendId,
                confidence = descriptor.normalizedConfidence(),
                transform = descriptor.transform,
                sourceMask = descriptor
            )
            return ContentUnderstandingSnapshot(
                snapshotId = "content-${descriptor.maskId}",
                timestampMillis = timestampMillis,
                quality = descriptor.quality,
                backendId = descriptor.backendId,
                regions = listOf(region),
                tags = tags,
                diagnostics = descriptor.diagnostics
            )
        }

        fun fromTags(
            snapshotId: String,
            timestampMillis: Long,
            backendId: String,
            quality: SceneMaskQuality,
            tags: List<ContentTagDescriptor>,
            diagnostics: List<String> = emptyList()
        ): ContentUnderstandingSnapshot = ContentUnderstandingSnapshot(
            snapshotId = snapshotId,
            timestampMillis = timestampMillis,
            quality = if (tags.isEmpty() && quality != SceneMaskQuality.UNAVAILABLE) {
                SceneMaskQuality.DEGRADED
            } else {
                quality
            },
            backendId = backendId,
            regions = emptyList(),
            tags = tags,
            diagnostics = diagnostics
        )

        fun unavailable(
            timestampMillis: Long,
            backendId: String,
            reason: String
        ): ContentUnderstandingSnapshot = ContentUnderstandingSnapshot(
            snapshotId = "content-unavailable-$timestampMillis",
            timestampMillis = timestampMillis,
            quality = SceneMaskQuality.UNAVAILABLE,
            backendId = backendId,
            regions = emptyList(),
            diagnostics = listOf(
                ContentUnderstandingPipelineNotes.unavailable(),
                ContentUnderstandingPipelineNotes.reason(reason)
            )
        )

        fun combine(
            snapshotId: String,
            timestampMillis: Long,
            backendId: String,
            snapshots: List<ContentUnderstandingSnapshot>
        ): ContentUnderstandingSnapshot {
            val regions = snapshots.flatMap { it.regions }
            val tags = snapshots.flatMap { it.tags }
            val diagnostics = snapshots.flatMap { it.diagnostics }.distinct()
            return ContentUnderstandingSnapshot(
                snapshotId = snapshotId,
                timestampMillis = timestampMillis,
                quality = combinedQuality(snapshots, regions, tags),
                backendId = backendId,
                regions = regions,
                tags = tags,
                diagnostics = diagnostics
            )
        }

        private fun combinedQuality(
            snapshots: List<ContentUnderstandingSnapshot>,
            regions: List<ContentRegionDescriptor>,
            tags: List<ContentTagDescriptor>
        ): SceneMaskQuality {
            if (regions.isEmpty() && tags.isEmpty()) return SceneMaskQuality.UNAVAILABLE
            if (snapshots.any { it.quality == SceneMaskQuality.UNAVAILABLE || it.quality == SceneMaskQuality.DEGRADED }) {
                return SceneMaskQuality.DEGRADED
            }
            return when {
                snapshots.any { it.quality == SceneMaskQuality.SAVED_PHOTO } -> SceneMaskQuality.SAVED_PHOTO
                snapshots.any { it.quality == SceneMaskQuality.PREVIEW_APPROXIMATE } -> SceneMaskQuality.PREVIEW_APPROXIMATE
                else -> SceneMaskQuality.DEGRADED
            }
        }
    }
}

private val SUBJECT_REGION_ROLES = setOf(
    ContentRegionRole.PERSON_SUBJECT,
    ContentRegionRole.FOREGROUND,
    ContentRegionRole.FACE
)

private val FACE_REGION_ROLES = setOf(
    ContentRegionRole.FACE,
    ContentRegionRole.SKIN,
    ContentRegionRole.HAIR
)

private val SEMANTIC_REGION_ROLES = setOf(
    ContentRegionRole.SEMANTIC_REGION,
    ContentRegionRole.SKY,
    ContentRegionRole.VEGETATION,
    ContentRegionRole.BUILDING,
    ContentRegionRole.DOCUMENT,
    ContentRegionRole.FOOD,
    ContentRegionRole.WATER,
    ContentRegionRole.NIGHT_REGION
)

private val PLACEMENT_AVOIDANCE_REGION_ROLES =
    SUBJECT_REGION_ROLES + SEMANTIC_REGION_ROLES + ContentRegionRole.OBJECT

private const val MAX_SEMANTIC_REGION_CANDIDATES = 6
private const val MAX_PLACEMENT_AVOIDANCE_CANDIDATES = 6

private fun familySupport(
    hasEvidence: Boolean,
    hasDegradedEvidence: Boolean,
    diagnosticSupport: SceneMaskSupport? = null
): SceneMaskSupport = when {
    diagnosticSupport == SceneMaskSupport.DEGRADED -> SceneMaskSupport.DEGRADED
    hasDegradedEvidence -> SceneMaskSupport.DEGRADED
    hasEvidence -> SceneMaskSupport.SUPPORTED
    diagnosticSupport != null -> diagnosticSupport
    else -> SceneMaskSupport.UNSUPPORTED
}

private fun List<String>.primaryContentReason(): String? =
    firstNotNullOfOrNull { diagnostic ->
        diagnostic.removePrefix("content-understanding:reason=")
            .takeIf { it != diagnostic && it.isNotBlank() }
    }

private fun List<String>.contentFamilySupport(family: ContentUnderstandingFamily): SceneMaskSupport? =
    asSequence()
        .mapNotNull { ContentUnderstandingPipelineNotes.parseFamilySupport(it) }
        .filter { it.family == family }
        .map { it.support }
        .maxByOrNull { it.contentFamilySupportPriority() }

private fun String.toContentFamilySupport(): SceneMaskSupport? = when (this) {
    "applied", "supported" -> SceneMaskSupport.SUPPORTED
    "degraded" -> SceneMaskSupport.DEGRADED
    "unsupported" -> SceneMaskSupport.UNSUPPORTED
    else -> null
}

private fun SceneMaskSupport.contentFamilySupportPriority(): Int = when (this) {
    SceneMaskSupport.DEGRADED -> 3
    SceneMaskSupport.SUPPORTED -> 2
    SceneMaskSupport.UNSUPPORTED -> 1
}

private val SCENE_HINT_TAG_FAMILIES = setOf(
    ContentTagFamily.SCENE,
    ContentTagFamily.OBJECT
)

private fun ContentTagDescriptor.toSceneHint(): ContentSceneHint? =
    ContentRecognitionLabelClassifier.sceneHint(tagId = tagId, label = label)

private fun ContentRegionRole.toSceneHint(): ContentSceneHint? = when (this) {
    ContentRegionRole.FOOD -> ContentSceneHint.FOOD
    ContentRegionRole.SKY,
    ContentRegionRole.WATER -> ContentSceneHint.SKY_WATER
    ContentRegionRole.NIGHT_REGION -> ContentSceneHint.LOW_LIGHT
    else -> null
}

private fun ContentRegionDescriptor.toSubjectHintMatch(): ContentSubjectHintMatch? {
    val hint = role.toSubjectHint() ?: return null
    return ContentSubjectHintMatch(
        hint = hint,
        source = ContentSubjectHintSource.REGION,
        sourceKey = hint.sourceKey,
        sourceId = regionId,
        confidence = confidence
    )
}

private fun ContentTagDescriptor.toSubjectHintMatch(): ContentSubjectHintMatch? {
    if (family != ContentTagFamily.OBJECT) return null
    return ContentSubjectHintMatch(
        hint = ContentSubjectHint.OBJECT,
        source = ContentSubjectHintSource.TAG,
        sourceKey = ContentSubjectHint.OBJECT.sourceKey,
        sourceId = tagId,
        confidence = confidence
    )
}

fun ContentSubjectHintMatch.toStableSourceLabel(): String = when (source) {
    ContentSubjectHintSource.REGION -> "region:$sourceKey"
    ContentSubjectHintSource.TAG -> "tag:$sourceId"
}

fun ContentSceneHintMatch.toStableSourceLabel(): String = when (source) {
    ContentSceneHintSource.REGION -> "region:$sourceKey"
    ContentSceneHintSource.TAG -> "tag:$sourceId"
}

private fun ContentTagFamily.toSceneSourceKey(): String = when (this) {
    ContentTagFamily.SCENE -> "scene-tag"
    ContentTagFamily.OBJECT -> "object-tag"
    ContentTagFamily.FACE_ATTRIBUTE -> "face-attribute"
}

private fun ContentRegionRole.toStableKey(): String =
    name.lowercase().replace('_', '-')

private fun ContentRegionRole.toSubjectHint(): ContentSubjectHint? = when (this) {
    ContentRegionRole.FACE -> ContentSubjectHint.FACE
    ContentRegionRole.PERSON_SUBJECT -> ContentSubjectHint.PERSON
    ContentRegionRole.FOREGROUND -> ContentSubjectHint.FOREGROUND
    ContentRegionRole.OBJECT -> ContentSubjectHint.OBJECT
    else -> null
}

private fun <T> Sequence<T>.sortedByStableCandidateRank(
    selector: (T) -> Pair<Float, String>
): List<T> =
    sortedWith(
        compareByDescending<T> { selector(it).first }
            .thenBy { selector(it).second }
    ).toList()

private fun <T> Iterable<T>.sortedByStableCandidateRank(
    selector: (T) -> Pair<Float, String>
): List<T> =
    asSequence()
        .sortedByStableCandidateRank(selector)

data class ContentUnderstandingCapability(
    val subjectRegions: SceneMaskSupport,
    val semanticRegions: SceneMaskSupport,
    val faceLandmarks: SceneMaskSupport,
    val objectTags: SceneMaskSupport,
    val sceneTags: SceneMaskSupport,
    val backendId: String,
    val reason: String? = null
) {
    fun supportFor(family: ContentUnderstandingFamily): SceneMaskSupport = when (family) {
        ContentUnderstandingFamily.SUBJECT -> subjectRegions
        ContentUnderstandingFamily.SEMANTIC_REGIONS -> semanticRegions
        ContentUnderstandingFamily.FACE_LANDMARKS -> faceLandmarks
        ContentUnderstandingFamily.OBJECT_TAGS -> objectTags
        ContentUnderstandingFamily.SCENE_TAGS -> sceneTags
    }

    fun familySupports(): List<ContentUnderstandingFamilySupport> =
        ContentUnderstandingFamily.entries.map { family ->
            ContentUnderstandingFamilySupport(
                family = family,
                support = supportFor(family)
            )
        }
}

object ContentUnderstandingPipelineNotes {
    fun backend(backendId: String): String =
        "content-understanding:backend=$backendId"

    fun availability(isAvailable: Boolean): String =
        if (isAvailable) "content-understanding:available" else unavailable()

    fun unavailable(): String =
        "content-understanding:unavailable"

    fun familySupport(familyKey: String, support: SceneMaskSupport): String =
        "content-understanding:$familyKey=${ContentUnderstandingSupportLabels.support(support)}"

    fun familySupport(family: ContentUnderstandingFamily, support: SceneMaskSupport): String =
        familySupport(family.key, support)

    fun parseFamilySupport(note: String): ContentUnderstandingFamilySupport? {
        val body = note.removePrefix("content-understanding:")
            .takeIf { it != note }
            ?: return null
        val separatorIndex = body.indexOf('=')
        if (separatorIndex <= 0 || separatorIndex == body.lastIndex) return null
        val family = ContentUnderstandingFamily.fromKey(body.substring(0, separatorIndex)) ?: return null
        val support = body.substring(separatorIndex + 1).toContentFamilySupport() ?: return null
        return ContentUnderstandingFamilySupport(family = family, support = support)
    }

    fun subjectRegions(support: SceneMaskSupport): String =
        familySupport(ContentUnderstandingFamilyKeys.SUBJECT, support)

    fun semanticRegions(support: SceneMaskSupport): String =
        familySupport(ContentUnderstandingFamilyKeys.SEMANTIC_REGIONS, support)

    fun faceLandmarks(support: SceneMaskSupport): String =
        familySupport(ContentUnderstandingFamilyKeys.FACE_LANDMARKS, support)

    fun objectTags(support: SceneMaskSupport): String =
        familySupport(ContentUnderstandingFamilyKeys.OBJECT_TAGS, support)

    fun sceneTags(support: SceneMaskSupport): String =
        familySupport(ContentUnderstandingFamilyKeys.SCENE_TAGS, support)

    fun reason(reason: String): String =
        "content-understanding:reason=$reason"

    fun partialFailureReason(prefix: String, failedFamilyKeys: List<String>): String =
        reason("$prefix:${failedFamilyKeys.joinToString("+")}")

    fun partialFailureReason(prefix: String, failedFamilies: Iterable<ContentUnderstandingFamily>): String =
        partialFailureReason(
            prefix = prefix,
            failedFamilyKeys = failedFamilies.map { it.key }
        )

    fun capabilityNotes(capability: ContentUnderstandingCapability): List<String> = buildList {
        add(backend(capability.backendId))
        capability.familySupports().forEach { familySupport ->
            add(familySupport(familySupport.family, familySupport.support))
        }
        capability.reason?.let { add(reason(it)) }
    }

    fun snapshotCapabilityNotes(snapshot: ContentUnderstandingSnapshot): List<String> =
        capabilityNotes(snapshot.capabilitySummary())

    fun semanticSummaryNotes(snapshot: ContentUnderstandingSnapshot): List<String> = buildList {
        snapshot.primarySceneHint()?.let { scene ->
            add("content-understanding:primary-scene=${ContentUnderstandingCandidateLabels.sceneHint(scene)}")
            add("content-understanding:primary-scene-source=${ContentUnderstandingCandidateLabels.sceneSourceKind(scene)}")
            add("content-understanding:primary-scene-source-id=${ContentUnderstandingCandidateLabels.sceneSource(scene)}")
            add("content-understanding:primary-scene-confidence=${ContentUnderstandingCandidateLabels.sceneConfidence(scene)}")
        }
        snapshot.primarySubjectHint(
            ContentSubjectHint.FACE,
            ContentSubjectHint.PERSON,
            ContentSubjectHint.FOREGROUND
        )?.let { people ->
            add("content-understanding:primary-people=${ContentUnderstandingCandidateLabels.subjectHint(people)}")
            add("content-understanding:primary-people-source=${ContentUnderstandingCandidateLabels.subjectSource(people)}")
            add("content-understanding:primary-people-confidence=${ContentUnderstandingCandidateLabels.subjectConfidence(people)}")
        }
        snapshot.primarySubjectHint(ContentSubjectHint.OBJECT)?.let { obj ->
            add("content-understanding:primary-object=${ContentUnderstandingCandidateLabels.subjectHint(obj)}")
            add("content-understanding:primary-object-source=${ContentUnderstandingCandidateLabels.subjectSource(obj)}")
            add("content-understanding:primary-object-confidence=${ContentUnderstandingCandidateLabels.subjectConfidence(obj)}")
        }
        val semanticRegionCandidates = snapshot.semanticRegionCandidates(ContentSavedPhotoAdaptationProfile.DEFAULT)
        if (semanticRegionCandidates.isNotEmpty()) {
            add(
                "content-understanding:semantic-region-candidates=" +
                    ContentUnderstandingCandidateLabels.semanticRegions(semanticRegionCandidates)
            )
            ContentUnderstandingCandidateLabels.semanticRegionBounds(semanticRegionCandidates)
                .takeIf { it.isNotBlank() }
                ?.let { add("content-understanding:semantic-region-bounds=$it") }
        }
        val placementAvoidanceCandidates = snapshot.placementAvoidanceCandidates(ContentSavedPhotoAdaptationProfile.DEFAULT)
        if (placementAvoidanceCandidates.isNotEmpty()) {
            add(
                "content-understanding:placement-avoidance=" +
                    ContentUnderstandingCandidateLabels.placementAvoidance(placementAvoidanceCandidates)
            )
        }
    }

    fun savedPhotoAdaptationDecisionNotes(snapshot: ContentUnderstandingSnapshot): List<String> = buildList {
        if (!snapshot.isReadyForSavedPhotoAdaptation()) {
            val capability = snapshot.capabilitySummary()
            add("content-understanding:adaptation-ready=false")
            add("content-understanding:adaptation-not-ready-quality=${snapshot.quality.name.lowercase()}")
            capability.reason?.let { reason ->
                add("content-understanding:adaptation-not-ready-reason=$reason")
            }
            return@buildList
        }
        add("content-understanding:adaptation-ready=true")
        val decisions = snapshot.savedPhotoAdaptationDecisions()
        decisions.styleScene?.let { scene ->
            add("content-understanding:adaptation-style-scene=${ContentUnderstandingCandidateLabels.sceneHint(scene)}")
            add("content-understanding:adaptation-style-scene-source=${ContentUnderstandingCandidateLabels.sceneSourceKind(scene)}")
            add("content-understanding:adaptation-style-scene-source-id=${ContentUnderstandingCandidateLabels.sceneSource(scene)}")
            add("content-understanding:adaptation-style-scene-confidence=${ContentUnderstandingCandidateLabels.sceneConfidence(scene)}")
        }
        if (decisions.styleSceneCandidates.isNotEmpty()) {
            add(
                "content-understanding:adaptation-style-scene-candidates=" +
                    ContentUnderstandingCandidateLabels.sceneCandidates(decisions.styleSceneCandidates)
            )
        }
        decisions.portraitSubject?.let { subject ->
            add("content-understanding:adaptation-portrait-subject=${ContentUnderstandingCandidateLabels.subjectHint(subject)}")
            add("content-understanding:adaptation-portrait-subject-source=${ContentUnderstandingCandidateLabels.subjectSource(subject)}")
            add("content-understanding:adaptation-portrait-subject-confidence=${ContentUnderstandingCandidateLabels.subjectConfidence(subject)}")
        }
        if (decisions.portraitSubjectCandidates.isNotEmpty()) {
            add(
                "content-understanding:adaptation-portrait-subject-candidates=" +
                    ContentUnderstandingCandidateLabels.subjectCandidates(decisions.portraitSubjectCandidates)
            )
        }
        decisions.checkInScenario?.let { checkIn ->
            add("content-understanding:adaptation-checkin-scenario=${ContentUnderstandingCandidateLabels.checkInScenario(checkIn)}")
            add("content-understanding:adaptation-checkin-source=${ContentUnderstandingCandidateLabels.checkInSource(checkIn)}")
            add("content-understanding:adaptation-checkin-confidence=${ContentUnderstandingCandidateLabels.checkInConfidence(checkIn)}")
        }
        if (decisions.checkInScenarioCandidates.isNotEmpty()) {
            add(
                "content-understanding:adaptation-checkin-candidates=" +
                    ContentUnderstandingCandidateLabels.checkInCandidates(decisions.checkInScenarioCandidates)
            )
        }
    }

}

object ContentUnderstandingCandidateLabels {
    fun sceneHint(scene: ContentSceneHintMatch): String =
        scene.hint.storageKey

    fun sceneSourceKind(scene: ContentSceneHintMatch): String = when (scene.source) {
        ContentSceneHintSource.TAG -> "tag"
        ContentSceneHintSource.REGION -> "region"
    }

    fun sceneSource(scene: ContentSceneHintMatch): String =
        scene.toStableSourceLabel()

    fun sceneConfidence(scene: ContentSceneHintMatch): String =
        scene.confidence.toCandidateConfidence()

    fun subjectHint(subject: ContentSubjectHintMatch): String =
        subject.hint.sourceKey

    fun subjectSource(subject: ContentSubjectHintMatch): String =
        subject.toStableSourceLabel()

    fun subjectConfidence(subject: ContentSubjectHintMatch): String =
        subject.confidence.toCandidateConfidence()

    fun checkInScenario(checkIn: ContentCheckInScenarioDecision): String =
        checkIn.scenario.storageKey

    fun checkInSource(checkIn: ContentCheckInScenarioDecision): String =
        checkIn.source

    fun checkInConfidence(checkIn: ContentCheckInScenarioDecision): String =
        checkIn.confidence.toCandidateConfidence()

    fun sceneCandidates(candidates: List<ContentSceneHintMatch>): String =
        candidates.joinToString(",") { scene ->
            "${sceneHint(scene)}@${sceneSource(scene)}:${sceneConfidence(scene)}"
        }

    fun subjectCandidates(candidates: List<ContentSubjectHintMatch>): String =
        candidates.joinToString(",") { subject ->
            "${subjectHint(subject)}@${subjectSource(subject)}:${subjectConfidence(subject)}"
        }

    fun checkInCandidates(candidates: List<ContentCheckInScenarioDecision>): String =
        candidates.joinToString(",") { checkIn ->
            "${checkInScenario(checkIn)}@${checkInSource(checkIn)}:${checkInConfidence(checkIn)}"
        }

    fun semanticRegions(candidates: List<ContentSemanticRegionCandidate>): String =
        candidates.joinToString(",") { semantic ->
            "${semantic.role.toStableKey()}@${semantic.source}:${semantic.confidence.toCandidateConfidence()}"
        }

    fun semanticRegionBounds(candidates: List<ContentSemanticRegionCandidate>): String =
        candidates
            .mapNotNull { semantic ->
                semantic.bounds?.let { bounds ->
                    "${semantic.source}=${bounds.toCandidateBounds()}"
                }
            }
            .joinToString(";")

    fun placementAvoidance(candidates: List<ContentPlacementAvoidanceCandidate>): String =
        candidates.joinToString(";") { candidate ->
            "${candidate.role.toStableKey()}@${candidate.source}:${candidate.confidence.toCandidateConfidence()}=" +
                candidate.bounds.toCandidateBounds()
        }

    fun placementRole(candidate: ContentPlacementAvoidanceCandidate): String =
        candidate.role.toStableKey()

    fun placementSource(candidate: ContentPlacementAvoidanceCandidate): String =
        candidate.source

    fun placementConfidence(candidate: ContentPlacementAvoidanceCandidate): String =
        candidate.confidence.toCandidateConfidence()

    fun placementCenter(candidate: ContentPlacementAvoidanceCandidate): String =
        candidate.bounds.toCandidateCenter()

    fun placementSize(candidate: ContentPlacementAvoidanceCandidate): String =
        candidate.bounds.toCandidateSize()

    fun placementArea(candidate: ContentPlacementAvoidanceCandidate): String =
        candidate.bounds.toCandidateArea()

    private fun Float.toCandidateConfidence(): String =
        String.format(Locale.US, "%.2f", this)

    private fun ContentRegionBounds.toCandidateBounds(): String =
        listOf(left, top, right, bottom)
            .joinToString(",") { it.toCandidateConfidence() }

    private fun ContentRegionBounds.toCandidateCenter(): String {
        val centerX = ((left + right) / 2f).coerceIn(0f, 1f)
        val centerY = ((top + bottom) / 2f).coerceIn(0f, 1f)
        return listOf(centerX, centerY).joinToString(",") { it.toCandidateConfidence() }
    }

    private fun ContentRegionBounds.toCandidateSize(): String {
        val width = (right - left).coerceIn(0f, 1f)
        val height = (bottom - top).coerceIn(0f, 1f)
        return listOf(width, height).joinToString(",") { it.toCandidateConfidence() }
    }

    private fun ContentRegionBounds.toCandidateArea(): String =
        ((right - left).coerceIn(0f, 1f) * (bottom - top).coerceIn(0f, 1f))
            .coerceIn(0f, 1f)
            .toCandidateConfidence()
}

object ContentUnderstandingConsumerSkipLabels {
    const val MISSING_CONTENT: String = "missing-content"
    const val NOT_READY: String = "not-ready"
    const val NO_SCENE_HINT: String = "no-scene-hint"
    const val NO_SUBJECT_HINT: String = "no-subject-hint"
    const val NO_SCENARIO: String = "no-scenario"
    const val LOW_CONFIDENCE_SCENE_HINT: String = "low-confidence-scene-hint"
    const val LOW_CONFIDENCE_SUBJECT_HINT: String = "low-confidence-subject-hint"
    const val LOW_CONFIDENCE_SCENARIO: String = "low-confidence-scenario"
}

object ContentUnderstandingSupportLabels {
    fun support(
        capability: ContentUnderstandingCapability,
        family: ContentUnderstandingFamily
    ): String = support(capability.supportFor(family))

    fun support(support: SceneMaskSupport): String = when (support) {
        SceneMaskSupport.SUPPORTED -> "applied"
        SceneMaskSupport.DEGRADED -> "degraded"
        SceneMaskSupport.UNSUPPORTED -> "unsupported"
    }
}

object ContentUnderstandingFamilyMetadataLabels {
    fun supportTag(family: ContentUnderstandingFamily): String = when (family) {
        ContentUnderstandingFamily.SUBJECT -> "contentSubjectRegions"
        ContentUnderstandingFamily.SEMANTIC_REGIONS -> "contentSemanticRegions"
        ContentUnderstandingFamily.FACE_LANDMARKS -> "contentFaceLandmarks"
        ContentUnderstandingFamily.OBJECT_TAGS -> "contentObjectTags"
        ContentUnderstandingFamily.SCENE_TAGS -> "contentSceneTags"
    }

    fun supportTags(capability: ContentUnderstandingCapability): Map<String, String> =
        capability.familySupports().associate { familySupport ->
            supportTag(familySupport.family) to ContentUnderstandingSupportLabels.support(familySupport.support)
        }
}

object ContentUnderstandingConsumerFamilyLabels {
    fun supportNote(
        prefix: String,
        capability: ContentUnderstandingCapability,
        family: ContentUnderstandingFamily
    ): String = "$prefix:${family.key}=${ContentUnderstandingSupportLabels.support(capability, family)}"

    fun supportNotes(
        prefix: String,
        capability: ContentUnderstandingCapability,
        families: Iterable<ContentUnderstandingFamily>
    ): List<String> = families.map { family -> supportNote(prefix, capability, family) }

    fun supportMetadataTags(
        capability: ContentUnderstandingCapability,
        keysByFamily: Map<ContentUnderstandingFamily, String>
    ): Map<String, String> = keysByFamily.entries.associate { (family, metadataKey) ->
        metadataKey to ContentUnderstandingSupportLabels.support(capability, family)
    }
}

fun SceneMaskRole.toContentRegionRole(): ContentRegionRole = when (this) {
    SceneMaskRole.PERSON_SUBJECT -> ContentRegionRole.PERSON_SUBJECT
    SceneMaskRole.FOREGROUND -> ContentRegionRole.FOREGROUND
    SceneMaskRole.BACKGROUND -> ContentRegionRole.BACKGROUND
    SceneMaskRole.DEPTH_APPROXIMATION -> ContentRegionRole.DEPTH_APPROXIMATION
    SceneMaskRole.SEMANTIC_REGION -> ContentRegionRole.SEMANTIC_REGION
}
