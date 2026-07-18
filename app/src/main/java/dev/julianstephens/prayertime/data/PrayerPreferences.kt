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

        return normalizeSchedule(
            loadStoredSchedule(),
        ).map { hour ->
            hour.copy(
                completedToday = loadResolution(
                    hour.id,
                    today,
                ) == PrayerResolution.COMPLETED,
            )
        }
    }

    fun addHour(
        hour: PrayerHour,
    ) {
        val hours = loadStoredSchedule()
            .toMutableList()

        if (hours.any { it.id == hour.id }) {
            return
        }

        hours += hour.copy(
            completedToday = false,
        )

        saveSchedule(hours)
    }

    fun updateHour(updatedHour: PrayerHour) {
        val hours = loadStoredSchedule()
        val updated = hours.map { existing ->
            if (existing.id == updatedHour.id) {
                updatedHour.copy(
                    completedToday = false,
                )
            } else {
                existing
            }
        }

        saveSchedule(updated)
    }

    fun deleteHour(
        id: PrayerHourId,
    ) {
        val hours = loadStoredSchedule()
            .filterNot { it.id == id }

        saveSchedule(hours)
        deleteResolutionRecords(id)
    }

    fun saveTime(
        id: PrayerHourId,
        time: LocalTime,
    ) {
        mutateHour(id) { hour ->
            hour.copy(targetTime = time)
        }
    }

    fun saveEnabled(
        id: PrayerHourId,
        enabled: Boolean,
    ) {
        mutateHour(id) { hour ->
            hour.copy(enabled = enabled)
        }
    }

    fun saveName(
        id: PrayerHourId,
        name: String,
    ) {
        mutateHour(id) { hour ->
            hour.copy(name = name)
        }
    }

    fun saveWindowMinutes(
        id: PrayerHourId,
        windowMinutes: Int,
    ) {
        mutateHour(id) { hour ->
            hour.copy(windowMinutes = windowMinutes)
        }
    }

    fun saveCompleted(
        id: PrayerHourId,
        completed: Boolean,
    ) {
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

        if (currentValue != null) {
            return parseResolution(currentValue)
        }

        // Preserve today's state from the original enum-backed format.
        val legacyValue = preferences.getString(
            legacyResolutionKey(prayerId, date),
            null,
        )

        return if (legacyValue != null) {
            parseResolution(legacyValue)
        } else {
            PrayerResolution.PENDING
        }
    }

    fun saveResolution(
        prayerId: PrayerHourId,
        date: LocalDate,
        resolution: PrayerResolution,
    ) {
        preferences.edit()
            .putString(
                resolutionKey(prayerId, date),
                resolution.name,
            )
            .apply()
    }

    private fun mutateHour(
        id: PrayerHourId,
        transform: (PrayerHour) -> PrayerHour,
    ) {
        val hours = loadStoredSchedule()
        val updated = hours.map { hour ->
            if (hour.id == id) {
                transform(hour)
            } else {
                hour
            }
        }

        saveSchedule(updated)
    }

    private fun ensureScheduleExists() {
        if (preferences.contains(SCHEDULE_KEY)) {
            return
        }

        saveSchedule(
            migrateLegacySchedule(),
        )
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
        val raw = preferences.getString(
            SCHEDULE_KEY,
            null,
        ) ?: return defaultPrayerHours

        return runCatching {
            val array = JSONArray(raw)

            buildList {
                for (index in 0 until array.length()) {
                    val objectValue = array.getJSONObject(index)

                    add(
                        PrayerHour(
                            id = PrayerHourId(
                                objectValue.getString("id"),
                            ),
                            name = objectValue.getString("name"),
                            targetTime = LocalTime.of(
                                objectValue.getInt("hour"),
                                objectValue.getInt("minute"),
                            ),
                            windowMinutes = objectValue.optInt(
                                "windowMinutes",
                                DEFAULT_WINDOW_MINUTES,
                            ),
                            enabled = objectValue.optBoolean(
                                "enabled",
                                true,
                            ),
                        ),
                    )
                }
            }
        }.getOrElse {
            saveSchedule(defaultPrayerHours)
            defaultPrayerHours
        }
    }

    private fun saveSchedule(
        hours: List<PrayerHour>,
    ) {
        val normalized = normalizeSchedule(hours)
        val array = JSONArray()

        normalized.forEach { hour ->
            array.put(
                JSONObject().apply {
                    put("id", hour.id.value)
                    put("name", hour.name)
                    put("hour", hour.targetTime.hour)
                    put("minute", hour.targetTime.minute)
                    put(
                        "windowMinutes",
                        hour.windowMinutes,
                    )
                    put("enabled", hour.enabled)
                },
            )
        }

        preferences.edit()
            .putString(
                SCHEDULE_KEY,
                array.toString(),
            )
            .apply()
    }


    private fun deleteResolutionRecords(
        id: PrayerHourId,
    ) {
        val currentPrefix = "resolution_${id.value}_"
        val legacyPrefix =
            "resolution_${id.value.uppercase()}_"

        val editor = preferences.edit()

        preferences.all.keys
            .filter { key ->
                key.startsWith(currentPrefix) ||
                        key.startsWith(legacyPrefix)
            }
            .forEach(editor::remove)

        editor.apply()
    }

    private fun parseResolution(
        value: String,
    ): PrayerResolution =
        runCatching {
            PrayerResolution.valueOf(value)
        }.getOrDefault(PrayerResolution.PENDING)

    private fun resolutionKey(
        prayerId: PrayerHourId,
        date: LocalDate,
    ): String =
        "resolution_${prayerId.value}_$date"

    private fun legacyResolutionKey(
        prayerId: PrayerHourId,
        date: LocalDate,
    ): String =
        "resolution_${prayerId.value.uppercase()}_$date"

    private companion object {
        const val PREFERENCES_NAME =
            "prayer_time_preferences"

        const val SCHEDULE_KEY =
            "prayer_schedule_json"

        const val DEFAULT_WINDOW_MINUTES = 60
    }

    private fun normalizeSchedule(
        hours: List<PrayerHour>,
    ): List<PrayerHour> =
        hours
            .sortedWith(
                compareBy<PrayerHour> { it.targetTime }
                    .thenBy { it.id.value },
            )
            .mapIndexed { index, hour ->
                hour.copy(
                    completedToday = false,
                )
            }
}