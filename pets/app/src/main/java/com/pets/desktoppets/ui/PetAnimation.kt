package com.pets.desktoppets.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.nativeCanvas
import com.pets.desktoppets.data.CharacterInfo
import com.pets.desktoppets.data.FramePack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Plays the real (transparent) animation inside the app. Decodes one frame at a time and only while
 * [playing] is true and the composable is in the composition, so previews cost nothing when off-screen.
 * [fallback] is the static thumbnail shown while paused.
 */
@Composable
fun PetAnimation(info: CharacterInfo, playing: Boolean, fallback: Bitmap?, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    var frame by remember(info.id) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(info.id, playing) {
        if (!playing) { frame = null; return@LaunchedEffect }
        val pack = withContext(Dispatchers.IO) { runCatching { FramePack(ctx, info) }.getOrNull() } ?: return@LaunchedEffect
        try {
            var i = 0
            val period = info.frameDurationMs
            while (true) {
                val t0 = System.nanoTime()
                frame = withContext(Dispatchers.Default) { pack.decode(i) } ?: break
                i = (i + 1) % info.frameCount
                val spent = (System.nanoTime() - t0) / 1_000_000
                delay((period - spent).coerceAtLeast(1))
            }
        } finally {
            pack.close()
        }
    }

    val shown = frame ?: fallback
    Canvas(modifier.aspectRatio(info.aspect).semantics { contentDescription = "${info.name} animation" }) {
        val b = shown ?: return@Canvas
        drawIntoCanvas { c ->
            c.nativeCanvas.drawBitmap(b, null, android.graphics.RectF(0f, 0f, size.width, size.height), null)
        }
    }
}
