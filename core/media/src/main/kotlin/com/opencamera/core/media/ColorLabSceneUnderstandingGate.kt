package com.opencamera.core.media

data class ColorLabSceneUnderstandingSample(
    val sampleId: String,
    val eligibleForRegionAwareColor: Boolean,
    val regionAwareColorApplied: Boolean,
    val neutralColorDamaged: Boolean = false,
    val skinColorDamaged: Boolean = false
)

data class ColorLabSceneUnderstandingGateResult(
    val eligibleCount: Int,
    val ineligibleCount: Int,
    val eligibleApplyRate: Float,
    val falseApplyRate: Float,
    val neutralDamageRate: Float,
    val skinDamageRate: Float,
    val positiveGatePassed: Boolean,
    val safetyGatePassed: Boolean,
    val readyForDefaultStage3: Boolean
)

object ColorLabSceneUnderstandingGate {
    const val MIN_ELIGIBLE_APPLY_RATE: Float = 0.90f
    const val MAX_FALSE_APPLY_RATE: Float = 0.01f
    const val MAX_DAMAGE_RATE: Float = 0.01f

    fun evaluate(samples: List<ColorLabSceneUnderstandingSample>): ColorLabSceneUnderstandingGateResult {
        val eligible = samples.filter { it.eligibleForRegionAwareColor }
        val ineligible = samples.filterNot { it.eligibleForRegionAwareColor }
        val eligibleApplyRate = ratio(
            numerator = eligible.count { it.regionAwareColorApplied },
            denominator = eligible.size
        )
        val falseApplyRate = ratio(
            numerator = ineligible.count { it.regionAwareColorApplied },
            denominator = ineligible.size
        )
        val neutralDamageRate = ratio(
            numerator = samples.count { it.neutralColorDamaged },
            denominator = samples.size
        )
        val skinDamageRate = ratio(
            numerator = samples.count { it.skinColorDamaged },
            denominator = samples.size
        )
        val positiveGatePassed = eligible.isNotEmpty() && eligibleApplyRate >= MIN_ELIGIBLE_APPLY_RATE
        val safetyGatePassed = ineligible.isNotEmpty() &&
            falseApplyRate <= MAX_FALSE_APPLY_RATE &&
            neutralDamageRate <= MAX_DAMAGE_RATE &&
            skinDamageRate <= MAX_DAMAGE_RATE

        return ColorLabSceneUnderstandingGateResult(
            eligibleCount = eligible.size,
            ineligibleCount = ineligible.size,
            eligibleApplyRate = eligibleApplyRate,
            falseApplyRate = falseApplyRate,
            neutralDamageRate = neutralDamageRate,
            skinDamageRate = skinDamageRate,
            positiveGatePassed = positiveGatePassed,
            safetyGatePassed = safetyGatePassed,
            readyForDefaultStage3 = positiveGatePassed && safetyGatePassed
        )
    }

    private fun ratio(numerator: Int, denominator: Int): Float {
        if (denominator <= 0) return 0f
        return numerator.toFloat() / denominator.toFloat()
    }
}
