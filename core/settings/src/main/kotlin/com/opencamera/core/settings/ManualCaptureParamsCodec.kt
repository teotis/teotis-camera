package com.opencamera.core.settings

internal object ManualCaptureParamsCodec {

    const val DEFAULT_METADATA_PREFIX: String = "manualDraft"
    private const val AUTO_PLACEHOLDER: String = "auto"

    private val FIELDS: List<Field<*>> = listOf(
        BooleanField(
            metadataSuffix = "Raw",
            draftKey = "rawEnabled",
            default = false,
            read = { it.rawEnabled },
            write = { params, value -> params.copy(rawEnabled = value) }
        ),
        OptionalIntField(
            metadataSuffix = "Iso",
            draftKey = "iso",
            read = { it.iso },
            write = { params, value -> params.copy(iso = value) }
        ),
        OptionalLongField(
            metadataSuffix = "ShutterSpeedMillis",
            draftKey = "shutterSpeedMillis",
            read = { it.shutterSpeedMillis },
            write = { params, value -> params.copy(shutterSpeedMillis = value) }
        ),
        OptionalIntField(
            metadataSuffix = "ExposureCompensationSteps",
            draftKey = "exposureCompensationSteps",
            read = { it.exposureCompensationSteps },
            write = { params, value -> params.copy(exposureCompensationSteps = value) }
        ),
        OptionalFloatField(
            metadataSuffix = "FocusDistanceDiopters",
            draftKey = "focusDistanceDiopters",
            read = { it.focusDistanceDiopters },
            write = { params, value -> params.copy(focusDistanceDiopters = value) }
        ),
        OptionalFloatField(
            metadataSuffix = "ApertureFNumber",
            draftKey = "apertureFNumber",
            read = { it.apertureFNumber },
            write = { params, value -> params.copy(apertureFNumber = value) }
        ),
        OptionalIntField(
            metadataSuffix = "WhiteBalanceKelvin",
            draftKey = "whiteBalanceKelvin",
            read = { it.whiteBalanceKelvin },
            write = { params, value -> params.copy(whiteBalanceKelvin = value) }
        )
    )

    fun toMetadataTags(
        params: ManualCaptureParams,
        prefix: String = DEFAULT_METADATA_PREFIX
    ): Map<String, String> {
        return linkedMapOf<String, String>().apply {
            FIELDS.forEach { field ->
                put("$prefix${field.metadataSuffix}", field.encodeMetadata(params))
            }
        }
    }

    fun toDraftLines(params: ManualCaptureParams): List<Pair<String, String>> {
        return FIELDS.map { it.draftKey to it.encodeDraft(params) }
    }

    fun fromDraftValues(values: Map<String, String>): ManualCaptureParams {
        var params = ManualCaptureParams()
        for (field in FIELDS) {
            params = field.applyParsed(params, values[field.draftKey])
        }
        return params
    }

    fun compactSummary(params: ManualCaptureParams): String {
        return buildString {
            append("RAW ")
            append(if (params.rawEnabled) "On" else "Off")
            append(" | ISO ")
            append(params.iso?.toString() ?: "Auto")
            append(" | S ")
            append(params.shutterSpeedMillis?.let { "${it}ms" } ?: "Auto")
            append(" | WB ")
            append(params.whiteBalanceKelvin?.let { "${it}K" } ?: "Auto")
        }
    }

    private sealed class Field<T>(
        val metadataSuffix: String,
        val draftKey: String,
        val read: (ManualCaptureParams) -> T,
        val write: (ManualCaptureParams, T) -> ManualCaptureParams
    ) {
        abstract fun encodeMetadata(params: ManualCaptureParams): String
        abstract fun encodeDraft(params: ManualCaptureParams): String

        fun applyParsed(
            params: ManualCaptureParams,
            raw: String?
        ): ManualCaptureParams = write(params, parseOrDefault(raw))

        protected abstract fun parseOrDefault(raw: String?): T
    }

    private class BooleanField(
        metadataSuffix: String,
        draftKey: String,
        private val default: Boolean,
        read: (ManualCaptureParams) -> Boolean,
        write: (ManualCaptureParams, Boolean) -> ManualCaptureParams
    ) : Field<Boolean>(metadataSuffix, draftKey, read, write) {
        override fun encodeMetadata(params: ManualCaptureParams): String =
            if (read(params)) "on" else "off"

        override fun encodeDraft(params: ManualCaptureParams): String =
            read(params).toString()

        override fun parseOrDefault(raw: String?): Boolean =
            raw?.toBooleanStrictOrNull() ?: default
    }

    private abstract class OptionalNumericField<T : Any>(
        metadataSuffix: String,
        draftKey: String,
        read: (ManualCaptureParams) -> T?,
        write: (ManualCaptureParams, T?) -> ManualCaptureParams
    ) : Field<T?>(metadataSuffix, draftKey, read, write) {
        override fun encodeMetadata(params: ManualCaptureParams): String =
            read(params)?.toString() ?: AUTO_PLACEHOLDER

        override fun encodeDraft(params: ManualCaptureParams): String =
            read(params)?.toString() ?: AUTO_PLACEHOLDER

        override fun parseOrDefault(raw: String?): T? =
            raw?.takeUnless { it == AUTO_PLACEHOLDER }?.let(::parseValue)

        protected abstract fun parseValue(raw: String): T?
    }

    private class OptionalIntField(
        metadataSuffix: String,
        draftKey: String,
        read: (ManualCaptureParams) -> Int?,
        write: (ManualCaptureParams, Int?) -> ManualCaptureParams
    ) : OptionalNumericField<Int>(metadataSuffix, draftKey, read, write) {
        override fun parseValue(raw: String): Int? = raw.toIntOrNull()
    }

    private class OptionalLongField(
        metadataSuffix: String,
        draftKey: String,
        read: (ManualCaptureParams) -> Long?,
        write: (ManualCaptureParams, Long?) -> ManualCaptureParams
    ) : OptionalNumericField<Long>(metadataSuffix, draftKey, read, write) {
        override fun parseValue(raw: String): Long? = raw.toLongOrNull()
    }

    private class OptionalFloatField(
        metadataSuffix: String,
        draftKey: String,
        read: (ManualCaptureParams) -> Float?,
        write: (ManualCaptureParams, Float?) -> ManualCaptureParams
    ) : OptionalNumericField<Float>(metadataSuffix, draftKey, read, write) {
        override fun parseValue(raw: String): Float? = raw.toFloatOrNull()
    }
}
