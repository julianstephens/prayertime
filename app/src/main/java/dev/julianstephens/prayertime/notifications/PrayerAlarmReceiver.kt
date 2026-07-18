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
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.julianstephens.prayertime.MainActivity
import dev.julianstephens.prayertime.R
import dev.julianstephens.prayertime.data.NotificationFeedbackMode
import dev.julianstephens.prayertime.data.NotificationSoundSettings
import dev.julianstephens.prayertime.data.NotificationSoundSource
import dev.julianstephens.prayertime.data.NotificationVolumeMode
import dev.julianstephens.prayertime.data.PrayerPreferences
import dev.julianstephens.prayertime.data.PrayerResolution
import dev.julianstephens.prayertime.model.PrayerHourId
import java.time.LocalDate
import java.util.Collections
import kotlin.math.absoluteValue

class PrayerAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val prayerId = intent.getStringExtra(EXTRA_PRAYER_ID)
            ?.let(::PrayerHourId)
            ?: return
        val occurrenceDate = intent.getStringExtra(EXTRA_OCCURRENCE_DATE)
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: return
        val kind = intent.getStringExtra(EXTRA_ALARM_KIND)
            ?.let { runCatching { AlarmKind.valueOf(it) }.getOrNull() }
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

        val settings = preferences.loadNotificationSoundSettings()
        val vibrationEnabled = hour.soundEnabled
        val soundAllowedByPhone = settings.overridePhoneSoundMode ||
            context.getSystemService(AudioManager::class.java).ringerMode ==
            AudioManager.RINGER_MODE_NORMAL
        val soundRequested =
            hour.soundEnabled &&
                settings.feedbackMode == NotificationFeedbackMode.SOUND_AND_VIBRATION &&
                soundAllowedByPhone
        val resolvedSound = if (soundRequested) {
            resolveSoundUri(context, settings)
        } else {
            null
        }
        val useCustomVolume =
            soundRequested &&
                settings.volumeMode == NotificationVolumeMode.CUSTOM
        val channelSound = if (useCustomVolume) null else resolvedSound

        val channelId = createNotificationChannel(
            context = context,
            sound = channelSound,
            vibrationEnabled = vibrationEnabled,
            settings = settings,
            manualPlayback = useCustomVolume,
        )

        if (canPostNotifications(context)) {
            NotificationManagerCompat.from(context).notify(
                notificationId(prayerId, occurrenceDate),
                buildNotification(
                    context = context,
                    channelId = channelId,
                    sound = channelSound,
                    vibrationEnabled = vibrationEnabled,
                    prayerId = prayerId,
                    date = occurrenceDate,
                    prayerName = hour.name,
                    kind = kind,
                ),
            )

            if (useCustomVolume && resolvedSound != null) {
                playAtCustomVolume(
                    context = context,
                    sound = resolvedSound,
                    settings = settings,
                )
            }
        }

        if (kind == AlarmKind.TARGET) {
            PrayerAlarmScheduler(context).scheduleTomorrow(
                hour,
                occurrenceDate,
            )
        }
    }

    private fun buildNotification(
        context: Context,
        channelId: String,
        sound: Uri?,
        vibrationEnabled: Boolean,
        prayerId: PrayerHourId,
        date: LocalDate,
        prayerName: String,
        kind: AlarmKind,
    ): android.app.Notification {
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_PRAYER_ID, prayerId.value)
            putExtra(EXTRA_OCCURRENCE_DATE, date.toString())
        }
        val openPendingIntent = PendingIntent.getActivity(
            context,
            PrayerAlarmScheduler.requestCode(prayerId, date, "open"),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val text = if (kind == AlarmKind.DELAYED) {
            "$prayerName remains due."
        } else {
            "It is time to stop and pray."
        }

        return NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_prayer)
            .setContentTitle("$prayerName prayer")
            .setContentText(text)
            .setContentIntent(openPendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .apply {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                    setSound(sound, AudioManager.STREAM_ALARM)
                    setVibrate(
                        if (vibrationEnabled) VIBRATION_PATTERN else null,
                    )
                }
            }
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

    private fun canPostNotifications(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED

    private fun createNotificationChannel(
        context: Context,
        sound: Uri?,
        vibrationEnabled: Boolean,
        settings: NotificationSoundSettings,
        manualPlayback: Boolean,
    ): String {
        val channelId = channelId(
            sound = sound,
            vibrationEnabled = vibrationEnabled,
            settings = settings,
            manualPlayback = manualPlayback,
        )
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return channelId

        val channelName = when {
            !vibrationEnabled -> "Prayer hours · silent"
            manualPlayback -> "Prayer hours · custom volume"
            sound == null -> "Prayer hours · vibration"
            settings.overridePhoneSoundMode -> "Prayer hours · interrupting sound"
            else -> "Prayer hours · sound"
        }

        val channel = NotificationChannel(
            channelId,
            channelName,
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Notifications for scheduled prayer hours"
            enableVibration(vibrationEnabled)
            vibrationPattern = if (vibrationEnabled) VIBRATION_PATTERN else null
            setSound(
                sound,
                if (sound != null) alarmAudioAttributes() else null,
            )
        }

        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
        return channelId
    }

    private fun channelId(
        sound: Uri?,
        vibrationEnabled: Boolean,
        settings: NotificationSoundSettings,
        manualPlayback: Boolean,
    ): String {
        if (!vibrationEnabled) return "prayer_hours_v6_silent"
        if (sound == null && !manualPlayback) return "prayer_hours_v6_vibration"

        val behavior = if (settings.overridePhoneSoundMode) "override" else "respect"
        if (manualPlayback) {
            return "prayer_hours_v6_manual_${behavior}_${settings.customVolumePercent}"
        }

        val source = when (settings.source) {
            NotificationSoundSource.SYSTEM_ALARM -> "system"
            NotificationSoundSource.ONBOARD -> "onboard"
            NotificationSoundSource.CUSTOM ->
                "custom_${sound.toString().hashCode().absoluteValue}"
        }
        return "prayer_hours_v6_${behavior}_$source"
    }

    private fun playAtCustomVolume(
        context: Context,
        sound: Uri,
        settings: NotificationSoundSettings,
    ) {
        val player = MediaPlayer()
        val volume = settings.customVolumePercent
            .coerceIn(0, 100) / 100f

        fun releasePlayer() {
            activePlayers.remove(player)
            runCatching { player.release() }
        }

        runCatching {
            player.setAudioAttributes(alarmAudioAttributes())
            player.setDataSource(context, sound)
            player.setVolume(volume, volume)
            player.setOnPreparedListener { it.start() }
            player.setOnCompletionListener { releasePlayer() }
            player.setOnErrorListener { _, _, _ ->
                releasePlayer()
                true
            }
            activePlayers.add(player)
            player.prepareAsync()
        }.onFailure {
            releasePlayer()
        }
    }

    private fun alarmAudioAttributes(): AudioAttributes =
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

    private fun resolveSoundUri(
        context: Context,
        settings: NotificationSoundSettings,
    ): Uri = when (settings.source) {
        NotificationSoundSource.SYSTEM_ALARM ->
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                ?: onboardSoundUri(context)

        NotificationSoundSource.ONBOARD -> onboardSoundUri(context)

        NotificationSoundSource.CUSTOM ->
            settings.customSoundUri
                ?.let(Uri::parse)
                ?: onboardSoundUri(context)
    }

    private fun onboardSoundUri(context: Context): Uri =
        Uri.Builder()
            .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
            .authority(context.packageName)
            .appendPath(R.raw.prayer_bell.toString())
            .build()

    companion object {
        const val EXTRA_PRAYER_ID = "prayer_id"
        const val EXTRA_OCCURRENCE_DATE = "occurrence_date"
        const val EXTRA_ALARM_KIND = "alarm_kind"

        private val VIBRATION_PATTERN = longArrayOf(0L, 400L, 200L, 400L)
        private val activePlayers = Collections.synchronizedSet(
            mutableSetOf<MediaPlayer>(),
        )

        fun notificationId(
            prayerId: PrayerHourId,
            date: LocalDate,
        ): Int = "${prayerId.value}:$date".hashCode()
    }
}
