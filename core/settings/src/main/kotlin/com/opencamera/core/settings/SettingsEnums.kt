package com.opencamera.core.settings

interface StorageKeyEnum {
    val storageKey: String
    val label: String
}

inline fun <reified T> storageKeyFrom(value: String?): T?
    where T : Enum<T>, T : StorageKeyEnum {
    return enumValues<T>().firstOrNull { it.storageKey == value }
}

enum class CompositionGridMode(
    override val storageKey: String,
    override val label: String
) : StorageKeyEnum {
    OFF("off", "Off"),
    RULE_OF_THIRDS("rule-of-thirds", "3x3"),
    GOLDEN_RATIO("golden-ratio", "Golden");

    companion object {
        fun fromStorageKey(value: String?): CompositionGridMode? = storageKeyFrom(value)
    }
}

enum class CountdownDuration(
    override val storageKey: String,
    val seconds: Int,
    override val label: String
) : StorageKeyEnum {
    OFF("off", 0, "Off"),
    SECONDS_3("3s", 3, "3s"),
    SECONDS_5("5s", 5, "5s"),
    SECONDS_10("10s", 10, "10s");

    companion object {
        fun fromStorageKey(value: String?): CountdownDuration? = storageKeyFrom(value)
    }
}

enum class AudioProfile(
    override val storageKey: String,
    override val label: String
) : StorageKeyEnum {
    STANDARD("standard", "Standard"),
    CONCERT("concert", "Concert");

    companion object {
        fun fromStorageKey(value: String?): AudioProfile? = storageKeyFrom(value)
    }
}

enum class VideoResolution(
    override val storageKey: String,
    override val label: String
) : StorageKeyEnum {
    UHD_8K("8k", "8K"),
    UHD_4K("4k", "4K"),
    FHD_1080P("1080p", "1080p"),
    HD_720P("720p", "720p"),
    SD_480P("480p", "480p");

    companion object {
        fun fromStorageKey(value: String?): VideoResolution? = storageKeyFrom(value)
    }
}

enum class VideoFrameRate(
    override val storageKey: String,
    val fps: Int
) : StorageKeyEnum {
    FPS_24("24", 24),
    FPS_25("25", 25),
    FPS_30("30", 30),
    FPS_60("60", 60),
    FPS_100("100", 100),
    FPS_120("120", 120);

    override val label: String
        get() = "${fps}fps"

    companion object {
        fun fromStorageKey(value: String?): VideoFrameRate? = storageKeyFrom(value)
    }
}

enum class DynamicVideoFpsPolicy(
    override val storageKey: String,
    override val label: String
) : StorageKeyEnum {
    LOCKED("locked", "Locked fps"),
    LOW_LIGHT_AUTO_24FPS("low-light-auto-24fps", "Low-light auto 24fps");

    companion object {
        fun fromStorageKey(value: String?): DynamicVideoFpsPolicy? = storageKeyFrom(value)
    }
}

enum class FilterProfileCategory {
    PHOTO,
    PORTRAIT,
    HUMANISTIC,
    CUSTOM
}

enum class LiveWatermarkMotionBehavior(
    override val storageKey: String,
    override val label: String,
    val isDynamic: Boolean,
    val brightnessCouplingKey: String,
    val opacityCouplingKey: String
) : StorageKeyEnum {
    STATIC_OVERLAY("static-overlay", "Static Overlay", false, "locked", "locked"),
    FOLLOW_FRAME_LUMA("follow-frame-luma", "Follow Frame Luma", true, "follow-frame-luma", "locked"),
    FOLLOW_FRAME_LUMA_AND_MOTION("follow-frame-luma-and-motion", "Follow Frame Luma + Motion", true, "follow-frame-luma", "follow-frame-motion");

    companion object {
        fun fromStorageKey(value: String?): LiveWatermarkMotionBehavior? = storageKeyFrom(value)
    }
}

enum class WatermarkTextPlacement(
    override val storageKey: String,
    override val label: String
) : StorageKeyEnum {
    TOP_LEFT("top-left", "Top Left"),
    TOP_RIGHT("top-right", "Top Right"),
    BOTTOM_LEFT("bottom-left", "Bottom Left"),
    BOTTOM_RIGHT("bottom-right", "Bottom Right"),
    BOTTOM_CENTER("bottom-center", "Bottom Center");

    companion object {
        fun fromStorageKey(value: String?): WatermarkTextPlacement? = storageKeyFrom(value)
    }
}

enum class WatermarkTextScale(
    override val storageKey: String,
    override val label: String,
    val multiplier: Float
) : StorageKeyEnum {
    COMPACT("compact", "Compact", 0.85f),
    NORMAL("normal", "Normal", 1f),
    LARGE("large", "Large", 1.2f);

    companion object {
        fun fromStorageKey(value: String?): WatermarkTextScale? = storageKeyFrom(value)
    }
}

enum class WatermarkTextOpacity(
    override val storageKey: String,
    override val label: String,
    val alphaFraction: Float
) : StorageKeyEnum {
    SUBTLE("subtle", "Subtle", 0.55f),
    SOFT("soft", "Soft", 0.8f),
    SOLID("solid", "Solid", 1f);

    companion object {
        fun fromStorageKey(value: String?): WatermarkTextOpacity? = storageKeyFrom(value)
    }
}

enum class WatermarkFrameBackground(
    override val storageKey: String,
    override val label: String
) : StorageKeyEnum {
    DARK("dark", "Dark"),
    WHITE("white", "White"),
    SOURCE_BLUR("source-blur", "Source Blur"),
    SOURCE_LIGHT_BLUR("source-light-blur", "Light Blur"),
    SOURCE_VIVID_BLUR("source-vivid-blur", "Vivid Blur");

    companion object {
        fun fromStorageKey(value: String?): WatermarkFrameBackground? = storageKeyFrom(value)
    }
}

enum class PortraitProfile(
    override val storageKey: String,
    override val label: String
) : StorageKeyEnum {
    NATIVE("native", "Native Portrait"),
    LUMINOUS("luminous", "Luminous Portrait");

    companion object {
        fun fromStorageKey(value: String?): PortraitProfile? = storageKeyFrom(value)
    }
}

enum class PortraitBeautyPreset(
    override val storageKey: String,
    override val label: String
) : StorageKeyEnum {
    AUTHENTIC("authentic", "Authentic"),
    CLEAR("clear", "Clear"),
    RADIANT("radiant", "Radiant");

    companion object {
        fun fromStorageKey(value: String?): PortraitBeautyPreset? = storageKeyFrom(value)
    }
}

enum class PortraitBeautyStrength(
    override val storageKey: String,
    override val label: String,
    val intensity: Float
) : StorageKeyEnum {
    OFF("off", "Off", 0f),
    SOFT("soft", "Soft", 0.35f),
    BALANCED("balanced", "Balanced", 0.6f),
    ELEVATED("elevated", "Elevated", 0.85f);

    companion object {
        fun fromStorageKey(value: String?): PortraitBeautyStrength? = storageKeyFrom(value)
    }
}

enum class PortraitBokehEffect(
    override val storageKey: String,
    override val label: String
) : StorageKeyEnum {
    NATURAL("natural", "Natural"),
    CREAMY("creamy", "Creamy"),
    DREAMY("dreamy", "Dreamy");

    companion object {
        fun fromStorageKey(value: String?): PortraitBokehEffect? = storageKeyFrom(value)
    }
}
