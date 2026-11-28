package com.terra.browser.ui

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.zIndex
import com.terra.browser.model.BrowserLayoutClassifier
import com.terra.browser.model.BrowserLayoutProfile
import com.terra.browser.model.BrowserSafeAreaCalculator
import com.terra.browser.model.BrowserScrollCollapseTracker
import com.terra.browser.model.BrowserTab
import com.terra.browser.model.BrowserTopVeilCalculator
import com.terra.browser.model.DarkModePreference
import com.terra.browser.model.LibraryTab
import com.terra.browser.model.SiteDarkMode
import com.terra.browser.model.TabBarMode
import com.terra.browser.reader.SafariReaderOverlay
import com.terra.browser.state.TerraBrowserState
import com.terra.browser.webview.PixelCopyCaptureSpec
import com.terra.browser.webview.TabSnapshotMeta
import com.terra.browser.webview.TerraWebViewHost
import com.terra.browser.webview.TerraWebViewPool
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import io.liquidkit.LiquidKitGlassSurface
import io.liquidkit.LiquidKitHaptic
import io.liquidkit.LiquidKitOverscrollScope
import io.liquidkit.LiquidKitRoot
import io.liquidkit.LiquidKitSpring
import io.liquidkit.LiquidKitSymbol
import io.liquidkit.LiquidKitToast
import io.liquidkit.LiquidKitToastAlignment
import io.liquidkit.LocalLiquidKitSymbolRenderer
import io.liquidkit.liquidKitBackdrop
import io.liquidkit.liquidKitColors
import io.liquidkit.rememberLiquidKitBackdrop
import io.liquidkit.rememberLiquidKitHaptics
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin

private data class LiftCardGeometry(
    val rect: Rect,
    val cornerDp: Float,
)

private fun computeDragLiftGeometry(
    curX: Float,
    curY: Float,
    rootW: Float,
    rootH: Float,
    swipeGapPx: Float,
    cardAspectRatio: Float,
): LiftCardGeometry {
    if (curY >= -1f) {
        val swipeStep = (rootW + swipeGapPx).coerceAtLeast(1f)
        val slideFrac = (curX / swipeStep).coerceIn(-1.25f, 1.25f)
        val horizP = abs(slideFrac).coerceIn(0f, 1f)
        // Symmetric bell curve: 0 at rest (0.0), peaks at mid-swipe (0.5), returns to 0 on dock (1.0)
        val sep = sin(horizP * PI).toFloat().coerceIn(0f, 1f)
        val smoothSep = Math.pow(sep.toDouble(), 0.72).toFloat().coerceIn(0f, 1f)
        val scale = lerp(1f, 0.94f, smoothSep)
        val curW = (rootW * scale).coerceAtLeast(1f)
        val curH = (rootH * scale).coerceAtLeast(1f)
        val adjStep = curW + swipeGapPx
        val left = (rootW - curW) * 0.5f + slideFrac * adjStep
        val top = (rootH - curH) * 0.5f
        val cornerDp = lerp(0f, 34f, smoothSep)
        return LiftCardGeometry(
            rect = Rect(left, top, left + curW, top + curH),
            cornerDp = cornerDp,
        )
    }
    val rawLift = (-curY / (rootH * 0.50f).coerceAtLeast(1f)).coerceAtLeast(0f)
    val clampedLift = rawLift.coerceIn(0f, 1f)
    val widthScale = lerp(1f, 0.50f, clampedLift)
    val curW = (rootW * widthScale).coerceAtLeast(1f)
    val fullScaledH = (rootH * widthScale).coerceAtLeast(1f)
    val cardRatioH = (curW / cardAspectRatio.coerceAtLeast(0.4f)).coerceAtMost(fullScaledH)
    val cropT = 1f - (1f - clampedLift) * (1f - clampedLift)
    val curH = lerp(fullScaledH, cardRatioH, cropT)
    val extraUp = if (rawLift > 1f) rubberBand(rawLift - 1f, 0.14f) * rootH * 0.22f else 0f
    val bottomY = (rootH + curY * 0.85f - extraUp).coerceAtLeast(curH + rootH * 0.12f)
    val topY = bottomY - curH
    val centerX = rootW * 0.5f + curX * lerp(1f, 0.78f, clampedLift)
    val cornerDp = if (clampedLift < 0.06f) {
        lerp(0f, 32f, clampedLift / 0.06f)
    } else {
        lerp(32f, 22f, ((clampedLift - 0.06f) / 0.94f).coerceIn(0f, 1f))
    }
    return LiftCardGeometry(
        rect = Rect(
            left = centerX - curW * 0.5f,
            top = topY,
            right = centerX + curW * 0.5f,
            bottom = bottomY,
        ),
        cornerDp = cornerDp,
    )
}

private fun lerpHeroRect(full: Rect, target: Rect, p: Float): Rect {
    val t = p.coerceIn(0f, 1f)
    val hT = 1f - (1f - t) * (1f - t)
    val left = lerp(full.left, target.left, t)
    val right = lerp(full.right, target.right, t)
    val top = lerp(full.top, target.top, t)
    val w = (right - left).coerceAtLeast(1f)
    val h = lerp(full.height, target.height, hT).coerceAtLeast(target.height)
    return Rect(left, top, left + w, top + h)
}

private fun rubberBand(over: Float, limit: Float): Float {
    if (over == 0f || limit <= 0f) return 0f
    val a = abs(over)
    return (1f - 1f / (a * 0.55f / limit + 1f)) * limit * sign(over)
}

@Composable
fun TerraBrowserApp(
    state: TerraBrowserState,
    pool: TerraWebViewPool,
) {
    val systemDark = isSystemInDarkTheme()
    val appDark = when (state.darkModePreference) {
        DarkModePreference.SYSTEM -> systemDark
        DarkModePreference.LIGHT -> false
        DarkModePreference.DARK -> true
    }

    TerraLiquidKitRoot(
        modifier = Modifier.fillMaxSize(),
        opacity = state.glassOpacity,
        dark = appDark,
        edgeToEdgeSystemBars = true,
    ) {
        val colors = liquidKitColors(appDark)
        val pageBackdrop = rememberLiquidKitBackdrop()
        val scope = rememberCoroutineScope()
        val density = LocalDensity.current

        val cardBoundsMap = remember { HashMap<String, Rect>() }
        val overviewProgress = remember { Animatable(if (state.isOverviewOpen) 1f else 0f, visibilityThreshold = 0.004f) }
        var liveDragX by remember { mutableFloatStateOf(0f) }
        var rawDragX by remember { mutableFloatStateOf(0f) }
        var liveDragY by remember { mutableFloatStateOf(0f) }
        var isBarDragging by remember { mutableStateOf(false) }
        val settleDragX = remember { Animatable(0f) }
        val settleDragY = remember { Animatable(0f) }
        var isSettlingBarDrag by remember { mutableStateOf(false) }
        var barGestureJob by remember { mutableStateOf<Job?>(null) }
        var releaseStartRect by remember { mutableStateOf<Rect?>(null) }
        var releaseStartCornerDp by remember { mutableFloatStateOf(24f) }
        var releaseStartP by remember { mutableFloatStateOf(0f) }
        var pendingReleaseVelocity by remember { mutableFloatStateOf(0f) }
        var morphTargetRectOverride by remember { mutableStateOf<Rect?>(null) }
        var closingOverrideRect by remember { mutableStateOf<Rect?>(null) }
        var closingOverrideCornerDp by remember { mutableFloatStateOf(18f) }
        var closingOverrideP by remember { mutableFloatStateOf(1f) }
        var settledWebViewTabId by remember { mutableStateOf<String?>(null) }
        val addressEditProgress = remember { Animatable(if (state.isAddressEditing) 1f else 0f) }
        val heroOpenSpring = remember { LiquidKitSpring.swiftUI(0.32f, 0.88f) }
        val heroCloseSpring = remember { LiquidKitSpring.swiftUI(0.30f, 0.88f) }
        val editSpring = remember { LiquidKitSpring.swiftUI(0.32f, 0.86f) }
        var lastRootWidthPx by remember { mutableFloatStateOf(1f) }
        var lastRootHeightPx by remember { mutableFloatStateOf(1f) }
        var lastStatusBarTopPx by remember { mutableFloatStateOf(0f) }
        var lastCutoutStartPx by remember { mutableFloatStateOf(0f) }
        var lastCutoutEndPx by remember { mutableFloatStateOf(0f) }
        var lastLayoutProfile by remember { mutableStateOf(BrowserLayoutProfile.PHONE_PORTRAIT) }

        LaunchedEffect(state.isOverviewOpen) {
            if (state.isOverviewOpen) {
                val curTab = state.activeTab
                if (!curTab.isStartPage && releaseStartRect == null) {
                    pool.ensureSnapshotForTransition(curTab.id)
                }
                closingOverrideRect = null
                val vel = if (releaseStartRect != null) {
                    pendingReleaseVelocity.also { pendingReleaseVelocity = 0f }
                } else {
                    overviewProgress.velocity.coerceIn(-8f, 8f)
                }
                if (abs(overviewProgress.value - 1f) > 0.001f) {
                    try {
                        overviewProgress.animateTo(
                            targetValue = 1f,
                            animationSpec = heroOpenSpring.spec,
                            initialVelocity = vel,
                        )
                    } finally {
                        if (coroutineContext.isActive) {
                            releaseStartRect = null
                            morphTargetRectOverride = null
                        }
                    }
                } else {
                    releaseStartRect = null
                    morphTargetRectOverride = null
                }
            } else {
                val curReleaseStart = releaseStartRect
                if (curReleaseStart != null && overviewProgress.value < 0.99f) {
                    val activeId = state.activeTab.id
                    val targetRect = morphTargetRectOverride
                        ?: cardBoundsMap[activeId]
                        ?: computeExpectedOverviewCardRect(
                            state = state,
                            tabId = activeId,
                            layoutProfile = lastLayoutProfile,
                            rootWidthPx = lastRootWidthPx,
                            rootHeightPx = lastRootHeightPx,
                            statusBarTopPx = lastStatusBarTopPx,
                            densityPx = density.density,
                            cutoutStartPx = lastCutoutStartPx,
                            cutoutEndPx = lastCutoutEndPx,
                        )
                    val t = ((overviewProgress.value - releaseStartP) / (1f - releaseStartP).coerceAtLeast(0.01f)).coerceIn(0f, 1f)
                    closingOverrideRect = androidx.compose.ui.geometry.lerp(curReleaseStart, targetRect, t)
                    closingOverrideCornerDp = lerp(releaseStartCornerDp, 18f, t)
                    closingOverrideP = overviewProgress.value.coerceAtLeast(0.01f)
                } else if (overviewProgress.value >= 0.96f) {
                    closingOverrideRect = null
                }
                releaseStartRect = null
                pendingReleaseVelocity = 0f

                if (abs(overviewProgress.value) > 0.001f) {
                    try {
                        overviewProgress.animateTo(
                            targetValue = 0f,
                            animationSpec = heroCloseSpring.spec,
                            initialVelocity = overviewProgress.velocity.coerceIn(-8f, 8f),
                        )
                    } finally {
                        if (coroutineContext.isActive) {
                            closingOverrideRect = null
                            morphTargetRectOverride = null
                        }
                    }
                } else {
                    closingOverrideRect = null
                    morphTargetRectOverride = null
                }
            }
        }

        LaunchedEffect(state.isAddressEditing) {
            val target = if (state.isAddressEditing) 1f else 0f
            if (abs(addressEditProgress.value - target) > 0.001f) {
                addressEditProgress.animateTo(target, editSpring.spec)
            }
        }

        val triggerHaptic = rememberLiquidKitHaptics()
        val activeTab = state.activeTab
        val hostActivity = LocalContext.current as? android.app.Activity
        val hostView = LocalView.current
        val statusBarDarkSurface = appDark ||
            (state.activeGroup.isPrivate && (state.isOverviewOpen || activeTab.isStartPage))
        SideEffect {
            val win = hostActivity?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(win, hostView)
            controller.isAppearanceLightStatusBars = !statusBarDarkSurface
            controller.isAppearanceLightNavigationBars = !statusBarDarkSurface
        }

        BackHandler(enabled = !state.showSettingsSheet) {
            when {
                state.onBackDismissChromeMenu?.invoke() == true -> Unit
                state.activeContextHit != null -> state.activeContextHit = null
                state.showNewGroupAlert -> {
                    state.showNewGroupAlert = false
                    state.newGroupDraft = ""
                }
                state.showManageGroupsSheet -> state.showManageGroupsSheet = false
                state.showCustomizeMenuSheet -> state.showCustomizeMenuSheet = false
                state.showZoomSheet -> state.showZoomSheet = false
                state.showPrivacyReportSheet -> state.showPrivacyReportSheet = false
                state.showShareSheet -> state.showShareSheet = false
                state.showLibrarySheet -> state.showLibrarySheet = false
                state.isAddressEditing -> state.isAddressEditing = false
                state.findInPageVisible -> {
                    state.findInPageVisible = false
                    state.findQuery = ""
                    pool.clearFind(activeTab.id)
                }
                state.activeReaderArticle != null && state.activeReaderTabId == activeTab.id -> state.closeReader()
                state.isOverviewSearchActive -> {
                    state.isOverviewSearchActive = false
                    state.overviewSearchQuery = ""
                }
                state.isOverviewOpen -> state.isOverviewOpen = false
                state.isTabletSidebarOpen -> state.isTabletSidebarOpen = false
                !activeTab.isStartPage && activeTab.canGoBack && pool.goBack(activeTab.id) -> Unit
                !activeTab.isStartPage -> state.navigateToStartPage()
                else -> hostActivity?.moveTaskToBack(true)
            }
        }

        BoxWithConstraints(Modifier.fillMaxSize()) {
            val layoutProfile = BrowserLayoutClassifier.classify(maxWidth.value, maxHeight.value)
            val showTabletSidebar by remember(layoutProfile) {
                derivedStateOf {
                    layoutProfile == BrowserLayoutProfile.TABLET_LANDSCAPE &&
                        state.isTabletSidebarOpen &&
                        !state.isOverviewOpen &&
                        overviewProgress.value < 0.05f
                }
            }

            val topBarExtra by animateDpAsState(
                targetValue = when {
                    layoutProfile == BrowserLayoutProfile.TABLET_LANDSCAPE -> if (state.isToolbarHidden) {
                        0.dp
                    } else if (state.isToolbarMinimized) {
                        44.dp
                    } else if (state.showTabletTabBar) {
                        92.dp
                    } else {
                        54.dp
                    }
                    layoutProfile != BrowserLayoutProfile.PHONE_LANDSCAPE &&
                        state.tabBarMode == TabBarMode.TOP &&
                        !state.isToolbarHidden -> {
                        if (state.isToolbarMinimized) 42.dp else 62.dp
                    }
                    else -> 0.dp
                },
                animationSpec = LiquidKitSpring.swiftUI(0.32f, 0.86f).dpSpec,
                label = "TopBarExtra",
            )
            val statusBarTopDp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
            val navBarBottomDp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding().value
            val topSafeInsetPx = with(density) { (statusBarTopDp + topBarExtra).toPx() }
            val dockBottomClearanceDp = BrowserSafeAreaCalculator.bottomSafeInsetDp(
                layoutProfile = layoutProfile,
                tabBarMode = state.tabBarMode,
                isToolbarHidden = state.isToolbarHidden,
                isToolbarMinimized = false,
                navigationBarBottomDp = navBarBottomDp,
            )

            val tabMinimizedState = remember { mutableMapOf<String, Boolean>() }
            val initialTopOffsetPx = remember(activeTab.id) {
                pool.currentTopSafeOffsetPx(activeTab.id, topSafeInsetPx)
            }
            var dragTopSafeOffsetPx by remember(activeTab.id) { mutableFloatStateOf(initialTopOffsetPx) }
            var dragPullRefreshPx by remember(activeTab.id) { mutableFloatStateOf(0f) }
            val topSafeOffsetAnim = remember(activeTab.id) { Animatable(initialTopOffsetPx) }
            val pullRefreshAnim = remember(activeTab.id) { Animatable(0f) }
            val refreshHoldAnim = remember(activeTab.id) { Animatable(0f) }
            var topInteractionJob by remember(activeTab.id) { mutableStateOf<Job?>(null) }
            var isTopDragging by remember(activeTab.id) { mutableStateOf(false) }
            var isPullRefreshing by remember(activeTab.id) { mutableStateOf(false) }
            val scrollTracker = remember(activeTab.id) { BrowserScrollCollapseTracker() }

            LaunchedEffect(activeTab.id) {
                val restoredOffset = pool.currentTopSafeOffsetPx(activeTab.id, topSafeInsetPx)
                dragTopSafeOffsetPx = restoredOffset
                topSafeOffsetAnim.snapTo(restoredOffset)
                state.isToolbarMinimized = if (activeTab.isStartPage) false else (tabMinimizedState[activeTab.id] ?: false)
            }
            LaunchedEffect(topSafeInsetPx) {
                if (!isTopDragging && topSafeOffsetAnim.value > 1f && dragPullRefreshPx <= 0.5f &&
                    pool.currentTopSafeOffsetPx(activeTab.id, topSafeInsetPx) > 1f
                ) {
                    dragTopSafeOffsetPx = topSafeInsetPx
                    topSafeOffsetAnim.snapTo(topSafeInsetPx)
                }
            }
            LaunchedEffect(activeTab.id) {
                snapshotFlow { state.isToolbarMinimized to state.isOverviewOpen }.collect { (minimized, overviewOpen) ->
                    if (!overviewOpen && !activeTab.isStartPage) {
                        tabMinimizedState[activeTab.id] = minimized
                    }
                    if (!minimized) {
                        scrollTracker.reset()
                    }
                }
            }
            LaunchedEffect(isPullRefreshing) {
                refreshHoldAnim.animateTo(
                    targetValue = if (isPullRefreshing) with(density) { 44.dp.toPx() } else 0f,
                    animationSpec = LiquidKitSpring.swiftUI(0.32f, 0.86f).spec,
                )
            }
            LaunchedEffect(isPullRefreshing, activeTab.id) {
                if (isPullRefreshing) {
                    delay(380)
                    if (state.activeTab.id == activeTab.id && state.activeTab.isLoading) {
                        withTimeoutOrNull(1400L) {
                            snapshotFlow { state.activeTab.isLoading }.first { !it }
                        }
                    }
                    isPullRefreshing = false
                }
            }

            Row(Modifier.fillMaxSize()) {
                AnimatedVisibility(
                    visible = showTabletSidebar,
                    enter = fadeIn(tween(180)) + expandHorizontally(expandFrom = Alignment.Start, animationSpec = tween(200)),
                    exit = fadeOut(tween(150)) + shrinkHorizontally(shrinkTowards = Alignment.Start, animationSpec = tween(180)),
                ) {
                    TabletSidebar(
                        state = state,
                        pool = pool,
                        onCloseSidebar = { state.isTabletSidebarOpen = false },
                    )
                }

                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                ) {
                    val rootWidthDp = maxWidth
                    val rootHeightDp = maxHeight
                    val rootWidthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
                    val rootHeightPx = with(density) { maxHeight.toPx() }.coerceAtLeast(1f)
                    val statusBarTopPx = with(density) { statusBarTopDp.toPx() }
                    val cutoutTopDp = WindowInsets.displayCutout.asPaddingValues().calculateTopPadding()
                    val topSafePx = with(density) { maxOf(statusBarTopDp, cutoutTopDp).toPx() }
                    val cutoutPadding = WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal).asPaddingValues()
                    val cutoutStartPx = with(density) { cutoutPadding.calculateLeftPadding(LayoutDirection.Ltr).toPx() }
                    val cutoutEndPx = with(density) { cutoutPadding.calculateRightPadding(LayoutDirection.Ltr).toPx() }
                    lastRootWidthPx = rootWidthPx
                    lastRootHeightPx = rootHeightPx
                    lastStatusBarTopPx = topSafePx
                    lastCutoutStartPx = cutoutStartPx
                    lastCutoutEndPx = cutoutEndPx
                    lastLayoutProfile = layoutProfile

            val swipeGapPx = with(density) { 16.dp.toPx() }
            val swipeStepPx = rootWidthPx + swipeGapPx
            val activeGesture by remember { derivedStateOf { isBarDragging || isSettlingBarDrag } }
            val canSwipeToNewTab = state.peekAdjacentTab(+1) == null && !activeTab.isStartPage
            val overviewCardAspectRatio = overviewCardAspectRatio(layoutProfile, maxWidth.value, maxHeight.value)
            var overviewWarmed by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                delay(450L)
                overviewWarmed = true
            }
            val effectiveOverviewPProvider: () -> Float = {
                val dragY = if (isBarDragging) liveDragY else settleDragY.value
                val liftP = if (isBarDragging || isSettlingBarDrag) (-dragY / (rootHeightPx * 0.50f)).coerceIn(0f, 1f) else 0f
                val dragPreview = if (liftP > 0.03f) ((liftP - 0.03f) / 0.62f).coerceIn(0f, 0.82f) else 0f
                maxOf(overviewProgress.value.coerceIn(0f, 1f), dragPreview)
            }
            val showOverviewLayer by remember(rootHeightPx) {
                derivedStateOf {
                    effectiveOverviewPProvider() > 0.002f
                }
            }
            val isHeroMorphActive by remember {
                derivedStateOf {
                    val p = overviewProgress.value
                    isBarDragging ||
                        isSettlingBarDrag ||
                        closingOverrideRect != null ||
                        (p > 0.003f && p < 0.994f) ||
                        (state.isOverviewOpen && p < 0.994f) ||
                        (!state.isOverviewOpen && p > 0.003f)
                }
            }
            val isLivePageLayerVisible by remember {
                derivedStateOf {
                    !isHeroMorphActive && overviewProgress.value <= 0.002f && !state.isOverviewOpen
                }
            }
            val isWebContentVisible by remember {
                derivedStateOf {
                    overviewProgress.value < 0.99f || !state.isOverviewOpen
                }
            }
            val isTransitionInFlight by remember {
                derivedStateOf {
                    isHeroMorphActive ||
                        (overviewProgress.value > 0.002f && overviewProgress.value < 0.998f)
                }
            }
            LaunchedEffect(isTransitionInFlight) {
                pool.isTransitionActive = isTransitionInFlight
            }
            val hasBlockingModal by remember {
                derivedStateOf {
                    state.showSettingsSheet ||
                        state.showLibrarySheet ||
                        state.showShareSheet ||
                        state.showPrivacyReportSheet ||
                        state.showManageGroupsSheet ||
                        state.showCustomizeMenuSheet ||
                        state.showZoomSheet ||
                        state.isAddressEditing
                }
            }
            val pageBackdropEnabled by remember {
                derivedStateOf {
                    !state.isOverviewOpen &&
                        overviewProgress.value < 0.02f &&
                        !isBarDragging &&
                        !isSettlingBarDrag &&
                        !hasBlockingModal
                }
            }
            val pixelCopyCropTopPx = if (layoutProfile == BrowserLayoutProfile.TABLET_LANDSCAPE ||
                (layoutProfile != BrowserLayoutProfile.PHONE_LANDSCAPE && state.tabBarMode == TabBarMode.TOP && !state.isToolbarHidden)
            ) {
                topSafeInsetPx.roundToInt()
            } else {
                statusBarTopPx.roundToInt()
            }
            val pixelCopyBottomClearanceDp = when {
                layoutProfile == BrowserLayoutProfile.TABLET_LANDSCAPE -> navBarBottomDp
                state.isToolbarHidden -> navBarBottomDp + 8f
                layoutProfile == BrowserLayoutProfile.PHONE_LANDSCAPE -> navBarBottomDp + if (state.isToolbarMinimized) 44f else 62f
                else -> when (state.tabBarMode) {
                    TabBarMode.BOTTOM -> navBarBottomDp + if (state.isToolbarMinimized) 50f else 116f
                    TabBarMode.COMPACT -> navBarBottomDp + if (state.isToolbarMinimized) 48f else 74f
                    TabBarMode.TOP -> navBarBottomDp + 8f
                }
            }
            val pixelCopyCropBottomPx = (rootHeightPx - with(density) { pixelCopyBottomClearanceDp.dp.toPx() })
                .roundToInt()
                .coerceAtLeast(pixelCopyCropTopPx + 64)

            SideEffect {
                val win = hostActivity?.window
                pool.pixelCopySpecProvider = if (win != null) {
                    {
                        PixelCopyCaptureSpec(
                            window = win,
                            activeTabId = activeTab.id,
                            cropTopPx = pixelCopyCropTopPx,
                            cropBottomPx = pixelCopyCropBottomPx,
                            canPixelCopy = isLivePageLayerVisible &&
                                !activeTab.isStartPage &&
                                !hasBlockingModal &&
                                state.activeReaderArticle == null &&
                                state.activeContextHit == null &&
                                !isPullRefreshing &&
                                dragPullRefreshPx <= 1f &&
                                !isTopDragging,
                        )
                    }
                } else {
                    null
                }
            }
            DisposableEffect(Unit) {
                onDispose {
                    pool.pixelCopySpecProvider = null
                }
            }

            // 1. Tab Overview Layer (kept warm after startup; collapses to 0x0 when hidden)
            if (overviewWarmed || showOverviewLayer) {
                OverviewLayerHost(
                    state = state,
                    pool = pool,
                    backdrop = pageBackdrop,
                    effectiveOverviewPProvider = effectiveOverviewPProvider,
                    isHeroMorphActive = { isHeroMorphActive },
                    cardBoundsMap = cardBoundsMap,
                    layoutProfile = layoutProfile,
                    onCardPicked = { pickedId, bounds ->
                        val resolvedBounds = bounds ?: computeExpectedOverviewCardRect(
                            state = state,
                            tabId = pickedId,
                            layoutProfile = layoutProfile,
                            rootWidthPx = rootWidthPx,
                            rootHeightPx = rootHeightPx,
                            statusBarTopPx = topSafePx,
                            densityPx = density.density,
                            cutoutStartPx = cutoutStartPx,
                            cutoutEndPx = cutoutEndPx,
                        )
                        releaseStartRect = null
                        morphTargetRectOverride = resolvedBounds
                        if (pickedId != activeTab.id && overviewProgress.value < 0.96f) {
                            closingOverrideRect = resolvedBounds
                            closingOverrideCornerDp = 18f
                            closingOverrideP = overviewProgress.value.coerceAtLeast(0.15f)
                        } else {
                            closingOverrideRect = null
                        }
                    },
                )
            }

            // 2. Active Browser Page Layer + Pure-GPU Zero-Hitch Hero Transition Overlay
            run {
                val wantDarkWebCss = when (activeTab.adaptiveDarkMode) {
                    SiteDarkMode.ON -> true
                    SiteDarkMode.OFF -> false
                    SiteDarkMode.AUTO -> appDark
                }
                val targetWebBg = when {
                    state.extendWebsiteTint && activeTab.themeColorArgb != null -> Color(activeTab.themeColorArgb)
                    wantDarkWebCss -> Color(0xFF141519)
                    else -> Color.White
                }
                val webBgColor by animateColorAsState(
                    targetValue = targetWebBg,
                    animationSpec = spring(dampingRatio = 0.90f, stiffness = 420f),
                    label = "WebThemeBg",
                )

                var edgeBackDragPx by remember { mutableFloatStateOf(0f) }
                var edgeForwardDragPx by remember { mutableFloatStateOf(0f) }
                var startPageScrollFraction by remember(activeTab.id) { mutableFloatStateOf(0f) }

                // Live Page Host: NEVER scaled or shape-clipped, keeping Chromium HW layers 100% glitch-free.
                val pageModifier = Modifier
                    .fillMaxSize()
                    .zIndex(if (isLivePageLayerVisible) 0f else -2f)
                    .graphicsLayer {
                        val editP = addressEditProgress.value.coerceIn(0f, 1f)
                        alpha = if (isLivePageLayerVisible) 1f else 0f
                        val s = lerp(1f, 0.965f, editP)
                        scaleX = s
                        scaleY = s
                    }

                Box(
                    modifier = pageModifier
                        .liquidKitBackdrop(
                            backdrop = pageBackdrop,
                            enabled = isLivePageLayerVisible,
                            scale = 0.5f,
                            frozen = !pageBackdropEnabled,
                            minRefreshIntervalMillis = 32L,
                        ),
                ) {
                    if (activeTab.isStartPage) {
                        StartPage(
                            state = state,
                            onOpenUrl = { url -> state.navigateActiveTab(url) },
                            onOpenUrlInNewTab = { url ->
                                state.createTab(groupId = state.activeGroup.id, url = url, select = true, insertAfterActive = true)
                            },
                            onOpenReadingList = {
                                state.libraryInitialTab = LibraryTab.READING_LIST
                                state.showLibrarySheet = true
                            },
                            onOpenPrivacyReport = {
                                state.showPrivacyReportSheet = true
                            },
                            onOpenSettings = {
                                state.showSettingsSheet = true
                            },
                            topExtraPadding = topBarExtra,
                            layoutProfile = layoutProfile,
                            onScrollFractionChange = { startPageScrollFraction = it },
                        )
                    } else {
                        val pullThresholdPx = with(density) { 72.dp.toPx() }
                        val webBottomSafeDp = dockBottomClearanceDp.roundToInt().coerceAtLeast(0)
                        val showPullSpinner by remember(activeTab.id) {
                            derivedStateOf {
                                isPullRefreshing || (if (isTopDragging) dragPullRefreshPx else pullRefreshAnim.value) > 4f
                            }
                        }
                        val cachedWarm = remember(activeTab.id) { pool.isTabLoadedAndAttached(activeTab.id) }
                        if (cachedWarm && isLivePageLayerVisible && settledWebViewTabId != activeTab.id) {
                            settledWebViewTabId = activeTab.id
                        }
                        val veilAnim = remember(activeTab.id) {
                            Animatable(if (settledWebViewTabId == activeTab.id || cachedWarm) 0f else 1f)
                        }
                        val keepTransitionVeil by remember(activeTab.id) {
                            derivedStateOf {
                                val hasThumb = state.thumbnails[activeTab.id] != null
                                settledWebViewTabId != activeTab.id &&
                                    (!pool.isTabLoadedAndAttached(activeTab.id) || hasThumb)
                            }
                        }
                        LaunchedEffect(activeTab.id, isWebContentVisible) {
                            if (!isWebContentVisible) {
                                settledWebViewTabId = null
                            } else if (settledWebViewTabId != activeTab.id) {
                                delay(if (pool.isTabLoadedAndAttached(activeTab.id)) 48L else 380L)
                                if (settledWebViewTabId != activeTab.id) {
                                    settledWebViewTabId = activeTab.id
                                }
                            }
                        }
                        LaunchedEffect(keepTransitionVeil, activeTab.id) {
                            if (keepTransitionVeil) {
                                veilAnim.snapTo(1f)
                            } else {
                                veilAnim.animateTo(0f, tween(90, easing = FastOutSlowInEasing))
                            }
                        }
                        val showVeil by remember(activeTab.id) {
                            derivedStateOf { keepTransitionVeil || veilAnim.value > 0.01f }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(webBgColor),
                        ) {
                            TerraWebViewHost(
                                tab = activeTab,
                                pool = pool,
                                appDark = appDark,
                                topSafeInsetPx = topSafeInsetPx,
                                bottomSafeInsetDp = webBottomSafeDp,
                                isVisible = isWebContentVisible,
                                onScrollDelta = { dy, scrollY ->
                                    if (!state.isOverviewOpen && !state.isAddressEditing && !state.isToolbarHidden) {
                                        val nextMinimized = scrollTracker.onScroll(
                                            dyPx = dy,
                                            scrollYPx = scrollY,
                                            density = density.density,
                                            currentlyMinimized = state.isToolbarMinimized,
                                        )
                                        if (nextMinimized != state.isToolbarMinimized) {
                                            state.isToolbarMinimized = nextMinimized
                                        }
                                    }
                                },
                                onTopInteraction = { safePx, pullPx, dragging ->
                                    if (dragging) {
                                        topInteractionJob?.cancel()
                                        topInteractionJob = null
                                        dragTopSafeOffsetPx = safePx
                                        dragPullRefreshPx = pullPx
                                        isTopDragging = true
                                    } else {
                                        val wasDragging = isTopDragging
                                        val fromSafe = if (wasDragging) dragTopSafeOffsetPx else topSafeOffsetAnim.value
                                        val fromPull = if (wasDragging) dragPullRefreshPx else pullRefreshAnim.value
                                        dragTopSafeOffsetPx = safePx
                                        dragPullRefreshPx = pullPx
                                        isTopDragging = false
                                        if (!wasDragging &&
                                            abs(topSafeOffsetAnim.value - safePx) <= 0.5f &&
                                            abs(pullRefreshAnim.value - pullPx) <= 0.5f &&
                                            topInteractionJob?.isActive != true
                                        ) {
                                            return@TerraWebViewHost
                                        }
                                        topInteractionJob?.cancel()
                                        topInteractionJob = scope.launch {
                                            coroutineScope {
                                                launch {
                                                    if (abs(topSafeOffsetAnim.value - fromSafe) > 0.5f) {
                                                        topSafeOffsetAnim.snapTo(fromSafe)
                                                    }
                                                    if (abs(topSafeOffsetAnim.value - safePx) > 0.5f) {
                                                        topSafeOffsetAnim.animateTo(safePx, LiquidKitSpring.swiftUI(0.26f, 0.90f).spec)
                                                    }
                                                }
                                                launch {
                                                    if (abs(pullRefreshAnim.value - fromPull) > 0.5f) {
                                                        pullRefreshAnim.snapTo(fromPull)
                                                    }
                                                    if (abs(pullRefreshAnim.value - pullPx) > 0.5f) {
                                                        pullRefreshAnim.animateTo(pullPx, LiquidKitSpring.swiftUI(0.30f, 0.84f).spec)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                },
                                onPullRefreshTrigger = {
                                    triggerHaptic(LiquidKitHaptic.ThresholdDetent)
                                    isPullRefreshing = true
                                    pool.reload(activeTab.id)
                                },
                                visualPullPxProvider = {
                                    if (isTopDragging) dragPullRefreshPx else pullRefreshAnim.value
                                },
                                onVisualFrameReady = { readyId ->
                                    if (readyId == activeTab.id) {
                                        settledWebViewTabId = readyId
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        val safeY = if (isTopDragging) dragTopSafeOffsetPx else topSafeOffsetAnim.value
                                        val pullY = if (isTopDragging) dragPullRefreshPx else pullRefreshAnim.value
                                        translationY = safeY + pullY + refreshHoldAnim.value
                                    },
                            )

                            if (showVeil) {
                                val veilMeta = pool.snapshotMeta(activeTab.id)
                                val veilTopOffset = veilMeta?.takeIf { it.fromPixelCopy }?.capturedTopInsetPx
                                    ?: if (isTopDragging) dragTopSafeOffsetPx else topSafeOffsetAnim.value
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer { alpha = if (keepTransitionVeil) 1f else veilAnim.value.coerceIn(0f, 1f) },
                                ) {
                                    AdjacentTabCardPreview(
                                        state = state,
                                        peekTab = activeTab,
                                        appDark = appDark,
                                        colors = colors,
                                        topExtraPadding = topBarExtra,
                                        layoutProfile = layoutProfile,
                                        topSafeOffsetPx = veilTopOffset,
                                        snapMeta = veilMeta,
                                    )
                                }
                            }

                            // Pull-to-Refresh Spinner revealed smoothly below the status bar
                            if (showPullSpinner) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopCenter)
                                        .padding(top = statusBarTopDp + topBarExtra + 10.dp)
                                        .graphicsLayer {
                                            val pullPx = if (isTopDragging) dragPullRefreshPx else pullRefreshAnim.value
                                            alpha = if (isPullRefreshing) 1f else (pullPx / pullThresholdPx).coerceIn(0f, 1f)
                                        },
                                ) {
                                    SafariPullRefreshSpinner(
                                        progressProvider = {
                                            if (isPullRefreshing) 1f
                                            else {
                                                val pullPx = if (isTopDragging) dragPullRefreshPx else pullRefreshAnim.value
                                                (pullPx / pullThresholdPx).coerceIn(0f, 1f)
                                            }
                                        },
                                        spinning = isPullRefreshing,
                                        color = colors.secondary,
                                    )
                                }
                            }
                        }

                        // Left Edge Swipe Back Zone
                        if (!state.isOverviewOpen && !state.isAddressEditing && activeTab.canGoBack) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterStart)
                                    .size(width = 22.dp, height = 380.dp)
                                    .pointerInput(activeTab.id) {
                                        detectHorizontalDragGestures(
                                            onDragStart = { edgeBackDragPx = 0f },
                                            onHorizontalDrag = { change, amount ->
                                                change.consume()
                                                edgeBackDragPx = (edgeBackDragPx + amount).coerceIn(0f, 180f)
                                            },
                                            onDragEnd = {
                                                if (edgeBackDragPx > 85f) pool.goBack(activeTab.id)
                                                edgeBackDragPx = 0f
                                            },
                                            onDragCancel = { edgeBackDragPx = 0f },
                                        )
                                    },
                            )
                        }

                        // Right Edge Swipe Forward Zone
                        if (!state.isOverviewOpen && !state.isAddressEditing && activeTab.canGoForward) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .size(width = 22.dp, height = 380.dp)
                                    .pointerInput(activeTab.id) {
                                        detectHorizontalDragGestures(
                                            onDragStart = { edgeForwardDragPx = 0f },
                                            onHorizontalDrag = { change, amount ->
                                                change.consume()
                                                edgeForwardDragPx = (edgeForwardDragPx - amount).coerceIn(0f, 180f)
                                            },
                                            onDragEnd = {
                                                if (edgeForwardDragPx > 85f) pool.goForward(activeTab.id)
                                                edgeForwardDragPx = 0f
                                            },
                                            onDragCancel = { edgeForwardDragPx = 0f },
                                        )
                                    },
                            )
                        }

                        // Elastic Liquid Glass edge chevron indicators
                        if (edgeBackDragPx > 6f) {
                            val p = (edgeBackDragPx / 100f).coerceIn(0f, 1f)
                            LiquidKitGlassSurface(
                                backdrop = pageBackdrop,
                                shape = CircleShape,
                                modifier = Modifier
                                    .align(Alignment.CenterStart)
                                    .offset(x = lerp(-24f, 16f, p).dp)
                                    .size(44.dp)
                                    .graphicsLayer { alpha = p; scaleX = 0.7f + 0.3f * p; scaleY = scaleX },
                            ) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    TerraSymbolIcon(symbol = LiquidKitSymbol.Back, tint = colors.ink, modifier = Modifier.size(20.dp))
                                }
                            }
                        }

                        if (edgeForwardDragPx > 6f) {
                            val p = (edgeForwardDragPx / 100f).coerceIn(0f, 1f)
                            LiquidKitGlassSurface(
                                backdrop = pageBackdrop,
                                shape = CircleShape,
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .offset(x = lerp(24f, -16f, p).dp)
                                    .size(44.dp)
                                    .graphicsLayer { alpha = p; scaleX = 0.7f + 0.3f * p; scaleY = scaleX },
                            ) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    TerraSymbolIcon(symbol = LiquidKitSymbol.Forward, tint = colors.ink, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }

                // Top progressive blur veil: 0 alpha when empty safe area is at rest, feathered seamlessly on scroll
                TopProgressiveVeilHost(
                    activeTabIsStartPage = activeTab.isStartPage,
                    startPageScrollFraction = { startPageScrollFraction },
                    topSafeInsetPx = topSafeInsetPx,
                    effectiveTopSafeOffsetPx = { if (isTopDragging) dragTopSafeOffsetPx else topSafeOffsetAnim.value },
                    overviewProgress = overviewProgress,
                    curDragX = { if (isBarDragging) liveDragX else settleDragX.value },
                    addressEditProgress = addressEditProgress,
                    layoutProfile = layoutProfile,
                    statusBarTopDp = statusBarTopDp,
                    topBarExtra = topBarExtra,
                    tabBarMode = state.tabBarMode,
                    isToolbarHidden = state.isToolbarHidden,
                    isDark = appDark || (state.activeGroup.isPrivate && activeTab.isStartPage),
                    pageBackdrop = pageBackdrop,
                    modifier = Modifier.align(Alignment.TopCenter).zIndex(1f),
                )

                // Pure-GPU Hero Transition Overlay (100% decouples animated zoom/clip/corner from Chromium Surface)
                if (isHeroMorphActive) {
                    val shapeCache = remember { HashMap<Int, RoundedCornerShape>() }
                    fun cachedShape(dpInt: Int): RoundedCornerShape =
                        shapeCache.getOrPut(dpInt) { RoundedCornerShape(dpInt.dp) }

                    val fullRect = Rect(0f, 0f, rootWidthPx, rootHeightPx)
                    val curX = if (isBarDragging) liveDragX else settleDragX.value
                    val curY = if (isBarDragging) liveDragY else settleDragY.value
                    val pOverview = overviewProgress.value.coerceIn(0f, 1f)
                    val dragGeo = if (activeGesture) {
                        computeDragLiftGeometry(curX, curY, rootWidthPx, rootHeightPx, swipeGapPx, overviewCardAspectRatio)
                    } else {
                        null
                    }

                    if (activeGesture && dragGeo != null) {
                        val liftP = (-curY / (rootHeightPx * 0.50f)).coerceIn(0f, 1f)
                        val dragPreview = if (liftP > 0.03f) ((liftP - 0.03f) / 0.62f).coerceIn(0f, 0.82f) else 0f
                        val ambientAlpha = (1f - dragPreview).coerceIn(0f, 1f)
                        if (ambientAlpha > 0.01f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .zIndex(1.4f)
                                    .graphicsLayer { alpha = ambientAlpha }
                                    .background(if (appDark) Color(0xFF101218) else Color(0xFFDCE3F0)),
                            ) {
                                val ambientThumb = state.thumbnails[activeTab.id]
                                if (ambientThumb != null) {
                                    Image(
                                        bitmap = ambientThumb,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .graphicsLayer {
                                                alpha = if (appDark) 0.42f else 0.36f
                                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                                    val r = 46.dp.toPx()
                                                    renderEffect = BlurEffect(r, r, TileMode.Clamp)
                                                }
                                            },
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            if (appDark) Color(0xFF0A0C12).copy(alpha = 0.48f)
                                            else Color(0xFFE7ECF6).copy(alpha = 0.45f),
                                        ),
                                )
                            }
                        }

                        val adjStep = dragGeo.rect.width + swipeGapPx
                        val adjScale = (dragGeo.rect.width / rootWidthPx).coerceIn(0.05f, 1.2f)
                        listOf(-1, +1).forEach { delta ->
                            val peekTab = state.peekAdjacentTab(delta)
                            val showNewTabCard = peekTab == null && delta == +1 && canSwipeToNewTab
                            if (peekTab != null || showNewTabCard) {
                                val adjAlpha = (1f - ((liftP - 0.14f) / 0.28f).coerceIn(0f, 1f))
                                if (adjAlpha > 0.01f) {
                                    val adjSnapMeta = peekTab?.let { pool.snapshotMeta(it.id) }
                                    val adjSafeOffPx = when {
                                        peekTab == null || peekTab.isStartPage -> 0f
                                        adjSnapMeta?.fromPixelCopy == true -> adjSnapMeta.capturedTopInsetPx * (1f - liftP) * adjScale
                                        else -> pool.currentTopSafeOffsetPx(peekTab.id, topSafeInsetPx) * (1f - liftP) * adjScale
                                    }
                                    Box(
                                        modifier = Modifier
                                            .zIndex(1.5f)
                                            .layout { measurable, _ ->
                                                val w = dragGeo.rect.width.roundToInt().coerceAtLeast(1)
                                                val h = dragGeo.rect.height.roundToInt().coerceAtLeast(1)
                                                val placeable = measurable.measure(
                                                    androidx.compose.ui.unit.Constraints.fixed(w, h),
                                                )
                                                layout(w, h) {
                                                    placeable.placeRelative(0, 0)
                                                }
                                            }
                                            .graphicsLayer {
                                                translationX = dragGeo.rect.left + delta * adjStep
                                                translationY = dragGeo.rect.top
                                                alpha = adjAlpha
                                                if (dragGeo.cornerDp > 0.5f) {
                                                    clip = true
                                                    shape = cachedShape(dragGeo.cornerDp.roundToInt())
                                                }
                                            }
                                            .background(if (appDark) Color(0xFF14171F) else Color(0xFFEEF1F7)),
                                    ) {
                                        AdjacentTabCardPreview(
                                            state = state,
                                            peekTab = peekTab,
                                            appDark = appDark,
                                            colors = colors,
                                            topExtraPadding = topBarExtra,
                                            layoutProfile = layoutProfile,
                                            topSafeOffsetPx = adjSafeOffPx,
                                            snapMeta = adjSnapMeta,
                                            rootWidthPx = rootWidthPx,
                                            rootHeightPx = rootHeightPx,
                                            statusBarTopPx = statusBarTopPx,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    val targetRect = morphTargetRectOverride
                        ?: cardBoundsMap[activeTab.id]
                        ?: computeExpectedOverviewCardRect(
                            state = state,
                            tabId = activeTab.id,
                            layoutProfile = layoutProfile,
                            rootWidthPx = rootWidthPx,
                            rootHeightPx = rootHeightPx,
                            statusBarTopPx = topSafePx,
                            densityPx = density.density,
                            cutoutStartPx = cutoutStartPx,
                            cutoutEndPx = cutoutEndPx,
                        )
                    val curReleaseStart = releaseStartRect
                    val curClosingOverride = closingOverrideRect

                    val heroRect: Rect
                    val heroCornerDp: Float
                    val flightProgress: Float
                    when {
                        dragGeo != null -> {
                            heroRect = dragGeo.rect
                            heroCornerDp = dragGeo.cornerDp
                            flightProgress = (-curY / (rootHeightPx * 0.50f)).coerceIn(0f, 1f)
                        }
                        curReleaseStart != null -> {
                            val t = ((pOverview - releaseStartP) / (1f - releaseStartP).coerceAtLeast(0.01f)).coerceIn(0f, 1f)
                            heroRect = androidx.compose.ui.geometry.lerp(curReleaseStart, targetRect, t)
                            heroCornerDp = lerp(releaseStartCornerDp, 18f, t)
                            flightProgress = pOverview
                        }
                        curClosingOverride != null -> {
                            val t = (pOverview / closingOverrideP.coerceAtLeast(0.01f)).coerceIn(0f, 1f)
                            heroRect = lerpHeroRect(fullRect, curClosingOverride, t)
                            heroCornerDp = if (t < 0.04f) lerp(0f, 34f, t / 0.04f) else lerp(34f, closingOverrideCornerDp, (t - 0.04f) / 0.96f)
                            flightProgress = t
                        }
                        else -> {
                            heroRect = lerpHeroRect(fullRect, targetRect, pOverview)
                            heroCornerDp = if (pOverview < 0.04f) lerp(0f, 34f, pOverview / 0.04f) else lerp(34f, 18f, (pOverview - 0.04f) / 0.96f)
                            flightProgress = pOverview
                        }
                    }

                    val cardW = heroRect.width.coerceAtLeast(1f)
                    val cardH = heroRect.height.coerceAtLeast(1f)
                    val cardShape = cachedShape(heroCornerDp.roundToInt().coerceAtLeast(0))
                    val elevDp = (sin(flightProgress.coerceIn(0f, 1f) * PI).toFloat() * 18f).dp
                    val safeOffPx = if (isTopDragging) dragTopSafeOffsetPx else topSafeOffsetAnim.value

                    Box(
                        modifier = Modifier
                            .zIndex(2f)
                            .layout { measurable, _ ->
                                val w = cardW.roundToInt().coerceAtLeast(1)
                                val h = cardH.roundToInt().coerceAtLeast(1)
                                val placeable = measurable.measure(
                                    androidx.compose.ui.unit.Constraints.fixed(w, h),
                                )
                                layout(w, h) {
                                    placeable.placeRelative(0, 0)
                                }
                            }
                            .graphicsLayer {
                                translationX = heroRect.left
                                translationY = heroRect.top
                            }
                            .shadow(elevation = elevDp, shape = cardShape, clip = false)
                            .clip(cardShape),
                    ) {
                        if (activeTab.isStartPage) {
                            val spTopBg = when {
                                state.activeGroup.isPrivate -> Color(0xFF1A1B1F)
                                appDark -> Color(0xFF141820)
                                else -> Color(0xFFF3F5FA)
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(spTopBg)
                                    .layout { measurable, constraints ->
                                        val rw = rootWidthPx.roundToInt().coerceAtLeast(1)
                                        val rh = rootHeightPx.roundToInt().coerceAtLeast(1)
                                        val placeable = measurable.measure(
                                            androidx.compose.ui.unit.Constraints.fixed(rw, rh),
                                        )
                                        layout(constraints.maxWidth, constraints.maxHeight) {
                                            placeable.placeRelative(0, 0)
                                        }
                                    }
                                    .graphicsLayer {
                                        val s = (cardW / rootWidthPx).coerceIn(0.05f, 1.2f)
                                        transformOrigin = TransformOrigin(0f, 0f)
                                        scaleX = s
                                        scaleY = s
                                        translationY = lerp(statusBarTopPx, 0f, flightProgress) * s
                                    },
                            ) {
                                StartPage(
                                    state = state,
                                    onOpenUrl = {},
                                    onOpenUrlInNewTab = {},
                                    onOpenReadingList = {},
                                    onOpenPrivacyReport = {},
                                    onOpenSettings = {},
                                    topExtraPadding = topBarExtra,
                                    layoutProfile = layoutProfile,
                                    isPreview = true,
                                    isPrivateOverride = state.activeGroup.isPrivate,
                                )
                            }
                        } else {
                            val thumb = state.thumbnails[activeTab.id]
                            val snapMeta = pool.snapshotMeta(activeTab.id)
                            if (thumb != null) {
                                val topFillColor = snapMeta?.topEdgeColorArgb?.let(::Color) ?: webBgColor
                                val botFillColor = snapMeta?.bottomEdgeColorArgb?.let(::Color) ?: webBgColor
                                val startTopInset = snapMeta?.capturedTopInsetPx ?: safeOffPx
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .drawBehind {
                                            val midY = size.height * 0.5f
                                            drawRect(topFillColor, size = Size(size.width, midY))
                                            drawRect(botFillColor, topLeft = Offset(0f, midY), size = Size(size.width, size.height - midY))
                                        },
                                ) {
                                    Image(
                                        bitmap = thumb,
                                        contentDescription = activeTab.title,
                                        alignment = Alignment.TopCenter,
                                        contentScale = ContentScale.FillWidth,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .graphicsLayer {
                                                val s = (cardW / rootWidthPx).coerceIn(0.05f, 1.2f)
                                                translationY = lerp(startTopInset, 0f, flightProgress) * s
                                            },
                                    )
                                }
                            } else {
                                AdjacentTabCardPreview(
                                    state = state,
                                    peekTab = activeTab,
                                    appDark = appDark,
                                    colors = colors,
                                    topExtraPadding = topBarExtra,
                                    layoutProfile = layoutProfile,
                                    snapMeta = snapMeta,
                                )
                            }
                        }

                        if (!activeGesture || flightProgress > 0.65f) {
                            val badgeAlpha = ((flightProgress - 0.65f) / 0.35f).coerceIn(0f, 1f)
                            if (badgeAlpha > 0.01f) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(2.dp)
                                        .size(36.dp)
                                        .graphicsLayer { alpha = badgeAlpha },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(26.dp)
                                            .clip(CircleShape)
                                            .background(Color.Black.copy(alpha = 0.48f)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        TerraSymbolIcon(
                                            symbol = LiquidKitSymbol.Close,
                                            tint = Color.White,
                                            modifier = Modifier.size(12.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            val commitThreshold = rootWidthPx * 0.22f
            val swipeStepTargetPx = swipeStepPx
            val edgeBouncePx = with(density) { 68.dp.toPx() }
            val maxSwipeVel = rootWidthPx * 4.5f
            val swipeCommitSpring = remember { LiquidKitSpring.swiftUI(0.26f, 1.0f) }
            val swipeCancelSpring = remember { LiquidKitSpring.swiftUI(0.30f, 0.86f) }
            val handleBarDragStart: () -> Unit = {
                barGestureJob?.cancel()
                barGestureJob = null
                if (!activeTab.isStartPage) {
                    pool.ensureSnapshotForTransition(activeTab.id)
                }
                if (isSettlingBarDrag) {
                    liveDragX = settleDragX.value
                    rawDragX = settleDragX.value
                    liveDragY = settleDragY.value
                    isSettlingBarDrag = false
                } else {
                    liveDragX = 0f
                    rawDragX = 0f
                    liveDragY = 0f
                }
                isBarDragging = true
            }
            val handleBarDragDelta: (Float, Float) -> Unit = { dx, dy ->
                if (!isBarDragging) {
                    barGestureJob?.cancel()
                    barGestureJob = null
                    if (isSettlingBarDrag) {
                        liveDragX = settleDragX.value
                        rawDragX = settleDragX.value
                        liveDragY = settleDragY.value
                        isSettlingBarDrag = false
                    }
                    isBarDragging = true
                }
                val hasNext = state.peekAdjacentTab(+1) != null || canSwipeToNewTab
                val hasPrev = state.peekAdjacentTab(-1) != null
                val minValid = if (hasNext) -swipeStepTargetPx else 0f
                val maxValid = if (hasPrev) swipeStepTargetPx else 0f
                rawDragX += dx
                liveDragX = when {
                    rawDragX < minValid -> minValid + rubberBand(rawDragX - minValid, edgeBouncePx)
                    rawDragX > maxValid -> maxValid + rubberBand(rawDragX - maxValid, edgeBouncePx)
                    else -> rawDragX
                }
                liveDragY = (liveDragY + dy).coerceAtMost(0f)
            }
            val handleBarDragEnd: (Float, Float, Boolean) -> Unit = { vx, vy, allowOverview ->
                val endX = liveDragX
                val endY = liveDragY
                val liftP = if (allowOverview) (-endY / (rootHeightPx * 0.50f)).coerceIn(0f, 1f) else 0f
                val dragOverviewPreview = if (liftP > 0.03f) ((liftP - 0.03f) / 0.62f).coerceIn(0f, 0.82f) else 0f
                val clampedVx = vx.coerceIn(-maxSwipeVel, maxSwipeVel)

                barGestureJob?.cancel()
                barGestureJob = scope.launch {
                    when {
                        allowOverview && (liftP >= 0.18f || (vy <= -420f && abs(vy) > abs(vx) * 0.85f)) -> {
                            val releaseGeo = computeDragLiftGeometry(endX, endY, rootWidthPx, rootHeightPx, swipeGapPx, overviewCardAspectRatio)
                            closingOverrideRect = null
                            morphTargetRectOverride = cardBoundsMap[activeTab.id] ?: computeExpectedOverviewCardRect(
                                state = state,
                                tabId = activeTab.id,
                                layoutProfile = layoutProfile,
                                rootWidthPx = rootWidthPx,
                                rootHeightPx = rootHeightPx,
                                statusBarTopPx = topSafePx,
                                densityPx = density.density,
                                cutoutStartPx = cutoutStartPx,
                                cutoutEndPx = cutoutEndPx,
                            )
                            releaseStartRect = releaseGeo.rect
                            releaseStartCornerDp = releaseGeo.cornerDp
                            releaseStartP = dragOverviewPreview
                            pendingReleaseVelocity = (-vy / (rootHeightPx * 0.45f)).coerceIn(0f, 5.5f)
                            overviewProgress.snapTo(dragOverviewPreview)
                            isBarDragging = false
                            isSettlingBarDrag = false
                            liveDragX = 0f
                            rawDragX = 0f
                            liveDragY = 0f
                            settleDragX.snapTo(0f)
                            settleDragY.snapTo(0f)
                            if (!activeTab.isStartPage) {
                                pool.ensureSnapshotForTransition(activeTab.id)
                            }
                            state.isOverviewOpen = true
                        }
                        liftP < 0.16f && (endX < -commitThreshold || vx < -480f) && (state.peekAdjacentTab(+1) != null || canSwipeToNewTab) -> {
                            settleDragX.snapTo(endX)
                            settleDragY.snapTo(endY)
                            isBarDragging = false
                            isSettlingBarDrag = true
                            try {
                                coroutineScope {
                                    launch { settleDragX.animateTo(-swipeStepTargetPx, swipeCommitSpring.spec, initialVelocity = clampedVx) }
                                    launch { settleDragY.animateTo(0f, swipeCommitSpring.spec, initialVelocity = vy) }
                                }
                                if (isActive) {
                                    if (state.peekAdjacentTab(+1) != null) {
                                        state.switchAdjacentTab(+1)
                                    } else {
                                        state.createTab(groupId = state.activeGroup.id, url = "", select = true, insertAfterActive = true)
                                    }
                                    liveDragX = 0f
                                    rawDragX = 0f
                                    liveDragY = 0f
                                    settleDragX.snapTo(0f)
                                    settleDragY.snapTo(0f)
                                }
                            } finally {
                                if (isActive) {
                                    isSettlingBarDrag = false
                                }
                            }
                        }
                        liftP < 0.16f && (endX > commitThreshold || vx > 480f) && state.peekAdjacentTab(-1) != null -> {
                            settleDragX.snapTo(endX)
                            settleDragY.snapTo(endY)
                            isBarDragging = false
                            isSettlingBarDrag = true
                            try {
                                coroutineScope {
                                    launch { settleDragX.animateTo(swipeStepTargetPx, swipeCommitSpring.spec, initialVelocity = clampedVx) }
                                    launch { settleDragY.animateTo(0f, swipeCommitSpring.spec, initialVelocity = vy) }
                                }
                                if (isActive) {
                                    state.switchAdjacentTab(-1)
                                    liveDragX = 0f
                                    rawDragX = 0f
                                    liveDragY = 0f
                                    settleDragX.snapTo(0f)
                                    settleDragY.snapTo(0f)
                                }
                            } finally {
                                if (isActive) {
                                    isSettlingBarDrag = false
                                }
                            }
                        }
                        else -> {
                            settleDragX.snapTo(endX)
                            settleDragY.snapTo(endY)
                            isBarDragging = false
                            isSettlingBarDrag = true
                            try {
                                coroutineScope {
                                    launch { settleDragX.animateTo(0f, swipeCancelSpring.spec, initialVelocity = clampedVx) }
                                    launch { settleDragY.animateTo(0f, swipeCancelSpring.spec, initialVelocity = vy) }
                                }
                                if (isActive) {
                                    liveDragX = 0f
                                    rawDragX = 0f
                                    liveDragY = 0f
                                }
                            } finally {
                                if (isActive) {
                                    isSettlingBarDrag = false
                                }
                            }
                        }
                    }
                }
            }

            // 3. Adaptive Browser Chrome (Tablet Unified Top Chrome OR Phone Landscape/Portrait Bar)
            ChromeLayerHost(
                state = state,
                pool = pool,
                pageBackdrop = pageBackdrop,
                layoutProfile = layoutProfile,
                swipeStepPx = swipeStepPx,
                effectiveOverviewPProvider = effectiveOverviewPProvider,
                addressEditProgress = addressEditProgress,
                isBarDragging = { isBarDragging },
                isSettlingBarDrag = { isSettlingBarDrag },
                liveDragX = { liveDragX },
                settleDragX = settleDragX,
                onBarDragStart = handleBarDragStart,
                onBarDragDelta = handleBarDragDelta,
                onBarDragEnd = handleBarDragEnd,
                onBarTouchDown = {
                    if (!activeTab.isStartPage) {
                        pool.capturePixelCopy(activeTab.id, force = true)
                    }
                },
                onOpenOverview = {
                    val launchOverview = {
                        releaseStartRect = null
                        morphTargetRectOverride = cardBoundsMap[activeTab.id] ?: computeExpectedOverviewCardRect(
                            state = state,
                            tabId = activeTab.id,
                            layoutProfile = layoutProfile,
                            rootWidthPx = rootWidthPx,
                            rootHeightPx = rootHeightPx,
                            statusBarTopPx = topSafePx,
                            densityPx = density.density,
                            cutoutStartPx = cutoutStartPx,
                            cutoutEndPx = cutoutEndPx,
                        )
                        state.isOverviewOpen = true
                    }
                    if (!activeTab.isStartPage) {
                        pool.ensureSnapshotForTransition(activeTab.id, onReady = launchOverview)
                    } else {
                        launchOverview()
                    }
                },
                onOpenNewTab = {
                    state.createTab(groupId = state.activeGroup.id, url = "", select = true, insertAfterActive = true)
                },
            )

            // 4. Address / Search Editing Overlay (100% opaque / Tablet in-place popover)
            Box(Modifier.fillMaxSize().zIndex(10f)) {
                AddressOverlayHost(
                    state = state,
                    layoutProfile = layoutProfile,
                    addressEditProgress = addressEditProgress,
                )
            }

            // 4b. Safari Reader Mode Overlay
            val readerArticle = state.activeReaderArticle
            if (readerArticle != null && state.activeReaderTabId == activeTab.id && !state.isOverviewOpen) {
                Box(Modifier.fillMaxSize().zIndex(12f)) {
                    SafariReaderOverlay(
                        tab = activeTab,
                        article = readerArticle,
                        readerStyle = state.readerStyle,
                        appDark = appDark,
                        onUpdateStyle = { state.readerStyle = it },
                        onOpenUrl = { url -> state.navigateActiveTab(url) },
                        onClose = { state.closeReader() },
                    )
                }
            }

            // 5. All Safari Modals, Sheets & Web Context Menu
            Box(Modifier.fillMaxSize().zIndex(20f)) {
                SafariModalsHost(
                    state = state,
                    pool = pool,
                    backdrop = pageBackdrop,
                    onNavigateUrl = { url -> state.navigateActiveTab(url) },
                    onOpenInNewTab = { url ->
                        state.createTab(groupId = state.activeGroup.id, url = url, select = true, insertAfterActive = true)
                    },
                    onOpenInBackgroundTab = { url ->
                        state.createTab(groupId = state.activeGroup.id, url = url, select = false, insertAfterActive = true)
                    },
                )
            }

            // 6. Floating Liquid Glass Status Toast Banner
            Box(Modifier.fillMaxSize().zIndex(40f)) {
                LiquidKitToast(
                    visible = state.statusBannerMessage != null,
                    message = state.statusBannerMessage.orEmpty(),
                    symbol = LiquidKitSymbol.CheckCircleFilled,
                    tint = if (state.activeGroup.isPrivate && !appDark) Color(0xFF1F222A).copy(alpha = 0.84f) else Color.Unspecified,
                    autoDismissMillis = 1800L,
                    alignment = LiquidKitToastAlignment.Top,
                    respectSafeArea = true,
                    backdrop = pageBackdrop,
                    onDismiss = { state.statusBannerMessage = null },
                )
            }
                } // end weight(1f) viewport BoxWithConstraints
            } // end TabletSidebar Row
        }
    }
}

@Composable
private fun AdjacentTabCardPreview(
    state: TerraBrowserState,
    peekTab: BrowserTab?,
    appDark: Boolean,
    colors: io.liquidkit.LiquidKitColors,
    topExtraPadding: androidx.compose.ui.unit.Dp,
    layoutProfile: BrowserLayoutProfile = BrowserLayoutProfile.PHONE_PORTRAIT,
    topSafeOffsetPx: Float = 0f,
    snapMeta: TabSnapshotMeta? = null,
    rootWidthPx: Float = 0f,
    rootHeightPx: Float = 0f,
    statusBarTopPx: Float = 0f,
) {
    if (peekTab != null && peekTab.isStartPage && rootWidthPx > 1f && rootHeightPx > 1f) {
        val isPrivate = state.activeGroup.isPrivate
        val spTopBg = when {
            isPrivate -> Color(0xFF1A1B1F)
            appDark -> Color(0xFF141820)
            else -> Color(0xFFF3F5FA)
        }
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(spTopBg),
        ) {
            val density = LocalDensity.current
            val targetW = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
            val s = (targetW / rootWidthPx).coerceIn(0.05f, 1.2f)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .layout { measurable, constraints ->
                        val rw = rootWidthPx.roundToInt().coerceAtLeast(1)
                        val rh = rootHeightPx.roundToInt().coerceAtLeast(1)
                        val placeable = measurable.measure(
                            androidx.compose.ui.unit.Constraints.fixed(rw, rh),
                        )
                        layout(constraints.maxWidth, constraints.maxHeight) {
                            placeable.placeRelative(0, 0)
                        }
                    }
                    .graphicsLayer {
                        transformOrigin = TransformOrigin(0f, 0f)
                        scaleX = s
                        scaleY = s
                        translationY = statusBarTopPx * s
                    },
            ) {
                StartPage(
                    state = state,
                    onOpenUrl = {},
                    onOpenUrlInNewTab = {},
                    onOpenReadingList = {},
                    onOpenPrivacyReport = {},
                    onOpenSettings = {},
                    topExtraPadding = topExtraPadding,
                    layoutProfile = layoutProfile,
                    isPreview = true,
                    isPrivateOverride = isPrivate,
                )
            }
        }
    } else if (peekTab == null || peekTab.isStartPage) {
        val isPrivate = state.activeGroup.isPrivate
        val bg = when {
            isPrivate -> Color(0xFF141518)
            appDark -> Color(0xFF14171F)
            else -> Color(0xFFEEF1F7)
        }
        val title = if (peekTab == null) "新建标签页" else "起始页"
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bg),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(24.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(62.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            if (isPrivate || appDark) Color.White.copy(alpha = 0.08f)
                            else Color(0xFF0A84FF).copy(alpha = 0.12f),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    SafariCompassIcon(
                        color = if (isPrivate || appDark) Color.White.copy(alpha = 0.90f) else Color(0xFF0A84FF),
                        modifier = Modifier.size(30.dp),
                    )
                }
                BasicText(
                    text = title,
                    style = TextStyle(color = colors.ink, fontSize = 19.sp, fontWeight = FontWeight.SemiBold),
                )
            }
        }
    } else {
        val thumb = state.thumbnails[peekTab.id]
        if (thumb != null) {
            val peekDark = when (peekTab.adaptiveDarkMode) {
                SiteDarkMode.ON -> true
                SiteDarkMode.OFF -> false
                SiteDarkMode.AUTO -> appDark
            }
            val peekBgColor = when {
                state.extendWebsiteTint && peekTab.themeColorArgb != null -> Color(peekTab.themeColorArgb)
                peekDark -> Color(0xFF141519)
                else -> Color.White
            }
            val topFillColor = snapMeta?.topEdgeColorArgb?.let(::Color) ?: peekBgColor
            val botFillColor = snapMeta?.bottomEdgeColorArgb?.let(::Color) ?: peekBgColor
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind {
                        val midY = size.height * 0.5f
                        drawRect(topFillColor, size = Size(size.width, midY))
                        drawRect(botFillColor, topLeft = Offset(0f, midY), size = Size(size.width, size.height - midY))
                    },
            ) {
                Image(
                    bitmap = thumb,
                    contentDescription = peekTab.title,
                    alignment = Alignment.TopCenter,
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { translationY = topSafeOffsetPx },
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (appDark) Color(0xFF14171F) else Color(0xFFEEF1F7)),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(24.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(peekTab.accentColorArgb)),
                        contentAlignment = Alignment.Center,
                    ) {
                        BasicText(
                            text = peekTab.title.take(1).uppercase(),
                            style = TextStyle(color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold),
                        )
                    }
                    BasicText(
                        text = peekTab.title.ifBlank { peekTab.host },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(color = colors.ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
                    )
                    BasicText(
                        text = peekTab.host,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(color = colors.secondary, fontSize = 13.sp),
                    )
                }
            }
        }
    }
}

@Composable
private fun OverviewLayerHost(
    state: TerraBrowserState,
    pool: TerraWebViewPool,
    backdrop: io.liquidkit.LiquidKitLayerBackdrop,
    effectiveOverviewPProvider: () -> Float,
    isHeroMorphActive: () -> Boolean,
    cardBoundsMap: MutableMap<String, Rect>,
    layoutProfile: BrowserLayoutProfile,
    onCardPicked: (tabId: String, bounds: Rect?) -> Unit,
) {
    val hiddenCardTabIdProvider: () -> String? = {
        if (isHeroMorphActive()) state.activeTab.id else null
    }
    TabOverviewScreen(
        state = state,
        pool = pool,
        backdrop = backdrop,
        overviewProgressProvider = effectiveOverviewPProvider,
        cardBoundsMap = cardBoundsMap,
        onSelectTab = { picked ->
            onCardPicked(picked.id, cardBoundsMap[picked.id])
            state.selectTab(picked.id)
            state.isOverviewOpen = false
        },
        onCreateNewTab = { grpId ->
            state.createTab(groupId = grpId, url = "", select = true)
            state.isOverviewOpen = false
        },
        onCloseOverview = {
            state.isOverviewOpen = false
        },
        isBlurred = false,
        hiddenCardTabIdProvider = hiddenCardTabIdProvider,
        layoutProfile = layoutProfile,
    )
}

@Composable
private fun BoxScope.TopProgressiveVeilHost(
    activeTabIsStartPage: Boolean,
    startPageScrollFraction: () -> Float,
    topSafeInsetPx: Float,
    effectiveTopSafeOffsetPx: () -> Float,
    overviewProgress: Animatable<Float, AnimationVector1D>,
    curDragX: () -> Float,
    addressEditProgress: Animatable<Float, AnimationVector1D>,
    layoutProfile: BrowserLayoutProfile,
    statusBarTopDp: androidx.compose.ui.unit.Dp,
    topBarExtra: androidx.compose.ui.unit.Dp,
    tabBarMode: TabBarMode,
    isToolbarHidden: Boolean,
    isDark: Boolean,
    pageBackdrop: io.liquidkit.LiquidKitLayerBackdrop,
    modifier: Modifier = Modifier,
) {
    if (layoutProfile == BrowserLayoutProfile.TABLET_LANDSCAPE) return
    val baseColor = if (isDark) Color(0xFF0A0C10) else Color.White
    val tintBrush = remember(isDark) {
        Brush.verticalGradient(
            0.00f to baseColor.copy(alpha = if (isDark) 0.82f else 0.86f),
            0.45f to baseColor.copy(alpha = if (isDark) 0.56f else 0.60f),
            0.78f to baseColor.copy(alpha = if (isDark) 0.22f else 0.24f),
            1.00f to Color.Transparent,
        )
    }
    val topFadeMask = remember {
        Brush.verticalGradient(
            0.00f to Color.Black,
            0.52f to Color.Black.copy(alpha = 0.96f),
            0.78f to Color.Black.copy(alpha = 0.45f),
            1.00f to Color.Transparent,
        )
    }
    val alphaProvider = {
        val intrusionFraction = BrowserTopVeilCalculator.intrusionFraction(
            isStartPage = activeTabIsStartPage,
            startPageScrollFraction = startPageScrollFraction(),
            topSafeOffsetPx = effectiveTopSafeOffsetPx(),
            topSafeInsetPx = topSafeInsetPx,
        )
        val pOverview = overviewProgress.value.coerceIn(0f, 1f)
        val overviewFade = (1f - pOverview / 0.08f).coerceIn(0f, 1f)
        val dragFade = (1f - (abs(curDragX()) / 24f).coerceIn(0f, 1f))
        val dockVisibleAlpha = (1f - addressEditProgress.value * 1.6f).coerceIn(0f, 1f)
        (intrusionFraction * dockVisibleAlpha * overviewFade * dragFade).coerceIn(0f, 1f)
    }
    val featherHeight = if (tabBarMode == TabBarMode.TOP && !isToolbarHidden && layoutProfile != BrowserLayoutProfile.PHONE_LANDSCAPE) {
        statusBarTopDp + topBarExtra + 26.dp
    } else {
        statusBarTopDp + 34.dp
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(featherHeight)
            .layout { measurable, constraints ->
                val a = alphaProvider()
                if (a <= 0.01f) {
                    layout(0, 0) {}
                } else {
                    val placeable = measurable.measure(constraints)
                    layout(placeable.width, placeable.height) {
                        placeable.placeRelative(0, 0)
                    }
                }
            }
            .graphicsLayer {
                this.alpha = alphaProvider()
                compositingStrategy = CompositingStrategy.Offscreen
            }
            .drawWithContent {
                drawContent()
                drawRect(brush = topFadeMask, blendMode = BlendMode.DstIn)
            }
            .drawBackdrop(
                backdrop = pageBackdrop,
                shape = { RectangleShape },
                effects = { blur(14.dp.toPx(), TileMode.Clamp) },
                highlight = null,
                shadow = null,
                onDrawSurface = { drawRect(brush = tintBrush) },
            ),
    )
}

@Composable
private fun BoxScope.ChromeLayerHost(
    state: TerraBrowserState,
    pool: TerraWebViewPool,
    pageBackdrop: io.liquidkit.LiquidKitLayerBackdrop,
    layoutProfile: BrowserLayoutProfile,
    swipeStepPx: Float,
    effectiveOverviewPProvider: () -> Float,
    addressEditProgress: Animatable<Float, AnimationVector1D>,
    isBarDragging: () -> Boolean,
    isSettlingBarDrag: () -> Boolean,
    liveDragX: () -> Float,
    settleDragX: Animatable<Float, AnimationVector1D>,
    onBarDragStart: () -> Unit,
    onBarDragDelta: (Float, Float) -> Unit,
    onBarDragEnd: (Float, Float, Boolean) -> Unit,
    onBarTouchDown: () -> Unit = {},
    onOpenOverview: () -> Unit,
    onOpenNewTab: () -> Unit,
) {
    val activeGestureProvider: () -> Boolean = { isBarDragging() || isSettlingBarDrag() }
    val swipeFractionProvider: () -> Float = {
        val curDragX = if (isBarDragging()) liveDragX() else settleDragX.value
        (curDragX / swipeStepPx).coerceIn(-1.25f, 1.25f)
    }
    val editProgressProvider: () -> Float = { addressEditProgress.value.coerceIn(0f, 1f) }
    val topAlphaProvider: () -> Float = {
        val editP = editProgressProvider()
        val editAlpha = if (state.isAddressEditing) {
            (1f - editP * 1.6f).coerceIn(0f, 1f)
        } else {
            (1f - editP).coerceIn(0f, 1f)
        }
        val overviewFade = (1f - (effectiveOverviewPProvider() / 0.30f)).coerceIn(0f, 1f)
        editAlpha * overviewFade
    }
    val dockAlphaProvider: () -> Float = {
        val effectiveP = effectiveOverviewPProvider()
        val dockOverviewFade = (1f - (effectiveP / 0.30f)).coerceIn(0f, 1f)
        val editAlpha = if (state.isAddressEditing) {
            (1f - editProgressProvider() * 1.6f).coerceIn(0f, 1f)
        } else {
            (1f - editProgressProvider()).coerceIn(0f, 1f)
        }
        editAlpha * dockOverviewFade
    }

    if (layoutProfile == BrowserLayoutProfile.TABLET_LANDSCAPE) {
        if (effectiveOverviewPProvider() < 0.35f) {
            TabletTopChrome(
                state = state,
                pool = pool,
                backdrop = pageBackdrop,
                alphaFactorProvider = topAlphaProvider,
                tabSwipeFractionProvider = swipeFractionProvider,
                onBarDragStart = onBarDragStart,
                onBarDragDelta = { dx, _ -> onBarDragDelta(dx, 0f) },
                onBarDragEnd = { vx, _ -> onBarDragEnd(vx, 0f, false) },
                onOpenOverview = onOpenOverview,
                onOpenNewTab = onOpenNewTab,
                modifier = Modifier.align(Alignment.TopCenter).zIndex(5f),
            )
        }

        AnimatedVisibility(
            visible = state.findInPageVisible && !state.isOverviewOpen,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .zIndex(5f)
                .navigationBarsPadding()
                .displayCutoutPadding()
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .widthIn(max = 520.dp),
        ) {
            FindInPageGlassBar(state = state, pool = pool, backdrop = pageBackdrop)
        }
    } else {
        if (layoutProfile != BrowserLayoutProfile.PHONE_LANDSCAPE &&
            state.tabBarMode == TabBarMode.TOP &&
            effectiveOverviewPProvider() < 0.35f &&
            !state.isToolbarHidden
        ) {
            SafariTopAddressBar(
                state = state,
                pool = pool,
                backdrop = pageBackdrop,
                alphaFactorProvider = topAlphaProvider,
                tabSwipeFractionProvider = swipeFractionProvider,
                onBarDragStart = onBarDragStart,
                onBarDragDelta = { dx, _ -> onBarDragDelta(dx, 0f) },
                onBarDragEnd = { vx, _ -> onBarDragEnd(vx, 0f, false) },
                onBarTouchDown = onBarTouchDown,
                onOpenNewTab = onOpenNewTab,
                modifier = Modifier.align(Alignment.TopCenter).zIndex(5f),
            )
        }

        SafariBottomDock(
            state = state,
            pool = pool,
            backdrop = pageBackdrop,
            alphaFactorProvider = dockAlphaProvider,
            editProgressProvider = editProgressProvider,
            tabSwipeFractionProvider = swipeFractionProvider,
            isDraggingToOverviewProvider = activeGestureProvider,
            layoutProfile = layoutProfile,
            onBarDragStart = onBarDragStart,
            onBarDragDelta = onBarDragDelta,
            onBarDragEnd = { vx, vy -> onBarDragEnd(vx, vy, true) },
            onOpenOverview = onOpenOverview,
            onOpenNewTab = onOpenNewTab,
            modifier = Modifier.align(Alignment.BottomCenter).zIndex(5f),
            onBarTouchDown = onBarTouchDown,
        )
    }
}

@Composable
private fun AddressOverlayHost(
    state: TerraBrowserState,
    layoutProfile: BrowserLayoutProfile,
    addressEditProgress: Animatable<Float, AnimationVector1D>,
) {
    val editP = addressEditProgress.value
    if (state.isAddressEditing || editP > 0.01f) {
        AddressEditingOverlay(
            state = state,
            progress = editP,
            layoutProfile = layoutProfile,
            onSubmitInput = { input -> state.navigateActiveTab(input) },
            onSelectOpenTab = { tabId ->
                state.selectTab(tabId)
                state.isAddressEditing = false
            },
            onDismiss = { state.isAddressEditing = false },
        )
    }
}

@Composable
private fun SafariPullRefreshSpinner(
    progressProvider: () -> Float,
    spinning: Boolean,
    color: Color,
) {
    val transition = rememberInfiniteTransition(label = "SafariPullRefresh")
    val spinAngle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(820, easing = LinearEasing)),
        label = "SpinAngle",
    )
    val spokeCount = 12

    Canvas(modifier = Modifier.size(22.dp)) {
        val progress = progressProvider().coerceIn(0f, 1f)
        val activeSpokes = if (spinning) spokeCount else (progress * spokeCount).roundToInt().coerceIn(0, spokeCount)
        val baseRotation = if (spinning) spinAngle else 0f
        val cx = size.width * 0.5f
        val cy = size.height * 0.5f
        val rInner = size.minDimension * 0.23f
        val rOuter = size.minDimension * 0.46f
        val strokeW = size.minDimension * 0.11f

        for (i in 0 until activeSpokes) {
            val angleDeg = -90f + i * (360f / spokeCount) + baseRotation
            val rad = angleDeg * (PI / 180.0)
            val spokeAlpha = if (spinning) {
                val trailIdx = (spokeCount - 1 - i)
                (0.22f + 0.78f * (1f - trailIdx / spokeCount.toFloat()))
            } else {
                (0.35f + 0.65f * progress)
            }
            drawLine(
                color = color.copy(alpha = spokeAlpha),
                start = Offset(cx + (cos(rad) * rInner).toFloat(), cy + (sin(rad) * rInner).toFloat()),
                end = Offset(cx + (cos(rad) * rOuter).toFloat(), cy + (sin(rad) * rOuter).toFloat()),
                strokeWidth = strokeW,
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun TerraLiquidKitRoot(
    modifier: Modifier = Modifier,
    opacity: Float = 0.5f,
    dark: Boolean,
    edgeToEdgeSystemBars: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    CompositionLocalProvider(
        LocalLiquidKitSymbolRenderer provides { symbol, color, iconModifier ->
            TerraSymbolIcon(symbol = symbol, tint = color, modifier = iconModifier)
        },
    ) {
        LiquidKitRoot(
            modifier = modifier,
            opacity = opacity,
            dark = dark,
            edgeToEdgeSystemBars = edgeToEdgeSystemBars,
        ) {
            LiquidKitOverscrollScope(enabled = true) {
                Box(Modifier.fillMaxSize()) {
                    content()
                }
            }
        }
    }
}
