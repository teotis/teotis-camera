package com.opencamera.core.settings

internal object FilterRenderSpecCodec {

    const val DEFAULT_METADATA_PREFIX: String = "filterSpec"
    private const val METADATA_VERSION_VALUE: String = "1"

    private val FIELDS: List<Field<*>> = listOf(
        IntField(
            name = "brightnessShift",
            default = 0,
            read = { it.brightnessShift },
            write = { spec, v -> spec.copy(brightnessShift = v) }
        ),
        FloatField(
            name = "contrast",
            default = 1f,
            read = { it.contrast },
            write = { spec, v -> spec.copy(contrast = v) }
        ),
        FloatField(
            name = "saturation",
            default = 1f,
            read = { it.saturation },
            write = { spec, v -> spec.copy(saturation = v) }
        ),
        IntField(
            name = "warmthShift",
            default = 0,
            read = { it.warmthShift },
            write = { spec, v -> spec.copy(warmthShift = v) }
        ),
        IntField(
            name = "tintShift",
            default = 0,
            read = { it.tintShift },
            write = { spec, v -> spec.copy(tintShift = v) }
        ),
        FloatField(
            name = "monochromeMix",
            default = 0f,
            read = { it.monochromeMix },
            write = { spec, v -> spec.copy(monochromeMix = v) }
        ),
        FloatField(
            name = "vignetteStrength",
            default = 0f,
            read = { it.vignetteStrength },
            write = { spec, v -> spec.copy(vignetteStrength = v) }
        ),
        FloatField(
            name = "softGlowStrength",
            default = 0f,
            read = { it.softGlowStrength },
            write = { spec, v -> spec.copy(softGlowStrength = v) }
        ),
        FloatField(
            name = "haloStrength",
            default = 0f,
            read = { it.haloStrength },
            write = { spec, v -> spec.copy(haloStrength = v) }
        ),
        FloatField(
            name = "grainStrength",
            default = 0f,
            read = { it.grainStrength },
            write = { spec, v -> spec.copy(grainStrength = v) }
        ),
        FloatField(
            name = "sharpnessBoost",
            default = 0f,
            read = { it.sharpnessBoost },
            write = { spec, v -> spec.copy(sharpnessBoost = v) }
        ),
        FloatField(
            name = "highlightCompression",
            default = 0f,
            read = { it.highlightCompression },
            write = { spec, v -> spec.copy(highlightCompression = v) }
        ),
        FloatField(
            name = "shadowLift",
            default = 0f,
            read = { it.shadowLift },
            write = { spec, v -> spec.copy(shadowLift = v) }
        ),
        FloatField(
            name = "warmBoost",
            default = 0f,
            read = { it.warmBoost },
            write = { spec, v -> spec.copy(warmBoost = v) }
        ),
        FloatField(
            name = "coolBoost",
            default = 0f,
            read = { it.coolBoost },
            write = { spec, v -> spec.copy(coolBoost = v) }
        )
    )

    fun toMetadataTags(
        spec: FilterRenderSpec,
        prefix: String = DEFAULT_METADATA_PREFIX
    ): Map<String, String> {
        return linkedMapOf<String, String>().apply {
            put("$prefix.version", METADATA_VERSION_VALUE)
            FIELDS.forEach { field ->
                put("$prefix.${field.name}", field.encode(spec))
            }
        }
    }

    fun fromMetadataTags(
        tags: Map<String, String>,
        prefix: String = DEFAULT_METADATA_PREFIX
    ): FilterRenderSpec? {
        if (tags["$prefix.version"] == null) {
            return null
        }
        var spec = FilterRenderSpec()
        for (field in FIELDS) {
            spec = field.applyParsed(spec, tags["$prefix.${field.name}"])
        }
        return spec
    }

    fun toShareLines(spec: FilterRenderSpec): List<Pair<String, String>> {
        return FIELDS.map { it.name to it.encode(spec) }
    }

    fun fromShareValues(values: Map<String, String>): FilterRenderSpec {
        var spec = FilterRenderSpec()
        for (field in FIELDS) {
            spec = field.applyParsed(spec, values[field.name])
        }
        return spec
    }

    private sealed class Field<T>(
        val name: String,
        val default: T,
        val read: (FilterRenderSpec) -> T,
        val write: (FilterRenderSpec, T) -> FilterRenderSpec
    ) {
        fun encode(spec: FilterRenderSpec): String = read(spec).toString()

        fun applyParsed(spec: FilterRenderSpec, raw: String?): FilterRenderSpec {
            return write(spec, parseOrDefault(raw))
        }

        protected abstract fun parseOrDefault(raw: String?): T
    }

    private class IntField(
        name: String,
        default: Int,
        read: (FilterRenderSpec) -> Int,
        write: (FilterRenderSpec, Int) -> FilterRenderSpec
    ) : Field<Int>(name, default, read, write) {
        override fun parseOrDefault(raw: String?): Int = raw?.toIntOrNull() ?: default
    }

    private class FloatField(
        name: String,
        default: Float,
        read: (FilterRenderSpec) -> Float,
        write: (FilterRenderSpec, Float) -> FilterRenderSpec
    ) : Field<Float>(name, default, read, write) {
        override fun parseOrDefault(raw: String?): Float = raw?.toFloatOrNull() ?: default
    }
}
