package com.pets.desktoppets.overlay

import android.content.Context
import android.view.TextureView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.pets.desktoppets.data.CharacterInfo

/**
 * Looping video (+ song) player for one pet. The clips are pre-built as seamless loops (cross-faded
 * end -> start), so plain REPEAT_MODE_ONE is enough. Must be created and used on the main thread.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PetPlayer(context: Context, private val handleAudioFocus: Boolean = true) {
    private val player: ExoPlayer = ExoPlayer.Builder(context.applicationContext)
        .setLoadControl(androidx.media3.exoplayer.DefaultLoadControl.Builder().setBufferDurationsMs(1500, 5000, 150, 300).build())
        .build().apply {
        repeatMode = Player.REPEAT_MODE_ONE
        setAudioAttributes(
            AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
            handleAudioFocus,
        )
        playWhenReady = false
    }
    private var currentId: String? = null

    fun setVideoView(view: TextureView?) {
        if (view != null) player.setVideoTextureView(view) else player.clearVideoTextureView(null)
    }

    fun setCharacter(info: CharacterInfo) {
        if (info.id == currentId) return
        currentId = info.id
        player.setMediaItem(MediaItem.fromUri(info.videoUri))
        player.prepare()
    }

    /** 0f = silent. */
    fun setVolume(v: Float) { player.volume = v.coerceIn(0f, 1f) }

    fun play() { player.playWhenReady = true }
    fun pause() { player.playWhenReady = false }
    fun release() { player.release() }
}
