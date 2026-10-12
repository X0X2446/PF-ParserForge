package com.parserforge.app.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.parserforge.app.ui.builder.BuilderRoute
import com.parserforge.app.ui.home.HomeRoute
import com.parserforge.app.ui.json.JsonRoute
import com.parserforge.app.ui.testrun.TestRunRoute
import com.parserforge.core.store.ParserRepository

@Composable
fun ParserForgeNav(repository: ParserRepository) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "home") {
        composable("home") {
            HomeRoute(repository = repository, nav = nav)
        }
        composable(
            route = "builder/{id}",
            arguments = listOf(navArgument("id") { type = NavType.StringType }),
        ) { entry ->
            BuilderRoute(
                repository = repository,
                parserId = entry.arguments?.getString("id").orEmpty(),
                nav = nav,
            )
        }
        composable(
            route = "json/{id}",
            arguments = listOf(navArgument("id") { type = NavType.StringType }),
        ) { entry ->
            JsonRoute(
                repository = repository,
                parserId = entry.arguments?.getString("id").orEmpty(),
                nav = nav,
            )
        }
        composable(
            route = "test/{id}",
            arguments = listOf(navArgument("id") { type = NavType.StringType }),
        ) { entry ->
            TestRunRoute(
                repository = repository,
                parserId = entry.arguments?.getString("id").orEmpty(),
                nav = nav,
            )
        }
    }
}
