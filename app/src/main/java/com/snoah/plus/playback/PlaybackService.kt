package com.snoah.plus.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.snoah.plus.MainActivity

/**
 * Foreground media session service. It exposes the shared ExoPlayer so
 * Android notifications, Quick Settings, Bluetooth controls and the
 * lock screen can show and control Snoah Plus playback.
 *
 * The service must be exported: the system media UI binds to it from
 * outside the app, and with exported=false Android could not see the
 * session at all, so Quick Settings kept showing "Not playing".
 */
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        createSessionIfNeeded()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createSessionIfNeeded()
        // Let MediaSessionService handle controller intents, then
        // keep the service alive while music may be playing.
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    private fun createSessionIfNeeded() {
        if (mediaSession != null) return
        PlayerManager.init(this)
        val player = PlayerManager.getPlayer() ?: return
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

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        createSessionIfNeeded()
        return mediaSession
    }

    override fun onDestroy() {
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }
}
