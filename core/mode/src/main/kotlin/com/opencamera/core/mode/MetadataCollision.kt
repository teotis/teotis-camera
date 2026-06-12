package com.opencamera.core.mode

/**
 * Thrown when two non-override metadata layers produce the same key with different values.
 * Callers must resolve the conflict by placing the intended value in overrideTags.
 */
class MetadataCollision(
    val key: String,
    val leftValue: String,
    val rightValue: String,
    val leftLayer: String,
    val rightLayer: String
) : IllegalStateException(
    "Metadata key '$key' has conflicting values: " +
        "$leftLayer='$leftValue' vs $rightLayer='$rightValue'. " +
        "Use overrideTags to resolve."
)
