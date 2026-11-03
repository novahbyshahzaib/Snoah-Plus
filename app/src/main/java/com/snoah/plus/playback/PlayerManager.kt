package com.snoah.plus.playback

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.audiofx.BassBoost
import android.media.audiofx.EnvironmentalReverb
import android.media.audiofx.Equalizer
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
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
 * Single playback controller used by the whole app. It owns one ExoPlayer
 * instance so the mini-player and the full player always stay in sync.
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

    private var player: ExoPlayer? = null
    private var appContext: Context? = null
    private var library: LibraryStore? = null

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private val handler = Handler(Looper.getMainLooper())
    private var sleepEndAtMs: Long? = null
    private var tickCount = 0

    // Real audio effects, attached to the ExoPlayer audio session.
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var reverb: EnvironmentalReverb? = null
    private var effectsSessionId: Int = -1
    private var noisyReceiver: BroadcastReceiver? = null

    /** MediaItem with title/artist/album/artwork, so Android's media
     *  notification, Quick Settings tile and lock screen can show
     *  what is playing. Without metadata they show nothing. */
    private fun mediaItemFor(song: Song): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(song.title)
            .setArtist(song.artist)
            .setAlbumTitle(song.album)
            .setArtworkUri(song.albumArtUri)
            .setIsPlayable(true)
            .build()
        return MediaItem.Builder()
            .setUri(song.contentUri)
            .setMediaMetadata(metadata)
            .build()
    }

    private fun ensureServiceStarted() {
        val ctx = appContext ?: return
        try {
            val intent = Intent(ctx, PlaybackService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ctx.startForegroundService(intent)
            } else {
                ctx.startService(intent)
            }
        } catch (e: Exception) {
        }
    }

    private val ticker = object : Runnable {
        override fun run() {
            val p = player
            if (p != null) {
                val current = _state.value.currentSong
                _state.value = _state.value.copy(
                    isPlaying = p.isPlaying,
                    positionMs = p.currentPosition,
                    durationMs = if (p.duration > 0) p.duration else (current?.durationMs ?: 0L)
                )
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
                ensureEffects()
                saveLastPlayed()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED && _state.value.repeatMode == RepeatMode.OFF) {
                _state.value.currentSong?.let { library?.clearResumePosition(it.id) }
                _state.value = _state.value.copy(isPlaying = false)
            }
            if (playbackState == Player.STATE_READY) ensureEffects()
        }
    }

    fun init(context: Context) {
        if (player != null) return
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
        val exo = ExoPlayer.Builder(context.applicationContext).build()
        exo.addListener(listener)
        player = exo
        applyPlaybackParameters()
        handler.post(ticker)
        ensureServiceStarted()
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

    fun getPlayer(): ExoPlayer? = player

    // ---- Queue ----

    fun playQueue(songs: List<Song>, startIndex: Int, startAtMs: Long = 0L, onPlayed: ((Song) -> Unit)? = null) {
        val exo = player ?: return
        if (songs.isEmpty()) return
        val safeIndex = startIndex.coerceIn(0, songs.size - 1)
        ensureServiceStarted()
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
        ensureEffects()
        saveLastPlayed()
        onPlayed?.invoke(songs[safeIndex])
    }

    /** Load the saved queue paused, so the mini-player shows the last song on open. */
    fun restoreLastPlayed(allSongs: List<Song>): Boolean {
        val exo = player ?: return false
        if (_state.value.queue.isNotEmpty()) return false
        val store = library ?: return false
        val lastId = store.getLastSongId()
        if (lastId < 0 || allSongs.isEmpty()) return false
        val queueIds = store.getLastQueueIds()
        val byId = allSongs.associateBy { it.id }
        val queue = queueIds.mapNotNull { byId[it] }
        if (queue.isEmpty()) return false
        ensureServiceStarted()
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
        ensureEffects()
        return true
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
        val exo = player ?: return
        if (exo.isPlaying) exo.pause() else exo.play()
        _state.value = _state.value.copy(isPlaying = exo.isPlaying)
        saveLastPlayed()
    }

    fun play() {
        ensureServiceStarted()
        player?.play()
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
        applyReverb()
    }

    fun setVibeEnabled(enabled: Boolean) {
        library?.saveVibeEnabled(enabled)
        _state.value = _state.value.copy(vibeEnabled = enabled)
        applyPlaybackParameters()
        applyReverb()
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

    fun setEqualizerPreset(preset: String) {
        library?.saveEqualizerPreset(preset)
        _state.value = _state.value.copy(equalizerPreset = preset)
        applyEqualizer()
    }

    private fun ensureEffects() {
        val exo = player ?: return
        val sessionId = try { exo.audioSessionId } catch (e: Exception) { 0 }
        if (sessionId == 0 || sessionId == effectsSessionId) return
        releaseEffects()
        effectsSessionId = sessionId
        try {
            equalizer = Equalizer(0, sessionId).apply { enabled = true }
        } catch (e: Exception) { equalizer = null }
        try {
            bassBoost = BassBoost(0, sessionId).apply { enabled = true }
        } catch (e: Exception) { bassBoost = null }
        try {
            reverb = EnvironmentalReverb(0, sessionId).apply { enabled = false }
        } catch (e: Exception) { reverb = null }
        applyEqualizer()
        applyReverb()
    }

    private fun presetLevels(preset: String): List<Float> = when (preset) {
        "Bass Boost" -> listOf(0.8f, 0.5f, 0f, -0.2f, -0.3f)
        "Treble Boost" -> listOf(-0.3f, -0.2f, 0f, 0.5f, 0.8f)
        "Vocal" -> listOf(-0.4f, 0.2f, 0.6f, 0.4f, -0.2f)
        "Rock" -> listOf(0.6f, 0.3f, -0.2f, 0.3f, 0.6f)
        "Pop" -> listOf(0.2f, 0.4f, 0.3f, 0.2f, -0.1f)
        "Classical" -> listOf(0.4f, 0.2f, 0f, 0.2f, 0.4f)
        else -> listOf(0f, 0f, 0f, 0f, 0f)
    }

    private fun applyEqualizer() {
        val eq = equalizer ?: return
        try {
            val bands = eq.numberOfBands.toInt()
            if (bands <= 0) return
            val levels = presetLevels(_state.value.equalizerPreset)
            val range = eq.bandLevelRange
            val max = (range?.get(1)?.toInt() ?: 1500).coerceAtLeast(1)
            for (i in 0 until bands) {
                val level = levels[(i * levels.size) / bands]
                eq.setBandLevel(i.toShort(), (level * max).toInt().toShort())
            }
            val boost = bassBoost
            if (boost != null) {
                val strength = when (_state.value.equalizerPreset) {
                    "Bass Boost" -> 700
                    "Rock" -> 400
                    "Pop" -> 200
                    else -> 0
                }
                boost.setStrength(strength.toShort())
                boost.enabled = strength > 0
            }
        } catch (e: Exception) { }
    }

    private fun applyReverb() {
        val rev = reverb ?: return
        try {
            val speed = _state.value.playbackSpeed
            val active = _state.value.vibeEnabled && speed != 1f
            rev.enabled = active
            if (!active) return
            // Slower = longer, deeper reverb. Faster = shorter, brighter reverb.
            val decayMs = when {
                speed < 0.75f -> 3200
                speed < 1f -> 2600
                speed <= 1.25f -> 1400
                else -> 900
            }
            rev.decayTime = decayMs
            rev.decayHFRatio = if (speed < 1f) 400 else 800
            rev.density = 900
            rev.diffusion = 900
            rev.reflectionsLevel = -900
            rev.reverbLevel = if (speed < 1f) -700 else -1100
            rev.roomLevel = -1000
            rev.roomHFLevel = if (speed < 1f) -2000 else -800
        } catch (e: Exception) { }
    }

    private fun releaseEffects() {
        try { equalizer?.release() } catch (e: Exception) { }
        try { bassBoost?.release() } catch (e: Exception) { }
        try { reverb?.release() } catch (e: Exception) { }
        equalizer = null
        bassBoost = null
        reverb = null
        effectsSessionId = -1
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
        releaseEffects()
        player?.removeListener(listener)
        player?.release()
        player = null
    }
}
