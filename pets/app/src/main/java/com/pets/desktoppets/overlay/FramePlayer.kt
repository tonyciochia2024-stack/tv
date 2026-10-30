package com.pets.desktoppets.overlay

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import com.pets.desktoppets.data.CharacterInfo
import com.pets.desktoppets.data.FramePack

/**
 * Drift-free frame scheduler. Frames are decoded on a private thread into two reusable bitmaps
 * (no per-frame allocation) and handed to the main thread for drawing. Nothing runs while stopped.
 */
class FramePlayer(private val context: Context, private val onFrame: (Bitmap) -> Unit) {
    private val thread = HandlerThread("pet-decode").apply { start() }
    private val worker = Handler(thread.looper)
    private val main = Handler(Looper.getMainLooper())

    // Touched only on the worker thread.
    private var pack: FramePack? = null
    private val buffers = arrayOfNulls<Bitmap>(2)
    private var next = 0
    private var index = 0
    private var epoch = 0L
    private var ticks = 0L

    @Volatile private var running = false
    private val tick = Runnable { step() }

    fun setCharacter(info: CharacterInfo?) {
        worker.post {
            worker.removeCallbacks(tick)
            pack?.close(); pack = null
            buffers.fill(null)
            index = 0
            if (info != null) pack = runCatching { FramePack(context, info) }.getOrNull()
            if (running) restartClock()
        }
    }

    fun start() {
        running = true
        worker.post { worker.removeCallbacks(tick); restartClock() }
    }

    fun stop() {
        running = false
        worker.post { worker.removeCallbacks(tick) }
    }

    fun release() {
        running = false
        worker.removeCallbacksAndMessages(null)
        worker.post { pack?.close(); pack = null; buffers.fill(null); thread.quitSafely() }
    }

    private fun restartClock() {
        epoch = SystemClock.uptimeMillis(); ticks = 0
        step()
    }

    private fun step() {
        val p = pack ?: return
        if (!running) return
        val info = p.info
        val bmp = p.decode(index, buffers[next])
        if (bmp != null) {
            buffers[next] = bmp
            next = 1 - next
            main.post { onFrame(bmp) }
        }
        index = (index + 1) % info.frameCount
        ticks++
        var at = epoch + (ticks * 1000f / info.fps).toLong()
        val now = SystemClock.uptimeMillis()
        if (at < now - 2 * info.frameDurationMs) { epoch = now; ticks = 0; at = now + info.frameDurationMs } // fell behind: resync, never burst
        worker.postAtTime(tick, at)
    }
}
