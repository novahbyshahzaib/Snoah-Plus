package com.snoah.plus.playback

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.snoah.plus.data.LibraryStore
import com.snoah.plus.data.RepeatMode
import com.snoah.plus.data.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PlayerState(
    val currentSong: Song? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val queue: List<Song> = emptyList(),
    val queueIndex: Int = -1,
    val shuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val playbackSpeed: Float = 1f,
    val vibeEnabled: Boolean = false,
    val equalizerPreset: String = "Balanced",
    val sleepTimerSecondsLeft: Int? = null
)

/**
 * Single playback controller used by the whole app.
 *
 * The player itself lives in PlaybackService (the service owns one
 * ExoPlayer and a MediaSession), and this object talks to it only
 * through a MediaController connected to that session. That is the
 * arrangement that makes Android show the media notification and the
 * Quick Settings tile: the controller connection activates the
 * session, and every play/pause/skip goes through the session, so
 * the system media UI sees the same state the app shows. Driving a
 * separate player directly from the UI left the system UI blind.
 *
 * Audio effects are real Android audio effects attached to the player's
 * audio session: an Equalizer + BassBoost for the EQ presets, and an
 * EnvironmentalReverb whose decay follows the playback speed when the
 * Slowed + Reverb vibe is on. Pitch follows speed in vibe mode, so a
 * slowed song sounds deeper and a sped-up song sounds brighter, like
 * the slowed/reverb versions people share online. Everything is wrapped
 * in try/catch: on a device that does not support an effect, playback
 * simply continues without that effect.
 */
object PlayerManager {

    // The controller is a Player: every playback call below goes
    // through the session to the service-owned ExoPlayer.
    private var player: Player? = null
    private var controller: MediaController? = null
    private var connectStarted = false
    private var pendingAction: (() -> Unit)? = null
    private var appContext: Context? = null
    private var library: LibraryStore? = null

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val handler = Handler(Looper.getMainLooper())
    private var sleepEndAtMs: Long? = null
    private var tickCount = 0

    private var noisyReceiver: BroadcastReceiver? = null

    /** MediaItem with id + title/artist/album/artwork, so Android's
     *  media notification, Quick Settings tile and lock screen can
     *  show what is playing. Without metadata they show nothing. */
    private fun mediaItemFor(song: Song): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(song.title)
            .setArtist(song.artist)
            .setAlbumTitle(song.album)
            .setArtworkUri(song.albumArtUri)
            .setIsPlayable(true)
            .build()
        return MediaItem.Builder()
            .setMediaId(song.id.toString())
            .setUri(song.contentUri)
            .setMediaMetadata(metadata)
            .build()
    }

    /** Connect to the service's session. Safe to call repeatedly:
     *  the connection is started once, and a play request that
     *  arrives before the controller is ready is kept and run as
     *  soon as it connects, instead of being silently dropped. */
    private fun connectIfNeeded() {
        val ctx = appContext ?: return
        if (connectStarted) return
        connectStarted = true
        try {
            val token = SessionToken(ctx, ComponentName(ctx, PlaybackService::class.java))
            val future = MediaController.Builder(ctx, token).buildAsync()
            future.addListener({
                try {
                    val connected = future.get()
                    controller = connected
                    player = connected
                    connected.addListener(listener)
                    applyPlaybackParameters()
                    val action = pendingAction
                    pendingAction = null
                    action?.invoke()
                } catch (e: Exception) {
                    connectStarted = false
                }
            }, ContextCompat.getMainExecutor(ctx))
        } catch (e: Exception) {
            connectStarted = false
        }
    }

    private val ticker = object : Runnable {
        override fun run() {
            val p = player
            if (p != null) {
                val newPosition = p.currentPosition
                if (kotlin.math.abs(newPosition - _positionMs.value) >= 250L) {
                    _positionMs.value = newPosition
                }
                val current = _state.value.currentSong
                val newDuration = if (p.duration > 0) p.duration else (current?.durationMs ?: 0L)
                if (_state.value.isPlaying != p.isPlaying || _state.value.durationMs != newDuration) {
                    _state.value = _state.value.copy(isPlaying = p.isPlaying, durationMs = newDuration)
                }
                tickCount++
                if (tickCount % 10 == 0) saveLastPlayed()
                checkSleepTimer()
            }
            handler.postDelayed(this, 500)
        }
    }

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.value = _state.value.copy(isPlaying = isPlaying)
            if (!isPlaying) saveLastPlayed()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val index = player?.currentMediaItemIndex ?: -1
            val queue = _state.value.queue
            val song = queue.getOrNull(index)
            if (song != null) {
                _state.value = _state.value.copy(currentSong = song, queueIndex = index, positionMs = 0L)
                _positionMs.value = 0L
                applyAudioSettings()
                saveLastPlayed()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED && _state.value.repeatMode == RepeatMode.OFF) {
                _state.value.currentSong?.let { library?.clearResumePosition(it.id) }
                _state.value = _state.value.copy(isPlaying = false)
            }
            if (playbackState == Player.STATE_READY) applyAudioSettings()
        }
    }

    fun init(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        library = LibraryStore(context.applicationContext)
        val savedSpeed = library?.getPlaybackSpeed() ?: 1f
        val savedVibe = library?.getVibeEnabled() ?: false
        val savedPreset = library?.getEqualizerPreset() ?: "Balanced"
        _state.value = _state.value.copy(
            playbackSpeed = savedSpeed,
            vibeEnabled = savedVibe,
            equalizerPreset = savedPreset
        )
        // Connect to the service session: the controller connection
        // is what activates the session for the notification and
        // Quick Settings. The service itself creates the player, so
        // nothing here starts a foreground service directly.
        connectIfNeeded()
        handler.post(ticker)
        try {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (intent?.action == android.media.AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                        pause()
                    }
                }
            }
            noisyReceiver = receiver
            context.applicationContext.registerReceiver(
                receiver,
                IntentFilter(android.media.AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            )
        } catch (e: Exception) {
        }
    }

    // ---- Queue ----

    fun playQueue(songs: List<Song>, startIndex: Int, startAtMs: Long = 0L, onPlayed: ((Song) -> Unit)? = null) {
        if (songs.isEmpty()) return
        // If the controller is not connected yet (first tap right
        // after opening the app), keep this request and run it when
        // the connection lands instead of dropping it.
        if (player == null) {
            pendingAction = { playQueue(songs, startIndex, startAtMs, onPlayed) }
            connectIfNeeded()
            return
        }
        val exo = player ?: return
        val safeIndex = startIndex.coerceIn(0, songs.size - 1)
        val items = songs.map { mediaItemFor(it) }
        exo.setMediaItems(items, safeIndex, startAtMs)
        exo.prepare()
        exo.play()
        _state.value = _state.value.copy(
            queue = songs,
            queueIndex = safeIndex,
            currentSong = songs[safeIndex],
            isPlaying = true,
            positionMs = startAtMs
        )
        _positionMs.value = startAtMs
        applyAudioSettings()
        saveLastPlayed()
        onPlayed?.invoke(songs[safeIndex])
    }

    /** Load the saved queue paused, so the mini-player shows the last song on open. */
    fun restoreLastPlayed(allSongs: List<Song>): Boolean {
        if (player == null) {
            pendingAction = { restoreLastPlayed(allSongs) }
            connectIfNeeded()
            return false
        }
        val exo = player ?: return false
        if (_state.value.queue.isNotEmpty()) return false
        val store = library ?: return false
        val lastId = store.getLastSongId()
        if (lastId < 0 || allSongs.isEmpty()) return false
        val queueIds = store.getLastQueueIds()
        val byId = allSongs.associateBy { it.id }
        val queue = queueIds.mapNotNull { byId[it] }
        if (queue.isEmpty()) return false
        val index = queue.indexOfFirst { it.id == lastId }.takeIf { it >= 0 }
            ?: store.getLastQueueIndex().coerceIn(0, queue.size - 1)
        val position = store.getLastPositionMs().coerceAtLeast(0L)
        val song = queue[index]
        val items = queue.map { mediaItemFor(it) }
        exo.setMediaItems(items, index, 0L)
        exo.prepare()
        exo.seekTo(index, position)
        exo.pause()
        _state.value = _state.value.copy(
            queue = queue,
            queueIndex = index,
            currentSong = song,
            isPlaying = false,
            positionMs = position,
            durationMs = song.durationMs
        )
        applyPlaybackParameters()
        applyAudioSettings()
        return true
    }

    fun clearQueue() {
        val exo = player ?: return
        try { exo.clearMediaItems() } catch (e: Exception) { }
        exo.pause()
        _state.value = _state.value.copy(queue = emptyList(), queueIndex = -1, currentSong = null, isPlaying = false)
        _positionMs.value = 0L
    }

    fun playNext(song: Song) {
        val exo = player ?: return
        val queue = _state.value.queue
        if (queue.isEmpty()) {
            playQueue(listOf(song), 0)
            return
        }
        if (queue.any { it.id == song.id }) return
        val insertAt = (_state.value.queueIndex + 1).coerceIn(0, queue.size)
        exo.addMediaItem(insertAt, mediaItemFor(song))
        val newQueue = queue.toMutableList().apply { add(insertAt, song) }
        _state.value = _state.value.copy(queue = newQueue)
        saveLastPlayed()
    }

    fun addToQueue(song: Song) {
        val exo = player ?: return
        val queue = _state.value.queue
        if (queue.isEmpty()) {
            playQueue(listOf(song), 0)
            return
        }
        if (queue.any { it.id == song.id }) return
        exo.addMediaItem(mediaItemFor(song))
        _state.value = _state.value.copy(queue = queue + song)
        saveLastPlayed()
    }

    fun playPause() {
        val exo = player
        if (exo == null) {
            connectIfNeeded()
            return
        }
        if (exo.isPlaying) exo.pause() else exo.play()
        _state.value = _state.value.copy(isPlaying = exo.isPlaying)
        saveLastPlayed()
    }

    fun play() {
        val exo = player
        if (exo == null) {
            pendingAction = { play() }
            connectIfNeeded()
            return
        }
        exo.play()
        _state.value = _state.value.copy(isPlaying = true)
    }

    fun pause() {
        player?.pause()
        _state.value = _state.value.copy(isPlaying = false)
        saveLastPlayed()
    }

    fun next() {
        val exo = player ?: return
        if (exo.hasNextMediaItem()) {
            exo.seekToNextMediaItem()
        } else if (_state.value.queue.isNotEmpty()) {
            playQueue(_state.value.queue, 0)
        }
    }

    fun previous() {
        val exo = player ?: return
        if (exo.currentPosition > 3000) {
            exo.seekTo(0L)
        } else if (exo.hasPreviousMediaItem()) {
            exo.seekToPreviousMediaItem()
        } else {
            exo.seekTo(0L)
        }
    }

    fun seekTo(positionMs: Long) {
        player?.seekTo(positionMs)
        _positionMs.value = positionMs
        _state.value = _state.value.copy(positionMs = positionMs)
    }

    fun seekBy(deltaMs: Long) {
        val exo = player ?: return
        val target = (exo.currentPosition + deltaMs).coerceIn(0L, (exo.duration.takeIf { it > 0 } ?: Long.MAX_VALUE))
        seekTo(target)
    }

    fun toggleShuffle() {
        val exo = player ?: return
        val enabled = !_state.value.shuffleEnabled
        exo.shuffleModeEnabled = enabled
        _state.value = _state.value.copy(shuffleEnabled = enabled)
    }

    fun cycleRepeatMode() {
        val exo = player ?: return
        val next = when (_state.value.repeatMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        exo.repeatMode = when (next) {
            RepeatMode.OFF -> Player.REPEAT_MODE_OFF
            RepeatMode.ALL -> Player.REPEAT_MODE_ALL
            RepeatMode.ONE -> Player.REPEAT_MODE_ONE
        }
        _state.value = _state.value.copy(repeatMode = next)
    }

    // ---- Speed, pitch and vibe ----

    fun setSpeed(speed: Float) {
        library?.savePlaybackSpeed(speed)
        _state.value = _state.value.copy(playbackSpeed = speed)
        applyPlaybackParameters()
        applyAudioSettings()
    }

    fun setVibeEnabled(enabled: Boolean) {
        library?.saveVibeEnabled(enabled)
        _state.value = _state.value.copy(vibeEnabled = enabled)
        applyPlaybackParameters()
        applyAudioSettings()
    }

    private fun applyPlaybackParameters() {
        val exo = player ?: return
        val speed = _state.value.playbackSpeed
        // In vibe mode pitch follows speed: slower = deeper, faster = brighter.
        // Otherwise pitch stays natural at 1.0 so voices do not sound odd.
        val pitch = if (_state.value.vibeEnabled) speed.coerceIn(0.5f, 2f) else 1f
        try {
            exo.playbackParameters = PlaybackParameters(speed, pitch)
        } catch (e: Exception) {
            try { exo.setPlaybackSpeed(speed) } catch (_: Exception) { }
        }
    }

    // ---- Real audio effects ----
    //
    // The Equalizer / BassBoost / EnvironmentalReverb must attach to
    // the service-owned player's audio session id, which the
    // MediaController does not expose. They therefore live in
    // PlaybackService, and this object only sends the current
    // preset, vibe and speed there. The service remembers them and
    // applies them as soon as its effects exist.

    fun setEqualizerPreset(preset: String) {
        library?.saveEqualizerPreset(preset)
        _state.value = _state.value.copy(equalizerPreset = preset)
        applyAudioSettings()
    }

    private fun applyAudioSettings() {
        try {
            PlaybackService.applyAudioSettings(
                _state.value.equalizerPreset,
                _state.value.vibeEnabled,
                _state.value.playbackSpeed
            )
        } catch (e: Exception) { }
    }

    // ---- Sleep timer (fades out in the last 15 seconds) ----

    fun setSleepTimer(minutes: Int?) {
        if (minutes == null) {
            sleepEndAtMs = null
            player?.volume = 1f
            _state.value = _state.value.copy(sleepTimerSecondsLeft = null)
        } else {
            sleepEndAtMs = System.currentTimeMillis() + minutes * 60L * 1000L
            _state.value = _state.value.copy(sleepTimerSecondsLeft = minutes * 60)
        }
    }

    private fun checkSleepTimer() {
        val end = sleepEndAtMs ?: return
        val left = ((end - System.currentTimeMillis()) / 1000).toInt()
        if (left <= 0) {
            sleepEndAtMs = null
            player?.volume = 1f
            pause()
            _state.value = _state.value.copy(sleepTimerSecondsLeft = null)
        } else {
            if (left <= 15) {
                player?.volume = (left / 15f).coerceIn(0.1f, 1f)
            }
            _state.value = _state.value.copy(sleepTimerSecondsLeft = left)
        }
    }

    // ---- Last-played persistence ----

    private fun saveLastPlayed() {
        val store = library ?: return
        val song = _state.value.currentSong ?: return
        val queue = _state.value.queue
        if (queue.isEmpty()) return
        try {
            store.saveLastPlayed(
                songId = song.id,
                queueIds = queue.map { it.id },
                queueIndex = _state.value.queueIndex.coerceAtLeast(0),
                positionMs = _state.value.positionMs
            )
            // Remember where this song was left, unless it just finished.
            if (_state.value.positionMs > 3000 && _state.value.positionMs < (_state.value.durationMs - 5000).coerceAtLeast(0L)) {
                store.saveResumePosition(song.id, _state.value.positionMs)
            }
        } catch (e: Exception) { }
    }

    fun release() {
        val receiver = noisyReceiver
        noisyReceiver = null
        if (receiver != null) {
            try {
                appContext?.unregisterReceiver(receiver)
            } catch (e: Exception) {
            }
        }
        saveLastPlayed()
        handler.removeCallbacks(ticker)
        player?.removeListener(listener)
        try { controller?.release() } catch (e: Exception) { }
        controller = null
        player = null
        connectStarted = false
        pendingAction = null
    }
}
