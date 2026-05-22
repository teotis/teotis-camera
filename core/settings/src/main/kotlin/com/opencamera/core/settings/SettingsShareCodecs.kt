package com.opencamera.core.settings

object FilterProfileShareCodec {
    private const val HEADER = "OPEN_CAMERA_FILTER_PROFILE_V1"

    fun export(profile: FilterProfile): String {
        val renderSpec = profile.renderSpec ?: FilterRenderSpec()
        return buildString {
            appendLine(HEADER)
            appendLine("id=${profile.id}")
            appendLine("label=${profile.label}")
            appendLine("category=${profile.category.name}")
            appendLine("builtIn=${profile.builtIn}")
            appendLine("brightnessShift=${renderSpec.brightnessShift}")
            appendLine("contrast=${renderSpec.contrast}")
            appendLine("saturation=${renderSpec.saturation}")
            appendLine("warmthShift=${renderSpec.warmthShift}")
            appendLine("tintShift=${renderSpec.tintShift}")
            appendLine("monochromeMix=${renderSpec.monochromeMix}")
            appendLine("vignetteStrength=${renderSpec.vignetteStrength}")
            appendLine("softGlowStrength=${renderSpec.softGlowStrength}")
            appendLine("haloStrength=${renderSpec.haloStrength}")
            appendLine("grainStrength=${renderSpec.grainStrength}")
            appendLine("sharpnessBoost=${renderSpec.sharpnessBoost}")
            appendLine("highlightCompression=${renderSpec.highlightCompression}")
            appendLine("shadowLift=${renderSpec.shadowLift}")
            appendLine("warmBoost=${renderSpec.warmBoost}")
            appendLine("coolBoost=${renderSpec.coolBoost}")
        }
    }

    fun import(serialized: String): FilterProfile {
        val lines = serialized.lineSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .toList()
        require(lines.firstOrNull() == HEADER) {
            "Invalid filter profile share header"
        }
        val values = lines.drop(1).associate { line ->
            val separatorIndex = line.indexOf('=')
            require(separatorIndex > 0) {
                "Malformed filter profile share line: $line"
            }
            line.substring(0, separatorIndex) to line.substring(separatorIndex + 1)
        }
        return FilterProfile(
            id = values.getValue("id"),
            label = values.getValue("label"),
            category = runCatching {
                FilterProfileCategory.valueOf(values.getValue("category"))
            }.getOrElse { error("Unknown filter profile category: ${values["category"]}") },
            builtIn = values["builtIn"]?.toBooleanStrictOrNull() ?: false,
            renderSpec = FilterRenderSpec(
                brightnessShift = values["brightnessShift"]?.toIntOrNull() ?: 0,
                contrast = values["contrast"]?.toFloatOrNull() ?: 1f,
                saturation = values["saturation"]?.toFloatOrNull() ?: 1f,
                warmthShift = values["warmthShift"]?.toIntOrNull() ?: 0,
                tintShift = values["tintShift"]?.toIntOrNull() ?: 0,
                monochromeMix = values["monochromeMix"]?.toFloatOrNull() ?: 0f,
                vignetteStrength = values["vignetteStrength"]?.toFloatOrNull() ?: 0f,
                softGlowStrength = values["softGlowStrength"]?.toFloatOrNull() ?: 0f,
                haloStrength = values["haloStrength"]?.toFloatOrNull() ?: 0f,
                grainStrength = values["grainStrength"]?.toFloatOrNull() ?: 0f,
                sharpnessBoost = values["sharpnessBoost"]?.toFloatOrNull() ?: 0f,
                highlightCompression = values["highlightCompression"]?.toFloatOrNull() ?: 0f,
                shadowLift = values["shadowLift"]?.toFloatOrNull() ?: 0f,
                warmBoost = values["warmBoost"]?.toFloatOrNull() ?: 0f,
                coolBoost = values["coolBoost"]?.toFloatOrNull() ?: 0f
            )
        )
    }
}

object ImportedFilterProfilesSerializer {
    private const val DELIMITER = "\n<<<OPEN_CAMERA_IMPORTED_FILTER>>>\n"

    fun serialize(profiles: List<FilterProfile>): String {
        return profiles
            .filterNot(FilterProfile::builtIn)
            .joinToString(separator = DELIMITER) { profile ->
                FilterProfileShareCodec.export(profile).trim()
            }
    }

    fun deserialize(serialized: String): List<FilterProfile> {
        return serialized
            .trim()
            .takeIf(String::isNotEmpty)
            ?.split(DELIMITER)
            ?.map { chunk -> FilterProfileShareCodec.import(chunk.trim()) }
            ?.map { profile -> profile.copy(builtIn = false) }
            ?: emptyList()
    }
}

object ManualCaptureDraftSerializer {
    fun serialize(params: ManualCaptureParams): String {
        return buildString {
            appendLine("rawEnabled=${params.rawEnabled}")
            appendLine("iso=${params.iso ?: "auto"}")
            appendLine("shutterSpeedMillis=${params.shutterSpeedMillis ?: "auto"}")
            appendLine(
                "exposureCompensationSteps=${params.exposureCompensationSteps ?: "auto"}"
            )
            appendLine(
                "focusDistanceDiopters=${params.focusDistanceDiopters ?: "auto"}"
            )
            appendLine("apertureFNumber=${params.apertureFNumber ?: "auto"}")
            appendLine("whiteBalanceKelvin=${params.whiteBalanceKelvin ?: "auto"}")
        }.trim()
    }

    fun deserialize(serialized: String?): ManualCaptureParams {
        val values = mutableMapOf<String, String>()
        serialized
            ?.lineSequence()
            ?.map(String::trim)
            ?.filter(String::isNotEmpty)
            ?.forEach { line ->
                val separatorIndex = line.indexOf('=')
                if (separatorIndex > 0) {
                    values[line.substring(0, separatorIndex)] =
                        line.substring(separatorIndex + 1)
                }
            }
        return ManualCaptureParams(
            rawEnabled = values["rawEnabled"]?.toBooleanStrictOrNull() ?: false,
            iso = values["iso"].decodeAutoInt(),
            shutterSpeedMillis = values["shutterSpeedMillis"].decodeAutoLong(),
            exposureCompensationSteps = values["exposureCompensationSteps"].decodeAutoInt(),
            focusDistanceDiopters = values["focusDistanceDiopters"].decodeAutoFloat(),
            apertureFNumber = values["apertureFNumber"].decodeAutoFloat(),
            whiteBalanceKelvin = values["whiteBalanceKelvin"].decodeAutoInt()
        )
    }
}

private fun String?.decodeAutoInt(): Int? = this?.takeUnless { it == "auto" }?.toIntOrNull()
private fun String?.decodeAutoLong(): Long? = this?.takeUnless { it == "auto" }?.toLongOrNull()
private fun String?.decodeAutoFloat(): Float? = this?.takeUnless { it == "auto" }?.toFloatOrNull()
