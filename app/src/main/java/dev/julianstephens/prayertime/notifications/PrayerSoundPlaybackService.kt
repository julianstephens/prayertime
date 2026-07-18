package dev.julianstephens.prayertime.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import dev.julianstephens.prayertime.MainActivity
import dev.julianstephens.prayertime.R

class PrayerSoundPlaybackService : Service() {

    private var player: MediaPlayer? = null

    override fun onCreate() {
        super.onCreate()
        createPlaybackChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val sound = intent?.getStringExtra(EXTRA_SOUND_URI)
            ?.let(Uri::parse)
            ?: return stopAndDoNotRestart(startId)
        val volume = intent.getIntExtra(EXTRA_VOLUME_PERCENT, 70)
            .coerceIn(0, 100) / 100f
        val overridePhoneSoundMode = intent.getBooleanExtra(
            EXTRA_OVERRIDE_PHONE_SOUND_MODE,
            false,
        )

        startForeground(
            FOREGROUND_NOTIFICATION_ID,
            buildForegroundNotification(),
        )
        startPlayback(
            sound = sound,
            volume = volume,
            overridePhoneSoundMode = overridePhoneSoundMode,
            startId = startId,
        )
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        releasePlayer()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startPlayback(
        sound: Uri,
        volume: Float,
        overridePhoneSoundMode: Boolean,
        startId: Int,
    ) {
        releasePlayer()

        val newPlayer = MediaPlayer()
        player = newPlayer

        fun finishPlayback() {
            if (player === newPlayer) {
                releasePlayer()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf(startId)
            } else {
                runCatching { newPlayer.release() }
            }
        }

        runCatching {
            newPlayer.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(
                        if (overridePhoneSoundMode) {
                            AudioAttributes.USAGE_ALARM
                        } else {
                            AudioAttributes.USAGE_NOTIFICATION_EVENT
                        },
                    )
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            newPlayer.setDataSource(this, sound)
            newPlayer.setVolume(volume, volume)
            newPlayer.setOnPreparedListener { it.start() }
            newPlayer.setOnCompletionListener { finishPlayback() }
            newPlayer.setOnErrorListener { _, _, _ ->
                finishPlayback()
                true
            }
            newPlayer.prepareAsync()
        }.onFailure {
            finishPlayback()
        }
    }

    private fun releasePlayer() {
        player?.let { current ->
            runCatching {
                if (current.isPlaying) current.stop()
            }
            runCatching { current.reset() }
            runCatching { current.release() }
        }
        player = null
    }

    private fun buildForegroundNotification(): android.app.Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            FOREGROUND_NOTIFICATION_ID,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, PLAYBACK_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_prayer)
            .setContentTitle("Prayer Time")
            .setContentText("Playing prayer notification sound")
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun createPlaybackChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            PLAYBACK_CHANNEL_ID,
            "Prayer sound playback",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Temporary service used to play prayer sounds at a custom volume"
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
        }

        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    private fun stopAndDoNotRestart(startId: Int): Int {
        stopSelf(startId)
        return START_NOT_STICKY
    }

    companion object {
        private const val PLAYBACK_CHANNEL_ID = "prayer_sound_playback_v1"
        private const val FOREGROUND_NOTIFICATION_ID = 10_002
        private const val EXTRA_SOUND_URI = "sound_uri"
        private const val EXTRA_VOLUME_PERCENT = "volume_percent"
        private const val EXTRA_OVERRIDE_PHONE_SOUND_MODE = "override_phone_sound_mode"

        fun start(
            context: Context,
            sound: Uri,
            volumePercent: Int,
            overridePhoneSoundMode: Boolean,
        ) {
            val intent = Intent(context, PrayerSoundPlaybackService::class.java).apply {
                putExtra(EXTRA_SOUND_URI, sound.toString())
                putExtra(EXTRA_VOLUME_PERCENT, volumePercent.coerceIn(0, 100))
                putExtra(EXTRA_OVERRIDE_PHONE_SOUND_MODE, overridePhoneSoundMode)
            }
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
