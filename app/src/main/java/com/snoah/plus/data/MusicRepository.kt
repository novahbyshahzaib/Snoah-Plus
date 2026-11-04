package com.snoah.plus.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore

class MusicRepository(private val context: Context) {

    fun loadSongs(): List<Song> {
        val found = linkedMapOf<Uri, Song>()
        // 1) Strict music query (the usual case).
        queryCollection(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} > 0",
            found
        )
        // 2) Relaxed query: files from chat apps (Telegram, WhatsApp) and
        //    recorders are often not flagged IS_MUSIC, so a strict query
        //    alone can return nothing even though songs are on the phone.
        //    This pass accepts any audio file with a real duration, and
        //    skips ringtones / notifications / alarms.
        queryCollection(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            "${MediaStore.Audio.Media.DURATION} > 0",
            found,
            relaxed = true
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            queryCollection(
                MediaStore.Audio.Media.INTERNAL_CONTENT_URI,
                "${MediaStore.Audio.Media.DURATION} > 0",
                found,
                relaxed = true
            )
        }
        return found.values.sortedBy { it.title.lowercase() }
    }

    private fun queryCollection(
        collection: Uri,
        selection: String,
        out: MutableMap<Uri, Song>,
        relaxed: Boolean = false
    ) {
        val projectionList = mutableListOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.DATE_MODIFIED
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            projectionList.add(MediaStore.Audio.Media.RELATIVE_PATH)
        }
        val projection = projectionList.toTypedArray()
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"
        try {
            context.contentResolver.query(collection, projection, selection, null, sortOrder)?.use { cursor ->
                val idCol = cursor.getColumnIndex(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM)
                val durationCol = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION)
                val albumIdCol = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID)
                val dateCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATE_ADDED)
                val dateModifiedCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATE_MODIFIED)
                val sizeCol = cursor.getColumnIndex(MediaStore.Audio.Media.SIZE)
                val mimeCol = cursor.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE)
                val pathCol = cursor.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH)
                if (idCol < 0) return
                while (cursor.moveToNext()) {
                    try {
                        val id = cursor.getLong(idCol)
                        val title = if (titleCol >= 0) cursor.getString(titleCol) ?: "Unknown title" else "Unknown title"
                        val mime = if (mimeCol >= 0) cursor.getString(mimeCol) ?: "" else ""
                        val relativePath = if (pathCol >= 0) cursor.getString(pathCol) ?: "" else ""
                        if (relaxed) {
                            val lowerPath = relativePath.lowercase()
                            if (lowerPath.contains("ringtone") || lowerPath.contains("notification") || lowerPath.contains("alarm")) {
                                continue
                            }
                            val isAudioMime = mime.startsWith("audio/")
                            val lowerTitle = title.lowercase()
                            val hasAudioExt = lowerTitle.endsWith(".mp3") || lowerTitle.endsWith(".m4a") ||
                                lowerTitle.endsWith(".flac") || lowerTitle.endsWith(".wav") ||
                                lowerTitle.endsWith(".ogg") || lowerTitle.endsWith(".opus") ||
                                lowerTitle.endsWith(".aac")
                            if (!isAudioMime && !hasAudioExt && mime.isNotEmpty()) continue
                        }
                        val albumId = if (albumIdCol >= 0) cursor.getLong(albumIdCol) else 0L
                        val artUri = if (albumId > 0) {
                            try {
                                ContentUris.withAppendedId(
                                    Uri.parse("content://media/external/audio/albumart"),
                                    albumId
                                )
                            } catch (e: Exception) {
                                null
                            }
                        } else null
                        val uri = ContentUris.withAppendedId(collection, id)
                        if (out.containsKey(uri)) continue
                        val dateAdded = if (dateCol >= 0) cursor.getLong(dateCol) else 0L
                        val dateModified = if (dateModifiedCol >= 0) cursor.getLong(dateModifiedCol) else 0L
                        // DATE_ADDED is 0 for many files (e.g. chat-app audio),
                        // which made "Recent" sorting look like it did nothing.
                        // Fall back to the file's modified time so Recent
                        // sorting always has a real order.
                        val effectiveDate = if (dateAdded > 0) dateAdded else dateModified
                        // Folder name for the Folders view: the last part
                        // of the storage path, e.g. "Telegram Audio".
                        val folder = relativePath.trim('/').split('/')
                            .lastOrNull { it.isNotEmpty() } ?: "Other files"
                        out[uri] = Song(
                            id = id,
                            title = title,
                            artist = if (artistCol >= 0) cursor.getString(artistCol) ?: "Unknown artist" else "Unknown artist",
                            album = if (albumCol >= 0) cursor.getString(albumCol) ?: "Unknown album" else "Unknown album",
                            durationMs = if (durationCol >= 0) cursor.getLong(durationCol) else 0L,
                            contentUri = uri,
                            albumArtUri = artUri,
                            dateAdded = effectiveDate,
                            sizeBytes = if (sizeCol >= 0) cursor.getLong(sizeCol) else 0L,
                            folder = folder
                        )
                    } catch (e: Exception) {
                        // Skip one bad row instead of losing the whole list.
                    }
                }
            }
        } catch (e: Exception) {
            // One collection failing must not lose songs from the others.
        }
    }
}
