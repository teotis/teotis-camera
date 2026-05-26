package com.opencamera.core.effect

import com.opencamera.core.settings.FilterRenderSpec
import com.opencamera.core.settings.PreviewColorFidelity

/**
 * Builds a 4x5 color matrix from [FilterRenderSpec] for preview color transformation.
 *
 * The matrix encodes saturation, contrast, brightness, warmth, tint, monochrome,
 * shadow lift, and highlight compression. It can be used as an Android
 * [android.graphics.ColorMatrixColorFilter] on API targets that support it.
 *
 * Vignette, soft glow, halo, grain, and sharpness are NOT encoded in the matrix
 * (they require per-pixel spatial operations).
 */
data class PreviewColorTransform(
    val matrix: FloatArray? = null,
    val tintColor: Int = 0,
    val tintAlpha: Float = 0f,
    val fidelity: PreviewColorFidelity = PreviewColorFidelity.APPROXIMATE
) {
    val hasColorMatrix: Boolean get() = matrix != null && matrix.size == 20

    val isIdentity: Boolean
        get() {
            if (matrix == null) return true
            if (matrix.size != 20) return false
            val identity = floatArrayOf(
                1f, 0f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f, 0f,
                0f, 0f, 1f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )
            return matrix.contentEquals(identity)
        }

    companion object {
        val NONE = PreviewColorTransform()
        val MASK_AWARE = PreviewColorTransform(fidelity = PreviewColorFidelity.MASK_AWARE)
        val FALLBACK = PreviewColorTransform(fidelity = PreviewColorFidelity.FALLBACK)

        val IDENTITY = PreviewColorTransform(
            matrix = floatArrayOf(
                1f, 0f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f, 0f,
                0f, 0f, 1f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )
        )

        /**
         * Build a color matrix from the given [FilterRenderSpec].
         *
         * Returns [IDENTITY] when [spec] is null or represents no visual change.
         */
        fun fromSpec(spec: FilterRenderSpec?): PreviewColorTransform {
            if (spec == null) return IDENTITY
            if (isNoOp(spec)) return IDENTITY

            var m = identityMatrix()

            if (spec.saturation != 1f) {
                m = multiply(m, saturationMatrix(spec.saturation))
            }

            if (spec.monochromeMix > 0f) {
                val effectiveSat = 1f - spec.monochromeMix
                if (effectiveSat < 1f) {
                    m = multiply(m, saturationMatrix(effectiveSat))
                }
            }

            val warmth = spec.warmthShift + spec.warmBoost * 60f - spec.coolBoost * 60f
            if (warmth != 0f) {
                m = multiply(m, warmthMatrix(warmth))
            }

            if (spec.tintShift != 0) {
                m = multiply(m, tintMatrix(spec.tintShift))
            }

            if (spec.brightnessShift != 0) {
                m = multiply(m, brightnessMatrix(spec.brightnessShift.toFloat()))
            }

            if (spec.contrast != 1f) {
                m = multiply(m, contrastMatrix(spec.contrast))
            }

            if (spec.shadowLift > 0f) {
                m = multiply(m, shadowLiftMatrix(spec.shadowLift))
            }

            if (spec.highlightCompression > 0f) {
                m = multiply(m, highlightCompressionMatrix(spec.highlightCompression))
            }

            return PreviewColorTransform(matrix = m)
        }

        private fun isNoOp(spec: FilterRenderSpec): Boolean {
            return spec.brightnessShift == 0 &&
                spec.contrast == 1f &&
                spec.saturation == 1f &&
                spec.warmthShift == 0 &&
                spec.tintShift == 0 &&
                spec.monochromeMix == 0f &&
                spec.shadowLift == 0f &&
                spec.highlightCompression == 0f &&
                spec.warmBoost == 0f &&
                spec.coolBoost == 0f
        }

        private fun identityMatrix(): FloatArray = floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )

        private fun saturationMatrix(sat: Float): FloatArray {
            val invSat = 1f - sat
            val r = 0.213f * invSat
            val g = 0.715f * invSat
            val b = 0.072f * invSat
            return floatArrayOf(
                r + sat, g, b, 0f, 0f,
                r, g + sat, b, 0f, 0f,
                r, g, b + sat, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )
        }

        private fun warmthMatrix(warmth: Float): FloatArray {
            val shift = warmth / 255f
            return floatArrayOf(
                1f, 0f, 0f, 0f, shift * 255f,
                0f, 1f, 0f, 0f, 0f,
                0f, 0f, 1f, 0f, -shift * 255f,
                0f, 0f, 0f, 1f, 0f
            )
        }

        private fun tintMatrix(tintShift: Int): FloatArray {
            val shift = tintShift / 255f
            return floatArrayOf(
                1f, 0f, 0f, 0f, -shift * 128f,
                0f, 1f, 0f, 0f, shift * 255f,
                0f, 0f, 1f, 0f, -shift * 128f,
                0f, 0f, 0f, 1f, 0f
            )
        }

        private fun brightnessMatrix(brightness: Float): FloatArray {
            return floatArrayOf(
                1f, 0f, 0f, 0f, brightness,
                0f, 1f, 0f, 0f, brightness,
                0f, 0f, 1f, 0f, brightness,
                0f, 0f, 0f, 1f, 0f
            )
        }

        private fun contrastMatrix(contrast: Float): FloatArray {
            val translate = 128f * (1f - contrast)
            return floatArrayOf(
                contrast, 0f, 0f, 0f, translate,
                0f, contrast, 0f, 0f, translate,
                0f, 0f, contrast, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            )
        }

        private fun shadowLiftMatrix(strength: Float): FloatArray {
            val lift = strength * 30f
            return floatArrayOf(
                1f, 0f, 0f, 0f, lift,
                0f, 1f, 0f, 0f, lift,
                0f, 0f, 1f, 0f, lift,
                0f, 0f, 0f, 1f, 0f
            )
        }

        private fun highlightCompressionMatrix(strength: Float): FloatArray {
            val scale = 1f - strength * 0.3f
            val offset = strength * 40f
            return floatArrayOf(
                scale, 0f, 0f, 0f, offset,
                0f, scale, 0f, 0f, offset,
                0f, 0f, scale, 0f, offset,
                0f, 0f, 0f, 1f, 0f
            )
        }

        internal fun multiply(left: FloatArray, right: FloatArray): FloatArray {
            val result = FloatArray(20)
            for (row in 0..3) {
                for (col in 0..3) {
                    var sum = 0f
                    for (k in 0..3) {
                        sum += left[row * 5 + k] * right[k * 5 + col]
                    }
                    result[row * 5 + col] = sum
                }
                var sum = left[row * 5 + 4]
                for (k in 0..3) {
                    sum += left[row * 5 + k] * right[k * 5 + 4]
                }
                result[row * 5 + 4] = sum
            }
            return result
        }
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
