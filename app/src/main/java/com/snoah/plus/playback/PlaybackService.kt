package com.snoah.plus.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
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
 * The service must be exported: the system media UI binds to it from
 * outside the app, and with exported=false Android could not see the
 * session at all.
 */
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this).build()
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
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
