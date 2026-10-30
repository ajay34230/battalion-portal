package com.vision.assistant.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.vision.assistant.VisionApp

object Routes {
    const val HOME = "home"
    const val DEVICE = "device"
    const val PERMISSIONS = "permissions"
}

@Composable
fun VisionNav(app: VisionApp) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) { HomeScreen(app.deviceProfile) { nav.navigate(it) } }
        composable(Routes.DEVICE) { DeviceScreen(app.deviceProfile) { nav.popBackStack() } }
        composable(Routes.PERMISSIONS) { PermissionsScreen { nav.popBackStack() } }
    }
}
