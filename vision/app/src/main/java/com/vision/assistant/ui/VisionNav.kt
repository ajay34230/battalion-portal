package com.vision.assistant.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.vision.assistant.VisionApp

object Routes {
    const val HOME = "home"
    const val CHAT = "chat"
    const val TASKS = "tasks"
    const val MEMORY = "memory"
    const val MODELS = "models"
    const val DEVICE = "device"
    const val PERMISSIONS = "permissions"
    const val SETTINGS = "settings"
    const val PRIVACY = "privacy"
    const val TERMUX = "termux"
}

@Composable
fun VisionNav(app: VisionApp) {
    val nav = rememberNavController()
    val back = { nav.popBackStack(); Unit }
    androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize()) {
    NavHost(
        navController = nav, startDestination = Routes.HOME,
        enterTransition = { fadeIn(tween(700)) + scaleIn(tween(700), initialScale = 0.94f) },
        exitTransition = { fadeOut(tween(450)) + scaleOut(tween(450), targetScale = 1.04f) },
        popEnterTransition = { fadeIn(tween(700)) + scaleIn(tween(700), initialScale = 1.04f) },
        popExitTransition = { fadeOut(tween(450)) + scaleOut(tween(450), targetScale = 0.94f) },
    ) {
        composable(Routes.HOME) { HomeScreen(app) { nav.navigate(it) } }
        composable(Routes.CHAT) { AiScreen(app, overlay = false, autoListen = false, onClose = back) }
        composable(Routes.TASKS) { TasksScreen(app, back) }
        composable(Routes.MEMORY) { MemoryScreen(app, back) }
        composable(Routes.MODELS) { ModelsScreen(app, back) }
        composable(Routes.DEVICE) { DeviceScreen(app.deviceProfile, back) }
        composable(Routes.PERMISSIONS) { PermissionsScreen(back) }
        composable(Routes.SETTINGS) { SettingsScreen(app, back) }
        composable(Routes.PRIVACY) { PrivacyScreen(app, back) }
        composable(Routes.TERMUX) { TermuxScreen(app, back) }
        }
    androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.align(androidx.compose.ui.Alignment.TopCenter)) { DownloadBanner(onClick = { nav.navigate(Routes.MODELS) { launchSingleTop = true } }) }
    }
}
