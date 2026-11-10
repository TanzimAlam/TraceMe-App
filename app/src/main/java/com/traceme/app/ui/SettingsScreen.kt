package com.traceme.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.traceme.app.data.AuthRepo
import com.traceme.app.data.Repo
import com.traceme.app.data.UserSettings
import com.traceme.app.location.TrackingService
import com.traceme.app.util.Perms

@Composable
fun SettingsScreen(onSignedOut: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { Repo.get(context) }
    var settings by remember { mutableStateOf(repo.loadSettings()) }
    var refresh by remember { mutableStateOf(0) }

    val account = remember(refresh) { repo.currentAccount() }
    val hasLocation = remember(refresh) { Perms.hasLocation(context) }
    val hasNotifications = remember(refresh) { Perms.hasNotifications(context) }
    val hasSms = remember(refresh) { Perms.hasSms(context) }
    val hasCall = remember(refresh) { Perms.hasCall(context) }

    fun update(next: UserSettings) {
        settings = next
        repo.saveSettings(next)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Cream)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            "Settings",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Ink
        )

        SoftCard {
            Text(account?.name ?: "Overwatch user", style = MaterialTheme.typography.titleMedium, color = Ink)
            Text(account?.email ?: "", style = MaterialTheme.typography.bodySmall, color = InkSoft)
            StatusPill(
                text = if (account?.provider == "google") "Google account" else "Local account",
                color = SafeGreen
            )
        }

        SoftCard {
            EmergencyNumberSection(settings, onChange = { update(it) })
        }

        SoftCard {
            TimingSection(settings, onChange = { update(it) })
        }

        SoftCard {
            SectionLabel("Alerts")
            SwitchRow(
                title = "Call the emergency number automatically",
                subtitle = "Otherwise we only open the dialer for you.",
                checked = settings.autoCall,
                onCheckedChange = { update(settings.copy(autoCall = it)) }
            )
            HorizontalDivider(color = OutlineWarm)
            SwitchRow(
                title = "Vibrate on check-in",
                subtitle = "A buzz plus the alarm tone when we ask if you're okay.",
                checked = settings.vibrate,
                onCheckedChange = { update(settings.copy(vibrate = it)) }
            )
        }

        SoftCard {
            SectionLabel("Permissions")
            PermissionLine("Location", hasLocation)
            PermissionLine("Notifications", hasNotifications)
            PermissionLine("SMS", hasSms)
            PermissionLine("Phone calls", hasCall)
            TextButton(onClick = {
                refresh++
                openAppSettings(context)
            }) { Text("Open Android settings", color = Clay) }
        }

        SoftCard {
            TextButton(onClick = {
                TrackingService.stop(context)
                AuthRepo.signOut(repo)
                onSignedOut()
            }) { Text("Sign out", color = Clay) }

            TextButton(onClick = {
                TrackingService.stop(context)
                repo.resetEverything()
                onSignedOut()
            }) { Text("Delete all local data", color = Danger) }

            Text(
                "Deleting local data removes your account, contacts and history from this phone.",
                style = MaterialTheme.typography.bodySmall,
                color = InkFaint
            )
        }

        Text(
            "Overwatch · night walk safety · palette from Color Hunt",
            style = MaterialTheme.typography.bodySmall,
            color = InkFaint
        )
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = Ink)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = InkSoft)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun PermissionLine(label: String, granted: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Ink, modifier = Modifier.weight(1f))
        StatusPill(
            text = if (granted) "Allowed" else "Not allowed",
            color = if (granted) SafeGreen else Danger
        )
    }
}

private fun openAppSettings(context: Context) {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null)
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        // no settings app
    }
}
