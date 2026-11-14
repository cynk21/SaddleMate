package com.example.saddlemate.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.saddlemate.ui.screens.CockpitScreen
import com.example.saddlemate.ui.screens.FeedbackScreen
import com.example.saddlemate.ui.screens.MapScreen
import com.example.saddlemate.ui.screens.PlannerScreen
import com.example.saddlemate.ui.screens.WelfareScreen

sealed class Tab(val route: String, val label: String) {
    data object Map : Tab("map", "Karte")
    data object Planner : Tab("planner", "Planer")
    data object Cockpit : Tab("cockpit", "Ritt")
    data object Welfare : Tab("welfare", "Pferd")
    data object Feedback : Tab("feedback", "Feedback")

    companion object {
        val all = listOf(Map, Planner, Cockpit, Welfare, Feedback)
    }
}

@Composable
fun SaddleMateNavHost() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentDest = backStack?.destination

    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.all.forEach { tab ->
                    val selected = currentDest?.hierarchy?.any { it.route == tab.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Text(tab.label.take(1)) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Tab.Map.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(Tab.Map.route) { MapScreen() }
            composable(Tab.Planner.route) { PlannerScreen() }
            composable(Tab.Cockpit.route) { CockpitScreen() }
            composable(Tab.Welfare.route) { WelfareScreen() }
            composable(Tab.Feedback.route) { FeedbackScreen() }
        }
    }
}
