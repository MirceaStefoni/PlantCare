package com.example.plantcare.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.plantcare.ui.care.CareGuideScreen
import com.example.plantcare.ui.detail.PlantDetailScreen
import com.example.plantcare.ui.home.HomeScreen
import com.example.plantcare.ui.auth.SignInScreen
import com.example.plantcare.ui.auth.SignUpScreen
import com.example.plantcare.ui.auth.ResetPasswordScreen
import com.example.plantcare.ui.profile.ProfileScreen
import com.example.plantcare.ui.auth.WelcomeScreen
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.plantcare.ui.auth.AuthViewModel

import com.example.plantcare.ui.detail.EditPlantScreen
import com.example.plantcare.ui.light.LightMonitorScreen

object Routes {
    const val ROOT = "root"
    const val SIGN_IN = "sign_in"
    const val SIGN_UP = "sign_up"
    const val RESET = "reset"
    const val WELCOME = "welcome"
    const val HOME = "home"
    const val DETAIL = "detail/{plantId}"
    const val EDIT_PLANT = "edit_plant/{plantId}"
    const val CARE_GUIDE = "care_guide/{plantId}"
    const val LIGHT_MONITOR = "light_monitor/{plantId}"
    const val PROFILE = "profile"
}

@Composable
fun AppNavHost(navController: NavHostController, startDestination: String) {
    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.ROOT) {
            val vm: AuthViewModel = hiltViewModel()
            val session by vm.session.collectAsState(initial = null)
            val ready by vm.ready.collectAsState(initial = false)
            LaunchedEffect(session, ready) {
                val dest = if (session != null) Routes.HOME else if (ready) Routes.WELCOME else null
                if (dest != null) {
                    navController.navigate(dest) {
                        popUpTo(Routes.ROOT) { inclusive = false }
                        launchSingleTop = true
                    }
                }
            }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
        composable(Routes.WELCOME) {
            WelcomeScreen(
                onGetStarted = { navController.navigate(Routes.SIGN_UP) },
                onSignIn = { navController.navigate(Routes.SIGN_IN) }
            )
        }
        composable(Routes.SIGN_IN) {
            SignInScreen(
                onNavigateHome = { navController.navigate(Routes.HOME) { popUpTo(Routes.SIGN_IN) { inclusive = true } } },
                onOpenSignUp = { navController.navigate(Routes.SIGN_UP) },
                onOpenReset = { navController.navigate(Routes.RESET) }
            )
        }
        composable(Routes.SIGN_UP) {
            SignUpScreen(
                onNavigateHome = { navController.navigate(Routes.HOME) { popUpTo(Routes.SIGN_UP) { inclusive = true } } },
                onOpenSignIn = { navController.navigate(Routes.SIGN_IN) },
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.RESET) {
            ResetPasswordScreen(onBackToSignIn = { navController.popBackStack() })
        }
        composable(Routes.HOME) {
            HomeScreen(
                onOpenPlant = { id -> navController.navigate("detail/$id") },
                onOpenProfile = { /* Profile is now a tab in Home */ },
                onLogout = {
                    navController.navigate(Routes.WELCOME) {
                        popUpTo(Routes.ROOT) { inclusive = false }
                        launchSingleTop = true
                    }
                },
                onAccountDeleted = {
                    navController.navigate(Routes.WELCOME) {
                        popUpTo(Routes.ROOT) { inclusive = false }
                        launchSingleTop = true
                    }
                }
            )
        }
        composable(Routes.DETAIL) { backStackEntry ->
            val id = backStackEntry.arguments?.getString("plantId") ?: return@composable
            PlantDetailScreen(
                plantId = id,
                onBack = { navController.popBackStack() },
                onEdit = { plantId -> navController.navigate("edit_plant/$plantId") },
                onOpenCareGuide = { plantId -> navController.navigate("care_guide/$plantId") },
                onOpenLightMonitor = { plantId -> navController.navigate("light_monitor/$plantId") }
            )
        }
        composable(Routes.EDIT_PLANT) { backStackEntry ->
            val id = backStackEntry.arguments?.getString("plantId") ?: return@composable
            EditPlantScreen(
                plantId = id,
                onBack = { navController.popBackStack() },
                onPlantDeleted = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.HOME) { inclusive = true }
                    }
                }
            )
        }
        composable(
            Routes.CARE_GUIDE,
            arguments = listOf(navArgument("plantId") { type = NavType.StringType })
        ) {
            CareGuideScreen(onBack = { navController.popBackStack() })
        }
        composable(
            Routes.LIGHT_MONITOR,
            arguments = listOf(navArgument("plantId") { type = NavType.StringType })
        ) {
            LightMonitorScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.PROFILE) {
            ProfileScreen(
                onLogout = {
                    navController.navigate(Routes.WELCOME) {
                        popUpTo(Routes.ROOT) { inclusive = false }
                        launchSingleTop = true
                    }
                },
                onAccountDeleted = {
                    navController.navigate(Routes.WELCOME) {
                        popUpTo(Routes.ROOT) { inclusive = false }
                        launchSingleTop = true
                    }
                },
                onBack = {
                    if (!navController.popBackStack()) {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.ROOT) { inclusive = false }
                            launchSingleTop = true
                        }
                    }
                }
            )
        }
    }
}


