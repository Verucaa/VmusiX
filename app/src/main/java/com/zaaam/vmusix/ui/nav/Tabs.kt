package com.zaaam.vmusix.ui.nav

sealed class Tab(val route: String, val label: String) {
    data object Home : Tab("home", "Home")
    data object New : Tab("new", "New")
    data object Library : Tab("library", "Library")
}
