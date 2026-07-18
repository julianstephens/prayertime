package dev.julianstephens.prayertime.model

import java.time.LocalTime

enum class PrayerHourId {
    MATINS,
    SEXT,
    NONE,
    COMPLINE,
}

data class PrayerHour(
    val id: PrayerHourId,
    val name: String,
    val targetTime: LocalTime,
    val enabled: Boolean = true,
    val completedToday: Boolean = false,
)

val defaultPrayerHours = listOf(
    PrayerHour(
        id = PrayerHourId.MATINS,
        name = "Matins",
        targetTime = LocalTime.of(6, 0),
    ),
    PrayerHour(
        id = PrayerHourId.SEXT,
        name = "Sext",
        targetTime = LocalTime.of(12, 0),
    ),
    PrayerHour(
        id = PrayerHourId.NONE,
        name = "None",
        targetTime = LocalTime.of(15, 0),
    ),
    PrayerHour(
        id = PrayerHourId.COMPLINE,
        name = "Compline",
        targetTime = LocalTime.of(22, 0),
    ),
)