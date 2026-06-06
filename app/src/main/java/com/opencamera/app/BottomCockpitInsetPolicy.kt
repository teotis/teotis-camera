package com.opencamera.app

internal object BottomCockpitInsetPolicy {
    fun resolveBottomPadding(
        layoutPaddingBottomPx: Int,
        navigationBarsBottomPx: Int,
        maxNavigationPaddingPx: Int
    ): Int {
        val basePadding = layoutPaddingBottomPx.coerceAtLeast(0)
        val cappedNavigationPadding = navigationBarsBottomPx
            .coerceAtLeast(0)
            .coerceAtMost(maxNavigationPaddingPx.coerceAtLeast(0))
        return maxOf(basePadding, cappedNavigationPadding)
    }
}
