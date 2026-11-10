package com.traceme.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.traceme.app.data.LogEntry
import com.traceme.app.data.Repo

@Composable
fun ActivityScreen() {
    val context = LocalContext.current
    val repo = remember { Repo.get(context) }
    var entries by remember { mutableStateOf(repo.loadLog()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Cream)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Activity",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Ink,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = {
                repo.clearLog()
                entries = repo.loadLog()
            }) { Text("Clear", color = Clay) }
        }

        if (entries.isEmpty()) {
            SoftCard {
                Text(
                    "Nothing here yet. Start a night watch and this page fills with check-ins, pauses and alerts.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkSoft
                )
            }
        }

        entries.groupBy { formatDay(it.timestamp) }.forEach { (day, dayEntries) ->
            Text(day, style = MaterialTheme.typography.labelLarge, color = InkSoft)
            dayEntries.forEach { entry ->
                LogRow(entry)
            }
        }
    }
}

@Composable
private fun LogRow(entry: LogEntry) {
    SoftCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .background(kindColor(entry.kind), CircleShape)
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(entry.message, style = MaterialTheme.typography.bodyMedium, color = Ink)
                Text(
                    "${formatClock(entry.timestamp)} · ${relativeDuration(System.currentTimeMillis() - entry.timestamp)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = InkFaint
                )
            }
        }
    }
}

private fun kindColor(kind: String): Color = when (kind) {
    "sos" -> Danger
    "check" -> WarnAmber
    "quiet" -> WarnAmber
    "watch" -> SafeGreen
    else -> InkFaint
}
