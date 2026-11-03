package com.snoah.plus.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.snoah.plus.MainActivity

/**
 * Foreground media session service. It exposes the shared ExoPlayer so
 * Android notifications, Quick Settings, Bluetooth controls and the
 * lock screen can show and control Snoah Plus playback. This service
 * must actually be started (PlayerManager does that when playback
 * starts); creating the MediaSession here alone is not enough.
 */
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        PlayerManager.init(this)
        val player = PlayerManager.getPlayer()
        if (player != null) {
            val sessionIntent = Intent(this, MainActivity::class.java)
            val sessionActivity = PendingIntent.getActivity(
                this,
                0,
                sessionIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            mediaSession = MediaSession.Builder(this, player)
                .setSessionActivity(sessionActivity)
                .build()
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
