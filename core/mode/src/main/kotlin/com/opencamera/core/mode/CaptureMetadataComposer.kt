package com.opencamera.core.mode

/**
 * Explicit metadata layer composition with collision detection.
 *
 * Layers are merged in order: effectTags → captureAidTags → modeTags.
 * A key may appear in multiple layers only if the value is identical.
 * Differing values among the first three layers are allowed only when
 * the key is also present in [overrideTags], which unconditionally resolves the conflict.
 * [overrideTags] also add keys not present in any other layer.
 *
 * Output order is deterministic: effectTags first, then captureAidTags, then modeTags,
 * then overrideKeys for keys not already present.
 */
data class CaptureMetadataLayers(
    val effectTags: Map<String, String> = emptyMap(),
    val captureAidTags: Map<String, String> = emptyMap(),
    val modeTags: Map<String, String> = emptyMap(),
    val overrideTags: Map<String, String> = emptyMap()
) {
    fun compose(): Map<String, String> {
        validateCollisions()

        val result = LinkedHashMap<String, String>()
        mergeLayer(result, effectTags)
        mergeLayer(result, captureAidTags)
        mergeLayer(result, modeTags)

        for ((key, value) in overrideTags) {
            result[key] = value
        }
        return result
    }

    private fun validateCollisions() {
        val layers = listOf(
            "effect" to effectTags,
            "captureAid" to captureAidTags,
            "mode" to modeTags
        )

        val valueByKey = mutableMapOf<String, MutableMap<String, String>>()

        for ((layerName, layer) in layers) {
            for ((key, value) in layer) {
                if (key in overrideTags) continue
                val layerValues = valueByKey.getOrPut(key) { mutableMapOf() }
                val existingValue = layerValues.values.firstOrNull()
                if (existingValue != null && existingValue != value) {
                    val existingLayer = layerValues.keys.first()
                    throw MetadataCollision(
                        key = key,
                        leftValue = existingValue,
                        rightValue = value,
                        leftLayer = existingLayer,
                        rightLayer = layerName
                    )
                }
                layerValues[layerName] = value
            }
        }
    }

    private fun mergeLayer(
        target: MutableMap<String, String>,
        layer: Map<String, String>
    ) {
        for ((key, value) in layer) {
            target[key] = value
        }
    }
}
