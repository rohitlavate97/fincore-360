package com.fincore.app.navigation

sealed class Screen(val route: String) {
    object Dashboard : Screen("dashboard")
    object Accounts : Screen("accounts")
    object Profile : Screen("profile")
}
