package app.terra.browser

import android.animation.ValueAnimator
import android.content.Context
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityManager
import android.view.animation.DecelerateInterpolator
import kotlin.math.abs

/** Folds browser controls only while the finger is vertically scrolling the live document. */
internal class ReadingToolbarMotion(context: Context) {
    private val density = context.resources.displayMetrics.density
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private val accessibility = context.getSystemService(AccessibilityManager::class.java)
    private var page: Page? = null
    private var active = false
    private var vertical = false
    private var x = 0f
    private var y = 0f
    private var initial = 0f
    private var travel = 0f
    private var animation: ValueAnimator? = null

    fun bind(next: Page?) {
        if (page === next) return
        active = false
        animation?.cancel()
        page?.let { it.toolbarFold = if (it.toolbarFold >= .5f) 1f else 0f }
        page = next
        if (next?.compactTools == true) settle(1f)
    }

    fun resetDocument(current: Page) {
        if (page === current) {
            active = false
            animation?.cancel()
        }
        current.toolbarFold = 0f
    }

    fun expand() = settle(0f)

    private fun settle(target: Float) {
        val current = page ?: return
        animation?.cancel()
        if (current.toolbarFold == target) return
        animation = ValueAnimator.ofFloat(current.toolbarFold,target).apply {
            duration = 180
            interpolator = DecelerateInterpolator()
            addUpdateListener { current.toolbarFold = it.animatedValue as Float }
            start()
        }
    }

    fun touch(event: MotionEvent) {
        val current = page ?: return
        if (current.compactTools) return
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (accessibility.isTouchExplorationEnabled) return
                animation?.cancel()
                x = event.x
                y = event.y
                initial = current.toolbarFold
                travel = 0f
                vertical = false
                active = true
            }
            MotionEvent.ACTION_MOVE -> if (active && !vertical) {
                val dx = abs(event.x-x)
                val dy = abs(event.y-y)
                if (dx > slop && dx > dy) {
                    active = false
                    settle(if (initial >= .5f) 1f else 0f)
                }
                else if (dy > slop && dy > dx*1.3f) vertical = true
            }
            MotionEvent.ACTION_UP -> if (active) {
                active = false
                settle(when {
                    current.toolbarFold <= 0f || current.toolbarFold >= 1f -> current.toolbarFold
                    travel > 12*density -> 1f
                    travel < -12*density -> 0f
                    else -> if (current.toolbarFold >= .5f) 1f else 0f
                })
            }
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> if (active) {
                active = false
                settle(if (initial >= .5f) 1f else 0f)
            }
        }
    }

    fun scroll(position: Int, delta: Int) {
        val current = page ?: return
        if (!active || !vertical || delta == 0) return
        if (travel * delta < 0) travel = 0f
        travel += delta
        current.toolbarFold = if (position <= 0) 0f
            else (current.toolbarFold + delta/(92f*density)).coerceIn(0f,1f)
    }
}
