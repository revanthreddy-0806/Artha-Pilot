package com.arthapilot.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.arthapilot.app.ui.auth.AuthState
import com.arthapilot.app.ui.auth.AuthViewModel
import com.arthapilot.app.ui.auth.LoginScreen
import com.arthapilot.app.ui.auth.RegisterScreen
import com.arthapilot.app.ui.home.HomeScreen
import com.arthapilot.app.ui.upload.UploadStatementScreen

private object Routes {
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val HOME = "home"
    const val UPLOAD = "upload"
}

@Composable
fun AppNavHost(navController: NavHostController = rememberNavController()) {
    val authViewModel: AuthViewModel = hiltViewModel()
    val authState by authViewModel.authState.collectAsStateWithLifecycle()

    val startDestination = if (authState == AuthState.Authenticated) Routes.HOME else Routes.LOGIN

    LaunchedEffect(authState) {
        val current = navController.currentDestination?.route
        when (authState) {
            AuthState.Unauthenticated -> {
                if (current != Routes.LOGIN) {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }

            AuthState.Authenticated -> {
                if (current != Routes.HOME) {
                    navController.navigate(Routes.HOME) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }
        }
    }

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.LOGIN) {
            LoginScreen(onRegisterClick = { navController.navigate(Routes.REGISTER) })
        }

        composable(Routes.REGISTER) {
            RegisterScreen()
        }

        composable(Routes.HOME) {
            HomeScreen(
                onUploadClick = { navController.navigate(Routes.UPLOAD) },
                onLogout = { authViewModel.logout() },
            )
        }

        composable(Routes.UPLOAD) {
            UploadStatementScreen(onBack = { navController.popBackStack() })
        }
    }
}