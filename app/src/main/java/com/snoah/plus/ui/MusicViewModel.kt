package com.snoah.plus.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snoah.plus.data.LibraryStore
import com.snoah.plus.data.MusicRepository
import com.snoah.plus.data.Playlist
import com.snoah.plus.data.Song
import com.snoah.plus.data.SortOrder
import com.snoah.plus.playback.PlayerManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MusicViewModel(context: Context) : ViewModel() {

    private val repository = MusicRepository(context.applicationContext)
    private val library = LibraryStore(context.applicationContext)

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _sortOrder = MutableStateFlow(SortOrder.TITLE)
    val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()

    private val _favourites = MutableStateFlow<Set<Long>>(emptySet())
    val favourites: StateFlow<Set<Long>> = _favourites.asStateFlow()

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    private val _recentIds = MutableStateFlow<List<Long>>(emptyList())
    val recentIds: StateFlow<List<Long>> = _recentIds.asStateFlow()

    private val _darkTheme = MutableStateFlow(true)
    val darkTheme: StateFlow<Boolean> = _darkTheme.asStateFlow()

    private val _accentName = MutableStateFlow("Violet")
    val accentName: StateFlow<String> = _accentName.asStateFlow()

    private val _equalizerPreset = MutableStateFlow("Balanced")
    val equalizerPreset: StateFlow<String> = _equalizerPreset.asStateFlow()

    init {
        _favourites.value = library.getFavourites()
        _playlists.value = library.getPlaylists()
        _recentIds.value = library.getRecentlyPlayed()
        // Settings the user chose before must come back exactly as left.
        runCatching {
            library.getSortOrder()?.let { _sortOrder.value = SortOrder.valueOf(it) }
        }
        library.getDarkTheme()?.let { _darkTheme.value = it }
        library.getAccentName()?.let { _accentName.value = it }
        library.getEqualizerPreset()?.let {
            _equalizerPreset.value = it
            PlayerManager.setEqualizerPreset(it)
        }
    }

    fun loadSongs() {
        viewModelScope.launch {
            _isLoading.value = true
            val loaded = withContext(Dispatchers.IO) { repository.loadSongs() }
            _songs.value = loaded
            _isLoading.value = false
            // Bring back the last song in the mini-player, paused, ready to play.
            PlayerManager.restoreLastPlayed(loaded)
        }
    }

    /** Manual refresh, e.g. from the refresh button on Home. */
    fun refreshSongs() {
        loadSongs()
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSortOrder(order: SortOrder) {
        _sortOrder.value = order
        library.saveSortOrder(order.name)
    }

    fun filteredSongs(): List<Song> {
        val query = _searchQuery.value.trim().lowercase()
        val base = if (query.isEmpty()) {
            _songs.value
        } else {
            _songs.value.filter {
                it.title.lowercase().contains(query) ||
                    it.artist.lowercase().contains(query) ||
                    it.album.lowercase().contains(query)
            }
        }
        return when (_sortOrder.value) {
            SortOrder.TITLE -> base.sortedBy { it.title.lowercase() }
            SortOrder.ARTIST -> base.sortedBy { it.artist.lowercase() }
            SortOrder.DURATION -> base.sortedByDescending { it.durationMs }
            SortOrder.RECENT -> base.sortedByDescending { it.dateAdded }
        }
    }

    fun favouriteSongs(): List<Song> {
        val favs = _favourites.value
        return _songs.value.filter { favs.contains(it.id) }
    }

    fun recentSongs(): List<Song> {
        val byId = _songs.value.associateBy { it.id }
        return _recentIds.value.mapNotNull { byId[it] }
    }

    fun albums(): List<Pair<String, List<Song>>> {
        return _songs.value.groupBy { it.album }.toList().sortedBy { it.first.lowercase() }
    }

    fun artists(): List<Pair<String, List<Song>>> {
        return _songs.value.groupBy { it.artist }.toList().sortedBy { it.first.lowercase() }
    }

    fun playSong(song: Song, queue: List<Song>) {
        val index = queue.indexOfFirst { it.id == song.id }.takeIf { it >= 0 } ?: 0
        // Resume where this song was left off, if it was stopped mid-song.
        val resume = library.getResumePosition(song.id)
        val startAt = if (resume > 3000 && resume < (song.durationMs - 5000).coerceAtLeast(0L)) resume else 0L
        PlayerManager.playQueue(queue, index, startAtMs = startAt) { played ->
            library.recordPlayed(played.id)
            _recentIds.value = library.getRecentlyPlayed()
        }
    }

    fun playNext(song: Song) {
        PlayerManager.playNext(song)
    }

    fun addToQueue(song: Song) {
        PlayerManager.addToQueue(song)
    }

    fun shuffleAll() {
        val songs = filteredSongs()
        if (songs.isEmpty()) return
        val shuffled = songs.shuffled()
        PlayerManager.playQueue(shuffled, 0) { played ->
            library.recordPlayed(played.id)
            _recentIds.value = library.getRecentlyPlayed()
        }
    }

    fun mostPlayedSongs(limit: Int = 8): List<Song> {
        return _songs.value
            .filter { library.getPlayCount(it.id) > 0 }
            .sortedByDescending { library.getPlayCount(it.id) }
            .take(limit)
    }

    fun recentlyAddedSongs(limit: Int = 8): List<Song> {
        return _songs.value.sortedByDescending { it.dateAdded }.take(limit)
    }

    fun playlistM3U(playlist: Playlist): String {
        val byId = _songs.value.associateBy { it.id }
        val lines = StringBuilder("#EXTM3U\n#PLAYLIST:${playlist.name}\n")
        playlist.songIds.mapNotNull { byId[it] }.forEach { song ->
            lines.append("#EXTINF:${song.durationMs / 1000},${song.artist} - ${song.title}\n")
            lines.append("${song.contentUri}\n")
        }
        return lines.toString()
    }

    fun toggleFavourite(song: Song) {
        library.toggleFavourite(song.id)
        _favourites.value = library.getFavourites()
    }

    fun createPlaylist(name: String) {
        if (name.isBlank()) return
        library.createPlaylist(name.trim())
        _playlists.value = library.getPlaylists()
    }

    fun renamePlaylist(id: String, newName: String) {
        if (newName.isBlank()) return
        library.renamePlaylist(id, newName.trim())
        _playlists.value = library.getPlaylists()
    }

    fun deletePlaylist(id: String) {
        library.deletePlaylist(id)
        _playlists.value = library.getPlaylists()
    }

    fun addToPlaylist(playlistId: String, song: Song) {
        library.addToPlaylist(playlistId, song.id)
        _playlists.value = library.getPlaylists()
    }

    fun removeFromPlaylist(playlistId: String, song: Song) {
        library.removeFromPlaylist(playlistId, song.id)
        _playlists.value = library.getPlaylists()
    }

    fun playlistSongs(playlist: Playlist): List<Song> {
        val byId = _songs.value.associateBy { it.id }
        return playlist.songIds.mapNotNull { byId[it] }
    }

    fun setDarkTheme(dark: Boolean) {
        _darkTheme.value = dark
        library.saveDarkTheme(dark)
    }

    fun setAccent(name: String) {
        _accentName.value = name
        library.saveAccentName(name)
    }

    fun setEqualizerPreset(preset: String) {
        _equalizerPreset.value = preset
        library.saveEqualizerPreset(preset)
        PlayerManager.setEqualizerPreset(preset)
    }

    fun setPlaybackSpeed(speed: Float) {
        PlayerManager.setSpeed(speed)
    }

    fun setVibeEnabled(enabled: Boolean) {
        PlayerManager.setVibeEnabled(enabled)
    }
}
