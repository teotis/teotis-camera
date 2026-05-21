package com.opencamera.core.settings

enum class AppLanguage(val storageKey: String) {
    ZH("zh"),
    EN("en");

    companion object {
        fun fromStorageKey(value: String?): AppLanguage? {
            return entries.firstOrNull { it.storageKey == value }
        }
    }
}
