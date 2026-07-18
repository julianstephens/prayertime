package dev.julianstephens.prayertime.ui.settings

import android.app.TimePickerDialog
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.julianstephens.prayertime.PrayerTimeViewModel
import dev.julianstephens.prayertime.model.PrayerHour
import dev.julianstephens.prayertime.model.PrayerHourId
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable
fun EditPrayerDialog(
    hour: PrayerHour,
    canDelete: Boolean,
    onDismiss: () -> Unit,
    onSave: (PrayerHour) -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current

    var name by remember(
        hour.id,
        hour.name,
    ) {
        mutableStateOf(hour.name)
    }

    var time by remember(
        hour.id,
        hour.targetTime,
    ) {
        mutableStateOf(hour.targetTime)
    }

    var windowText by remember(
        hour.id,
        hour.windowMinutes,
    ) {
        mutableStateOf(
            hour.windowMinutes.toString(),
        )
    }

    var enabled by remember(
        hour.id,
        hour.enabled,
    ) {
        mutableStateOf(hour.enabled)
    }

    val windowMinutes =
        windowText.toIntOrNull()

    val valid =
        name.trim().isNotEmpty() &&
                windowMinutes != null &&
                windowMinutes in
                PrayerTimeViewModel
                    .MIN_WINDOW_MINUTES..
                PrayerTimeViewModel
                    .MAX_WINDOW_MINUTES

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Edit prayer time")
        },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(
                        rememberScrollState(),
                    ),
                verticalArrangement =
                    Arrangement.spacedBy(16.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                    },
                    modifier =
                        Modifier.fillMaxWidth(),
                    label = {
                        Text("Name")
                    },
                    singleLine = true,
                )

                Column(
                    verticalArrangement =
                        Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Time",
                        style = MaterialTheme
                            .typography.labelLarge,
                    )

                    OutlinedButton(
                        onClick = {
                            TimePickerDialog(
                                context,
                                {
                                        _,
                                        selectedHour,
                                        selectedMinute,
                                    ->
                                    time =
                                        LocalTime.of(
                                            selectedHour,
                                            selectedMinute,
                                        )
                                },
                                time.hour,
                                time.minute,
                                false,
                            ).show()
                        },
                        modifier =
                            Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            time.format(
                                DateTimeFormatter
                                    .ofPattern(
                                        "h:mm a",
                                    ),
                            ),
                        )
                    }
                }

                Column(
                    verticalArrangement =
                        Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Prayer window",
                        style = MaterialTheme
                            .typography.labelLarge,
                    )

                    WindowPresetRow(
                        values = listOf(
                            30,
                            45,
                            60,
                        ),
                        selected =
                            windowMinutes,
                        onSelected = {
                            windowText =
                                it.toString()
                        },
                    )

                    WindowPresetRow(
                        values = listOf(
                            90,
                            120,
                            180,
                        ),
                        selected =
                            windowMinutes,
                        onSelected = {
                            windowText =
                                it.toString()
                        },
                    )

                    OutlinedTextField(
                        value = windowText,
                        onValueChange = { value ->
                            windowText =
                                value.filter(
                                    Char::isDigit,
                                )
                        },
                        modifier =
                            Modifier.fillMaxWidth(),
                        label = {
                            Text(
                                "Custom minutes",
                            )
                        },
                        supportingText = {
                            Text(
                                "${PrayerTimeViewModel.MIN_WINDOW_MINUTES}–${PrayerTimeViewModel.MAX_WINDOW_MINUTES} minutes",
                            )
                        },
                        keyboardOptions =
                            KeyboardOptions(
                                keyboardType =
                                    KeyboardType.Number,
                            ),
                        singleLine = true,
                    )
                }

                Row(
                    modifier =
                        Modifier.fillMaxWidth(),
                    verticalAlignment =
                        Alignment.CenterVertically,
                ) {
                    Column(
                        modifier =
                            Modifier.weight(1f),
                    ) {
                        Text(
                            text = "Notifications",
                            style = MaterialTheme
                                .typography
                                .titleMedium,
                        )

                        Text(
                            text = if (enabled) {
                                "This prayer time is active."
                            } else {
                                "No alarms will be scheduled."
                            },
                            style = MaterialTheme
                                .typography
                                .bodySmall,
                            color = MaterialTheme
                                .colorScheme
                                .onSurfaceVariant,
                        )
                    }

                    Switch(
                        checked = enabled,
                        onCheckedChange = {
                            enabled = it
                        },
                    )
                }

                if (canDelete) {
                    TextButton(
                        onClick = onDelete,
                        modifier =
                            Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text =
                                "Delete prayer time",
                            color = MaterialTheme
                                .colorScheme.error,
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
                            windowMinutes =
                                windowMinutes
                                    ?: hour
                                        .windowMinutes,
                            enabled = enabled,
                        ),
                    )
                },
                enabled = valid,
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
            ) {
                Text("Cancel")
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    hours: List<PrayerHour>,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (PrayerHourId) -> Unit,
) {
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                modifier = Modifier.statusBarsPadding(),
                title = {
                    Text("Prayer schedule")
                },
                navigationIcon = {
                    TextButton(
                        onClick = onBack,
                    ) {
                        Text("Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding(),
            contentPadding = PaddingValues(
                horizontal = 20.dp,
                vertical = 20.dp,
            ),
            verticalArrangement =
                Arrangement.spacedBy(14.dp),
        ) {
            item {
                Column(
                    verticalArrangement =
                        Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text =
                            "${hours.size} prayer times",
                        style = MaterialTheme
                            .typography.headlineMedium,
                    )

                    Text(
                        text = "Add, rename, or remove prayer times and adjust each permissible window.",
                        style = MaterialTheme
                            .typography.bodyMedium,
                        color = MaterialTheme
                            .colorScheme
                            .onSurfaceVariant,
                    )
                }
            }

            items(
                items = hours,
                key = { it.id.value },
            ) { hour ->
                SettingsPrayerCard(
                    hour = hour,
                    onClick = {
                        onEdit(hour.id)
                    },
                )
            }

            item {
                Button(
                    onClick = onAdd,
                    enabled = hours.size <
                            PrayerTimeViewModel
                                .MAX_PRAYER_HOURS,
                    modifier =
                        Modifier.fillMaxWidth(),
                    contentPadding =
                        PaddingValues(
                            vertical = 14.dp,
                        ),
                ) {
                    Text("Add prayer time")
                }
            }

            if (
                hours.size >=
                PrayerTimeViewModel.MAX_PRAYER_HOURS
            ) {
                item {
                    Text(
                        text =
                            "The current limit is ${PrayerTimeViewModel.MAX_PRAYER_HOURS} prayer times.",
                        style = MaterialTheme
                            .typography.bodySmall,
                        color = MaterialTheme
                            .colorScheme
                            .onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsPrayerCard(
    hour: PrayerHour,
    onClick: () -> Unit,
) {
    val formatter =
        DateTimeFormatter.ofPattern("h:mm a")

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor =
                MaterialTheme.colorScheme
                    .surfaceContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment =
                Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement =
                    Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = hour.name,
                    style = MaterialTheme
                        .typography.titleMedium,
                )

                Text(
                    text =
                        "${hour.targetTime.format(formatter)} · ${hour.windowMinutes}-minute window",
                    style = MaterialTheme
                        .typography.bodyMedium,
                    color = MaterialTheme
                        .colorScheme
                        .onSurfaceVariant,
                )
            }

            Text(
                text = if (hour.enabled) {
                    "On"
                } else {
                    "Off"
                },
                style = MaterialTheme
                    .typography.labelLarge,
                color = if (hour.enabled) {
                    MaterialTheme
                        .colorScheme.primary
                } else {
                    MaterialTheme
                        .colorScheme
                        .onSurfaceVariant
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
        horizontalArrangement =
            Arrangement.spacedBy(8.dp),
    ) {
        values.forEach { value ->
            FilterChip(
                selected = selected == value,
                onClick = {
                    onSelected(value)
                },
                label = {
                    Text("$value min")
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}