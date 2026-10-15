package com.extforge.app.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.extforge.app.ui.editor.EditorRoute
import com.extforge.app.ui.home.HomeRoute
import com.extforge.app.ui.json.JsonRoute
import com.extforge.app.ui.testrun.TestRunRoute
import com.extforge.core.store.ExtensionRepository

@Composable
fun ExtForgeNav(repository: ExtensionRepository) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "home") {
        composable("home") {
            HomeRoute(repository = repository, nav = nav)
        }
        composable(
            route = "edit/{id}",
            arguments = listOf(navArgument("id") { type = NavType.StringType }),
        ) { entry ->
            EditorRoute(
                repository = repository,
                extensionId = entry.arguments?.getString("id").orEmpty(),
                nav = nav,
            )
        }
        composable(
            route = "json/{id}",
            arguments = listOf(navArgument("id") { type = NavType.StringType }),
        ) { entry ->
            JsonRoute(
                repository = repository,
                extensionId = entry.arguments?.getString("id").orEmpty(),
                nav = nav,
            )
        }
        composable(
            route = "test/{id}",
            arguments = listOf(navArgument("id") { type = NavType.StringType }),
        ) { entry ->
            TestRunRoute(
                repository = repository,
                extensionId = entry.arguments?.getString("id").orEmpty(),
                nav = nav,
            )
        }
    }
}
