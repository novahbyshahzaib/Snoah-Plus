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
    }

    companion object {
        private const val KEY_FAVOURITES = "favourites"
        private const val KEY_PLAYLIST_IDS = "playlist_ids"
        private const val KEY_PLAYLIST_PREFIX = "playlist_name_"
        private const val KEY_PLAYLIST_SONGS_PREFIX = "playlist_songs_"
        private const val KEY_PLAYLIST_CREATED_PREFIX = "playlist_created_"
        private const val KEY_RECENT = "recently_played"
    }
}
