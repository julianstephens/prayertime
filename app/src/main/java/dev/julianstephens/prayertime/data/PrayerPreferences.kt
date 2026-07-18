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
        resetCompletionsIfDateChanged()

        return defaultPrayerHours.map { default ->
            PrayerHour(
                id = default.id,
                name = default.name,
                targetTime = loadTime(default),
                enabled = preferences.getBoolean(
                    enabledKey(default.id),
                    default.enabled,
                ),
                completedToday = preferences.getBoolean(
                    completedKey(default.id),
                    false,
                ),
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
        ensureCompletionDateIsCurrent()

        preferences.edit()
            .putBoolean(completedKey(id), completed)
            .apply()
    }

    fun loadResolution(prayerId: PrayerHourId, date: LocalDate): PrayerResolution {
        val storedDate = preferences.getString(COMPLETION_DATE_KEY, null)
        if (storedDate != date.toString()) {
            return PrayerResolution.PENDING
        }

        val completed = preferences.getBoolean(completedKey(prayerId), false)
        return if (completed) {
            PrayerResolution.COMPLETED
        } else {
            PrayerResolution.PENDING
        }
    }

    fun saveResolution(prayerId: PrayerHourId, date: LocalDate, resolution: PrayerResolution) {
        ensureCompletionDateIsCurrent()

        val completed = when (resolution) {
            PrayerResolution.COMPLETED -> true
            else -> false
        }

        preferences.edit()
            .putBoolean(completedKey(prayerId), completed)
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

    private fun resetCompletionsIfDateChanged() {
        val today = LocalDate.now().toString()
        val storedDate = preferences.getString(COMPLETION_DATE_KEY, null)

        if (storedDate == today) {
            return
        }

        val editor = preferences.edit()
            .putString(COMPLETION_DATE_KEY, today)

        PrayerHourId.entries.forEach { id ->
            editor.remove(completedKey(id))
        }

        editor.apply()
    }

    private fun ensureCompletionDateIsCurrent() {
        resetCompletionsIfDateChanged()
    }

    private fun hourKey(id: PrayerHourId) = "${id.name}_hour"

    private fun minuteKey(id: PrayerHourId) = "${id.name}_minute"

    private fun enabledKey(id: PrayerHourId) = "${id.name}_enabled"

    private fun completedKey(id: PrayerHourId) = "${id.name}_completed"

    private fun resolutionKey(
        prayerId: PrayerHourId,
        date: LocalDate,
    ) = "resolution_${prayerId.name}_$date"

    private companion object {
        const val PREFERENCES_NAME = "prayer_time_preferences"
        const val COMPLETION_DATE_KEY = "completion_date"
    }
}

