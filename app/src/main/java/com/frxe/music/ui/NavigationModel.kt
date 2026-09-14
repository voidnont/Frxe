package com.frxe.music.ui

enum class FrxeTab { Home, Search, Save, Library, Settings }

fun primaryNavigationTabs(): List<FrxeTab> = listOf(
    FrxeTab.Home,
    FrxeTab.Library,
    FrxeTab.Settings,
    FrxeTab.Search
)

fun primaryNavigationLabel(tab: FrxeTab): String? = when (tab) {
    FrxeTab.Home -> "Home"
    FrxeTab.Library -> "Library"
    FrxeTab.Settings -> "Settings"
    FrxeTab.Search -> "Search"
    FrxeTab.Save -> null
}
