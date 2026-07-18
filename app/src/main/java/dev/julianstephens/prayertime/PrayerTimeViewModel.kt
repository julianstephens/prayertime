package dev.julianstephens.prayertime

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import dev.julianstephens.prayertime.data.NotificationFeedbackMode
import dev.julianstephens.prayertime.data.NotificationSoundSettings
import dev.julianstephens.prayertime.data.NotificationSoundSource
import dev.julianstephens.prayertime.data.PrayerPreferences
import dev.julianstephens.prayertime.model.PrayerHour
import dev.julianstephens.prayertime.model.PrayerHourId
import dev.julianstephens.prayertime.notifications.PrayerAlarmScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

class PrayerTimeViewModel(
    application: Application,
) : AndroidViewModel(application) {

    private val preferences = PrayerPreferences(application)
    private val scheduler = PrayerAlarmScheduler(application)

    private val _hours = MutableStateFlow(preferences.loadHours())
    val hours: StateFlow<List<PrayerHour>> = _hours.asStateFlow()

    private val _soundSettings = MutableStateFlow(
        preferences.loadNotificationSoundSettings(),
    )
    val soundSettings: StateFlow<NotificationSoundSettings> =
        _soundSettings.asStateFlow()

    init {
        scheduler.reconcileAll()
    }

    fun addPrayerHour(): PrayerHourId? {
        val current = _hours.value
        if (current.size >= MAX_PRAYER_HOURS) return null

        val id = PrayerHourId(UUID.randomUUID().toString())
        val suggestedTime = current
            .maxByOrNull { it.targetTime }
            ?.targetTime
            ?.plusHours(3)
            ?: LocalTime.NOON

        preferences.addHour(
            PrayerHour(
                id = id,
                name = "Prayer ${current.size + 1}",
                targetTime = suggestedTime,
                windowMinutes = 60,
                enabled = true,
                soundEnabled = true,
                sortOrder = current.size,
            ),
        )

        refresh()
        _hours.value
            .firstOrNull { it.id == id }
            ?.let(scheduler::reconcileChangedHour)

        return id
    }

    fun updateHour(hour: PrayerHour) {
        val sanitized = hour.copy(
            name = hour.name.trim(),
            windowMinutes = hour.windowMinutes.coerceIn(
                MIN_WINDOW_MINUTES,
                MAX_WINDOW_MINUTES,
            ),
        )

        preferences.updateHour(sanitized)
        refresh()
        _hours.value
            .firstOrNull { it.id == sanitized.id }
            ?.let(scheduler::reconcileChangedHour)
    }

    fun deletePrayerHour(id: PrayerHourId): Boolean {
        if (_hours.value.size <= MIN_PRAYER_HOURS) return false

        scheduler.cancelAll(id)
        preferences.deleteHour(id)
        refresh()
        scheduler.reconcileAll()
        return true
    }

    fun updateTime(id: PrayerHourId, time: LocalTime) {
        currentHour(id)?.let { updateHour(it.copy(targetTime = time)) }
    }

    fun updateEnabled(id: PrayerHourId, enabled: Boolean) {
        currentHour(id)?.let { updateHour(it.copy(enabled = enabled)) }
    }

    fun updateSoundEnabled(id: PrayerHourId, enabled: Boolean) {
        currentHour(id)?.let { hour ->
            preferences.saveSoundEnabled(id, enabled)
            refresh()
            _hours.value
                .firstOrNull { it.id == hour.id }
                ?.let(scheduler::reconcileChangedHour)
        }
    }

    fun updateName(id: PrayerHourId, name: String) {
        currentHour(id)?.let { updateHour(it.copy(name = name)) }
    }

    fun updateWindowMinutes(id: PrayerHourId, windowMinutes: Int) {
        currentHour(id)?.let {
            updateHour(it.copy(windowMinutes = windowMinutes))
        }
    }

    fun updateSoundSource(source: NotificationSoundSource) {
        saveSoundSettings(_soundSettings.value.copy(source = source))
    }

    fun updateFeedbackMode(mode: NotificationFeedbackMode) {
        saveSoundSettings(_soundSettings.value.copy(feedbackMode = mode))
    }

    fun updateCustomSound(uri: String, displayName: String?) {
        saveSoundSettings(
            _soundSettings.value.copy(
                source = NotificationSoundSource.CUSTOM,
                customSoundUri = uri,
                customSoundName = displayName ?: "Custom sound",
            ),
        )
    }

    fun markCompleted(id: PrayerHourId, completed: Boolean) {
        preferences.saveCompleted(id, completed)

        if (completed) {
            val today = LocalDate.now()
            scheduler.cancelDelayed(id, today)
            scheduler.cancelWindowClose(id, today)
        } else {
            scheduler.reconcileAll()
        }

        refresh()
    }

    fun refresh() {
        _hours.value = preferences.loadHours()
        _soundSettings.value = preferences.loadNotificationSoundSettings()
    }

    private fun saveSoundSettings(settings: NotificationSoundSettings) {
        preferences.saveNotificationSoundSettings(settings)
        _soundSettings.value = settings
    }

    private fun currentHour(id: PrayerHourId): PrayerHour? =
        _hours.value.firstOrNull { it.id == id }

    companion object {
        const val MIN_PRAYER_HOURS = 1
        const val MAX_PRAYER_HOURS = 12
        const val MIN_WINDOW_MINUTES = 5
        const val MAX_WINDOW_MINUTES = 360
    }
}
