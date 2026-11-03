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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.snoah.plus.ui.theme.SnoahCyan
import com.snoah.plus.ui.theme.SnoahPink
import com.snoah.plus.ui.theme.SnoahViolet
import com.snoah.plus.util.FormatUtils

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
fun HomeScreen(
    viewModel: MusicViewModel,
    onOpenPlayer: () -> Unit,
    onOpenPlaylist: (String) -> Unit,
    onOpenFavourites: () -> Unit,
    onOpenAlbums: () -> Unit,
    onOpenArtists: () -> Unit
) {
    val songs by viewModel.songs.collectAsState()
    val favourites by viewModel.favourites.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val recent by viewModel.recentSongs().let { remember { mutableStateOf(it) } }
    val playerState by PlayerManager.state.collectAsState()
    val sortOrder by viewModel.sortOrder.collectAsState()
    var selectedForPlaylist by remember { mutableStateOf<Song?>(null) }
    var showCreate by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }

    val displayed = viewModel.filteredSongs()
    val recentSongs = viewModel.recentSongs()

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
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = sortOrder == SortOrder.TITLE, onClick = { viewModel.setSortOrder(SortOrder.TITLE) }, label = { Text("Title") })
                FilterChip(selected = sortOrder == SortOrder.ARTIST, onClick = { viewModel.setSortOrder(SortOrder.ARTIST) }, label = { Text("Artist") })
                FilterChip(selected = sortOrder == SortOrder.RECENT, onClick = { viewModel.setSortOrder(SortOrder.RECENT) }, label = { Text("Recent") })
                FilterChip(selected = sortOrder == SortOrder.DURATION, onClick = { viewModel.setSortOrder(SortOrder.DURATION) }, label = { Text("Longest") })
            }
        }
        if (recentSongs.isNotEmpty()) {
            item {
                Text("Recently played", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(horizontal = 16.dp)) {
                    items(recentSongs.take(10)) { song ->
                        Column(modifier = Modifier.width(120.dp).clickable { viewModel.playSong(song, displayed) }) {
                            AlbumArt(song = song, modifier = Modifier.size(120.dp), cornerDp = 20)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                            Text(song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
        item {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Card(modifier = Modifier.weight(1f).clickable { onOpenFavourites() }, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Icon(Icons.Filled.Favorite, contentDescription = null, tint = SnoahPink)
                        Text("Favourites", style = MaterialTheme.typography.titleMedium)
                        Text(FormatUtils.formatCount(viewModel.favouriteSongs().size, "song", "songs"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Card(modifier = Modifier.weight(1f).clickable { onOpenAlbums() }, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Icon(Icons.Filled.Album, contentDescription = null, tint = SnoahViolet)
                        Text("Albums", style = MaterialTheme.typography.titleMedium)
                        Text(FormatUtils.formatCount(viewModel.albums().size, "album", "albums"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Card(modifier = Modifier.weight(1f).clickable { onOpenArtists() }, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Icon(Icons.Filled.Person, contentDescription = null, tint = SnoahCyan)
                        Text("Artists", style = MaterialTheme.typography.titleMedium)
                        Text(FormatUtils.formatCount(viewModel.artists().size, "artist", "artists"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("All songs", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = { showCreate = true }) { Text("+ Playlist") }
            }
        }
        if (displayed.isEmpty()) {
            item { EmptyState("No songs yet", "Grant audio permission and add music files to this device to see them here.") }
        } else {
            items(displayed, key = { it.id }) { song ->
                var menuExpanded by remember { mutableStateOf(false) }
                Box {
                    SongRow(
                        song = song,
                        isFavourite = favourites.contains(song.id),
                        isPlaying = playerState.currentSong?.id == song.id,
                        onClick = { viewModel.playSong(song, displayed) },
                        onFavouriteClick = { viewModel.toggleFavourite(song) }
                    )
                    // Small overflow affordance is handled by long-press alternative:
                    // tapping the row plays; favourites are on the row itself.
                    // Playlist adding is available from the playlist detail screen
                    // and from search via the same dialog in the player queue.
                }
            }
        }
        item { Spacer(modifier = Modifier.height(96.dp)) }
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
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
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
    var showCreate by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

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
                    Icon(Icons.Filled.Favorite, contentDescription = null, tint = SnoahPink)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Favourites", style = MaterialTheme.typography.titleMedium)
                        Text(FormatUtils.formatCount(viewModel.favouriteSongs().size, "song", "songs"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
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
                        Box(modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(Brush.linearGradient(listOf(SnoahViolet, SnoahPink))), contentAlignment = Alignment.Center) {
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
    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
            Text(playlist.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
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
fun PlayerScreen(viewModel: MusicViewModel, onBack: () -> Unit) {
    val playerState by PlayerManager.state.collectAsState()
    val favourites by viewModel.favourites.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val song = playerState.currentSong
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
            text = { Text("Choose how fast the song plays.") },
            confirmButton = {
                Column {
                    listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { speed ->
                        TextButton(onClick = { PlayerManager.setSpeed(speed); showSpeed = false }) { Text("${speed}x") }
                    }
                }
            },
            dismissButton = { TextButton(onClick = { showSpeed = false }) { Text("Cancel") } }
        )
    }
    if (showAddToPlaylist) {
        AddToPlaylistDialog(song = song, playlists = playlists, onDismiss = { showAddToPlaylist = false }, onAdd = { id -> viewModel.addToPlaylist(id, song); showAddToPlaylist = false }, onCreatePlaylist = { name -> viewModel.createPlaylist(name) })
    }

    LazyColumn(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(SnoahViolet.copy(alpha = 0.25f), MaterialTheme.colorScheme.background)))) {
        item {
            Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
                Text("Now Playing", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = { showAddToPlaylist = true }) { Icon(Icons.Filled.PlaylistAdd, contentDescription = "Add to playlist") }
            }
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                AlbumArt(song = song, modifier = Modifier.size(300.dp), cornerDp = 32)
                Spacer(modifier = Modifier.height(24.dp))
                Text(song.title, style = MaterialTheme.typography.headlineMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(song.artist, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(song.album, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(20.dp))
                Slider(
                    value = playerState.positionMs.toFloat(),
                    onValueChange = { PlayerManager.seekTo(it.toLong()) },
                    valueRange = 0f..(if (playerState.durationMs > 0) playerState.durationMs.toFloat() else 1f),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(FormatUtils.formatDuration(playerState.positionMs), style = MaterialTheme.typography.bodyMedium)
                    Text(FormatUtils.formatDuration(playerState.durationMs), style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                    IconButton(onClick = { PlayerManager.toggleShuffle() }) {
                        Icon(Icons.Filled.Shuffle, contentDescription = "Shuffle", tint = if (playerState.shuffleEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { PlayerManager.previous() }, modifier = Modifier.size(56.dp)) { Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous", modifier = Modifier.size(40.dp)) }
                    IconButton(
                        onClick = { PlayerManager.playPause() },
                        modifier = Modifier.size(76.dp).background(MaterialTheme.colorScheme.primary, CircleShape)
                    ) {
                        Icon(imageVector = if (playerState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = if (playerState.isPlaying) "Pause" else "Play", tint = Color.White, modifier = Modifier.size(40.dp))
                    }
                    IconButton(onClick = { PlayerManager.next() }, modifier = Modifier.size(56.dp)) { Icon(Icons.Filled.SkipNext, contentDescription = "Next", modifier = Modifier.size(40.dp)) }
                    IconButton(onClick = { PlayerManager.cycleRepeatMode() }) {
                        val icon = if (playerState.repeatMode == RepeatMode.ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat
                        Icon(icon, contentDescription = "Repeat", tint = if (playerState.repeatMode == RepeatMode.OFF) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = false, onClick = { showSleep = true }, label = { Text(if (playerState.sleepTimerSecondsLeft != null) "Sleep ${playerState.sleepTimerSecondsLeft!! / 60}m left" else "Sleep timer") }, leadingIcon = { Icon(Icons.Filled.Timer, contentDescription = null, modifier = Modifier.size(16.dp)) })
                    FilterChip(selected = false, onClick = { showSpeed = true }, label = { Text("${playerState.playbackSpeed}x speed") }, leadingIcon = { Icon(Icons.Filled.Speed, contentDescription = null, modifier = Modifier.size(16.dp)) })
                    FilterChip(selected = favourites.contains(song.id), onClick = { viewModel.toggleFavourite(song) }, label = { Text("Favourite") }, leadingIcon = { Icon(Icons.Filled.Favorite, contentDescription = null, modifier = Modifier.size(16.dp)) })
                }
                Spacer(modifier = Modifier.height(20.dp))
                Text("Up next", style = MaterialTheme.typography.titleLarge, modifier = Modifier.fillMaxWidth())
            }
        }
        val queue = playerState.queue
        if (queue.isEmpty()) {
            item { EmptyState("Queue is empty", "Your queue will appear here while music plays.") }
        } else {
            items(queue, key = { it.id }) { queueSong ->
                SongRow(song = queueSong, isFavourite = favourites.contains(queueSong.id), isPlaying = playerState.currentSong?.id == queueSong.id, onClick = { viewModel.playSong(queueSong, queue) }, onFavouriteClick = { viewModel.toggleFavourite(queueSong) })
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
    val presets = listOf("Balanced", "Bass Boost", "Treble Boost", "Vocal", "Rock", "Pop", "Classical")

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item { GradientHeader(title = "Settings", subtitle = "Make Snoah Plus yours") }
        item {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Dark theme", style = MaterialTheme.typography.titleMedium)
                        Text("Dark first, easy on the eyes at night", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = darkTheme, onCheckedChange = { viewModel.setDarkTheme(it) })
                }
                Spacer(modifier = Modifier.height(20.dp))
                Text("Accent colour", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AccentOptions.forEach { (name, color) ->
                        FilterChip(selected = accentName == name, onClick = { viewModel.setAccent(name) }, label = { Text(name) })
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
                Text("Equalizer preset", style = MaterialTheme.typography.titleMedium)
                Text("Saved on this device. This preset picker does not change system audio.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(presets) { item ->
                        FilterChip(selected = preset == item, onClick = { viewModel.setEqualizerPreset(item) }, label = { Text(item) })
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
                Text("About", style = MaterialTheme.typography.titleMedium)
                Text("Snoah Plus 1.0.0 • by Shahzaib Muzaffar\nPlays audio files already on your Android device. No streaming, no account, no uploads. Your favourites and playlists stay on your device.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(96.dp))
            }
        }
    }
}
