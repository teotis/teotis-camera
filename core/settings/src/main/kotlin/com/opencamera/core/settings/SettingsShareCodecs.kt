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
            FilterRenderSpecCodec.toShareLines(renderSpec).forEach { (key, value) ->
                appendLine("$key=$value")
            }
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
        val categoryValue = values.requireFilterProfileField("category")
        return FilterProfile(
            id = values.requireFilterProfileField("id"),
            label = values.requireFilterProfileField("label"),
            category = runCatching {
                FilterProfileCategory.valueOf(categoryValue)
            }.getOrElse { throw IllegalArgumentException("Unknown filter profile category: $categoryValue", it) },
            builtIn = values["builtIn"]?.toBooleanStrictOrNull() ?: false,
            renderSpec = FilterRenderSpecCodec.fromShareValues(values)
        )
    }

    private fun Map<String, String>.requireFilterProfileField(key: String): String =
        this[key] ?: throw IllegalArgumentException("Missing required filter profile field: $key")
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
            ManualCaptureParamsCodec.toDraftLines(params).forEach { (key, value) ->
                appendLine("$key=$value")
            }
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
        return ManualCaptureParamsCodec.fromDraftValues(values)
    }
}
