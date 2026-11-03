package com.snoah.plus

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.snoah.plus.playback.PlayerManager
import com.snoah.plus.ui.MusicViewModel
import com.snoah.plus.ui.components.MiniPlayer
import com.snoah.plus.ui.screens.AlbumsArtistsScreen
import com.snoah.plus.ui.screens.FavouritesScreen
import com.snoah.plus.ui.screens.HomeScreen
import com.snoah.plus.ui.screens.LibraryScreen
import com.snoah.plus.ui.screens.PlayerScreen
import com.snoah.plus.ui.screens.PlaylistDetailScreen
import com.snoah.plus.ui.screens.SearchScreen
import com.snoah.plus.ui.screens.SettingsScreen
import com.snoah.plus.ui.theme.AccentOptions
import com.snoah.plus.ui.theme.SnoahPlusTheme

object Routes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val LIBRARY = "library"
    const val SETTINGS = "settings"
    const val PLAYER = "player"
    const val PLAYLIST = "playlist/{playlistId}"
    const val FAVOURITES = "favourites"
    const val ALBUMS = "albums"
    const val ARTISTS = "artists"
}

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    private var hasPermission by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PlayerManager.init(this)
        hasPermission = checkAudioPermission()
        if (!hasPermission) {
            permissionLauncher.launch(audioPermission())
        }
        // Start the song scan immediately, before the first frame is
        // drawn, instead of waiting for composition + permission effects.
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return MusicViewModel(applicationContext) as T
            }
        }
        val earlyViewModel = ViewModelProvider(this, factory)[MusicViewModel::class.java]
        if (hasPermission) earlyViewModel.loadSongs()
        // Notification permission lets Android show the media
        // notification / Quick Settings tile while music plays.
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            val viewModel: MusicViewModel = viewModel(factory = factory)
            val darkTheme by viewModel.darkTheme.collectAsState()
            val accentName by viewModel.accentName.collectAsState()
            val accent = AccentOptions.firstOrNull { it.first == accentName }?.second
                ?: AccentOptions[0].second

            SnoahPlusTheme(darkTheme = darkTheme, accent = accent) {
                SnoahPlusApp(viewModel = viewModel, hasPermission = hasPermission, onRequestPermission = {
                    permissionLauncher.launch(audioPermission())
                }, onOpenSettings = {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", packageName, null)
                    }
                    startActivity(intent)
                })
            }
        }
    }

    private fun audioPermission(): String {
        return if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
    }

    private fun checkAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, audioPermission()) == PackageManager.PERMISSION_GRANTED
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SnoahPlusApp(
    viewModel: MusicViewModel,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val navController = rememberNavController()
    val playerState by PlayerManager.state.collectAsState()
    val playerPositionMs by PlayerManager.positionMs.collectAsState()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showBottomBar = currentRoute in listOf(Routes.HOME, Routes.SEARCH, Routes.LIBRARY, Routes.SETTINGS)

    LaunchedEffect(hasPermission) {
        if (hasPermission) viewModel.loadSongs()
    }

    // Reload every time the app comes back to the front. This is what
    // makes songs appear after permission is granted in Settings, or
    // after new files are added, without having to switch tabs.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, hasPermission) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && hasPermission) {
                viewModel.loadSongs()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (!hasPermission) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                Text("Snoah Plus needs audio permission", style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Allow access to audio files on this device to play your music.")
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onRequestPermission) { Text("Grant permission") }
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = onOpenSettings) { Text("Open app settings") }
            }
        }
        return
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                Column {
                    val currentSong = playerState.currentSong
                    if (currentSong != null) {
                        MiniPlayer(
                            song = currentSong,
                            isPlaying = playerState.isPlaying,
                            positionMs = playerPositionMs,
                            durationMs = playerState.durationMs,
                            onPlayPause = { PlayerManager.playPause() },
                            onClick = { navController.navigate(Routes.PLAYER) }
                        )
                    }
                    androidx.compose.material3.HorizontalDivider(color = androidx.compose.material3.MaterialTheme.colorScheme.outline)
                    NavigationBar(
                        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface,
                        tonalElevation = 0.dp
                    ) {
                        val items = listOf(
                            Triple(Routes.HOME, "Home", Icons.Filled.Home),
                            Triple(Routes.SEARCH, "Search", Icons.Filled.Search),
                            Triple(Routes.LIBRARY, "Library", Icons.Filled.LibraryMusic),
                            Triple(Routes.SETTINGS, "Settings", Icons.Filled.Settings)
                        )
                        items.forEach { (route, label, icon) ->
                            NavigationBarItem(
                                selected = currentRoute == route,
                                onClick = {
                                    navController.navigate(route) {
                                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(icon, contentDescription = label) },
                                label = { Text(label) }
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(padding),
            enterTransition = { fadeIn(tween(220)) + slideInHorizontally(tween(220)) { it / 6 } },
            exitTransition = { fadeOut(tween(180)) },
            popEnterTransition = { fadeIn(tween(220)) },
            popExitTransition = { fadeOut(tween(180)) + slideOutHorizontally(tween(180)) { it / 6 } }
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    viewModel = viewModel,
                    onOpenPlayer = { navController.navigate(Routes.PLAYER) },
                    onOpenPlaylist = { id -> navController.navigate("playlist/$id") },
                    onOpenFavourites = { navController.navigate(Routes.FAVOURITES) },
                    onOpenAlbums = { navController.navigate(Routes.ALBUMS) },
                    onOpenArtists = { navController.navigate(Routes.ARTISTS) }
                )
            }
            composable(Routes.SEARCH) { SearchScreen(viewModel = viewModel) }
            composable(Routes.LIBRARY) {
                LibraryScreen(
                    viewModel = viewModel,
                    onOpenPlaylist = { id -> navController.navigate("playlist/$id") },
                    onOpenFavourites = { navController.navigate(Routes.FAVOURITES) }
                )
            }
            composable(Routes.SETTINGS) { SettingsScreen(viewModel = viewModel) }
            composable(Routes.PLAYER) {
                PlayerScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
            }
            composable(
                route = Routes.PLAYLIST,
                arguments = listOf(navArgument("playlistId") { type = NavType.StringType })
            ) { backStackEntry ->
                val id = backStackEntry.arguments?.getString("playlistId") ?: ""
                PlaylistDetailScreen(viewModel = viewModel, playlistId = id, onBack = { navController.popBackStack() })
            }
            composable(Routes.FAVOURITES) {
                FavouritesScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
            }
            composable(Routes.ALBUMS) {
                AlbumsArtistsScreen(viewModel = viewModel, showArtists = false, onBack = { navController.popBackStack() }, onPlayGroup = { group ->
                    if (group.isNotEmpty()) viewModel.playSong(group[0], group)
                })
            }
            composable(Routes.ARTISTS) {
                AlbumsArtistsScreen(viewModel = viewModel, showArtists = true, onBack = { navController.popBackStack() }, onPlayGroup = { group ->
                    if (group.isNotEmpty()) viewModel.playSong(group[0], group)
                })
            }
        }
    }
}
