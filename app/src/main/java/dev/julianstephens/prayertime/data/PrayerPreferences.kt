package dev.julianstephens.prayertime.data

import android.content.Context
import dev.julianstephens.prayertime.model.PrayerHour
import dev.julianstephens.prayertime.model.PrayerHourId
import dev.julianstephens.prayertime.model.defaultPrayerHours
import java.time.LocalDate
import java.time.LocalTime

enum class PrayerResolution {
    PENDING,
    COMPLETED,
    CANNOT_PRAY,
    MISSED,
}

class PrayerPreferences(context: Context) {

    private val preferences = context.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    fun loadHours(): List<PrayerHour> {
        val today = LocalDate.now()

        return defaultPrayerHours.map { default ->
            PrayerHour(
                id = default.id,
                name = default.name,
                targetTime = loadTime(default),
                windowMinutes = default.windowMinutes,
                enabled = preferences.getBoolean(
                    enabledKey(default.id),
                    default.enabled,
                ),
                completedToday = loadResolution(default.id, today) ==
                    PrayerResolution.COMPLETED,
            )
        }
    }

    fun saveTime(id: PrayerHourId, time: LocalTime) {
        preferences.edit()
            .putInt(hourKey(id), time.hour)
            .putInt(minuteKey(id), time.minute)
            .apply()
    }

    fun saveEnabled(id: PrayerHourId, enabled: Boolean) {
        preferences.edit()
            .putBoolean(enabledKey(id), enabled)
            .apply()
    }

    fun saveCompleted(id: PrayerHourId, completed: Boolean) {
        saveResolution(
            id,
            LocalDate.now(),
            if (completed) PrayerResolution.COMPLETED else PrayerResolution.PENDING,
        )
    }

    fun loadResolution(
        prayerId: PrayerHourId,
        date: LocalDate,
    ): PrayerResolution {
        val value = preferences.getString(
            resolutionKey(prayerId, date),
            PrayerResolution.PENDING.name,
        )

        return runCatching {
            PrayerResolution.valueOf(value ?: PrayerResolution.PENDING.name)
        }.getOrDefault(PrayerResolution.PENDING)
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

    private fun loadTime(default: PrayerHour): LocalTime {
        val hour = preferences.getInt(
            hourKey(default.id),
            default.targetTime.hour,
        )
        val minute = preferences.getInt(
            minuteKey(default.id),
            default.targetTime.minute,
        )

        return LocalTime.of(hour, minute)
    }

    private fun hourKey(id: PrayerHourId) = "${id.name}_hour"

    private fun minuteKey(id: PrayerHourId) = "${id.name}_minute"

    private fun enabledKey(id: PrayerHourId) = "${id.name}_enabled"

    private fun resolutionKey(
        prayerId: PrayerHourId,
        date: LocalDate,
    ) = "resolution_${prayerId.name}_$date"

    private companion object {
        const val PREFERENCES_NAME = "prayer_time_preferences"
    }
}
