package dev.julianstephens.prayertime.notifications

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import dev.julianstephens.prayertime.data.PrayerPreferences
import dev.julianstephens.prayertime.data.PrayerResolution
import dev.julianstephens.prayertime.model.PrayerHourId
import java.time.LocalDate

class PrayerActionReceiver : BroadcastReceiver() {

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val prayerId = intent
            .getStringExtra(EXTRA_PRAYER_ID)
            ?.let(::PrayerHourId)
            ?: return

        val date = intent
            .getStringExtra(EXTRA_OCCURRENCE_DATE)
            ?.let {
                runCatching {
                    LocalDate.parse(it)
                }.getOrNull()
            }
            ?: return

        val preferences =
            PrayerPreferences(context)

        val scheduler =
            PrayerAlarmScheduler(context)

        when (intent.action) {
            ACTION_COMPLETE -> {
                preferences.saveResolution(
                    prayerId,
                    date,
                    PrayerResolution.COMPLETED,
                )

                scheduler.cancelDelayed(
                    prayerId,
                    date,
                )
                scheduler.cancelWindowClose(
                    prayerId,
                    date,
                )
            }

            ACTION_DELAY -> {
                scheduler.scheduleDelay(
                    prayerId = prayerId,
                    date = date,
                    delayMinutes = 10,
                )
            }

            ACTION_CANNOT_PRAY -> {
                preferences.saveResolution(
                    prayerId,
                    date,
                    PrayerResolution.CANNOT_PRAY,
                )

                scheduler.cancelDelayed(
                    prayerId,
                    date,
                )
                scheduler.cancelWindowClose(
                    prayerId,
                    date,
                )
            }
        }

        NotificationManagerCompat
            .from(context)
            .cancel(
                PrayerAlarmReceiver.notificationId(
                    prayerId,
                    date,
                ),
            )
    }

    companion object {
        const val ACTION_COMPLETE =
            "dev.julianstephens.prayertime.action.COMPLETE"

        const val ACTION_DELAY =
            "dev.julianstephens.prayertime.action.DELAY"

        const val ACTION_CANNOT_PRAY =
            "dev.julianstephens.prayertime.action.CANNOT_PRAY"

        const val EXTRA_PRAYER_ID =
            "prayer_id"

        const val EXTRA_OCCURRENCE_DATE =
            "occurrence_date"

        fun pendingIntent(
            context: Context,
            prayerId: PrayerHourId,
            date: LocalDate,
            action: String,
        ): PendingIntent {
            val intent = Intent(
                context,
                PrayerActionReceiver::class.java,
            ).apply {
                this.action = action

                putExtra(
                    EXTRA_PRAYER_ID,
                    prayerId.value,
                )
                putExtra(
                    EXTRA_OCCURRENCE_DATE,
                    date.toString(),
                )
            }

            return PendingIntent.getBroadcast(
                context,
                PrayerAlarmScheduler.requestCode(
                    prayerId,
                    date,
                    action,
                ),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                        PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}