package com.snoah.plus.ui

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snoah.plus.data.LibraryStore
import com.snoah.plus.data.MusicRepository
import com.snoah.plus.data.Playlist
import com.snoah.plus.data.Song
import com.snoah.plus.data.SortOrder
import com.snoah.plus.playback.PlayerManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MusicViewModel(context: Context) : ViewModel() {

    private val appContext = context.applicationContext
    private val repository = MusicRepository(appContext)
    private val library = LibraryStore(appContext)

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _loadMessage = MutableStateFlow("")
    val loadMessage: StateFlow<String> = _loadMessage.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchHistory = MutableStateFlow<List<String>>(emptyList())
    val searchHistory: StateFlow<List<String>> = _searchHistory.asStateFlow()

    private var loadJob: Job? = null
    private var observerJob: Job? = null

    // Reload when MediaStore changes (a new file is downloaded or
    // deleted), so songs appear without switching tabs or restarting.
    // Debounced: the scanner fires many events in a row, and reloading
    // on every one kept cancelling the scan and rebuilding the lists.
    private val mediaObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            observerJob?.cancel()
            observerJob = viewModelScope.launch {
                delay(1500)
                loadSongs()
            }
        }
    }

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

    private val _accentName = MutableStateFlow("White")
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
        library.getAccentName()?.let { saved ->
            // Old colour names from earlier versions fall back to White.
            _accentName.value = if (saved in listOf("White", "Silver", "Graphite")) saved else "White"
        }
        library.getEqualizerPreset()?.let {
            _equalizerPreset.value = it
            PlayerManager.setEqualizerPreset(it)
        }
        _searchHistory.value = library.getSearchHistory()
        try {
            appContext.contentResolver.registerContentObserver(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                true,
                mediaObserver
            )
        } catch (e: Exception) {
        }
    }

    override fun onCleared() {
        try {
            appContext.contentResolver.unregisterContentObserver(mediaObserver)
        } catch (e: Exception) {
        }
        super.onCleared()
    }

    fun loadSongs(force: Boolean = false) {
        // Single flight: while a scan is running, another trigger
        // (resume, observer, Home entry) must not cancel it and
        // restart the scan, or the list never settles.
        if (!force && loadJob?.isActive == true) return
        // Cancel a stale load so a fresh Refresh can never be
        // overwritten by an older, slower query finishing later.
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _isLoading.value = true
            _loadMessage.value = "Scanning your music…"
            var loaded: List<Song> = emptyList()
            // MediaStore can still be scanning right after permission
            // is granted or files arrive: retry an empty result a
            // few times instead of showing "No songs yet" too early.
            for (attempt in 0 until 3) {
                loaded = withContext(Dispatchers.IO) { repository.loadSongs() }
                if (loaded.isNotEmpty() || attempt == 2) break
                delay(900)
            }
            _songs.value = loaded
            _isLoading.value = false
            _loadMessage.value = if (loaded.isEmpty()) {
                "No songs found. Check audio permission in Settings, then tap Refresh."
            } else {
                "${loaded.size} songs found"
            }
            // Bring back the last song in the mini-player, paused, ready to play.
            PlayerManager.restoreLastPlayed(loaded)
        }
    }

    /** Manual refresh, e.g. from the refresh button on Home. */
    fun refreshSongs() {
        loadSongs(force = true)
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun submitSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        library.saveSearchQuery(trimmed)
        _searchHistory.value = library.getSearchHistory()
    }

    fun clearSearchHistory() {
        library.clearSearchHistory()
        _searchHistory.value = emptyList()
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
            SortOrder.RECENT -> base.sortedWith(compareByDescending<Song> { it.dateAdded }.thenBy { it.title.lowercase() })
            SortOrder.MOST_PLAYED -> base.sortedByDescending { library.getPlayCount(it.id) }
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

    fun totalPlayCount(): Int {
        return _songs.value.sumOf { library.getPlayCount(it.id) }
    }

    fun totalListeningTimeMs(): Long {
        return _songs.value.sumOf { it.durationMs * library.getPlayCount(it.id).toLong() }
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
