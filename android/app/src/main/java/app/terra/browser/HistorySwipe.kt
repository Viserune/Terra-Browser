package app.terra.browser

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.LruCache
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import android.webkit.WebView
import kotlin.math.abs
import kotlin.math.roundToInt

/** History images belong to the open viewport and are never persisted. */
internal class HistorySwipe(private val host: TabViewport, private val redraw: () -> Unit) {
    private data class Entry(val web: WebView, val index: Int, val url: String)
    private data class Snapshot(val bitmap: Bitmap, val width: Int, val height: Int)
    private val images = object : LruCache<Entry, Snapshot>(6 * 1024 * 1024) {
        override fun sizeOf(key: Entry, value: Snapshot) = value.bitmap.allocationByteCount
    }
    private val backgroundColors = LruCache<Entry, Int>(48)
    private val density get() = host.resources.displayMetrics.density
    private val slop = ViewConfiguration.get(host.context).scaledTouchSlop
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val destination = RectF()
    private var startX = 0f
    private var startY = 0f
    private var direction = 0
    private var velocity: VelocityTracker? = null
    private var source: Entry? = null
    private var target: Entry? = null
    private var targetImage: Snapshot? = null
    private var outgoingImage: Snapshot? = null
    private var fraction = 0f
    private var animator: ValueAnimator? = null
    private var navigating = false
    private var destinationDrawn = false
    private var destinationVisited = false
    private val endTransition = Runnable { reset() }

    fun remember(web: WebView): Bitmap? {
        if (web.width == 0 || web.height == 0 || web.visibility != WebView.VISIBLE || !web.isAttachedToWindow) return null
        val history = web.copyBackForwardList()
        val url = history.currentItem?.url ?: return null
        if (web.url != url) return null
        val scale = minOf(1f, 800f / maxOf(web.width, web.height))
        val image = Bitmap.createBitmap((web.width * scale).roundToInt().coerceAtLeast(1),
            (web.height * scale).roundToInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        image.eraseColor(Color.WHITE)
        image.setHasAlpha(false)
        Canvas(image).apply {
            scale(scale, scale)
            translate(-web.scrollX.toFloat(), -web.scrollY.toFloat())
            web.draw(this)
        }
        val entry = Entry(web, history.currentIndex, url)
        images.put(entry, Snapshot(image, web.width, web.height))
        backgroundColors.put(entry, image.getPixel(0, image.height / 2))
        web.postInvalidateOnAnimation()
        return image
    }

    fun rememberWhenDrawn(web: WebView, url: String) {
        if (!web.isAttachedToWindow || web.visibility != WebView.VISIBLE) return
        val index = web.copyBackForwardList().currentIndex
        web.postVisualStateCallback(0, object : WebView.VisualStateCallback() {
            override fun onComplete(requestId: Long) {
                if (web.parent === host && web.url == url && web.copyBackForwardList().currentIndex == index) {
                    remember(web)
                }
            }
        })
    }

    fun visualCommit(web: WebView) {
        val entry = target ?: return
        if (!navigating || entry.web !== web || web.copyBackForwardList().currentIndex != entry.index) return
        val committedUrl = web.url
        web.postVisualStateCallback(0, object : WebView.VisualStateCallback() {
            override fun onComplete(requestId: Long) {
                if (target !== entry || web.url != committedUrl || web.copyBackForwardList().currentIndex != entry.index) return
                destinationDrawn = true
                if (animator == null) reset()
            }
        })
    }

    fun historyChanged(web: WebView, url: String) {
        val from = source ?: return
        val to = target ?: return
        if (from.web !== web) return
        val index = web.copyBackForwardList().currentIndex
        if (navigating && index == to.index) destinationVisited = true
        else if (destinationVisited || index != from.index || url != from.url) reset()
    }

    fun reset() {
        host.removeCallbacks(endTransition)
        val previous = animator
        animator = null
        previous?.cancel()
        direction = 0
        velocity?.recycle()
        velocity = null
        val visible = source != null
        source = null
        target = null
        targetImage = null
        outgoingImage = null
        fraction = 0f
        navigating = false
        destinationDrawn = false
        destinationVisited = false
        if (visible) redraw()
    }

    fun forget(web: WebView) {
        images.snapshot().keys.filter { it.web === web }.forEach { images.remove(it) }
        backgroundColors.snapshot().keys.filter { it.web === web }.forEach { backgroundColors.remove(it) }
    }

    fun touch(web: WebView, event: MotionEvent, cancelChild: () -> Unit): Boolean {
        if (animator != null) return true
        if (navigating) {
            if (event.actionMasked != MotionEvent.ACTION_DOWN) return true
            reset()
        }
        val dx = event.x - startX
        val dy = event.y - startY
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                reset()
                startX = event.x
                startY = event.y
                val edge = 34 * density
                direction = when {
                    startX < edge && web.canGoBack() -> -1
                    startX > host.width-edge && web.canGoForward() -> 1
                    else -> 0
                }
                if (direction != 0) velocity = VelocityTracker.obtain().also { it.addMovement(event) }
            }
            MotionEvent.ACTION_MOVE -> {
                if (direction == 0) return false
                velocity?.addMovement(event)
                if (source == null) {
                    if (abs(dy) > slop && abs(dy) >= abs(dx) || -direction * dx < -slop) {
                        reset()
                        return false
                    }
                    if (-direction * dx < slop || abs(dx) < 1.45f * abs(dy)) return false
                    if (web.canScrollHorizontally(direction)) { reset(); return false }
                    val history = web.copyBackForwardList()
                    val index = history.currentIndex
                    if (index+direction !in 0 until history.size) { reset(); return false }
                    val currentUrl = history.currentItem?.url ?: return false
                    source = Entry(web, index, currentUrl)
                    val next = Entry(web, index+direction, history.getItemAtIndex(index+direction).url)
                    target = next
                    targetImage = images.get(next)
                    cancelChild()
                }
                fraction = (-direction * dx / host.width).coerceIn(0f,1f)
                redraw()
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (source != null) {
                    velocity?.apply { addMovement(event); computeCurrentVelocity(1000) }
                    val speed = -direction * (velocity?.xVelocity ?: 0f)
                    val fling = 780 * density
                    settle(speed > -fling && (fraction >= .34f || fraction * host.width > 24*density && speed > fling))
                    return true
                }
                reset()
            }
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_CANCEL -> {
                if (source != null) { settle(false); return true }
                reset()
            }
        }
        return source != null
    }

    private fun settle(commit: Boolean) {
        val from = source ?: return
        val to = target ?: return
        val history = from.web.copyBackForwardList()
        if (history.currentIndex != from.index || history.currentItem?.url != from.url) { reset(); return }
        val follow = commit && to.index in 0 until history.size && history.getItemAtIndex(to.index).url == to.url
        val animation = ValueAnimator.ofFloat(fraction, if (follow) 1f else 0f)
        animator = animation
        animation.duration = (140 + 90 * if (follow) 1-fraction else fraction).toLong()
        animation.interpolator = DecelerateInterpolator()
        animation.addUpdateListener { fraction = it.animatedValue as Float; redraw() }
        animation.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(ended: Animator) {
                if (animator !== ended) return
                animator = null
                if (navigating && !destinationDrawn) host.postDelayed(endTransition, 3000)
                else reset()
            }
        })
        if (follow) {
            remember(from.web)
            outgoingImage = images.get(from)
            navigating = true
            from.web.goBackOrForward(to.index - from.index)
        }
        animation.start()
    }

    fun draw(canvas: Canvas): Boolean {
        val current = source ?: return false
        val next = target ?: return false
        val w = host.width.toFloat()
        fun layer(entry: Entry, offset: Float, shade: Float, left: Float, right: Float) {
            if (right <= left) return
            val box = destination
            box.set(offset, current.web.top.toFloat(), offset+w, current.web.bottom.toFloat())
            canvas.save()
            canvas.clipRect(left,box.top,right,box.bottom)
            if (entry === current && !navigating) {
                canvas.save()
                canvas.translate(offset, 0f)
                host.drawHistoryPage(canvas, entry.web)
                canvas.restore()
            } else {
                val snapshot = if (entry === current) outgoingImage else targetImage
                canvas.drawColor(backgroundColors.get(entry) ?: Color.WHITE)
                if (snapshot != null && snapshot.width == current.web.width && abs(snapshot.height - box.height()) < 100*density) {
                    paint.color = Color.WHITE
                    box.bottom = box.top + snapshot.height
                    canvas.drawBitmap(snapshot.bitmap, null, box, paint)
                }
            }
            if (shade > 0) canvas.drawColor(Color.argb((shade*42).toInt(),0,0,0))
            canvas.restore()
        }
        // Only draw the exposed part of each sheet, including during glass sampling.
        val boundary = w * if (direction < 0) fraction else 1-fraction
        if (direction < 0) {
            layer(next, -.23f*w*(1-fraction),1-fraction,0f,boundary)
            layer(current,boundary,0f,boundary,w)
        } else {
            layer(current,-.23f*w*fraction,fraction,0f,boundary)
            layer(next,boundary,0f,boundary,w)
        }
        return true
    }
}

