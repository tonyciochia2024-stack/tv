package com.pets.desktoppets.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Outline
import android.view.MotionEvent
import android.view.TextureView
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout

/**
 * The floating pet: a rounded video surface that implements dragging and nothing else. A tap that does
 * not travel beyond the touch slop triggers no action at all.
 */
class PetView(context: Context) : FrameLayout(context) {
    interface DragListener {
        fun onDrag(dx: Int, dy: Int)       // total displacement since ACTION_DOWN
        fun onDragStart()
        fun onDragEnd()
    }

    val texture = TextureView(context).apply { isOpaque = true }
    var dragListener: DragListener? = null
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var dragging = false

    init {
        addView(texture, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO   // purely decorative
        isClickable = false
        isFocusable = false
        val radius = 18f * resources.displayMetrics.density
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) = outline.setRoundRect(0, 0, view.width, view.height, radius)
        }
        clipToOutline = true
    }

    override fun onInterceptTouchEvent(ev: MotionEvent) = true   // children never see touches

    @SuppressLint("ClickableViewAccessibility")   // decorative: there is deliberately no click action
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
        return true
    }
}
