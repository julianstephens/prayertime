package dev.julianstephens.prayertime.model

import java.time.LocalTime

@JvmInline
value class PrayerHourId(
    val value: String,
)

data class PrayerHour(
    val id: PrayerHourId,
    val name: String,
    val targetTime: LocalTime,
    val windowMinutes: Int = 60,
    val enabled: Boolean = true,
    val sortOrder: Int = 0,
    val completedToday: Boolean = false,
)

val defaultPrayerHours = listOf(
    PrayerHour(
        id = PrayerHourId("matins"),
        name = "Matins",
        targetTime = LocalTime.of(6, 0),
        windowMinutes = 60,
        sortOrder = 0,
    ),
    PrayerHour(
        id = PrayerHourId("sext"),
        name = "Sext",
        targetTime = LocalTime.of(12, 0),
        windowMinutes = 90,
        sortOrder = 1,
    ),
    PrayerHour(
        id = PrayerHourId("none"),
        name = "None",
        targetTime = LocalTime.of(15, 0),
        windowMinutes = 90,
        sortOrder = 2,
    ),
    PrayerHour(
        id = PrayerHourId("compline"),
        name = "Compline",
        targetTime = LocalTime.of(22, 0),
        windowMinutes = 60,
        sortOrder = 3,
    ),
)
