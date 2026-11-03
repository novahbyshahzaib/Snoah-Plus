package com.snoah.plus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.snoah.plus.data.Playlist
import com.snoah.plus.data.RepeatMode
import com.snoah.plus.data.Song
import com.snoah.plus.data.SortOrder
import com.snoah.plus.playback.PlayerManager
import com.snoah.plus.ui.MusicViewModel
import com.snoah.plus.ui.components.AlbumArt
import com.snoah.plus.ui.components.EmptyState
import com.snoah.plus.ui.components.GradientHeader
import com.snoah.plus.ui.components.SongRow
import com.snoah.plus.ui.theme.AccentOptions
import com.snoah.plus.util.FormatUtils
import kotlin.math.roundToInt

@Composable
fun AddToPlaylistDialog(
    song: Song,
    playlists: List<Playlist>,
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit,
    onCreatePlaylist: (String) -> Unit
) {
    var newName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to playlist") },
        text = {
            Column {
                Text(song.title, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(12.dp))
                if (playlists.isEmpty()) {
                    Text("No playlists yet. Create one below.")
                } else {
                    playlists.forEach { playlist ->
                        Text(
                            text = playlist.name,
                            modifier = Modifier.fillMaxWidth().clickable { onAdd(playlist.id) }.padding(vertical = 10.dp),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(value = newName, onValueChange = { newName = it }, label = { Text("New playlist name") }, singleLine = true)
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = {
                    if (newName.isNotBlank()) {
                        onCreatePlaylist(newName.trim())
                        newName = ""
                    }
                }) { Text("Create playlist") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}

@Composable
fun SongDetailsDialog(song: Song, onDismiss: () -> Unit) {
    val dateText = if (song.dateAdded > 0) {
        try {
            java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.getDefault())
                .format(java.util.Date(song.dateAdded * 1000L))
        } catch (e: Exception) {
            "Unknown"
        }
    } else "Unknown"
    val sizeText = if (song.sizeBytes >= 1_000_000) {
        "%.1f MB".format(song.sizeBytes / 1_000_000f)
    } else if (song.sizeBytes > 0) {
        "${song.sizeBytes / 1000} KB"
    } else "Unknown"
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Song details", maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            Column {
                Text(song.title, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Artist: ${song.artist}")
                Text("Album: ${song.album}")
                Text("Duration: ${FormatUtils.formatDuration(song.durationMs)}")
                Text("Size: $sizeText")
                Text("Added: $dateText")
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}

fun shareSong(context: android.content.Context, song: Song) {
    try {
        val share = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "audio/*"
            putExtra(android.content.Intent.EXTRA_STREAM, song.contentUri)
            putExtra(android.content.Intent.EXTRA_SUBJECT, song.title)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(android.content.Intent.createChooser(share, "Share song"))
    } catch (e: Exception) {
    }
}

@Composable
fun HomeScreen(
    viewModel: MusicViewModel,
    onOpenPlayer: () -> Unit,
    onOpenPlaylist: (String) -> Unit,
    onOpenFavourites: () -> Unit,
    onOpenAlbums: () -> Unit,
    onOpenArtists: () -> Unit
) {
    val songs by viewModel.songs.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val favourites by viewModel.favourites.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val playerState by PlayerManager.state.collectAsState()
    val sortOrder by viewModel.sortOrder.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    var selectedForPlaylist by remember { mutableStateOf<Song?>(null) }
    var detailsSong by remember { mutableStateOf<Song?>(null) }
    val homeContext = LocalContext.current
    // Coming back to Home must never leave an empty list: if the
    // first scan finished empty, scan again on every Home entry.
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (viewModel.songs.value.isEmpty()) viewModel.loadSongs()
    }
    var showCreate by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }

    if (detailsSong != null) {
        SongDetailsDialog(song = detailsSong!!, onDismiss = { detailsSong = null })
    }

    val recentIds by viewModel.recentIds.collectAsState()
    // Memoize the sorted/filtered/grouped lists: recomputing them on
    // every 500ms playback tick made scrolling jitter and stall.
    val displayed = androidx.compose.runtime.remember(songs, searchQuery, sortOrder, recentIds) { viewModel.filteredSongs() }
    // Card counts computed here, in the screen body, not inside a
    // LazyColumn item: remember() inside a lazy item can crash or
    // go stale when items are recycled during fast scrolling.
    val homeFavouriteCount = remember(songs, favourites) { viewModel.favouriteSongs().size }
    val homeAlbumCount = remember(songs) { viewModel.albums().size }
    val homeArtistCount = remember(songs) { viewModel.artists().size }
    // Safety net: the All-songs list must never be empty while songs
    // are known. If a stale search filter hides everything, fall
    // back to the full list so Home can never show "No songs yet"
    // while Albums / Artists counts are non-zero.
    val displayedSafe = if (displayed.isEmpty() && songs.isNotEmpty() && searchQuery.isNotBlank()) songs else displayed

    if (selectedForPlaylist != null) {
        AddToPlaylistDialog(
            song = selectedForPlaylist!!,
            playlists = playlists,
            onDismiss = { selectedForPlaylist = null },
            onAdd = { id -> viewModel.addToPlaylist(id, selectedForPlaylist!!); selectedForPlaylist = null },
            onCreatePlaylist = { name -> viewModel.createPlaylist(name) }
        )
    }
    if (showCreate) {
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("New playlist") },
            text = { OutlinedTextField(value = newPlaylistName, onValueChange = { newPlaylistName = it }, label = { Text("Playlist name") }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.createPlaylist(newPlaylistName)
                    newPlaylistName = ""
                    showCreate = false
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showCreate = false }) { Text("Cancel") } }
        )
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item { GradientHeader(title = "Snoah Plus", subtitle = "Your music, beautifully played.") }
        item {
            // One horizontal row of same-height chips, scrollable on small phones.
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item { FilterChip(selected = sortOrder == SortOrder.TITLE, onClick = { viewModel.setSortOrder(SortOrder.TITLE) }, label = { Text("Title") }) }
                item { FilterChip(selected = sortOrder == SortOrder.ARTIST, onClick = { viewModel.setSortOrder(SortOrder.ARTIST) }, label = { Text("Artist") }) }
                item { FilterChip(selected = sortOrder == SortOrder.RECENT, onClick = { viewModel.setSortOrder(SortOrder.RECENT) }, label = { Text("Recent") }) }
                item { FilterChip(selected = sortOrder == SortOrder.DURATION, onClick = { viewModel.setSortOrder(SortOrder.DURATION) }, label = { Text("Longest") }) }
                item { FilterChip(selected = sortOrder == SortOrder.MOST_PLAYED, onClick = { viewModel.setSortOrder(SortOrder.MOST_PLAYED) }, label = { Text("Most played") }) }
            }
        }
        item {
            // Three equal cards, fixed height, single-line labels so nothing wraps.
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    Triple("Favourites", FormatUtils.formatCount(homeFavouriteCount, "song", "songs"), Icons.Filled.Favorite to MaterialTheme.colorScheme.onSurface),
                    Triple("Albums", FormatUtils.formatCount(homeAlbumCount, "album", "albums"), Icons.Filled.Album to MaterialTheme.colorScheme.onSurface),
                    Triple("Artists", FormatUtils.formatCount(homeArtistCount, "artist", "artists"), Icons.Filled.Person to MaterialTheme.colorScheme.onSurface)
                ).forEachIndexed { index, (label, count, iconTint) ->
                    Card(
                        modifier = Modifier.weight(1f).height(92.dp).clickable {
                            when (index) {
                                0 -> onOpenFavourites()
                                1 -> onOpenAlbums()
                                else -> onOpenArtists()
                            }
                        },
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Icon(iconTint.first, contentDescription = null, tint = iconTint.second, modifier = Modifier.size(22.dp))
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(count, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { viewModel.shuffleAll() }, modifier = Modifier.weight(1f).height(48.dp), enabled = displayedSafe.isNotEmpty()) {
                    Icon(Icons.Filled.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Shuffle all")
                }
                OutlinedButton(onClick = { viewModel.refreshSongs() }, modifier = Modifier.weight(1f).height(48.dp)) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Refresh")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
        item {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("All songs", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Text("${displayedSafe.size}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                IconButton(onClick = { viewModel.refreshSongs() }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh songs")
                }
                TextButton(onClick = { showCreate = true }) { Text("+ Playlist") }
            }
        }
        if (isLoading && displayedSafe.isEmpty()) {
            item {
                Column(modifier = Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Loading your songs…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else if (displayedSafe.isEmpty()) {
            item {
                EmptyState("No songs yet", "Grant audio permission and add music files to this device to see them here. Tap Refresh after adding files.")
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    val message by viewModel.loadMessage.collectAsState()
                    if (message.isNotEmpty()) {
                        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    if (searchQuery.isNotBlank() && songs.isNotEmpty()) {
                        Button(onClick = { viewModel.setSearchQuery("") }, modifier = Modifier.height(48.dp)) {
                            Text("Clear search")
                        }
                    } else {
                        Button(onClick = { viewModel.refreshSongs() }, modifier = Modifier.height(48.dp)) {
                            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Refresh songs")
                        }
                    }
                }
            }
        } else {
            items(displayedSafe, key = { it.id }) { song ->
                SongRow(
                    song = song,
                    isFavourite = favourites.contains(song.id),
                    isPlaying = playerState.currentSong?.id == song.id,
                    onClick = { viewModel.playSong(song, displayedSafe) },
                    onFavouriteClick = { viewModel.toggleFavourite(song) },
                    onAddToPlaylistClick = { selectedForPlaylist = song },
                    onPlayNextClick = { viewModel.playNext(song) },
                    onAddToQueueClick = { viewModel.addToQueue(song) },
                    onDetailsClick = { detailsSong = song },
                    onShareClick = { shareSong(homeContext, song) }
                )
            }
        }
        item { Spacer(modifier = Modifier.height(96.dp)) }
    }
}

@Composable
private fun SectionRow(title: String, songs: List<Song>, onPlay: (Song) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp)) {
            items(songs) { song ->
                Column(modifier = Modifier.width(104.dp).clickable { onPlay(song) }) {
                    AlbumArt(song = song, modifier = Modifier.size(104.dp), cornerDp = 18)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                    Text(song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(viewModel: MusicViewModel) {
    val query by viewModel.searchQuery.collectAsState()
    val favourites by viewModel.favourites.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val playerState by PlayerManager.state.collectAsState()
    var selectedForPlaylist by remember { mutableStateOf<Song?>(null) }
    val results = viewModel.filteredSongs()

    if (selectedForPlaylist != null) {
        AddToPlaylistDialog(
            song = selectedForPlaylist!!,
            playlists = playlists,
            onDismiss = { selectedForPlaylist = null },
            onAdd = { id -> viewModel.addToPlaylist(id, selectedForPlaylist!!); selectedForPlaylist = null },
            onCreatePlaylist = { name -> viewModel.createPlaylist(name) }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { viewModel.setSearchQuery(it) },
            label = { Text("Search songs, artists, albums…") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { viewModel.submitSearch(query) }),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        val history by viewModel.searchHistory.collectAsState()
        if (query.isEmpty() && history.isNotEmpty()) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Recent searches", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { viewModel.clearSearchHistory() }) { Text("Clear") }
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp)) {
                items(history) { past ->
                    FilterChip(selected = false, onClick = { viewModel.setSearchQuery(past) }, label = { Text(past, maxLines = 1) })
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            if (results.isEmpty()) {
                item { EmptyState("Nothing found", "Try a different song, artist or album name.") }
            } else {
                items(results, key = { it.id }) { song ->
                    SongRow(
                        song = song,
                        isFavourite = favourites.contains(song.id),
                        isPlaying = playerState.currentSong?.id == song.id,
                        onClick = { viewModel.playSong(song, results) },
                        onFavouriteClick = { viewModel.toggleFavourite(song) }
                    )
                }
            }
            item { Spacer(modifier = Modifier.height(96.dp)) }
        }
    }
}

@Composable
fun LibraryScreen(
    viewModel: MusicViewModel,
    onOpenPlaylist: (String) -> Unit,
    onOpenFavourites: () -> Unit
) {
    val playlists by viewModel.playlists.collectAsState()
    val songs by viewModel.songs.collectAsState()
    val favourites by viewModel.favourites.collectAsState()
    val playerState by PlayerManager.state.collectAsState()
    val libraryRecentIds by viewModel.recentIds.collectAsState()
    var showCreate by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var detailsSong by remember { mutableStateOf<Song?>(null) }
    // Memoize: these lists must not be rebuilt on every playback tick.
    val librarySections = remember(songs, favourites, libraryRecentIds) {
        listOf(
            "Recently played" to viewModel.recentSongs().take(8),
            "Recently added" to viewModel.recentlyAddedSongs(limit = 8),
            "Most played" to viewModel.mostPlayedSongs(limit = 8)
        )
    }
    val libraryContext = LocalContext.current

    if (detailsSong != null) {
        SongDetailsDialog(song = detailsSong!!, onDismiss = { detailsSong = null })
    }

    if (showCreate) {
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("New playlist") },
            text = { OutlinedTextField(value = newName, onValueChange = { newName = it }, label = { Text("Playlist name") }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = { viewModel.createPlaylist(newName); newName = ""; showCreate = false }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showCreate = false }) { Text("Cancel") } }
        )
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item { GradientHeader(title = "Library", subtitle = "${songs.size} songs on this device") }
        item {
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Playlists", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Button(onClick = { showCreate = true }) { Icon(Icons.Filled.Add, contentDescription = null); Text("New") }
            }
            Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).clickable { onOpenFavourites() }, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Favorite, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Favourites", style = MaterialTheme.typography.titleMedium)
                        Text(FormatUtils.formatCount(viewModel.favouriteSongs().size, "song", "songs"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
        // Library-only sections: Recently played, Recently added and
        // Most played live here in their own sections, not on Home.
        librarySections.forEach { (title, sectionSongs) ->
            if (sectionSongs.isNotEmpty()) {
                item {
                    Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                }
                items(sectionSongs, key = { song -> "$title-${song.id}" }) { song ->
                    SongRow(
                        song = song,
                        isFavourite = favourites.contains(song.id),
                        isPlaying = playerState.currentSong?.id == song.id,
                        onClick = { viewModel.playSong(song, songs) },
                        onFavouriteClick = { viewModel.toggleFavourite(song) },
                        onPlayNextClick = { viewModel.playNext(song) },
                        onAddToQueueClick = { viewModel.addToQueue(song) },
                        onDetailsClick = { detailsSong = song },
                        onShareClick = { shareSong(libraryContext, song) }
                    )
                }
            }
        }
        if (playlists.isEmpty()) {
            item { EmptyState("No playlists yet", "Create your first playlist and add songs to it.") }
        } else {
            items(playlists, key = { it.id }) { playlist ->
                var menuExpanded by remember { mutableStateOf(false) }
                var showRename by remember { mutableStateOf(false) }
                var renameText by remember { mutableStateOf(playlist.name) }
                if (showRename) {
                    AlertDialog(
                        onDismissRequest = { showRename = false },
                        title = { Text("Rename playlist") },
                        text = { OutlinedTextField(value = renameText, onValueChange = { renameText = it }, singleLine = true) },
                        confirmButton = { TextButton(onClick = { viewModel.renamePlaylist(playlist.id, renameText); showRename = false }) { Text("Save") } },
                        dismissButton = { TextButton(onClick = { showRename = false }) { Text("Cancel") } }
                    )
                }
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clickable { onOpenPlaylist(playlist.id) }, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.QueueMusic, contentDescription = null, tint = Color.White)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(playlist.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(FormatUtils.formatCount(playlist.songIds.size, "song", "songs"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Box {
                            IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Playlist options") }
                            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                                DropdownMenuItem(text = { Text("Rename") }, onClick = { menuExpanded = false; showRename = true })
                                DropdownMenuItem(text = { Text("Delete") }, onClick = { menuExpanded = false; viewModel.deletePlaylist(playlist.id) })
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(modifier = Modifier.height(96.dp)) }
    }
}

@Composable
fun PlaylistDetailScreen(viewModel: MusicViewModel, playlistId: String, onBack: () -> Unit) {
    val playlists by viewModel.playlists.collectAsState()
    val favourites by viewModel.favourites.collectAsState()
    val playerState by PlayerManager.state.collectAsState()
    val playlist = playlists.firstOrNull { it.id == playlistId }
    if (playlist == null) {
        Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
            Text("Playlist not found", style = MaterialTheme.typography.titleLarge)
            Button(onClick = onBack, modifier = Modifier.padding(top = 16.dp)) { Text("Back") }
        }
        return
    }
    val songs = viewModel.playlistSongs(playlist)
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
            Text(playlist.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            IconButton(onClick = {
                val share = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(android.content.Intent.EXTRA_SUBJECT, "${playlist.name}.m3u")
                    putExtra(android.content.Intent.EXTRA_TEXT, viewModel.playlistM3U(playlist))
                }
                context.startActivity(android.content.Intent.createChooser(share, "Share playlist"))
            }) { Icon(Icons.Filled.Share, contentDescription = "Share playlist as M3U") }
            IconButton(onClick = { if (songs.isNotEmpty()) viewModel.playSong(songs[0], songs) }) { Icon(Icons.Filled.PlayArrow, contentDescription = "Play all") }
        }
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            if (songs.isEmpty()) {
                item { EmptyState("Empty playlist", "Add songs from Search or Home using a playlist, or play songs and add them from here later.") }
            } else {
                items(songs, key = { it.id }) { song ->
                    SongRow(
                        song = song,
                        isFavourite = favourites.contains(song.id),
                        isPlaying = playerState.currentSong?.id == song.id,
                        onClick = { viewModel.playSong(song, songs) },
                        onFavouriteClick = { viewModel.toggleFavourite(song) }
                    )
                }
            }
            item { Spacer(modifier = Modifier.height(96.dp)) }
        }
    }
}

@Composable
fun FavouritesScreen(viewModel: MusicViewModel, onBack: () -> Unit) {
    val favourites by viewModel.favourites.collectAsState()
    val playerState by PlayerManager.state.collectAsState()
    val songs = viewModel.favouriteSongs()
    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
            Text("Favourites", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = { if (songs.isNotEmpty()) viewModel.playSong(songs[0], songs) }) { Icon(Icons.Filled.PlayArrow, contentDescription = "Play all") }
        }
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            if (songs.isEmpty()) {
                item { EmptyState("No favourites yet", "Tap the heart on any song to save it here.") }
            } else {
                items(songs, key = { it.id }) { song ->
                    SongRow(song = song, isFavourite = true, isPlaying = playerState.currentSong?.id == song.id, onClick = { viewModel.playSong(song, songs) }, onFavouriteClick = { viewModel.toggleFavourite(song) })
                }
            }
            item { Spacer(modifier = Modifier.height(96.dp)) }
        }
    }
}

@Composable
fun AlbumsArtistsScreen(viewModel: MusicViewModel, showArtists: Boolean, onBack: () -> Unit, onPlayGroup: (List<Song>) -> Unit) {
    val groups = if (showArtists) viewModel.artists() else viewModel.albums()
    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
            Text(if (showArtists) "Artists" else "Albums", style = MaterialTheme.typography.titleLarge)
        }
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            if (groups.isEmpty()) {
                item { EmptyState("Nothing here yet", "Songs on this device will be grouped here.") }
            } else {
                items(groups) { (name, groupSongs) ->
                    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clickable { onPlayGroup(groupSongs) }, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            AlbumArt(song = groupSongs.firstOrNull(), modifier = Modifier.size(52.dp), cornerDp = 14)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(FormatUtils.formatCount(groupSongs.size, "song", "songs"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(96.dp)) }
        }
    }
}

@Composable
fun VolumeRow() {
    val context = LocalContext.current
    val audioManager = remember {
        context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
    }
    val maxVolume = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    var volumeFraction by remember {
        mutableStateOf(audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC).toFloat() / maxVolume.toFloat())
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Filled.VolumeDown, contentDescription = "Volume down", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Slider(
            value = volumeFraction,
            onValueChange = {
                volumeFraction = it
                audioManager.setStreamVolume(
                    android.media.AudioManager.STREAM_MUSIC,
                    (it * maxVolume).roundToInt(),
                    0
                )
            },
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
        )
        Icon(Icons.Filled.VolumeUp, contentDescription = "Volume up", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun PlayerScreen(viewModel: MusicViewModel, onBack: () -> Unit) {
    val playerState by PlayerManager.state.collectAsState()
    val playerPositionMs by PlayerManager.positionMs.collectAsState()
    val favourites by viewModel.favourites.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val song = playerState.currentSong
    val eqPreset by viewModel.equalizerPreset.collectAsState()
    var showSleep by remember { mutableStateOf(false) }
    var showSpeed by remember { mutableStateOf(false) }
    var showAddToPlaylist by remember { mutableStateOf(false) }

    if (song == null) {
        Column(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            IconButton(onClick = onBack, modifier = Modifier.align(Alignment.Start)) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
            Spacer(modifier = Modifier.height(48.dp))
            EmptyState("Nothing playing", "Pick a song from Home, Search or Library to start listening.")
        }
        return
    }

    if (showSleep) {
        AlertDialog(
            onDismissRequest = { showSleep = false },
            title = { Text("Sleep timer") },
            text = { Text("Music will stop after the selected time.") },
            confirmButton = {
                Column {
                    listOf(15, 30, 60).forEach { minutes ->
                        TextButton(onClick = { PlayerManager.setSleepTimer(minutes); showSleep = false }) { Text("$minutes minutes") }
                    }
                    TextButton(onClick = { PlayerManager.setSleepTimer(null); showSleep = false }) { Text("Off") }
                }
            },
            dismissButton = { TextButton(onClick = { showSleep = false }) { Text("Cancel") } }
        )
    }
    if (showSpeed) {
        AlertDialog(
            onDismissRequest = { showSpeed = false },
            title = { Text("Playback speed") },
            text = {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Slowed + Reverb vibe", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Pitch follows speed: slower sounds deeper with long reverb, faster sounds brighter with short reverb.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = playerState.vibeEnabled, onCheckedChange = { viewModel.setVibeEnabled(it) })
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { speed ->
                        TextButton(
                            onClick = { viewModel.setPlaybackSpeed(speed); showSpeed = false },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("${speed}x" + if (speed == playerState.playbackSpeed) "  •  current" else "") }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showSpeed = false }) { Text("Done") } }
        )
    }
    if (showAddToPlaylist) {
        AddToPlaylistDialog(song = song, playlists = playlists, onDismiss = { showAddToPlaylist = false }, onAdd = { id -> viewModel.addToPlaylist(id, song); showAddToPlaylist = false }, onCreatePlaylist = { name -> viewModel.createPlaylist(name) })
    }

    LazyColumn(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        item {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
                Text("Now Playing", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                IconButton(onClick = { showAddToPlaylist = true }, modifier = Modifier.size(48.dp)) { Icon(Icons.Filled.PlaylistAdd, contentDescription = "Add to playlist") }
            }
            // Artwork fits the phone: never wider than the screen minus padding,
            // never taller than 320dp, always square.
            androidx.compose.foundation.layout.BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp)) {
                val artSize = minOf(maxWidth, 320.dp)
                Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    AlbumArt(song = song, modifier = Modifier.size(artSize), cornerDp = 28, isPlaying = playerState.isPlaying)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(song.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(song.artist, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(song.album, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (playerState.isPlaying) {
                        Spacer(modifier = Modifier.height(8.dp))
                        com.snoah.plus.ui.components.VisualizerBars(isPlaying = true, modifier = Modifier.size(width = 56.dp, height = 20.dp))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Slider(
                        value = playerPositionMs.toFloat(),
                        onValueChange = { PlayerManager.seekTo(it.toLong()) },
                        valueRange = 0f..(if (playerState.durationMs > 0) playerState.durationMs.toFloat() else 1f),
                        modifier = Modifier.fillMaxWidth().height(32.dp)
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(FormatUtils.formatDuration(playerPositionMs), style = MaterialTheme.typography.bodyMedium)
                        Text(FormatUtils.formatDuration(playerState.durationMs), style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    // One row, all buttons the same height, play button centred and larger.
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        modifier = Modifier.fillMaxWidth().height(72.dp)
                    ) {
                        IconButton(onClick = { PlayerManager.toggleShuffle() }, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Filled.Shuffle, contentDescription = "Shuffle", modifier = Modifier.size(26.dp), tint = if (playerState.shuffleEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { PlayerManager.previous() }, modifier = Modifier.size(48.dp)) { Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous", modifier = Modifier.size(34.dp)) }
                        IconButton(
                            onClick = { PlayerManager.playPause() },
                            modifier = Modifier.size(64.dp).background(MaterialTheme.colorScheme.primary, CircleShape)
                        ) {
                            Icon(imageVector = if (playerState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = if (playerState.isPlaying) "Pause" else "Play", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(32.dp))
                        }
                        IconButton(onClick = { PlayerManager.next() }, modifier = Modifier.size(48.dp)) { Icon(Icons.Filled.SkipNext, contentDescription = "Next", modifier = Modifier.size(34.dp)) }
                        IconButton(onClick = { PlayerManager.cycleRepeatMode() }, modifier = Modifier.size(48.dp)) {
                            val icon = if (playerState.repeatMode == RepeatMode.ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat
                            Icon(icon, contentDescription = "Repeat", modifier = Modifier.size(26.dp), tint = if (playerState.repeatMode == RepeatMode.OFF) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary)
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = { PlayerManager.seekBy(-10_000L) }, modifier = Modifier.weight(1f).height(40.dp)) {
                            Icon(Icons.Filled.FastRewind, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("10s", maxLines = 1)
                        }
                        OutlinedButton(onClick = { PlayerManager.seekBy(10_000L) }, modifier = Modifier.weight(1f).height(40.dp)) {
                            Icon(Icons.Filled.FastForward, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("10s", maxLines = 1)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    // Icon-only actions: no names, just icons. A coloured
                    // icon means the feature is on (timer set, speed
                    // changed, or song favourited).
                    Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { showSleep = true }, modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)) {
                            Icon(Icons.Filled.Timer, contentDescription = "Sleep timer", modifier = Modifier.size(24.dp), tint = if (playerState.sleepTimerSecondsLeft != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                        }
                        IconButton(onClick = { showSpeed = true }, modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)) {
                            Icon(Icons.Filled.Speed, contentDescription = "Playback speed", modifier = Modifier.size(24.dp), tint = if (playerState.playbackSpeed != 1f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                        }
                        IconButton(onClick = { viewModel.toggleFavourite(song) }, modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)) {
                            Icon(
                                imageVector = if (favourites.contains(song.id)) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                contentDescription = "Favourite",
                                modifier = Modifier.size(24.dp),
                                tint = if (favourites.contains(song.id)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    if (playerState.vibeEnabled) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Slowed + Reverb vibe on • pitch follows speed (${playerState.playbackSpeed}x)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    VolumeRow()
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("Equalizer", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Text(if (eqPreset == "Balanced") "" else eqPreset, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(listOf("Balanced", "Bass Boost", "Treble Boost", "Vocal", "Rock", "Pop", "Classical")) { presetName ->
                            FilterChip(selected = eqPreset == presetName, onClick = { viewModel.setEqualizerPreset(presetName) }, label = { Text(presetName) })
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("Up next", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                        if (playerState.queue.isNotEmpty()) {
                            TextButton(onClick = { PlayerManager.clearQueue() }) { Text("Clear") }
                        }
                    }
                }
            }
        }
        val queue = playerState.queue
        if (queue.isEmpty()) {
            item { EmptyState("Queue is empty", "Your queue will appear here while music plays.") }
        } else {
            itemsIndexed(queue, key = { index, queueSong -> "$index-${queueSong.id}" }) { _, queueSong ->
                SongRow(song = queueSong, isFavourite = favourites.contains(queueSong.id), isPlaying = playerState.currentSong?.id == queueSong.id, onClick = { viewModel.playSong(queueSong, queue) }, onFavouriteClick = { viewModel.toggleFavourite(queueSong) }, onPlayNextClick = { viewModel.playNext(queueSong) }, onAddToQueueClick = { viewModel.addToQueue(queueSong) })
            }
        }
        item { Spacer(modifier = Modifier.height(32.dp)) }
    }
}

@Composable
fun SettingsScreen(viewModel: MusicViewModel) {
    val darkTheme by viewModel.darkTheme.collectAsState()
    val accentName by viewModel.accentName.collectAsState()
    val preset by viewModel.equalizerPreset.collectAsState()
    val playerState by PlayerManager.state.collectAsState()
    val presets = listOf("Balanced", "Bass Boost", "Treble Boost", "Vocal", "Rock", "Pop", "Classical")

    val settingsSongs by viewModel.songs.collectAsState()
    val settingsFavourites by viewModel.favourites.collectAsState()
    val settingsPlaylists by viewModel.playlists.collectAsState()
    val statsPlays = remember(settingsSongs) { viewModel.totalPlayCount() }
    val statsHours = remember(settingsSongs) { viewModel.totalListeningTimeMs() / 3_600_000 }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item { GradientHeader(title = "Settings", subtitle = "Make Snoah Plus yours") }
        item {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Your music", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(4.dp))
                Text("${settingsSongs.size} songs  •  ${settingsFavourites.size} favourites  •  ${settingsPlaylists.size} playlists  •  $statsPlays plays  •  about $statsHours hours listened", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(20.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Dark theme", style = MaterialTheme.typography.titleMedium)
                        Text("Dark first, easy on the eyes at night", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = darkTheme, onCheckedChange = { viewModel.setDarkTheme(it) })
                }
                Spacer(modifier = Modifier.height(20.dp))
                Text("Accent (black & white)", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AccentOptions.forEach { (name, color) ->
                        FilterChip(selected = accentName == name, onClick = { viewModel.setAccent(name) }, label = { Text(name) })
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
                Text("Equalizer preset", style = MaterialTheme.typography.titleMedium)
                Text("This really changes the sound while a song plays, using the phone's equalizer and bass boost. Saved on this device.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(presets) { item ->
                        FilterChip(selected = preset == item, onClick = { viewModel.setEqualizerPreset(item) }, label = { Text(item) })
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
                Text("Playback", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Slowed + Reverb vibe", style = MaterialTheme.typography.titleMedium)
                        Text("Pitch follows speed and reverb grows as you slow down. Current speed: ${playerState.playbackSpeed}x", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = playerState.vibeEnabled, onCheckedChange = { viewModel.setVibeEnabled(it) })
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("Your last song stays in the mini-player when you reopen the app, paused at the place you left it. Songs you stop mid-way resume from the same place when you tap them again. Sort order, theme, accent and equalizer are all remembered.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(20.dp))
                Text("About", style = MaterialTheme.typography.titleMedium)
                Text("Snoah Plus 1.2.0 • by Shahzaib Muzaffar\nPlays audio files already on your Android device. No streaming, no account, no uploads. Your favourites and playlists stay on your device.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(96.dp))
            }
        }
    }
}
