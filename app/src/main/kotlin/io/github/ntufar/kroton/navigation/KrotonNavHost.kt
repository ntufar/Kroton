package io.github.ntufar.kroton.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsGymnastics
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.github.ntufar.kroton.R
import io.github.ntufar.kroton.feature.exercises.ExercisesScreen
import io.github.ntufar.kroton.feature.history.HistoryScreen
import io.github.ntufar.kroton.feature.measure.MeasureScreen
import io.github.ntufar.kroton.feature.settings.SettingsScreen
import io.github.ntufar.kroton.feature.stats.StatsScreen
import io.github.ntufar.kroton.feature.workout.WorkoutScreen

private val bottomBarDestinations =
    listOf(
        BottomBarDestination(KrotonRoute.Workout, R.string.nav_workout),
        BottomBarDestination(KrotonRoute.History, R.string.nav_history),
        BottomBarDestination(KrotonRoute.Measure, R.string.nav_measure),
        BottomBarDestination(KrotonRoute.Stats, R.string.nav_stats),
        BottomBarDestination(KrotonRoute.Exercises, R.string.nav_exercises),
    )

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KrotonApp() {
    val navController = rememberNavController()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = { navController.navigate(KrotonRoute.Settings) }) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings))
                    }
                },
            )
        },
        bottomBar = {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentDestination = navBackStackEntry?.destination

            NavigationBar {
                bottomBarDestinations.forEach { destination ->
                    val selected =
                        currentDestination?.hierarchy?.any {
                            it.hasRoute(destination.route::class)
                        } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(destination.route.icon(), contentDescription = null) },
                        label = { Text(stringResource(destination.labelRes)) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = KrotonRoute.Workout,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable<KrotonRoute.Workout> { WorkoutScreen() }
            composable<KrotonRoute.History> {
                HistoryScreen(onWorkoutDuplicated = { navController.navigate(KrotonRoute.Workout) })
            }
            composable<KrotonRoute.Measure> { MeasureScreen() }
            composable<KrotonRoute.Stats> { StatsScreen() }
            composable<KrotonRoute.Exercises> { ExercisesScreen() }
            composable<KrotonRoute.Settings> { SettingsScreen() }
        }
    }
}

private fun KrotonRoute.icon() =
    when (this) {
        is KrotonRoute.Workout -> Icons.Filled.FitnessCenter
        is KrotonRoute.History -> Icons.Filled.History
        is KrotonRoute.Measure -> Icons.Filled.MonitorWeight
        is KrotonRoute.Stats -> Icons.Filled.BarChart
        is KrotonRoute.Exercises -> Icons.Filled.SportsGymnastics
        is KrotonRoute.Settings -> Icons.Filled.Settings
    }
