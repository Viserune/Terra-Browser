package com.terra.browser.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.terra.browser.model.BrowserTab
import com.terra.browser.model.BrowserUrlUtils
import com.terra.browser.model.LibraryTab
import com.terra.browser.state.TerraBrowserState
import com.terra.browser.webview.TerraWebViewPool
import io.liquidkit.LiquidKitGlassSurface
import io.liquidkit.LiquidKitHaptic
import io.liquidkit.LiquidKitMenuItem
import io.liquidkit.LiquidKitSegmentedPicker
import io.liquidkit.LiquidKitSpring
import io.liquidkit.LiquidKitSymbol
import io.liquidkit.liquidKitAppearance
import io.liquidkit.liquidKitColors
import io.liquidkit.liquidKitPopoverAnchor
import io.liquidkit.rememberLiquidKitHaptics
import io.liquidkit.rememberLiquidKitMenuState
import io.liquidkit.rememberLiquidKitPopoverAnchorState
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Unified iPadOS / unfolded foldable top chrome featuring:
 * 1) Left sidebar toggle + Back/Forward with long-press history menus, centered AddressPillSurface
 *    with HTTPS lock + Page Menu morph, and right Share / New Tab / Tab Overview controls.
 * 2) Optional scrollable horizontal Tab Bar Strip with favicons, inline close, and long-press menus.
 */
@Composable
fun TabletTopChrome(
    state: TerraBrowserState,
    pool: TerraWebViewPool,
    backdrop: Backdrop,
    alphaFactorProvider: () -> Float,
    tabSwipeFractionProvider: () -> Float,
    onBarDragStart: () -> Unit,
    onBarDragDelta: (dx: Float, dy: Float) -> Unit,
    onBarDragEnd: (velocityX: Float, velocityY: Float) -> Unit,
    onOpenOverview: () -> Unit,
    onOpenNewTab: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tab = state.activeTab
    val isDark = liquidKitAppearance().dark || state.activeGroup.isPrivate
    val colors = liquidKitColors(isDark)
    val clipboard = LocalClipboardManager.current
    val density = LocalDensity.current
    val triggerHaptic = rememberLiquidKitHaptics()
    val accentBlue = Color(0xFF0A84FF)

    val pageMenuItems by remember(state) {
        derivedStateOf { buildPageMenuItems(state, state.activeTab) }
    }
    val pageMenuState = rememberLiquidKitMenuState { picked ->
        handlePageMenuAction(
            key = picked.key,
            state = state,
            tab = state.activeTab,
            pool = pool,
            onOpenNewTab = onOpenNewTab,
        )
    }

    val addressLongPressItems by remember(state) {
        derivedStateOf { buildAddressLongPressMenuItems(state, state.activeTab) }
    }
    val addressLongPressMenuState = rememberLiquidKitMenuState { picked ->
        handleAddressLongPressAction(
            key = picked.key,
            state = state,
            tab = state.activeTab,
            pool = pool,
            clipboard = clipboard,
        )
    }

    val backHistoryItems = remember(tab.id, tab.url, tab.canGoBack, tab.isLoading) {
        if (!tab.canGoBack) {
            emptyList()
        } else {
            pool.getBackForwardItems(tab.id, forward = false).map { (steps, title, url) ->
                LiquidKitMenuItem(
                    key = "hist:$steps",
                    title = title,
                    subtitle = url.removePrefix("https://").removePrefix("http://"),
                    symbol = LiquidKitSymbol.Globe,
                )
            }
        }
    }
    val backHistoryMenuState = rememberLiquidKitMenuState { picked ->
        val currentTab = state.activeTab
        when {
            picked.key.startsWith("hist:") -> {
                val steps = picked.key.removePrefix("hist:").toIntOrNull() ?: return@rememberLiquidKitMenuState
                if (steps != 0) pool.goBackOrForward(currentTab.id, steps)
            }
            picked.key.startsWith("open_hist:") -> {
                val targetUrl = picked.key.removePrefix("open_hist:")
                if (targetUrl.isNotBlank()) state.navigateActiveTab(targetUrl)
            }
        }
    }

    val forwardHistoryItems = remember(tab.id, tab.url, tab.canGoForward, tab.isLoading) {
        if (!tab.canGoForward) {
            emptyList()
        } else {
            pool.getBackForwardItems(tab.id, forward = true).map { (steps, title, url) ->
                LiquidKitMenuItem(
                    key = "hist:$steps",
                    title = title,
                    subtitle = url.removePrefix("https://").removePrefix("http://"),
                    symbol = LiquidKitSymbol.Globe,
                )
            }
        }
    }
    val forwardHistoryMenuState = rememberLiquidKitMenuState { picked ->
        val steps = picked.key.removePrefix("hist:").toIntOrNull() ?: return@rememberLiquidKitMenuState
        if (steps != 0) pool.goBackOrForward(state.activeTab.id, steps)
    }

    val shareQuickMenuItems = remember(tab.id, tab.url, tab.isStartPage) {
        listOf(
            LiquidKitMenuItem("share_sheet", "共享…", symbol = LiquidKitSymbol.Share),
            LiquidKitMenuItem("copy_url", "拷贝链接", symbol = LiquidKitSymbol.Clipboard, enabled = !tab.isStartPage),
            LiquidKitMenuItem("add_favorite", "添加到个人收藏", symbol = LiquidKitSymbol.StarFilled, enabled = !tab.isStartPage),
            LiquidKitMenuItem("add_bookmark", "添加到书签", symbol = LiquidKitSymbol.Bookmark, enabled = !tab.isStartPage),
            LiquidKitMenuItem("add_reading", "添加到阅读列表", symbol = LiquidKitSymbol.Document, enabled = !tab.isStartPage),
        )
    }
    val shareQuickMenuState = rememberLiquidKitMenuState { picked ->
        val currentTab = state.activeTab
        when (picked.key) {
            "share_sheet" -> state.showShareSheet = true
            "copy_url" -> {
                clipboard.setText(AnnotatedString(currentTab.url.ifBlank { "https://terra.browser/start" }))
                state.showBanner("已拷贝链接")
            }
            "add_favorite" -> {
                state.addFavorite(currentTab.title, currentTab.url)
                state.showBanner("已添加到个人收藏")
            }
            "add_bookmark" -> {
                state.addBookmark(currentTab.title, currentTab.url)
                state.showBanner("已添加到书签")
            }
            "add_reading" -> {
                state.addReadingList(currentTab.title, currentTab.url)
                state.showBanner("已添加到阅读列表")
            }
        }
    }

    val newTabQuickMenuItems = remember {
        listOf(
            LiquidKitMenuItem("new_tab", "新建标签页", symbol = LiquidKitSymbol.Add),
            LiquidKitMenuItem("new_private_tab", "新建无痕浏览标签页", symbol = LiquidKitSymbol.Filter),
        )
    }
    val newTabQuickMenuState = rememberLiquidKitMenuState { picked ->
        when (picked.key) {
            "new_tab" -> onOpenNewTab()
            "new_private_tab" -> {
                val privateId = state.tabGroups.firstOrNull { it.isPrivate }?.id ?: "private"
                state.selectGroup(privateId)
                state.createTab(groupId = privateId, url = "", select = true)
            }
        }
    }

    val quickTabsMenuItems by remember(state) {
        derivedStateOf { buildQuickTabsMenuItems(state, state.activeTab) }
    }
    val quickTabsMenuState = rememberLiquidKitMenuState { picked ->
        val currentTab = state.activeTab
        when {
            picked.key == "new_tab" -> onOpenNewTab()
            picked.key == "new_private_tab" -> {
                val privateId = state.tabGroups.firstOrNull { it.isPrivate }?.id ?: "private"
                state.selectGroup(privateId)
                state.createTab(groupId = privateId, url = "", select = true)
            }
            picked.key == "copy_url" -> {
                clipboard.setText(AnnotatedString(currentTab.url.ifBlank { "https://terra.browser/start" }))
                state.showBanner("已拷贝链接")
            }
            picked.key == "close_current" -> {
                pool.evictTab(currentTab.id)
                state.closeTab(currentTab.id)
            }
            picked.key == "close_all" -> {
                state.tabsInGroup(state.activeGroup.id).forEach { pool.evictTab(it.id) }
                state.closeAllTabsInGroup(state.activeGroup.id)
            }
            picked.key.startsWith("switch_grp:") -> state.selectGroup(picked.key.removePrefix("switch_grp:"))
            picked.key.startsWith("move_grp:") -> {
                val targetId = picked.key.removePrefix("move_grp:")
                state.moveTabToGroup(currentTab.id, targetId)
                state.selectGroup(targetId)
            }
        }
    }

    val collapseState = animateFloatAsState(
        targetValue = if (state.isToolbarMinimized) 1f else 0f,
        animationSpec = LiquidKitSpring.swiftUI(0.32f, 0.86f).spec,
        label = "TabletCollapse",
    )
    val latestAlphaFactor by rememberUpdatedState(alphaFactorProvider)
    val latestSwipeFraction by rememberUpdatedState(tabSwipeFractionProvider)
    val showAdjacent by remember { derivedStateOf { abs(latestSwipeFraction()) > 0.004f } }
    val swipeDelta by remember { derivedStateOf { if (latestSwipeFraction() < 0f) +1 else -1 } }
    val sideInteractive by remember {
        derivedStateOf { (1f - collapseState.value * 1.5f) > 0.25f }
    }

    val barBg = when {
        state.activeGroup.isPrivate -> Color(0xFF1D1E24).copy(alpha = 0.88f)
        isDark -> Color(0xFF171920).copy(alpha = 0.88f)
        else -> Color(0xFFF6F7FB).copy(alpha = 0.90f)
    }
    val pillTint = if (state.activeGroup.isPrivate) {
        Color(0xFF23252C).copy(alpha = 0.56f)
    } else {
        Color.Unspecified
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = latestAlphaFactor().coerceIn(0f, 1f) },
        contentAlignment = Alignment.TopCenter,
    ) {
        if (state.isToolbarHidden) {
            LiquidKitGlassSurface(
                backdrop = backdrop,
                shape = RoundedCornerShape(20.dp),
                interactive = true,
                onClick = {
                    state.isToolbarHidden = false
                    state.isToolbarMinimized = false
                },
                modifier = Modifier
                    .statusBarsPadding()
                    .displayCutoutPadding()
                    .padding(top = 6.dp),
            ) {
                Box(Modifier.padding(horizontal = 16.dp, vertical = 7.dp)) {
                    BasicText(
                        text = tab.host.ifBlank { "显示工具栏" },
                        style = TextStyle(color = colors.ink, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(barBg)
                    .statusBarsPadding()
                    .displayCutoutPadding(),
            ) {
                val rowFullPx = with(density) { 52.dp.toPx() }
                val rowMiniPx = with(density) { 40.dp.toPx() }
                val pillFullPx = with(density) { 40.dp.toPx() }
                val pillMiniPx = with(density) { 32.dp.toPx() }
                val gap12Px = with(density) { 12.dp.toPx() }
                val tabletPillAnchor = rememberLiquidKitPopoverAnchorState()

                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .layout { measurable, constraints ->
                            val c = collapseState.value.coerceIn(0f, 1f)
                            val hPx = lerp(rowFullPx, rowMiniPx, c).roundToInt().coerceAtLeast(1)
                            val placeable = measurable.measure(constraints.copy(minHeight = hPx, maxHeight = hPx))
                            layout(placeable.width, hPx) { placeable.placeRelative(0, 0) }
                        }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    val leftClusterWidthDp = 114.dp
                    val rightClusterWidthDp = 118.dp
                    val centerGapDp = 20.dp
                    val safeMaxDp = (maxWidth - 24.dp).coerceAtLeast(1.dp)
                    val expandedCenterMaxDp = (maxWidth - leftClusterWidthDp - rightClusterWidthDp - centerGapDp)
                        .coerceAtMost(520.dp)
                        .coerceAtLeast(minOf(180.dp, safeMaxDp))
                    val expandedCenterWidthPx = with(density) { expandedCenterMaxDp.toPx() }
                    val miniCenterWidthPx = with(density) { 260.dp.coerceAtMost(safeMaxDp).toPx() }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .layout { measurable, constraints ->
                                val sideAlpha = (1f - collapseState.value * 1.5f).coerceIn(0f, 1f)
                                if (sideAlpha <= 0.005f) {
                                    layout(0, 0) {}
                                } else {
                                    val placeable = measurable.measure(constraints)
                                    layout(placeable.width, placeable.height) {
                                        placeable.placeRelative(0, 0)
                                    }
                                }
                            }
                            .graphicsLayer {
                                alpha = (1f - collapseState.value * 1.5f).coerceIn(0f, 1f)
                            },
                    ) {
                        var sidebarPressed by remember { mutableStateOf(false) }
                        val sidebarScaleState = animateFloatAsState(
                            targetValue = if (sidebarPressed) 0.90f else 1f,
                            animationSpec = LiquidKitSpring.swiftUI(0.20f, 0.82f).spec,
                            label = "SidebarBtnScale",
                        )
                        Box(
                            modifier = Modifier
                                .size(width = 38.dp, height = 36.dp)
                                .graphicsLayer {
                                    scaleX = sidebarScaleState.value
                                    scaleY = sidebarScaleState.value
                                }
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (state.isTabletSidebarOpen) accentBlue.copy(alpha = 0.18f) else Color.Transparent,
                                )
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onPress = {
                                            if (!sideInteractive) return@detectTapGestures
                                            sidebarPressed = true
                                            try {
                                                tryAwaitRelease()
                                            } finally {
                                                sidebarPressed = false
                                            }
                                        },
                                        onTap = {
                                            if (!sideInteractive) return@detectTapGestures
                                            triggerHaptic(LiquidKitHaptic.SelectionTick)
                                            state.isTabletSidebarOpen = !state.isTabletSidebarOpen
                                        },
                                    )
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            SafariSidebarIcon(
                                color = if (state.isTabletSidebarOpen) accentBlue else colors.ink,
                                modifier = Modifier.size(20.dp),
                            )
                        }

                        BorderlessMenuButton(
                            menuState = backHistoryMenuState,
                            menuItems = backHistoryItems,
                            backdrop = backdrop,
                            buttonWidth = 36.dp,
                            buttonHeight = 36.dp,
                            enabled = sideInteractive && tab.canGoBack,
                            onShortTap = {
                                if (tab.canGoBack) pool.goBack(tab.id)
                            },
                        ) {
                            TerraSymbolIcon(
                                symbol = LiquidKitSymbol.Back,
                                tint = if (tab.canGoBack) colors.ink else colors.secondary.copy(alpha = 0.36f),
                                modifier = Modifier.size(20.dp),
                            )
                        }

                        BorderlessMenuButton(
                            menuState = forwardHistoryMenuState,
                            menuItems = forwardHistoryItems,
                            backdrop = backdrop,
                            buttonWidth = 36.dp,
                            buttonHeight = 36.dp,
                            enabled = sideInteractive && tab.canGoForward,
                            onShortTap = {
                                if (tab.canGoForward) pool.goForward(tab.id)
                            },
                        ) {
                            TerraSymbolIcon(
                                symbol = LiquidKitSymbol.Forward,
                                tint = if (tab.canGoForward) colors.ink else colors.secondary.copy(alpha = 0.36f),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .layout { measurable, _ ->
                                val c = collapseState.value.coerceIn(0f, 1f)
                                val wPx = lerp(expandedCenterWidthPx, miniCenterWidthPx, c).roundToInt().coerceAtLeast(1)
                                val hPx = lerp(pillFullPx, pillMiniPx, c).roundToInt().coerceAtLeast(1)
                                val placeable = measurable.measure(Constraints.fixed(wPx, hPx))
                                layout(wPx, hPx) { placeable.placeRelative(0, 0) }
                            }
                            .liquidKitPopoverAnchor(tabletPillAnchor),
                    ) {
                        AnchoredGlassMenuOverlay(
                            menuState = addressLongPressMenuState,
                            items = addressLongPressItems,
                            backdrop = backdrop,
                            anchorBoundsProvider = { tabletPillAnchor.boundsInRoot },
                            ink = colors.ink,
                            isDark = isDark,
                            centerHorizontally = true,
                            showScrim = true,
                            anchorGapDp = 12.dp,
                            liftedAnchorContent = { progress ->
                                SelectedAddressPillPreview(
                                    tab = tab,
                                    isPrivate = state.activeGroup.isPrivate,
                                    collapse = collapseState.value,
                                    ink = colors.ink,
                                    secondary = colors.secondary,
                                    isDark = isDark,
                                    progress = progress,
                                )
                            },
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            BoxWithConstraints(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clipToBounds(),
                            ) {
                                val pillStepPx = with(density) { maxWidth.toPx() } + gap12Px
                                val swipeTxProvider: () -> Float = { latestSwipeFraction() * pillStepPx }

                                AddressPillSurface(
                                    state = state,
                                    tab = tab,
                                    pool = pool,
                                    backdrop = backdrop,
                                    pageMenuState = pageMenuState,
                                    pageMenuItems = pageMenuItems,
                                    addressLongPressMenuState = addressLongPressMenuState,
                                    collapse = 0f,
                                    onBarDragStart = onBarDragStart,
                                    onBarDragDelta = { dx, _ -> onBarDragDelta(dx, 0f) },
                                    onBarDragEnd = { vx, _ -> onBarDragEnd(vx, 0f) },
                                    enableOverviewPullUpProvider = { false },
                                    ink = colors.ink,
                                    secondary = colors.secondary,
                                    accentBlue = accentBlue,
                                    pillHeight = 40.dp,
                                    innerPill = false,
                                    surfaceTint = pillTint,
                                    swipeTxPx = 0f,
                                    collapseProvider = { collapseState.value },
                                    swipeTxPxProvider = swipeTxProvider,
                                    modifier = Modifier.fillMaxSize(),
                                )

                                if (showAdjacent) {
                                    val delta = swipeDelta
                                    val adjTab = state.peekAdjacentTab(delta)
                                    val canSwipeToNewTab = delta == +1 && adjTab == null && !tab.isStartPage
                                    if (adjTab != null || canSwipeToNewTab) {
                                        AdjacentSwipePill(
                                            adjacentTab = adjTab,
                                            isPrivate = state.activeGroup.isPrivate,
                                            backdrop = backdrop,
                                            innerPill = false,
                                            surfaceTint = pillTint,
                                            ink = colors.ink,
                                            secondary = colors.secondary,
                                            collapse = collapseState.value,
                                            collapseProvider = { collapseState.value },
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .graphicsLayer { translationX = swipeTxProvider() + delta * pillStepPx },
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .layout { measurable, constraints ->
                                val sideAlpha = (1f - collapseState.value * 1.5f).coerceIn(0f, 1f)
                                if (sideAlpha <= 0.005f) {
                                    layout(0, 0) {}
                                } else {
                                    val placeable = measurable.measure(constraints)
                                    layout(placeable.width, placeable.height) {
                                        placeable.placeRelative(0, 0)
                                    }
                                }
                            }
                            .graphicsLayer {
                                alpha = (1f - collapseState.value * 1.5f).coerceIn(0f, 1f)
                            },
                    ) {
                        BorderlessMenuButton(
                            menuState = shareQuickMenuState,
                            menuItems = shareQuickMenuItems,
                            backdrop = backdrop,
                            buttonWidth = 38.dp,
                            buttonHeight = 36.dp,
                            enabled = sideInteractive,
                            onShortTap = { state.showShareSheet = true },
                        ) {
                            TerraSymbolIcon(symbol = LiquidKitSymbol.Share, tint = colors.ink, modifier = Modifier.size(19.dp))
                        }

                        BorderlessMenuButton(
                            menuState = newTabQuickMenuState,
                            menuItems = newTabQuickMenuItems,
                            backdrop = backdrop,
                            buttonWidth = 38.dp,
                            buttonHeight = 36.dp,
                            enabled = sideInteractive,
                            onShortTap = onOpenNewTab,
                        ) {
                            TerraSymbolIcon(symbol = LiquidKitSymbol.Add, tint = colors.ink, modifier = Modifier.size(20.dp))
                        }

                        BorderlessMenuButton(
                            menuState = quickTabsMenuState,
                            menuItems = quickTabsMenuItems,
                            backdrop = backdrop,
                            buttonWidth = 38.dp,
                            buttonHeight = 36.dp,
                            enabled = sideInteractive,
                            onShortTap = onOpenOverview,
                        ) {
                            SafariTabsIcon(color = colors.ink, modifier = Modifier.size(19.dp))
                        }
                    }
                }

                AnimatedVisibility(
                    visible = state.showTabletTabBar && !state.isToolbarMinimized,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    TabletTabStrip(
                        state = state,
                        pool = pool,
                        backdrop = backdrop,
                        ink = colors.ink,
                        secondary = colors.secondary,
                        accentBlue = accentBlue,
                        isDark = isDark,
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(0.5.dp)
                        .background(colors.ink.copy(alpha = 0.12f)),
                )
            }
        }
    }
}

@Composable
private fun TabletTabStrip(
    state: TerraBrowserState,
    pool: TerraWebViewPool,
    backdrop: Backdrop,
    ink: Color,
    secondary: Color,
    accentBlue: Color,
    isDark: Boolean,
    onBeforeOpenMenu: (io.liquidkit.LiquidKitMenuState) -> Unit = {},
) {
    val groupTabs = state.tabsInGroup(state.activeGroup.id)
    val activeTabId = state.activeTab.id
    val clipboard = LocalClipboardManager.current
    val triggerHaptic = rememberLiquidKitHaptics()
    val listState = rememberLazyListState()
    var ctxTab by remember { mutableStateOf<BrowserTab?>(null) }
    val ctxAnchor = rememberLiquidKitPopoverAnchorState()
    val stripBoundsMap = remember { HashMap<String, Rect>() }

    LaunchedEffect(activeTabId, groupTabs.size) {
        val activeIndex = groupTabs.indexOfFirst { it.id == activeTabId }
        if (activeIndex >= 0) {
            val isVisible = listState.layoutInfo.visibleItemsInfo.any { it.index == activeIndex }
            if (!isVisible) {
                listState.animateScrollToItem(activeIndex)
            }
        }
    }

    val stripCtxItems = remember(ctxTab?.id, ctxTab?.isPinned, ctxTab?.url) {
        val t = ctxTab
        if (t == null) {
            emptyList()
        } else {
            listOf(
                LiquidKitMenuItem("copy_link", "拷贝链接", symbol = LiquidKitSymbol.Link, enabled = !t.isStartPage),
                LiquidKitMenuItem(
                    key = "pin_tab",
                    title = if (t.isPinned) "取消固定标签页" else "固定标签页",
                    symbol = LiquidKitSymbol.Star,
                ),
                LiquidKitMenuItem("duplicate_tab", "复制标签页", symbol = LiquidKitSymbol.SquareOnSquare),
                LiquidKitMenuItem(
                    key = "close_tab",
                    title = "关闭标签页",
                    symbol = LiquidKitSymbol.Close,
                    startsSection = true,
                    destructive = true,
                ),
                LiquidKitMenuItem(
                    key = "close_others",
                    title = "关闭其他标签页",
                    symbol = LiquidKitSymbol.Close,
                    destructive = true,
                ),
            )
        }
    }
    val stripCtxMenuState = rememberLiquidKitMenuState { picked ->
        val t = ctxTab ?: return@rememberLiquidKitMenuState
        when (picked.key) {
            "copy_link" -> {
                clipboard.setText(AnnotatedString(t.url))
                state.showBanner("已拷贝链接")
            }
            "pin_tab" -> state.togglePinTab(t.id)
            "duplicate_tab" -> state.createTab(groupId = t.groupId, url = t.url, select = true, insertAfterActive = true)
            "close_tab" -> {
                pool.evictTab(t.id)
                state.closeTab(t.id)
            }
            "close_others" -> {
                state.tabsInGroup(t.groupId).filter { it.id != t.id }.forEach { pool.evictTab(it.id) }
                state.closeOtherTabsInGroup(t.id)
            }
        }
        ctxTab = null
    }

    AnchoredGlassMenuOverlay(
        menuState = stripCtxMenuState,
        items = stripCtxItems,
        backdrop = backdrop,
        anchorBoundsProvider = { ctxAnchor.boundsInRoot },
        ink = ink,
        isDark = isDark,
    ) {
        LazyRow(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
                .padding(bottom = 6.dp),
            contentPadding = PaddingValues(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items(groupTabs, key = { "strip_${it.id}" }) { stripTab ->
                val isSelected = stripTab.id == activeTabId
                val chipShape = RoundedCornerShape(10.dp)
                val chipBg = when {
                    isSelected && isDark -> Color.White.copy(alpha = 0.16f)
                    isSelected -> Color.White.copy(alpha = 0.92f)
                    isDark -> Color.White.copy(alpha = 0.055f)
                    else -> Color.Black.copy(alpha = 0.05f)
                }
                val chipBorder = when {
                    isSelected -> accentBlue.copy(alpha = 0.42f)
                    isDark -> Color.White.copy(alpha = 0.06f)
                    else -> Color.Black.copy(alpha = 0.06f)
                }
                Row(
                    modifier = Modifier
                        .height(31.dp)
                        .widthIn(min = 124.dp, max = 216.dp)
                        .clip(chipShape)
                        .background(chipBg)
                        .border(0.75.dp, chipBorder, chipShape)
                        .onGloballyPositioned { if (it.isAttached) stripBoundsMap[stripTab.id] = it.boundsInRoot() }
                        .pointerInput(stripTab.id) {
                            detectTapGestures(
                                onTap = { state.selectTab(stripTab.id) },
                                onLongPress = {
                                    triggerHaptic(LiquidKitHaptic.LongPressLift)
                                    ctxTab = stripTab
                                    ctxAnchor.boundsInRoot = stripBoundsMap[stripTab.id]
                                    onBeforeOpenMenu(stripCtxMenuState)
                                    stripCtxMenuState.show()
                                },
                            )
                        }
                        .padding(horizontal = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val icon = state.favicons[stripTab.id]
                    Box(
                        modifier = Modifier
                            .size(15.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(stripTab.accentColorArgb)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (icon != null && !stripTab.isStartPage) {
                            Image(
                                bitmap = icon,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else if (stripTab.isStartPage) {
                            SafariCompassIcon(color = Color.White, modifier = Modifier.size(10.dp))
                        } else {
                            BasicText(
                                text = stripTab.title.take(1).uppercase(),
                                style = TextStyle(color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold),
                            )
                        }
                    }

                    if (stripTab.isPinned) {
                        TerraSymbolIcon(symbol = LiquidKitSymbol.StarFilled, tint = accentBlue, modifier = Modifier.size(11.dp))
                    }

                    BasicText(
                        text = stripTab.title.ifBlank { "起始页" },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(
                            color = if (isSelected) ink else secondary,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        ),
                        modifier = Modifier.weight(1f, fill = false),
                    )

                    if (isSelected || groupTabs.size > 1) {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) {
                                    pool.evictTab(stripTab.id)
                                    state.closeTab(stripTab.id)
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            TerraSymbolIcon(
                                symbol = LiquidKitSymbol.Close,
                                tint = secondary,
                                modifier = Modifier.size(12.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Collapsible iPadOS-style Left Sidebar displaying Tab Groups, inline Bookmarks / Reading List /
 * History browser, plus New Group, Privacy Report, and Terra Settings shortcuts.
 */
@Composable
fun TabletSidebar(
    state: TerraBrowserState,
    pool: TerraWebViewPool,
    onCloseSidebar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = liquidKitAppearance().dark
    val colors = liquidKitColors(isDark)
    val accentBlue = Color(0xFF0A84FF)
    var selectedLibrary by remember { mutableStateOf(LibraryTab.BOOKMARKS) }

    val sidebarBg = if (isDark) Color(0xFF15171D) else Color(0xFFF3F5FA)
    val cardFill = if (isDark) Color.White.copy(alpha = 0.065f) else Color.White.copy(alpha = 0.85f)

    Row(
        modifier = modifier
            .width(276.dp)
            .fillMaxHeight()
            .background(sidebarBg),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .statusBarsPadding()
                .navigationBarsPadding()
                .displayCutoutPadding()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicText(
                    text = "Terra Browser",
                    style = TextStyle(color = colors.ink, fontSize = 17.sp, fontWeight = FontWeight.Bold),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {
                                state.newGroupDraft = ""
                                state.showNewGroupAlert = true
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        TerraSymbolIcon(symbol = LiquidKitSymbol.NewFolder, tint = accentBlue, modifier = Modifier.size(18.dp))
                    }
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onCloseSidebar,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        SafariSidebarIcon(color = colors.secondary, modifier = Modifier.size(18.dp))
                    }
                }
            }

            val isPrivateGroup = state.activeGroup.isPrivate
            val sidebarDownloads = remember(isPrivateGroup, pool.downloads.revision, selectedLibrary) {
                if (selectedLibrary == LibraryTab.DOWNLOADS) pool.downloads.read(isPrivateGroup).take(16) else emptyList()
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item("hdr_groups") {
                    BasicText(
                        text = "标签页组",
                        style = TextStyle(color = colors.secondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                    )
                }

                items(state.tabGroups, key = { "sb_grp_${it.id}" }) { grp ->
                    val selected = grp.id == state.activeGroupId
                    val count = state.tabsInGroup(grp.id).size
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selected) accentBlue.copy(alpha = 0.16f) else cardFill)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { state.selectGroup(grp.id) }
                            .padding(horizontal = 10.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(9.dp),
                    ) {
                        if (grp.isPrivate) {
                            SafariPrivateMaskIcon(
                                color = if (selected) accentBlue else colors.secondary,
                                modifier = Modifier.size(16.dp),
                            )
                        } else {
                            TerraSymbolIcon(
                                symbol = if (grp.isDefault) LiquidKitSymbol.SquareOnSquare else LiquidKitSymbol.Folder,
                                tint = if (selected) accentBlue else Color(grp.accentColorArgb),
                                modifier = Modifier.size(16.dp),
                            )
                        }
                        BasicText(
                            text = if (grp.isDefault) "标签页" else grp.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = TextStyle(
                                color = if (selected) accentBlue else colors.ink,
                                fontSize = 14.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            ),
                            modifier = Modifier.weight(1f),
                        )
                        BasicText(
                            text = count.toString(),
                            style = TextStyle(color = colors.secondary, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                        )
                    }
                }

                item("hdr_library") {
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BasicText(
                            text = "资料库",
                            style = TextStyle(color = colors.secondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                        BasicText(
                            text = "全部…",
                            style = TextStyle(color = accentBlue, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) {
                                    state.libraryInitialTab = selectedLibrary
                                    state.showLibrarySheet = true
                                }
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                        )
                    }
                }

                item("lib_picker") {
                    LiquidKitSegmentedPicker(
                        options = LibraryTab.entries.map { it.label },
                        selected = selectedLibrary.label,
                        select = { lbl ->
                            selectedLibrary = LibraryTab.entries.firstOrNull { it.label == lbl } ?: LibraryTab.BOOKMARKS
                        },
                        colors = colors,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                when (selectedLibrary) {
                    LibraryTab.BOOKMARKS -> {
                        if (state.bookmarks.isEmpty()) {
                            item("sb_bm_empty") {
                                BasicText(
                                    text = "暂无书签",
                                    style = TextStyle(color = colors.secondary, fontSize = 12.sp),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                                )
                            }
                        } else {
                            items(state.bookmarks, key = { "sb_bm_${it.id}" }) { bm ->
                                SidebarUrlRow(
                                    title = bm.title,
                                    host = BrowserUrlUtils.displayHost(bm.url),
                                    accentArgb = BrowserUrlUtils.accentColorForUrl(bm.url),
                                    ink = colors.ink,
                                    secondary = colors.secondary,
                                    cardFill = cardFill,
                                ) { state.navigateActiveTab(bm.url) }
                            }
                        }
                    }
                    LibraryTab.READING_LIST -> {
                        if (state.readingList.isEmpty()) {
                            item("sb_rl_empty") {
                                BasicText(
                                    text = "暂无阅读列表",
                                    style = TextStyle(color = colors.secondary, fontSize = 12.sp),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                                )
                            }
                        } else {
                            items(state.readingList, key = { "sb_rl_${it.id}" }) { rl ->
                                SidebarUrlRow(
                                    title = rl.title,
                                    host = BrowserUrlUtils.displayHost(rl.url),
                                    accentArgb = BrowserUrlUtils.accentColorForUrl(rl.url),
                                    ink = colors.ink,
                                    secondary = colors.secondary,
                                    cardFill = cardFill,
                                ) { state.navigateActiveTab(rl.url) }
                            }
                        }
                    }
                    LibraryTab.HISTORY -> {
                        if (state.history.isEmpty()) {
                            item("sb_his_empty") {
                                BasicText(
                                    text = "暂无历史记录",
                                    style = TextStyle(color = colors.secondary, fontSize = 12.sp),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                                )
                            }
                        } else {
                            items(state.history.take(16), key = { "sb_his_${it.id}" }) { his ->
                                SidebarUrlRow(
                                    title = his.title,
                                    host = BrowserUrlUtils.displayHost(his.url),
                                    accentArgb = BrowserUrlUtils.accentColorForUrl(his.url),
                                    ink = colors.ink,
                                    secondary = colors.secondary,
                                    cardFill = cardFill,
                                ) { state.navigateActiveTab(his.url) }
                            }
                        }
                    }
                    LibraryTab.DOWNLOADS -> {
                        items(sidebarDownloads, key = { "sb_dl_${it.id}" }) { dl ->
                            SidebarUrlRow(
                                title = dl.title.ifBlank { BrowserUrlUtils.displayHost(dl.url) },
                                host = when {
                                    dl.completed -> "已完成"
                                    dl.failed -> "下载失败"
                                    else -> "下载中…"
                                },
                                accentArgb = if (dl.failed) 0xFFFF3B30 else BrowserUrlUtils.accentColorForUrl(dl.url),
                                ink = colors.ink,
                                secondary = colors.secondary,
                                cardFill = cardFill,
                            ) {
                                if (dl.completed) pool.downloads.open(dl) else pool.downloads.openDirectory()
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(cardFill)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { state.showPrivacyReportSheet = true }
                        .padding(horizontal = 10.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    SafariShieldIcon(color = accentBlue, modifier = Modifier.size(16.dp))
                    BasicText(
                        text = "${state.totalTrackersBlocked} 拦截",
                        maxLines = 1,
                        style = TextStyle(color = colors.ink, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                    )
                }
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(cardFill)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { state.showSettingsSheet = true }
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    TerraSymbolIcon(symbol = LiquidKitSymbol.SliderHorizontal, tint = colors.ink, modifier = Modifier.size(16.dp))
                    BasicText(
                        text = "设置",
                        style = TextStyle(color = colors.ink, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .width(0.5.dp)
                .fillMaxHeight()
                .background(colors.ink.copy(alpha = 0.14f)),
        )
    }
}

@Composable
private fun SidebarUrlRow(
    title: String,
    host: String,
    accentArgb: Long,
    ink: Color,
    secondary: Color,
    cardFill: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(11.dp))
            .background(cardFill)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(accentArgb)),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(
                text = title.take(1).uppercase(),
                style = TextStyle(color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            BasicText(
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(color = ink, fontSize = 12.5.sp, fontWeight = FontWeight.Medium),
            )
            BasicText(
                text = host,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(color = secondary, fontSize = 10.5.sp),
            )
        }
    }
}
