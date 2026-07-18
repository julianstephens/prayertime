package dev.julianstephens.prayertime.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import dev.julianstephens.prayertime.data.PrayerPreferences
import dev.julianstephens.prayertime.model.PrayerHour
import dev.julianstephens.prayertime.model.PrayerHourId
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class PrayerAlarmScheduler(
    private val context: Context,
) {

    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val preferences = PrayerPreferences(context)

    fun reconcileAll() {
        preferences.loadHours().forEach { hour ->
            cancelAll(hour.id)
            if (hour.enabled) {
                scheduleNext(hour)
            }
        }
    }

    fun scheduleNext(hour: PrayerHour) {
        if (!hour.enabled) return

        val zone = ZoneId.systemDefault()
        val now = ZonedDateTime.now(zone)
        var date = LocalDate.now(zone)
        var target = date.atTime(hour.targetTime).atZone(zone)

        if (!target.isAfter(now)) {
            date = date.plusDays(1)
            target = date.atTime(hour.targetTime).atZone(zone)
        }

        scheduleOccurrence(hour, date, target)
    }

    fun scheduleTomorrow(hour: PrayerHour, occurrenceDate: LocalDate) {
        if (!hour.enabled) return
        val date = occurrenceDate.plusDays(1)
        scheduleOccurrence(
            hour,
            date,
            date.atTime(hour.targetTime).atZone(ZoneId.systemDefault()),
        )
    }

    fun scheduleDelay(
        prayerId: PrayerHourId,
        date: LocalDate,
        delayMinutes: Int,
    ): Boolean {
        val hour = preferences.loadHours().firstOrNull { it.id == prayerId } ?: return false
        val zone = ZoneId.systemDefault()
        val triggerAt = ZonedDateTime.now(zone).plusMinutes(delayMinutes.toLong())
        val closesAt = date.atTime(hour.targetTime)
            .atZone(zone)
            .plusMinutes(hour.windowMinutes.toLong())

        if (!triggerAt.isBefore(closesAt)) return false

        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAt.toInstant().toEpochMilli(),
            alarmPendingIntent(prayerId, date, AlarmKind.DELAYED),
        )
        return true
    }

    fun cancelDelayed(prayerId: PrayerHourId, date: LocalDate) {
        alarmManager.cancel(alarmPendingIntent(prayerId, date, AlarmKind.DELAYED))
    }

    fun cancelWindowClose(prayerId: PrayerHourId, date: LocalDate) {
        alarmManager.cancel(alarmPendingIntent(prayerId, date, AlarmKind.WINDOW_CLOSE))
    }

    fun cancelAll(prayerId: PrayerHourId) {
        val today = LocalDate.now()
        listOf(today, today.plusDays(1)).forEach { date ->
            AlarmKind.entries.forEach { kind ->
                alarmManager.cancel(alarmPendingIntent(prayerId, date, kind))
            }
        }
    }

    private fun scheduleOccurrence(
        hour: PrayerHour,
        date: LocalDate,
        target: ZonedDateTime,
    ) {
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            target.toInstant().toEpochMilli(),
            alarmPendingIntent(hour.id, date, AlarmKind.TARGET),
        )

        val closesAt = target.plusMinutes(hour.windowMinutes.toLong())
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            closesAt.toInstant().toEpochMilli(),
            alarmPendingIntent(hour.id, date, AlarmKind.WINDOW_CLOSE),
        )
    }

    private fun alarmPendingIntent(
        prayerId: PrayerHourId,
        date: LocalDate,
        kind: AlarmKind,
    ): PendingIntent {
        val receiver = when (kind) {
            AlarmKind.TARGET,
            AlarmKind.DELAYED,
            -> PrayerAlarmReceiver::class.java
            AlarmKind.WINDOW_CLOSE -> PrayerWindowReceiver::class.java
        }

        val intent = Intent(context, receiver).apply {
            action = "${context.packageName}.alarm.${kind.name}"
            putExtra(PrayerAlarmReceiver.EXTRA_PRAYER_ID, prayerId.name)
            putExtra(PrayerAlarmReceiver.EXTRA_OCCURRENCE_DATE, date.toString())
            putExtra(PrayerAlarmReceiver.EXTRA_ALARM_KIND, kind.name)
        }

        return PendingIntent.getBroadcast(
            context,
            requestCode(prayerId, date, kind.name),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        fun requestCode(
            prayerId: PrayerHourId,
            date: LocalDate,
            kind: String,
        ): Int = "${prayerId.name}:$date:$kind".hashCode()
    }
}

enum class AlarmKind {
    TARGET,
    DELAYED,
    WINDOW_CLOSE,
}
