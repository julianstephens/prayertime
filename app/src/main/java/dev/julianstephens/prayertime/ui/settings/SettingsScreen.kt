package dev.julianstephens.prayertime.ui.settings

import android.app.AlarmManager
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.julianstephens.prayertime.PrayerTimeViewModel
import dev.julianstephens.prayertime.data.NotificationFeedbackMode
import dev.julianstephens.prayertime.data.NotificationSoundSettings
import dev.julianstephens.prayertime.data.NotificationSoundSource
import dev.julianstephens.prayertime.data.NotificationVolumeMode
import dev.julianstephens.prayertime.data.PrayerPreferences
import dev.julianstephens.prayertime.model.PrayerHour
import dev.julianstephens.prayertime.model.PrayerHourId
import dev.julianstephens.prayertime.notifications.AlarmKind
import dev.julianstephens.prayertime.notifications.PrayerAlarmReceiver
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@Composable
fun EditPrayerDialog(
    hour: PrayerHour,
    canDelete: Boolean,
    onDismiss: () -> Unit,
    onSave: (PrayerHour) -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    var name by remember(hour.id, hour.name) { mutableStateOf(hour.name) }
    var time by remember(hour.id, hour.targetTime) { mutableStateOf(hour.targetTime) }
    var windowText by remember(hour.id, hour.windowMinutes) {
        mutableStateOf(hour.windowMinutes.toString())
    }
    var enabled by remember(hour.id, hour.enabled) { mutableStateOf(hour.enabled) }
    val windowMinutes = windowText.toIntOrNull()
    val valid = name.trim().isNotEmpty() &&
        windowMinutes != null &&
        windowMinutes in PrayerTimeViewModel.MIN_WINDOW_MINUTES..
            PrayerTimeViewModel.MAX_WINDOW_MINUTES

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit prayer time") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Name") },
                    singleLine = true,
                )

                Text("Time", style = MaterialTheme.typography.labelLarge)
                OutlinedButton(
                    onClick = {
                        TimePickerDialog(
                            context,
                            { _, hourOfDay, minute ->
                                time = LocalTime.of(hourOfDay, minute)
                            },
                            time.hour,
                            time.minute,
                            false,
                        ).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(time.format(DateTimeFormatter.ofPattern("h:mm a")))
                }

                Text("Prayer window", style = MaterialTheme.typography.labelLarge)
                WindowPresetRow(
                    values = listOf(30, 45, 60),
                    selected = windowMinutes,
                    onSelected = { windowText = it.toString() },
                )
                WindowPresetRow(
                    values = listOf(90, 120, 180),
                    selected = windowMinutes,
                    onSelected = { windowText = it.toString() },
                )
                OutlinedTextField(
                    value = windowText,
                    onValueChange = { value ->
                        windowText = value.filter(Char::isDigit)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Custom minutes") },
                    supportingText = {
                        Text(
                            "${PrayerTimeViewModel.MIN_WINDOW_MINUTES}–" +
                                "${PrayerTimeViewModel.MAX_WINDOW_MINUTES} minutes",
                        )
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                    ),
                    singleLine = true,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Notifications", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (enabled) "This prayer time is active."
                            else "No alarm will be scheduled.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }

                if (canDelete) {
                    TextButton(
                        onClick = onDelete,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            "Delete prayer time",
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        hour.copy(
                            name = name.trim(),
                            targetTime = time,
                            windowMinutes = windowMinutes ?: hour.windowMinutes,
                            enabled = enabled,
                        ),
                    )
                },
                enabled = valid,
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    hours: List<PrayerHour>,
    soundSettings: NotificationSoundSettings,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (PrayerHourId) -> Unit,
    onSoundSourceChanged: (NotificationSoundSource) -> Unit,
    onFeedbackModeChanged: (NotificationFeedbackMode) -> Unit,
    onSelectCustomSound: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var currentSettings by remember(soundSettings) {
        mutableStateOf(soundSettings)
    }
    var exactAlarmsAllowed by remember {
        mutableStateOf(canScheduleExactAlarms(context))
    }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                exactAlarmsAllowed = canScheduleExactAlarms(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun saveAdditionalSettings(updated: NotificationSoundSettings) {
        PrayerPreferences(context).saveNotificationSoundSettings(updated)
        currentSettings = updated
        onSoundSourceChanged(updated.source)
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                modifier = Modifier.statusBarsPadding(),
                title = { Text("Settings") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Back") }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                SettingsSectionHeader(
                    title = "Prayer schedule",
                    body = "Add, rename, remove, and adjust prayer times.",
                )
            }

            items(hours, key = { it.id.value }) { hour ->
                SettingsPrayerCard(hour = hour, onClick = { onEdit(hour.id) })
            }

            item {
                Button(
                    onClick = onAdd,
                    enabled = hours.size < PrayerTimeViewModel.MAX_PRAYER_HOURS,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 14.dp),
                ) { Text("Add prayer time") }
            }

            item {
                SettingsSectionHeader(
                    title = "Notification readiness",
                    body = "Confirm that Android can deliver prayer reminders as expected.",
                )
            }

            item {
                NotificationReadinessCard(
                    exactAlarmsAllowed = exactAlarmsAllowed,
                    hasEnabledPrayer = hours.any { it.enabled },
                    onOpenExactAlarmSettings = { openExactAlarmSettings(context) },
                    onTestNotification = { postTestNotification(context, hours) },
                )
            }

            item {
                SettingsSectionHeader(
                    title = "Sound",
                    body = "Choose how prayer notifications sound and interrupt you.",
                )
            }

            item {
                SoundSourceCard(
                    settings = currentSettings,
                    onSoundSourceChanged = onSoundSourceChanged,
                    onSelectCustomSound = onSelectCustomSound,
                )
            }

            item {
                FeedbackCard(
                    selected = currentSettings.feedbackMode,
                    onSelected = onFeedbackModeChanged,
                )
            }

            item {
                InterruptionCard(
                    overridePhoneSoundMode = currentSettings.overridePhoneSoundMode,
                    onOverrideChanged = {
                        saveAdditionalSettings(
                            currentSettings.copy(overridePhoneSoundMode = it),
                        )
                    },
                )
            }

            if (currentSettings.feedbackMode == NotificationFeedbackMode.SOUND_AND_VIBRATION) {
                item {
                    VolumeCard(
                        settings = currentSettings,
                        onVolumeModeChanged = {
                            saveAdditionalSettings(
                                currentSettings.copy(volumeMode = it),
                            )
                        },
                        onCustomVolumeChanged = {
                            saveAdditionalSettings(
                                currentSettings.copy(customVolumePercent = it),
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun NotificationReadinessCard(
    exactAlarmsAllowed: Boolean,
    hasEnabledPrayer: Boolean,
    onOpenExactAlarmSettings: () -> Unit,
    onTestNotification: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Precise alarms", style = MaterialTheme.typography.titleMedium)
            Text(
                if (exactAlarmsAllowed) {
                    "Allowed. Prayer reminders can be scheduled at their exact times."
                } else {
                    "Not allowed. Android may delay prayer reminders."
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (exactAlarmsAllowed) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
            )

            if (!exactAlarmsAllowed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Button(
                    onClick = onOpenExactAlarmSettings,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Allow precise alarms")
                }
            }

            OutlinedButton(
                onClick = onTestNotification,
                enabled = hasEnabledPrayer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Send test notification")
            }

            Text(
                if (hasEnabledPrayer) {
                    "The test uses the current sound, vibration, volume, and override settings."
                } else {
                    "Enable at least one prayer time to send a test notification."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SoundSourceCard(
    settings: NotificationSoundSettings,
    onSoundSourceChanged: (NotificationSoundSource) -> Unit,
    onSelectCustomSound: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Notification sound", style = MaterialTheme.typography.titleMedium)
            SoundChoice(
                title = "Built-in alarm sound",
                description = "Use the phone's default alarm tone.",
                selected = settings.source == NotificationSoundSource.SYSTEM_ALARM,
                onClick = { onSoundSourceChanged(NotificationSoundSource.SYSTEM_ALARM) },
            )
            SoundChoice(
                title = "Onboard prayer sound",
                description = "Use the sound included with Prayer Time.",
                selected = settings.source == NotificationSoundSource.ONBOARD,
                onClick = { onSoundSourceChanged(NotificationSoundSource.ONBOARD) },
            )
            SoundChoice(
                title = "Custom file",
                description = settings.customSoundName
                    ?: "Select an audio file from this device.",
                selected = settings.source == NotificationSoundSource.CUSTOM,
                onClick = {
                    if (settings.customSoundUri == null) onSelectCustomSound()
                    else onSoundSourceChanged(NotificationSoundSource.CUSTOM)
                },
            )
            OutlinedButton(
                onClick = onSelectCustomSound,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (settings.customSoundUri == null) "Choose custom audio"
                    else "Replace custom audio",
                )
            }
        }
    }
}

@Composable
private fun SoundChoice(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FeedbackCard(
    selected: NotificationFeedbackMode,
    onSelected: (NotificationFeedbackMode) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Notification feedback", style = MaterialTheme.typography.titleMedium)
            FilterChip(
                selected = selected == NotificationFeedbackMode.SOUND_AND_VIBRATION,
                onClick = {
                    onSelected(NotificationFeedbackMode.SOUND_AND_VIBRATION)
                },
                label = { Text("Sound and vibration") },
                modifier = Modifier.fillMaxWidth(),
            )
            FilterChip(
                selected = selected == NotificationFeedbackMode.VIBRATION_ONLY,
                onClick = {
                    onSelected(NotificationFeedbackMode.VIBRATION_ONLY)
                },
                label = { Text("Vibration only") },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Muted prayer times always post silently without sound or vibration.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun InterruptionCard(
    overridePhoneSoundMode: Boolean,
    onOverrideChanged: (Boolean) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("Override phone sound mode", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (overridePhoneSoundMode) {
                        "Prayer sounds may play while the phone is silent or set to vibrate."
                    } else {
                        "Respect the phone's silent and vibrate settings."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = overridePhoneSoundMode,
                onCheckedChange = onOverrideChanged,
            )
        }
    }
}

@Composable
private fun VolumeCard(
    settings: NotificationSoundSettings,
    onVolumeModeChanged: (NotificationVolumeMode) -> Unit,
    onCustomVolumeChanged: (Int) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Sound volume", style = MaterialTheme.typography.titleMedium)
            FilterChip(
                selected = settings.volumeMode == NotificationVolumeMode.PHONE_ALARM,
                onClick = { onVolumeModeChanged(NotificationVolumeMode.PHONE_ALARM) },
                label = { Text("Use phone alarm volume") },
                modifier = Modifier.fillMaxWidth(),
            )
            FilterChip(
                selected = settings.volumeMode == NotificationVolumeMode.CUSTOM,
                onClick = { onVolumeModeChanged(NotificationVolumeMode.CUSTOM) },
                label = { Text("Use custom volume") },
                modifier = Modifier.fillMaxWidth(),
            )

            if (settings.volumeMode == NotificationVolumeMode.CUSTOM) {
                Text(
                    "${settings.customVolumePercent}%",
                    style = MaterialTheme.typography.labelLarge,
                )
                Slider(
                    value = settings.customVolumePercent.toFloat(),
                    onValueChange = {
                        onCustomVolumeChanged(it.roundToInt().coerceIn(0, 100))
                    },
                    valueRange = 0f..100f,
                    steps = 9,
                )
                Text(
                    "Custom volume is applied to the selected sound without changing the phone's alarm volume.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SettingsPrayerCard(
    hour: PrayerHour,
    onClick: () -> Unit,
) {
    val formatter = DateTimeFormatter.ofPattern("h:mm a")
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(hour.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${hour.targetTime.format(formatter)} · ${hour.windowMinutes}-minute window",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                if (hour.enabled) "On" else "Off",
                style = MaterialTheme.typography.labelLarge,
                color = if (hour.enabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

@Composable
private fun WindowPresetRow(
    values: List<Int>,
    selected: Int?,
    onSelected: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        values.forEach { value ->
            FilterChip(
                selected = selected == value,
                onClick = { onSelected(value) },
                label = { Text("$value min") },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private fun canScheduleExactAlarms(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
    return context.getSystemService(AlarmManager::class.java)
        .canScheduleExactAlarms()
}

private fun openExactAlarmSettings(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return

    val exactAlarmIntent = Intent(
        Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
        Uri.parse("package:${context.packageName}"),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    val fallbackIntent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.parse("package:${context.packageName}"),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    runCatching { context.startActivity(exactAlarmIntent) }
        .onFailure { context.startActivity(fallbackIntent) }
}

private fun postTestNotification(
    context: Context,
    hours: List<PrayerHour>,
) {
    val hour = hours.firstOrNull { it.enabled } ?: return
    val testDate = LocalDate.now().plusDays(1)

    context.sendBroadcast(
        Intent(context, PrayerAlarmReceiver::class.java).apply {
            action = "${context.packageName}.notification.TEST"
            putExtra(PrayerAlarmReceiver.EXTRA_PRAYER_ID, hour.id.value)
            putExtra(
                PrayerAlarmReceiver.EXTRA_OCCURRENCE_DATE,
                testDate.toString(),
            )
            putExtra(
                PrayerAlarmReceiver.EXTRA_ALARM_KIND,
                AlarmKind.DELAYED.name,
            )
        },
    )
}
