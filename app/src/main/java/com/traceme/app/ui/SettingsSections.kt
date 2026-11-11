package com.traceme.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.traceme.app.data.UserSettings
import com.traceme.app.data.shortDuration

data class NumberPreset(val label: String, val number: String)

val EmergencyPresets = listOf(
    NumberPreset("Bangladesh", "999"),
    NumberPreset("India", "112"),
    NumberPreset("Pakistan", "1122"),
    NumberPreset("USA", "911"),
    NumberPreset("UK", "999"),
    NumberPreset("UAE", "999"),
    NumberPreset("Australia", "000")
)

@Composable
fun ChipRow(
    title: String,
    options: List<Pair<Int, String>>,
    selected: Int,
    onSelect: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.bodyMedium, color = InkSoft)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { (value, label) ->
                FilterChip(
                    selected = selected == value,
                    onClick = { onSelect(value) },
                    label = { Text(label) }
                )
            }
        }
    }
}

@Composable
fun EmergencyNumberSection(
    settings: UserSettings,
    onChange: (UserSettings) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader("Emergency number", accent = Danger)
        Text(
            "Who we call when a check-in is missed. Pick your country or type the number yourself.",
            style = MaterialTheme.typography.bodySmall,
            color = InkSoft
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            EmergencyPresets.forEach { preset ->
                FilterChip(
                    selected = settings.emergencyNumber == preset.number &&
                        settings.countryLabel == preset.label,
                    onClick = {
                        onChange(
                            settings.copy(
                                emergencyNumber = preset.number,
                                countryLabel = preset.label
                            )
                        )
                    },
                    label = { Text("${preset.label} ${preset.number}") }
                )
            }
        }
        OutlinedTextField(
            value = settings.emergencyNumber,
            onValueChange = { typed ->
                val cleaned = typed.filter { it.isDigit() || it == '+' }.take(15)
                onChange(settings.copy(emergencyNumber = cleaned, countryLabel = "Custom"))
            },
            label = { Text("Emergency number") },
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun TimingSection(
    settings: UserSettings,
    onChange: (UserSettings) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SectionHeader("Check-in timing", accent = Clay)
            Text(
                "Slide to any value — no fixed menus. Each step lands on a whole 30 seconds and clicks as you cross it.",
                style = MaterialTheme.typography.bodySmall,
                color = InkSoft
            )
        }

        StepSlider(
            label = "Ask if I haven't moved for",
            value = settings.stuckSeconds,
            range = 60..900,
            step = 30,
            format = { shortDuration(it) },
            hint = "30 second steps · 1 min to 15 min",
            onChange = { onChange(settings.copy(stuckSeconds = it)) }
        )

        StepSlider(
            label = "Time I get to answer",
            value = settings.responseSeconds,
            range = 15..180,
            step = 15,
            format = { shortDuration(it) },
            hint = "15 second steps · 15 s to 3 min",
            onChange = { onChange(settings.copy(responseSeconds = it)) }
        )

        StepSlider(
            label = "Default pause length",
            value = settings.quietSeconds,
            range = 900..3600,
            step = 30,
            format = { shortDuration(it) },
            hint = "30 second steps · 15 min to 1 hour (so 20 min 30 s is allowed)",
            onChange = { onChange(settings.copy(quietSeconds = it)) }
        )
    }
}
