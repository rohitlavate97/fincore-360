package com.fincore.app.navigation

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
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.fincore.core.common.result.ScreenState
import com.fincore.core.ui.component.ScreenStateContainer

@Composable
fun FinCoreNavGraph(
    navController: NavHostController = rememberNavController()
) {
    val navBackStackEntry = navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry.value?.destination?.route

    Scaffold(
        bottomBar = {
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
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Dashboard.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Dashboard.route) {
                DashboardScreenPlaceholder(onNavigate = { route -> navController.navigate(route) })
            }
            composable(Screen.Accounts.route) {
                AccountsScreenPlaceholder()
            }
            composable(Screen.Profile.route) {
                ProfileScreenPlaceholder()
            }
        }
    }
}

@Composable
fun DashboardScreenPlaceholder(onNavigate: (String) -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Dashboard Screen")
            Button(onClick = { onNavigate(Screen.Accounts.route) }) {
                Text("Go to Accounts")
            }
            Button(onClick = { onNavigate(Screen.Profile.route) }) {
                Text("Go to Profile")
            }
        }
    }
}

@Composable
fun AccountsScreenPlaceholder() {
    ScreenStateContainer(
        state = ScreenState.Success("Data loaded"),
        onRetry = {}
    ) { data ->
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Accounts Screen: $data")
        }
    }
}

@Composable
fun ProfileScreenPlaceholder() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Profile Screen")
    }
}
