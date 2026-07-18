package dev.julianstephens.prayertime


import android.Manifest
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.julianstephens.prayertime.model.PrayerHour
import dev.julianstephens.prayertime.model.PrayerHourId
import dev.julianstephens.prayertime.notifications.PrayerAlarmReceiver
import dev.julianstephens.prayertime.ui.theme.PrayerTimeTheme
import java.time.LocalTime
import java.time.format.DateTimeFormatter


class MainActivity : ComponentActivity() {

    private val viewModel: PrayerTimeViewModel by viewModels()

    private val notificationPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) {
            viewModel.refresh()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestNotificationPermissionWhenNeeded()

        val openedPrayerId = intent
            ?.getStringExtra(PrayerAlarmReceiver.EXTRA_PRAYER_ID)
            ?.let { value ->
                runCatching {
                    PrayerHourId.valueOf(value)
                }.getOrNull()
            }

        setContent {
            PrayerTimeTheme {
                PrayerTimeApp(
                    viewModel = viewModel,
                    initiallyOpenedPrayerId = openedPrayerId,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
    }

    private fun requestNotificationPermissionWhenNeeded() {
        if (android.os.Build.VERSION.SDK_INT < 33) {
            return
        }

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
@OptIn(ExperimentalMaterial3Api::class)
private fun PrayerTimeApp(
    viewModel: PrayerTimeViewModel,
    initiallyOpenedPrayerId: PrayerHourId?,
) {
    val hours by viewModel.hours.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Prayer Time")
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Spacer(Modifier.height(4.dp))

                Text(
                    text = "Today",
                    style = MaterialTheme.typography.headlineMedium,
                )

                Text(
                    text = "Four interruptions for attention to God.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            items(
                items = hours,
                key = { it.id },
            ) { hour ->
                PrayerHourCard(
                    hour = hour,
                    highlighted = hour.id == initiallyOpenedPrayerId,
                    onTimeChanged = { time ->
                        viewModel.updateTime(hour.id, time)
                    },
                    onEnabledChanged = { enabled ->
                        viewModel.updateEnabled(hour.id, enabled)
                    },
                    onCompletedChanged = { completed ->
                        viewModel.markCompleted(hour.id, completed)
                    },
                )
            }
        }
    }
}

@Composable
private fun PrayerHourCard(
    hour: PrayerHour,
    highlighted: Boolean,
    onTimeChanged: (LocalTime) -> Unit,
    onEnabledChanged: (Boolean) -> Unit,
    onCompletedChanged: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val timeFormatter = DateTimeFormatter.ofPattern("h:mm a")

    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = hour.name,
                        style = MaterialTheme.typography.titleLarge,
                    )

                    Text(
                        text = hour.targetTime.format(timeFormatter),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Switch(
                    checked = hour.enabled,
                    onCheckedChange = onEnabledChanged,
                )
            }

            if (highlighted) {
                Spacer(Modifier.height(8.dp))

                Text(
                    text = "It is time to pray.",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = {
                        TimePickerDialog(
                            context,
                            { _, selectedHour, selectedMinute ->
                                onTimeChanged(
                                    LocalTime.of(
                                        selectedHour,
                                        selectedMinute,
                                    ),
                                )
                            },
                            hour.targetTime.hour,
                            hour.targetTime.minute,
                            false,
                        ).show()
                    },
                ) {
                    Text("Change time")
                }

                Spacer(Modifier.weight(1f))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = hour.completedToday,
                        onCheckedChange = onCompletedChanged,
                        enabled = hour.enabled,
                    )

                    Text("Prayed")
                }
            }

            if (highlighted && !hour.completedToday) {
                Spacer(Modifier.height(8.dp))

                Button(
                    onClick = {
                        onCompletedChanged(true)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Mark prayer completed")
                }
            }
        }
    }
}

//@Composable
//private fun QaumoText() {
//    Column(
//        verticalArrangement = Arrangement.spacedBy(12.dp),
//    ) {
//        Text(
//            """
//            In the name of the Father,
//            and of the Son,
//            and of the Holy Spirit.
//            """.trimIndent(),
//        )
//
//        Text(
//            """
//            Glory be to him,
//            and may his grace and mercy
//            be upon us forever.
//            """.trimIndent(),
//        )
//
//        Text(
//            """
//            Holy God,
//            Holy Mighty,
//            Holy Immortal,
//            have mercy on us.
//            """.trimIndent(),
//            style = MaterialTheme.typography.bodyLarge,
//        )
//
//        Text("Repeat three times, with prostrations.")
//
//        Text(
//            """
//            Our Father in heaven,
//            hallowed be your name.
//            Your kingdom come.
//            Your will be done,
//            on earth as in heaven.
//
//            Give us today our daily bread.
//            Forgive us our sins,
//            as we forgive those who sin against us.
//            Save us from the time of trial,
//            and deliver us from evil.
//            """.trimIndent(),
//        )
//
//        Text(
//            text = "Let me rise and serve you in peace.",
//            style = MaterialTheme.typography.bodyLarge,
//        )
//    }
//}