package com.traceme.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.traceme.app.data.Repo
import com.traceme.app.ui.ActivityScreen
import com.traceme.app.ui.AuthScreen
import com.traceme.app.ui.ContactsScreen
import com.traceme.app.ui.Cream
import com.traceme.app.ui.HomeScreen
import com.traceme.app.ui.MapScreen
import com.traceme.app.ui.OnboardingScreen
import com.traceme.app.ui.PeachSoft
import com.traceme.app.ui.SettingsScreen
import com.traceme.app.ui.TraceMeTheme

enum class Route { AUTH, ONBOARDING, HOME, MAP, CONTACTS, ACTIVITY, SETTINGS }

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TraceMeTheme {
                val context = LocalContext.current
                val repo = remember { Repo.get(context) }
                var route by remember {
                    mutableStateOf(
                        when {
                            repo.sessionEmail == null -> Route.AUTH
                            !repo.onboarded -> Route.ONBOARDING
                            else -> Route.HOME
                        }
                    )
                }

                when (route) {
                    Route.AUTH -> AuthScreen(
                        onSignedIn = {
                            route = if (repo.onboarded) Route.HOME else Route.ONBOARDING
                        }
                    )
                    Route.ONBOARDING -> OnboardingScreen(
                        onFinished = { route = Route.HOME }
                    )
                    else -> AppShell(
                        route = route,
                        onNavigate = { route = it },
                        onSignedOut = { route = Route.AUTH }
                    )
                }
            }
        }
    }
}

@Composable
private fun AppShell(
    route: Route,
    onNavigate: (Route) -> Unit,
    onSignedOut: () -> Unit
) {
    Scaffold(
        containerColor = Cream,
        bottomBar = {
            NavigationBar(containerColor = PeachSoft) {
                NavigationBarItem(
                    selected = route == Route.HOME,
                    onClick = { onNavigate(Route.HOME) },
                    icon = { Icon(Icons.Rounded.Shield, contentDescription = null) },
                    label = { Text("Watch") }
                )
                NavigationBarItem(
                    selected = route == Route.MAP,
                    onClick = { onNavigate(Route.MAP) },
                    icon = { Icon(Icons.Rounded.Explore, contentDescription = null) },
                    label = { Text("Route") }
                )
                NavigationBarItem(
                    selected = route == Route.CONTACTS,
                    onClick = { onNavigate(Route.CONTACTS) },
                    icon = { Icon(Icons.Rounded.People, contentDescription = null) },
                    label = { Text("Contacts") }
                )
                NavigationBarItem(
                    selected = route == Route.ACTIVITY,
                    onClick = { onNavigate(Route.ACTIVITY) },
                    icon = { Icon(Icons.Rounded.History, contentDescription = null) },
                    label = { Text("Activity") }
                )
                NavigationBarItem(
                    selected = route == Route.SETTINGS,
                    onClick = { onNavigate(Route.SETTINGS) },
                    icon = { Icon(Icons.Rounded.Settings, contentDescription = null) },
                    label = { Text("Settings") }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (route) {
                Route.HOME -> HomeScreen(
                    onOpenContacts = { onNavigate(Route.CONTACTS) },
                    onOpenActivity = { onNavigate(Route.ACTIVITY) },
                    onOpenMap = { onNavigate(Route.MAP) }
                )
                Route.MAP -> MapScreen()
                Route.CONTACTS -> ContactsScreen()
                Route.ACTIVITY -> ActivityScreen()
                Route.SETTINGS -> SettingsScreen(onSignedOut = onSignedOut)
                else -> Unit
            }
        }
    }

    BackHandler(enabled = route != Route.HOME) { onNavigate(Route.HOME) }
}
