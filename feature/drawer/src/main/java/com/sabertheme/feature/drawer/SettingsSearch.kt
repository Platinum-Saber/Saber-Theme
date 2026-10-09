package com.sabertheme.feature.drawer

import android.content.Intent
import android.provider.Settings

/** A system settings page reachable from search. [action] is an `Intent` action. */
data class SettingShortcut(val title: String, val section: String, val action: String, val keywords: List<String>)

internal object SettingsSearch {
    /** Not in the SDK, but handled by AOSP and One UI; falls back to the settings root. */
    const val ACTION_NOTIFICATIONS = "android.settings.NOTIFICATION_SETTINGS"

    val entries = listOf(
        SettingShortcut("Wi-Fi", "Connections", Settings.ACTION_WIFI_SETTINGS, listOf("wifi", "wireless", "network", "internet", "hotspot")),
        SettingShortcut("Bluetooth", "Connections", Settings.ACTION_BLUETOOTH_SETTINGS, listOf("pair", "headphones", "buds", "speaker")),
        SettingShortcut("Display", "Display", Settings.ACTION_DISPLAY_SETTINGS, listOf("brightness", "screen", "dark mode", "font", "refresh rate")),
        SettingShortcut("Battery", "Battery", Intent.ACTION_POWER_USAGE_SUMMARY, listOf("power", "charging", "usage", "saver")),
        SettingShortcut("Sound", "Sounds and vibration", Settings.ACTION_SOUND_SETTINGS, listOf("volume", "ringtone", "vibration", "mute")),
        SettingShortcut("Notifications", "Notifications", ACTION_NOTIFICATIONS, listOf("alerts", "do not disturb", "badges")),
        SettingShortcut("Apps", "Apps", Settings.ACTION_APPLICATION_SETTINGS, listOf("applications", "uninstall", "permissions", "storage")),
        SettingShortcut("Location", "Location", Settings.ACTION_LOCATION_SOURCE_SETTINGS, listOf("gps", "position")),
        SettingShortcut("Security", "Security and privacy", Settings.ACTION_SECURITY_SETTINGS, listOf("lock screen", "fingerprint", "face", "password", "pin", "privacy")),
        SettingShortcut("About phone", "About phone", Settings.ACTION_DEVICE_INFO_SETTINGS, listOf("device", "software", "version", "model", "build")),
        SettingShortcut("Default home app", "Apps", Settings.ACTION_HOME_SETTINGS, listOf("launcher", "home screen", "default apps")),
    )

    /**
     * Every query word must prefix-match a word of the title or keywords.
     * Title prefix > title word > keyword; ties keep list order.
     */
    fun search(query: String, limit: Int = 3): List<SettingShortcut> {
        val q = AppSearch.normalize(query).replace(Regex("\\s+"), " ")
        val tokens = q.split(' ', '-').filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return emptyList()
        return entries
            .mapNotNull { entry ->
                val title = AppSearch.normalize(entry.title)
                val titleWords = words(entry.title)
                val allWords = titleWords + entry.keywords.flatMap(::words)
                if (!tokens.all { t -> allWords.any { it.startsWith(t) } }) return@mapNotNull null
                val score = when {
                    title.startsWith(q) -> 3
                    tokens.all { t -> titleWords.any { it.startsWith(t) } } -> 2
                    else -> 1
                }
                entry to score
            }
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }
    }

    // "Wi-Fi" also matches "wifi".
    private fun words(text: String): List<String> {
        val n = AppSearch.normalize(text)
        return (n.split(' ', '-') + n.replace("-", "").split(' ')).filter { it.isNotEmpty() }.distinct()
    }
}
