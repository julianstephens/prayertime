package dev.julianstephens.prayertime

import android.Manifest
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.julianstephens.prayertime.data.NotificationFeedbackMode
import dev.julianstephens.prayertime.data.NotificationSoundSettings
import dev.julianstephens.prayertime.data.NotificationSoundSource
import dev.julianstephens.prayertime.model.PrayerHour
import dev.julianstephens.prayertime.model.PrayerHourId
import dev.julianstephens.prayertime.notifications.PrayerAlarmReceiver
import dev.julianstephens.prayertime.ui.settings.EditPrayerDialog
import dev.julianstephens.prayertime.ui.settings.SettingsScreen
import dev.julianstephens.prayertime.ui.theme.PrayerTimeTheme
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class MainActivity : ComponentActivity() {

    private val viewModel: PrayerTimeViewModel by viewModels()

    private val notificationPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) {
            viewModel.refresh()
        }

    private val customSoundLauncher =
        registerForActivityResult(
            ActivityResultContracts.OpenDocument(),
        ) { uri ->
            uri ?: return@registerForActivityResult

            runCatching {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }

            viewModel.updateCustomSound(
                uri = uri.toString(),
                displayName = customSoundDisplayName(uri),
            )
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermissionWhenNeeded()

        val openedPrayerId = intent
            ?.getStringExtra(PrayerAlarmReceiver.EXTRA_PRAYER_ID)
            ?.let(::PrayerHourId)

        setContent {
            PrayerTimeTheme {
                PrayerTimeApp(
                    viewModel = viewModel,
                    initiallyOpenedPrayerId = openedPrayerId,
                    onSelectCustomSound = {
                        customSoundLauncher.launch(arrayOf("audio/*"))
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
    }

    private fun customSoundDisplayName(uri: android.net.Uri): String? =
        contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0) cursor.getString(index) else null
        }

    private fun requestNotificationPermissionWhenNeeded() {
        if (android.os.Build.VERSION.SDK_INT < 33) return

        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED

        if (!granted) {
            notificationPermissionLauncher.launch(
                Manifest.permission.POST_NOTIFICATIONS,
            )
        }
    }
}

@Composable
private fun PrayerTimeApp(
    viewModel: PrayerTimeViewModel,
    initiallyOpenedPrayerId: PrayerHourId?,
    onSelectCustomSound: () -> Unit,
) {
    val hours by viewModel.hours.collectAsStateWithLifecycle()
    val soundSettings by viewModel.soundSettings.collectAsStateWithLifecycle()

    var showingSettings by rememberSaveable { mutableStateOf(false) }
    var editingId by remember { mutableStateOf<PrayerHourId?>(null) }
    val editingHour = editingId?.let { id ->
        hours.firstOrNull { it.id == id }
    }

    if (showingSettings) {
        SettingsScreen(
            hours = hours,
            soundSettings = soundSettings,
            onBack = { showingSettings = false },
            onAdd = { editingId = viewModel.addPrayerHour() },
            onEdit = { editingId = it },
            onSoundSourceChanged = viewModel::updateSoundSource,
            onFeedbackModeChanged = viewModel::updateFeedbackMode,
            onSelectCustomSound = onSelectCustomSound,
        )
    } else {
        TodayContent(
            hours = hours,
            soundSettings = soundSettings,
            initiallyOpenedPrayerId = initiallyOpenedPrayerId,
            onOpenSettings = { showingSettings = true },
            onTimeChanged = viewModel::updateTime,
            onEnabledChanged = viewModel::updateEnabled,
            onSoundEnabledChanged = viewModel::updateSoundEnabled,
            onCompletedChanged = viewModel::markCompleted,
        )
    }

    if (editingHour != null) {
        EditPrayerDialog(
            hour = editingHour,
            canDelete = hours.size > PrayerTimeViewModel.MIN_PRAYER_HOURS,
            onDismiss = { editingId = null },
            onSave = { updated ->
                viewModel.updateHour(updated)
                editingId = null
            },
            onDelete = {
                viewModel.deletePrayerHour(editingHour.id)
                editingId = null
            },
        )
    }
}

@Composable
private fun TodayContent(
    hours: List<PrayerHour>,
    soundSettings: NotificationSoundSettings,
    initiallyOpenedPrayerId: PrayerHourId?,
    onOpenSettings: () -> Unit,
    onTimeChanged: (PrayerHourId, LocalTime) -> Unit,
    onEnabledChanged: (PrayerHourId, Boolean) -> Unit,
    onSoundEnabledChanged: (PrayerHourId, Boolean) -> Unit,
    onCompletedChanged: (PrayerHourId, Boolean) -> Unit,
) {
    val now = LocalDateTime.now()
    val nextPrayer = findNextPrayer(hours, now)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0),
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.surfaceContainerLowest,
                        ),
                    ),
                ),
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .statusBarsPadding()
                    .navigationBarsPadding(),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = 24.dp,
                    bottom = 32.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item { AppHeader(onOpenSettings) }
                item {
                    NextPrayerCard(
                        nextPrayer = nextPrayer,
                        now = now,
                        onMarkCompleted = { id ->
                            onCompletedChanged(id, true)
                        },
                    )
                }
                item {
                    SectionHeader(
                        title = "Daily hours",
                        subtitle = feedbackSummary(soundSettings),
                    )
                }
                itemsIndexed(
                    items = hours,
                    key = { _, hour -> hour.id.value },
                ) { index, hour ->
                    PrayerHourRow(
                        hour = hour,
                        now = now,
                        highlighted = hour.id == initiallyOpenedPrayerId,
                        isLast = index == hours.lastIndex,
                        onTimeChanged = { onTimeChanged(hour.id, it) },
                        onEnabledChanged = { onEnabledChanged(hour.id, it) },
                        onSoundEnabledChanged = {
                            onSoundEnabledChanged(hour.id, it)
                        },
                        onCompletedChanged = {
                            onCompletedChanged(hour.id, it)
                        },
                    )
                }
                item { AppFooter() }
            }
        }
    }
}

@Composable
private fun AppHeader(onOpenSettings: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                LocalDate.now().format(
                    DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL),
                ),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                "Prayer Time",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                "A daily rhythm of interruption and attention.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onOpenSettings) { Text("Settings") }
    }
}

@Composable
private fun NextPrayerCard(
    nextPrayer: NextPrayer?,
    now: LocalDateTime,
    onMarkCompleted: (PrayerHourId) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(
                when {
                    nextPrayer == null -> "TODAY COMPLETE"
                    nextPrayer.isDue -> "PRAYER WINDOW OPEN"
                    nextPrayer.isTomorrow -> "NEXT PRAYER · TOMORROW"
                    else -> "NEXT PRAYER"
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f),
            )

            if (nextPrayer == null) {
                Text(
                    "The hours are complete",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    "The next cycle begins tomorrow.",
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.76f),
                )
                return@Column
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        nextPrayer.hour.name,
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        nextPrayer.hour.targetTime.format(
                            DateTimeFormatter.ofPattern("h:mm a"),
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f),
                    )
                }
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                ) {
                    Text(
                        nextPrayerRelativeText(nextPrayer, now),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Text(
                if (nextPrayer.isDue) {
                    "The ${nextPrayer.hour.windowMinutes}-minute prayer window is open."
                } else {
                    "You will be notified at the configured time."
                },
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.74f),
            )

            if (nextPrayer.isDue) {
                Button(
                    onClick = { onMarkCompleted(nextPrayer.hour.id) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    contentPadding = PaddingValues(vertical = 14.dp),
                ) { Text("Mark as prayed") }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PrayerHourRow(
    hour: PrayerHour,
    now: LocalDateTime,
    highlighted: Boolean,
    isLast: Boolean,
    onTimeChanged: (LocalTime) -> Unit,
    onEnabledChanged: (Boolean) -> Unit,
    onSoundEnabledChanged: (Boolean) -> Unit,
    onCompletedChanged: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val status = prayerStatus(hour, now)

    Column(modifier = Modifier.fillMaxWidth()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(if (hour.enabled) 1f else 0.52f),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(
                containerColor = when {
                    highlighted -> MaterialTheme.colorScheme.secondaryContainer
                    status == PrayerUiStatus.DUE ->
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                    else -> MaterialTheme.colorScheme.surfaceContainer
                },
            ),
            border = if (highlighted) CardDefaults.outlinedCardBorder() else null,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StatusMarker(status)
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(hour.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            statusText(status),
                            style = MaterialTheme.typography.bodySmall,
                            color = statusColor(status),
                        )
                    }
                    Switch(
                        checked = hour.enabled,
                        onCheckedChange = onEnabledChanged,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        modifier = Modifier.clickable(enabled = hour.enabled) {
                            TimePickerDialog(
                                context,
                                { _, selectedHour, selectedMinute ->
                                    onTimeChanged(
                                        LocalTime.of(selectedHour, selectedMinute),
                                    )
                                },
                                hour.targetTime.hour,
                                hour.targetTime.minute,
                                false,
                            ).show()
                        },
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                    ) {
                        Text(
                            hour.targetTime.format(DateTimeFormatter.ofPattern("h:mm a")),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "${hour.windowMinutes} min window",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Checkbox(
                        checked = hour.completedToday,
                        onCheckedChange = onCompletedChanged,
                        enabled = hour.enabled,
                        colors = CheckboxDefaults.colors(
                            checkedColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                    Text(
                        "Prayed",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            if (hour.soundEnabled) "Sound on" else "Muted",
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Text(
                            if (hour.soundEnabled) {
                                "Uses the sound behavior selected in Settings."
                            } else {
                                "This prayer will vibrate without sound."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = hour.soundEnabled,
                        onCheckedChange = onSoundEnabledChanged,
                        enabled = hour.enabled,
                    )
                }

                if (highlighted && !hour.completedToday) {
                    Text(
                        "Opened from this prayer notification",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        if (!isLast) Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun StatusMarker(status: PrayerUiStatus) {
    Box(
        modifier = Modifier
            .size(12.dp)
            .background(statusColor(status), CircleShape),
    )
}

@Composable
private fun statusColor(status: PrayerUiStatus): Color = when (status) {
    PrayerUiStatus.COMPLETED -> MaterialTheme.colorScheme.primary
    PrayerUiStatus.DUE -> MaterialTheme.colorScheme.tertiary
    PrayerUiStatus.UPCOMING -> MaterialTheme.colorScheme.secondary
    PrayerUiStatus.PASSED -> MaterialTheme.colorScheme.outline
    PrayerUiStatus.DISABLED -> MaterialTheme.colorScheme.outlineVariant
}

private fun statusText(status: PrayerUiStatus): String = when (status) {
    PrayerUiStatus.COMPLETED -> "Completed today"
    PrayerUiStatus.DUE -> "Prayer window open"
    PrayerUiStatus.UPCOMING -> "Scheduled"
    PrayerUiStatus.PASSED -> "Window closed"
    PrayerUiStatus.DISABLED -> "Notifications disabled"
}

@Composable
private fun AppFooter() {
    Column(
        modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
        )
        Spacer(Modifier.height(20.dp))
        Text(
            "Made with love by the grace of God.",
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun feedbackSummary(settings: NotificationSoundSettings): String =
    if (settings.feedbackMode == NotificationFeedbackMode.VIBRATION_ONLY) {
        "Vibration only"
    } else {
        when (settings.source) {
            NotificationSoundSource.SYSTEM_ALARM -> "System alarm sound"
            NotificationSoundSource.ONBOARD -> "Onboard sound"
            NotificationSoundSource.CUSTOM -> "Custom sound"
        }
    }

private fun findNextPrayer(
    hours: List<PrayerHour>,
    now: LocalDateTime,
): NextPrayer? {
    val enabledHours = hours.filter { it.enabled }.sortedBy { it.targetTime }
    if (enabledHours.isEmpty()) return null

    val due = enabledHours.firstOrNull { hour ->
        if (hour.completedToday) return@firstOrNull false
        val target = now.toLocalDate().atTime(hour.targetTime)
        val closes = target.plusMinutes(hour.windowMinutes.toLong())
        !now.isBefore(target) && now.isBefore(closes)
    }
    if (due != null) {
        return NextPrayer(
            due,
            now.toLocalDate().atTime(due.targetTime),
            isDue = true,
            isTomorrow = false,
        )
    }

    val upcoming = enabledHours.firstOrNull { hour ->
        !hour.completedToday && now.toLocalTime().isBefore(hour.targetTime)
    }
    if (upcoming != null) {
        return NextPrayer(
            upcoming,
            now.toLocalDate().atTime(upcoming.targetTime),
            isDue = false,
            isTomorrow = false,
        )
    }

    val firstTomorrow = enabledHours.first()
    return NextPrayer(
        firstTomorrow,
        now.toLocalDate().plusDays(1).atTime(firstTomorrow.targetTime),
        isDue = false,
        isTomorrow = true,
    )
}

private fun nextPrayerRelativeText(
    nextPrayer: NextPrayer,
    now: LocalDateTime,
): String {
    if (nextPrayer.isDue) {
        val closesAt = nextPrayer.dateTime.plusMinutes(
            nextPrayer.hour.windowMinutes.toLong(),
        )
        return "${Duration.between(now, closesAt).toMinutes().coerceAtLeast(1)} min left"
    }

    val totalMinutes = Duration.between(now, nextPrayer.dateTime)
        .toMinutes()
        .coerceAtLeast(0)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60

    return when {
        hours >= 24 -> "Tomorrow"
        hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
        hours > 0 -> "${hours}h"
        minutes > 0 -> "${minutes}m"
        else -> "Now"
    }
}

private fun prayerStatus(
    hour: PrayerHour,
    now: LocalDateTime,
): PrayerUiStatus {
    if (!hour.enabled) return PrayerUiStatus.DISABLED
    if (hour.completedToday) return PrayerUiStatus.COMPLETED

    val target = now.toLocalDate().atTime(hour.targetTime)
    val closes = target.plusMinutes(hour.windowMinutes.toLong())
    return when {
        now.isBefore(target) -> PrayerUiStatus.UPCOMING
        now.isBefore(closes) -> PrayerUiStatus.DUE
        else -> PrayerUiStatus.PASSED
    }
}

private data class NextPrayer(
    val hour: PrayerHour,
    val dateTime: LocalDateTime,
    val isDue: Boolean,
    val isTomorrow: Boolean,
)

private enum class PrayerUiStatus {
    COMPLETED,
    DUE,
    UPCOMING,
    PASSED,
    DISABLED,
}
