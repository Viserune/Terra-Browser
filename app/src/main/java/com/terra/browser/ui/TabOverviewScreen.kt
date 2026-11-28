package com.terra.browser.ui

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.zIndex
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.terra.browser.model.BrowserLayoutProfile
import com.terra.browser.model.BrowserTab
import com.terra.browser.model.FavoriteItem
import com.terra.browser.model.TabGroup
import com.terra.browser.model.TabSortMode
import com.terra.browser.state.TerraBrowserState
import com.terra.browser.webview.TerraWebViewPool
import io.liquidkit.LiquidKitAnchoredMenuHost
import io.liquidkit.LiquidKitBottomTabs
import io.liquidkit.LiquidKitContextMenuHost
import io.liquidkit.LiquidKitGlassSurface
import io.liquidkit.LiquidKitHaptic
import io.liquidkit.LiquidKitIconButton
import io.liquidkit.LiquidKitMenuActivation
import io.liquidkit.LiquidKitMenuItem
import io.liquidkit.LiquidKitMenuPanel
import io.liquidkit.LiquidKitMenuPresentation
import io.liquidkit.LiquidKitSpring
import io.liquidkit.LiquidKitSymbol
import io.liquidkit.LiquidKitText
import io.liquidkit.LiquidKitTheme
import io.liquidkit.liquidKitAppearance
import io.liquidkit.liquidKitBackdrop
import io.liquidkit.liquidKitColors
import io.liquidkit.liquidKitMenuMetrics
import io.liquidkit.liquidKitMenuSource
import io.liquidkit.liquidKitMenuTrigger
import io.liquidkit.liquidKitTypography
import io.liquidkit.rememberLiquidKitBackdrop
import io.liquidkit.rememberLiquidKitHaptics
import io.liquidkit.rememberLiquidKitMenuState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private val OverviewAccentBlue = Color(0xFF0A84FF)
private val OverviewCardShape = RoundedCornerShape(18.dp)
private val OverviewFaviconShape = RoundedCornerShape(4.dp)
private val OverviewPillShape = RoundedCornerShape(26.dp)
private val OverviewMenuShape = RoundedCornerShape(30.dp)
private val OverviewCardBottomScrimBrush = Brush.verticalGradient(
    listOf(Color.Transparent, Color.Black.copy(alpha = 0.28f)),
)
private val MiniStartTileShape = RoundedCornerShape(6.dp)
private val MiniStartBarShape = RoundedCornerShape(8.dp)
private val MiniWebLineShape = RoundedCornerShape(4.dp)
private val MiniWebHeroShape = RoundedCornerShape(10.dp)

private fun Float.coerceInSafe(minVal: Float, maxVal: Float): Float =
    coerceIn(minVal, maxOf(minVal, maxVal))

private fun Int.coerceInSafe(minVal: Int, maxVal: Int): Int =
    coerceIn(minVal, maxOf(minVal, maxVal))

private fun androidx.compose.ui.unit.Dp.coerceInSafe(
    minVal: androidx.compose.ui.unit.Dp,
    maxVal: androidx.compose.ui.unit.Dp,
): androidx.compose.ui.unit.Dp = coerceIn(minVal, maxOf(minVal, maxVal))

@Composable
fun TabOverviewScreen(
    state: TerraBrowserState,
    pool: TerraWebViewPool,
    backdrop: Backdrop,
    overviewProgressProvider: () -> Float,
    cardBoundsMap: MutableMap<String, Rect>,
    onSelectTab: (BrowserTab) -> Unit,
    onCreateNewTab: (groupId: String) -> Unit,
    onCloseOverview: () -> Unit,
    isBlurred: Boolean = false,
    hiddenCardTabIdProvider: () -> String? = { null },
    layoutProfile: BrowserLayoutProfile = BrowserLayoutProfile.PHONE_PORTRAIT,
    modifier: Modifier = Modifier,
) {
    val isDark = liquidKitAppearance().dark
    val colors = liquidKitColors(isDark)
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val density = LocalDensity.current
    val triggerHaptic = rememberLiquidKitHaptics()
    val accentBlue = OverviewAccentBlue

    val latestOverviewProgress by rememberUpdatedState(overviewProgressProvider)
    val latestHiddenCardTabId by rememberUpdatedState(hiddenCardTabIdProvider)
    val pagerSnapSpring = remember { LiquidKitSpring.swiftUI(0.33f, 0.94f).spec }

    val initialPage = remember(state.tabGroups.size) {
        state.tabGroups.indexOfFirst { it.id == state.activeGroupId }.coerceAtLeast(0)
    }
    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { state.tabGroups.size.coerceAtLeast(1) },
    )

    var menuGroupScrollJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(state.activeGroupId, state.tabGroups.size) {
        val target = state.tabGroups.indexOfFirst { it.id == state.activeGroupId }
        if (target >= 0 && target != pagerState.targetPage && !pagerState.isScrollInProgress) {
            pagerState.animateScrollToPage(target, animationSpec = pagerSnapSpring)
        }
    }

    LaunchedEffect(pagerState.settledPage, state.tabGroups.size) {
        val grp = state.tabGroups.getOrNull(pagerState.settledPage)
        if (grp != null && grp.id != state.activeGroupId) {
            state.selectGroup(grp.id)
        }
    }

    var contextCardTab by remember { mutableStateOf<BrowserTab?>(null) }
    var cardMenuSession by remember { mutableIntStateOf(0) }
    var pressedTabId by remember { mutableStateOf<String?>(null) }
    var liftArmedTabId by remember { mutableStateOf<String?>(null) }
    var draggedTabId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var isScrubbingGroupPill by remember { mutableStateOf(false) }
    var isSelectionMode by remember { mutableStateOf(false) }
    val selectedTabIds = remember { mutableStateMapOf<String, Boolean>() }
    val closingTabIds = remember { mutableStateMapOf<String, Boolean>() }

    val latestContextCardTab by rememberUpdatedState(contextCardTab)

    LaunchedEffect(pagerState.currentPage) {
        if (isSelectionMode) {
            isSelectionMode = false
            selectedTabIds.clear()
        }
    }

    val cardContextMenuItems = remember(contextCardTab?.id, contextCardTab?.isPinned, contextCardTab?.groupId, state.tabGroups.size) {
        val target = contextCardTab
        if (target == null) {
            emptyList()
        } else {
            listOf(
                LiquidKitMenuItem("copy_link", "拷贝链接", symbol = LiquidKitSymbol.Link),
                LiquidKitMenuItem(
                    key = "pin_tab",
                    title = if (target.isPinned) "取消固定标签页" else "固定标签页",
                    symbol = LiquidKitSymbol.Star,
                ),
                LiquidKitMenuItem("duplicate_tab", "复制标签页", symbol = LiquidKitSymbol.SquareOnSquare),
                LiquidKitMenuItem("select_tabs", "选择标签页", symbol = LiquidKitSymbol.CheckCircle),
                LiquidKitMenuItem(
                    key = "move_tab",
                    title = "移动标签页",
                    symbol = LiquidKitSymbol.Folder,
                    children = state.tabGroups.map { grp ->
                        LiquidKitMenuItem(
                            key = "move_grp:${grp.id}",
                            title = grp.name,
                            symbol = if (grp.isPrivate) LiquidKitSymbol.Filter else LiquidKitSymbol.Folder,
                            checked = grp.id == target.groupId,
                        )
                    } + LiquidKitMenuItem(
                        key = "move_new_group",
                        title = "新建标签页组",
                        symbol = LiquidKitSymbol.NewFolder,
                        startsSection = true,
                    ),
                ),
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
                    startsSection = true,
                    destructive = true,
                ),
            )
        }
    }
    val cardContextMenuState = rememberLiquidKitMenuState { picked ->
        val target = contextCardTab ?: return@rememberLiquidKitMenuState
        when {
            picked.key == "copy_link" -> {
                clipboard.setText(AnnotatedString(target.url.ifBlank { "https://terra.browser/start" }))
                contextCardTab = null
                state.showBanner("已拷贝链接")
            }
            picked.key == "pin_tab" -> {
                state.togglePinTab(target.id)
                contextCardTab = null
            }
            picked.key == "duplicate_tab" -> {
                state.createTab(
                    groupId = target.groupId,
                    url = target.url,
                    select = false,
                    insertAfterActive = true,
                )
                contextCardTab = null
                state.showBanner("已复制标签页")
            }
            picked.key == "select_tabs" -> {
                isSelectionMode = true
                selectedTabIds.clear()
                selectedTabIds[target.id] = true
                contextCardTab = null
            }
            picked.key.startsWith("move_grp:") -> {
                val targetGrpId = picked.key.removePrefix("move_grp:")
                val targetGrp = state.tabGroups.firstOrNull { it.id == targetGrpId }
                if (targetGrp != null && targetGrp.id != target.groupId) {
                    state.moveTabToGroup(target.id, targetGrp.id)
                    state.showBanner("已移至「${targetGrp.name}」")
                }
                contextCardTab = null
            }
            picked.key == "move_new_group" -> {
                contextCardTab = null
                state.newGroupDraft = ""
                state.showNewGroupAlert = true
            }
            picked.key == "close_tab" -> {
                closingTabIds[target.id] = true
                pool.evictTab(target.id)
                state.closeTab(target.id)
                contextCardTab = null
            }
            picked.key == "close_others" -> {
                state.tabsInGroup(target.groupId)
                    .filter { it.id != target.id }
                    .forEach {
                        closingTabIds[it.id] = true
                        pool.evictTab(it.id)
                    }
                state.closeOtherTabsInGroup(target.id)
                contextCardTab = null
            }
        }
    }
    LaunchedEffect(cardContextMenuState.expanded) {
        if (!cardContextMenuState.expanded) {
            contextCardTab = null
        }
    }

    // Group switcher menu (top-left ≡ button)
    val groupsMenuItems = remember(state.tabGroups.size, state.tabs.size, state.activeGroupId, state.sortMode) {
        state.tabGroups.map { grp ->
            LiquidKitMenuItem(
                key = "grp:${grp.id}",
                title = grp.name,
                subtitle = "${state.tabsInGroup(grp.id).size} 个标签页",
                symbol = if (grp.isPrivate) LiquidKitSymbol.Filter else LiquidKitSymbol.Folder,
                checked = grp.id == state.activeGroupId,
            )
        } + listOf(
            LiquidKitMenuItem(
                key = "new_group",
                title = "新建标签页组",
                symbol = LiquidKitSymbol.NewFolder,
                startsSection = true,
            ),
            LiquidKitMenuItem(
                key = "manage_groups",
                title = "管理标签页组",
                symbol = LiquidKitSymbol.List,
            ),
            LiquidKitMenuItem(
                key = "arrange",
                title = "整理标签页",
                symbol = LiquidKitSymbol.Grid,
                startsSection = true,
                children = listOf(
                    LiquidKitMenuItem("sort_title", "按标题排列", symbol = LiquidKitSymbol.Ascending, checked = state.sortMode == TabSortMode.TITLE),
                    LiquidKitMenuItem("sort_site", "按网站排列", symbol = LiquidKitSymbol.Globe, checked = state.sortMode == TabSortMode.WEBSITE),
                ),
            ),
        )
    }
    val groupsMenuState = rememberLiquidKitMenuState { picked ->
        when {
            picked.key.startsWith("grp:") -> {
                val id = picked.key.removePrefix("grp:")
                state.selectGroup(id)
                val idx = state.tabGroups.indexOfFirst { it.id == id }
                if (idx >= 0) {
                    menuGroupScrollJob?.cancel()
                    menuGroupScrollJob = scope.launch {
                        pagerState.animateScrollToPage(idx, animationSpec = pagerSnapSpring)
                    }
                }
            }
            picked.key == "new_group" -> {
                state.newGroupDraft = ""
                state.showNewGroupAlert = true
            }
            picked.key == "manage_groups" -> state.showManageGroupsSheet = true
            picked.key == "sort_title" -> state.sortTabsInGroup(state.activeGroup.id, TabSortMode.TITLE)
            picked.key == "sort_site" -> state.organizeTabsByWebsite(state.activeGroup.id)
        }
    }

    // Overview options menu (top-right ••• button)
    val moreMenuItems = remember(state.activeGroupId, state.tabs.size, state.sortMode) {
        val currentCount = state.tabsInGroup(state.activeGroup.id).size
        listOf(
            LiquidKitMenuItem("manage_groups", "管理标签页组", symbol = LiquidKitSymbol.Folder),
            LiquidKitMenuItem("select_tabs", "选择标签页", symbol = LiquidKitSymbol.CheckCircle),
            LiquidKitMenuItem(
                key = "sort_submenu",
                title = "标签页排列方式",
                symbol = LiquidKitSymbol.List,
                startsSection = true,
                children = listOf(
                    LiquidKitMenuItem("sort_title", "按标题排列标签页", checked = state.sortMode == TabSortMode.TITLE),
                    LiquidKitMenuItem("sort_site", "按网站排列标签页", checked = state.sortMode == TabSortMode.WEBSITE),
                ),
            ),
            LiquidKitMenuItem("copy_all", "拷贝全部 $currentCount 个链接", symbol = LiquidKitSymbol.Link, startsSection = true),
            LiquidKitMenuItem("bookmark_all", "为 $currentCount 个标签页添加书签", symbol = LiquidKitSymbol.Bookmark),
            LiquidKitMenuItem("open_settings", "浏览器设置", symbol = LiquidKitSymbol.SliderHorizontal, startsSection = true),
            LiquidKitMenuItem("close_all", "关闭本组所有标签页", symbol = LiquidKitSymbol.Trash, startsSection = true, destructive = true),
        )
    }
    val moreMenuState = rememberLiquidKitMenuState { picked ->
        when (picked.key) {
            "manage_groups" -> state.showManageGroupsSheet = true
            "select_tabs" -> {
                isSelectionMode = true
                selectedTabIds.clear()
            }
            "new_group" -> {
                state.newGroupDraft = ""
                state.showNewGroupAlert = true
            }
            "sort_title" -> state.sortTabsInGroup(state.activeGroup.id, TabSortMode.TITLE)
            "sort_site" -> state.organizeTabsByWebsite(state.activeGroup.id)
            "open_settings" -> state.showSettingsSheet = true
            "bookmark_all" -> {
                val added = state.bookmarkAllTabsInGroup(state.activeGroup.id)
                state.showBanner(if (added > 0) "已将 $added 个标签页添加到书签" else "本组标签页已在书签中")
            }
            "copy_all" -> {
                val links = state.tabsInGroup(state.activeGroup.id)
                    .mapNotNull { it.url.takeIf { u -> u.isNotBlank() } }
                    .joinToString("\n")
                if (links.isNotEmpty()) {
                    clipboard.setText(AnnotatedString(links))
                    state.showBanner("已拷贝全部链接")
                } else {
                    state.showBanner("当前组暂无网页链接")
                }
            }
            "close_all" -> {
                state.tabsInGroup(state.activeGroup.id).forEach {
                    closingTabIds[it.id] = true
                    pool.evictTab(it.id)
                }
                state.closeAllTabsInGroup(state.activeGroup.id)
            }
        }
    }

    val isOverviewClosing by remember { derivedStateOf { latestOverviewProgress() < 0.05f } }
    BackHandler(enabled = groupsMenuState.expanded) { groupsMenuState.dismiss() }
    BackHandler(enabled = moreMenuState.expanded) { moreMenuState.dismiss() }
    BackHandler(enabled = cardContextMenuState.expanded) {
        cardContextMenuState.dismiss()
        contextCardTab = null
    }
    LaunchedEffect(isOverviewClosing) {
        if (isOverviewClosing) {
            cardContextMenuState.dismiss()
            groupsMenuState.dismiss()
            moreMenuState.dismiss()
            contextCardTab = null
            pressedTabId = null
            liftArmedTabId = null
            draggedTabId = null
            dragOffset = Offset.Zero
            isScrubbingGroupPill = false
            isSelectionMode = false
            selectedTabIds.clear()
        }
    }

    val overviewBackdrop = rememberLiquidKitBackdrop()
    val statusBarTopDp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val currentGroup by remember(state.tabGroups) {
        derivedStateOf { state.tabGroups.getOrNull(pagerState.settledPage) ?: state.activeGroup }
    }
    val overviewDark = isDark || currentGroup.isPrivate
    val overviewColors = liquidKitColors(overviewDark)
    val overviewBackdropEnabled by remember {
        derivedStateOf {
            latestOverviewProgress() >= 0.96f &&
                !pagerState.isScrollInProgress &&
                !isScrubbingGroupPill &&
                !state.showSettingsSheet &&
                !state.showLibrarySheet &&
                !state.showShareSheet &&
                !state.showPrivacyReportSheet &&
                !state.showManageGroupsSheet &&
                !state.showCustomizeMenuSheet &&
                !state.showZoomSheet
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .layout { measurable, constraints ->
                if (latestOverviewProgress() <= 0.005f) {
                    layout(0, 0) {}
                } else {
                    val placeable = measurable.measure(constraints)
                    layout(placeable.width, placeable.height) {
                        placeable.placeRelative(0, 0)
                    }
                }
            }
            .then(if (isBlurred) Modifier.blur(10.dp) else Modifier)
            .graphicsLayer {
                val p = latestOverviewProgress().coerceIn(0f, 1f)
                compositingStrategy = if (p < 0.995f) CompositingStrategy.ModulateAlpha else CompositingStrategy.Auto
                alpha = (p * 1.35f).coerceIn(0f, 1f)
                val s = lerp(1.045f, 1f, p)
                scaleX = s
                scaleY = s
            },
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .liquidKitBackdrop(
                    backdrop = overviewBackdrop,
                    enabled = latestOverviewProgress() > 0.05f,
                    scale = 0.5f,
                    frozen = !overviewBackdropEnabled,
                    minRefreshIntervalMillis = 16L,
                )
                .drawBehind {
                    val groups = state.tabGroups
                    val lastIdx = groups.lastIndex.coerceAtLeast(0)
                    val pos = (pagerState.currentPage + pagerState.currentPageOffsetFraction)
                        .coerceIn(0f, lastIdx.toFloat())
                    val floorIdx = pos.toInt().coerceIn(0, lastIdx)
                    val ceilIdx = (floorIdx + 1).coerceIn(0, lastIdx)
                    val frac = (pos - floorIdx).coerceIn(0f, 1f)
                    val fPriv = if (groups.getOrNull(floorIdx)?.isPrivate == true) 1f else 0f
                    val cPriv = if (groups.getOrNull(ceilIdx)?.isPrivate == true) 1f else 0f
                    val darkT = if (isDark) 1f else lerp(fPriv, cPriv, frac)
                    val topColor = lerpColor(Color(0xFFE7ECF6), Color(0xFF1B191D), darkT)
                    val midColor = lerpColor(Color(0xFFDFE6F2), Color(0xFF141519), darkT)
                    val botColor = lerpColor(Color(0xFFD9E1EE), Color(0xFF101318), darkT)
                    drawRect(Brush.verticalGradient(listOf(topColor, midColor, botColor)))
                },
        ) {
            val isTablet = layoutProfile == BrowserLayoutProfile.TABLET_LANDSCAPE
            val isPortraitTablet = isTablet && maxWidth < maxHeight
            val gridCols = when (layoutProfile) {
                BrowserLayoutProfile.PHONE_PORTRAIT -> 2
                BrowserLayoutProfile.PHONE_LANDSCAPE -> 3
                BrowserLayoutProfile.TABLET_LANDSCAPE -> when {
                    isPortraitTablet -> 3
                    maxWidth >= 960.dp -> 4
                    else -> 3
                }
            }
            val cardAspectRatio = when (layoutProfile) {
                BrowserLayoutProfile.PHONE_PORTRAIT -> 0.68f
                BrowserLayoutProfile.TABLET_LANDSCAPE -> if (isPortraitTablet) 0.72f else 1.12f
                BrowserLayoutProfile.PHONE_LANDSCAPE -> 1.16f
            }
            val gridTopPad = statusBarTopDp + when {
                isTablet -> 72.dp
                layoutProfile == BrowserLayoutProfile.PHONE_LANDSCAPE -> 54.dp
                else -> 64.dp
            }
            val gridBottomPad = when {
                isTablet && !state.isOverviewSearchActive && !isSelectionMode -> 36.dp
                layoutProfile == BrowserLayoutProfile.PHONE_LANDSCAPE -> 88.dp
                else -> 128.dp
            }

            HorizontalPager(
                state = pagerState,
                beyondViewportPageCount = 0,
                flingBehavior = PagerDefaults.flingBehavior(
                    state = pagerState,
                    pagerSnapDistance = PagerSnapDistance.atMost(1),
                    snapAnimationSpec = pagerSnapSpring,
                    snapPositionalThreshold = 0.28f,
                ),
                modifier = Modifier.fillMaxSize(),
            ) { pageIndex ->
                val group = state.tabGroups.getOrNull(pageIndex) ?: return@HorizontalPager
                val allGroupTabs = state.tabsInGroup(group.id, includeSearchFilter = true)
                val (pinnedTabs, regularTabs) = remember(allGroupTabs) {
                    allGroupTabs.partition { it.isPinned }
                }
                val latestRegularTabs by rememberUpdatedState(regularTabs)
                val latestOnSelectTab by rememberUpdatedState(onSelectTab)
                val latestIsSelectionMode by rememberUpdatedState(isSelectionMode)

                LazyVerticalGrid(
                    columns = GridCells.Fixed(gridCols),
                    modifier = Modifier
                        .fillMaxSize()
                        .displayCutoutPadding(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = gridTopPad, bottom = gridBottomPad),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                if (!group.isDefault) {
                    item(key = "header_${group.id}", span = { GridItemSpan(maxLineSpan) }) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(bottom = 2.dp),
                        ) {
                            if (group.isPrivate) {
                                SafariPrivateMaskIcon(color = Color.White, modifier = Modifier.size(22.dp))
                            }
                            BasicText(
                                text = group.name,
                                style = TextStyle(
                                    color = if (group.isPrivate) Color.White else colors.ink,
                                    fontSize = 26.sp,
                                    fontWeight = FontWeight.Bold,
                                ),
                            )
                        }
                    }
                }

                if (pinnedTabs.isNotEmpty()) {
                    item(key = "pinned_${group.id}", span = { GridItemSpan(maxLineSpan) }) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            items(pinnedTabs, key = { "pin_${it.id}" }) { pinned ->
                                LiquidKitGlassSurface(
                                    backdrop = overviewBackdrop,
                                    shape = RoundedCornerShape(18.dp),
                                    interactive = true,
                                    onClick = {
                                        if (latestOverviewProgress() >= 0.15f) {
                                            latestOnSelectTab(pinned)
                                        }
                                    },
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(18.dp)
                                                .clip(CircleShape)
                                                .background(Color(pinned.accentColorArgb)),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            val pinIcon = state.favicons[pinned.id]
                                            if (pinIcon != null) {
                                                Image(
                                                    bitmap = pinIcon,
                                                    contentDescription = null,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize(),
                                                )
                                            } else {
                                                BasicText(
                                                    text = pinned.title.take(1).uppercase(),
                                                    style = TextStyle(color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                                )
                                            }
                                        }
                                        BasicText(
                                            text = pinned.title,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            style = TextStyle(color = colors.ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                items(
                    items = regularTabs,
                    key = { it.id },
                ) { tab ->
                    DisposableEffect(tab.id) {
                        onDispose {
                            closingTabIds.remove(tab.id)
                            selectedTabIds.remove(tab.id)
                        }
                    }

                    val isDraggingThis = draggedTabId == tab.id
                    val isLiftArmed = liftArmedTabId == tab.id
                    val isPressedThis = pressedTabId == tab.id
                    val isActive = tab.id == state.activeTabIdByGroup[group.id]
                    val isSelectedInMode = isSelectionMode && selectedTabIds[tab.id] == true
                    val swipeCloseOffset = remember(tab.id) { Animatable(0f) }
                    var swipeAnimJob by remember(tab.id) { mutableStateOf<Job?>(null) }
                    var isLiveSwiping by remember(tab.id) { mutableStateOf(false) }
                    var liveSwipeX by remember(tab.id) { mutableFloatStateOf(0f) }
                    val cardWidthPxRef = remember(tab.id) { FloatArray(1) }
                    val preTransformCoordsRef = remember(tab.id) { arrayOfNulls<LayoutCoordinates>(1) }
                    val previewBoundsRef = remember(tab.id) { arrayOfNulls<Rect>(1) }
                    val targetScale = when {
                        isDraggingThis -> 1.07f
                        isLiftArmed -> 1.04f
                        isPressedThis -> 0.97f
                        else -> 1f
                    }
                    val cardScale by animateFloatAsState(
                        targetValue = targetScale,
                        animationSpec = spring(dampingRatio = 0.78f, stiffness = 520f),
                        label = "TabCardScale",
                    )

                    Box(
                        modifier = Modifier
                            .animateItem(
                                placementSpec = spring(dampingRatio = 0.82f, stiffness = 420f),
                            )
                            .zIndex(if (isDraggingThis || isLiftArmed) 12f else 0f)
                            .onGloballyPositioned { coords ->
                                preTransformCoordsRef[0] = coords
                                cardWidthPxRef[0] = coords.size.width.toFloat()
                            }
                            .pointerInput(tab.id, group.id) {
                                val preHoldSlop = maxOf(viewConfiguration.touchSlop * 1.1f, with(density) { 10.dp.toPx() })
                                val swipeCloseSlop = minOf(viewConfiguration.touchSlop * 0.65f, with(density) { 6.dp.toPx() })
                                val liftReorderDragPx = with(density) { 10.dp.toPx() }
                                val menuEscapeDragPx = with(density) { 28.dp.toPx() }
                                val closeHitPx = with(density) { 40.dp.toPx() }
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    if (closingTabIds[tab.id] == true) return@awaitEachGesture
                                    val selectionActive = latestIsSelectionMode
                                    val inCloseBtn = !selectionActive && down.position.x > size.width - closeHitPx && down.position.y < closeHitPx
                                    val resumingSpringBack = !selectionActive &&
                                        !inCloseBtn &&
                                        (abs(swipeCloseOffset.value) > 1.5f || swipeCloseOffset.isRunning)

                                    if (!inCloseBtn) {
                                        pressedTabId = tab.id
                                    }

                                    fun toRootPos(local: Offset): Offset {
                                        val c = preTransformCoordsRef[0]
                                        return if (c != null && c.isAttached) c.localToRoot(local) else local
                                    }

                                    var prevRootPos = toRootPos(down.position)
                                    val vt = VelocityTracker()
                                    vt.addPosition(down.uptimeMillis, prevRootPos)
                                    var totalDx = 0f
                                    var totalDy = 0f
                                    var swipeTrackX = 0f
                                    var liftTravel = 0f
                                    var menuTravel = 0f
                                    var lastSwapAtMs = 0L
                                    var mode = 0 // 0=undecided, 1=scrollYield, 2=liftArmed(240..480ms), 3=swipeLeftClose, 4=contextMenuOpen(>=480ms), 5=draggingReorder

                                    if (resumingSpringBack) {
                                        swipeAnimJob?.cancel()
                                        swipeTrackX = swipeCloseOffset.value.coerceAtMost(0f)
                                        liveSwipeX = swipeTrackX
                                        isLiveSwiping = true
                                        pressedTabId = null
                                        mode = 3
                                        down.consume()
                                    }

                                    val holdJob = if (!inCloseBtn && !selectionActive && !resumingSpringBack) {
                                        scope.launch {
                                            delay(240L)
                                            if (mode == 0) {
                                                mode = 2
                                                pressedTabId = null
                                                liftArmedTabId = tab.id
                                                liftTravel = 0f
                                                triggerHaptic(LiquidKitHaptic.SelectionTick)
                                            }
                                            delay(240L)
                                            if (mode == 2) {
                                                mode = 4
                                                liftArmedTabId = null
                                                menuTravel = 0f
                                                triggerHaptic(LiquidKitHaptic.LongPressLift)
                                                contextCardTab = tab
                                                cardMenuSession += 1
                                                cardContextMenuState.show()
                                            }
                                        }
                                    } else {
                                        null
                                    }

                                    var handledRelease = false
                                    try {
                                        while (true) {
                                            val ev = awaitPointerEvent(PointerEventPass.Initial)
                                            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                                            val curRootPos = toRootPos(ch.position)
                                            val delta = curRootPos - prevRootPos
                                            prevRootPos = curRootPos
                                            if (!ch.pressed) {
                                                holdJob?.cancel()
                                                pressedTabId = null
                                                liftArmedTabId = null
                                                val vel = vt.calculateVelocity()
                                                when (mode) {
                                                    0 -> if (!inCloseBtn && latestOverviewProgress() >= 0.15f) {
                                                        if (selectionActive) {
                                                            if (selectedTabIds[tab.id] == true) {
                                                                selectedTabIds.remove(tab.id)
                                                            } else {
                                                                selectedTabIds[tab.id] = true
                                                            }
                                                            triggerHaptic(LiquidKitHaptic.SelectionTick)
                                                        } else {
                                                            previewBoundsRef[0]?.let {
                                                                cardBoundsMap[tab.id] = it
                                                            }
                                                            latestOnSelectTab(tab)
                                                        }
                                                    }
                                                    2 -> ch.consume()
                                                    4 -> ch.consume()
                                                    5 -> ch.consume()
                                                    3 -> {
                                                        ch.consume()
                                                        val releaseX = liveSwipeX
                                                        val cw = cardWidthPxRef[0].coerceAtLeast(with(density) { 160.dp.toPx() })
                                                        val shouldClose = (releaseX < -cw * 0.36f && vel.x <= 240f) ||
                                                            (vel.x < -480f && releaseX < -cw * 0.10f)
                                                        if (shouldClose) {
                                                            closingTabIds[tab.id] = true
                                                            triggerHaptic(LiquidKitHaptic.ImpactMedium)
                                                            swipeAnimJob?.cancel()
                                                            swipeAnimJob = scope.launch {
                                                                try {
                                                                    swipeCloseOffset.snapTo(releaseX)
                                                                    isLiveSwiping = false
                                                                    val targetOut = -cw * 1.35f
                                                                    val dist = (releaseX - targetOut).coerceAtLeast(1f)
                                                                    val dur = ((dist / maxOf(abs(vel.x), 1600f)) * 1000f)
                                                                        .roundToInt()
                                                                        .coerceIn(70, 155)
                                                                    swipeCloseOffset.animateTo(
                                                                        targetOut,
                                                                        tween(dur, easing = FastOutLinearInEasing),
                                                                    )
                                                                    pool.evictTab(tab.id)
                                                                    state.closeTab(tab.id)
                                                                } catch (e: kotlinx.coroutines.CancellationException) {
                                                                    closingTabIds.remove(tab.id)
                                                                    throw e
                                                                }
                                                            }
                                                        } else {
                                                            swipeAnimJob?.cancel()
                                                            swipeAnimJob = scope.launch {
                                                                swipeCloseOffset.snapTo(releaseX)
                                                                isLiveSwiping = false
                                                                swipeCloseOffset.animateTo(
                                                                    0f,
                                                                    spring(dampingRatio = 0.80f, stiffness = 540f),
                                                                    initialVelocity = vel.x.coerceIn(-2500f, 3500f),
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                                handledRelease = true
                                                break
                                            }
                                            vt.addPosition(ch.uptimeMillis, curRootPos)
                                            totalDx += delta.x
                                            totalDy += delta.y
                                            when (mode) {
                                                0 -> {
                                                    if (!selectionActive && totalDx < -swipeCloseSlop && abs(totalDx) > abs(totalDy) * 0.90f) {
                                                        holdJob?.cancel()
                                                        swipeAnimJob?.cancel()
                                                        pressedTabId = null
                                                        mode = 3
                                                        ch.consume()
                                                        swipeTrackX = totalDx.coerceAtMost(0f)
                                                        liveSwipeX = swipeTrackX
                                                        isLiveSwiping = true
                                                    } else if (totalDx > preHoldSlop || (abs(totalDy) > preHoldSlop && abs(totalDy) > abs(totalDx) * 1.15f)) {
                                                        holdJob?.cancel()
                                                        pressedTabId = null
                                                        mode = 1
                                                        break
                                                    }
                                                }
                                                3 -> {
                                                    ch.consume()
                                                    if (delta.x != 0f) {
                                                        swipeTrackX = (swipeTrackX + delta.x).coerceAtMost(0f)
                                                        liveSwipeX = swipeTrackX
                                                    }
                                                }
                                                2 -> {
                                                    ch.consume()
                                                    if (delta != Offset.Zero) {
                                                        liftTravel += delta.getDistance()
                                                        if (liftTravel >= liftReorderDragPx) {
                                                            holdJob?.cancel()
                                                            liftArmedTabId = null
                                                            draggedTabId = tab.id
                                                            dragOffset += delta
                                                            mode = 5
                                                        }
                                                    }
                                                }
                                                4 -> {
                                                    ch.consume()
                                                    if (delta != Offset.Zero) {
                                                        menuTravel += delta.getDistance()
                                                        if (menuTravel >= menuEscapeDragPx) {
                                                            cardContextMenuState.dismiss()
                                                            contextCardTab = null
                                                            draggedTabId = tab.id
                                                            dragOffset += delta
                                                            mode = 5
                                                        }
                                                    }
                                                }
                                                5 -> {
                                                    ch.consume()
                                                    if (delta != Offset.Zero) {
                                                        dragOffset += delta
                                                        val nowMs = SystemClock.uptimeMillis()
                                                        if (nowMs - lastSwapAtMs >= 160L) {
                                                            val currentBounds = cardBoundsMap[tab.id]
                                                            if (currentBounds != null) {
                                                                val draggedCenter = currentBounds.center + dragOffset
                                                                val targetEntry = cardBoundsMap.entries.firstOrNull { (otherId, rect) ->
                                                                    otherId != tab.id &&
                                                                        closingTabIds[otherId] != true &&
                                                                        latestRegularTabs.any { it.id == otherId } &&
                                                                        rect.deflate(rect.width * 0.15f).contains(draggedCenter)
                                                                }
                                                                if (targetEntry != null) {
                                                                    val targetBounds = targetEntry.value
                                                                    lastSwapAtMs = nowMs
                                                                    state.reorderTabsInGroup(group.id, tab.id, targetEntry.key)
                                                                    dragOffset -= (targetBounds.center - currentBounds.center)
                                                                    cardBoundsMap[tab.id] = targetBounds
                                                                    cardBoundsMap[targetEntry.key] = currentBounds
                                                                    triggerHaptic(LiquidKitHaptic.SelectionTick)
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    } finally {
                                        holdJob?.cancel()
                                        if (mode == 3 && !handledRelease && isLiveSwiping) {
                                            val curX = liveSwipeX
                                            swipeAnimJob?.cancel()
                                            swipeAnimJob = scope.launch {
                                                swipeCloseOffset.snapTo(curX)
                                                isLiveSwiping = false
                                                swipeCloseOffset.animateTo(0f, spring(0.80f, 540f))
                                            }
                                        }
                                        liftArmedTabId = null
                                        if (draggedTabId == tab.id) {
                                            draggedTabId = null
                                            dragOffset = Offset.Zero
                                        }
                                        if (pressedTabId == tab.id) {
                                            pressedTabId = null
                                        }
                                    }
                                }
                            },
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer {
                                    val tx = if (isLiveSwiping) liveSwipeX else swipeCloseOffset.value
                                    val dragDelta = if (isDraggingThis) dragOffset else Offset.Zero
                                    translationX = tx + dragDelta.x
                                    translationY = dragDelta.y
                                    scaleX = cardScale
                                    scaleY = cardScale
                                    val cwDenom = cardWidthPxRef[0].coerceAtLeast(1f) * 0.9f
                                    val swipeFade = 1f - (abs(tx) / cwDenom).coerceIn(0f, 0.88f)
                                    alpha = if (tab.id == latestHiddenCardTabId() || latestContextCardTab?.id == tab.id) 0f else swipeFade
                                },
                        ) {
                            OverviewTabCard(
                                tab = tab,
                                thumbnail = state.thumbnails[tab.id],
                                favicon = state.favicons[tab.id],
                                favorites = state.favorites,
                                isActive = isActive,
                                isDragged = isDraggingThis,
                                isPrivateGroup = group.isPrivate,
                                isDark = isDark || group.isPrivate,
                                aspectRatio = cardAspectRatio,
                                isSelectionMode = isSelectionMode,
                                isSelected = isSelectedInMode,
                                onPreviewPositioned = { rect ->
                                    previewBoundsRef[0] = rect
                                    if (!pagerState.isScrollInProgress && !isDraggingThis && closingTabIds[tab.id] != true) {
                                        cardBoundsMap[tab.id] = rect
                                    }
                                },
                                onClose = {
                                    if (closingTabIds[tab.id] != true) {
                                        closingTabIds[tab.id] = true
                                        pool.evictTab(tab.id)
                                        state.closeTab(tab.id)
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }

        }

        // Top Progressive Blur Veil keeping status bar & floating buttons legible with zero horizontal cutoff seam
        OverviewProgressiveBlurEdge(
            backdrop = overviewBackdrop,
            height = statusBarTopDp + 78.dp,
            isDark = overviewDark,
            isTop = true,
            blurEnabled = overviewBackdropEnabled,
            modifier = Modifier.align(Alignment.TopCenter),
        )

        // Top Floating Standard Liquid Glass Buttons / iPadOS Tablet Overview Toolbar
        val isTabletOverview = layoutProfile == BrowserLayoutProfile.TABLET_LANDSCAPE
        LiquidKitTheme(opacity = liquidKitAppearance().opacity, dark = overviewDark) {
            if (isTabletOverview) {
                TabletOverviewTopBar(
                    groups = state.tabGroups,
                    state = state,
                    pagerState = pagerState,
                    groupsMenuState = groupsMenuState,
                    groupsMenuItems = groupsMenuItems,
                    moreMenuState = moreMenuState,
                    moreMenuItems = moreMenuItems,
                    backdrop = overviewBackdrop,
                    ink = overviewColors.ink,
                    secondary = overviewColors.secondary,
                    accentBlue = accentBlue,
                    isSearchActive = state.isOverviewSearchActive,
                    onToggleSearch = { state.isOverviewSearchActive = !state.isOverviewSearchActive },
                    onAddTab = { onCreateNewTab(currentGroup.id) },
                    onDone = onCloseOverview,
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .displayCutoutPadding()
                        .padding(horizontal = 16.dp, vertical = if (layoutProfile == BrowserLayoutProfile.PHONE_LANDSCAPE) 6.dp else 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        LiquidKitIconButton(
                            onClick = { state.isOverviewSearchActive = !state.isOverviewSearchActive },
                            backdrop = overviewBackdrop,
                            size = 44.dp,
                        ) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                TerraSymbolIcon(
                                    symbol = LiquidKitSymbol.Search,
                                    tint = if (state.isOverviewSearchActive) accentBlue else overviewColors.ink,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }

                        LiquidKitAnchoredMenuHost(
                            state = groupsMenuState,
                            items = groupsMenuItems,
                            backdrop = overviewBackdrop,
                            presentation = LiquidKitMenuPresentation.ButtonTransform,
                        ) {
                            LiquidKitIconButton(
                                onClick = { if (groupsMenuItems.isNotEmpty()) groupsMenuState.show() },
                                backdrop = overviewBackdrop,
                                size = 44.dp,
                                modifier = Modifier
                                    .liquidKitMenuSource(groupsMenuState, cornerRadius = 22.dp)
                                    .then(
                                        if (groupsMenuItems.isNotEmpty() || groupsMenuState.expanded) {
                                            Modifier.liquidKitMenuTrigger(
                                                state = groupsMenuState,
                                                activation = LiquidKitMenuActivation.Press,
                                            )
                                        } else {
                                            Modifier
                                        },
                                    ),
                            ) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    TerraSymbolIcon(
                                        symbol = LiquidKitSymbol.List,
                                        tint = overviewColors.ink,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }
                    }

                    LiquidKitAnchoredMenuHost(
                        state = moreMenuState,
                        items = moreMenuItems,
                        backdrop = overviewBackdrop,
                        presentation = LiquidKitMenuPresentation.ButtonTransform,
                    ) {
                        LiquidKitIconButton(
                            onClick = { if (moreMenuItems.isNotEmpty()) moreMenuState.show() },
                            backdrop = overviewBackdrop,
                            size = 44.dp,
                            modifier = Modifier
                                .liquidKitMenuSource(moreMenuState, cornerRadius = 22.dp)
                                .then(
                                    if (moreMenuItems.isNotEmpty() || moreMenuState.expanded) {
                                        Modifier.liquidKitMenuTrigger(
                                            state = moreMenuState,
                                            activation = LiquidKitMenuActivation.Press,
                                        )
                                    } else {
                                        Modifier
                                    },
                                ),
                        ) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                TerraSymbolIcon(
                                    symbol = LiquidKitSymbol.More,
                                    tint = overviewColors.ink,
                                    modifier = Modifier.size(19.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        // Bottom Progressive Blur Veil so scrolled card × buttons & titles fade cleanly behind bottom bar
        val navBottomDp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        if (!isTabletOverview || state.isOverviewSearchActive || isSelectionMode) {
            OverviewProgressiveBlurEdge(
                backdrop = overviewBackdrop,
                height = navBottomDp + if (layoutProfile == BrowserLayoutProfile.PHONE_LANDSCAPE) 78.dp else 108.dp,
                isDark = overviewDark,
                isTop = false,
                blurEnabled = overviewBackdropEnabled,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }

        // Bottom Chrome: Search Bar, Selection Action Bar, or Phone Dynamic Expanding Group Switcher
        val barMaxWidth = if (layoutProfile == BrowserLayoutProfile.PHONE_PORTRAIT) 520.dp else 440.dp
        val barVertPad = if (layoutProfile == BrowserLayoutProfile.PHONE_LANDSCAPE) 6.dp else 12.dp
        if (!isTabletOverview || state.isOverviewSearchActive || isSelectionMode) {
            LiquidKitTheme(opacity = liquidKitAppearance().opacity, dark = overviewDark) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .widthIn(max = barMaxWidth)
                    .then(if (state.isOverviewSearchActive) Modifier.imePadding() else Modifier)
                    .navigationBarsPadding()
                    .displayCutoutPadding()
                    .padding(horizontal = 16.dp, vertical = barVertPad),
            ) {
                if (state.isOverviewSearchActive) {
                    OverviewSearchBar(
                        query = state.overviewSearchQuery,
                        onQueryChange = { state.overviewSearchQuery = it },
                        onDismiss = {
                            state.isOverviewSearchActive = false
                            state.overviewSearchQuery = ""
                        },
                        backdrop = overviewBackdrop,
                        ink = overviewColors.ink,
                        secondary = overviewColors.secondary,
                    )
                } else if (isSelectionMode) {
                    val selectedIds = selectedTabIds.keys.toList()
                    OverviewSelectionBar(
                        selectedCount = selectedIds.size,
                        backdrop = overviewBackdrop,
                        ink = overviewColors.ink,
                        secondary = overviewColors.secondary,
                        accentBlue = accentBlue,
                        isDarkTheme = overviewDark,
                        onCopyLinks = {
                            val links = state.tabsInGroup(currentGroup.id)
                                .filter { selectedTabIds[it.id] == true && it.url.isNotBlank() }
                                .joinToString("\n") { it.url }
                            if (links.isNotEmpty()) {
                                clipboard.setText(AnnotatedString(links))
                                state.showBanner("已拷贝所选链接")
                            } else {
                                state.showBanner("所选标签页暂无网页链接")
                            }
                            isSelectionMode = false
                            selectedTabIds.clear()
                        },
                        onCloseSelected = {
                            selectedIds.forEach { id ->
                                closingTabIds[id] = true
                                pool.evictTab(id)
                                state.closeTab(id)
                            }
                            isSelectionMode = false
                            selectedTabIds.clear()
                        },
                        onDone = {
                            isSelectionMode = false
                            selectedTabIds.clear()
                        },
                    )
                } else {
                    val isExpandedBar = isScrubbingGroupPill
                    DynamicBottomGroupBar(
                        groups = state.tabGroups,
                        state = state,
                        pool = pool,
                        pagerState = pagerState,
                        isExpandedBar = isExpandedBar,
                        backdrop = overviewBackdrop,
                        ink = overviewColors.ink,
                        secondary = overviewColors.secondary,
                        accentBlue = accentBlue,
                        isDarkTheme = overviewDark,
                        onScrubbingChange = { isScrubbingGroupPill = it },
                        onEnterSelectionMode = {
                            isSelectionMode = true
                            selectedTabIds.clear()
                        },
                        onAddTab = { onCreateNewTab(currentGroup.id) },
                        onDone = onCloseOverview,
                    )
                }
            }
            }
        }

        // Real LiquidKit Card Context Menu (authentic refractive Liquid Glass with Move Submenu)
        LiquidKitContextMenuHost(
            state = cardContextMenuState,
            backdrop = overviewBackdrop,
            submenuInk = overviewColors.ink,
            submenuTint = Color.Unspecified,
            modifier = Modifier.fillMaxSize(),
            rootOverlay = { behind ->
                val target = contextCardTab
                if (cardContextMenuState.expanded && target != null && cardContextMenuItems.isNotEmpty()) {
                    val reveal = remember(target.id, cardMenuSession) { Animatable(0f) }
                    LaunchedEffect(target.id, cardMenuSession) {
                        reveal.snapTo(0f)
                        reveal.animateTo(1f, LiquidKitSpring.swiftUI(0.30f, 0.84f).spec)
                    }
                    val menuMetrics = liquidKitMenuMetrics()

                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxSize()
                            .displayCutoutPadding(),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .drawBehind {
                                    val p = reveal.value.coerceIn(0f, 1f)
                                    drawRect(Color.Black.copy(alpha = 0.52f * p))
                                }
                                .pointerInput(target.id, cardMenuSession) {
                                    awaitEachGesture {
                                        val down = awaitFirstDown(requireUnconsumed = false)
                                        down.consume()
                                        cardContextMenuState.dismiss()
                                        contextCardTab = null
                                        while (true) {
                                            val ev = awaitPointerEvent(PointerEventPass.Initial)
                                            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                                            ch.consume()
                                            if (!ch.pressed) break
                                        }
                                    }
                                },
                        )

                        val isLandscape = layoutProfile != BrowserLayoutProfile.PHONE_PORTRAIT
                        val ctxAspect = if (isLandscape) 1.16f else 0.68f
                        val bounds = cardBoundsMap[target.id]
                        val cardW = if (bounds != null && bounds.width > 10f) {
                            with(density) { bounds.width.toDp() }
                        } else {
                            ((maxWidth - 46.dp) * 0.5f).coerceInSafe(120.dp, 200.dp)
                        }
                        val previewW = if (isLandscape) minOf(cardW, 240.dp) else cardW
                        val cardH = previewW / ctxAspect + 26.dp
                        val safeTop = statusBarTopDp + 10.dp
                        val safeBottom = (maxHeight - navBottomDp - 10.dp).coerceAtLeast(safeTop + 120.dp)
                        val availW = (maxWidth - 24.dp).coerceAtLeast(120.dp)
                        val menuW = minOf(menuMetrics.width, availW)
                        val gapDp = 12.dp

                        val cardLeftOrig = if (bounds != null) with(density) { bounds.left.toDp() } else 16.dp
                        val cardTopOrig = if (bounds != null) with(density) { bounds.top.toDp() } else safeTop + 20.dp

                        val menuH: androidx.compose.ui.unit.Dp
                        val cardDestLeft: androidx.compose.ui.unit.Dp
                        val cardDestTop: androidx.compose.ui.unit.Dp
                        val menuLeft: androidx.compose.ui.unit.Dp
                        val menuDestTop: androidx.compose.ui.unit.Dp

                        if (isLandscape && maxWidth >= previewW + menuW + gapDp + 24.dp) {
                            // Side-by-side placement in landscape so card & menu never overflow vertical viewport
                            menuH = minOf(menuMetrics.height(cardContextMenuItems), (safeBottom - safeTop).coerceAtLeast(140.dp))
                            val isCardInRightHalf = (cardLeftOrig + previewW * 0.5f) > maxWidth * 0.5f
                            if (isCardInRightHalf) {
                                cardDestLeft = cardLeftOrig.coerceInSafe(menuW + gapDp + 12.dp, maxWidth - previewW - 12.dp)
                                menuLeft = (cardDestLeft - gapDp - menuW).coerceInSafe(12.dp, maxWidth - menuW - 12.dp)
                            } else {
                                cardDestLeft = cardLeftOrig.coerceInSafe(12.dp, maxWidth - previewW - menuW - gapDp - 12.dp)
                                menuLeft = (cardDestLeft + previewW + gapDp).coerceInSafe(12.dp, maxWidth - menuW - 12.dp)
                            }
                            cardDestTop = cardTopOrig.coerceInSafe(safeTop, safeBottom - cardH)
                            menuDestTop = cardDestTop.coerceInSafe(safeTop, safeBottom - menuH)
                        } else {
                            val portraitMenuBottom = (safeBottom - 64.dp).coerceAtLeast(safeTop + 140.dp)
                            val availPortraitH = (portraitMenuBottom - safeTop).coerceAtLeast(140.dp)
                            val maxMenuH = (availPortraitH - cardH - gapDp).coerceAtLeast(minOf(160.dp, availPortraitH))
                            menuH = minOf(menuMetrics.height(cardContextMenuItems), maxMenuH)
                            val openAbove = (cardTopOrig + cardH * 0.5f) > maxHeight * 0.54f
                            cardDestLeft = cardLeftOrig.coerceInSafe(12.dp, maxWidth - previewW - 12.dp)
                            if (openAbove) {
                                val minCardBottom = minOf(safeTop + menuH + gapDp + cardH, portraitMenuBottom)
                                val clampedCardBottom = (cardTopOrig + cardH).coerceInSafe(minCardBottom, portraitMenuBottom)
                                cardDestTop = (clampedCardBottom - cardH).coerceAtLeast(safeTop)
                                menuDestTop = (cardDestTop - gapDp - menuH).coerceInSafe(safeTop, portraitMenuBottom - menuH)
                            } else {
                                val maxTotalTop = (portraitMenuBottom - (cardH + gapDp + menuH)).coerceAtLeast(safeTop)
                                cardDestTop = cardTopOrig.coerceInSafe(safeTop, maxTotalTop)
                                menuDestTop = (cardDestTop + cardH + gapDp).coerceInSafe(safeTop, portraitMenuBottom - menuH)
                            }
                            val isRightColumn = (cardLeftOrig + cardW * 0.5f) > maxWidth * 0.5f
                            menuLeft = if (isRightColumn) {
                                (cardLeftOrig + cardW - menuW).coerceInSafe(12.dp, maxWidth - menuW - 12.dp)
                            } else {
                                cardLeftOrig.coerceInSafe(12.dp, maxWidth - menuW - 12.dp)
                            }
                        }

                        val cardLeftOrigPx = with(density) { cardLeftOrig.toPx() }
                        val cardTopOrigPx = with(density) { cardTopOrig.toPx() }
                        val cardDestLeftPx = with(density) { cardDestLeft.toPx() }
                        val cardDestTopPx = with(density) { cardDestTop.toPx() }

                        // Lifted live card preview anchored in its column (120Hz deferred offset & scale)
                        Box(
                            modifier = Modifier
                                .align(AbsoluteAlignment.TopLeft)
                                .offset {
                                    val p = reveal.value.coerceIn(0f, 1f)
                                    IntOffset(
                                        lerp(cardLeftOrigPx, cardDestLeftPx, p).roundToInt(),
                                        lerp(cardTopOrigPx, cardDestTopPx, p).roundToInt(),
                                    )
                                }
                                .width(previewW)
                                .graphicsLayer {
                                    val p = reveal.value.coerceIn(0f, 1f)
                                    val s = lerp(1f, 1.04f, p)
                                    scaleX = s
                                    scaleY = s
                                },
                        ) {
                            OverviewTabCard(
                                tab = target,
                                thumbnail = state.thumbnails[target.id],
                                favicon = state.favicons[target.id],
                                isActive = false,
                                isDragged = false,
                                isPrivateGroup = currentGroup.isPrivate,
                                isDark = isDark || currentGroup.isPrivate,
                                aspectRatio = ctxAspect,
                                onClose = {},
                            )
                        }

                        // 250.dp native LiquidKit menu card with Move Submenu support
                        LiquidKitGlassSurface(
                            backdrop = behind,
                            shape = OverviewMenuShape,
                            tint = Color.Unspecified,
                            modifier = Modifier
                                .align(AbsoluteAlignment.TopLeft)
                                .absoluteOffset(x = menuLeft, y = menuDestTop)
                                .size(width = menuW, height = menuH)
                                .graphicsLayer {
                                    val p = reveal.value.coerceIn(0f, 1f)
                                    alpha = p
                                    val s = lerp(0.86f, 1f, p)
                                    scaleX = s
                                    scaleY = s
                                },
                        ) {
                            LiquidKitMenuPanel(
                                state = cardContextMenuState,
                                items = cardContextMenuItems,
                                ink = overviewColors.ink,
                                modifier = Modifier.requiredSize(menuW, menuH),
                            )
                        }
                    }
                }
            },
        ) {}
    }
}

@Composable
private fun TabletOverviewTopBar(
    groups: List<TabGroup>,
    state: TerraBrowserState,
    pagerState: androidx.compose.foundation.pager.PagerState,
    groupsMenuState: io.liquidkit.LiquidKitMenuState,
    groupsMenuItems: List<LiquidKitMenuItem>,
    moreMenuState: io.liquidkit.LiquidKitMenuState,
    moreMenuItems: List<LiquidKitMenuItem>,
    backdrop: Backdrop,
    ink: Color,
    secondary: Color,
    accentBlue: Color,
    isSearchActive: Boolean,
    onToggleSearch: () -> Unit,
    onAddTab: () -> Unit,
    onDone: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val pillHighlightColor = if (state.activeGroup.isPrivate || liquidKitAppearance().dark) {
        Color.White.copy(alpha = 0.16f)
    } else {
        Color.Black.copy(alpha = 0.08f)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .displayCutoutPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier.align(Alignment.CenterStart),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LiquidKitAnchoredMenuHost(
                state = groupsMenuState,
                items = groupsMenuItems,
                backdrop = backdrop,
                presentation = LiquidKitMenuPresentation.ButtonTransform,
            ) {
                LiquidKitIconButton(
                    onClick = { if (groupsMenuItems.isNotEmpty()) groupsMenuState.show() },
                    backdrop = backdrop,
                    size = 44.dp,
                    modifier = Modifier
                        .liquidKitMenuSource(groupsMenuState, cornerRadius = 22.dp)
                        .then(
                            if (groupsMenuItems.isNotEmpty() || groupsMenuState.expanded) {
                                Modifier.liquidKitMenuTrigger(
                                    state = groupsMenuState,
                                    activation = LiquidKitMenuActivation.Press,
                                )
                            } else {
                                Modifier
                            },
                        ),
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        SafariSidebarIcon(color = ink, modifier = Modifier.size(18.dp))
                    }
                }
            }

            LiquidKitAnchoredMenuHost(
                state = moreMenuState,
                items = moreMenuItems,
                backdrop = backdrop,
                presentation = LiquidKitMenuPresentation.ButtonTransform,
            ) {
                LiquidKitIconButton(
                    onClick = { if (moreMenuItems.isNotEmpty()) moreMenuState.show() },
                    backdrop = backdrop,
                    size = 44.dp,
                    modifier = Modifier
                        .liquidKitMenuSource(moreMenuState, cornerRadius = 22.dp)
                        .then(
                            if (moreMenuItems.isNotEmpty() || moreMenuState.expanded) {
                                Modifier.liquidKitMenuTrigger(
                                    state = moreMenuState,
                                    activation = LiquidKitMenuActivation.Press,
                                )
                            } else {
                                Modifier
                            },
                        ),
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        TerraSymbolIcon(symbol = LiquidKitSymbol.More, tint = ink, modifier = Modifier.size(19.dp))
                    }
                }
            }
        }

        // Center iPadOS Group Switcher Capsule
        LiquidKitGlassSurface(
            backdrop = backdrop,
            shape = CircleShape,
            modifier = Modifier
                .align(Alignment.Center)
                .widthIn(max = 420.dp)
                .height(44.dp),
        ) {
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                groups.forEachIndexed { idx, grp ->
                    val selected = pagerState.currentPage == idx
                    val title = if (grp.isPrivate) "无痕浏览" else if (grp.isDefault) "${state.tabsInGroup(grp.id).size} 个标签页" else grp.name
                    Box(
                        modifier = Modifier
                            .height(36.dp)
                            .clip(CircleShape)
                            .background(if (selected) pillHighlightColor else Color.Transparent)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {
                                scope.launch {
                                    pagerState.animateScrollToPage(idx)
                                    state.selectGroup(grp.id)
                                }
                            }
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        BasicText(
                            text = title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = TextStyle(
                                color = if (selected) ink else secondary,
                                fontSize = 14.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            ),
                        )
                    }
                }
            }
        }

        // Right cluster: [+ | Search] combined pill + Blue Done Check
        Row(
            modifier = Modifier.align(Alignment.CenterEnd),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LiquidKitGlassSurface(
                backdrop = backdrop,
                shape = CircleShape,
                interactive = true,
                modifier = Modifier.height(44.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onAddTab,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        TerraSymbolIcon(symbol = LiquidKitSymbol.Add, tint = ink, modifier = Modifier.size(19.dp))
                    }
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onToggleSearch,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        TerraSymbolIcon(
                            symbol = LiquidKitSymbol.Search,
                            tint = if (isSearchActive) accentBlue else ink,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            LiquidKitIconButton(
                onClick = onDone,
                backdrop = backdrop,
                size = 44.dp,
                tint = accentBlue,
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    TerraSymbolIcon(symbol = LiquidKitSymbol.Check, tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun OverviewProgressiveBlurEdge(
    backdrop: Backdrop,
    height: androidx.compose.ui.unit.Dp,
    isDark: Boolean,
    isTop: Boolean,
    blurEnabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val baseColor = when {
        isTop && isDark -> Color(0xFF141519)
        isTop -> Color(0xFFE7ECF6)
        isDark -> Color(0xFF0E1015)
        else -> Color(0xFFE3EAF4)
    }
    val tintBrush = remember(isDark, isTop) {
        if (isTop) {
            Brush.verticalGradient(
                0f to baseColor.copy(alpha = if (isDark) 0.90f else 0.88f),
                0.24f to baseColor.copy(alpha = if (isDark) 0.78f else 0.74f),
                0.52f to baseColor.copy(alpha = if (isDark) 0.48f else 0.44f),
                0.76f to baseColor.copy(alpha = if (isDark) 0.18f else 0.16f),
                0.90f to baseColor.copy(alpha = if (isDark) 0.04f else 0.03f),
                1f to Color.Transparent,
            )
        } else {
            Brush.verticalGradient(
                0f to Color.Transparent,
                0.18f to baseColor.copy(alpha = if (isDark) 0.16f else 0.14f),
                0.38f to baseColor.copy(alpha = if (isDark) 0.46f else 0.40f),
                0.62f to baseColor.copy(alpha = if (isDark) 0.76f else 0.70f),
                0.84f to baseColor.copy(alpha = if (isDark) 0.91f else 0.88f),
                1f to baseColor.copy(alpha = if (isDark) 0.97f else 0.96f),
            )
        }
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .drawBehind {
                drawRect(brush = tintBrush)
            },
    )
}

@Composable
private fun OverviewSelectionBar(
    selectedCount: Int,
    backdrop: Backdrop,
    ink: Color,
    secondary: Color,
    accentBlue: Color,
    isDarkTheme: Boolean = liquidKitAppearance().dark,
    onCopyLinks: () -> Unit,
    onCloseSelected: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectionDark = isDarkTheme
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val selectionTint = if (selectionDark) Color(0xFF1F222B).copy(alpha = 0.72f) else Color(0xFFF6F7FB).copy(alpha = 0.74f)
        val selectionBorder = Color.White.copy(alpha = if (selectionDark) 0.16f else 0.65f)
        LiquidKitGlassSurface(
            backdrop = backdrop,
            shape = CircleShape,
            tint = selectionTint,
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .border(0.6.dp, selectionBorder, CircleShape),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                LiquidKitText(
                    text = if (selectedCount > 0) "已选 $selectedCount 项" else "选择标签页",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = liquidKitTypography().footnote,
                    color = ink,
                    fontWeight = FontWeight.SemiBold,
                    monospacedDigits = true,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BasicText(
                        text = "拷贝链接",
                        style = TextStyle(
                            color = if (selectedCount > 0) accentBlue else secondary.copy(alpha = 0.55f),
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                        modifier = if (selectedCount > 0) {
                            Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onCopyLinks,
                            )
                        } else {
                            Modifier
                        },
                    )
                    BasicText(
                        text = "关闭",
                        style = TextStyle(
                            color = if (selectedCount > 0) Color(0xFFFF453A) else secondary.copy(alpha = 0.55f),
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        modifier = if (selectedCount > 0) {
                            Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onCloseSelected,
                            )
                        } else {
                            Modifier
                        },
                    )
                }
            }
        }

        LiquidKitIconButton(
            onClick = onDone,
            backdrop = backdrop,
            size = 48.dp,
            tint = accentBlue,
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                TerraSymbolIcon(symbol = LiquidKitSymbol.Check, tint = Color.White, modifier = Modifier.size(19.dp))
            }
        }
    }
}

@Composable
private fun OverviewTabCard(
    tab: BrowserTab,
    thumbnail: androidx.compose.ui.graphics.ImageBitmap?,
    favicon: androidx.compose.ui.graphics.ImageBitmap? = null,
    favorites: List<FavoriteItem> = emptyList(),
    isActive: Boolean,
    isDragged: Boolean,
    isPrivateGroup: Boolean,
    isDark: Boolean,
    aspectRatio: Float = 0.68f,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onPreviewPositioned: ((Rect) -> Unit)? = null,
    onClose: () -> Unit,
) {
    val accentBlue = OverviewAccentBlue
    val cardSurface = if (isDark) Color(0xFF1F222A) else Color(0xFFFDFEFF)
    val labelColor = if (isDark || isPrivateGroup) Color.White else Color(0xFF111318)

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(aspectRatio)
                .onGloballyPositioned { coords ->
                    if (coords.isAttached) {
                        onPreviewPositioned?.invoke(coords.boundsInRoot())
                    }
                }
                .clip(OverviewCardShape)
                .background(cardSurface)
                .border(
                    width = when {
                        isSelected || isDragged -> 2.6.dp
                        isActive -> 2.2.dp
                        else -> 0.75.dp
                    },
                    color = when {
                        isSelected || isDragged || isActive -> accentBlue
                        isDark -> Color.White.copy(alpha = 0.16f)
                        else -> Color(0xFF1A2233).copy(alpha = 0.12f)
                    },
                    shape = OverviewCardShape,
                ),
        ) {
            if (thumbnail != null && !tab.isStartPage) {
                Image(
                    bitmap = thumbnail,
                    contentDescription = tab.title,
                    alignment = Alignment.TopCenter,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else if (tab.isStartPage) {
                MiniStartPagePreview(isPrivate = isPrivateGroup, isDark = isDark, favorites = favorites)
            } else {
                MiniWebPreviewFallback(tab = tab, isDark = isDark)
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .align(Alignment.BottomCenter)
                    .background(OverviewCardBottomScrimBrush),
            )

            if (isSelectionMode) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) accentBlue else Color.Black.copy(alpha = 0.35f))
                        .border(1.5.dp, Color.White.copy(alpha = if (isSelected) 0.95f else 0.75f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isSelected) {
                        TerraSymbolIcon(
                            symbol = LiquidKitSymbol.Check,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            } else {
                // Top-right circular close button with generous 36dp hit target
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(2.dp)
                        .size(36.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onClose,
                        ),
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

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier.padding(horizontal = 4.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(15.dp)
                    .clip(OverviewFaviconShape)
                    .background(Color(tab.accentColorArgb)),
                contentAlignment = Alignment.Center,
            ) {
                if (favicon != null && !tab.isStartPage) {
                    Image(
                        bitmap = favicon,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else if (tab.isStartPage) {
                    SafariCompassIcon(color = Color.White, modifier = Modifier.size(10.dp))
                } else {
                    BasicText(
                        text = tab.title.take(1).uppercase(),
                        style = TextStyle(color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold),
                    )
                }
            }
            BasicText(
                text = tab.title.ifBlank { "起始页" },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(color = labelColor, fontSize = 12.sp, fontWeight = FontWeight.Medium),
            )
        }
    }
}

@Composable
private fun MiniStartPagePreview(
    isPrivate: Boolean,
    isDark: Boolean,
    favorites: List<FavoriteItem> = emptyList(),
) {
    val bg = if (isPrivate) Color(0xFF1B1C21) else if (isDark) Color(0xFF161A24) else Color(0xFFF1F4FA)
    val surfaceBar = if (isDark || isPrivate) Color.White.copy(alpha = 0.08f) else Color.White
    val mutedInk = if (isPrivate || isDark) Color.White.copy(alpha = 0.45f) else Color(0xFF8E8E93)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
            .padding(12.dp),
        verticalArrangement = if (favorites.isEmpty()) Arrangement.Center else Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (favorites.isEmpty()) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(surfaceBar),
                contentAlignment = Alignment.Center,
            ) {
                if (isPrivate) {
                    SafariPrivateMaskIcon(color = mutedInk, modifier = Modifier.size(18.dp))
                } else {
                    SafariCompassIcon(color = OverviewAccentBlue, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.84f)
                    .height(22.dp)
                    .clip(MiniStartBarShape)
                    .background(surfaceBar),
            )
        } else {
            Spacer(Modifier.height(6.dp))
            favorites.take(8).chunked(4).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    row.forEach { fav ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(MiniStartTileShape)
                                .background(Color(fav.colorArgb)),
                        )
                    }
                    repeat(4 - row.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(26.dp)
                    .clip(MiniStartBarShape)
                    .background(surfaceBar),
            )
        }
    }
}

@Composable
private fun MiniWebPreviewFallback(tab: BrowserTab, isDark: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isDark) Color(0xFF191B22) else Color.White)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(22.dp)
                .clip(MiniStartTileShape)
                .background(Color(tab.accentColorArgb).copy(alpha = 0.85f)),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicText(
                text = tab.host.ifBlank { tab.title },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth(0.76f)
                .height(10.dp)
                .clip(MiniWebLineShape)
                .background(Color.Gray.copy(alpha = 0.25f)),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .clip(MiniWebHeroShape)
                .background(Color(tab.accentColorArgb).copy(alpha = 0.14f)),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .height(8.dp)
                .clip(MiniWebLineShape)
                .background(Color.Gray.copy(alpha = 0.18f)),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(0.65f)
                .height(8.dp)
                .clip(MiniWebLineShape)
                .background(Color.Gray.copy(alpha = 0.18f)),
        )
    }
}

/**
 * Dynamic Expanding Bottom Group Switcher Pill:
 * - Resting edge (private / default group): 2-item layout `[ 无痕浏览 | N个标签页 ]`
 * - Resting middle group: 3-item center layout `[ prev | (active) | next ]`
 * - Tapping active capsule opens group actions (`管理标签页组 / 选择标签页 / 标签页排列方式 / 拷贝链接 / 添加书签`)
 * - Dragging pill or swiping HorizontalPager: side [+] and [✓] smoothly shrink & fade out,
 *   while the center Liquid Glass pill expands to full width and slides the group track smoothly.
 */
@Composable
private fun DynamicBottomGroupBar(
    groups: List<TabGroup>,
    state: TerraBrowserState,
    @Suppress("UNUSED_PARAMETER") pool: TerraWebViewPool,
    pagerState: androidx.compose.foundation.pager.PagerState,
    isExpandedBar: Boolean,
    backdrop: Backdrop,
    ink: Color,
    @Suppress("UNUSED_PARAMETER") secondary: Color,
    accentBlue: Color,
    isDarkTheme: Boolean = liquidKitAppearance().dark,
    onScrubbingChange: (Boolean) -> Unit,
    onEnterSelectionMode: () -> Unit,
    onAddTab: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (groups.isEmpty()) return

    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val triggerHaptic = rememberLiquidKitHaptics()
    val density = LocalDensity.current

    var isTabInteracting by remember { mutableStateOf(false) }
    val isSwitching = isExpandedBar || isTabInteracting

    val count = groups.size.coerceAtLeast(1)
    val pagePositionState = remember(pagerState, count) {
        derivedStateOf {
            (pagerState.currentPage + pagerState.currentPageOffsetFraction)
                .coerceIn(0f, (count - 1).toFloat())
        }
    }

    val activeGroup by remember(groups) {
        derivedStateOf { groups.getOrNull(pagerState.settledPage) ?: state.activeGroup }
    }
    val pagerSnapSpring = remember { LiquidKitSpring.swiftUI(0.33f, 0.94f).spec }
    val groupTabs = state.tabsInGroup(activeGroup.id)
    val webUrlCount = groupTabs.count { !it.isStartPage && it.url.isNotBlank() }

    val groupPillMenuItems = remember(activeGroup.id, groupTabs.size, webUrlCount, state.sortMode) {
        listOf(
            LiquidKitMenuItem("manage_groups", "管理标签页组", symbol = LiquidKitSymbol.Folder),
            LiquidKitMenuItem("select_tabs", "选择标签页", symbol = LiquidKitSymbol.CheckCircle),
            LiquidKitMenuItem(
                key = "sort_submenu",
                title = "标签页排列方式",
                symbol = LiquidKitSymbol.List,
                startsSection = true,
                children = listOf(
                    LiquidKitMenuItem("sort_title", "按标题排列标签页", checked = state.sortMode == TabSortMode.TITLE),
                    LiquidKitMenuItem("sort_site", "按网站排列标签页", checked = state.sortMode == TabSortMode.WEBSITE),
                ),
            ),
            LiquidKitMenuItem(
                key = "copy_links",
                title = "拷贝 $webUrlCount 个链接",
                symbol = LiquidKitSymbol.Link,
                startsSection = true,
                enabled = webUrlCount > 0,
            ),
            LiquidKitMenuItem(
                key = "bookmark_all",
                title = "为 $webUrlCount 个标签页添加书签",
                symbol = LiquidKitSymbol.Bookmark,
                enabled = webUrlCount > 0,
            ),
        )
    }

    val groupPillMenuState = rememberLiquidKitMenuState { picked ->
        when (picked.key) {
            "manage_groups" -> state.showManageGroupsSheet = true
            "select_tabs" -> onEnterSelectionMode()
            "sort_title" -> state.sortTabsInGroup(activeGroup.id, TabSortMode.TITLE)
            "sort_site" -> state.organizeTabsByWebsite(activeGroup.id)
            "copy_links" -> {
                val links = groupTabs.mapNotNull { it.url.takeIf { u -> u.isNotBlank() } }.joinToString("\n")
                if (links.isNotEmpty()) {
                    clipboard.setText(AnnotatedString(links))
                    state.showBanner("已拷贝 $webUrlCount 个链接")
                }
            }
            "bookmark_all" -> {
                val added = state.bookmarkAllTabsInGroup(activeGroup.id)
                state.showBanner(if (added > 0) "已将 $added 个标签页添加到书签" else "本组标签页已在书签中")
            }
        }
    }

    BackHandler(enabled = groupPillMenuState.expanded) { groupPillMenuState.dismiss() }

    var pillScrollJob by remember { mutableStateOf<Job?>(null) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        contentAlignment = Alignment.Center,
    ) {
        val fullWidth = maxWidth
        val sideBtnWidth = 48.dp
        val gap = 10.dp
        val fullWidthPx = with(density) { fullWidth.toPx() }.coerceAtLeast(1f)
        val sideBtnWidthPx = with(density) { sideBtnWidth.toPx() }
        val gapPx = with(density) { gap.toPx() }
        val maxRestingW = (fullWidthPx - sideBtnWidthPx * 2f - gapPx * 2f)
            .coerceAtLeast(with(density) { 160.dp.toPx() })
        val restingPillWidthPx = if (count <= 1) {
            minOf(maxRestingW, with(density) { 148.dp.toPx() })
        } else {
            maxRestingW
        }
        val expandedPillWidthPx = fullWidthPx.coerceAtLeast(restingPillWidthPx)
        val canExpandOnScrub = count >= 2 && (expandedPillWidthPx - restingPillWidthPx) > with(density) { 6.dp.toPx() }

        val morphSpring = remember { LiquidKitSpring.swiftUI(0.32f, 0.84f) }
        val expandFractionState = animateFloatAsState(
            targetValue = if (canExpandOnScrub && isSwitching) 1f else 0f,
            animationSpec = morphSpring.spec,
            label = "GroupPillExpand",
        )

        val sideTuckPx = with(density) { 12.dp.toPx() }
        val pillHeightPx = with(density) { 48.dp.toPx() }.roundToInt()
        val pagerWidthPx = with(density) { (fullWidth + 32.dp).toPx() }
        // Opaque muted tint because TabInkShader multiplies glyph alpha * ink.a inside the lens.
        val unselectedTabInk = if (isDarkTheme) Color(0xFF9A9FA8) else Color(0xFF6E737D)

        // Side [+] and [✓] stay composed and animate smoothly via graphicsLayer (zero unmount pop)
        LiquidKitIconButton(
            onClick = { if (expandFractionState.value < 0.45f) onAddTab() },
            backdrop = backdrop,
            size = sideBtnWidth,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .graphicsLayer {
                    val e = expandFractionState.value
                    alpha = (1f - e * 1.55f).coerceIn(0f, 1f)
                    val s = lerp(1f, 0.40f, e)
                    scaleX = s
                    scaleY = s
                    translationX = lerp(0f, sideTuckPx, e)
                },
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                TerraSymbolIcon(symbol = LiquidKitSymbol.Add, tint = ink, modifier = Modifier.size(20.dp))
            }
        }

        LiquidKitIconButton(
            onClick = { if (expandFractionState.value < 0.45f) onDone() },
            backdrop = backdrop,
            size = sideBtnWidth,
            tint = accentBlue,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .graphicsLayer {
                    val e = expandFractionState.value
                    alpha = (1f - e * 1.55f).coerceIn(0f, 1f)
                    val s = lerp(1f, 0.40f, e)
                    scaleX = s
                    scaleY = s
                    translationX = lerp(0f, -sideTuckPx, e)
                },
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                TerraSymbolIcon(symbol = LiquidKitSymbol.Check, tint = Color.White, modifier = Modifier.size(19.dp))
            }
        }

        val latestPagerState by rememberUpdatedState(pagerState)

        LiquidKitAnchoredMenuHost(
            state = groupPillMenuState,
            items = groupPillMenuItems,
            backdrop = backdrop,
            presentation = LiquidKitMenuPresentation.ButtonTransform,
            modifier = Modifier.layout { measurable, constraints ->
                val w = lerp(restingPillWidthPx, expandedPillWidthPx, expandFractionState.value)
                    .roundToInt()
                    .coerceAtLeast(1)
                val placeable = measurable.measure(
                    constraints.copy(minWidth = w, maxWidth = w, minHeight = pillHeightPx, maxHeight = pillHeightPx),
                )
                layout(w, pillHeightPx) {
                    placeable.placeRelative(0, 0)
                }
            },
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                LiquidKitBottomTabs(
                    tabsCount = count,
                    selectedTabIndex = { latestPagerState.currentPage.coerceInSafe(0, count - 1) },
                    onTabSelected = { target ->
                        val dest = target.coerceInSafe(0, count - 1)
                        pillScrollJob?.cancel()
                        lateinit var job: Job
                        job = scope.launch {
                            try {
                                latestPagerState.animateScrollToPage(
                                    page = dest,
                                    animationSpec = pagerSnapSpring,
                                )
                            } finally {
                                if (pillScrollJob === job && !isTabInteracting) {
                                    onScrubbingChange(false)
                                }
                            }
                        }
                        pillScrollJob = job
                    },
                    backdrop = backdrop,
                    height = 48.dp,
                    tint = ink,
                    selectedPosition = { pagePositionState.value },
                    scrollable = count >= 2,
                    onScrubPosition = { pos ->
                        val pageSpanPx = (latestPagerState.layoutInfo.pageSize + latestPagerState.layoutInfo.pageSpacing)
                            .toFloat()
                            .coerceAtLeast(pagerWidthPx)
                        val currentPos = latestPagerState.currentPage + latestPagerState.currentPageOffsetFraction
                        val deltaPages = pos - currentPos
                        val isReleaseSnap = (pos == pos.roundToInt().toFloat()) && abs(deltaPages) > 0.08f
                        if (!isReleaseSnap && abs(deltaPages) > 0.0005f) {
                            latestPagerState.dispatchRawDelta(deltaPages * pageSpanPx)
                        }
                    },
                    onInteractingChange = { active ->
                        isTabInteracting = active
                        if (active) {
                            pillScrollJob?.cancel()
                            onScrubbingChange(true)
                        } else if (pillScrollJob?.isActive != true) {
                            onScrubbingChange(false)
                        }
                    },
                    onSelectedTabClick = { _ ->
                        if (!latestPagerState.isScrollInProgress && groupPillMenuItems.isNotEmpty()) {
                            triggerHaptic(LiquidKitHaptic.SelectionTick)
                            groupPillMenuState.show()
                        }
                    },
                    onTabLongPress = { _ ->
                        if (groupPillMenuItems.isNotEmpty()) {
                            groupPillMenuState.show()
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .liquidKitMenuSource(groupPillMenuState, cornerRadius = 24.dp),
                ) {
                    groups.forEach { grp ->
                        Box(
                            modifier = Modifier
                                .defaultMinSize(minWidth = 64.dp)
                                .widthIn(max = 148.dp)
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            BasicText(
                                text = state.groupTitle(grp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = TextStyle(
                                    color = unselectedTabInk,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                ),
                            )
                        }
                    }
                }

                // Off-hit-area anchor trigger so MenuTrigger registers bounds for show()
                // without intercepting touches aimed at LiquidKitBottomTabs.
                if (groupPillMenuItems.isNotEmpty() || groupPillMenuState.expanded) {
                    Spacer(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .offset(y = 2.dp)
                            .fillMaxWidth()
                            .height(1.dp)
                            .liquidKitMenuTrigger(
                                state = groupPillMenuState,
                                activation = LiquidKitMenuActivation.LongPress,
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun OverviewSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onDismiss: () -> Unit,
    backdrop: Backdrop,
    ink: Color,
    secondary: Color,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    val isDarkSearch = liquidKitAppearance().dark
    val searchTint = if (isDarkSearch) Color(0xFF1F222B).copy(alpha = 0.72f) else Color(0xFFF6F7FB).copy(alpha = 0.76f)
    val searchBorder = Color.White.copy(alpha = if (isDarkSearch) 0.16f else 0.65f)

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        LiquidKitGlassSurface(
            backdrop = backdrop,
            shape = CircleShape,
            tint = searchTint,
            modifier = Modifier
                .weight(1f)
                .height(46.dp)
                .border(0.6.dp, searchBorder, CircleShape),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TerraSymbolIcon(symbol = LiquidKitSymbol.Search, tint = secondary, modifier = Modifier.size(17.dp))
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(color = ink, fontSize = 14.sp),
                    cursorBrush = SolidColor(Color(0xFF0A84FF)),
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    decorationBox = { inner ->
                        if (query.isEmpty()) {
                            BasicText(
                                text = "搜索标签页",
                                style = TextStyle(color = secondary, fontSize = 14.sp),
                            )
                        }
                        inner()
                    },
                )
                if (query.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(secondary.copy(alpha = 0.25f))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { onQueryChange("") },
                        contentAlignment = Alignment.Center,
                    ) {
                        TerraSymbolIcon(symbol = LiquidKitSymbol.Close, tint = ink, modifier = Modifier.size(12.dp))
                    }
                }
            }
        }

        LiquidKitIconButton(
            onClick = onDismiss,
            backdrop = backdrop,
            size = 46.dp,
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                TerraSymbolIcon(symbol = LiquidKitSymbol.Close, tint = ink, modifier = Modifier.size(17.dp))
            }
        }
    }
}
