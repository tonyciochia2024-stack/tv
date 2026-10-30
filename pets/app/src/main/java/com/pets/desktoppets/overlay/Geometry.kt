package com.pets.desktoppets.overlay

import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowInsets
import android.view.WindowManager

/** Area of the screen the pet may occupy: everything except status bar, navigation bar and display cutout. */
fun usableBounds(context: Context, wm: WindowManager): Rect {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val m = wm.currentWindowMetrics
        val i = m.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
        val b = m.bounds
        return Rect(b.left + i.left, b.top + i.top, b.right - i.right, b.bottom - i.bottom)
    }
    @Suppress("DEPRECATION")
    val d = wm.defaultDisplay
    val real = DisplayMetrics().also { d.getRealMetrics(it) }
    val app = DisplayMetrics().also { d.getMetrics(it) }
    val res = context.resources
    val sbId = res.getIdentifier("status_bar_height", "dimen", "android")
    val status = if (sbId > 0) res.getDimensionPixelSize(sbId) else 0
    val navBottom = (real.heightPixels - app.heightPixels).coerceAtLeast(0)
    val navRight = (real.widthPixels - app.widthPixels).coerceAtLeast(0)
    return Rect(0, status, real.widthPixels - navRight, real.heightPixels - navBottom)
}

/** Plain-int rectangle so placement maths can be unit-tested without Android classes. */
data class Area(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width get() = right - left
    val height get() = bottom - top
}

fun Rect.toArea() = Area(left, top, right, bottom)

/** Maps the stored fractions to window coordinates, always keeping the whole pet on screen. */
fun fractionToXY(bounds: Area, w: Int, h: Int, fx: Float, fy: Float): Pair<Int, Int> {
    val rx = (bounds.width - w).coerceAtLeast(0)
    val ry = (bounds.height - h).coerceAtLeast(0)
    return (bounds.left + fx.coerceIn(0f, 1f) * rx).toInt() to (bounds.top + fy.coerceIn(0f, 1f) * ry).toInt()
}

fun xyToFraction(bounds: Area, w: Int, h: Int, x: Int, y: Int): Pair<Float, Float> {
    val rx = (bounds.width - w).coerceAtLeast(1)
    val ry = (bounds.height - h).coerceAtLeast(1)
    return ((x - bounds.left).toFloat() / rx).coerceIn(0f, 1f) to ((y - bounds.top).toFloat() / ry).coerceIn(0f, 1f)
}

/** Clamps a dragged position so the pet never leaves the usable area. */
fun clampToArea(bounds: Area, w: Int, h: Int, x: Int, y: Int): Pair<Int, Int> =
    x.coerceIn(bounds.left, (bounds.right - w).coerceAtLeast(bounds.left)) to
        y.coerceIn(bounds.top, (bounds.bottom - h).coerceAtLeast(bounds.top))
