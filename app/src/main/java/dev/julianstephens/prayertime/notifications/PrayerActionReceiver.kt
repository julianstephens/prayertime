package dev.julianstephens.prayertime.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import dev.julianstephens.prayertime.data.PrayerPreferences
import dev.julianstephens.prayertime.model.PrayerHourId
import dev.julianstephens.prayertime.model.PrayerResolution
import java.time.LocalDate

class PrayerActionReceiver : BroadcastReceiver() {

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val prayerId = intent
            .getStringExtra(EXTRA_PRAYER_ID)
            ?.let { runCatching { PrayerHourId.valueOf(it) }.getOrNull() }
            ?: return

        val date = intent
            .getStringExtra(EXTRA_OCCURRENCE_DATE)
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: return

        val preferences = PrayerPreferences(context)
        val scheduler = PrayerAlarmScheduler(context)

        when (intent.action) {
            ACTION_COMPLETE -> {
                preferences.saveResolution(
                    prayerId,
                    date,
                    PrayerResolution.COMPLETED,
                )

                scheduler.cancelDelayed(prayerId, date)
                scheduler.cancelWindowClose(prayerId, date)
                dismissNotification(context, prayerId, date)
            }

            ACTION_DELAY -> {
                val delayMinutes =
                    intent.getIntExtra(EXTRA_DELAY_MINUTES, 10)

                scheduler.scheduleDelay(
                    prayerId = prayerId,
                    date = date,
                    delayMinutes = delayMinutes,
                )

                dismissNotification(context, prayerId, date)
            }

            ACTION_CANNOT_PRAY -> {
                preferences.saveResolution(
                    prayerId,
                    date,
                    PrayerResolution.CANNOT_PRAY,
                )

                scheduler.cancelDelayed(prayerId, date)
                scheduler.cancelWindowClose(prayerId, date)
                dismissNotification(context, prayerId, date)
            }
        }
    }

    private fun dismissNotification(
        context: Context,
        prayerId: PrayerHourId,
        date: LocalDate,
    ) {
        NotificationManagerCompat.from(context)
            .cancel(notificationId(prayerId, date))
    }

    companion object {
        const val ACTION_COMPLETE =
            "dev.julianstephens.prayertime.action.COMPLETE"

        const val ACTION_DELAY =
            "dev.julianstephens.prayertime.action.DELAY"

        const val ACTION_CANNOT_PRAY =
            "dev.julianstephens.prayertime.action.CANNOT_PRAY"

        const val EXTRA_PRAYER_ID = "prayer_id"
        const val EXTRA_OCCURRENCE_DATE = "occurrence_date"
        const val EXTRA_DELAY_MINUTES = "delay_minutes"
    }
}