package com.opencamera.core.effect

import com.opencamera.core.settings.FilterRenderSpec

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
    val colorMatrix: FloatArray
) {
    init {
        require(colorMatrix.size == 20) { "Color matrix must have 20 elements (4x5)" }
    }

    val isIdentity: Boolean
        get() {
            // Identity: diagonal = 1, translation column = 0
            val identity = floatArrayOf(
                1f, 0f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f, 0f,
                0f, 0f, 1f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )
            return colorMatrix.contentEquals(identity)
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PreviewColorTransform) return false
        return colorMatrix.contentEquals(other.colorMatrix)
    }

    override fun hashCode(): Int = colorMatrix.contentHashCode()

    companion object {
        val IDENTITY = PreviewColorTransform(
            floatArrayOf(
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

            // 1. Saturation
            if (spec.saturation != 1f) {
                m = multiply(m, saturationMatrix(spec.saturation))
            }

            // 2. Monochrome (desaturation blend)
            if (spec.monochromeMix > 0f) {
                val effectiveSat = 1f - spec.monochromeMix
                if (effectiveSat < 1f) {
                    m = multiply(m, saturationMatrix(effectiveSat))
                }
            }

            // 3. Warmth / color temperature
            val warmth = spec.warmthShift + spec.warmBoost * 60f - spec.coolBoost * 60f
            if (warmth != 0f) {
                m = multiply(m, warmthMatrix(warmth))
            }

            // 4. Tint shift
            if (spec.tintShift != 0) {
                m = multiply(m, tintMatrix(spec.tintShift))
            }

            // 5. Brightness
            if (spec.brightnessShift != 0) {
                m = multiply(m, brightnessMatrix(spec.brightnessShift.toFloat()))
            }

            // 6. Contrast
            if (spec.contrast != 1f) {
                m = multiply(m, contrastMatrix(spec.contrast))
            }

            // 7. Shadow lift
            if (spec.shadowLift > 0f) {
                m = multiply(m, shadowLiftMatrix(spec.shadowLift))
            }

            // 8. Highlight compression
            if (spec.highlightCompression > 0f) {
                m = multiply(m, highlightCompressionMatrix(spec.highlightCompression))
            }

            return PreviewColorTransform(m)
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

        /**
         * Saturation matrix (matching android.graphics.ColorMatrix.setSaturation logic).
         * sat=0 → grayscale, sat=1 → identity.
         */
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

        /**
         * Warmth matrix: shifts red up and blue down (positive=warm, negative=cool).
         * The warmth value is in abstract units; divide by 255 for the per-channel shift.
         */
        private fun warmthMatrix(warmth: Float): FloatArray {
            val shift = warmth / 255f
            return floatArrayOf(
                1f, 0f, 0f, 0f, shift * 255f,
                0f, 1f, 0f, 0f, 0f,
                0f, 0f, 1f, 0f, -shift * 255f,
                0f, 0f, 0f, 1f, 0f
            )
        }

        /**
         * Tint matrix: shifts green vs magenta.
         * Positive tintShift → green boost; negative → magenta boost.
         */
        private fun tintMatrix(tintShift: Int): FloatArray {
            val shift = tintShift / 255f
            return floatArrayOf(
                1f, 0f, 0f, 0f, -shift * 128f,
                0f, 1f, 0f, 0f, shift * 255f,
                0f, 0f, 1f, 0f, -shift * 128f,
                0f, 0f, 0f, 1f, 0f
            )
        }

        /** Brightness matrix: adds a constant to each RGB channel. */
        private fun brightnessMatrix(brightness: Float): FloatArray {
            return floatArrayOf(
                1f, 0f, 0f, 0f, brightness,
                0f, 1f, 0f, 0f, brightness,
                0f, 0f, 1f, 0f, brightness,
                0f, 0f, 0f, 1f, 0f
            )
        }

        /** Contrast matrix: scales around midpoint 128. */
        private fun contrastMatrix(contrast: Float): FloatArray {
            val translate = 128f * (1f - contrast)
            return floatArrayOf(
                contrast, 0f, 0f, 0f, translate,
                0f, contrast, 0f, 0f, translate,
                0f, 0f, contrast, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            )
        }

        /** Shadow lift: adds brightness primarily to dark pixels via a power curve approximation. */
        private fun shadowLiftMatrix(strength: Float): FloatArray {
            // Approximate: lift shadows by adding a small value to dark pixels.
            // Using a simple additive offset scaled by strength.
            val lift = strength * 30f
            return floatArrayOf(
                1f, 0f, 0f, 0f, lift,
                0f, 1f, 0f, 0f, lift,
                0f, 0f, 1f, 0f, lift,
                0f, 0f, 0f, 1f, 0f
            )
        }

        /** Highlight compression: reduces contrast in bright areas. */
        private fun highlightCompressionMatrix(strength: Float): FloatArray {
            // Approximate: compress highlights by reducing scale and adding offset.
            val scale = 1f - strength * 0.3f
            val offset = strength * 40f
            return floatArrayOf(
                scale, 0f, 0f, 0f, offset,
                0f, scale, 0f, 0f, offset,
                0f, 0f, scale, 0f, offset,
                0f, 0f, 0f, 1f, 0f
            )
        }

        /** Multiply two 4x5 matrices (left * right). */
        internal fun multiply(left: FloatArray, right: FloatArray): FloatArray {
            val result = FloatArray(20)
            for (row in 0..3) {
                // Color channels (cols 0-3): standard matrix multiply
                for (col in 0..3) {
                    var sum = 0f
                    for (k in 0..3) {
                        sum += left[row * 5 + k] * right[k * 5 + col]
                    }
                    result[row * 5 + col] = sum
                }
                // Translation column (col 4): left[row]·right[4] + left[row][4]
                var sum = left[row * 5 + 4]
                for (k in 0..3) {
                    sum += left[row * 5 + k] * right[k * 5 + 4]
                }
                result[row * 5 + 4] = sum
            }
            return result
        }
    }
}
