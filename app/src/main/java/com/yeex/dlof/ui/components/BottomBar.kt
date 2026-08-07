package com.yeex.dlof.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.yeex.dlof.R

/** The app's four top-level tabs — real Material icons, no emoji. */
enum class BottomTab(val route: String) {
    HOME("feed"),
    SEARCH("search"),
    CREATE("create_paragraph"),
    PROFILE("profile")
}

@Composable
fun YeexBottomBar(currentTab: BottomTab?, onTabSelected: (BottomTab) -> Unit) {
    NavigationBar {
        NavigationBarItem(
            selected = currentTab == BottomTab.HOME,
            onClick = { onTabSelected(BottomTab.HOME) },
            icon = { Icon(Icons.Filled.Home, contentDescription = stringResource(R.string.nav_home)) },
            label = { Text(stringResource(R.string.nav_home)) }
        )
        NavigationBarItem(
            selected = currentTab == BottomTab.SEARCH,
            onClick = { onTabSelected(BottomTab.SEARCH) },
            icon = { Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.nav_search)) },
            label = { Text(stringResource(R.string.nav_search)) }
        )
        NavigationBarItem(
            selected = currentTab == BottomTab.CREATE,
            onClick = { onTabSelected(BottomTab.CREATE) },
            icon = { Icon(Icons.Filled.AddCircle, contentDescription = stringResource(R.string.nav_create)) },
            label = { Text(stringResource(R.string.nav_create)) }
        )
        NavigationBarItem(
            selected = currentTab == BottomTab.PROFILE,
            onClick = { onTabSelected(BottomTab.PROFILE) },
            icon = { Icon(Icons.Filled.Person, contentDescription = stringResource(R.string.nav_profile)) },
            label = { Text(stringResource(R.string.nav_profile)) }
        )
    }
}
