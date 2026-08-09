package com.arthapilot.app.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.arthapilot.app.ui.auth.AuthState
import com.arthapilot.app.ui.auth.AuthViewModel
import com.arthapilot.app.ui.auth.LoginScreen
import com.arthapilot.app.ui.auth.RegisterScreen
import com.arthapilot.app.ui.budgets.BudgetsScreen
import com.arthapilot.app.ui.home.HomeScreen
import com.arthapilot.app.ui.insights.InsightsScreen
import com.arthapilot.app.ui.recurring.RecurringScreen
import com.arthapilot.app.ui.recommendations.RecommendationScreen
import com.arthapilot.app.ui.risk.RiskScreen
import com.arthapilot.app.ui.statements.StatementsScreen
import com.arthapilot.app.ui.upload.UploadStatementScreen

private object Routes {
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val HOME = "home"
    const val UPLOAD = "upload"
    const val INSIGHTS = "insights"
    const val BUDGETS = "budgets"
    const val RECURRING = "recurring"
    const val RISK = "risk"
    const val STATEMENTS = "statements"
    const val RECOMMENDATIONS = "recommendations"
}

private data class BottomTab(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private val BOTTOM_TABS = listOf(
    BottomTab(Routes.HOME, "Transactions", Icons.Filled.Home),
    BottomTab(Routes.INSIGHTS, "Insights", Icons.Filled.Insights),
    BottomTab(Routes.BUDGETS, "Budgets", Icons.Filled.AccountBalanceWallet),
    BottomTab(Routes.RECURRING, "Recurring", Icons.Filled.Repeat),
    BottomTab(Routes.RISK, "Risk", Icons.Filled.Shield),
)

private val TAB_ROUTES = BOTTOM_TABS.map { it.route }

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

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            if (currentRoute in TAB_ROUTES) {
                NavigationBar {
                    BOTTOM_TABS.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.LOGIN) {
                LoginScreen(onRegisterClick = { navController.navigate(Routes.REGISTER) })
            }

            composable(Routes.REGISTER) {
                RegisterScreen()
            }

            composable(Routes.HOME) {
                HomeScreen(
                    onUploadClick = { navController.navigate(Routes.UPLOAD) },
                    onStatementsClick = { navController.navigate(Routes.STATEMENTS) },
                    onLogout = { authViewModel.logout() },
                )
            }

            composable(Routes.UPLOAD) {
                UploadStatementScreen(onBack = { navController.popBackStack() })
            }

            composable(Routes.INSIGHTS) {
                InsightsScreen(onBack = { navController.popBackStack() })
            }

            composable(Routes.BUDGETS) {
                BudgetsScreen(onBack = { navController.popBackStack() })
            }

            composable(Routes.RECURRING) {
                RecurringScreen(onBack = { navController.popBackStack() })
            }

            composable(Routes.RISK) {
                RiskScreen(
                    onBack = { navController.popBackStack() },
                    onOpenRecommendations = { navController.navigate(Routes.RECOMMENDATIONS) },
                )
            }

            composable(Routes.STATEMENTS) {
                StatementsScreen(onBack = { navController.popBackStack() })
            }

            composable(Routes.RECOMMENDATIONS) {
                RecommendationScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
