package com.vision.assistant.ui

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
}

@Composable
fun VisionNav(app: VisionApp) {
    val nav = rememberNavController()
    val back = { nav.popBackStack(); Unit }
    NavHost(navController = nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) { HomeScreen(app.deviceProfile) { nav.navigate(it) } }
        composable(Routes.CHAT) { ChatScreen(app, back) }
        composable(Routes.TASKS) { TasksScreen(app, back) }
        composable(Routes.MEMORY) { MemoryScreen(app, back) }
        composable(Routes.MODELS) { ModelsScreen(app, back) }
        composable(Routes.DEVICE) { DeviceScreen(app.deviceProfile, back) }
        composable(Routes.PERMISSIONS) { PermissionsScreen(back) }
    }
}
