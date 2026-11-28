package com.terra.browser.webview

import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.terra.browser.model.BrowserTab

private const val MAX_ATTACHED_WEBVIEWS = 6

@Composable
fun TerraWebViewHost(
    tab: BrowserTab,
    pool: TerraWebViewPool,
    appDark: Boolean,
    topSafeInsetPx: Float,
    bottomSafeInsetDp: Int,
    onScrollDelta: (dy: Int, scrollY: Int) -> Unit,
    onTopInteraction: (topSafeOffsetPx: Float, pullRefreshPx: Float, isDragging: Boolean) -> Unit,
    onPullRefreshTrigger: () -> Unit,
    modifier: Modifier = Modifier,
    visualPullPxProvider: (() -> Float)? = null,
    onVisualFrameReady: ((tabId: String) -> Unit)? = null,
    isVisible: Boolean = true,
) {
    val latestOnVisualReady by rememberUpdatedState(onVisualFrameReady)
    val latestOnScrollDelta by rememberUpdatedState(onScrollDelta)
    val latestOnTopInteraction by rememberUpdatedState(onTopInteraction)
    val latestOnPullRefreshTrigger by rememberUpdatedState(onPullRefreshTrigger)
    val latestVisualPullPxProvider by rememberUpdatedState(visualPullPxProvider)
    var visualSeq by remember { mutableLongStateOf(0L) }
    val attachedOrder = remember { ArrayList<String>(MAX_ATTACHED_WEBVIEWS + 2) }
    val attachedViews = remember { HashMap<String, TerraWebView>() }

    LaunchedEffect(tab.id, tab.url, tab.requestDesktopSite, tab.pageZoom, tab.adaptiveDarkMode, appDark) {
        pool.syncTab(tab, appDark)
    }

    LaunchedEffect(tab.id, bottomSafeInsetDp) {
        pool.updateBottomSafeInset(tab.id, bottomSafeInsetDp)
    }

    DisposableEffect(Unit) {
        onDispose {
            pool.saveAllActiveStatesAndSnapshots()
        }
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            FrameLayout(ctx).apply {
                clipChildren = false
                clipToPadding = false
            }
        },
        update = { container ->
            val target = pool.obtain(tab, appDark) { dy, scrollY ->
                latestOnScrollDelta(dy, scrollY)
            }
            val wasAlreadyAttached = target.parent === container &&
                target.width > 0 &&
                target.height > 0

            target.onTopInteraction = { safePx, pullPx, dragging ->
                latestOnTopInteraction(safePx, pullPx, dragging)
            }
            target.onPullRefreshTrigger = { latestOnPullRefreshTrigger() }
            target.externalVisualPullPxProvider = { latestVisualPullPxProvider?.invoke() ?: 0f }
            target.topSafeInsetPx = topSafeInsetPx

            if (target.parent !== container) {
                (target.parent as? ViewGroup)?.removeView(target)
                container.addView(
                    target,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    ),
                )
            }
            attachedViews[tab.id] = target
            attachedOrder.remove(tab.id)
            attachedOrder.add(tab.id)

            // Hide and pause inactive cached WebViews WITHOUT detaching them from the persistent container,
            // preserving Chromium's compositor layer & exact layout across rapid tab switches.
            val cachedIds = pool.activeCachedIds()
            val toDetachIds = mutableListOf<String>()
            attachedViews.forEach { (id, wv) ->
                if (id !in cachedIds) {
                    toDetachIds.add(id)
                } else if (id != tab.id) {
                    if (wv.visibility == View.VISIBLE) {
                        pool.captureSnapshotBeforeHide(id)
                        pool.saveTabStateDeferred(id)
                    }
                    if (wv.visibility != View.INVISIBLE) {
                        wv.visibility = View.INVISIBLE
                        wv.onPause()
                    }
                }
            }
            toDetachIds.forEach { staleId ->
                attachedOrder.remove(staleId)
                attachedViews.remove(staleId)?.let { wv ->
                    if (wv.parent === container) container.removeView(wv)
                }
            }
            while (attachedOrder.size > MAX_ATTACHED_WEBVIEWS) {
                val oldestId = attachedOrder.firstOrNull { it != tab.id } ?: break
                attachedOrder.remove(oldestId)
                attachedViews.remove(oldestId)?.let { wv ->
                    if (wv.parent === container) container.removeView(wv)
                }
            }

            if (isVisible) {
                target.onResume()
                if (target.visibility != View.VISIBLE) {
                    target.visibility = View.VISIBLE
                }
                if (container.childCount > 1 && container.getChildAt(container.childCount - 1) !== target) {
                    target.bringToFront()
                }
            } else {
                if (target.visibility != View.INVISIBLE) {
                    pool.captureSnapshotBeforeHide(tab.id)
                    target.visibility = View.INVISIBLE
                }
            }

            if (wasAlreadyAttached) {
                latestOnVisualReady?.invoke(tab.id)
            } else {
                val expectedTabId = tab.id
                val seq = ++visualSeq
                target.post {
                    runCatching {
                        target.postVisualStateCallback(
                            seq,
                            object : WebView.VisualStateCallback() {
                                override fun onComplete(requestId: Long) {
                                    if (requestId == seq) {
                                        latestOnVisualReady?.invoke(expectedTabId)
                                    }
                                }
                            },
                        )
                    }.onFailure {
                        latestOnVisualReady?.invoke(expectedTabId)
                    }
                }
            }
        },
    )
}
