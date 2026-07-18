package dev.julianstephens.prayertime.data

import android.content.Context
import dev.julianstephens.prayertime.model.PrayerHour
import dev.julianstephens.prayertime.model.PrayerHourId
import dev.julianstephens.prayertime.model.defaultPrayerHours
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime

enum class PrayerResolution {
    PENDING,
    COMPLETED,
    CANNOT_PRAY,
    MISSED,
}

class PrayerPreferences(
    context: Context,
) {

    private val preferences = context.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    init {
        ensureScheduleExists()
    }

    fun loadHours(): List<PrayerHour> {
        val today = LocalDate.now()

        return normalizeSchedule(loadStoredSchedule()).map { hour ->
            hour.copy(
                completedToday = loadResolution(
                    hour.id,
                    today,
                ) == PrayerResolution.COMPLETED,
            )
        }
    }

    fun addHour(hour: PrayerHour) {
        val hours = loadStoredSchedule().toMutableList()
        if (hours.any { it.id == hour.id }) return

        hours += hour.copy(completedToday = false)
        saveSchedule(hours)
    }

    fun updateHour(updatedHour: PrayerHour) {
        val updated = loadStoredSchedule().map { existing ->
            if (existing.id == updatedHour.id) {
                updatedHour.copy(completedToday = false)
            } else {
                existing
            }
        }
        saveSchedule(updated)
    }

    fun deleteHour(id: PrayerHourId) {
        saveSchedule(loadStoredSchedule().filterNot { it.id == id })
        deleteResolutionRecords(id)
    }

    fun saveTime(id: PrayerHourId, time: LocalTime) {
        mutateHour(id) { it.copy(targetTime = time) }
    }

    fun saveEnabled(id: PrayerHourId, enabled: Boolean) {
        mutateHour(id) { it.copy(enabled = enabled) }
    }

    fun saveSoundEnabled(id: PrayerHourId, enabled: Boolean) {
        mutateHour(id) { it.copy(soundEnabled = enabled) }
    }

    fun saveName(id: PrayerHourId, name: String) {
        mutateHour(id) { it.copy(name = name) }
    }

    fun saveWindowMinutes(id: PrayerHourId, windowMinutes: Int) {
        mutateHour(id) { it.copy(windowMinutes = windowMinutes) }
    }

    fun loadNotificationSoundSettings(): NotificationSoundSettings =
        NotificationSoundSettings(
            source = preferences.getString(SOUND_SOURCE_KEY, null)
                ?.let { runCatching { NotificationSoundSource.valueOf(it) }.getOrNull() }
                ?: NotificationSoundSource.ONBOARD,
            feedbackMode = preferences.getString(FEEDBACK_MODE_KEY, null)
                ?.let { runCatching { NotificationFeedbackMode.valueOf(it) }.getOrNull() }
                ?: NotificationFeedbackMode.SOUND_AND_VIBRATION,
            customSoundUri = preferences.getString(CUSTOM_SOUND_URI_KEY, null),
            customSoundName = preferences.getString(CUSTOM_SOUND_NAME_KEY, null),
            overridePhoneSoundMode = preferences.getBoolean(
                OVERRIDE_PHONE_SOUND_MODE_KEY,
                false,
            ),
            volumeMode = preferences.getString(VOLUME_MODE_KEY, null)
                ?.let { runCatching { NotificationVolumeMode.valueOf(it) }.getOrNull() }
                ?: NotificationVolumeMode.PHONE_ALARM,
            customVolumePercent = preferences.getInt(
                CUSTOM_VOLUME_PERCENT_KEY,
                DEFAULT_CUSTOM_VOLUME_PERCENT,
            ).coerceIn(0, 100),
        )

    fun saveNotificationSoundSettings(settings: NotificationSoundSettings) {
        preferences.edit()
            .putString(SOUND_SOURCE_KEY, settings.source.name)
            .putString(FEEDBACK_MODE_KEY, settings.feedbackMode.name)
            .putString(CUSTOM_SOUND_URI_KEY, settings.customSoundUri)
            .putString(CUSTOM_SOUND_NAME_KEY, settings.customSoundName)
            .putBoolean(
                OVERRIDE_PHONE_SOUND_MODE_KEY,
                settings.overridePhoneSoundMode,
            )
            .putString(VOLUME_MODE_KEY, settings.volumeMode.name)
            .putInt(
                CUSTOM_VOLUME_PERCENT_KEY,
                settings.customVolumePercent.coerceIn(0, 100),
            )
            .apply()
    }

    fun saveCompleted(id: PrayerHourId, completed: Boolean) {
        saveResolution(
            prayerId = id,
            date = LocalDate.now(),
            resolution = if (completed) {
                PrayerResolution.COMPLETED
            } else {
                PrayerResolution.PENDING
            },
        )
    }

    fun loadResolution(
        prayerId: PrayerHourId,
        date: LocalDate,
    ): PrayerResolution {
        val currentValue = preferences.getString(
            resolutionKey(prayerId, date),
            null,
        )
        if (currentValue != null) return parseResolution(currentValue)

        val legacyValue = preferences.getString(
            legacyResolutionKey(prayerId, date),
            null,
        )
        return legacyValue?.let(::parseResolution) ?: PrayerResolution.PENDING
    }

    fun saveResolution(
        prayerId: PrayerHourId,
        date: LocalDate,
        resolution: PrayerResolution,
    ) {
        preferences.edit()
            .putString(resolutionKey(prayerId, date), resolution.name)
            .apply()
    }

    private fun mutateHour(
        id: PrayerHourId,
        transform: (PrayerHour) -> PrayerHour,
    ) {
        saveSchedule(
            loadStoredSchedule().map { hour ->
                if (hour.id == id) transform(hour) else hour
            },
        )
    }

    private fun ensureScheduleExists() {
        if (!preferences.contains(SCHEDULE_KEY)) {
            saveSchedule(migrateLegacySchedule())
        }
    }

    private fun migrateLegacySchedule(): List<PrayerHour> =
        defaultPrayerHours.map { default ->
            val legacyPrefix = default.id.value.uppercase()
            default.copy(
                targetTime = LocalTime.of(
                    preferences.getInt(
                        "${legacyPrefix}_hour",
                        default.targetTime.hour,
                    ),
                    preferences.getInt(
                        "${legacyPrefix}_minute",
                        default.targetTime.minute,
                    ),
                ),
                enabled = preferences.getBoolean(
                    "${legacyPrefix}_enabled",
                    default.enabled,
                ),
            )
        }

    private fun loadStoredSchedule(): List<PrayerHour> {
        val raw = preferences.getString(SCHEDULE_KEY, null)
            ?: return defaultPrayerHours

        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val value = array.getJSONObject(index)
                    add(
                        PrayerHour(
                            id = PrayerHourId(value.getString("id")),
                            name = value.getString("name"),
                            targetTime = LocalTime.of(
                                value.getInt("hour"),
                                value.getInt("minute"),
                            ),
                            windowMinutes = value.optInt(
                                "windowMinutes",
                                DEFAULT_WINDOW_MINUTES,
                            ),
                            enabled = value.optBoolean("enabled", true),
                            soundEnabled = value.optBoolean("soundEnabled", true),
                            sortOrder = index,
                        ),
                    )
                }
            }
        }.getOrElse {
            saveSchedule(defaultPrayerHours)
            defaultPrayerHours
        }
    }

    private fun saveSchedule(hours: List<PrayerHour>) {
        val array = JSONArray()
        normalizeSchedule(hours).forEach { hour ->
            array.put(
                JSONObject().apply {
                    put("id", hour.id.value)
                    put("name", hour.name)
                    put("hour", hour.targetTime.hour)
                    put("minute", hour.targetTime.minute)
                    put("windowMinutes", hour.windowMinutes)
                    put("enabled", hour.enabled)
                    put("soundEnabled", hour.soundEnabled)
                },
            )
        }

        preferences.edit()
            .putString(SCHEDULE_KEY, array.toString())
            .apply()
    }

    private fun deleteResolutionRecords(id: PrayerHourId) {
        val currentPrefix = "resolution_${id.value}_"
        val legacyPrefix = "resolution_${id.value.uppercase()}_"
        val editor = preferences.edit()

        preferences.all.keys
            .filter { it.startsWith(currentPrefix) || it.startsWith(legacyPrefix) }
            .forEach(editor::remove)

        editor.apply()
    }

    private fun parseResolution(value: String): PrayerResolution =
        runCatching { PrayerResolution.valueOf(value) }
            .getOrDefault(PrayerResolution.PENDING)

    private fun resolutionKey(
        prayerId: PrayerHourId,
        date: LocalDate,
    ): String = "resolution_${prayerId.value}_$date"

    private fun legacyResolutionKey(
        prayerId: PrayerHourId,
        date: LocalDate,
    ): String = "resolution_${prayerId.value.uppercase()}_$date"

    private fun normalizeSchedule(hours: List<PrayerHour>): List<PrayerHour> =
        hours
            .sortedWith(
                compareBy<PrayerHour> { it.targetTime }
                    .thenBy { it.id.value },
            )
            .mapIndexed { index, hour ->
                hour.copy(
                    sortOrder = index,
                    completedToday = false,
                )
            }

    private companion object {
        const val PREFERENCES_NAME = "prayer_time_preferences"
        const val SCHEDULE_KEY = "prayer_schedule_json"
        const val SOUND_SOURCE_KEY = "notification_sound_source"
        const val FEEDBACK_MODE_KEY = "notification_feedback_mode"
        const val CUSTOM_SOUND_URI_KEY = "custom_sound_uri"
        const val CUSTOM_SOUND_NAME_KEY = "custom_sound_name"
        const val OVERRIDE_PHONE_SOUND_MODE_KEY = "override_phone_sound_mode"
        const val VOLUME_MODE_KEY = "notification_volume_mode"
        const val CUSTOM_VOLUME_PERCENT_KEY = "custom_volume_percent"
        const val DEFAULT_CUSTOM_VOLUME_PERCENT = 70
        const val DEFAULT_WINDOW_MINUTES = 60
    }
}
