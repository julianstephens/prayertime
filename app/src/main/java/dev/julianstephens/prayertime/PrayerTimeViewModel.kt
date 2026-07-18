package dev.julianstephens.prayertime

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import dev.julianstephens.prayertime.data.PrayerPreferences
import dev.julianstephens.prayertime.model.PrayerHour
import dev.julianstephens.prayertime.model.PrayerHourId
import dev.julianstephens.prayertime.notifications.PrayerAlarmScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalTime

class PrayerTimeViewModel(
    application: Application,
) : AndroidViewModel(application) {

    private val preferences = PrayerPreferences(application)
    private val scheduler = PrayerAlarmScheduler(application)

    private val _hours = MutableStateFlow(preferences.loadHours())
    val hours: StateFlow<List<PrayerHour>> = _hours.asStateFlow()

    init {
        scheduler.reconcileAll()
    }

    fun updateTime(id: PrayerHourId, time: LocalTime) {
        preferences.saveTime(id, time)
        refresh()

        currentHour(id)?.let(scheduler::scheduleNext)
    }

    fun updateEnabled(id: PrayerHourId, enabled: Boolean) {
        preferences.saveEnabled(id, enabled)
        refresh()

        if (enabled) {
            currentHour(id)?.let(scheduler::scheduleNext)
        } else {
            scheduler.cancel(id)
        }
    }

    fun markCompleted(id: PrayerHourId, completed: Boolean) {
        preferences.saveCompleted(id, completed)
        refresh()
    }

    fun refresh() {
        _hours.value = preferences.loadHours()
    }

    private fun currentHour(id: PrayerHourId): PrayerHour? =
        _hours.value.firstOrNull { it.id == id }
}