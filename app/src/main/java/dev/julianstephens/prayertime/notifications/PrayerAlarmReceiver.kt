package dev.julianstephens.prayertime.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.julianstephens.prayertime.MainActivity
import dev.julianstephens.prayertime.R
import dev.julianstephens.prayertime.data.PrayerPreferences
import dev.julianstephens.prayertime.data.PrayerResolution
import dev.julianstephens.prayertime.model.PrayerHourId
import java.time.LocalDate

class PrayerAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val prayerId = intent
            .getStringExtra(EXTRA_PRAYER_ID)
            ?.let(::PrayerHourId)
            ?: return

        val occurrenceDate = intent
            .getStringExtra(EXTRA_OCCURRENCE_DATE)
            ?.let {
                runCatching {
                    LocalDate.parse(it)
                }.getOrNull()
            }
            ?: return

        val kind = intent
            .getStringExtra(EXTRA_ALARM_KIND)
            ?.let {
                runCatching {
                    AlarmKind.valueOf(it)
                }.getOrNull()
            }
            ?: AlarmKind.TARGET

        val preferences = PrayerPreferences(context)

        val hour = preferences.loadHours()
            .firstOrNull { it.id == prayerId }
            ?: return

        if (
            !hour.enabled ||
            preferences.loadResolution(
                prayerId,
                occurrenceDate,
            ) != PrayerResolution.PENDING
        ) {
            return
        }

        createNotificationChannel(context)

        if (canPostNotifications(context)) {
            NotificationManagerCompat
                .from(context)
                .notify(
                    notificationId(
                        prayerId,
                        occurrenceDate,
                    ),
                    buildNotification(
                        context = context,
                        prayerId = prayerId,
                        date = occurrenceDate,
                        prayerName = hour.name,
                        kind = kind,
                    ),
                )
        }

        if (kind == AlarmKind.TARGET) {
            PrayerAlarmScheduler(context)
                .scheduleTomorrow(
                    hour,
                    occurrenceDate,
                )
        }
    }

    private fun buildNotification(
        context: Context,
        prayerId: PrayerHourId,
        date: LocalDate,
        prayerName: String,
        kind: AlarmKind,
    ): android.app.Notification {
        val openIntent = Intent(
            context,
            MainActivity::class.java,
        ).apply {
            flags =
                Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP

            putExtra(
                EXTRA_PRAYER_ID,
                prayerId.value,
            )
            putExtra(
                EXTRA_OCCURRENCE_DATE,
                date.toString(),
            )
        }

        val openPendingIntent =
            PendingIntent.getActivity(
                context,
                PrayerAlarmScheduler.requestCode(
                    prayerId,
                    date,
                    "open",
                ),
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                        PendingIntent.FLAG_IMMUTABLE,
            )

        val text = when (kind) {
            AlarmKind.DELAYED ->
                "$prayerName remains due."

            else ->
                "It is time to stop and pray."
        }

        return NotificationCompat
            .Builder(
                context,
                CHANNEL_ID,
            )
            .setSmallIcon(
                R.drawable.ic_stat_prayer,
            )
            .setContentTitle(
                "$prayerName prayer",
            )
            .setContentText(text)
            .setContentIntent(openPendingIntent)
            .setAutoCancel(true)
            .setPriority(
                NotificationCompat.PRIORITY_HIGH,
            )
            .setCategory(
                NotificationCompat.CATEGORY_REMINDER,
            )
            .setSound(soundUri(context))
            .addAction(
                R.drawable.ic_stat_prayer,
                "Prayed",
                PrayerActionReceiver.pendingIntent(
                    context,
                    prayerId,
                    date,
                    PrayerActionReceiver.ACTION_COMPLETE,
                ),
            )
            .addAction(
                R.drawable.ic_stat_prayer,
                "Delay 10 min",
                PrayerActionReceiver.pendingIntent(
                    context,
                    prayerId,
                    date,
                    PrayerActionReceiver.ACTION_DELAY,
                ),
            )
            .addAction(
                R.drawable.ic_stat_prayer,
                "Cannot pray",
                PrayerActionReceiver.pendingIntent(
                    context,
                    prayerId,
                    date,
                    PrayerActionReceiver.ACTION_CANNOT_PRAY,
                ),
            )
            .build()
    }

    private fun canPostNotifications(
        context: Context,
    ): Boolean =
        Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED

    private fun createNotificationChannel(
        context: Context,
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val audioAttributes =
            AudioAttributes.Builder()
                .setUsage(
                    AudioAttributes.USAGE_NOTIFICATION,
                )
                .setContentType(
                    AudioAttributes.CONTENT_TYPE_SONIFICATION,
                )
                .build()

        val channel = NotificationChannel(
            CHANNEL_ID,
            "Prayer hours",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description =
                "Notifications for scheduled prayer hours"

            setSound(
                soundUri(context),
                audioAttributes,
            )

            enableVibration(true)
        }

        context
            .getSystemService(
                NotificationManager::class.java,
            )
            .createNotificationChannel(channel)
    }

    private fun soundUri(
        context: Context,
    ): Uri =
        Uri.Builder()
            .scheme(
                ContentResolver.SCHEME_ANDROID_RESOURCE,
            )
            .authority(context.packageName)
            .appendPath(
                R.raw.prayer_bell.toString(),
            )
            .build()

    companion object {
        const val EXTRA_PRAYER_ID =
            "prayer_id"

        const val EXTRA_OCCURRENCE_DATE =
            "occurrence_date"

        const val EXTRA_ALARM_KIND =
            "alarm_kind"

        private const val CHANNEL_ID =
            "prayer_hours_v2"

        fun notificationId(
            prayerId: PrayerHourId,
            date: LocalDate,
        ): Int =
            "${prayerId.value}:$date".hashCode()
    }
}