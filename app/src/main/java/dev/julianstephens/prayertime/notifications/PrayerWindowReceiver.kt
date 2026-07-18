package dev.julianstephens.prayertime.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import dev.julianstephens.prayertime.data.PrayerPreferences
import dev.julianstephens.prayertime.data.PrayerResolution
import dev.julianstephens.prayertime.model.PrayerHourId
import java.time.LocalDate

class PrayerWindowReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val prayerId = intent.getStringExtra(PrayerAlarmReceiver.EXTRA_PRAYER_ID)
            ?.let { runCatching { PrayerHourId.valueOf(it) }.getOrNull() }
            ?: return
        val date = intent.getStringExtra(PrayerAlarmReceiver.EXTRA_OCCURRENCE_DATE)
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: return

        val preferences = PrayerPreferences(context)
        if (preferences.loadResolution(prayerId, date) == PrayerResolution.PENDING) {
            preferences.saveResolution(prayerId, date, PrayerResolution.MISSED)
        }

        PrayerAlarmScheduler(context).cancelDelayed(prayerId, date)
        NotificationManagerCompat.from(context)
            .cancel(PrayerAlarmReceiver.notificationId(prayerId, date))
    }
}
