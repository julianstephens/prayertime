package dev.julianstephens.prayertime.data

enum class NotificationSoundSource {
    SYSTEM_ALARM,
    ONBOARD,
    CUSTOM,
}

enum class NotificationFeedbackMode {
    SOUND_AND_VIBRATION,
    VIBRATION_ONLY,
}

enum class NotificationVolumeMode {
    PHONE_ALARM,
    CUSTOM,
}

data class NotificationSoundSettings(
    val source: NotificationSoundSource = NotificationSoundSource.ONBOARD,
    val feedbackMode: NotificationFeedbackMode = NotificationFeedbackMode.SOUND_AND_VIBRATION,
    val customSoundUri: String? = null,
    val customSoundName: String? = null,
    val overridePhoneSoundMode: Boolean = false,
    val volumeMode: NotificationVolumeMode = NotificationVolumeMode.PHONE_ALARM,
    val customVolumePercent: Int = 70,
)
