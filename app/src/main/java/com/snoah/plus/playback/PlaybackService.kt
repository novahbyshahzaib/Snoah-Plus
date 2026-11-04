package com.snoah.plus.playback

import android.app.PendingIntent
import android.content.Intent
import android.media.audiofx.BassBoost
import android.media.audiofx.EnvironmentalReverb
import android.media.audiofx.Equalizer
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.snoah.plus.MainActivity

/**
 * Foreground media session service.
 *
 * This service OWNS the ExoPlayer and the MediaSession, and the app's
 * UI talks to that player only through a MediaController connected to
 * this session. That is the arrangement Android needs to show the
 * media notification and the Quick Settings tile: a session with a
 * connected controller becomes active, and the system media UI can
 * then see and control playback. Driving a player directly from the
 * UI, with no controller ever connecting, left Quick Settings stuck
 * on "Not playing" even while a song was playing, so the service now
 * creates the player itself instead of borrowing the UI's player.
 *
 * The real audio effects also live here, because they must attach to
 * this player's audio session id, which only the service-owned
 * ExoPlayer exposes. PlayerManager sends the chosen preset, vibe and
 * speed here through applyAudioSettings(); the settings are kept
 * until the player is ready, then applied to the effects.
 *
 * The service must be exported: the system media UI binds to it from
 * outside the app, and with exported=false Android could not see the
 * session at all.
 */
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) ensureEffects()
        }
    }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this).build()
        player.addListener(playerListener)
        val sessionIntent = Intent(this, MainActivity::class.java)
        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            sessionIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        mediaSession = MediaSession.Builder(this, player)
            .setId("SnoahPlusSession")
            .setSessionActivity(sessionActivity)
            .build()
        ensureEffects()
        // The floating dynamic bar lives as long as the media
        // session does: it shows over other apps while music plays.
        DynamicBarManager.start(applicationContext)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        mediaSession?.player?.removeListener(playerListener)
        releaseEffects()
        DynamicBarManager.stop()
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }

    private fun ensureEffects() {
        val player = mediaSession?.player as? ExoPlayer ?: return
        val sessionId = try { player.audioSessionId } catch (e: Exception) { 0 }
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
        applyAudioSettings(pendingPreset, pendingVibe, pendingSpeed)
    }

    companion object {
        private var equalizer: Equalizer? = null
        private var bassBoost: BassBoost? = null
        private var reverb: EnvironmentalReverb? = null
        private var effectsSessionId: Int = -1
        private var pendingPreset: String = "Balanced"
        private var pendingVibe: Boolean = false
        private var pendingSpeed: Float = 1f

        /** Called by PlayerManager whenever the EQ preset, vibe or
         *  speed changes. Settings are remembered and applied as
         *  soon as the service's effects exist. */
        fun applyAudioSettings(preset: String, vibeEnabled: Boolean, speed: Float) {
            pendingPreset = preset
            pendingVibe = vibeEnabled
            pendingSpeed = speed
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
                val levels = presetLevels(pendingPreset)
                val range = eq.bandLevelRange
                val max = (range?.get(1)?.toInt() ?: 1500).coerceAtLeast(1)
                for (i in 0 until bands) {
                    val level = levels[(i * levels.size) / bands]
                    eq.setBandLevel(i.toShort(), (level * max).toInt().toShort())
                }
                val boost = bassBoost
                if (boost != null) {
                    val strength = when (pendingPreset) {
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
                val speed = pendingSpeed
                val active = pendingVibe && speed != 1f
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
    }
}
