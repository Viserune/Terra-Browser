package app.terra.browser

import android.animation.ValueAnimator
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityManager
import android.view.animation.DecelerateInterpolator
import android.webkit.WebView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.abs

/** Tracks a one-finger pull only after Chromium reports overscroll at the root document. */
internal class PullRefresh {
    var distance by mutableFloatStateOf(0f)
        private set
    var armed by mutableStateOf(false)
        private set
    var loading by mutableStateOf(false)
        private set
    private var web: WebView? = null
    private var tracking = false
    private var gesture = 0L
    private var permitsRefresh = false
    private var edge = false
    private var ownsTouch = false
    private var x = 0f
    private var y = 0f
    private var animator: ValueAnimator? = null
    private val finish = Runnable { settle() }

    fun bind(next: WebView?) {
        if (next === web) return
        reset()
        web = next
    }

    fun reset() {
        web?.removeCallbacks(finish)
        animator?.cancel()
        tracking = false
        gesture++
        permitsRefresh = false
        ownsTouch = false
        edge = false
        armed = false
        loading = false
        move(0f)
    }

    fun reachedTop(view: WebView) {
        if (tracking && view === web) edge = true
    }

    fun documentStarted(view: WebView) {
        if (view === web && !loading) reset()
    }

    private fun checkScrollOwner(view: WebView, event: MotionEvent) {
        if (view.width <= 0) return
        val token = gesture
        val xFraction = (event.x-view.left)/view.width
        val yFraction = (event.y-view.top)/view.width
        // CSS pixels track the visual viewport, including a pinch-zoomed or desktop-mode page.
        // Some WebView versions also report overScrollBy for an inner scroll container.
        view.evaluateJavascript("""(() => {
            const width = window.visualViewport?.width || innerWidth;
            let node = document.elementFromPoint($xFraction * width, $yFraction * width);
            if (!node) return false;
            while (node.shadowRoot) {
                const inner = node.shadowRoot.elementFromPoint($xFraction * width, $yFraction * width);
                if (!inner || inner === node) break;
                node = inner;
            }
            while (node) {
                const style = getComputedStyle(node);
                if (node.matches('input,textarea,select,iframe,video,canvas') || node.isContentEditable ||
                    style.overscrollBehaviorY === 'none' || style.overscrollBehaviorY === 'contain' ||
                    style.touchAction === 'none' || style.touchAction === 'pan-x') return false;
                if (node.scrollTop > 1 && /(auto|scroll|overlay)/.test(style.overflowY)) return false;
                node = node.parentElement || node.getRootNode().host;
            }
            return true;
        })()""") { result -> if (tracking && gesture == token && view === web) permitsRefresh = result == "true" }
    }

    fun loaded(view: WebView) {
        if (view === web && loading) {
            view.removeCallbacks(finish)
            view.postDelayed(finish,180)
        }
    }

    private fun move(value: Float) {
        distance = value
        web?.translationY = value
    }

    private fun animate(to: Float, millis: Long) {
        animator?.cancel()
        animator = ValueAnimator.ofFloat(distance,to).apply {
            duration = millis
            interpolator = DecelerateInterpolator(1.7f)
            addUpdateListener { move(it.animatedValue as Float) }
            start()
        }
    }

    private fun settle() {
        loading = false
        armed = false
        animate(0f,220)
    }

    fun touch(event: MotionEvent, cancelChild: () -> Unit): Boolean {
        val view = web ?: return false
        val density = view.resources.displayMetrics.density
        val slop = ViewConfiguration.get(view.context).scaledTouchSlop
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (loading) return false
                reset()
                tracking = event.getToolType(0) == MotionEvent.TOOL_TYPE_FINGER &&
                    view.progress == 100 && !view.canScrollVertically(-1) &&
                    !view.context.getSystemService(AccessibilityManager::class.java).isTouchExplorationEnabled
                x = event.x
                y = event.y
                if (tracking) checkScrollOwner(view,event)
            }
            MotionEvent.ACTION_MOVE -> if (tracking) {
                val dy = event.y-y
                val dx = abs(event.x-x)
                if (!ownsTouch) {
                    if (dy < -slop || dx > slop && dx >= dy || view.canScrollVertically(-1) ||
                        event.eventTime-event.downTime > ViewConfiguration.getLongPressTimeout() && !edge) {
                        tracking = false
                        return false
                    }
                    if (!edge || !permitsRefresh || dy < slop || dy < dx*1.35f) return false
                    cancelChild()
                    ownsTouch = true
                }
                val travel = (dy-slop).coerceAtLeast(0f)
                val limit = 136f*density
                move(limit*travel/(limit+travel))
                val ready = travel >= 108f*density
                if (ready && !armed) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                armed = ready
            }
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP -> {
                val wasOurs = ownsTouch
                if (tracking && wasOurs) {
                    if (armed && event.actionMasked == MotionEvent.ACTION_UP && event.y-y-slop >= 108f*density) {
                        loading = true
                        armed = false
                        animate(44f*density,150)
                        view.reload()
                    } else settle()
                }
                tracking = false
                // Once the renderer has received CANCEL, consume the remaining pointer stream.
                if (event.actionMasked != MotionEvent.ACTION_POINTER_DOWN) ownsTouch = false
                return wasOurs
            }
        }
        return ownsTouch
    }
}
