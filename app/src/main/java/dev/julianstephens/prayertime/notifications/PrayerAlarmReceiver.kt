package dev.julianstephens.prayertime.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.julianstephens.prayertime.MainActivity
import dev.julianstephens.prayertime.R
import dev.julianstephens.prayertime.data.PrayerPreferences
import dev.julianstephens.prayertime.model.PrayerHourId

class PrayerAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val idValue = intent.getStringExtra(EXTRA_PRAYER_ID) ?: return
        val prayerId = runCatching {
            PrayerHourId.valueOf(idValue)
        }.getOrNull() ?: return

        val hour = PrayerPreferences(context)
            .loadHours()
            .firstOrNull { it.id == prayerId }
            ?: return

        createNotificationChannel(context)

        val notificationPermissionGranted =
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED

        if (
            android.os.Build.VERSION.SDK_INT < 33 ||
            notificationPermissionGranted
        ) {
            NotificationManagerCompat.from(context).notify(
                prayerId.ordinal,
                buildNotification(context, prayerId, hour.name),
            )
        }

        PrayerAlarmScheduler(context).scheduleNext(hour)
    }

    private fun buildNotification(
        context: Context,
        prayerId: PrayerHourId,
        prayerName: String,
    ): android.app.Notification {
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_PRAYER_ID, prayerId.name)
        }

        val openPendingIntent = PendingIntent.getActivity(
            context,
            prayerId.ordinal,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_prayer)
            .setContentTitle("$prayerName prayer")
            .setContentText("It is time to stop and pray.")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(
                        "It is time to stop and pray. " +
                                "Holy God, Holy Mighty, Holy Immortal, " +
                                "have mercy on us.",
                    ),
            )
            .setContentIntent(openPendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
    }

    private fun createNotificationChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Prayer hours",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Notifications for scheduled prayer hours"
        }

        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    companion object {
        const val EXTRA_PRAYER_ID = "prayer_id"
        private const val CHANNEL_ID = "prayer_hours"
    }
}