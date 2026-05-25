package com.opencamera.core.media

enum class SceneMaskRole {
    PERSON_SUBJECT,
    FOREGROUND,
    BACKGROUND,
    DEPTH_APPROXIMATION,
    SEMANTIC_REGION
}

enum class SceneMaskQuality {
    UNAVAILABLE,
    PREVIEW_APPROXIMATE,
    SAVED_PHOTO,
    DEGRADED
}

data class SceneMaskTransform(
    val sourceWidth: Int,
    val sourceHeight: Int,
    val maskWidth: Int,
    val maskHeight: Int,
    val rotationDegrees: Int,
    val mirrorHorizontally: Boolean = false,
    val cropLeft: Float = 0f,
    val cropTop: Float = 0f,
    val cropRight: Float = 1f,
    val cropBottom: Float = 1f
)

data class SceneMaskDescriptor(
    val maskId: String,
    val role: SceneMaskRole,
    val quality: SceneMaskQuality,
    val backendId: String,
    val confidence: Float,
    val transform: SceneMaskTransform,
    val diagnostics: List<String> = emptyList()
) {
    fun normalizedConfidence(): Float = confidence.coerceIn(0f, 1f)

    fun toMetadataTags(): Map<String, String> = buildMap {
        put("scene-mask:id", maskId)
        put("scene-mask:role", role.name)
        put("scene-mask:quality", quality.name)
        put("scene-mask:backend", backendId)
        put("scene-mask:confidence", normalizedConfidence().toString())
        put("scene-mask:transform",
            "${transform.sourceWidth}x${transform.sourceHeight}>" +
            "${transform.maskWidth}x${transform.maskHeight}" +
            ":rot${transform.rotationDegrees}" +
            if (transform.mirrorHorizontally) ":mirror" else "" +
            ":crop(${transform.cropLeft},${transform.cropTop},${transform.cropRight},${transform.cropBottom})"
        )
        if (diagnostics.isNotEmpty()) {
            put("scene-mask:diagnostics", diagnostics.joinToString(";"))
        }
    }

    companion object {
        fun fromMetadataTags(tags: Map<String, String>): SceneMaskDescriptor? {
            val maskId = tags["scene-mask:id"] ?: return null
            val role = tags["scene-mask:role"]?.let { value ->
                SceneMaskRole.entries.firstOrNull { it.name == value }
            } ?: return null
            val quality = tags["scene-mask:quality"]?.let { value ->
                SceneMaskQuality.entries.firstOrNull { it.name == value }
            } ?: return null
            val backendId = tags["scene-mask:backend"] ?: return null
            val confidence = tags["scene-mask:confidence"]?.toFloatOrNull() ?: 0f
            val transform = parseTransform(tags["scene-mask:transform"]) ?: return null
            val diagnostics = tags["scene-mask:diagnostics"]
                ?.split(";")
                ?.filter { it.isNotEmpty() }
                ?: emptyList()

            return SceneMaskDescriptor(
                maskId = maskId,
                role = role,
                quality = quality,
                backendId = backendId,
                confidence = confidence,
                transform = transform,
                diagnostics = diagnostics
            )
        }

        private fun parseTransform(value: String?): SceneMaskTransform? {
            if (value == null) return null
            val pattern = Regex("""(\d+)x(\d+)>(\d+)x(\d+):rot(\d+)(:mirror)?(?:.*:crop\(([\d.]+),([\d.]+),([\d.]+),([\d.]+)\))?""")
            val match = pattern.matchEntire(value) ?: return null
            return SceneMaskTransform(
                sourceWidth = match.groupValues[1].toInt(),
                sourceHeight = match.groupValues[2].toInt(),
                maskWidth = match.groupValues[3].toInt(),
                maskHeight = match.groupValues[4].toInt(),
                rotationDegrees = match.groupValues[5].toInt(),
                mirrorHorizontally = match.groupValues[6] == ":mirror",
                cropLeft = match.groupValues[7].toFloatOrNull() ?: 0f,
                cropTop = match.groupValues[8].toFloatOrNull() ?: 0f,
                cropRight = match.groupValues[9].toFloatOrNull() ?: 1f,
                cropBottom = match.groupValues[10].toFloatOrNull() ?: 1f
            )
        }
    }
}

interface SceneMaskPayload {
    val descriptor: SceneMaskDescriptor
    fun alphaAt(maskX: Int, maskY: Int): Float
}

enum class SceneMaskSupport {
    SUPPORTED,
    DEGRADED,
    UNSUPPORTED
}

data class SceneMaskCapability(
    val subjectMask: SceneMaskSupport,
    val savedPhotoMask: SceneMaskSupport,
    val previewMask: SceneMaskSupport,
    val backendId: String,
    val reason: String? = null
)

object SceneMaskPipelineNotes {
    fun backend(backendId: String): String = "scene-mask:backend=$backendId"

    fun preview(support: SceneMaskSupport): String = "scene-mask:preview=${support.toNoteValue()}"

    fun saved(support: SceneMaskSupport): String = "scene-mask:saved=${support.toNoteValue()}"

    fun reason(reason: String): String = "scene-mask:reason=$reason"

    fun staleMask(ageMs: Long, thresholdMs: Long): String =
        "scene-mask:stale:age=${ageMs}ms:threshold=${thresholdMs}ms"

    fun capabilityNotes(capability: SceneMaskCapability): List<String> = buildList {
        add(backend(capability.backendId))
        add(preview(capability.previewMask))
        add(saved(capability.savedPhotoMask))
        capability.reason?.let { add(reason(it)) }
    }

    private fun SceneMaskSupport.toNoteValue(): String = when (this) {
        SceneMaskSupport.SUPPORTED -> "applied"
        SceneMaskSupport.DEGRADED -> "degraded"
        SceneMaskSupport.UNSUPPORTED -> "unsupported"
    }
}
