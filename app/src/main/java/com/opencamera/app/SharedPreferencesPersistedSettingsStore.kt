package com.opencamera.app

import android.content.Context
import com.opencamera.core.settings.PersistedSettings
import com.opencamera.core.settings.PersistedSettingsSerializer
import com.opencamera.core.settings.PersistedSettingsStore

class SharedPreferencesPersistedSettingsStore(
    context: Context,
    name: String = DEFAULT_NAME
) : PersistedSettingsStore {
    private val sharedPreferences = context.applicationContext.getSharedPreferences(
        name,
        Context.MODE_PRIVATE
    )

    override fun load(): PersistedSettings {
        val persistedValues = sharedPreferences.all.entries
            .mapNotNull { (key, value) ->
                (value as? String)?.let { key to it }
            }
            .toMap()
        return PersistedSettingsSerializer.fromMap(persistedValues)
    }

    override fun save(settings: PersistedSettings) {
        val values = PersistedSettingsSerializer.toMap(settings)
        sharedPreferences.edit().apply {
            clear()
            values.forEach { (key, value) ->
                putString(key, value)
            }
        }.apply()
    }

    companion object {
        private const val DEFAULT_NAME = "open_camera_settings"
    }
}
