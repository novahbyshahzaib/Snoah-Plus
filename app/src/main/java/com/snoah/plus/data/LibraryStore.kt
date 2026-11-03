package com.snoah.plus.data

import android.content.Context
import android.content.SharedPreferences
import java.util.UUID

/**
 * Favourites and playlists are stored locally on the device in
 * SharedPreferences. No account, no network and no server is needed.
 */
class LibraryStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("snoah_plus_library", Context.MODE_PRIVATE)

    fun getFavourites(): Set<Long> {
        return prefs.getStringSet(KEY_FAVOURITES, emptySet())
            ?.mapNotNull { it.toLongOrNull() }?.toSet() ?: emptySet()
    }

    fun isFavourite(songId: Long): Boolean = getFavourites().contains(songId)

    fun toggleFavourite(songId: Long): Boolean {
        val current = getFavourites().toMutableSet()
        val nowFavourite = if (current.contains(songId)) {
            current.remove(songId)
            false
        } else {
            current.add(songId)
            true
        }
        prefs.edit().putStringSet(KEY_FAVOURITES, current.map { it.toString() }.toSet()).apply()
        return nowFavourite
    }

    fun getPlaylists(): List<Playlist> {
        val names = prefs.getStringSet(KEY_PLAYLIST_IDS, emptySet()) ?: emptySet()
        return names.mapNotNull { id ->
            val name = prefs.getString("${KEY_PLAYLIST_PREFIX}$id", null) ?: return@mapNotNull null
            val ids = prefs.getStringSet("${KEY_PLAYLIST_SONGS_PREFIX}$id", emptySet())
                ?.mapNotNull { it.toLongOrNull() } ?: emptyList()
            val created = prefs.getLong("${KEY_PLAYLIST_CREATED_PREFIX}$id", System.currentTimeMillis())
            Playlist(id = id, name = name, songIds = ids, createdAt = created)
        }.sortedBy { it.name.lowercase() }
    }

    fun createPlaylist(name: String): Playlist {
        val id = UUID.randomUUID().toString()
        prefs.edit()
            .putStringSet(KEY_PLAYLIST_IDS, getPlaylists().map { it.id }.toSet() + id)
            .putString("${KEY_PLAYLIST_PREFIX}$id", name)
            .putLong("${KEY_PLAYLIST_CREATED_PREFIX}$id", System.currentTimeMillis())
            .putStringSet("${KEY_PLAYLIST_SONGS_PREFIX}$id", emptySet())
            .apply()
        return Playlist(id, name, emptyList(), System.currentTimeMillis())
    }

    fun renamePlaylist(id: String, newName: String) {
        prefs.edit().putString("${KEY_PLAYLIST_PREFIX}$id", newName).apply()
    }

    fun deletePlaylist(id: String) {
        val remaining = getPlaylists().map { it.id }.toSet() - id
        prefs.edit()
            .putStringSet(KEY_PLAYLIST_IDS, remaining)
            .remove("${KEY_PLAYLIST_PREFIX}$id")
            .remove("${KEY_PLAYLIST_SONGS_PREFIX}$id")
            .remove("${KEY_PLAYLIST_CREATED_PREFIX}$id")
            .apply()
    }

    fun addToPlaylist(playlistId: String, songId: Long) {
        val current = getPlaylists().firstOrNull { it.id == playlistId }?.songIds ?: return
        if (current.contains(songId)) return
        prefs.edit().putStringSet(
            "${KEY_PLAYLIST_SONGS_PREFIX}$playlistId",
            (current + songId).map { it.toString() }.toSet()
        ).apply()
    }

    fun removeFromPlaylist(playlistId: String, songId: Long) {
        val current = getPlaylists().firstOrNull { it.id == playlistId }?.songIds ?: return
        prefs.edit().putStringSet(
            "${KEY_PLAYLIST_SONGS_PREFIX}$playlistId",
            (current - songId).map { it.toString() }.toSet()
        ).apply()
    }

    fun getRecentlyPlayed(): List<Long> {
        return prefs.getString(KEY_RECENT, "")
            ?.split(",")?.mapNotNull { it.toLongOrNull() } ?: emptyList()
    }

    fun recordPlayed(songId: Long) {
        val current = getRecentlyPlayed().toMutableList()
        current.remove(songId)
        current.add(0, songId)
        val trimmed = current.take(30)
        prefs.edit().putString(KEY_RECENT, trimmed.joinToString(",")).apply()
        recordPlayCount(songId)
    }

    // ---- Settings that must survive an app restart ----

    fun getSortOrder(): String? = prefs.getString(KEY_SORT_ORDER, null)
    fun saveSortOrder(order: String) {
        prefs.edit().putString(KEY_SORT_ORDER, order).apply()
    }

    fun getDarkTheme(): Boolean? =
        if (prefs.contains(KEY_DARK_THEME)) prefs.getBoolean(KEY_DARK_THEME, true) else null

    fun saveDarkTheme(dark: Boolean) {
        prefs.edit().putBoolean(KEY_DARK_THEME, dark).apply()
    }

    fun getAccentName(): String? = prefs.getString(KEY_ACCENT_NAME, null)
    fun saveAccentName(name: String) {
        prefs.edit().putString(KEY_ACCENT_NAME, name).apply()
    }

    fun getEqualizerPreset(): String? = prefs.getString(KEY_EQ_PRESET, null)
    fun saveEqualizerPreset(preset: String) {
        prefs.edit().putString(KEY_EQ_PRESET, preset).apply()
    }

    fun getPlaybackSpeed(): Float = prefs.getFloat(KEY_SPEED, 1f)
    fun savePlaybackSpeed(speed: Float) {
        prefs.edit().putFloat(KEY_SPEED, speed).apply()
    }

    fun getVibeEnabled(): Boolean = prefs.getBoolean(KEY_VIBE, false)
    fun saveVibeEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_VIBE, enabled).apply()
    }

    // ---- Last played song / queue, so the mini-player is there on open ----

    fun saveLastPlayed(songId: Long, queueIds: List<Long>, queueIndex: Int, positionMs: Long) {
        prefs.edit()
            .putLong(KEY_LAST_SONG, songId)
            .putString(KEY_LAST_QUEUE, queueIds.joinToString(","))
            .putInt(KEY_LAST_INDEX, queueIndex)
            .putLong(KEY_LAST_POSITION, positionMs)
            .apply()
    }

    fun getLastSongId(): Long = prefs.getLong(KEY_LAST_SONG, -1L)
    fun getLastQueueIds(): List<Long> =
        prefs.getString(KEY_LAST_QUEUE, "")?.split(",")?.mapNotNull { it.toLongOrNull() } ?: emptyList()

    fun getLastQueueIndex(): Int = prefs.getInt(KEY_LAST_INDEX, 0)
    fun getLastPositionMs(): Long = prefs.getLong(KEY_LAST_POSITION, 0L)

    fun saveResumePosition(songId: Long, positionMs: Long) {
        prefs.edit().putLong("$KEY_RESUME_PREFIX$songId", positionMs).apply()
    }

    fun getResumePosition(songId: Long): Long = prefs.getLong("$KEY_RESUME_PREFIX$songId", 0L)
    fun clearResumePosition(songId: Long) {
        prefs.edit().remove("$KEY_RESUME_PREFIX$songId").apply()
    }

    // ---- Play counts for Most Played ----

    fun recordPlayCount(songId: Long) {
        val key = "$KEY_PLAY_COUNT_PREFIX$songId"
        prefs.edit().putInt(key, prefs.getInt(key, 0) + 1).apply()
    }

    fun getPlayCount(songId: Long): Int = prefs.getInt("$KEY_PLAY_COUNT_PREFIX$songId", 0)

    companion object {
        private const val KEY_FAVOURITES = "favourites"
        private const val KEY_PLAYLIST_IDS = "playlist_ids"
        private const val KEY_PLAYLIST_PREFIX = "playlist_name_"
        private const val KEY_PLAYLIST_SONGS_PREFIX = "playlist_songs_"
        private const val KEY_PLAYLIST_CREATED_PREFIX = "playlist_created_"
        private const val KEY_RECENT = "recently_played"
        private const val KEY_SORT_ORDER = "sort_order"
        private const val KEY_DARK_THEME = "dark_theme"
        private const val KEY_ACCENT_NAME = "accent_name"
        private const val KEY_EQ_PRESET = "equalizer_preset"
        private const val KEY_SPEED = "playback_speed"
        private const val KEY_VIBE = "vibe_enabled"
        private const val KEY_LAST_SONG = "last_song_id"
        private const val KEY_LAST_QUEUE = "last_queue_ids"
        private const val KEY_LAST_INDEX = "last_queue_index"
        private const val KEY_LAST_POSITION = "last_position_ms"
        private const val KEY_RESUME_PREFIX = "resume_position_"
        private const val KEY_PLAY_COUNT_PREFIX = "play_count_"
    }
}
