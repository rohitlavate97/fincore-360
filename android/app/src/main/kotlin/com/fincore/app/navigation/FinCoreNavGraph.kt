package com.fincore.app.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navDeepLink
import com.fincore.feature.accounts.presentation.AccountsScreen
import com.fincore.feature.accounts.presentation.AccountsViewModel
import com.fincore.feature.auth.presentation.LoginScreen
import com.fincore.feature.auth.presentation.LoginViewModel
import com.fincore.feature.notifications.presentation.NotificationsScreen
import com.fincore.feature.notifications.presentation.NotificationsViewModel
import com.fincore.feature.transactions.presentation.TransactionDetailScreen
import com.fincore.feature.transfer.presentation.TransferScreen
import com.fincore.feature.transfer.presentation.TransferViewModel

@Composable
fun FinCoreNavGraph(
    navController: NavHostController = rememberNavController(),
    startDestination: String = Screen.Login.route
) {
    val navBackStackEntry = navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry.value?.destination?.route

    Scaffold(
        bottomBar = {
            if (currentRoute != Screen.Login.route) {
                FinCoreBottomBar(
                    currentRoute = currentRoute,
                    onNavigateToRoute = { route ->
                        navController.navigate(route) {
                            popUpTo(navController.graph.startDestinationId) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Login.route) {
                val viewModel: LoginViewModel = hiltViewModel()
                LoginScreen(
                    viewModel = viewModel,
                    onLoginSuccess = {
                        navController.navigate(Screen.Dashboard.route) {
                            popUpTo(Screen.Login.route) { inclusive = true }
                        }
                    }
                )
            }
            composable(Screen.Dashboard.route) {
                DashboardScreenPlaceholder(onNavigate = { route -> navController.navigate(route) })
            }
            composable(Screen.Accounts.route) {
                val viewModel: AccountsViewModel = hiltViewModel()
                AccountsScreen(viewModel = viewModel)
            }
            composable(Screen.Transfer.route) {
                val viewModel: TransferViewModel = hiltViewModel()
                TransferScreen(viewModel = viewModel)
            }
            composable(Screen.Notifications.route) {
                val viewModel: NotificationsViewModel = hiltViewModel()
                NotificationsScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToTransaction = { transactionId ->
                        navController.navigate("transactions/$transactionId")
                    },
                    viewModel = viewModel
                )
            }
            composable(
                route = Screen.TransactionDetail.route,
                deepLinks = listOf(
                    navDeepLink { uriPattern = "fincore://transactions/{transactionId}" }
                )
            ) { backStackEntry ->
                val transactionId = backStackEntry.arguments?.getString("transactionId") ?: ""
                TransactionDetailScreen(
                    transactionId = transactionId,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(Screen.Profile.route) {
                ProfileScreenPlaceholder(
                    onLogout = {
                        navController.navigate(Screen.Login.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun DashboardScreenPlaceholder(onNavigate: (String) -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Dashboard Screen")
            Button(onClick = { onNavigate(Screen.Accounts.route) }) {
                Text("Go to Accounts")
            }
            Button(onClick = { onNavigate(Screen.Transfer.route) }) {
                Text("Transfer Money")
            }
            Button(onClick = { onNavigate(Screen.Notifications.route) }) {
                Text("View Notifications")
            }
            Button(onClick = { onNavigate(Screen.Profile.route) }) {
                Text("Go to Profile")
            }
        }
    }
}

@Composable
fun ProfileScreenPlaceholder(onLogout: () -> Unit = {}) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Profile Screen")
            Button(onClick = onLogout) {
                Text("Log Out")
            }
        }
    }
}
