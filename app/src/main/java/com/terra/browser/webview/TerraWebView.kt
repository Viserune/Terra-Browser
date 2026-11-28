package com.terra.browser.webview

import android.annotation.SuppressLint
import android.content.Context
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.webkit.WebView
import com.terra.browser.model.ContextHitInfo
import com.terra.browser.model.ContextHitType
import kotlin.math.abs
import kotlin.math.roundToInt

data class ReadingPosition(val url: String, val xPx: Int, val yPx: Int)

@SuppressLint("ViewConstructor")
class TerraWebView(context: Context) : WebView(context) {
    var onScrollDelta: ((dy: Int, scrollY: Int) -> Unit)? = null
    var onLongPressHit: ((ContextHitInfo) -> Unit)? = null
    var onTopInteraction: ((topSafeOffsetPx: Float, pullRefreshPx: Float, isDragging: Boolean) -> Unit)? = null
    var onPullRefreshTrigger: (() -> Unit)? = null
    var pendingReadingPosition: ReadingPosition? = null

    fun rememberReadingPosition(targetUrl: String) {
        if (targetUrl.isBlank()) return
        if (scrollX != 0 || scrollY != 0) {
            pendingReadingPosition = ReadingPosition(targetUrl, scrollX, scrollY)
        }
    }

    fun restoreReadingPosition(targetUrl: String, finished: Boolean) {
        val saved = pendingReadingPosition ?: return
        if (saved.url != targetUrl) return
        postVisualStateCallback(0L, object : VisualStateCallback() {
            override fun onComplete(requestId: Long) {
                if (this@TerraWebView.url != targetUrl || pendingReadingPosition !== saved) return
                val maxY = (computeVerticalScrollRange() - height).coerceAtLeast(0)
                if (!finished && maxY < saved.yPx) return
                pendingReadingPosition = null
                scrollTo(saved.xPx.coerceAtLeast(0), saved.yPx.coerceIn(0, maxY))
            }
        })
    }

    private val density = resources.displayMetrics.density
    var refreshThresholdPx: Float = density * 72f
    var maxPullPx: Float = density * 124f
    private val pullDeadzonePx: Float = density * 10f
    private val scrollCollapseMinPx: Int = (4f * density).toInt().coerceAtLeast(1)

    private var initializedTopOffset = false
    var currentTopSafeOffsetPx: Float = 0f
        private set
    private var currentPullPx: Float = 0f
    private var rawPullAccumPx: Float = 0f
    var externalVisualPullPxProvider: (() -> Float)? = null

    fun hasInitializedTopOffset(): Boolean = initializedTopOffset

    fun syncTopOffsetNow() {
        onTopInteraction?.invoke(currentTopSafeOffsetPx, currentPullPx, false)
    }

    var topSafeInsetPx: Float = 0f
        set(value) {
            val old = field
            if (initializedTopOffset && abs(old - value) < 0.5f) return
            field = value
            if (!initializedTopOffset) {
                initializedTopOffset = true
                currentTopSafeOffsetPx = if (scrollY > 0) 0f else value
                onTopInteraction?.invoke(currentTopSafeOffsetPx, currentPullPx, false)
            } else if (scrollY > 0) {
                if (currentTopSafeOffsetPx > 0.5f) {
                    currentTopSafeOffsetPx = 0f
                    onTopInteraction?.invoke(0f, currentPullPx, false)
                }
            } else if (!handlingTopGesture && currentTopSafeOffsetPx >= old - 1f) {
                currentTopSafeOffsetPx = value
                onTopInteraction?.invoke(currentTopSafeOffsetPx, currentPullPx, false)
            }
        }

    private var activePointerId = MotionEvent.INVALID_POINTER_ID
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var downTouchX = 0f
    private var downTouchY = 0f
    private var handlingTopGesture = false
    private var horizontalScrollLocked = false
    private var sentCancelToSuper = false
    private var refreshThresholdCrossed = false

    fun resetTopSafeOffset() {
        currentTopSafeOffsetPx = topSafeInsetPx
        currentPullPx = 0f
        rawPullAccumPx = 0f
        handlingTopGesture = false
        horizontalScrollLocked = false
        refreshThresholdCrossed = false
        activePointerId = MotionEvent.INVALID_POINTER_ID
        onTopInteraction?.invoke(currentTopSafeOffsetPx, 0f, false)
    }

    fun resetTopSafeOffsetIfAtTop() {
        if (scrollY == 0 && !handlingTopGesture) {
            currentTopSafeOffsetPx = topSafeInsetPx
            currentPullPx = 0f
            rawPullAccumPx = 0f
            onTopInteraction?.invoke(currentTopSafeOffsetPx, 0f, false)
        }
    }

    private fun canPageScrollDown(): Boolean {
        return canScrollVertically(1) && computeVerticalScrollRange() > height + scrollCollapseMinPx
    }

    private fun seedPullFromVisual(visualPullPx: Float) {
        val clamped = visualPullPx.coerceIn(0f, maxPullPx * 0.96f)
        if (clamped <= 0.5f) {
            currentPullPx = 0f
            rawPullAccumPx = 0f
            refreshThresholdCrossed = false
            return
        }
        val ratio = (clamped / maxPullPx).coerceIn(0f, 0.96f)
        val x = ((1f / (1f - ratio)) - 1f) / 0.62f
        rawPullAccumPx = pullDeadzonePx + x * maxPullPx
        currentPullPx = clamped
        refreshThresholdCrossed = clamped >= refreshThresholdPx
    }

    private fun updatePullFromRaw() {
        if (rawPullAccumPx <= pullDeadzonePx) {
            currentPullPx = 0f
        } else {
            val effectiveRaw = (rawPullAccumPx - pullDeadzonePx).coerceAtLeast(0f)
            val x = (effectiveRaw / maxPullPx).coerceAtLeast(0f)
            // Asymptotic Safari/UIScrollView rubber-band resistance curve
            currentPullPx = (maxPullPx * (1f - 1f / (x * 0.62f + 1f))).coerceIn(0f, maxPullPx)
        }
        if (currentPullPx < refreshThresholdPx) {
            refreshThresholdCrossed = false
        }
    }

    init {
        setOnLongClickListener {
            val hit = hitTestResult
            val extra = hit.extra?.takeIf { it.isNotBlank() } ?: return@setOnLongClickListener false
            when (hit.type) {
                HitTestResult.IMAGE_TYPE,
                HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> {
                    onLongPressHit?.invoke(ContextHitInfo(ContextHitType.IMAGE, extra, title ?: ""))
                    true
                }
                HitTestResult.SRC_ANCHOR_TYPE -> {
                    onLongPressHit?.invoke(ContextHitInfo(ContextHitType.LINK, extra, title ?: ""))
                    true
                }
                else -> false
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pendingReadingPosition = null
                activePointerId = event.getPointerId(0)
                lastTouchX = event.getX(0)
                lastTouchY = event.getY(0)
                downTouchX = lastTouchX
                downTouchY = lastTouchY
                handlingTopGesture = false
                horizontalScrollLocked = false
                sentCancelToSuper = false
                seedPullFromVisual(externalVisualPullPxProvider?.invoke() ?: 0f)
                return super.onTouchEvent(event)
            }

            MotionEvent.ACTION_POINTER_UP -> {
                val actionIdx = event.actionIndex
                if (event.pointerCount > 1 && event.getPointerId(actionIdx) == activePointerId) {
                    val newIdx = if (actionIdx == 0) 1 else 0
                    if (newIdx in 0 until event.pointerCount) {
                        activePointerId = event.getPointerId(newIdx)
                        lastTouchX = event.getX(newIdx)
                        lastTouchY = event.getY(newIdx)
                    }
                }
                return super.onTouchEvent(event)
            }

            MotionEvent.ACTION_MOVE -> {
                val ptrIdx = if (activePointerId != MotionEvent.INVALID_POINTER_ID) {
                    event.findPointerIndex(activePointerId)
                } else {
                    0
                }
                if (ptrIdx !in 0 until event.pointerCount) return super.onTouchEvent(event)
                val x = event.getX(ptrIdx)
                val y = event.getY(ptrIdx)
                val dx = x - lastTouchX
                val dy = y - lastTouchY
                lastTouchX = x
                lastTouchY = y

                if (!handlingTopGesture && !horizontalScrollLocked) {
                    val totalDx = abs(x - downTouchX)
                    val totalDy = abs(y - downTouchY)
                    if (totalDx > 6f * density && totalDx > totalDy * 1.45f) {
                        horizontalScrollLocked = true
                    }
                }

                if (scrollY == 0 && !horizontalScrollLocked && (handlingTopGesture || abs(dy) >= abs(dx) * 0.7f)) {
                    if (dy < 0f && (currentPullPx > 0f || rawPullAccumPx > 0f || (currentTopSafeOffsetPx > 0f && canPageScrollDown()))) {
                        handlingTopGesture = true
                        cancelSuperIfNeeded(event)
                        val prevRaw = rawPullAccumPx
                        val upDist = -dy
                        rawPullAccumPx = (rawPullAccumPx - upDist).coerceAtLeast(0f)
                        updatePullFromRaw()
                        val remUp = (upDist - prevRaw).coerceAtLeast(0f)
                        if (remUp > 0f && canPageScrollDown()) {
                            currentTopSafeOffsetPx = (currentTopSafeOffsetPx - remUp).coerceAtLeast(0f)
                        }
                        if (!canPageScrollDown()) {
                            currentTopSafeOffsetPx = topSafeInsetPx
                            if (currentPullPx <= 0f && rawPullAccumPx <= 0f) {
                                handlingTopGesture = false
                                onTopInteraction?.invoke(currentTopSafeOffsetPx, 0f, false)
                                return super.onTouchEvent(event)
                            }
                        }
                        if (currentTopSafeOffsetPx > 0f || currentPullPx > 0f) {
                            onTopInteraction?.invoke(currentTopSafeOffsetPx, currentPullPx, true)
                            return true
                        } else {
                            // Top safe offset reached 0: hand off cleanly into WebView scroll
                            onTopInteraction?.invoke(0f, 0f, false)
                            handlingTopGesture = false
                            if (sentCancelToSuper) {
                                sentCancelToSuper = false
                                val synthDown = MotionEvent.obtain(
                                    event.eventTime,
                                    event.eventTime,
                                    MotionEvent.ACTION_DOWN,
                                    x,
                                    y,
                                    0,
                                )
                                super.onTouchEvent(synthDown)
                                synthDown.recycle()
                            }
                            return super.onTouchEvent(event)
                        }
                    } else if (dy > 0f && (currentTopSafeOffsetPx < topSafeInsetPx || currentPullPx > 0f || handlingTopGesture || abs(dy) > 0.5f)) {
                        handlingTopGesture = true
                        cancelSuperIfNeeded(event)
                        val roomToSafe = (topSafeInsetPx - currentTopSafeOffsetPx).coerceAtLeast(0f)
                        val toSafe = minOf(dy, roomToSafe)
                        currentTopSafeOffsetPx += toSafe
                        val remDown = dy - toSafe
                        if (remDown > 0f && currentTopSafeOffsetPx >= topSafeInsetPx - 0.5f) {
                            rawPullAccumPx += remDown
                            updatePullFromRaw()
                            if (currentPullPx >= refreshThresholdPx && !refreshThresholdCrossed) {
                                refreshThresholdCrossed = true
                                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            }
                        }
                        onTopInteraction?.invoke(currentTopSafeOffsetPx, currentPullPx, true)
                        return true
                    }
                }
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                activePointerId = MotionEvent.INVALID_POINTER_ID
                horizontalScrollLocked = false
                if (handlingTopGesture) {
                    val shouldRefresh = currentPullPx >= refreshThresholdPx && event.actionMasked == MotionEvent.ACTION_UP
                    currentPullPx = 0f
                    rawPullAccumPx = 0f
                    handlingTopGesture = false
                    refreshThresholdCrossed = false
                    sentCancelToSuper = false
                    if (scrollY == 0) {
                        currentTopSafeOffsetPx = topSafeInsetPx
                    }
                    onTopInteraction?.invoke(currentTopSafeOffsetPx, 0f, false)
                    if (shouldRefresh) {
                        onPullRefreshTrigger?.invoke()
                    }
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }

    private fun cancelSuperIfNeeded(event: MotionEvent) {
        if (!sentCancelToSuper) {
            sentCancelToSuper = true
            val cancel = MotionEvent.obtain(event).apply { action = MotionEvent.ACTION_CANCEL }
            super.onTouchEvent(cancel)
            cancel.recycle()
        }
    }

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        if (!handlingTopGesture && t > scrollCollapseMinPx && currentTopSafeOffsetPx > 0.5f) {
            currentTopSafeOffsetPx = 0f
            currentPullPx = 0f
            onTopInteraction?.invoke(0f, 0f, false)
        } else if (t == 0 && oldt > 0 && !handlingTopGesture) {
            currentTopSafeOffsetPx = topSafeInsetPx
            currentPullPx = 0f
            onTopInteraction?.invoke(currentTopSafeOffsetPx, 0f, false)
        }
        onScrollDelta?.invoke(t - oldt, t)
    }
}
