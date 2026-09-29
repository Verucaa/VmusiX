package com.zaaam.vmusix.playback

import android.content.Intent
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.zaaam.vmusix.R

/**
 * Media3 MediaSessionService — satu engine untuk lokal + YouTube.
 *
 * `setMediaNotificationProvider` itu WAJIB, bukan opsional: Media3 tidak
 * membuat notifikasi media secara otomatis. Tanpa provider, foreground
 * service naik tanpa notifikasi — gejalanya persis "lagu tidak berbunyi"
 * padahal tidak ada apa pun yang error, dan di Android 13+ start-nya bisa
 * ditolak `ForegroundServiceStartNotAllowedException`.
 */
class VmusiXSessionService : MediaSessionService() {
    private var player: ExoPlayer? = null
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val p = ExoPlayer.Builder(this).build()
        player = p
        session = MediaSession.Builder(this, p).build()
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelId(CHANNEL_ID)
                .setChannelName(R.string.playback_channel)
                .build(),
        )
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    /**
     * Swipe-away dari Recent: matikan player kalau memang tidak lagi memutar.
     * Tanpa ini service foreground + notifnya nyangkut di panel selamanya.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        session?.release()
        player?.release()
        session = null
        player = null
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "vmusix_playback"

        /**
         * Satu-satunya tempat MediaItem dibentuk, jadi metadata lokal/YouTube
         * selalu punya bentuk yang sama untuk UI dan notifikasi.
         */
        fun buildMediaItem(
            key: String,
            title: String,
            artist: String,
            artworkUri: String?,
            streamUrl: String,
        ): MediaItem {
            val meta = MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setArtworkUri(artworkUri?.let { Uri.parse(it) })
                .build()
            return MediaItem.Builder()
                .setMediaId(key)
                .setUri(streamUrl)
                .setMediaMetadata(meta)
                .build()
        }
    }
}
