package app.async.presentation.navigation

import android.annotation.SuppressLint
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import app.async.presentation.screens.AlbumDetailScreen
import app.async.presentation.screens.ArtistDetailScreen
import app.async.presentation.screens.DailyMixScreen
import app.async.presentation.screens.EditTransitionScreen
import app.async.presentation.screens.GenreDetailScreen
import app.async.presentation.screens.HomeScreen
import app.async.presentation.screens.LibraryScreen
import app.async.presentation.screens.MashupScreen
import app.async.presentation.screens.NavBarCornerRadiusScreen
import app.async.presentation.screens.PlaylistDetailScreen
import app.async.presentation.screens.AboutScreen
import app.async.presentation.screens.SearchScreen
import app.async.presentation.screens.SettingsScreen
import app.async.presentation.viewmodel.PlayerViewModel
import app.async.presentation.viewmodel.PlaylistViewModel

@OptIn(UnstableApi::class)
@SuppressLint("UnrememberedGetBackStackEntry")
@Composable
fun AppNavigation(
    playerViewModel: PlayerViewModel,
    navController: NavHostController,
    paddingValues: PaddingValues
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        composable(
            Screen.Home.route,
            enterTransition = { enterTransition() },
            exitTransition = { exitTransition() },
            popEnterTransition = { enterTransition() },
            popExitTransition = { exitTransition() },
        ) {
            HomeScreen(navController = navController, paddingValuesParent = paddingValues, playerViewModel = playerViewModel)
        }
        composable(
            Screen.Search.route,
            enterTransition = { enterTransition() },
            exitTransition = { exitTransition() },
            popEnterTransition = { enterTransition() },
            popExitTransition = { exitTransition() },
        ) {
            SearchScreen(paddingValues = paddingValues, playerViewModel = playerViewModel, navController = navController)
        }
            composable(
                Screen.Library.route,
                enterTransition = { enterTransition() },
                exitTransition = { exitTransition() },
                popEnterTransition = { enterTransition() },
                popExitTransition = { exitTransition() },
            ) {
                LibraryScreen(navController = navController, playerViewModel = playerViewModel)
            }
            composable(
                Screen.Settings.route,
                enterTransition = { enterTransition() },
                exitTransition = { exitTransition() },
                popEnterTransition = { enterTransition() },
                popExitTransition = { exitTransition() },
            ) {
                SettingsScreen(
                    navController = navController,
                    playerViewModel = playerViewModel,
                    paddingValues = paddingValues
                )
            }
            composable(
                Screen.DailyMixScreen.route,
                enterTransition = { enterTransition() },
                exitTransition = { exitTransition() },
                popEnterTransition = { enterTransition() },
                popExitTransition = { exitTransition() },
            ) {
                DailyMixScreen(
                    playerViewModel = playerViewModel,
                    navController = navController
                )
            }
            composable(
                route = Screen.PlaylistDetail.route,
                arguments = listOf(navArgument("playlistId") { type = NavType.StringType }),
                enterTransition = { enterTransition() },
                exitTransition = { exitTransition() },
                popEnterTransition = { enterTransition() },
                popExitTransition = { exitTransition() },
            ) { backStackEntry ->
                val playlistId = backStackEntry.arguments?.getString("playlistId")
                val playlistViewModel: PlaylistViewModel = hiltViewModel()
                if (playlistId != null) {
                    PlaylistDetailScreen(
                        playlistId = playlistId,
                        playerViewModel = playerViewModel,
                        playlistViewModel = playlistViewModel,
                        onBackClick = { navController.popBackStack() },
                        onDeletePlayListClick = { navController.popBackStack() },
                        navController = navController
                    )
                }
            }
            composable(
                Screen.DJSpace.route,
                enterTransition = { enterTransition() },
                exitTransition = { exitTransition() },
                popEnterTransition = { enterTransition() },
                popExitTransition = { exitTransition() },
            ) {
                MashupScreen()
            }
            composable(
                route = Screen.GenreDetail.route,
                arguments = listOf(navArgument("genreId") { type = NavType.StringType }),
                enterTransition = { enterTransition() },
                exitTransition = { exitTransition() },
                popEnterTransition = { enterTransition() },
                popExitTransition = { exitTransition() },
            ) { backStackEntry ->
                val genreId = backStackEntry.arguments?.getString("genreId")
                if (genreId != null) {
                    GenreDetailScreen(
                        navController = navController,
                        genreId = genreId,
                        playerViewModel = playerViewModel
                    )
                } else {
                    Text("Error: Genre ID missing", modifier = Modifier)
                }
            }
            composable(
                route = Screen.AlbumDetail.route,
                arguments = listOf(navArgument("albumId") { type = NavType.StringType }),
                enterTransition = { enterTransition() },
                exitTransition = { exitTransition() },
                popEnterTransition = { enterTransition() },
                popExitTransition = { exitTransition() },
            ) { backStackEntry ->
                val albumId = backStackEntry.arguments?.getString("albumId")
                if (albumId != null) {
                    AlbumDetailScreen(
                        albumId = albumId,
                        navController = navController,
                        playerViewModel = playerViewModel
                    )
                }
            }
            composable(
                route = Screen.ArtistDetail.route,
                arguments = listOf(navArgument("artistId") { type = NavType.StringType }),
                enterTransition = { enterTransition() },
                exitTransition = { exitTransition() },
                popEnterTransition = { enterTransition() },
                popExitTransition = { exitTransition() },
            ) { backStackEntry ->
                val artistId = backStackEntry.arguments?.getString("artistId")
                if (artistId != null) {
                    ArtistDetailScreen(
                        artistId = artistId,
                        navController = navController,
                        playerViewModel = playerViewModel
                    )
                }
            }
            composable(
                "nav_bar_corner_radius",
                enterTransition = { enterTransition() },
                exitTransition = { exitTransition() },
                popEnterTransition = { enterTransition() },
                popExitTransition = { exitTransition() },
            ) {
                NavBarCornerRadiusScreen(navController)
            }
            composable(
                route = Screen.EditTransition.route,
                arguments = listOf(navArgument("playlistId") {
                    type = NavType.StringType
                    nullable = true
                }),
                enterTransition = { enterTransition() },
                exitTransition = { exitTransition() },
                popEnterTransition = { enterTransition() },
                popExitTransition = { exitTransition() },
            ) {
                EditTransitionScreen(navController = navController)
            }
            composable(
                Screen.About.route,
                enterTransition = { enterTransition() },
                exitTransition = { exitTransition() },
                popEnterTransition = { enterTransition() },
                popExitTransition = { exitTransition() },
            ) {
                AboutScreen(
                    navController = navController,
                    onNavigationIconClick = { navController.popBackStack() }
                )
            }
        }
}