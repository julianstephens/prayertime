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

class PrayerAlarmScheduler(
    private val context: Context,
) {

    private val alarmManager =
        context.getSystemService(AlarmManager::class.java)

    fun reconcileAll() {
        val hours = PrayerPreferences(context).loadHours()

        hours.forEach { hour ->
            if (hour.enabled) {
                scheduleNext(hour)
            } else {
                cancel(hour.id)
            }
        }
    }

    fun scheduleNext(hour: PrayerHour) {
        cancel(hour.id)

        if (!hour.enabled) {
            return
        }

        val zone = ZoneId.systemDefault()
        val now = java.time.ZonedDateTime.now(zone)

        var next = LocalDate.now(zone)
            .atTime(hour.targetTime)
            .atZone(zone)

        if (!next.isAfter(now)) {
            next = next.plusDays(1)
        }

        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            next.toInstant().toEpochMilli(),
            pendingIntent(hour.id),
        )
    }

    fun cancel(id: PrayerHourId) {
        alarmManager.cancel(pendingIntent(id))
    }

    private fun pendingIntent(id: PrayerHourId): PendingIntent {
        val intent = Intent(context, PrayerAlarmReceiver::class.java).apply {
            putExtra(PrayerAlarmReceiver.EXTRA_PRAYER_ID, id.name)
        }

        return PendingIntent.getBroadcast(
            context,
            id.ordinal,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}