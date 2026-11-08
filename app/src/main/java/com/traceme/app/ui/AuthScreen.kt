package com.traceme.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.traceme.app.BuildConfig
import com.traceme.app.data.AuthException
import com.traceme.app.data.AuthRepo
import com.traceme.app.data.Repo
import kotlinx.coroutines.launch

@Composable
fun AuthScreen(onSignedIn: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { Repo.get(context) }
    val scope = rememberCoroutineScope()

    var registerMode by remember { mutableStateOf(true) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var showGoogleHelp by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Pink, PinkSoft, Cream)))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 34.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(Berry, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Shield, contentDescription = null, tint = Color.White)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    "TraceMe",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Ink
                )
                Text(
                    "Walk home easy. We'll check in.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkSoft
                )
            }
        }

        SoftCard(container = SurfaceWarm) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = registerMode,
                    onClick = {
                        registerMode = true
                        message = null
                    },
                    label = { Text("Create account") }
                )
                FilterChip(
                    selected = !registerMode,
                    onClick = {
                        registerMode = false
                        message = null
                    },
                    label = { Text("Sign in") }
                )
            }

            if (registerMode) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Your name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Mobile number (optional)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            OutlinedTextField(
                value = email,
                onValueChange = { email = it.trim() },
                label = { Text("Email") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            message?.let {
                Text(it, color = Danger, style = MaterialTheme.typography.bodySmall)
            }

            ActionButton(
                text = if (registerMode) "Create account" else "Sign in",
                icon = Icons.Rounded.Shield,
                enabled = !busy
            ) {
                try {
                    if (registerMode) {
                        AuthRepo.register(repo, name, email, phone, password)
                    } else {
                        AuthRepo.login(repo, email, password)
                    }
                    onSignedIn()
                } catch (e: AuthException) {
                    message = e.message
                } catch (e: Exception) {
                    message = "Something went wrong. Please try again."
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                HorizontalDivider(Modifier.weight(1f))
                Text("  or  ", style = MaterialTheme.typography.bodySmall, color = InkFaint)
                HorizontalDivider(Modifier.weight(1f))
            }

            OutlinedButton(
                onClick = {
                    if (BuildConfig.GOOGLE_WEB_CLIENT_ID.isBlank()) {
                        showGoogleHelp = true
                        return@OutlinedButton
                    }
                    busy = true
                    message = null
                    scope.launch {
                        try {
                            AuthRepo.googleSignIn(context, BuildConfig.GOOGLE_WEB_CLIENT_ID)
                            onSignedIn()
                        } catch (e: AuthException) {
                            message = e.message
                        } catch (e: Exception) {
                            message = "Google sign-in failed. Try again."
                        }
                        busy = false
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(18.dp)
            ) {
                Icon(Icons.Rounded.AccountCircle, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Continue with Google")
            }
        }

        Text(
            "Accounts and contacts stay on this phone. Nothing is uploaded to a server.",
            style = MaterialTheme.typography.bodySmall,
            color = InkSoft
        )
    }

    if (showGoogleHelp) {
        AlertDialog(
            onDismissRequest = { showGoogleHelp = false },
            title = { Text("Google sign-in needs a client ID") },
            text = {
                Text(
                    "This build has no Google OAuth client yet. Create a free OAuth client in " +
                        "Google Cloud Console (type: Web application), then add\n\n" +
                        "GOOGLE_WEB_CLIENT_ID=xxxx.apps.googleusercontent.com\n\n" +
                        "to local.properties and rebuild. Full steps are in README.md."
                )
            },
            confirmButton = {
                TextButton(onClick = { showGoogleHelp = false }) { Text("Got it") }
            }
        )
    }
}
