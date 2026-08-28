package com.fincore.app.navigation

import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.fincore.app.R

@Composable
fun FinCoreBottomBar(
    currentRoute: String?,
    onNavigateToRoute: (String) -> Unit
) {
    NavigationBar {
        NavigationBarItem(
            selected = currentRoute == Screen.Dashboard.route,
            onClick = { onNavigateToRoute(Screen.Dashboard.route) },
            icon = { Text("D") }, // Placeholder icon
            label = { Text(stringResource(id = R.string.nav_dashboard)) }
        )
        NavigationBarItem(
            selected = currentRoute == Screen.Accounts.route,
            onClick = { onNavigateToRoute(Screen.Accounts.route) },
            icon = { Text("A") }, // Placeholder icon
            label = { Text(stringResource(id = R.string.nav_accounts)) }
        )
        NavigationBarItem(
            selected = currentRoute == Screen.Profile.route,
            onClick = { onNavigateToRoute(Screen.Profile.route) },
            icon = { Text("P") }, // Placeholder icon
            label = { Text(stringResource(id = R.string.nav_profile)) }
        )
    }
}
