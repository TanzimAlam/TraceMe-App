package com.traceme.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.traceme.app.data.Repo
import com.traceme.app.ui.ActivityScreen
import com.traceme.app.ui.AuthScreen
import com.traceme.app.ui.Clay
import com.traceme.app.ui.ContactsScreen
import com.traceme.app.ui.Cream
import com.traceme.app.ui.Glass
import com.traceme.app.ui.HomeScreen
import com.traceme.app.ui.InkFaint
import com.traceme.app.ui.MapScreen
import com.traceme.app.ui.OnboardingScreen
import com.traceme.app.ui.OutlineWarm
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

private data class NavItem(val route: Route, val label: String, val icon: ImageVector)

@Composable
private fun AppShell(
    route: Route,
    onNavigate: (Route) -> Unit,
    onSignedOut: () -> Unit
) {
    val items = remember {
        listOf(
            NavItem(Route.HOME, "Watch", Icons.Rounded.Shield),
            NavItem(Route.MAP, "Route", Icons.Rounded.Explore),
            NavItem(Route.CONTACTS, "People", Icons.Rounded.People),
            NavItem(Route.ACTIVITY, "Log", Icons.Rounded.History),
            NavItem(Route.SETTINGS, "Settings", Icons.Rounded.Settings)
        )
    }

    Scaffold(
        containerColor = Cream,
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(
                            elevation = 18.dp,
                            shape = CircleShape,
                            ambientColor = Color(0x26A2542F),
                            spotColor = Color(0x33A2542F)
                        )
                        .clip(CircleShape)
                        .background(Glass)
                        .border(1.dp, OutlineWarm, CircleShape)
                        .padding(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items.forEach { item ->
                        val selected = route == item.route
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(CircleShape)
                                .background(if (selected) Clay else Color.Transparent)
                                .clickable { onNavigate(item.route) }
                                .padding(vertical = 9.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                item.icon,
                                contentDescription = item.label,
                                tint = if (selected) Color.White else InkFaint,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                item.label,
                                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.2.sp),
                                color = if (selected) Color.White else InkFaint,
                                textAlign = TextAlign.Center,
                                maxLines = 1
                            )
                        }
                    }
                }
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
                Route.MAP -> MapScreen(onChangeDestination = { onNavigate(Route.HOME) })
                Route.CONTACTS -> ContactsScreen()
                Route.ACTIVITY -> ActivityScreen()
                Route.SETTINGS -> SettingsScreen(onSignedOut = onSignedOut)
                else -> Unit
            }
        }
    }

    BackHandler(enabled = route != Route.HOME) { onNavigate(Route.HOME) }
}
