package com.opencamera.core.settings

fun ManualCaptureParams.compactSummary(): String {
    return buildString {
        append("RAW ")
        append(if (rawEnabled) "On" else "Off")
        append(" | ISO ")
        append(iso?.toString() ?: "Auto")
        append(" | S ")
        append(shutterSpeedMillis?.let { "${it}ms" } ?: "Auto")
        append(" | WB ")
        append(whiteBalanceKelvin?.let { "${it}K" } ?: "Auto")
    }
}

fun ManualCaptureParams.toMetadataTags(prefix: String = "manualDraft"): Map<String, String> {
    return buildMap {
        put("${prefix}Raw", if (rawEnabled) "on" else "off")
        put("${prefix}Iso", iso?.toString() ?: "auto")
        put("${prefix}ShutterSpeedMillis", shutterSpeedMillis?.toString() ?: "auto")
        put(
            "${prefix}ExposureCompensationSteps",
            exposureCompensationSteps?.toString() ?: "auto"
        )
        put(
            "${prefix}FocusDistanceDiopters",
            focusDistanceDiopters?.toString() ?: "auto"
        )
        put("${prefix}ApertureFNumber", apertureFNumber?.toString() ?: "auto")
        put("${prefix}WhiteBalanceKelvin", whiteBalanceKelvin?.toString() ?: "auto")
    }
}
