package com.vision.assistant.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.NavigationBarItem
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.vision.assistant.VisionApp

object Routes {
    const val HOME = "home"
    const val CHAT = "chat"
    const val REMINDERS = "reminders"
    const val MEMORY = "memory"
    const val MORE = "more"
    const val CODING = "coding"
    const val HEALTH = "health"
    const val ROUTINES = "routines"
    const val MODELS = "models"
    const val DEVICE = "device"
    const val PERMISSIONS = "permissions"
    const val SETTINGS = "settings"
    const val PRIVACY = "privacy"
    const val TERMUX = "termux"
}

private data class Tab(val route: String, val icon: String, val label: String)
private val tabs = listOf(Tab(Routes.HOME, "🏠", "Home"), Tab(Routes.CHAT, "🎙", "Talk"), Tab(Routes.REMINDERS, "⏰", "Reminders"), Tab(Routes.MEMORY, "🧠", "Memory"), Tab(Routes.MORE, "☰", "More"))
private val secondary = setOf(Routes.ROUTINES, Routes.HEALTH, Routes.CODING, Routes.MODELS, Routes.DEVICE, Routes.PERMISSIONS, Routes.SETTINGS, Routes.PRIVACY, Routes.TERMUX)

@Composable
fun VisionNav(app: VisionApp) {
    val nav = rememberNavController()
    val back = { nav.popBackStack(); Unit }
    var pendingSay by remember { mutableStateOf<String?>(null) }
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: Routes.HOME
    val tabRoute = if (route in secondary) Routes.MORE else route
    fun goTab(r: String) { nav.navigate(r) { popUpTo(Routes.HOME) { saveState = true }; launchSingleTop = true; restoreState = true } }
    androidx.compose.material3.Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            androidx.compose.material3.NavigationBar(containerColor = Color(0xE6070F1C), contentColor = VisionColors.Text) {
                for (t in tabs) NavigationBarItem(
                    selected = tabRoute == t.route, onClick = { if (t.route == Routes.CHAT) pendingSay = null; goTab(t.route) },
                    icon = { androidx.compose.material3.Text(t.icon, fontSize = 20.sp) },
                    label = { androidx.compose.material3.Text(t.label, fontSize = 11.sp) },
                    colors = androidx.compose.material3.NavigationBarItemDefaults.colors(selectedTextColor = VisionColors.Cyan, unselectedTextColor = VisionColors.Muted, indicatorColor = Color(0x3300E5FF)),
                )
            }
        },
    ) { inner ->
        Box(Modifier.fillMaxSize().padding(bottom = inner.calculateBottomPadding()).consumeWindowInsets(inner)) {
            NavHost(
                navController = nav, startDestination = Routes.HOME,
                enterTransition = { fadeIn(tween(500)) + scaleIn(tween(500), initialScale = 0.96f) },
                exitTransition = { fadeOut(tween(300)) },
                popEnterTransition = { fadeIn(tween(500)) + scaleIn(tween(500), initialScale = 1.03f) },
                popExitTransition = { fadeOut(tween(300)) + scaleOut(tween(300), targetScale = 0.96f) },
            ) {
                composable(Routes.HOME) { HomeScreen(app, go = { nav.navigate(it) { launchSingleTop = true } }, say = { pendingSay = it; goTab(Routes.CHAT) }) }
                composable(Routes.CHAT) { AiScreen(app, overlay = false, autoListen = false, unlockedText = pendingSay, onClose = back) }
                composable(Routes.REMINDERS) { RemindersScreen(app) }
                composable(Routes.MEMORY) { MemoryScreen(app) }
                composable(Routes.MORE) { MoreScreen(app) { nav.navigate(it) { launchSingleTop = true } } }
                composable(Routes.CODING) { CodingScreen(app) }
                composable(Routes.HEALTH) { HealthScreen(app) }
                composable(Routes.ROUTINES) { RoutinesScreen(app) }
                composable(Routes.MODELS) { Box(Modifier.statusBarsPadding()) { ModelsScreen(app, back) } }
                composable(Routes.DEVICE) { Box(Modifier.statusBarsPadding()) { DeviceScreen(app.deviceProfile, back) } }
                composable(Routes.PERMISSIONS) { Box(Modifier.statusBarsPadding()) { PermissionsScreen(back) } }
                composable(Routes.SETTINGS) { SettingsScreen(app, back) }
                composable(Routes.PRIVACY) { Box(Modifier.statusBarsPadding()) { PrivacyScreen(app, back) } }
                composable(Routes.TERMUX) { TermuxScreen(app, back) }
            }
            Box(Modifier.align(Alignment.TopCenter)) { DownloadBanner(onClick = { nav.navigate(Routes.MODELS) { launchSingleTop = true } }) }
        }
    }
}
