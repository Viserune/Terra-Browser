package app.terra.browser

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.util.LruCache
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import android.webkit.WebView
import android.widget.FrameLayout
import kotlin.math.abs
import kotlin.math.roundToInt

internal data class ReadingPosition(val url: String, val x: Float, val y: Float)
internal class BrowserWebView(context: Context) : WebView(context) {
    var pendingPosition: ReadingPosition? = null
    var overviewPosition: ReadingPosition? = null

    fun readingPosition(url: String): ReadingPosition {
        val density = resources.displayMetrics.density
        return (overviewPosition ?: pendingPosition)?.takeIf { it.url == url }
            ?: ReadingPosition(url, scrollX / density, scrollY / density)
    }

    fun resumeFromOverview() {
        val position = overviewPosition ?: return
        overviewPosition = null
        if (url != position.url) return
        pendingPosition = position
        restorePosition(position.url, finished=true) {}
    }

    fun restorePosition(url: String, finished: Boolean, onReady: () -> Unit) {
        val saved = pendingPosition
        if (saved == null || saved.url != url) { onReady(); return }
        postVisualStateCallback(0, object : WebView.VisualStateCallback() {
            override fun onComplete(requestId: Long) {
                if (this@BrowserWebView.url != url || pendingPosition !== saved) return
                val density = resources.displayMetrics.density
                val y = (saved.y * density).roundToInt()
                val maxY = (computeVerticalScrollRange() - height).coerceAtLeast(0)
                // A committed document can still be growing. Page finish will
                // retry and clamp if the new document really is shorter.
                if (!finished && maxY < y) return
                pendingPosition = null
                scrollTo((saved.x * density).roundToInt(), y.coerceIn(0, maxY))
                onReady()
            }
        })
    }

    override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
        if (event.actionMasked == android.view.MotionEvent.ACTION_DOWN) {
            pendingPosition = null
            overviewPosition = null
        }
        return super.onTouchEvent(event)
    }

    override fun overScrollBy(deltaX: Int, deltaY: Int, scrollX: Int, scrollY: Int,
        scrollRangeX: Int, scrollRangeY: Int, maxOverScrollX: Int, maxOverScrollY: Int, isTouchEvent: Boolean): Boolean {
        if (deltaY < 0 && scrollY <= 0) (parent as? TabViewport)?.pullRefresh?.reachedTop(this)
        return super.overScrollBy(deltaX,deltaY,scrollX,scrollY,scrollRangeX,scrollRangeY,maxOverScrollX,maxOverScrollY,isTouchEvent)
    }

    fun remainingScroll(viewportHeight: Int): Int =
        (computeVerticalScrollRange() - scrollY - viewportHeight).coerceAtLeast(0)
}

/** Moves the actual web surfaces while the address bar is dragged. */
internal class TabViewport(context: Context, private val glass: WebGlassCapture,
    internal val pullRefresh: PullRefresh? = null) : FrameLayout(context) {
    internal val readingToolbar = ReadingToolbarMotion(context)
    internal val historySwipe = HistorySwipe(this) { invalidate(); refreshGlass() }
    private var backEdge = false
    private var forwardEdge = false

    fun historyEdges(back: Boolean, forward: Boolean) {
        if (back == backEdge && forward == forwardEdge) return
        backEdge = back
        forwardEdge = forward
        updateEdgeBounds()
    }

    private fun updateEdgeBounds() {
        val density = resources.displayMetrics.density
        val side = (34 * density).toInt().coerceAtMost(width)
        val half = (95 * density).toInt().coerceAtMost(height / 2)
        systemGestureExclusionRects = buildList {
            if (backEdge) add(Rect(0,height/2-half,side,height/2+half))
            if (forwardEdge) add(Rect(width-side,height/2-half,width,height/2+half))
        }
    }

    override fun dispatchTouchEvent(event: android.view.MotionEvent): Boolean {
        if (event.actionMasked == android.view.MotionEvent.ACTION_DOWN) clearLinkSource()
        readingToolbar.touch(event)
        val web = front
        if (web != null && outgoing == null) {
            val cancelChild = {
                val cancel = android.view.MotionEvent.obtain(event)
                cancel.action = android.view.MotionEvent.ACTION_CANCEL
                super.dispatchTouchEvent(cancel)
                cancel.recycle()
            }
            if (historySwipe.touch(web,event,cancelChild)) return true
            if (pullRefresh?.touch(event,cancelChild) == true) return true
        }
        return super.dispatchTouchEvent(event)
    }

    var topClearance = 0
        set(value) {
            if (value == field) return
            field = value
            updateInsets()
            refreshGlass()
        }

    var bottomClearance = 0
        set(value) {
            if (value == field) return
            field = value
            updateInsets()
            refreshGlass()
        }

    var fullGlassScene = false
        set(value) {
            if (field == value) return
            field = value
            refreshGlass()
        }

    private fun updateInsets() {
        val page = front as? BrowserWebView ?: return
        if (height <= 0 || page.height <= 0) return
        val top = (topClearance - page.scrollY).coerceIn(0,topClearance)
        val remaining = page.remainingScroll(height-top)
        val bottom = (bottomClearance - remaining).coerceIn(0,bottomClearance)
        if (paddingTop == top && paddingBottom == bottom) return
        val gained = bottom - paddingBottom
        val keepBottom = gained > 0 && remaining == 0 && page.scrollY > 0
        setPadding(0,top,0,bottom)
        if (keepBottom) page.post {
            if (front === page) page.scrollBy(0,gained)
        }
        refreshGlass()
    }

    private var scheduled = false
    private var sampling = false
    private var sampleMillis = 0.0
    private var frameMillis = 16.7
    private val sample = Runnable {
        scheduled = false
        if (isAttachedToWindow && width > 0 && height > 0 && front != null) {
            sampling = true
            val started = System.nanoTime()
            try {
                updateInsets()
                glass.sample(this)
            } finally {
                val elapsed = (System.nanoTime()-started)/1_000_000.0
                sampleMillis = if (sampleMillis==0.0) elapsed else sampleMillis*.75+elapsed*.25
                sampling = false
            }
        }
    }

    // Post from the frame callback so the real hardware traversal gets its
    // display list before any software readback for the glass.
    private val afterDisplayFrame = Runnable { post(sample) }

    private fun refreshGlass() {
        if (scheduled || sampling || !isAttachedToWindow) return
        scheduled = true
        // A small, cheap strip can track the very next display frame. Allow a
        // frame between captures when copying a full scene or a costly WebView.
        if (!fullGlassScene && sampleMillis < frameMillis/4) postOnAnimation(afterDisplayFrame)
        else postOnAnimationDelayed(afterDisplayFrame,frameMillis.toLong().coerceAtLeast(1L))
    }

    override fun onDescendantInvalidated(child: View, target: View) {
        super.onDescendantInvalidated(child, target)
        if (child.visibility == View.VISIBLE) refreshGlass()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w,h,oldw,oldh)
        updateEdgeBounds()
        refreshGlass()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val hz = display?.refreshRate ?: 60f
        if (hz > 0) frameMillis = 1000.0/hz
        refreshGlass()
    }
    private var front: WebView? = null
    private var neighbor: WebView? = null
    private data class SlideImage(val bitmap: Bitmap, val url: String?, val y: Int, val width: Int, val height: Int)
    private val images = LruCache<WebView, SlideImage>(3)
    private val filter = Paint(Paint.FILTER_BITMAP_FLAG)
    private val destination = Rect()
    private var outgoing: WebView? = null
    private var incoming: WebView? = null
    private var animation: ValueAnimator? = null
    private val settle = PathInterpolator(.2f, .85f, .25f, 1f)
    private var linkSource: Bitmap? = null
    private val linkPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private var linkFade: ValueAnimator? = null
    private var closingLinkMenu: ContextMenu? = null
    private var linkDestinationReady = false
    private val expireLinkSource = Runnable { clearLinkSource() }

    fun beginLinkNavigation(page: WebView, snapshot: Bitmap?, menu: ContextMenu?) {
        clearLinkSource()
        val target = menu?.previewUrl ?: return
        if (front !== page || !isAttachedToWindow || snapshot == null) return
        // A fragment jump can scroll the existing DOM without a new visual commit.
        val current = page.url
        if (current != target && current?.substringBefore('#') == target.substringBefore('#')) return
        closingLinkMenu = menu
        linkSource = snapshot
        linkPaint.alpha = 255
        invalidate()
        postDelayed(expireLinkSource, 2500)
    }

    fun revealLinkDestination(page: WebView) {
        if (front !== page || linkSource == null) return
        linkDestinationReady = true
        fadeLinkWhenReady()
    }

    fun linkCardClosed(menu: ContextMenu) {
        if (closingLinkMenu !== menu) return
        closingLinkMenu = null
        fadeLinkWhenReady()
    }

    fun cancelLinkNavigation(page: WebView) {
        if (front === page) clearLinkSource()
    }

    private fun fadeLinkWhenReady() {
        if (!linkDestinationReady || closingLinkMenu != null || linkSource == null || linkFade != null) return
        val fade = ValueAnimator.ofInt(255, 0)
        linkFade = fade
        fade.duration = 205
        fade.interpolator = PathInterpolator(.42f,0f,.78f,1f)
        fade.addUpdateListener {
            linkPaint.alpha = it.animatedValue as Int
            invalidate()
            refreshGlass()
        }
        fade.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(ended: Animator) {
                if (linkFade === ended) {
                    linkFade = null
                    clearLinkSource()
                }
            }
        })
        fade.start()
    }

    private fun clearLinkSource() {
        if (linkSource == null) return
        linkSource = null
        closingLinkMenu = null
        linkDestinationReady = false
        removeCallbacks(expireLinkSource)
        val fade = linkFade
        linkFade = null
        fade?.cancel()
        front?.postInvalidateOnAnimation()
        invalidate()
        refreshGlass()
    }

    private fun attach(page: WebView) {
        if (page.parent === this) return
        (page.parent as? ViewGroup)?.removeView(page)
        addView(page, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    private fun hide(page: WebView) {
        page.animate().cancel()
        page.visibility = View.INVISIBLE
        page.translationX = 0f
        page.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    }

    private fun trim() {
        for (i in 0 until childCount) {
            if (childCount <= 3) break
            val child = getChildAt(i) ?: break
            if (child.visibility == View.INVISIBLE) { removeView(child); break }
        }
    }

    private fun image(page: WebView): SlideImage? {
        if (page.width <= 0 || page.height <= 0) return null
        images.get(page)?.let {
            if (it.width == page.width && it.height == page.height && it.url == page.url && it.y == page.scrollY) return it
        }
        val scale = minOf(1f,540f/page.width)
        val bitmap = Bitmap.createBitmap((page.width*scale).toInt().coerceAtLeast(1),
            (page.height*scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val copy = Canvas(bitmap)
        copy.scale(scale,scale)
        copy.translate(-page.scrollX.toFloat(),-page.scrollY.toFloat())
        page.draw(copy)
        return SlideImage(bitmap,page.url,page.scrollY,page.width,page.height).also { images.put(page,it) }
    }

    private fun drawNavigation(canvas: Canvas): Boolean {
        if (historySwipe.draw(canvas)) return true
        val first = outgoing ?: return false
        val second = incoming ?: return false
        if (images.get(first) == null || images.get(second) == null) return false
        drawSlide(canvas,first)
        drawSlide(canvas,second)
        return true
    }

    private fun drawLinkSource(canvas: Canvas) {
        val source = linkSource ?: return
        val page = front ?: return
        destination.set(page.left,page.top,page.right,page.bottom)
        canvas.drawBitmap(source,null,destination,linkPaint)
    }

    override fun dispatchDraw(canvas: Canvas) {
        if (drawNavigation(canvas)) return
        super.dispatchDraw(canvas)
        drawLinkSource(canvas)
    }

    internal fun drawGlassDocument(canvas: Canvas) {
        if (drawNavigation(canvas)) return
        val page = front ?: return
        val x = page.left + page.translationX
        val y = page.top.toFloat()
        val checkpoint = canvas.save()
        canvas.clipRect(x,y,x+page.width,y+page.height)
        canvas.translate(x-page.scrollX,y-page.scrollY)
        // Traversing the ViewGroup into a bitmap can consume a WebView's dirty
        // display list. Sample the visible document without traversing siblings.
        val needsRedraw = page.isDirty
        page.draw(canvas)
        if (needsRedraw) page.invalidate()
        canvas.restoreToCount(checkpoint)
        drawLinkSource(canvas)
    }

    private fun drawSlide(canvas: Canvas, page: WebView) {
        val image = images.get(page) ?: return
        val x = page.translationX.toInt()
        destination.set(x,page.top,x+page.width,page.bottom)
        canvas.drawBitmap(image.bitmap,null,destination,filter)
    }

    fun track(page: WebView?, dx: Float) {
        val current = front ?: return
        pullRefresh?.reset()
        if (width <= 0 || page === current) return
        animation?.cancel()
        if (neighbor !== page) neighbor?.let(::hide)
        neighbor = page
        if (page != null) {
            attach(page)
            if (outgoing !== current || incoming !== page) {
                images.remove(current)
                image(current)
            }
            image(page)
            outgoing = current
            incoming = page
            page.visibility = View.VISIBLE
            page.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        } else {
            outgoing = null
            incoming = null
        }
        current.translationX = if (page == null) dx*.15f else dx.coerceIn(-width.toFloat(),width.toFloat())
        page?.translationX = current.translationX + if (dx < 0) width else -width
        invalidate()
        refreshGlass()
    }

    private fun finishSwipe(selected: WebView, other: WebView?) {
        animation?.cancel()
        val start = selected.translationX
        val otherStart = other?.translationX ?: 0f
        val animator = ValueAnimator.ofFloat(0f,1f)
        animation = animator
        animator.duration = 205
        animator.interpolator = settle
        animator.addUpdateListener {
            val t = it.animatedValue as Float
            selected.translationX = start*(1f-t)
            if (other != null) other.translationX = otherStart-start*t
            invalidate()
            refreshGlass()
        }
        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(ended: Animator) {
                if (animation !== ended) return
                animation = null
                other?.let(::hide)
                selected.translationX = 0f
                outgoing = null
                incoming = null
                invalidate()
                trim()
                refreshGlass()
            }
        })
        animator.start()
    }

    fun retract() {
        val current = front ?: return
        val other = neighbor
        neighbor = null
        if (current.translationX != 0f || other != null) finishSwipe(current,other)
    }

    fun display(page: WebView) {
        if (front === page) return
        clearLinkSource()
        historySwipe.reset()
        val old = front
        val follow = neighbor === page && old != null && abs(old.translationX) > 0
        if (!follow) glass.clear()
        if (neighbor !== page) neighbor?.let(::hide)
        neighbor = null
        old?.setOnScrollChangeListener(null)
        front = page
        page.setOnScrollChangeListener { _, _, y, _, previousY ->
            readingToolbar.scroll(y,y-previousY)
            updateInsets()
            refreshGlass()
        }
        updateInsets()
        old?.clearFocus()
        old?.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        attach(page)
        bringChildToFront(page)
        page.visibility = View.VISIBLE
        (page as? BrowserWebView)?.resumeFromOverview()
        page.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
        if (follow) finishSwipe(page,old)
        else {
            animation?.cancel()
            outgoing = null
            incoming = null
            old?.let(::hide)
            page.translationX = 0f
        }
        trim()
        refreshGlass()
    }

    override fun onViewRemoved(child: View) {
        super.onViewRemoved(child)
        if (child is WebView) { images.remove(child); historySwipe.forget(child) }
        if (child === outgoing || child === incoming) {
            outgoing = null
            incoming = null
        }
    }

    fun release() {
        clearLinkSource()
        pullRefresh?.bind(null)
        readingToolbar.bind(null)
        historySwipe.reset()
        animation?.cancel()
        images.evictAll()
        outgoing = null
        incoming = null
        for (i in 0 until childCount) (getChildAt(i) as? WebView)?.let(::hide)
        front?.setOnScrollChangeListener(null)
        removeCallbacks(afterDisplayFrame)
        removeCallbacks(sample)
        scheduled = false
        glass.clear()
        front = null
        neighbor = null
        removeAllViews()
    }
}
