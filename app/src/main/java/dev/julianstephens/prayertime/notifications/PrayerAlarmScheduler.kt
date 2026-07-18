package dev.julianstephens.prayertime.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import dev.julianstephens.prayertime.data.PrayerPreferences
import dev.julianstephens.prayertime.data.PrayerResolution
import dev.julianstephens.prayertime.model.PrayerHour
import dev.julianstephens.prayertime.model.PrayerHourId
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class PrayerAlarmScheduler(
    private val context: Context,
) {

    private val alarmManager =
        context.getSystemService(AlarmManager::class.java)

    private val preferences =
        PrayerPreferences(context)

    fun reconcileAll() {
        preferences.loadHours().forEach { hour ->
            cancelAll(hour.id)

            if (hour.enabled) {
                scheduleNext(hour)
            }
        }
    }

    fun scheduleNext(
        hour: PrayerHour,
    ) {
        if (!hour.enabled) {
            return
        }

        val zone = ZoneId.systemDefault()
        val now = ZonedDateTime.now(zone)
        val today = LocalDate.now(zone)

        val targetToday = today
            .atTime(hour.targetTime)
            .atZone(zone)

        val closesToday = targetToday.plusMinutes(
            hour.windowMinutes.toLong(),
        )

        when {
            now.isBefore(targetToday) -> {
                scheduleOccurrence(
                    hour = hour,
                    date = today,
                    target = targetToday,
                )
            }

            now.isBefore(closesToday) &&
                    preferences.loadResolution(
                        hour.id,
                        today,
                    ) == PrayerResolution.PENDING -> {
                scheduleWindowClose(
                    hour = hour,
                    date = today,
                    closesAt = closesToday,
                )
            }

            else -> {
                val tomorrow = today.plusDays(1)

                scheduleOccurrence(
                    hour = hour,
                    date = tomorrow,
                    target = tomorrow
                        .atTime(hour.targetTime)
                        .atZone(zone),
                )
            }
        }
    }

    fun scheduleTomorrow(
        hour: PrayerHour,
        occurrenceDate: LocalDate,
    ) {
        if (!hour.enabled) {
            return
        }

        val date = occurrenceDate.plusDays(1)

        scheduleOccurrence(
            hour = hour,
            date = date,
            target = date
                .atTime(hour.targetTime)
                .atZone(ZoneId.systemDefault()),
        )
    }

    fun scheduleDelay(
        prayerId: PrayerHourId,
        date: LocalDate,
        delayMinutes: Int,
    ): Boolean {
        val hour = preferences.loadHours()
            .firstOrNull { it.id == prayerId }
            ?: return false

        val zone = ZoneId.systemDefault()
        val triggerAt = ZonedDateTime.now(zone)
            .plusMinutes(delayMinutes.toLong())

        val closesAt = date
            .atTime(hour.targetTime)
            .atZone(zone)
            .plusMinutes(hour.windowMinutes.toLong())

        if (!triggerAt.isBefore(closesAt)) {
            return false
        }

        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAt.toInstant().toEpochMilli(),
            alarmPendingIntent(
                prayerId = prayerId,
                date = date,
                kind = AlarmKind.DELAYED,
            ),
        )

        return true
    }

    fun cancelDelayed(
        prayerId: PrayerHourId,
        date: LocalDate,
    ) {
        alarmManager.cancel(
            alarmPendingIntent(
                prayerId,
                date,
                AlarmKind.DELAYED,
            ),
        )
    }

    fun cancelWindowClose(
        prayerId: PrayerHourId,
        date: LocalDate,
    ) {
        alarmManager.cancel(
            alarmPendingIntent(
                prayerId,
                date,
                AlarmKind.WINDOW_CLOSE,
            ),
        )
    }

    fun cancelAll(
        prayerId: PrayerHourId,
    ) {
        val today = LocalDate.now()

        listOf(
            today.minusDays(1),
            today,
            today.plusDays(1),
        ).forEach { date ->
            AlarmKind.entries.forEach { kind ->
                alarmManager.cancel(
                    alarmPendingIntent(
                        prayerId,
                        date,
                        kind,
                    ),
                )
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
            alarmPendingIntent(
                prayerId = hour.id,
                date = date,
                kind = AlarmKind.TARGET,
            ),
        )

        scheduleWindowClose(
            hour = hour,
            date = date,
            closesAt = target.plusMinutes(
                hour.windowMinutes.toLong(),
            ),
        )
    }

    private fun scheduleWindowClose(
        hour: PrayerHour,
        date: LocalDate,
        closesAt: ZonedDateTime,
    ) {
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            closesAt.toInstant().toEpochMilli(),
            alarmPendingIntent(
                prayerId = hour.id,
                date = date,
                kind = AlarmKind.WINDOW_CLOSE,
            ),
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

            AlarmKind.WINDOW_CLOSE ->
                PrayerWindowReceiver::class.java
        }

        val intent = Intent(
            context,
            receiver,
        ).apply {
            action =
                "${context.packageName}.alarm.${kind.name}"

            putExtra(
                PrayerAlarmReceiver.EXTRA_PRAYER_ID,
                prayerId.value,
            )
            putExtra(
                PrayerAlarmReceiver.EXTRA_OCCURRENCE_DATE,
                date.toString(),
            )
            putExtra(
                PrayerAlarmReceiver.EXTRA_ALARM_KIND,
                kind.name,
            )
        }

        return PendingIntent.getBroadcast(
            context,
            requestCode(
                prayerId,
                date,
                kind.name,
            ),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        fun requestCode(
            prayerId: PrayerHourId,
            date: LocalDate,
            kind: String,
        ): Int =
            "${prayerId.value}:$date:$kind".hashCode()
    }
}

enum class AlarmKind {
    TARGET,
    DELAYED,
    WINDOW_CLOSE,
}