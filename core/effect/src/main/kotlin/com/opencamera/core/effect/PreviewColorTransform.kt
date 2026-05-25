package com.opencamera.core.effect

import com.opencamera.core.settings.PreviewColorFidelity

data class PreviewColorTransform(
    val matrix: FloatArray? = null,
    val tintColor: Int = 0,
    val tintAlpha: Float = 0f,
    val fidelity: PreviewColorFidelity = PreviewColorFidelity.APPROXIMATE
) {
    val hasColorMatrix: Boolean get() = matrix != null && matrix.size == 20

    companion object {
        val NONE = PreviewColorTransform()
        val MASK_AWARE = PreviewColorTransform(fidelity = PreviewColorFidelity.MASK_AWARE)
        val FALLBACK = PreviewColorTransform(fidelity = PreviewColorFidelity.FALLBACK)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PreviewColorTransform) return false
        val matricesEqual = when {
            matrix == null && other.matrix == null -> true
            matrix != null && other.matrix != null -> matrix.contentEquals(other.matrix)
            else -> false
        }
        return matricesEqual &&
            tintColor == other.tintColor &&
            tintAlpha == other.tintAlpha &&
            fidelity == other.fidelity
    }

    override fun hashCode(): Int {
        var result = matrix?.contentHashCode() ?: 0
        result = 31 * result + tintColor
        result = 31 * result + tintAlpha.hashCode()
        result = 31 * result + fidelity.hashCode()
        return result
    }
}
