package com.pets.desktoppets.overlay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration

/**
 * The floating pet. Draws the current frame and implements dragging - nothing else: a tap that does
 * not travel beyond the touch slop triggers no action at all.
 */
class PetView(context: Context) : View(context) {
    interface DragListener {
        fun onDrag(dx: Int, dy: Int)       // total displacement since ACTION_DOWN
        fun onDragStart()
        fun onDragEnd()
    }

    var dragListener: DragListener? = null
    private var bitmap: Bitmap? = null
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val dst = Rect()
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var dragging = false

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO // purely decorative
        isClickable = false
        isFocusable = false
        setLayerType(LAYER_TYPE_NONE, null)
    }

    fun show(frame: Bitmap) {
        bitmap = frame
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val b = bitmap ?: return
        dst.set(0, 0, width, height)
        canvas.drawBitmap(b, null, dst, paint)
    }

    @android.annotation.SuppressLint("ClickableViewAccessibility")   // decorative: there is deliberately no click action
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downX = e.rawX; downY = e.rawY; dragging = false }
            MotionEvent.ACTION_MOVE -> {
                val dx = (e.rawX - downX).toInt()
                val dy = (e.rawY - downY).toInt()
                if (!dragging && (dx * dx + dy * dy) > slop * slop) { dragging = true; dragListener?.onDragStart() }
                if (dragging) dragListener?.onDrag(dx, dy)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragging) {
                    if (e.actionMasked == MotionEvent.ACTION_UP) dragListener?.onDrag((e.rawX - downX).toInt(), (e.rawY - downY).toInt())
                    dragListener?.onDragEnd()
                }
                dragging = false
            }
        }
        return true // consume everything so nothing underneath is activated by a touch on the pet
    }
}
