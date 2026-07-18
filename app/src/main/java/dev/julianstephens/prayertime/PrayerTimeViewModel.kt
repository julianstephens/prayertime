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
import java.time.LocalDate
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
        scheduler.reconcileAll()
    }

    fun updateEnabled(id: PrayerHourId, enabled: Boolean) {
        preferences.saveEnabled(id, enabled)
        refresh()
        scheduler.reconcileAll()
    }

    fun markCompleted(id: PrayerHourId, completed: Boolean) {
        preferences.saveCompleted(id, completed)
        if (completed) {
            scheduler.cancelDelayed(id, LocalDate.now())
            scheduler.cancelWindowClose(id, LocalDate.now())
        }
        refresh()
    }

    fun refresh() {
        _hours.value = preferences.loadHours()
    }
}
