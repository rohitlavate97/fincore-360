package com.fincore.app.navigation

sealed class Screen(val route: String) {
    data object Login : Screen("login")
    data object Dashboard : Screen("dashboard")
    data object Accounts : Screen("accounts")
    data object Transfer : Screen("transfer")
    data object Profile : Screen("profile")
}
