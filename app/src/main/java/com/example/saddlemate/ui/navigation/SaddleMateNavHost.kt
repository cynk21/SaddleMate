package com.example.saddlemate.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.saddlemate.ui.icons.HorseIcons
import com.example.saddlemate.ui.screens.CockpitScreen
import com.example.saddlemate.ui.screens.FeedbackScreen
import com.example.saddlemate.ui.screens.MapScreen
import com.example.saddlemate.ui.screens.MyRidesScreen
import com.example.saddlemate.ui.screens.PlannerScreen
import com.example.saddlemate.ui.screens.RideDetailScreen
import com.example.saddlemate.ui.screens.RideSummaryScreen
import com.example.saddlemate.ui.screens.WelfareScreen
import com.example.saddlemate.ui.screens.rideViewModelShared
import com.example.saddlemate.ui.viewmodels.RideViewModel

/** Bottom-bar tabs. Each has a Material icon and a German label. */
sealed class Tab(val route: String, val label: String, val icon: ImageVector) {
    data object Map : Tab("map", "Karte", Icons.Filled.Map)
    data object Planner : Tab("planner", "Planer", Icons.Filled.Route)
    data object Cockpit : Tab("cockpit", "Ritt", Icons.Filled.DirectionsRun)
    data object MyRides : Tab("my-rides", "Ritte", HorseIcons.HorseShoe)
    data object Welfare : Tab("welfare", "Pferd", HorseIcons.HorseHead)
    data object Feedback : Tab("feedback", "Feedback", Icons.Filled.Feedback)

    companion object {
        val all = listOf(Map, Planner, Cockpit, MyRides, Welfare, Feedback)
    }
}

@Composable
fun SaddleMateNavHost() {
    val navController = rememberNavController()
    // The same ride state the map and the cockpit use.
    val rideViewModel: RideViewModel = rideViewModelShared()
    val backStack by navController.currentBackStackEntryAsState()
    val currentDest = backStack?.destination

    // A finished ride shows its short summary as a popup over the map (see MapScreen). The ride
    // is already saved at that point and appears in "Ritte". No separate screen is opened here.

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ) {
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
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
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
            composable(Tab.Planner.route) {
                PlannerScreen(
                    onChooseRide = { rideId ->
                        // The chosen ride becomes the route on the map, with its track and fit-to-view.
                        rideViewModel.showStoredRide(rideId)
                        navController.navigate(Tab.Map.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onChooseCatalogRoute = { routeId ->
                        rideViewModel.showCatalogRoute(routeId)
                        navController.navigate(Tab.Map.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onChooseGeneratedRoute = { loop ->
                        rideViewModel.showGeneratedRoute(loop.id, loop.name, loop.points)
                        navController.navigate(Tab.Map.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
            composable(Tab.Cockpit.route) { CockpitScreen() }
            composable(Tab.MyRides.route) {
                MyRidesScreen(
                    onOpenRide = { rideId -> navController.navigate("ride-detail/$rideId") },
                    onShowOnMap = { rideId ->
                        // Put the ride on the shared map state, then switch to the map tab.
                        rideViewModel.showStoredRide(rideId)
                        navController.navigate(Tab.Map.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
            composable(Tab.Welfare.route) { WelfareScreen() }
            composable(Tab.Feedback.route) { FeedbackScreen() }
            composable(
                route = "ride-summary/{rideId}",
                arguments = listOf(navArgument("rideId") { type = NavType.StringType }),
            ) {
                RideSummaryScreen(
                    onBackToMap = {
                        navController.navigate(Tab.Map.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
            composable(
                route = "ride-detail/{rideId}",
                arguments = listOf(navArgument("rideId") { type = NavType.StringType }),
            ) {
                RideDetailScreen(onDeleted = { navController.popBackStack() })
            }
        }
    }
}
