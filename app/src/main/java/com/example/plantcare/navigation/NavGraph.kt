package com.example.plantcare.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.plantcare.ui.detail.PlantDetailScreen
import com.example.plantcare.ui.home.HomeScreen

object Routes {
    const val LOGIN = "login"
    const val HOME = "home"
    const val DETAIL = "detail/{plantId}"
}

@Composable
fun AppNavHost(navController: NavHostController, startDestination: String) {
    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.LOGIN) {
            // TODO: Login screen placeholder
            HomeScreen(onOpenPlant = { id -> navController.navigate("detail/$id") })
        }
        composable(Routes.HOME) {
            HomeScreen(onOpenPlant = { id -> navController.navigate("detail/$id") })
        }
        composable(Routes.DETAIL) { backStackEntry ->
            val id = backStackEntry.arguments?.getString("plantId") ?: return@composable
            PlantDetailScreen(plantId = id)
        }
    }
}


