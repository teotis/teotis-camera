package com.opencamera.core.media

object ContentRecognitionLabelClassifier {
    fun sceneHint(
        tagId: String,
        label: String
    ): ContentSceneHint? = when {
        matches(tagId, label, aliases = FOOD_ALIASES) -> ContentSceneHint.FOOD
        matches(tagId, label, aliases = SKY_WATER_ALIASES) -> ContentSceneHint.SKY_WATER
        matches(tagId, label, aliases = LOW_LIGHT_ALIASES) -> ContentSceneHint.LOW_LIGHT
        else -> null
    }

    fun semanticRegionRole(label: String): ContentRegionRole? = when {
        matches(label, aliases = FOOD_ALIASES) -> ContentRegionRole.FOOD
        matches(label, aliases = DOCUMENT_ALIASES) -> ContentRegionRole.DOCUMENT
        matches(label, aliases = BUILDING_ALIASES) -> ContentRegionRole.BUILDING
        matches(label, aliases = WATER_ALIASES) -> ContentRegionRole.WATER
        matches(label, aliases = VEGETATION_ALIASES) -> ContentRegionRole.VEGETATION
        matches(label, aliases = SKY_ALIASES) -> ContentRegionRole.SKY
        matches(label, aliases = LOW_LIGHT_ALIASES) -> ContentRegionRole.NIGHT_REGION
        else -> null
    }

    private fun matches(vararg texts: String, aliases: Set<String>): Boolean =
        ContentRecognitionTextMatcher.matchesAnyToken(
            *texts,
            aliases = aliases
        )

    private val FOOD_ALIASES = setOf(
        "food",
        "foods",
        "meal",
        "meals",
        "dish",
        "dishes",
        "restaurant",
        "restaurants"
    )
    private val SKY_WATER_ALIASES = setOf(
        "sky",
        "skies",
        "beach",
        "beaches",
        "water",
        "lake",
        "lakes",
        "sea",
        "seas"
    )
    private val LOW_LIGHT_ALIASES = setOf(
        "night",
        "nights",
        "dark",
        "concert",
        "concerts"
    )
    private val DOCUMENT_ALIASES = setOf(
        "document",
        "documents",
        "receipt",
        "receipts",
        "paper",
        "papers",
        "menu",
        "menus"
    )
    private val BUILDING_ALIASES = setOf(
        "building",
        "buildings",
        "architecture",
        "house",
        "houses"
    )
    private val WATER_ALIASES = setOf(
        "water",
        "lake",
        "lakes",
        "sea",
        "seas",
        "beach",
        "beaches"
    )
    private val VEGETATION_ALIASES = setOf(
        "plant",
        "plants",
        "flower",
        "flowers",
        "tree",
        "trees",
        "grass",
        "vegetation"
    )
    private val SKY_ALIASES = setOf(
        "sky",
        "skies"
    )
}
