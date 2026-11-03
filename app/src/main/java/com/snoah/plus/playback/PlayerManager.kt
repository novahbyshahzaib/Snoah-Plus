package com.snoah.plus.playback

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
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
    val sleepTimerSecondsLeft: Int? = null
)

/**
 * Single playback controller used by the whole app. It owns one ExoPlayer
 * instance so the mini-player and the full player always stay in sync.
 */
object PlayerManager {

    private var player: ExoPlayer? = null
    private var appContext: Context? = null

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private val handler = Handler(Looper.getMainLooper())
    private var sleepEndAtMs: Long? = null

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
                checkSleepTimer()
            }
            handler.postDelayed(this, 500)
        }
    }

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.value = _state.value.copy(isPlaying = isPlaying)
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val index = player?.currentMediaItemIndex ?: -1
            val queue = _state.value.queue
            val song = queue.getOrNull(index)
            if (song != null) {
                _state.value = _state.value.copy(currentSong = song, queueIndex = index, positionMs = 0L)
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED && _state.value.repeatMode == RepeatMode.OFF) {
                _state.value = _state.value.copy(isPlaying = false)
            }
        }
    }

    fun init(context: Context) {
        if (player != null) return
        appContext = context.applicationContext
        val exo = ExoPlayer.Builder(context.applicationContext).build()
        exo.addListener(listener)
        player = exo
        handler.post(ticker)
    }

    fun getPlayer(): ExoPlayer? = player

    fun playQueue(songs: List<Song>, startIndex: Int, onPlayed: ((Song) -> Unit)? = null) {
        val exo = player ?: return
        if (songs.isEmpty()) return
        val safeIndex = startIndex.coerceIn(0, songs.size - 1)
        val items = songs.map { MediaItem.fromUri(it.contentUri) }
        exo.setMediaItems(items, safeIndex, 0L)
        exo.prepare()
        exo.play()
        _state.value = _state.value.copy(
            queue = songs,
            queueIndex = safeIndex,
            currentSong = songs[safeIndex],
            isPlaying = true
        )
        onPlayed?.invoke(songs[safeIndex])
    }

    fun playPause() {
        val exo = player ?: return
        if (exo.isPlaying) exo.pause() else exo.play()
        _state.value = _state.value.copy(isPlaying = exo.isPlaying)
    }

    fun play() {
        player?.play()
        _state.value = _state.value.copy(isPlaying = true)
    }

    fun pause() {
        player?.pause()
        _state.value = _state.value.copy(isPlaying = false)
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

    fun setSpeed(speed: Float) {
        val exo = player ?: return
        exo.setPlaybackSpeed(speed)
        _state.value = _state.value.copy(playbackSpeed = speed)
    }

    fun setSleepTimer(minutes: Int?) {
        if (minutes == null) {
            sleepEndAtMs = null
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
            pause()
            _state.value = _state.value.copy(sleepTimerSecondsLeft = null)
        } else {
            _state.value = _state.value.copy(sleepTimerSecondsLeft = left)
        }
    }

    fun release() {
        handler.removeCallbacks(ticker)
        player?.removeListener(listener)
        player?.release()
        player = null
    }
}
