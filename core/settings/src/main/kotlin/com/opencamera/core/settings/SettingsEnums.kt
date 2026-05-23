package com.opencamera.core.settings

enum class CompositionGridMode(
    val storageKey: String,
    val label: String
) {
    OFF(
        storageKey = "off",
        label = "Off"
    ),
    RULE_OF_THIRDS(
        storageKey = "rule-of-thirds",
        label = "3x3"
    ),
    GOLDEN_RATIO(
        storageKey = "golden-ratio",
        label = "Golden"
    );

    companion object {
        fun fromStorageKey(value: String?): CompositionGridMode? {
            return entries.firstOrNull { it.storageKey == value }
        }
    }
}

enum class CountdownDuration(
    val storageKey: String,
    val seconds: Int,
    val label: String
) {
    OFF(
        storageKey = "off",
        seconds = 0,
        label = "Off"
    ),
    SECONDS_3(
        storageKey = "3s",
        seconds = 3,
        label = "3s"
    ),
    SECONDS_5(
        storageKey = "5s",
        seconds = 5,
        label = "5s"
    ),
    SECONDS_10(
        storageKey = "10s",
        seconds = 10,
        label = "10s"
    );

    companion object {
        fun fromStorageKey(value: String?): CountdownDuration? {
            return entries.firstOrNull { it.storageKey == value }
        }
    }
}

enum class AudioProfile(
    val storageKey: String,
    val label: String
) {
    STANDARD(
        storageKey = "standard",
        label = "Standard"
    ),
    CONCERT(
        storageKey = "concert",
        label = "Concert"
    );

    companion object {
        fun fromStorageKey(value: String?): AudioProfile? {
            return entries.firstOrNull { it.storageKey == value }
        }
    }
}

enum class VideoResolution(
    val storageKey: String,
    val label: String
) {
    UHD_8K(
        storageKey = "8k",
        label = "8K"
    ),
    UHD_4K(
        storageKey = "4k",
        label = "4K"
    ),
    FHD_1080P(
        storageKey = "1080p",
        label = "1080p"
    ),
    HD_720P(
        storageKey = "720p",
        label = "720p"
    ),
    SD_480P(
        storageKey = "480p",
        label = "480p"
    );

    companion object {
        fun fromStorageKey(value: String?): VideoResolution? {
            return entries.firstOrNull { it.storageKey == value }
        }
    }
}

enum class VideoFrameRate(
    val storageKey: String,
    val fps: Int
) {
    FPS_24(
        storageKey = "24",
        fps = 24
    ),
    FPS_25(
        storageKey = "25",
        fps = 25
    ),
    FPS_30(
        storageKey = "30",
        fps = 30
    ),
    FPS_60(
        storageKey = "60",
        fps = 60
    ),
    FPS_100(
        storageKey = "100",
        fps = 100
    ),
    FPS_120(
        storageKey = "120",
        fps = 120
    );

    val label: String
        get() = "${fps}fps"

    companion object {
        fun fromStorageKey(value: String?): VideoFrameRate? {
            return entries.firstOrNull { it.storageKey == value }
        }
    }
}

enum class DynamicVideoFpsPolicy(
    val storageKey: String,
    val label: String
) {
    LOCKED(
        storageKey = "locked",
        label = "Locked fps"
    ),
    LOW_LIGHT_AUTO_24FPS(
        storageKey = "low-light-auto-24fps",
        label = "Low-light auto 24fps"
    );

    companion object {
        fun fromStorageKey(value: String?): DynamicVideoFpsPolicy? {
            return entries.firstOrNull { it.storageKey == value }
        }
    }
}

enum class FilterProfileCategory {
    PHOTO,
    PORTRAIT,
    HUMANISTIC,
    CUSTOM
}

enum class LiveWatermarkMotionBehavior(
    val storageKey: String,
    val label: String,
    val isDynamic: Boolean,
    val brightnessCouplingKey: String,
    val opacityCouplingKey: String
) {
    STATIC_OVERLAY(
        storageKey = "static-overlay",
        label = "Static Overlay",
        isDynamic = false,
        brightnessCouplingKey = "locked",
        opacityCouplingKey = "locked"
    ),
    FOLLOW_FRAME_LUMA(
        storageKey = "follow-frame-luma",
        label = "Follow Frame Luma",
        isDynamic = true,
        brightnessCouplingKey = "follow-frame-luma",
        opacityCouplingKey = "locked"
    ),
    FOLLOW_FRAME_LUMA_AND_MOTION(
        storageKey = "follow-frame-luma-and-motion",
        label = "Follow Frame Luma + Motion",
        isDynamic = true,
        brightnessCouplingKey = "follow-frame-luma",
        opacityCouplingKey = "follow-frame-motion"
    );

    companion object {
        fun fromStorageKey(value: String?): LiveWatermarkMotionBehavior? {
            return entries.firstOrNull { it.storageKey == value }
        }
    }
}

enum class WatermarkTextPlacement(
    val storageKey: String,
    val label: String
) {
    TOP_LEFT("top-left", "Top Left"),
    TOP_RIGHT("top-right", "Top Right"),
    BOTTOM_LEFT("bottom-left", "Bottom Left"),
    BOTTOM_RIGHT("bottom-right", "Bottom Right"),
    BOTTOM_CENTER("bottom-center", "Bottom Center");

    companion object {
        fun fromStorageKey(value: String?): WatermarkTextPlacement? {
            return entries.firstOrNull { it.storageKey == value }
        }
    }
}

enum class WatermarkTextScale(
    val storageKey: String,
    val label: String,
    val multiplier: Float
) {
    COMPACT("compact", "Compact", 0.85f),
    NORMAL("normal", "Normal", 1f),
    LARGE("large", "Large", 1.2f);

    companion object {
        fun fromStorageKey(value: String?): WatermarkTextScale? {
            return entries.firstOrNull { it.storageKey == value }
        }
    }
}

enum class WatermarkTextOpacity(
    val storageKey: String,
    val label: String,
    val alphaFraction: Float
) {
    SUBTLE("subtle", "Subtle", 0.55f),
    SOFT("soft", "Soft", 0.8f),
    SOLID("solid", "Solid", 1f);

    companion object {
        fun fromStorageKey(value: String?): WatermarkTextOpacity? {
            return entries.firstOrNull { it.storageKey == value }
        }
    }
}

enum class WatermarkFrameBackground(
    val storageKey: String,
    val label: String
) {
    DARK("dark", "Dark"),
    WHITE("white", "White"),
    SOURCE_BLUR("source-blur", "Source Blur"),
    SOURCE_LIGHT_BLUR("source-light-blur", "Light Blur"),
    SOURCE_VIVID_BLUR("source-vivid-blur", "Vivid Blur");

    companion object {
        fun fromStorageKey(value: String?): WatermarkFrameBackground? {
            return entries.firstOrNull { it.storageKey == value }
        }
    }
}

enum class PortraitProfile(
    val storageKey: String,
    val label: String
) {
    NATIVE(
        storageKey = "native",
        label = "Native Portrait"
    ),
    LUMINOUS(
        storageKey = "luminous",
        label = "Luminous Portrait"
    );

    companion object {
        fun fromStorageKey(value: String?): PortraitProfile? {
            return entries.firstOrNull { it.storageKey == value }
        }
    }
}

enum class PortraitBeautyPreset(
    val storageKey: String,
    val label: String
) {
    AUTHENTIC(
        storageKey = "authentic",
        label = "Authentic"
    ),
    CLEAR(
        storageKey = "clear",
        label = "Clear"
    ),
    RADIANT(
        storageKey = "radiant",
        label = "Radiant"
    );

    companion object {
        fun fromStorageKey(value: String?): PortraitBeautyPreset? {
            return entries.firstOrNull { it.storageKey == value }
        }
    }
}

enum class PortraitBeautyStrength(
    val storageKey: String,
    val label: String,
    val intensity: Float
) {
    OFF(
        storageKey = "off",
        label = "Off",
        intensity = 0f
    ),
    SOFT(
        storageKey = "soft",
        label = "Soft",
        intensity = 0.35f
    ),
    BALANCED(
        storageKey = "balanced",
        label = "Balanced",
        intensity = 0.6f
    ),
    ELEVATED(
        storageKey = "elevated",
        label = "Elevated",
        intensity = 0.85f
    );

    companion object {
        fun fromStorageKey(value: String?): PortraitBeautyStrength? {
            return entries.firstOrNull { it.storageKey == value }
        }
    }
}

enum class PortraitBokehEffect(
    val storageKey: String,
    val label: String
) {
    NATURAL(
        storageKey = "natural",
        label = "Natural"
    ),
    CREAMY(
        storageKey = "creamy",
        label = "Creamy"
    ),
    DREAMY(
        storageKey = "dreamy",
        label = "Dreamy"
    );

    companion object {
        fun fromStorageKey(value: String?): PortraitBokehEffect? {
            return entries.firstOrNull { it.storageKey == value }
        }
    }
}
