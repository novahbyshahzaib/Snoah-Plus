package com.snoah.plus.data

import android.net.Uri

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val contentUri: Uri,
    val albumArtUri: Uri?,
    val dateAdded: Long,
    val sizeBytes: Long,
    /** Folder the file lives in (last part of its storage path), for the Folders view. */
    val folder: String = ""
)

data class Playlist(
    val id: String,
    val name: String,
    val songIds: List<Long>,
    val createdAt: Long
)

enum class SortOrder { TITLE, ARTIST, DURATION, RECENT, MOST_PLAYED }

enum class RepeatMode { OFF, ALL, ONE }
