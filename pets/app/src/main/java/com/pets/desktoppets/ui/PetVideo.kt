package com.pets.desktoppets.ui

import android.graphics.Bitmap
import android.view.TextureView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pets.desktoppets.data.CharacterInfo
import com.pets.desktoppets.overlay.PetPlayer

/**
 * In-app preview: the poster, and - while [playing] - the real looping video on top (always silent, so it never
 * doubles the overlay's song). Only composed players exist, so an off-screen or stopped preview costs nothing.
 */
@Composable
fun PetVideo(info: CharacterInfo, playing: Boolean, poster: Bitmap?, modifier: Modifier = Modifier, corner: Dp = 20.dp) {
    Box(modifier.aspectRatio(info.aspect).clip(RoundedCornerShape(corner)).semantics { contentDescription = "${info.name} preview" }) {
        if (poster != null) Image(poster.asImageBitmap(), null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
        if (playing) {
            val ctx = LocalContext.current
            val player = remember(info.id) { PetPlayer(ctx, handleAudioFocus = false).also { it.setVolume(0f) } }
            val owner = LocalLifecycleOwner.current
            DisposableEffect(player, owner) {
                val obs = LifecycleEventObserver { _, e ->
                    if (e == Lifecycle.Event.ON_PAUSE) player.pause() else if (e == Lifecycle.Event.ON_RESUME) player.play()
                }
                owner.lifecycle.addObserver(obs)
                onDispose { owner.lifecycle.removeObserver(obs); player.release() }
            }
            AndroidView(
                modifier = Modifier.matchParentSize(),
                factory = { TextureView(it).apply { isOpaque = false } },
                update = { tv -> player.setVideoView(tv); player.setCharacter(info); player.play() },
            )
        }
    }
}
