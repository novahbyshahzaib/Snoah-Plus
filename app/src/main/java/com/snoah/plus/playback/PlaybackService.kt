package com.snoah.plus.playback

import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * Foreground media session service. It exposes the shared ExoPlayer so
 * Android notifications, Bluetooth controls and the lock screen can
 * control Snoah Plus playback.
 */
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        PlayerManager.init(this)
        val player = PlayerManager.getPlayer()
        if (player != null) {
            mediaSession = MediaSession.Builder(this, player).build()
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }
}
