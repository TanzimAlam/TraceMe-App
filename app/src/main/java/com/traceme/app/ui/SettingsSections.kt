package com.traceme.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
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
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel("Emergency number")
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
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionLabel("Check-in timing")
        ChipRow(
            title = "Ask if I haven't moved for",
            options = listOf(60 to "1 min", 120 to "2 min", 180 to "3 min", 300 to "5 min"),
            selected = settings.stuckSeconds,
            onSelect = { onChange(settings.copy(stuckSeconds = it)) }
        )
        ChipRow(
            title = "Time I get to answer",
            options = listOf(30 to "30 s", 45 to "45 s", 60 to "60 s", 90 to "90 s"),
            selected = settings.responseSeconds,
            onSelect = { onChange(settings.copy(responseSeconds = it)) }
        )
        ChipRow(
            title = "Default pause length",
            options = listOf(15 to "15 min", 30 to "30 min", 45 to "45 min", 60 to "1 hr"),
            selected = settings.quietMinutes,
            onSelect = { onChange(settings.copy(quietMinutes = it)) }
        )
    }
}
