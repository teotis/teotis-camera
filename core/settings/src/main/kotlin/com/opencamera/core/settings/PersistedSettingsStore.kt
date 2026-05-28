package com.opencamera.core.settings

interface PersistedSettingsStore {
    fun load(): PersistedSettings

    fun save(settings: PersistedSettings)
}

class MapPersistedSettingsStore(
    private val backing: MutableMap<String, String> = linkedMapOf()
) : PersistedSettingsStore {
    override fun load(): PersistedSettings {
        return PersistedSettingsSerializer.fromMap(backing)
    }

    override fun save(settings: PersistedSettings) {
        backing.clear()
        backing.putAll(PersistedSettingsSerializer.toMap(settings))
    }

    fun snapshot(): Map<String, String> = backing.toMap()
}
