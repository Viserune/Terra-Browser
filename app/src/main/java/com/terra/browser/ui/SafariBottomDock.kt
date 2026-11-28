package com.terra.browser.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.absoluteOffset
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.terra.browser.model.BrowserLayoutProfile
import com.terra.browser.model.BrowserTab
import com.terra.browser.model.BrowserUrlUtils
import com.terra.browser.model.LibraryTab
import com.terra.browser.model.SearchEngine
import com.terra.browser.model.SiteDarkMode
import com.terra.browser.model.TabBarMode
import com.terra.browser.state.TerraBrowserState
import com.terra.browser.webview.TerraWebViewPool
import io.liquidkit.LiquidKitAnchoredMenuHost
import io.liquidkit.LiquidKitBorderlessButton
import io.liquidkit.LiquidKitGlassSurface
import io.liquidkit.LiquidKitHaptic
import io.liquidkit.LiquidKitIconButton
import io.liquidkit.LiquidKitMenuActivation
import io.liquidkit.LiquidKitMenuItem
import io.liquidkit.LiquidKitMenuPresentation
import io.liquidkit.LiquidKitSpring
import io.liquidkit.LiquidKitSymbol
import io.liquidkit.LiquidKitText
import io.liquidkit.liquidKitAppearance
import io.liquidkit.liquidKitBackdrop
import io.liquidkit.liquidKitColors
import io.liquidkit.liquidKitMenuSource
import io.liquidkit.liquidKitMenuTrigger
import io.liquidkit.liquidKitTypography
import io.liquidkit.LiquidKitTheme
import io.liquidkit.rememberLiquidKitBackdrop
import io.liquidkit.rememberLiquidKitHaptics
import io.liquidkit.rememberLiquidKitMenuState
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

internal fun safariMenuGlassTint(@Suppress("UNUSED_PARAMETER") isDark: Boolean): Color =
    Color.Unspecified

internal fun safariDockGlassTint(isDark: Boolean, isPrivate: Boolean): Color = when {
    isPrivate -> Color(0xFF1D1F27).copy(alpha = 0.62f)
    isDark -> Color(0xFF1B1D24).copy(alpha = 0.56f)
    else -> Color(0xFFF6F7FB).copy(alpha = 0.42f)
}

internal fun showMenuExclusively(
    target: io.liquidkit.LiquidKitMenuState,
    allMenus: List<io.liquidkit.LiquidKitMenuState>,
) {
    for (menu in allMenus) {
        if (menu !== target && menu.expanded) {
            menu.dismiss()
        }
    }
}

internal fun dynamicRoundedCornerShape(
    cornerPxProvider: Density.() -> Float,
): RoundedCornerShape = RoundedCornerShape(
    object : CornerSize {
        override fun toPx(shapeSize: Size, density: Density): Float {
            val maxR = (minOf(shapeSize.width, shapeSize.height) * 0.5f).coerceAtLeast(0f)
            return with(density) { cornerPxProvider() }.coerceIn(0f, maxR)
        }
    },
)

@Composable
internal fun AnchoredGlassMenuOverlay(
    menuState: io.liquidkit.LiquidKitMenuState,
    items: List<LiquidKitMenuItem>,
    backdrop: Backdrop,
    anchorBoundsProvider: () -> Rect?,
    ink: Color,
    isDark: Boolean,
    preferTrailing: Boolean? = null,
    centerHorizontally: Boolean = false,
    showScrim: Boolean = false,
    anchorGapDp: Dp = 10.dp,
    liftedAnchorContent: (@Composable (progress: Float) -> Unit)? = null,
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit,
) {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val glassTint = safariMenuGlassTint(isDark)
    val scrimMaxAlpha = if (isDark) 0.56f else 0.34f
    LaunchedEffect(menuState.expanded, items.isEmpty()) {
        if (menuState.expanded && items.isEmpty()) {
            menuState.dismiss()
        }
    }
    BackHandler(enabled = menuState.expanded) { menuState.dismiss() }
    com.kyant.backdrop.components.LiquidMenuHost(
        state = menuState,
        backdrop = backdrop,
        modifier = modifier,
        submenuInk = ink,
        submenuTint = glassTint,
        rootOverlay = { behind ->
            if (menuState.expanded && items.isNotEmpty()) {
                val reveal = remember { Animatable(0f) }
                LaunchedEffect(menuState.expanded) {
                    reveal.snapTo(0f)
                    reveal.animateTo(1f, LiquidKitSpring.swiftUI(0.28f, 0.84f).spec)
                }
                val menuMetrics = com.kyant.backdrop.components.liquidMenuMetrics()
                val statusTopDp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
                val navBottomDp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                val cutoutPadding = WindowInsets.displayCutout.asPaddingValues()
                val cutoutLeftDp = cutoutPadding.calculateLeftPadding(layoutDirection)
                val cutoutRightDp = cutoutPadding.calculateRightPadding(layoutDirection)

                BoxWithConstraints(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .drawBehind {
                                if (showScrim) {
                                    val p = reveal.value.coerceIn(0f, 1f)
                                    if (p > 0.001f) {
                                        drawRect(Color.Black.copy(alpha = scrimMaxAlpha * p))
                                    }
                                }
                            }
                            .pointerInput(Unit) {
                                detectTapGestures { menuState.dismiss() }
                            },
                    )
                    val safeLeftDp = cutoutLeftDp + 12.dp
                    val safeRightDp = (maxWidth - cutoutRightDp - 12.dp).coerceAtLeast(safeLeftDp + 180.dp)
                    val safeTopDp = statusTopDp + 10.dp
                    val safeBottomDp = (maxHeight - navBottomDp - 10.dp).coerceAtLeast(safeTopDp + 140.dp)
                    val availW = (safeRightDp - safeLeftDp).coerceAtLeast(180.dp)
                    val availH = (safeBottomDp - safeTopDp).coerceAtLeast(140.dp)
                    val menuW = minOf(menuMetrics.width, availW)
                    val menuH = minOf(menuMetrics.height(items) + 12.dp, availH)

                    val fallbackAnchor = Rect(
                        left = with(density) { ((maxWidth - menuW) * 0.5f).toPx() },
                        top = with(density) { (safeBottomDp - 64.dp).toPx() },
                        right = with(density) { ((maxWidth + menuW) * 0.5f).toPx() },
                        bottom = with(density) { (safeBottomDp - 16.dp).toPx() },
                    )
                    val anchorBoundsInRoot = anchorBoundsProvider()
                    val anchor = anchorBoundsInRoot ?: fallbackAnchor
                    val anchorLeftDp = with(density) { anchor.left.toDp() }
                    val anchorRightDp = with(density) { anchor.right.toDp() }
                    val anchorTopDp = with(density) { anchor.top.toDp() }
                    val anchorBottomDp = with(density) { anchor.bottom.toDp() }
                    val anchorCenterXDp = with(density) { anchor.center.x.toDp() }
                    val anchorWidthDp = with(density) { anchor.width.toDp() }.coerceAtLeast(44.dp)
                    val anchorHeightDp = with(density) { anchor.height.toDp() }.coerceAtLeast(32.dp)

                    val fromBottom = anchor.center.y > constraints.maxHeight * 0.5f
                    val resolvedTrailing = preferTrailing ?: (anchor.center.x > constraints.maxWidth * 0.55f)
                    val rawLeft = when {
                        centerHorizontally -> anchorCenterXDp - menuW * 0.5f
                        resolvedTrailing -> anchorRightDp - menuW
                        else -> anchorLeftDp
                    }
                    val destLeft = rawLeft.coerceIn(safeLeftDp, (safeRightDp - menuW).coerceAtLeast(safeLeftDp))
                    val rawTop = if (fromBottom) anchorTopDp - menuH - anchorGapDp else anchorBottomDp + anchorGapDp
                    val destTop = rawTop.coerceIn(safeTopDp, (safeBottomDp - menuH).coerceAtLeast(safeTopDp))

                    if (liftedAnchorContent != null && anchorBoundsInRoot != null) {
                        Box(
                            modifier = Modifier
                                .align(AbsoluteAlignment.TopLeft)
                                .absoluteOffset(x = anchorLeftDp, y = anchorTopDp)
                                .size(width = anchorWidthDp, height = anchorHeightDp)
                                .graphicsLayer {
                                    val p = reveal.value.coerceIn(0f, 1f)
                                    val ls = lerp(1f, 1.022f, p)
                                    scaleX = ls
                                    scaleY = ls
                                },
                        ) {
                            liftedAnchorContent(1f)
                        }
                    }

                    val menuOriginX = when {
                        centerHorizontally -> 0.5f
                        resolvedTrailing -> 0.85f
                        else -> 0.25f
                    }
                    val menuOriginY = if (fromBottom) 1f else 0f
                    LiquidKitGlassSurface(
                        backdrop = behind,
                        shape = RoundedCornerShape(30.dp),
                        tint = glassTint,
                        modifier = Modifier
                            .align(AbsoluteAlignment.TopLeft)
                            .absoluteOffset(x = destLeft, y = destTop)
                            .size(width = menuW, height = menuH)
                            .graphicsLayer {
                                val p = reveal.value.coerceIn(0f, 1f)
                                alpha = p
                                val s = lerp(0.86f, 1f, p)
                                scaleX = s
                                scaleY = s
                                transformOrigin = TransformOrigin(menuOriginX, menuOriginY)
                            },
                    ) {
                        com.kyant.backdrop.components.LiquidMenuPanel(
                            state = menuState,
                            items = items,
                            ink = ink,
                            modifier = Modifier.requiredSize(menuW, menuH),
                        )
                    }
                }
            }
        },
        content = content,
    )
}

@Composable
internal fun SelectedAddressPillPreview(
    tab: BrowserTab,
    isPrivate: Boolean,
    collapse: Float,
    ink: Color,
    secondary: Color,
    isDark: Boolean,
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val isHttps = !tab.isStartPage && tab.url.startsWith("https://", ignoreCase = true)
    val fillColor = when {
        isPrivate -> Color(0xFF2C2F38)
        isDark -> Color(0xFF2E313B)
        else -> Color(0xFFF6F7FB)
    }
    val rimColor = if (isDark) Color.White.copy(alpha = 0.26f * progress) else Color.White.copy(alpha = 0.88f * progress)
    val fontSizeSp = lerp(15f, 12.5f, collapse).sp
    val showSideIcons = collapse < 0.5f

    Row(
        modifier = modifier
            .fillMaxSize()
            .clip(CircleShape)
            .background(fillColor)
            .border(1.dp, rimColor, CircleShape)
            .padding(horizontal = if (showSideIcons) 6.dp else 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showSideIcons) {
            Box(
                modifier = Modifier.size(width = 36.dp, height = 36.dp),
                contentAlignment = Alignment.Center,
            ) {
                SafariPageSettingsIcon(color = ink, modifier = Modifier.size(19.dp))
            }
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(horizontal = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                if (isPrivate && !tab.isStartPage) {
                    SafariPrivateMaskIcon(color = secondary, modifier = Modifier.size(13.dp))
                } else if (isHttps) {
                    SafariLockIcon(color = secondary, modifier = Modifier.size(12.dp))
                }
                BasicText(
                    text = if (tab.isStartPage) {
                        if (collapse > 0.5f) "起始页" else "搜索或输入网站"
                    } else {
                        tab.host.ifBlank { tab.url }
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(
                        color = if (tab.isStartPage && collapse < 0.5f) secondary else ink,
                        fontSize = fontSizeSp,
                        fontWeight = FontWeight.Medium,
                    ),
                )
            }
        }
        if (showSideIcons) {
            Box(
                modifier = Modifier.size(width = 36.dp, height = 36.dp),
                contentAlignment = Alignment.Center,
            ) {
                TerraSymbolIcon(
                    symbol = if (tab.isStartPage) LiquidKitSymbol.Search else LiquidKitSymbol.Reload,
                    tint = ink,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
fun SafariTopAddressBar(
    state: TerraBrowserState,
    pool: TerraWebViewPool,
    backdrop: Backdrop,
    alphaFactorProvider: () -> Float,
    tabSwipeFractionProvider: () -> Float,
    onBarDragStart: () -> Unit,
    onBarDragDelta: (dx: Float, dy: Float) -> Unit,
    onBarDragEnd: (velocityX: Float, velocityY: Float) -> Unit,
    onOpenNewTab: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tab = state.activeTab
    val isDark = liquidKitAppearance().dark || state.activeGroup.isPrivate
    val colors = liquidKitColors(isDark)
    val clipboard = LocalClipboardManager.current
    val density = LocalDensity.current
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

    val topMenus = remember(pageMenuState, addressLongPressMenuState) {
        listOf(pageMenuState, addressLongPressMenuState)
    }
    val openMenuExclusively: (io.liquidkit.LiquidKitMenuState) -> Unit = remember(topMenus) {
        { target -> showMenuExclusively(target, topMenus) }
    }

    val collapseState = animateFloatAsState(
        targetValue = if (state.isToolbarMinimized) 1f else 0f,
        animationSpec = LiquidKitSpring.swiftUI(0.36f, 0.86f).spec,
        label = "TopBarCollapse",
    )
    val latestAlphaFactor by rememberUpdatedState(alphaFactorProvider)
    val latestSwipeFraction by rememberUpdatedState(tabSwipeFractionProvider)
    val showAdjacent by remember { derivedStateOf { abs(latestSwipeFraction()) > 0.004f } }
    val swipeDelta by remember { derivedStateOf { if (latestSwipeFraction() < 0f) +1 else -1 } }

    val surfaceTint = safariDockGlassTint(isDark, state.activeGroup.isPrivate)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = latestAlphaFactor().coerceIn(0f, 1f) }
            .statusBarsPadding()
            .displayCutoutPadding()
            .padding(horizontal = 14.dp)
            .layout { measurable, constraints ->
                val c = collapseState.value.coerceIn(0f, 1f)
                val padVPx = lerp(8.dp.roundToPx(), 4.dp.roundToPx(), c)
                val childConstraints = constraints.copy(minHeight = 0)
                val placeable = measurable.measure(childConstraints)
                layout(placeable.width, placeable.height + padVPx * 2) {
                    placeable.placeRelative(0, padVPx)
                }
            },
        contentAlignment = Alignment.TopCenter,
    ) {
        val fullPillWidthPx = with(density) { maxWidth.coerceAtMost(560.dp).toPx() }
        val miniPillWidthPx = with(density) { (maxWidth * 0.56f).coerceAtMost(280.dp).toPx() }
        val fullPillHeightPx = with(density) { 48.dp.toPx() }
        val miniPillHeightPx = with(density) { 34.dp.toPx() }
        val gapPx = with(density) { 12.dp.toPx() }
        val currentStepPx: () -> Float = {
            val c = collapseState.value.coerceIn(0f, 1f)
            lerp(fullPillWidthPx, miniPillWidthPx, c) + gapPx
        }
        val swipeTxProvider: () -> Float = { latestSwipeFraction() * currentStepPx() }
        val topPillBoundsRef = remember { arrayOfNulls<Rect>(1) }

        Box(
            modifier = Modifier
                .layout { measurable, _ ->
                    val c = collapseState.value.coerceIn(0f, 1f)
                    val wPx = lerp(fullPillWidthPx, miniPillWidthPx, c).roundToInt().coerceAtLeast(1)
                    val hPx = lerp(fullPillHeightPx, miniPillHeightPx, c).roundToInt().coerceAtLeast(1)
                    val placeable = measurable.measure(Constraints.fixed(wPx, hPx))
                    layout(wPx, hPx) { placeable.placeRelative(0, 0) }
                }
                .onGloballyPositioned { if (it.isAttached) topPillBoundsRef[0] = it.boundsInRoot() },
        ) {
            AnchoredGlassMenuOverlay(
                menuState = addressLongPressMenuState,
                items = addressLongPressItems,
                backdrop = backdrop,
                anchorBoundsProvider = { topPillBoundsRef[0] },
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
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clipToBounds(),
                ) {
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
                        pillHeight = 48.dp,
                        innerPill = false,
                        surfaceTint = surfaceTint,
                        swipeTxPx = 0f,
                        collapseProvider = { collapseState.value },
                        swipeTxPxProvider = swipeTxProvider,
                        onBeforeOpenMenu = openMenuExclusively,
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
                                surfaceTint = surfaceTint,
                                ink = colors.ink,
                                secondary = colors.secondary,
                                collapse = collapseState.value,
                                collapseProvider = { collapseState.value },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer { translationX = swipeTxProvider() + delta * currentStepPx() },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SafariBottomDock(
    state: TerraBrowserState,
    pool: TerraWebViewPool,
    backdrop: Backdrop,
    alphaFactorProvider: () -> Float,
    editProgressProvider: () -> Float,
    tabSwipeFractionProvider: () -> Float,
    isDraggingToOverviewProvider: () -> Boolean = { false },
    layoutProfile: BrowserLayoutProfile = BrowserLayoutProfile.PHONE_PORTRAIT,
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
                val target = currentTab.url.ifBlank { "https://terra.browser/start" }
                clipboard.setText(AnnotatedString(target))
                state.showBanner("已拷贝链接")
            }
            picked.key == "close_current" -> {
                pool.evictTab(currentTab.id)
                state.closeTab(currentTab.id)
            }
            picked.key == "close_all" -> {
                val ids = state.tabsInGroup(state.activeGroup.id).map { it.id }
                ids.forEach { pool.evictTab(it) }
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
    val forwardHistoryMenuState = rememberLiquidKitMenuState { picked ->
        val steps = picked.key.removePrefix("hist:").toIntOrNull() ?: return@rememberLiquidKitMenuState
        if (steps != 0) pool.goBackOrForward(state.activeTab.id, steps)
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

    val groupWebCount = state.tabsInGroup(state.activeGroup.id).count { !it.isStartPage && it.url.isNotBlank() }
    val bookmarksQuickMenuItems = remember(tab.id, tab.url, tab.isStartPage, groupWebCount) {
        listOf(
            LiquidKitMenuItem("add_bookmark", "添加到书签", symbol = LiquidKitSymbol.Bookmark, enabled = !tab.isStartPage),
            LiquidKitMenuItem("add_reading", "添加到阅读列表", symbol = LiquidKitSymbol.Document, enabled = !tab.isStartPage),
            LiquidKitMenuItem("add_favorite", "添加到个人收藏", symbol = LiquidKitSymbol.StarFilled, enabled = !tab.isStartPage),
            LiquidKitMenuItem(
                key = "bookmark_all",
                title = "收藏全部 $groupWebCount 个标签页",
                symbol = LiquidKitSymbol.Folder,
                startsSection = true,
                enabled = groupWebCount > 0,
            ),
            LiquidKitMenuItem("open_history", "打开历史记录", symbol = LiquidKitSymbol.Globe, startsSection = true),
        )
    }
    val bookmarksQuickMenuState = rememberLiquidKitMenuState { picked ->
        val currentTab = state.activeTab
        when (picked.key) {
            "add_bookmark" -> {
                state.addBookmark(currentTab.title, currentTab.url)
                state.showBanner("已添加到书签")
            }
            "add_reading" -> {
                state.addReadingList(currentTab.title, currentTab.url)
                state.showBanner("已添加到阅读列表")
            }
            "add_favorite" -> {
                state.addFavorite(currentTab.title, currentTab.url)
                state.showBanner("已添加到个人收藏")
            }
            "bookmark_all" -> state.bookmarkAllTabsInGroup(state.activeGroup.id)
            "open_history" -> {
                state.libraryInitialTab = LibraryTab.HISTORY
                state.showLibrarySheet = true
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

    val allDockMenus = remember(
        pageMenuState,
        addressLongPressMenuState,
        quickTabsMenuState,
        backHistoryMenuState,
        forwardHistoryMenuState,
        shareQuickMenuState,
        bookmarksQuickMenuState,
        newTabQuickMenuState,
    ) {
        listOf(
            pageMenuState,
            addressLongPressMenuState,
            quickTabsMenuState,
            backHistoryMenuState,
            forwardHistoryMenuState,
            shareQuickMenuState,
            bookmarksQuickMenuState,
            newTabQuickMenuState,
        )
    }
    val openMenuExclusively: (io.liquidkit.LiquidKitMenuState) -> Unit = remember(allDockMenus) {
        { target -> showMenuExclusively(target, allDockMenus) }
    }

    val dockSpring = remember { LiquidKitSpring.swiftUI(0.36f, 0.86f) }
    val collapseState = animateFloatAsState(
        targetValue = if (state.isToolbarMinimized) 1f else 0f,
        animationSpec = dockSpring.spec,
        label = "DockCollapse",
    )
    val dockAlphaState = animateFloatAsState(
        targetValue = if (state.isOverviewOpen) 0f else 1f,
        animationSpec = dockSpring.spec,
        label = "DockAlpha",
    )
    val latestAlphaFactor by rememberUpdatedState(alphaFactorProvider)
    val latestEditProgress by rememberUpdatedState(editProgressProvider)
    val latestSwipeFraction by rememberUpdatedState(tabSwipeFractionProvider)
    val latestDragging by rememberUpdatedState(isDraggingToOverviewProvider)

    val showAdjacent by remember { derivedStateOf { abs(latestSwipeFraction()) > 0.004f } }
    val swipeDelta by remember { derivedStateOf { if (latestSwipeFraction() < 0f) +1 else -1 } }

    LaunchedEffect(state.isOverviewOpen) {
        if (state.isOverviewOpen) {
            allDockMenus.forEach { it.dismiss() }
        }
    }

    val solidInnerPillColor = when {
        state.activeGroup.isPrivate -> Color(0xFF23252C).copy(alpha = 0.72f)
        isDark -> Color(0xFF282B34).copy(alpha = 0.52f)
        else -> Color.Black.copy(alpha = 0.06f)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .layout { measurable, constraints ->
                val effectiveAlpha = (dockAlphaState.value * latestAlphaFactor()).coerceIn(0f, 1f)
                val visible = effectiveAlpha > 0.008f ||
                    latestEditProgress() > 0.005f ||
                    state.isAddressEditing ||
                    latestDragging()
                if (!visible) {
                    layout(0, 0) {}
                } else {
                    val placeable = measurable.measure(constraints)
                    layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
                }
            }
            .graphicsLayer { alpha = (dockAlphaState.value * latestAlphaFactor()).coerceIn(0f, 1f) },
        contentAlignment = Alignment.BottomCenter,
    ) {
        val veilMaxHeight = when {
            layoutProfile == BrowserLayoutProfile.PHONE_LANDSCAPE -> 78.dp
            state.tabBarMode == TabBarMode.BOTTOM -> 156.dp
            else -> 108.dp
        }
        val veilMinHeight = when {
            layoutProfile == BrowserLayoutProfile.PHONE_LANDSCAPE -> 56.dp
            state.tabBarMode == TabBarMode.BOTTOM -> 88.dp
            else -> 74.dp
        }
        val veilAlphaProvider: () -> Float = {
            val c = collapseState.value.coerceIn(0f, 1f)
            when {
                layoutProfile == BrowserLayoutProfile.PHONE_LANDSCAPE -> lerp(0.86f, 0.42f, c)
                state.tabBarMode == TabBarMode.TOP -> (1f - c * 1.4f).coerceIn(0f, 1f)
                else -> lerp(1f, 0.48f, c)
            }
        }
        val veilScaleYProvider: () -> Float = {
            val c = collapseState.value.coerceIn(0f, 1f)
            lerp(1f, veilMinHeight.value / veilMaxHeight.value, c)
        }
        SafariBottomProgressiveBlur(
            backdrop = backdrop,
            height = veilMaxHeight,
            alpha = 1f,
            isDark = isDark,
            alphaProvider = veilAlphaProvider,
            heightScaleProvider = veilScaleYProvider,
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .displayCutoutPadding()
                .imePadding()
                .padding(horizontal = 14.dp)
                .layout { measurable, constraints ->
                    val c = collapseState.value.coerceIn(0f, 1f)
                    val padVPx = if (layoutProfile == BrowserLayoutProfile.PHONE_LANDSCAPE) {
                        lerp(6.dp.roundToPx(), 4.dp.roundToPx(), c)
                    } else {
                        lerp(14.dp.roundToPx(), 8.dp.roundToPx(), c)
                    }
                    val placeable = measurable.measure(constraints.copy(minHeight = 0))
                    layout(placeable.width, placeable.height + padVPx * 2) {
                        placeable.placeRelative(0, padVPx)
                    }
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AnimatedVisibility(
                visible = state.findInPageVisible,
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 },
            ) {
                FindInPageGlassBar(
                    state = state,
                    pool = pool,
                    backdrop = backdrop,
                    modifier = Modifier.widthIn(max = 560.dp),
                )
            }

            if (state.isToolbarHidden) {
                LiquidKitGlassSurface(
                    backdrop = backdrop,
                    shape = RoundedCornerShape(20.dp),
                    interactive = true,
                    onClick = {
                        state.isToolbarHidden = false
                        state.isToolbarMinimized = false
                    },
                ) {
                    Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        BasicText(
                            text = tab.host.ifBlank { "显示工具栏" },
                            style = TextStyle(color = colors.ink, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                        )
                    }
                }
            } else if (layoutProfile == BrowserLayoutProfile.PHONE_LANDSCAPE) {
                PhoneLandscapeBar(
                    state = state,
                    tab = tab,
                    pool = pool,
                    backdrop = backdrop,
                    collapseState = collapseState,
                    swipeFractionProvider = { latestSwipeFraction() },
                    showAdjacent = showAdjacent,
                    swipeDelta = swipeDelta,
                    pageMenuState = pageMenuState,
                    pageMenuItems = pageMenuItems,
                    addressLongPressMenuState = addressLongPressMenuState,
                    addressLongPressItems = addressLongPressItems,
                    backHistoryMenuState = backHistoryMenuState,
                    backHistoryItems = backHistoryItems,
                    forwardHistoryMenuState = forwardHistoryMenuState,
                    forwardHistoryItems = forwardHistoryItems,
                    shareQuickMenuState = shareQuickMenuState,
                    shareQuickMenuItems = shareQuickMenuItems,
                    bookmarksQuickMenuState = bookmarksQuickMenuState,
                    bookmarksQuickMenuItems = bookmarksQuickMenuItems,
                    newTabQuickMenuState = newTabQuickMenuState,
                    newTabQuickMenuItems = newTabQuickMenuItems,
                    quickTabsMenuState = quickTabsMenuState,
                    quickTabsMenuItems = quickTabsMenuItems,
                    onBeforeOpenMenu = openMenuExclusively,
                    ink = colors.ink,
                    secondary = colors.secondary,
                    accentBlue = accentBlue,
                    isDark = isDark,
                    onBarDragStart = onBarDragStart,
                    onBarDragDelta = onBarDragDelta,
                    onBarDragEnd = onBarDragEnd,
                    onOpenOverview = onOpenOverview,
                    onOpenNewTab = onOpenNewTab,
                )
            } else {
                when (state.tabBarMode) {
                    TabBarMode.COMPACT -> {
                        BoxWithConstraints(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center,
                        ) {
                            val compactFadeSpec = remember { LiquidKitSpring.swiftUI(0.22f, 0.9f).spec }
                            val compactBackAlphaState = animateFloatAsState(
                                targetValue = if (quickTabsMenuState.expanded || pageMenuState.expanded) 0f else 1f,
                                animationSpec = compactFadeSpec,
                                label = "CompactBackAlpha",
                            )
                            val compactCenterAlphaState = animateFloatAsState(
                                targetValue = if (quickTabsMenuState.expanded) 0f else 1f,
                                animationSpec = compactFadeSpec,
                                label = "CompactCenterAlpha",
                            )
                            val compactRightAlphaState = animateFloatAsState(
                                targetValue = if (pageMenuState.expanded) 0f else 1f,
                                animationSpec = compactFadeSpec,
                                label = "CompactRightAlpha",
                            )
                            val sideButtonsInteractive by remember {
                                derivedStateOf {
                                    val sideAlpha = (1f - collapseState.value * 1.6f).coerceIn(0f, 1f)
                                    sideAlpha > 0.25f && !pageMenuState.expanded && !quickTabsMenuState.expanded
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterStart)
                                    .layout { measurable, constraints ->
                                        val c = collapseState.value.coerceIn(0f, 1f)
                                        val sideAlpha = (1f - c * 1.6f).coerceIn(0f, 1f)
                                        val effectiveAlpha = sideAlpha * compactBackAlphaState.value
                                        if (effectiveAlpha <= 0.005f) {
                                            layout(0, 0) {}
                                        } else {
                                            val placeable = measurable.measure(constraints)
                                            layout(placeable.width, placeable.height) {
                                                placeable.placeRelative(0, 0)
                                            }
                                        }
                                    }
                                    .graphicsLayer {
                                        val c = collapseState.value.coerceIn(0f, 1f)
                                        val sideAlpha = (1f - c * 1.6f).coerceIn(0f, 1f)
                                        val sideScale = lerp(1f, 0.45f, c)
                                        alpha = sideAlpha * compactBackAlphaState.value
                                        scaleX = sideScale
                                        scaleY = sideScale
                                    },
                            ) {
                                LiquidKitAnchoredMenuHost(
                                    state = backHistoryMenuState,
                                    items = backHistoryItems,
                                    backdrop = backdrop,
                                    presentation = LiquidKitMenuPresentation.ButtonTransform,
                                ) {
                                    var backHeld by remember { mutableStateOf(false) }
                                    val backScaleState = animateFloatAsState(
                                        targetValue = if (backHeld) 1.12f else 1f,
                                        animationSpec = LiquidKitSpring.swiftUI(0.22f, 0.82f).spec,
                                        label = "CompactBackScale",
                                    )
                                    val backTriggerActive = ((sideButtonsInteractive && tab.canGoBack) ||
                                        backHistoryMenuState.expanded) &&
                                        backHistoryItems.isNotEmpty()
                                    val backTapOnly = sideButtonsInteractive && tab.canGoBack && backHistoryItems.isEmpty()
                                    LiquidKitIconButton(
                                        onClick = {
                                            if (sideButtonsInteractive && tab.canGoBack) {
                                                pool.goBack(tab.id)
                                            }
                                        },
                                        backdrop = backdrop,
                                        size = 48.dp,
                                        isInteractive = sideButtonsInteractive && tab.canGoBack,
                                        modifier = Modifier
                                            .graphicsLayer {
                                                scaleX = backScaleState.value
                                                scaleY = backScaleState.value
                                            }
                                            .liquidKitMenuSource(backHistoryMenuState, cornerRadius = 24.dp)
                                            .then(
                                                if (backTriggerActive) {
                                                    Modifier.liquidKitMenuTrigger(
                                                        state = backHistoryMenuState,
                                                        activation = LiquidKitMenuActivation.LongPress,
                                                        beforeOpen = {
                                                            if (tab.canGoBack && backHistoryItems.isNotEmpty()) {
                                                                openMenuExclusively(backHistoryMenuState)
                                                            }
                                                        },
                                                        onShortTap = {
                                                            if (tab.canGoBack) pool.goBack(tab.id)
                                                        },
                                                        onHoldChange = { backHeld = it },
                                                    )
                                                } else {
                                                    Modifier
                                                },
                                            ),
                                    ) {
                                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            TerraSymbolIcon(
                                                symbol = LiquidKitSymbol.Back,
                                                tint = if (tab.canGoBack) colors.ink else colors.secondary.copy(alpha = 0.42f),
                                                modifier = Modifier.size(19.dp),
                                            )
                                        }
                                    }
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .layout { measurable, constraints ->
                                        val c = collapseState.value.coerceIn(0f, 1f)
                                        val sideAlpha = (1f - c * 1.6f).coerceIn(0f, 1f)
                                        val effectiveAlpha = sideAlpha * compactRightAlphaState.value
                                        if (effectiveAlpha <= 0.005f) {
                                            layout(0, 0) {}
                                        } else {
                                            val placeable = measurable.measure(constraints)
                                            layout(placeable.width, placeable.height) {
                                                placeable.placeRelative(0, 0)
                                            }
                                        }
                                    }
                                    .graphicsLayer {
                                        val c = collapseState.value.coerceIn(0f, 1f)
                                        val sideAlpha = (1f - c * 1.6f).coerceIn(0f, 1f)
                                        val sideScale = lerp(1f, 0.45f, c)
                                        alpha = sideAlpha * compactRightAlphaState.value
                                        scaleX = sideScale
                                        scaleY = sideScale
                                    },
                            ) {
                                LiquidKitAnchoredMenuHost(
                                    state = quickTabsMenuState,
                                    items = quickTabsMenuItems,
                                    backdrop = backdrop,
                                    presentation = LiquidKitMenuPresentation.ButtonTransform,
                                ) {
                                    var tabsHeld by remember { mutableStateOf(false) }
                                    val tabsScaleState = animateFloatAsState(
                                        targetValue = if (tabsHeld) 1.12f else 1f,
                                        animationSpec = LiquidKitSpring.swiftUI(0.22f, 0.82f).spec,
                                        label = "CompactTabsScale",
                                    )
                                    LiquidKitIconButton(
                                        onClick = {
                                            if (sideButtonsInteractive) onOpenOverview()
                                        },
                                        backdrop = backdrop,
                                        size = 48.dp,
                                        isInteractive = sideButtonsInteractive,
                                        modifier = Modifier
                                            .graphicsLayer {
                                                scaleX = tabsScaleState.value
                                                scaleY = tabsScaleState.value
                                            }
                                            .liquidKitMenuSource(quickTabsMenuState, cornerRadius = 24.dp)
                                            .then(
                                                if ((sideButtonsInteractive || quickTabsMenuState.expanded) &&
                                                    quickTabsMenuItems.isNotEmpty()
                                                ) {
                                                    Modifier.liquidKitMenuTrigger(
                                                        state = quickTabsMenuState,
                                                        activation = LiquidKitMenuActivation.LongPress,
                                                        beforeOpen = { openMenuExclusively(quickTabsMenuState) },
                                                        onShortTap = { onOpenOverview() },
                                                        onHoldChange = { tabsHeld = it },
                                                    )
                                                } else {
                                                    Modifier
                                                },
                                            ),
                                    ) {
                                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            SafariTabsIcon(
                                                color = colors.ink,
                                                modifier = Modifier.size(20.dp),
                                            )
                                        }
                                    }
                                }
                            }

                            val fullCenterWidthPx = with(density) { (maxWidth - 116.dp).coerceAtLeast(160.dp).toPx() }
                            val miniWidthPx = with(density) { (maxWidth * 0.56f).coerceAtLeast(156.dp).toPx() }
                            val fullCenterHeightPx = with(density) { 48.dp.toPx() }
                            val miniHeightPx = with(density) { 36.dp.toPx() }
                            val gapPx = with(density) { 12.dp.toPx() }
                            val currentStepPx: () -> Float = {
                                val c = collapseState.value.coerceIn(0f, 1f)
                                lerp(fullCenterWidthPx, miniWidthPx, c) + gapPx
                            }
                            val swipeTxProvider: () -> Float = { latestSwipeFraction() * currentStepPx() }
                            val compactTint = safariDockGlassTint(isDark, state.activeGroup.isPrivate)

                            val compactPillBoundsRef = remember { arrayOfNulls<Rect>(1) }
                            Box(
                                modifier = Modifier
                                    .layout { measurable, _ ->
                                        val c = collapseState.value.coerceIn(0f, 1f)
                                        val wPx = lerp(fullCenterWidthPx, miniWidthPx, c).roundToInt().coerceAtLeast(1)
                                        val hPx = lerp(fullCenterHeightPx, miniHeightPx, c).roundToInt().coerceAtLeast(1)
                                        val placeable = measurable.measure(Constraints.fixed(wPx, hPx))
                                        layout(wPx, hPx) { placeable.placeRelative(0, 0) }
                                    }
                                    .onGloballyPositioned { if (it.isAttached) compactPillBoundsRef[0] = it.boundsInRoot() }
                                    .graphicsLayer { alpha = compactCenterAlphaState.value },
                            ) {
                                AnchoredGlassMenuOverlay(
                                    menuState = addressLongPressMenuState,
                                    items = addressLongPressItems,
                                    backdrop = backdrop,
                                    anchorBoundsProvider = { compactPillBoundsRef[0] },
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
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clipToBounds(),
                                    ) {
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
                                            onBarDragDelta = onBarDragDelta,
                                            onBarDragEnd = onBarDragEnd,
                                            enableOverviewPullUpProvider = { collapseState.value < 0.5f },
                                            ink = colors.ink,
                                            secondary = colors.secondary,
                                            accentBlue = accentBlue,
                                            pillHeight = 48.dp,
                                            innerPill = false,
                                            surfaceTint = compactTint,
                                            swipeTxPx = 0f,
                                            collapseProvider = { collapseState.value },
                                            swipeTxPxProvider = swipeTxProvider,
                                            onBeforeOpenMenu = openMenuExclusively,
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
                                                    surfaceTint = compactTint,
                                                    ink = colors.ink,
                                                    secondary = colors.secondary,
                                                    collapse = 0f,
                                                    collapseProvider = { collapseState.value },
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .graphicsLayer { translationX = swipeTxProvider() + delta * currentStepPx() },
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    TabBarMode.BOTTOM -> {
                        BoxWithConstraints(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.BottomCenter,
                        ) {
                            val maxWidthPx = with(density) { maxWidth.toPx() }
                            val miniOuterWidthPx = maxWidthPx * 0.56f
                            val bottomDockChromeAlphaState = animateFloatAsState(
                                targetValue = if (pageMenuState.expanded) 0f else 1f,
                                animationSpec = LiquidKitSpring.swiftUI(0.22f, 0.9f).spec,
                                label = "BottomDockPageMenuFade",
                            )
                            val dockCornerShape = remember(collapseState) {
                                dynamicRoundedCornerShape {
                                    lerp(30.dp.toPx(), 18.dp.toPx(), collapseState.value.coerceIn(0f, 1f))
                                }
                            }
                            val innerTintAlphaProvider: () -> Float = {
                                val c = collapseState.value.coerceIn(0f, 1f)
                                val e = latestEditProgress().coerceIn(0f, 1f)
                                ((1f - c * 1.25f) * (1f - e * 1.4f)).coerceIn(0f, 1f)
                            }
                            val iconsInteractive by remember {
                                derivedStateOf {
                                    val hide = maxOf(collapseState.value, latestEditProgress().coerceIn(0f, 1f))
                                    val iconsAlpha = (1f - hide * 1.7f).coerceIn(0f, 1f) * bottomDockChromeAlphaState.value
                                    iconsAlpha > 0.25f && !pageMenuState.expanded
                                }
                            }

                            Box(
                                modifier = Modifier.layout { measurable, constraints ->
                                    val c = collapseState.value.coerceIn(0f, 1f)
                                    val targetW = lerp(maxWidthPx, miniOuterWidthPx, c).roundToInt().coerceAtLeast(1)
                                    val placeable = measurable.measure(constraints.copy(minWidth = targetW, maxWidth = targetW))
                                    layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
                                },
                            ) {
                                LiquidKitGlassSurface(
                                    backdrop = backdrop,
                                    shape = dockCornerShape,
                                    tint = safariDockGlassTint(isDark, state.activeGroup.isPrivate),
                                    modifier = Modifier
                                        .matchParentSize()
                                        .graphicsLayer { alpha = bottomDockChromeAlphaState.value },
                                ) {}

                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    val bottomPillBoundsRef = remember { arrayOfNulls<Rect>(1) }
                                    val gap12Px = with(density) { 12.dp.toPx() }
                                    val padHFullPx = with(density) { 10.dp.toPx() }
                                    val padHMiniPx = with(density) { 5.dp.toPx() }
                                    val padVFullPx = with(density) { 8.dp.toPx() }
                                    val padVMiniPx = with(density) { 3.dp.toPx() }
                                    val pillHFullPx = with(density) { 46.dp.toPx() }
                                    val pillHMiniPx = with(density) { 36.dp.toPx() }
                                    val spacingFullPx = with(density) { 7.dp.toPx() }
                                    val iconsHFullPx = with(density) { 42.dp.toPx() }

                                    AnchoredGlassMenuOverlay(
                                        menuState = addressLongPressMenuState,
                                        items = addressLongPressItems,
                                        backdrop = backdrop,
                                        anchorBoundsProvider = { bottomPillBoundsRef[0] },
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
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .layout { measurable, constraints ->
                                                val c = collapseState.value.coerceIn(0f, 1f)
                                                val ph = lerp(padHFullPx, padHMiniPx, c).roundToInt()
                                                val pv = lerp(padVFullPx, padVMiniPx, c).roundToInt()
                                                val hPx = lerp(pillHFullPx, pillHMiniPx, c).roundToInt().coerceAtLeast(1)
                                                val wPx = (constraints.maxWidth - ph * 2).coerceAtLeast(1)
                                                val placeable = measurable.measure(Constraints.fixed(wPx, hPx))
                                                layout(constraints.maxWidth, pv + hPx) {
                                                    placeable.placeRelative(ph, pv)
                                                }
                                            }
                                            .onGloballyPositioned { if (it.isAttached) bottomPillBoundsRef[0] = it.boundsInRoot() },
                                    ) {
                                        BoxWithConstraints(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clipToBounds(),
                                        ) {
                                            val pillSlotWidthPx = with(density) { maxWidth.toPx() }
                                            val pillStepPx = pillSlotWidthPx + gap12Px
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
                                                onBarDragDelta = onBarDragDelta,
                                                onBarDragEnd = onBarDragEnd,
                                                enableOverviewPullUpProvider = { collapseState.value < 0.5f },
                                                ink = colors.ink,
                                                secondary = colors.secondary,
                                                accentBlue = accentBlue,
                                                pillHeight = 46.dp,
                                                innerPill = true,
                                                surfaceTint = solidInnerPillColor,
                                                swipeTxPx = 0f,
                                                collapseProvider = { collapseState.value },
                                                swipeTxPxProvider = swipeTxProvider,
                                                innerTintAlphaProvider = innerTintAlphaProvider,
                                                onBeforeOpenMenu = openMenuExclusively,
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
                                                        innerPill = true,
                                                        surfaceTint = solidInnerPillColor,
                                                        ink = colors.ink,
                                                        secondary = colors.secondary,
                                                        collapse = collapseState.value,
                                                        collapseProvider = { collapseState.value },
                                                        tintAlphaProvider = innerTintAlphaProvider,
                                                        modifier = Modifier
                                                            .fillMaxSize()
                                                            .graphicsLayer { translationX = swipeTxProvider() + delta * pillStepPx },
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .layout { measurable, constraints ->
                                                val c = collapseState.value.coerceIn(0f, 1f)
                                                val hide = maxOf(c, latestEditProgress().coerceIn(0f, 1f))
                                                val ph = lerp(padHFullPx, padHMiniPx, c).roundToInt()
                                                val pv = lerp(padVFullPx, padVMiniPx, c).roundToInt()
                                                val iconsH = lerp(iconsHFullPx, 0f, hide).roundToInt()
                                                val gap = lerp(spacingFullPx, 0f, hide).roundToInt()
                                                if (iconsH <= 1) {
                                                    layout(constraints.maxWidth, pv) {}
                                                } else {
                                                    val wPx = (constraints.maxWidth - ph * 2).coerceAtLeast(1)
                                                    val placeable = measurable.measure(Constraints.fixed(wPx, iconsH))
                                                    layout(constraints.maxWidth, gap + iconsH + pv) {
                                                        placeable.placeRelative(ph, gap)
                                                    }
                                                }
                                            }
                                            .graphicsLayer {
                                                val c = collapseState.value.coerceIn(0f, 1f)
                                                val hide = maxOf(c, latestEditProgress().coerceIn(0f, 1f))
                                                alpha = (1f - hide * 1.7f).coerceIn(0f, 1f) * bottomDockChromeAlphaState.value
                                                scaleY = lerp(1f, 0.45f, c)
                                            },
                                    ) {
                                        FiveActionIconBar(
                                            tab = tab,
                                            pool = pool,
                                            backdrop = backdrop,
                                            backHistoryMenuState = backHistoryMenuState,
                                            backHistoryItems = backHistoryItems,
                                            forwardHistoryMenuState = forwardHistoryMenuState,
                                            forwardHistoryItems = forwardHistoryItems,
                                            shareQuickMenuState = shareQuickMenuState,
                                            shareQuickMenuItems = shareQuickMenuItems,
                                            bookmarksQuickMenuState = bookmarksQuickMenuState,
                                            bookmarksQuickMenuItems = bookmarksQuickMenuItems,
                                            quickTabsMenuState = quickTabsMenuState,
                                            quickTabsMenuItems = quickTabsMenuItems,
                                            ink = colors.ink,
                                            secondary = colors.secondary,
                                            enabled = iconsInteractive,
                                            onBeforeOpenMenu = openMenuExclusively,
                                            onShare = { state.showShareSheet = true },
                                            onBookmarks = {
                                                state.libraryInitialTab = LibraryTab.BOOKMARKS
                                                state.showLibrarySheet = true
                                            },
                                            onOpenOverview = onOpenOverview,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    TabBarMode.TOP -> {
                        val topIconsInteractive by remember {
                            derivedStateOf { (1f - collapseState.value * 1.6f) > 0.25f }
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .layout { measurable, constraints ->
                                    val c = collapseState.value.coerceIn(0f, 1f)
                                    val topIconsAlpha = (1f - c * 1.6f).coerceIn(0f, 1f)
                                    if (topIconsAlpha <= 0.005f) {
                                        layout(constraints.maxWidth, 0) {}
                                    } else {
                                        val placeable = measurable.measure(constraints)
                                        layout(placeable.width, placeable.height) {
                                            placeable.placeRelative(0, 0)
                                        }
                                    }
                                }
                                .graphicsLayer {
                                    val c = collapseState.value.coerceIn(0f, 1f)
                                    val topIconsAlpha = (1f - c * 1.6f).coerceIn(0f, 1f)
                                    alpha = topIconsAlpha
                                    translationY = c * 56.dp.toPx()
                                    val s = lerp(1f, 0.92f, c)
                                    scaleX = s
                                    scaleY = s
                                },
                        ) {
                            val topModeBarTint = when {
                                state.activeGroup.isPrivate -> Color(0xFF1D1F27).copy(alpha = 0.72f)
                                isDark -> Color(0xFF1B1D24).copy(alpha = 0.68f)
                                else -> Color(0xFFF6F7FB).copy(alpha = 0.64f)
                            }
                            LiquidKitGlassSurface(
                                backdrop = backdrop,
                                shape = RoundedCornerShape(26.dp),
                                tint = topModeBarTint,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 5.dp),
                                ) {
                                    FiveActionIconBar(
                                        tab = tab,
                                        pool = pool,
                                        backdrop = backdrop,
                                        backHistoryMenuState = backHistoryMenuState,
                                        backHistoryItems = backHistoryItems,
                                        forwardHistoryMenuState = forwardHistoryMenuState,
                                        forwardHistoryItems = forwardHistoryItems,
                                        shareQuickMenuState = shareQuickMenuState,
                                        shareQuickMenuItems = shareQuickMenuItems,
                                        bookmarksQuickMenuState = bookmarksQuickMenuState,
                                        bookmarksQuickMenuItems = bookmarksQuickMenuItems,
                                        quickTabsMenuState = quickTabsMenuState,
                                        quickTabsMenuItems = quickTabsMenuItems,
                                        ink = colors.ink,
                                        secondary = colors.secondary,
                                        enabled = topIconsInteractive,
                                        onBeforeOpenMenu = openMenuExclusively,
                                        onShare = { state.showShareSheet = true },
                                        onBookmarks = {
                                            state.libraryInitialTab = LibraryTab.BOOKMARKS
                                            state.showLibrarySheet = true
                                        },
                                        onOpenOverview = onOpenOverview,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PhoneLandscapeBar(
    state: TerraBrowserState,
    tab: BrowserTab,
    pool: TerraWebViewPool,
    backdrop: Backdrop,
    collapseState: State<Float>,
    swipeFractionProvider: () -> Float,
    showAdjacent: Boolean,
    swipeDelta: Int,
    pageMenuState: io.liquidkit.LiquidKitMenuState,
    pageMenuItems: List<LiquidKitMenuItem>,
    addressLongPressMenuState: io.liquidkit.LiquidKitMenuState,
    addressLongPressItems: List<LiquidKitMenuItem>,
    backHistoryMenuState: io.liquidkit.LiquidKitMenuState,
    backHistoryItems: List<LiquidKitMenuItem>,
    forwardHistoryMenuState: io.liquidkit.LiquidKitMenuState,
    forwardHistoryItems: List<LiquidKitMenuItem>,
    shareQuickMenuState: io.liquidkit.LiquidKitMenuState,
    shareQuickMenuItems: List<LiquidKitMenuItem>,
    bookmarksQuickMenuState: io.liquidkit.LiquidKitMenuState,
    bookmarksQuickMenuItems: List<LiquidKitMenuItem>,
    newTabQuickMenuState: io.liquidkit.LiquidKitMenuState,
    newTabQuickMenuItems: List<LiquidKitMenuItem>,
    quickTabsMenuState: io.liquidkit.LiquidKitMenuState,
    quickTabsMenuItems: List<LiquidKitMenuItem>,
    onBeforeOpenMenu: (io.liquidkit.LiquidKitMenuState) -> Unit,
    ink: Color,
    secondary: Color,
    accentBlue: Color,
    isDark: Boolean,
    onBarDragStart: () -> Unit,
    onBarDragDelta: (dx: Float, dy: Float) -> Unit,
    onBarDragEnd: (velocityX: Float, velocityY: Float) -> Unit,
    onOpenOverview: () -> Unit,
    onOpenNewTab: () -> Unit,
) {
    val density = LocalDensity.current
    val hideAllForPageMenuState = animateFloatAsState(
        targetValue = if (pageMenuState.expanded) 0f else 1f,
        animationSpec = LiquidKitSpring.swiftUI(0.22f, 0.9f).spec,
        label = "PhoneLandSideFade",
    )
    val clusterInteractive by remember {
        derivedStateOf {
            val sideAlpha = (1f - collapseState.value * 1.75f).coerceIn(0f, 1f)
            sideAlpha * hideAllForPageMenuState.value > 0.25f && !pageMenuState.expanded
        }
    }
    val clusterTint = safariDockGlassTint(isDark, state.activeGroup.isPrivate)
    val landPillBoundsRef = remember { arrayOfNulls<Rect>(1) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        contentAlignment = Alignment.Center,
    ) {
        val leftClusterWidthDp = 84.dp
        val rightClusterWidthDp = 160.dp
        val spacingDp = 8.dp
        val safeMaxDp = (maxWidth - 32.dp).coerceAtLeast(1.dp)
        val expandedCenterMaxDp = (maxWidth - leftClusterWidthDp - rightClusterWidthDp - spacingDp * 2)
            .coerceAtMost(460.dp)
            .coerceAtLeast(minOf(180.dp, safeMaxDp))
        val expandedCenterWidthPx = with(density) { expandedCenterMaxDp.toPx() }
        val miniCenterWidthPx = with(density) { 220.dp.coerceAtMost(safeMaxDp).toPx() }
        val pillFullHeightPx = with(density) { 46.dp.toPx() }
        val pillMiniHeightPx = with(density) { 34.dp.toPx() }
        val gap12Px = with(density) { 12.dp.toPx() }

        LiquidKitGlassSurface(
            backdrop = backdrop,
            shape = CircleShape,
            tint = clusterTint,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .height(44.dp)
                .layout { measurable, constraints ->
                    val sideAlpha = (1f - collapseState.value * 1.75f).coerceIn(0f, 1f)
                    val clusterAlpha = sideAlpha * hideAllForPageMenuState.value
                    if (clusterAlpha <= 0.005f) {
                        layout(0, 0) {}
                    } else {
                        val placeable = measurable.measure(constraints)
                        layout(placeable.width, placeable.height) {
                            placeable.placeRelative(0, 0)
                        }
                    }
                }
                .graphicsLayer {
                    val sideAlpha = (1f - collapseState.value * 1.75f).coerceIn(0f, 1f)
                    alpha = sideAlpha * hideAllForPageMenuState.value
                    val s = lerp(0.7f, 1f, sideAlpha)
                    scaleX = s
                    scaleY = s
                },
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BorderlessMenuButton(
                    menuState = backHistoryMenuState,
                    menuItems = backHistoryItems,
                    backdrop = backdrop,
                    buttonWidth = 38.dp,
                    buttonHeight = 42.dp,
                    enabled = clusterInteractive && tab.canGoBack,
                    onBeforeOpenMenu = { onBeforeOpenMenu(backHistoryMenuState) },
                    onShortTap = {
                        if (tab.canGoBack) pool.goBack(tab.id)
                    },
                ) {
                    TerraSymbolIcon(
                        symbol = LiquidKitSymbol.Back,
                        tint = if (tab.canGoBack) ink else secondary.copy(alpha = 0.36f),
                        modifier = Modifier.size(20.dp),
                    )
                }
                BorderlessMenuButton(
                    menuState = forwardHistoryMenuState,
                    menuItems = forwardHistoryItems,
                    backdrop = backdrop,
                    buttonWidth = 38.dp,
                    buttonHeight = 42.dp,
                    enabled = clusterInteractive && tab.canGoForward,
                    onBeforeOpenMenu = { onBeforeOpenMenu(forwardHistoryMenuState) },
                    onShortTap = {
                        if (tab.canGoForward) pool.goForward(tab.id)
                    },
                ) {
                    TerraSymbolIcon(
                        symbol = LiquidKitSymbol.Forward,
                        tint = if (tab.canGoForward) ink else secondary.copy(alpha = 0.36f),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .offset {
                    val c = collapseState.value.coerceIn(0f, 1f)
                    val leftReservedPx = with(density) { (leftClusterWidthDp + spacingDp).toPx() }
                    val rightReservedPx = with(density) { (rightClusterWidthDp + spacingDp).toPx() }
                    val asymOffsetPx = lerp((leftReservedPx - rightReservedPx) * 0.5f, 0f, c).roundToInt()
                    IntOffset(asymOffsetPx, 0)
                }
                .layout { measurable, _ ->
                    val c = collapseState.value.coerceIn(0f, 1f)
                    val wPx = lerp(expandedCenterWidthPx, miniCenterWidthPx, c).roundToInt().coerceAtLeast(1)
                    val hPx = lerp(pillFullHeightPx, pillMiniHeightPx, c).roundToInt().coerceAtLeast(1)
                    val placeable = measurable.measure(Constraints.fixed(wPx, hPx))
                    layout(wPx, hPx) {
                        placeable.placeRelative(0, 0)
                    }
                }
                .onGloballyPositioned { if (it.isAttached) landPillBoundsRef[0] = it.boundsInRoot() },
        ) {
            AnchoredGlassMenuOverlay(
                menuState = addressLongPressMenuState,
                items = addressLongPressItems,
                backdrop = backdrop,
                anchorBoundsProvider = { landPillBoundsRef[0] },
                ink = ink,
                isDark = isDark,
                centerHorizontally = true,
                showScrim = true,
                anchorGapDp = 12.dp,
                liftedAnchorContent = { progress ->
                    SelectedAddressPillPreview(
                        tab = tab,
                        isPrivate = state.activeGroup.isPrivate,
                        collapse = collapseState.value,
                        ink = ink,
                        secondary = secondary,
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
                    val swipeTxProvider: () -> Float = { swipeFractionProvider() * pillStepPx }

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
                        onBarDragDelta = onBarDragDelta,
                        onBarDragEnd = onBarDragEnd,
                        enableOverviewPullUpProvider = { collapseState.value < 0.5f },
                        ink = ink,
                        secondary = secondary,
                        accentBlue = accentBlue,
                        pillHeight = 46.dp,
                        innerPill = false,
                        surfaceTint = clusterTint,
                        swipeTxPx = 0f,
                        collapseProvider = { collapseState.value },
                        swipeTxPxProvider = swipeTxProvider,
                        onBeforeOpenMenu = onBeforeOpenMenu,
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
                                surfaceTint = clusterTint,
                                ink = ink,
                                secondary = secondary,
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

        LiquidKitGlassSurface(
            backdrop = backdrop,
            shape = CircleShape,
            tint = clusterTint,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .height(44.dp)
                .layout { measurable, constraints ->
                    val sideAlpha = (1f - collapseState.value * 1.75f).coerceIn(0f, 1f)
                    val clusterAlpha = sideAlpha * hideAllForPageMenuState.value
                    if (clusterAlpha <= 0.005f) {
                        layout(0, 0) {}
                    } else {
                        val placeable = measurable.measure(constraints)
                        layout(placeable.width, placeable.height) {
                            placeable.placeRelative(0, 0)
                        }
                    }
                }
                .graphicsLayer {
                    val sideAlpha = (1f - collapseState.value * 1.75f).coerceIn(0f, 1f)
                    alpha = sideAlpha * hideAllForPageMenuState.value
                    val s = lerp(0.7f, 1f, sideAlpha)
                    scaleX = s
                    scaleY = s
                },
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BorderlessMenuButton(
                    menuState = shareQuickMenuState,
                    menuItems = shareQuickMenuItems,
                    backdrop = backdrop,
                    buttonWidth = 38.dp,
                    buttonHeight = 42.dp,
                    enabled = clusterInteractive,
                    onBeforeOpenMenu = { onBeforeOpenMenu(shareQuickMenuState) },
                    onShortTap = { state.showShareSheet = true },
                ) {
                    TerraSymbolIcon(symbol = LiquidKitSymbol.Share, tint = ink, modifier = Modifier.size(19.dp))
                }
                BorderlessMenuButton(
                    menuState = bookmarksQuickMenuState,
                    menuItems = bookmarksQuickMenuItems,
                    backdrop = backdrop,
                    buttonWidth = 38.dp,
                    buttonHeight = 42.dp,
                    enabled = clusterInteractive,
                    onBeforeOpenMenu = { onBeforeOpenMenu(bookmarksQuickMenuState) },
                    onShortTap = {
                        state.libraryInitialTab = LibraryTab.BOOKMARKS
                        state.showLibrarySheet = true
                    },
                ) {
                    SafariOpenBookIcon(color = ink, modifier = Modifier.size(19.dp))
                }
                BorderlessMenuButton(
                    menuState = newTabQuickMenuState,
                    menuItems = newTabQuickMenuItems,
                    backdrop = backdrop,
                    buttonWidth = 38.dp,
                    buttonHeight = 42.dp,
                    enabled = clusterInteractive,
                    onBeforeOpenMenu = { onBeforeOpenMenu(newTabQuickMenuState) },
                    onShortTap = onOpenNewTab,
                ) {
                    TerraSymbolIcon(symbol = LiquidKitSymbol.Add, tint = ink, modifier = Modifier.size(20.dp))
                }
                BorderlessMenuButton(
                    menuState = quickTabsMenuState,
                    menuItems = quickTabsMenuItems,
                    backdrop = backdrop,
                    buttonWidth = 38.dp,
                    buttonHeight = 42.dp,
                    enabled = clusterInteractive,
                    onBeforeOpenMenu = { onBeforeOpenMenu(quickTabsMenuState) },
                    onShortTap = onOpenOverview,
                ) {
                    SafariTabsIcon(color = ink, modifier = Modifier.size(19.dp))
                }
            }
        }
    }
}

@Composable
internal fun AdjacentSwipePill(
    adjacentTab: BrowserTab?,
    isPrivate: Boolean,
    backdrop: Backdrop,
    innerPill: Boolean,
    surfaceTint: Color,
    ink: Color,
    secondary: Color,
    collapse: Float,
    modifier: Modifier = Modifier,
    collapseProvider: () -> Float = { collapse },
    tintAlphaProvider: (() -> Float)? = null,
) {
    val isStart = adjacentTab?.isStartPage != false
    val isCompactTitle by remember { derivedStateOf { collapseProvider() > 0.5f } }
    val label = when {
        adjacentTab == null -> "新建标签页"
        isStart -> if (isCompactTitle) "起始页" else "搜索或输入网站"
        else -> adjacentTab.host.ifBlank { adjacentTab.url }
    }
    val density = LocalDensity.current
    val sideWidthFullPx = with(density) { 40.dp.toPx() }
    val padFullPx = with(density) { 6.dp.toPx() }
    val padMiniPx = with(density) { 2.dp.toPx() }

    val innerContent: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .layout { measurable, constraints ->
                    val c = collapseProvider().coerceIn(0f, 1f)
                    val ph = lerp(padFullPx, padMiniPx, c).roundToInt()
                    val w = (constraints.maxWidth - ph * 2).coerceAtLeast(1)
                    val placeable = measurable.measure(constraints.copy(minWidth = w, maxWidth = w))
                    layout(constraints.maxWidth, placeable.height) {
                        placeable.placeRelative(ph, 0)
                    }
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .layout { measurable, _ ->
                        val c = collapseProvider().coerceIn(0f, 1f)
                        val wPx = lerp(sideWidthFullPx, 0f, c).roundToInt()
                        if (wPx <= 1) {
                            layout(0, 0) {}
                        } else {
                            val placeable = measurable.measure(Constraints.fixed(wPx, 36.dp.roundToPx()))
                            layout(wPx, placeable.height) { placeable.placeRelative(0, 0) }
                        }
                    }
                    .graphicsLayer {
                        alpha = (1f - collapseProvider().coerceIn(0f, 1f) * 1.8f).coerceIn(0f, 1f)
                    },
                contentAlignment = Alignment.Center,
            ) {
                SafariPageSettingsIcon(color = ink, modifier = Modifier.size(19.dp))
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.graphicsLayer {
                        val s = lerp(1f, 12.5f / 15f, collapseProvider().coerceIn(0f, 1f))
                        scaleX = s
                        scaleY = s
                    },
                ) {
                    if (isPrivate && !isStart) {
                        SafariPrivateMaskIcon(color = secondary, modifier = Modifier.size(13.dp))
                    } else if (!isStart && adjacentTab?.url?.startsWith("https://", ignoreCase = true) == true) {
                        SafariLockIcon(color = secondary, modifier = Modifier.size(12.dp))
                    }
                    BasicText(
                        text = label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(
                            color = if (isStart && !isCompactTitle) secondary else ink,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                    )
                }
            }
            Box(
                modifier = Modifier
                    .layout { measurable, _ ->
                        val c = collapseProvider().coerceIn(0f, 1f)
                        val wPx = lerp(sideWidthFullPx, 0f, c).roundToInt()
                        if (wPx <= 1) {
                            layout(0, 0) {}
                        } else {
                            val placeable = measurable.measure(Constraints.fixed(wPx, 36.dp.roundToPx()))
                            layout(wPx, placeable.height) { placeable.placeRelative(0, 0) }
                        }
                    }
                    .graphicsLayer {
                        alpha = (1f - collapseProvider().coerceIn(0f, 1f) * 1.8f).coerceIn(0f, 1f)
                    },
                contentAlignment = Alignment.Center,
            ) {
                TerraSymbolIcon(
                    symbol = if (isStart) LiquidKitSymbol.Search else LiquidKitSymbol.Reload,
                    tint = ink,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }

    if (innerPill) {
        Box(
            modifier = modifier
                .clip(CircleShape)
                .drawBehind {
                    val a = (surfaceTint.alpha * (tintAlphaProvider?.invoke() ?: 1f)).coerceIn(0f, 1f)
                    if (a > 0.005f) {
                        drawRect(surfaceTint.copy(alpha = a))
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            innerContent()
        }
    } else {
        LiquidKitGlassSurface(
            backdrop = backdrop,
            shape = CircleShape,
            tint = surfaceTint,
            modifier = modifier,
        ) {
            innerContent()
        }
    }
}

/**
 * Full / Minimized Address Pill wrapped in [LiquidMenuHost] with smooth pill-to-menu morph,
 * while the stationary outer gesture wrapper drives continuous unified 2D horizontal swipe & overview lift.
 */
@Composable
internal fun AddressPillSurface(
    state: TerraBrowserState,
    tab: BrowserTab,
    pool: TerraWebViewPool,
    backdrop: Backdrop,
    pageMenuState: io.liquidkit.LiquidKitMenuState,
    pageMenuItems: List<LiquidKitMenuItem>,
    addressLongPressMenuState: io.liquidkit.LiquidKitMenuState,
    collapse: Float,
    onBarDragStart: () -> Unit,
    onBarDragDelta: (dx: Float, dy: Float) -> Unit,
    onBarDragEnd: (velocityX: Float, velocityY: Float) -> Unit,
    enableOverviewPullUpProvider: () -> Boolean = { true },
    ink: Color,
    secondary: Color,
    accentBlue: Color,
    pillHeight: Dp,
    innerPill: Boolean,
    surfaceTint: Color,
    swipeTxPx: Float = 0f,
    modifier: Modifier = Modifier,
    collapseProvider: () -> Float = { collapse },
    swipeTxPxProvider: () -> Float = { swipeTxPx },
    innerTintAlphaProvider: (() -> Float)? = null,
    onBeforeOpenMenu: (io.liquidkit.LiquidKitMenuState) -> Unit = {},
) {
    val isDark = liquidKitAppearance().dark
    val scope = rememberCoroutineScope()
    val triggerHaptic = rememberLiquidKitHaptics()
    val density = LocalDensity.current
    val latestTab by rememberUpdatedState(tab)
    val latestCollapseProvider by rememberUpdatedState(collapseProvider)
    val latestEnablePullUpProvider by rememberUpdatedState(enableOverviewPullUpProvider)
    val latestOnBarDragStart by rememberUpdatedState(onBarDragStart)
    val latestOnBarDragDelta by rememberUpdatedState(onBarDragDelta)
    val latestOnBarDragEnd by rememberUpdatedState(onBarDragEnd)
    val latestOnBeforeOpenMenu by rememberUpdatedState(onBeforeOpenMenu)

    val isCompactTitle by remember { derivedStateOf { collapseProvider() > 0.5f } }
    val sideControlsInteractive by remember {
        derivedStateOf {
            collapseProvider() < 0.5f && !pageMenuState.expanded && !addressLongPressMenuState.expanded
        }
    }
    val pageMenuTriggerActive by remember {
        derivedStateOf {
            (collapseProvider() < 0.5f && !addressLongPressMenuState.expanded) ||
                pageMenuState.expanded
        }
    }
    val sideWidthFullPx = with(density) { 40.dp.toPx() }
    val padFullPx = with(density) { 6.dp.toPx() }
    val padMiniPx = with(density) { 2.dp.toPx() }

    var isHoldingCenter by remember { mutableStateOf(false) }
    LaunchedEffect(pageMenuState.expanded) {
        if (pageMenuState.expanded) {
            isHoldingCenter = false
        }
    }
    LaunchedEffect(addressLongPressMenuState.expanded) {
        if (addressLongPressMenuState.expanded) {
            isHoldingCenter = false
        }
    }

    // Outer container is strictly STATIONARY so pointerInput deltas/velocity never fight translationX.
    // Keyed on Unit so active swipe/drag touches are never dropped across mid-gesture tab switches.
    Box(
        modifier = modifier
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (down.isConsumed || pageMenuState.expanded || addressLongPressMenuState.expanded) {
                        return@awaitEachGesture
                    }
                    val curCollapse = latestCollapseProvider().coerceIn(0f, 1f)
                    val curSideEdgePx = if (curCollapse >= 0.5f) {
                        0f
                    } else {
                        with(density) { (8.dp + androidx.compose.ui.unit.lerp(36.dp, 0.dp, curCollapse)).toPx() }
                    }
                    val inCenter = curCollapse >= 0.5f ||
                        down.position.x in curSideEdgePx..(size.width - curSideEdgePx)
                    if (inCenter) {
                        isHoldingCenter = true
                    }
                    val slop = viewConfiguration.touchSlop
                    val vt = VelocityTracker()
                    vt.addPosition(down.uptimeMillis, down.position)
                    var totalDx = 0f
                    var totalDy = 0f
                    var mode = 0 // 0=undecided, 1=unified 2D bar drag, 3=ignored downward drag, 4=longPressMenu
                    var dragStarted = false
                    var endedCleanly = false
                    var gestureActive = true

                    val holdJob = if (inCenter) {
                        scope.launch {
                            delay(360L)
                            if (isActive && gestureActive && mode == 0) {
                                mode = 4
                                isHoldingCenter = false
                                triggerHaptic(LiquidKitHaptic.LongPressLift)
                                latestOnBeforeOpenMenu(addressLongPressMenuState)
                                addressLongPressMenuState.show()
                            }
                        }
                    } else {
                        null
                    }

                    try {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (event.changes.any { it.id != down.id && it.pressed }) {
                                gestureActive = false
                                holdJob?.cancel()
                                isHoldingCenter = false
                            }
                            if (!change.pressed) {
                                gestureActive = false
                                holdJob?.cancel()
                                isHoldingCenter = false
                                val vel = vt.calculateVelocity()
                                if (mode == 1 || mode == 4) {
                                    change.consume()
                                }
                                when (mode) {
                                    0 -> if (inCenter && !pageMenuState.expanded && !addressLongPressMenuState.expanded) {
                                        if (latestCollapseProvider() > 0.5f) {
                                            state.isToolbarMinimized = false
                                        } else {
                                            state.addressDraft = latestTab.url
                                            state.isAddressEditing = true
                                        }
                                    }
                                    1 -> {
                                        endedCleanly = true
                                        latestOnBarDragEnd(vel.x, vel.y)
                                    }
                                }
                                break
                            }
                            val d = change.positionChange()
                            vt.addPosition(change.uptimeMillis, change.position)
                            totalDx += d.x
                            totalDy += d.y

                            if (mode == 0 && (abs(totalDx) > slop || abs(totalDy) > slop)) {
                                holdJob?.cancel()
                                isHoldingCenter = false
                                mode = if (totalDy > 0f && abs(totalDy) > abs(totalDx) * 1.15f) {
                                    3
                                } else {
                                    1
                                }
                            }
                            if (mode == 1) {
                                if (!dragStarted) {
                                    dragStarted = true
                                    latestOnBarDragStart()
                                }
                                change.consume()
                                latestOnBarDragDelta(d.x, if (latestEnablePullUpProvider()) d.y else 0f)
                            } else if (mode == 4) {
                                change.consume()
                            }
                        }
                    } finally {
                        gestureActive = false
                        holdJob?.cancel()
                        isHoldingCenter = false
                        if (dragStarted && !endedCleanly) {
                            latestOnBarDragEnd(0f, 0f)
                        }
                    }
                }
            },
    ) {
        LaunchedEffect(pageMenuState.expanded, pageMenuItems.isEmpty()) {
            if (pageMenuState.expanded && pageMenuItems.isEmpty()) {
                pageMenuState.dismiss()
            }
        }
        BackHandler(enabled = pageMenuState.expanded) { pageMenuState.dismiss() }
        LiquidKitAnchoredMenuHost(
            state = pageMenuState,
            items = pageMenuItems,
            backdrop = backdrop,
            presentation = LiquidKitMenuPresentation.ButtonTransform,
            modifier = Modifier.fillMaxSize(),
        ) {
            val holdScaleState = animateFloatAsState(
                targetValue = when {
                    addressLongPressMenuState.expanded -> 1.02f
                    isHoldingCenter -> 0.975f
                    else -> 1f
                },
                animationSpec = LiquidKitSpring.swiftUI(0.24f, 0.82f).spec,
                label = "PillHoldScale",
            )
            val isHttps = !tab.isStartPage && tab.url.startsWith("https://", ignoreCase = true)
            val pillCornerDp = if (pillHeight >= 48.dp) 24.dp else 20.dp
            val sourcePillModifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = swipeTxPxProvider()
                    val hs = holdScaleState.value
                    scaleX = hs
                    scaleY = hs
                }
                .liquidKitMenuSource(pageMenuState, cornerRadius = pillCornerDp)

            val pillContent: @Composable BoxScope.() -> Unit = {
                if (innerPill) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clip(CircleShape)
                            .drawBehind {
                                val a = (surfaceTint.alpha * (innerTintAlphaProvider?.invoke() ?: 1f)).coerceIn(0f, 1f)
                                if (a > 0.005f) {
                                    drawRect(surfaceTint.copy(alpha = a))
                                }
                            },
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .layout { measurable, constraints ->
                            val c = collapseProvider().coerceIn(0f, 1f)
                            val ph = lerp(padFullPx, padMiniPx, c).roundToInt()
                            val w = (constraints.maxWidth - ph * 2).coerceAtLeast(1)
                            val placeable = measurable.measure(constraints.copy(minWidth = w, maxWidth = w))
                            layout(constraints.maxWidth, placeable.height) {
                                placeable.placeRelative(ph, 0)
                            }
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    var tunePressed by remember { mutableStateOf(false) }
                    val tuneScaleState = animateFloatAsState(
                        targetValue = if (tunePressed) 0.86f else 1f,
                        animationSpec = LiquidKitSpring.swiftUI(0.20f, 0.82f).spec,
                        label = "TuneScale",
                    )
                    Box(
                        modifier = Modifier
                            .layout { measurable, constraints ->
                                val c = collapseProvider().coerceIn(0f, 1f)
                                val wPx = lerp(sideWidthFullPx, 0f, c).roundToInt()
                                val hPx = if (constraints.hasBoundedHeight && constraints.maxHeight > 0) {
                                    constraints.maxHeight
                                } else {
                                    pillHeight.roundToPx()
                                }
                                if (wPx <= 1) {
                                    layout(0, 0) {}
                                } else {
                                    val placeable = measurable.measure(Constraints.fixed(wPx, hPx))
                                    layout(wPx, placeable.height) { placeable.placeRelative(0, 0) }
                                }
                            }
                            .graphicsLayer {
                                val c = collapseProvider().coerceIn(0f, 1f)
                                alpha = (1f - c * 1.8f).coerceIn(0f, 1f)
                                scaleX = tuneScaleState.value
                                scaleY = tuneScaleState.value
                            }
                            .then(
                                if (pageMenuTriggerActive && pageMenuItems.isNotEmpty()) {
                                    Modifier.liquidKitMenuTrigger(
                                        state = pageMenuState,
                                        activation = LiquidKitMenuActivation.Press,
                                        beforeOpen = {
                                            triggerHaptic(LiquidKitHaptic.SelectionTick)
                                            latestOnBeforeOpenMenu(pageMenuState)
                                        },
                                        onHoldChange = { tunePressed = it },
                                    )
                                } else {
                                    Modifier
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        SafariPageSettingsIcon(color = ink, modifier = Modifier.size(19.dp))
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(horizontal = 4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            modifier = Modifier.graphicsLayer {
                                val s = lerp(1f, 12.5f / 15f, collapseProvider().coerceIn(0f, 1f))
                                scaleX = s
                                scaleY = s
                            },
                        ) {
                            if (state.activeGroup.isPrivate && !tab.isStartPage) {
                                SafariPrivateMaskIcon(color = secondary, modifier = Modifier.size(13.dp))
                            } else if (isHttps) {
                                SafariLockIcon(color = secondary, modifier = Modifier.size(12.dp))
                            }
                            BasicText(
                                text = if (tab.isStartPage) {
                                    if (isCompactTitle) "起始页" else "搜索或输入网站"
                                } else {
                                    tab.host.ifBlank { tab.url }
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = TextStyle(
                                    color = if (tab.isStartPage && !isCompactTitle) secondary else ink,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                ),
                            )
                        }
                    }

                    LiquidKitBorderlessButton(
                        onClick = {
                            if (!sideControlsInteractive) return@LiquidKitBorderlessButton
                            if (tab.isStartPage) {
                                state.addressDraft = ""
                                state.isAddressEditing = true
                            } else if (tab.isLoading) {
                                pool.stopLoading(tab.id)
                            } else {
                                pool.reload(tab.id)
                            }
                        },
                        enabled = sideControlsInteractive,
                        modifier = Modifier
                            .layout { measurable, constraints ->
                                val c = collapseProvider().coerceIn(0f, 1f)
                                val wPx = lerp(sideWidthFullPx, 0f, c).roundToInt()
                                val hPx = if (constraints.hasBoundedHeight && constraints.maxHeight > 0) {
                                    constraints.maxHeight
                                } else {
                                    pillHeight.roundToPx()
                                }
                                if (wPx <= 1) {
                                    layout(0, 0) {}
                                } else {
                                    val placeable = measurable.measure(Constraints.fixed(wPx, hPx))
                                    layout(wPx, placeable.height) { placeable.placeRelative(0, 0) }
                                }
                            }
                            .graphicsLayer {
                                val c = collapseProvider().coerceIn(0f, 1f)
                                alpha = (1f - c * 1.8f).coerceIn(0f, 1f)
                            },
                    ) {
                        TerraSymbolIcon(
                            symbol = when {
                                tab.isStartPage -> LiquidKitSymbol.Search
                                tab.isLoading -> LiquidKitSymbol.Close
                                else -> LiquidKitSymbol.Reload
                            },
                            tint = ink,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                SafariPillLoadingBar(
                    tabId = tab.id,
                    isLoading = tab.isLoading,
                    progress = tab.progress,
                    accentBlue = accentBlue,
                )
            }

            if (innerPill) {
                Box(
                    modifier = sourcePillModifier.clip(CircleShape),
                    content = pillContent,
                )
            } else {
                LiquidKitGlassSurface(
                    backdrop = backdrop,
                    shape = CircleShape,
                    tint = surfaceTint,
                    modifier = sourcePillModifier,
                    content = pillContent,
                )
            }
        }
    }
}

@Composable
private fun BoxScope.SafariPillLoadingBar(
    tabId: String,
    isLoading: Boolean,
    progress: Float,
    accentBlue: Color,
) {
    val barProgress = remember(tabId) { Animatable(0f) }
    val barAlpha = remember(tabId) { Animatable(0f) }
    var wasLoading by remember(tabId) { mutableStateOf(false) }

    LaunchedEffect(tabId, isLoading, progress) {
        if (isLoading) {
            wasLoading = true
            val targetP = progress.coerceIn(0.12f, 0.96f)
            if (barAlpha.value < 0.99f) {
                barAlpha.snapTo(1f)
            }
            if (barProgress.value < 0.05f) {
                barProgress.snapTo(targetP)
            } else if (targetP > barProgress.value) {
                barProgress.animateTo(targetP, tween(160))
            }
        } else if (wasLoading) {
            wasLoading = false
            if (progress >= 0.95f) {
                barProgress.animateTo(1f, tween(130))
            }
            barAlpha.animateTo(0f, tween(220))
            barProgress.snapTo(0f)
        }
    }

    if (isLoading || barAlpha.value > 0.01f) {
        Box(
            modifier = Modifier
                .fillMaxWidth(barProgress.value.coerceIn(0f, 1f))
                .height(2.dp)
                .align(Alignment.BottomStart)
                .graphicsLayer { alpha = barAlpha.value.coerceIn(0f, 1f) }
                .background(accentBlue),
        )
    }
}

@Composable
internal fun SafariBottomProgressiveBlur(
    backdrop: Backdrop,
    height: Dp,
    alpha: Float,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    alphaProvider: () -> Float = { alpha },
    heightScaleProvider: () -> Float = { 1f },
) {
    val baseColor = if (isDark) Color(0xFF0A0C10) else Color.White
    val tintBrush = remember(isDark) {
        Brush.verticalGradient(
            0.00f to Color.Transparent,
            0.26f to baseColor.copy(alpha = if (isDark) 0.24f else 0.20f),
            0.60f to baseColor.copy(alpha = if (isDark) 0.56f else 0.50f),
            1.00f to baseColor.copy(alpha = if (isDark) 0.80f else 0.74f),
        )
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .layout { measurable, constraints ->
                val a = alphaProvider().coerceIn(0f, 1f)
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
                this.alpha = alphaProvider().coerceIn(0f, 1f)
                scaleY = heightScaleProvider().coerceIn(0.05f, 1f)
                transformOrigin = TransformOrigin(0.5f, 1f)
            }
            .drawBehind {
                drawRect(brush = tintBrush)
            },
    )
}

@Composable
internal fun Modifier.shortTapOrLongPress(
    enabled: Boolean = true,
    onHoldChange: (Boolean) -> Unit = {},
    onLongPress: () -> Unit,
    onShortTap: () -> Unit,
): Modifier {
    val scope = rememberCoroutineScope()
    val triggerHaptic = rememberLiquidKitHaptics()
    val latestEnabled by rememberUpdatedState(enabled)
    val latestOnHold by rememberUpdatedState(onHoldChange)
    val latestLongPress by rememberUpdatedState(onLongPress)
    val latestShortTap by rememberUpdatedState(onShortTap)
    return this.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (!latestEnabled || down.isConsumed) return@awaitEachGesture
            latestOnHold(true)
            val slop = viewConfiguration.touchSlop
            var totalDx = 0f
            var totalDy = 0f
            var cancelled = false
            var triggeredLong = false
            var gestureActive = true
            val holdJob = scope.launch {
                delay(360L)
                if (isActive && gestureActive && !cancelled && latestEnabled) {
                    triggeredLong = true
                    latestOnHold(false)
                    triggerHaptic(LiquidKitHaptic.LongPressLift)
                    latestLongPress()
                }
            }
            try {
                while (true) {
                    val ev = awaitPointerEvent(PointerEventPass.Initial)
                    val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                    if (ev.changes.any { it.id != down.id && it.pressed }) {
                        cancelled = true
                        holdJob.cancel()
                        latestOnHold(false)
                    }
                    if (!ch.pressed) {
                        gestureActive = false
                        holdJob.cancel()
                        latestOnHold(false)
                        if (triggeredLong) {
                            ch.consume()
                        } else if (!cancelled && latestEnabled) {
                            latestShortTap()
                        }
                        break
                    }
                    val d = ch.positionChange()
                    totalDx += d.x
                    totalDy += d.y
                    if (!triggeredLong && (abs(totalDx) > slop || abs(totalDy) > slop)) {
                        cancelled = true
                        holdJob.cancel()
                        latestOnHold(false)
                    } else if (triggeredLong) {
                        ch.consume()
                    }
                }
            } finally {
                gestureActive = false
                holdJob.cancel()
                latestOnHold(false)
            }
        }
    }
}

@Composable
internal fun BorderlessMenuButton(
    menuState: io.liquidkit.LiquidKitMenuState,
    menuItems: List<LiquidKitMenuItem>,
    backdrop: Backdrop,
    buttonWidth: Dp = 50.dp,
    buttonHeight: Dp = 44.dp,
    enabled: Boolean = true,
    beforeOpen: () -> Unit = {},
    onBeforeOpenMenu: () -> Unit = {},
    onShortTap: () -> Unit,
    content: @Composable () -> Unit,
) {
    val latestBeforeOpen by rememberUpdatedState(beforeOpen)
    val latestOnBeforeOpenMenu by rememberUpdatedState(onBeforeOpenMenu)
    val latestOnShortTap by rememberUpdatedState(onShortTap)
    val latestEnabled by rememberUpdatedState(enabled)
    val latestMenuItems by rememberUpdatedState(menuItems)

    var held by remember { mutableStateOf(false) }
    LaunchedEffect(menuState.expanded, menuItems.isEmpty()) {
        if (menuState.expanded) held = false
        if (menuState.expanded && menuItems.isEmpty()) {
            menuState.dismiss()
        }
    }
    BackHandler(enabled = menuState.expanded) { menuState.dismiss() }

    val scaleState = animateFloatAsState(
        targetValue = if (held) 1.15f else 1f,
        animationSpec = LiquidKitSpring.swiftUI(0.22f, 0.82f).spec,
        label = "BorderlessScale",
    )
    val pressAlphaState = animateFloatAsState(
        targetValue = if (held) 0.62f else 1f,
        animationSpec = LiquidKitSpring.swiftUI(0.22f, 0.82f).spec,
        label = "BorderlessAlpha",
    )
    LiquidKitAnchoredMenuHost(
        state = menuState,
        items = menuItems,
        backdrop = backdrop,
        presentation = LiquidKitMenuPresentation.Popup,
    ) {
        val triggerMod = when {
            (enabled || menuState.expanded) && menuItems.isNotEmpty() -> Modifier.liquidKitMenuTrigger(
                state = menuState,
                activation = LiquidKitMenuActivation.LongPress,
                beforeOpen = {
                    if (latestEnabled && latestMenuItems.isNotEmpty()) {
                        latestBeforeOpen()
                        latestOnBeforeOpenMenu()
                    }
                },
                onShortTap = {
                    if (latestEnabled) latestOnShortTap()
                },
                onHoldChange = { held = it },
            )
            enabled -> Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { if (latestEnabled) latestOnShortTap() },
            )
            else -> Modifier
        }
        Box(
            modifier = Modifier
                .size(width = buttonWidth, height = buttonHeight)
                .graphicsLayer {
                    scaleX = scaleState.value
                    scaleY = scaleState.value
                    alpha = pressAlphaState.value
                }
                .then(triggerMod),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}

@Composable
private fun FiveActionIconBar(
    tab: BrowserTab,
    pool: TerraWebViewPool,
    backdrop: Backdrop,
    backHistoryMenuState: io.liquidkit.LiquidKitMenuState,
    backHistoryItems: List<LiquidKitMenuItem>,
    forwardHistoryMenuState: io.liquidkit.LiquidKitMenuState,
    forwardHistoryItems: List<LiquidKitMenuItem>,
    shareQuickMenuState: io.liquidkit.LiquidKitMenuState,
    shareQuickMenuItems: List<LiquidKitMenuItem>,
    bookmarksQuickMenuState: io.liquidkit.LiquidKitMenuState,
    bookmarksQuickMenuItems: List<LiquidKitMenuItem>,
    quickTabsMenuState: io.liquidkit.LiquidKitMenuState,
    quickTabsMenuItems: List<LiquidKitMenuItem>,
    ink: Color,
    secondary: Color,
    enabled: Boolean = true,
    onBeforeOpenMenu: (io.liquidkit.LiquidKitMenuState) -> Unit = {},
    onShare: () -> Unit,
    onBookmarks: () -> Unit,
    onOpenOverview: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BorderlessMenuButton(
            menuState = backHistoryMenuState,
            menuItems = backHistoryItems,
            backdrop = backdrop,
            enabled = enabled && tab.canGoBack,
            onBeforeOpenMenu = { onBeforeOpenMenu(backHistoryMenuState) },
            onShortTap = {
                if (tab.canGoBack) pool.goBack(tab.id)
            },
        ) {
            TerraSymbolIcon(
                symbol = LiquidKitSymbol.Back,
                tint = if (tab.canGoBack) ink else secondary.copy(alpha = 0.36f),
                modifier = Modifier.size(21.dp),
            )
        }

        BorderlessMenuButton(
            menuState = forwardHistoryMenuState,
            menuItems = forwardHistoryItems,
            backdrop = backdrop,
            enabled = enabled && tab.canGoForward,
            onBeforeOpenMenu = { onBeforeOpenMenu(forwardHistoryMenuState) },
            onShortTap = {
                if (tab.canGoForward) pool.goForward(tab.id)
            },
        ) {
            TerraSymbolIcon(
                symbol = LiquidKitSymbol.Forward,
                tint = if (tab.canGoForward) ink else secondary.copy(alpha = 0.36f),
                modifier = Modifier.size(21.dp),
            )
        }

        BorderlessMenuButton(
            menuState = shareQuickMenuState,
            menuItems = shareQuickMenuItems,
            backdrop = backdrop,
            enabled = enabled,
            onBeforeOpenMenu = { onBeforeOpenMenu(shareQuickMenuState) },
            onShortTap = onShare,
        ) {
            TerraSymbolIcon(symbol = LiquidKitSymbol.Share, tint = ink, modifier = Modifier.size(21.dp))
        }

        BorderlessMenuButton(
            menuState = bookmarksQuickMenuState,
            menuItems = bookmarksQuickMenuItems,
            backdrop = backdrop,
            enabled = enabled,
            onBeforeOpenMenu = { onBeforeOpenMenu(bookmarksQuickMenuState) },
            onShortTap = onBookmarks,
        ) {
            SafariOpenBookIcon(color = ink, modifier = Modifier.size(21.dp))
        }

        BorderlessMenuButton(
            menuState = quickTabsMenuState,
            menuItems = quickTabsMenuItems,
            backdrop = backdrop,
            enabled = enabled,
            onBeforeOpenMenu = { onBeforeOpenMenu(quickTabsMenuState) },
            onShortTap = onOpenOverview,
        ) {
            SafariTabsIcon(color = ink, modifier = Modifier.size(21.dp))
        }
    }
}

@Composable
internal fun FindInPageGlassBar(
    state: TerraBrowserState,
    pool: TerraWebViewPool,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
) {
    val isDark = liquidKitAppearance().dark
    val colors = liquidKitColors(isDark)
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    val barTint = if (isDark) Color(0xFF1D1F27).copy(alpha = 0.72f) else Color(0xFFF6F7FB).copy(alpha = 0.72f)
    val borderStrokeColor = Color.White.copy(alpha = if (isDark) 0.16f else 0.62f)

    LiquidKitGlassSurface(
        backdrop = backdrop,
        shape = CircleShape,
        tint = barTint,
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .border(0.6.dp, borderStrokeColor, CircleShape),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            TerraSymbolIcon(symbol = LiquidKitSymbol.Search, tint = colors.secondary, modifier = Modifier.size(18.dp))
            BasicTextField(
                value = state.findQuery,
                onValueChange = { q ->
                    state.findQuery = q
                    pool.findInPage(state.activeTab.id, q)
                },
                singleLine = true,
                textStyle = TextStyle(color = colors.ink, fontSize = 14.sp),
                cursorBrush = SolidColor(Color(0xFF0A84FF)),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { pool.findNext(state.activeTab.id, true) }),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
                decorationBox = { inner ->
                    if (state.findQuery.isEmpty()) {
                        BasicText(
                            text = "在页面上查找…",
                            style = TextStyle(color = colors.secondary, fontSize = 14.sp),
                        )
                    }
                    inner()
                },
            )
            if (state.findQuery.isNotEmpty()) {
                LiquidKitText(
                    text = "${state.findMatchIndex}/${state.findMatchCount}",
                    style = liquidKitTypography().caption,
                    color = colors.secondary,
                    monospacedDigits = true,
                )
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(colors.secondary.copy(alpha = 0.24f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            state.findQuery = ""
                            pool.clearFind(state.activeTab.id)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    TerraSymbolIcon(symbol = LiquidKitSymbol.Close, tint = colors.ink, modifier = Modifier.size(12.dp))
                }
            }
            LiquidKitBorderlessButton(
                onClick = { pool.findNext(state.activeTab.id, false) },
                modifier = Modifier.size(30.dp),
            ) {
                TerraSymbolIcon(symbol = LiquidKitSymbol.Ascending, tint = colors.ink, modifier = Modifier.size(18.dp))
            }
            LiquidKitBorderlessButton(
                onClick = { pool.findNext(state.activeTab.id, true) },
                modifier = Modifier.size(30.dp),
            ) {
                TerraSymbolIcon(symbol = LiquidKitSymbol.Descending, tint = colors.ink, modifier = Modifier.size(18.dp))
            }
            LiquidKitBorderlessButton(
                onClick = {
                    state.findInPageVisible = false
                    state.findQuery = ""
                    pool.clearFind(state.activeTab.id)
                },
            ) {
                BasicText(
                    text = "完成",
                    style = TextStyle(color = Color(0xFF0A84FF), fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                )
            }
        }
    }
}

/**
 * 100% opaque Address & Search Editing sheet with its own isolated [overlayBackdrop]
 * so underlying webpage pixels never leak through, while the focused input bar shifts
 * cleanly into luminous Liquid Glass.
 */
@Composable
fun AddressEditingOverlay(
    state: TerraBrowserState,
    progress: Float,
    onSubmitInput: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val isDark = liquidKitAppearance().dark
    val colors = liquidKitColors(isDark)
    val accentBlue = Color(0xFF0A84FF)
    val overlayBackdrop = rememberLiquidKitBackdrop()
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val latestProgress = rememberUpdatedState(progress)
    val isEditing = state.isAddressEditing

    var textFieldValue by remember {
        val initial = state.addressDraft
        mutableStateOf(TextFieldValue(initial, TextRange(0, initial.length)))
    }

    LaunchedEffect(isEditing) {
        if (isEditing) {
            val initial = state.addressDraft
            textFieldValue = TextFieldValue(initial, TextRange(0, initial.length))
            focusRequester.requestFocus()
        } else {
            focusManager.clearFocus()
        }
    }

    val solidBg = if (isDark) Color(0xFF0E1015) else Color(0xFFF2F4F8)

    val query = textFieldValue.text.trim()
    val matchedItems = remember(query, state.favorites.size, state.bookmarks.size, state.readingList.size, state.history.size) {
        if (query.isEmpty()) {
            emptyList()
        } else {
            val favs = state.favorites.filter {
                it.title.contains(query, ignoreCase = true) || it.url.contains(query, ignoreCase = true)
            }.map { Triple(it.title, it.url, "个人收藏") }
            val bms = state.bookmarks.filter {
                it.title.contains(query, ignoreCase = true) || it.url.contains(query, ignoreCase = true)
            }.map { Triple(it.title, it.url, "书签") }
            val rds = state.readingList.filter {
                it.title.contains(query, ignoreCase = true) || it.url.contains(query, ignoreCase = true)
            }.map { Triple(it.title, it.url, "阅读列表") }
            val his = state.history.filter {
                it.title.contains(query, ignoreCase = true) || it.url.contains(query, ignoreCase = true)
            }.map { Triple(it.title, it.url, "历史") }
            (favs + bms + rds + his).distinctBy { it.second }.take(8)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                val p = latestProgress.value.coerceIn(0f, 1f)
                alpha = (p * 1.4f).coerceIn(0f, 1f)
            },
    ) {
        // Isolated 100% opaque substrate recorded into overlayBackdrop.
        // Disable click interception immediately once closing starts (!isEditing) so taps pass straight through.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .liquidKitBackdrop(overlayBackdrop)
                .background(solidBg)
                .then(
                    if (isEditing) {
                        Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onDismiss,
                        )
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.TopCenter,
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp)
                    .fillMaxHeight()
                    .statusBarsPadding()
                    .displayCutoutPadding()
                    .imePadding()
                    .padding(horizontal = 16.dp)
                    .graphicsLayer {
                        val p = latestProgress.value.coerceIn(0f, 1f)
                        alpha = p
                        translationY = lerp(28.dp.toPx(), 0f, p)
                    },
                contentPadding = PaddingValues(top = 12.dp, bottom = 140.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item("search_engines") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SearchEngine.entries.forEach { engine ->
                            val selected = state.searchEngine == engine
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(99.dp))
                                    .background(
                                        if (selected) accentBlue.copy(alpha = 0.18f)
                                        else if (isDark) Color.White.copy(alpha = 0.08f)
                                        else Color.Black.copy(alpha = 0.06f),
                                    )
                                    .border(
                                        0.75.dp,
                                        if (selected) accentBlue.copy(alpha = 0.6f) else Color.Transparent,
                                        RoundedCornerShape(99.dp),
                                    )
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        enabled = isEditing,
                                    ) {
                                        state.searchEngine = engine
                                        state.persist()
                                    }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                            ) {
                                BasicText(
                                    text = engine.label,
                                    style = TextStyle(
                                        color = if (selected) accentBlue else colors.secondary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    ),
                                )
                            }
                        }
                    }
                }

                if (query.isEmpty()) {
                    if (state.showRecentSearches) {
                        item("recent_header") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                BasicText(
                                    text = "最近搜索",
                                    style = TextStyle(color = colors.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold),
                                )
                                if (state.recentSearches.isNotEmpty()) {
                                    BasicText(
                                        text = "全部清除",
                                        style = TextStyle(color = accentBlue, fontSize = 13.sp, fontWeight = FontWeight.Medium),
                                        modifier = Modifier
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null,
                                                enabled = isEditing,
                                            ) { state.clearRecentSearches() }
                                            .padding(4.dp),
                                    )
                                }
                            }
                        }

                        items(state.recentSearches, key = { "rs_$it" }) { item ->
                            SuggestionRow(
                                icon = LiquidKitSymbol.Search,
                                title = item,
                                subtitle = "${state.searchEngine.label} 搜索",
                                ink = colors.ink,
                                secondary = colors.secondary,
                                isDark = isDark,
                                enabled = isEditing,
                            ) {
                                onSubmitInput(item)
                            }
                        }
                    }

                    if (state.favorites.isNotEmpty()) {
                        item("fav_header") {
                            BasicText(
                                text = "个人收藏",
                                style = TextStyle(color = colors.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold),
                                modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                            )
                        }
                        items(state.favorites.take(4), key = { "qs_${it.id}" }) { fav ->
                            SuggestionRow(
                                icon = LiquidKitSymbol.StarFilled,
                                title = fav.title,
                                subtitle = BrowserUrlUtils.displayHost(fav.url),
                                ink = colors.ink,
                                secondary = colors.secondary,
                                isDark = isDark,
                                enabled = isEditing,
                            ) {
                                onSubmitInput(fav.url)
                            }
                        }
                    }
                } else {
                    item("direct_search") {
                        SuggestionRow(
                            icon = LiquidKitSymbol.Search,
                            title = "使用 ${state.searchEngine.label} 搜索“$query”",
                            subtitle = BrowserUrlUtils.normalizeInputToUrl(query, state.searchEngine),
                            ink = accentBlue,
                            secondary = colors.secondary,
                            isDark = isDark,
                            enabled = isEditing,
                        ) {
                            onSubmitInput(query)
                        }
                    }
                    items(matchedItems, key = { "hit_${it.second}" }) { (title, url, kind) ->
                        SuggestionRow(
                            icon = LiquidKitSymbol.Globe,
                            title = title,
                            subtitle = "$kind · ${BrowserUrlUtils.displayHost(url)}",
                            ink = colors.ink,
                            secondary = colors.secondary,
                            isDark = isDark,
                            enabled = isEditing,
                        ) {
                            onSubmitInput(url)
                        }
                    }
                }
            }
        }

        // Bottom fade scrim behind the floating URL bar so scrolled suggestions never clash behind input text
        val scrimBrush = remember(solidBg) {
            Brush.verticalGradient(
                0f to Color.Transparent,
                0.28f to solidBg.copy(alpha = 0.92f),
                0.46f to solidBg,
                1f to solidBg,
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .imePadding()
                .height(152.dp)
                .background(scrimBrush),
        )

        // Foreground focused URL input bar smoothly morphing up into Liquid Glass (search chips safely at top)
        val glassTint = if (isDark) Color(0xFF1F222A).copy(alpha = 0.96f) else Color.White.copy(alpha = 0.97f)
        val borderMaxAlpha = if (isDark) 0.22f else 0.75f

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .widthIn(max = 640.dp)
                .imePadding()
                .navigationBarsPadding()
                .displayCutoutPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .graphicsLayer {
                    val p = latestProgress.value.coerceIn(0f, 1f)
                    val s = lerp(0.92f, 1f, p)
                    scaleX = s
                    scaleY = s
                    translationY = lerp(46.dp.toPx(), 0f, p)
                    alpha = (p * 1.35f).coerceIn(0f, 1f)
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            LiquidKitGlassSurface(
                backdrop = overlayBackdrop,
                shape = RoundedCornerShape(26.dp),
                tint = glassTint,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .drawWithContent {
                        drawContent()
                        val p = latestProgress.value.coerceIn(0f, 1f)
                        val borderAlpha = borderMaxAlpha * p
                        if (borderAlpha > 0.005f) {
                            val r = 26.dp.toPx()
                            drawRoundRect(
                                color = Color.White.copy(alpha = borderAlpha),
                                cornerRadius = CornerRadius(r, r),
                                style = Stroke(width = 0.75.dp.toPx()),
                            )
                        }
                    },
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TerraSymbolIcon(
                        symbol = LiquidKitSymbol.Search,
                        tint = colors.secondary,
                        modifier = Modifier.size(18.dp),
                    )
                    BasicTextField(
                        value = textFieldValue,
                        onValueChange = { if (isEditing) textFieldValue = it },
                        enabled = isEditing,
                        singleLine = true,
                        textStyle = TextStyle(color = colors.ink, fontSize = 15.sp),
                        cursorBrush = SolidColor(accentBlue),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(
                            onGo = {
                                if (isEditing && textFieldValue.text.isNotBlank()) {
                                    onSubmitInput(textFieldValue.text)
                                }
                            },
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester),
                        decorationBox = { inner ->
                            if (textFieldValue.text.isEmpty()) {
                                BasicText(
                                    text = "搜索或输入网站名称",
                                    style = TextStyle(color = colors.secondary, fontSize = 15.sp),
                                )
                            }
                            inner()
                        },
                    )
                    if (textFieldValue.text.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(colors.secondary.copy(alpha = 0.25f))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    enabled = isEditing,
                                ) { textFieldValue = TextFieldValue("") },
                            contentAlignment = Alignment.Center,
                        ) {
                            TerraSymbolIcon(
                                symbol = LiquidKitSymbol.Close,
                                tint = colors.ink,
                                modifier = Modifier.size(13.dp),
                            )
                        }
                    }
                }
            }

            LiquidKitIconButton(
                onClick = { if (isEditing) onDismiss() },
                backdrop = overlayBackdrop,
                size = 48.dp,
                isInteractive = isEditing,
                modifier = Modifier.graphicsLayer {
                    val p = latestProgress.value.coerceIn(0f, 1f)
                    val cs = lerp(0.55f, 1f, p)
                    scaleX = cs
                    scaleY = cs
                },
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    TerraSymbolIcon(symbol = LiquidKitSymbol.Close, tint = colors.ink, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun SuggestionRow(
    icon: LiquidKitSymbol,
    title: String,
    subtitle: String,
    ink: Color,
    secondary: Color,
    isDark: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (isDark) Color.White.copy(alpha = 0.08f) else Color.White)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TerraSymbolIcon(symbol = icon, tint = secondary, modifier = Modifier.size(18.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            BasicText(
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(color = ink, fontSize = 14.sp, fontWeight = FontWeight.Medium),
            )
            BasicText(
                text = subtitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(color = secondary, fontSize = 11.sp),
            )
        }
    }
}

internal fun buildPageMenuItems(
    state: TerraBrowserState,
    tab: BrowserTab,
): List<LiquidKitMenuItem> {
    val enabledConfigs = state.pageMenuItems.filter { it.enabled }.sortedBy { it.order }
    val list = mutableListOf<LiquidKitMenuItem>()
    val compactMode = state.tabBarMode == TabBarMode.COMPACT
    if (compactMode) {
        list.add(
            LiquidKitMenuItem(
                key = "share",
                title = "共享",
                symbol = LiquidKitSymbol.Share,
            ),
        )
        list.add(
            LiquidKitMenuItem(
                key = "add_bookmark",
                title = "添加到书签",
                symbol = LiquidKitSymbol.Bookmark,
                enabled = !tab.isStartPage,
            ),
        )
        list.add(
            LiquidKitMenuItem(
                key = "open_library",
                title = "书签与历史",
                symbol = LiquidKitSymbol.Folder,
            ),
        )
    }
    enabledConfigs.forEachIndexed { idx, cfg ->
        when (cfg.id) {
            "find" -> list.add(
                LiquidKitMenuItem(
                    key = "find",
                    title = "在页面上查找",
                    symbol = LiquidKitSymbol.Search,
                    startsSection = idx == 0 && compactMode,
                    enabled = !tab.isStartPage,
                ),
            )
            "zoom" -> list.add(
                LiquidKitMenuItem(
                    key = "zoom",
                    title = "页面缩放与外观",
                    subtitle = "${tab.pageZoom}%",
                    symbol = LiquidKitSymbol.SliderHorizontal,
                ),
            )
            "blocker" -> list.add(
                LiquidKitMenuItem(
                    key = "blocker",
                    title = if (tab.contentBlockerEnabled) "关闭内容拦截器" else "开启内容拦截器",
                    symbol = LiquidKitSymbol.Filter,
                    checked = tab.contentBlockerEnabled,
                ),
            )
            "hide_toolbar" -> list.add(
                LiquidKitMenuItem(
                    key = "hide_toolbar",
                    title = "隐藏工具栏",
                    symbol = LiquidKitSymbol.ListBulletRectangle,
                ),
            )
            "desktop" -> list.add(
                LiquidKitMenuItem(
                    key = "desktop",
                    title = if (tab.requestDesktopSite) "请求移动版网站" else "请求桌面网站",
                    symbol = LiquidKitSymbol.Globe,
                    checked = tab.requestDesktopSite,
                    enabled = !tab.isStartPage,
                ),
            )
            "tab_ops" -> {
                val moveChildren = state.tabGroups.map { grp ->
                    LiquidKitMenuItem(
                        key = "move_grp:${grp.id}",
                        title = grp.name,
                        checked = grp.id == tab.groupId,
                    )
                }
                list.add(
                    LiquidKitMenuItem(
                        key = "tab_ops",
                        title = "标签页操作",
                        symbol = LiquidKitSymbol.SquareOnSquare,
                        startsSection = idx > 0,
                        children = listOf(
                            LiquidKitMenuItem(
                                key = "move_to_group",
                                title = "移到标签页组",
                                symbol = LiquidKitSymbol.Folder,
                                children = moveChildren,
                            ),
                            LiquidKitMenuItem(
                                key = "pin_tab",
                                title = if (tab.isPinned) "取消固定标签页" else "固定标签页",
                                symbol = LiquidKitSymbol.Star,
                                checked = tab.isPinned,
                            ),
                            LiquidKitMenuItem(
                                key = "add_favorite",
                                title = "添加到个人收藏",
                                symbol = LiquidKitSymbol.StarFilled,
                                enabled = !tab.isStartPage,
                            ),
                            LiquidKitMenuItem(
                                key = "add_bookmark",
                                title = "添加到书签",
                                symbol = LiquidKitSymbol.Bookmark,
                                enabled = !tab.isStartPage,
                            ),
                            LiquidKitMenuItem(
                                key = "add_reading",
                                title = "添加到阅读列表",
                                symbol = LiquidKitSymbol.Document,
                                enabled = !tab.isStartPage,
                            ),
                        ),
                    ),
                )
            }
            "privacy" -> {
                list.add(
                    LiquidKitMenuItem(
                        key = "privacy",
                        title = "隐私与安全",
                        symbol = LiquidKitSymbol.CheckCircle,
                        startsSection = true,
                        children = listOf(
                            LiquidKitMenuItem(
                                key = "privacy_report",
                                title = "隐私报告",
                                subtitle = "已阻止 ${state.totalTrackersBlocked} 个追踪器",
                                symbol = LiquidKitSymbol.Filter,
                            ),
                            LiquidKitMenuItem(
                                key = "dark_mode_sub",
                                title = "自适应深色模式",
                                subtitle = tab.adaptiveDarkMode.label,
                                symbol = LiquidKitSymbol.Moon,
                                children = SiteDarkMode.entries.map { mode ->
                                    LiquidKitMenuItem(
                                        key = "site_dark:${mode.name}",
                                        title = mode.label,
                                        checked = tab.adaptiveDarkMode == mode,
                                    )
                                },
                            ),
                        ),
                    ),
                )
            }
        }
    }
    list.add(
        LiquidKitMenuItem(
            key = "open_settings",
            title = "浏览器设置",
            subtitle = "标签页栏：${state.tabBarMode.label}",
            symbol = LiquidKitSymbol.SliderHorizontal,
            startsSection = true,
        ),
    )
    list.add(
        LiquidKitMenuItem(
            key = "customize_menu",
            title = "自定义菜单…",
            symbol = LiquidKitSymbol.Rename,
        ),
    )
    return list
}

internal fun handlePageMenuAction(
    key: String,
    state: TerraBrowserState,
    tab: BrowserTab,
    pool: TerraWebViewPool,
    onOpenNewTab: () -> Unit,
) {
    when {
        key == "share" -> state.showShareSheet = true
        key == "open_library" -> {
            state.libraryInitialTab = LibraryTab.BOOKMARKS
            state.showLibrarySheet = true
        }
        key == "find" -> state.findInPageVisible = true
        key == "zoom" -> state.showZoomSheet = true
        key == "open_settings" -> state.showSettingsSheet = true
        key == "blocker" -> {
            state.updateTab(tab.id) { it.copy(contentBlockerEnabled = !it.contentBlockerEnabled) }
            pool.reload(tab.id)
            state.persist()
        }
        key == "hide_toolbar" -> state.isToolbarHidden = true
        key == "desktop" -> {
            state.updateTab(tab.id) { it.copy(requestDesktopSite = !it.requestDesktopSite) }
            state.persist()
        }
        key == "pin_tab" -> {
            state.togglePinTab(tab.id)
            state.showBanner(if (!tab.isPinned) "已固定标签页" else "已取消固定")
        }
        key == "add_favorite" -> {
            state.addFavorite(tab.title, tab.url)
            state.showBanner("已添加到个人收藏")
        }
        key == "add_bookmark" -> {
            state.addBookmark(tab.title, tab.url)
            state.showBanner("已添加到书签")
        }
        key == "add_reading" -> {
            state.addReadingList(tab.title, tab.url)
            state.showBanner("已添加到阅读列表")
        }
        key == "privacy_report" -> state.showPrivacyReportSheet = true
        key == "customize_menu" -> state.showCustomizeMenuSheet = true
        key == "new_tab" -> onOpenNewTab()
        key.startsWith("move_grp:") -> {
            val grpId = key.removePrefix("move_grp:")
            state.moveTabToGroup(tab.id, grpId)
            state.selectGroup(grpId)
        }
        key.startsWith("site_dark:") -> {
            val modeName = key.removePrefix("site_dark:")
            val mode = SiteDarkMode.entries.firstOrNull { it.name == modeName } ?: return
            state.updateTab(tab.id) { it.copy(adaptiveDarkMode = mode) }
            state.persist()
        }
    }
}

internal fun buildAddressLongPressMenuItems(
    state: TerraBrowserState,
    tab: BrowserTab,
): List<LiquidKitMenuItem> {
    val count = state.tabsInGroup(state.activeGroup.id).size
    val switchChildren = state.tabGroups.map { grp ->
        LiquidKitMenuItem(
            key = "switch_grp:${grp.id}",
            title = grp.name,
            subtitle = "${state.tabsInGroup(grp.id).size} 个标签页",
            checked = grp.id == state.activeGroup.id,
        )
    }
    val moveChildren = state.tabGroups.map { grp ->
        LiquidKitMenuItem(
            key = "move_grp:${grp.id}",
            title = grp.name,
            checked = grp.id == tab.groupId,
        )
    }
    return listOf(
        LiquidKitMenuItem(key = "share", title = "共享", symbol = LiquidKitSymbol.Share),
        LiquidKitMenuItem(
            key = "copy_url",
            title = "拷贝链接",
            symbol = LiquidKitSymbol.Clipboard,
            enabled = !tab.isStartPage,
        ),
        LiquidKitMenuItem(
            key = "paste_go",
            title = "粘贴并前往",
            symbol = LiquidKitSymbol.Link,
        ),
        LiquidKitMenuItem(
            key = "switch_group",
            title = "切换标签页组",
            symbol = LiquidKitSymbol.SquareOnSquare,
            startsSection = true,
            children = switchChildren,
        ),
        LiquidKitMenuItem(
            key = "move_group",
            title = "移到标签页组",
            symbol = LiquidKitSymbol.Folder,
            children = moveChildren,
        ),
        LiquidKitMenuItem(
            key = "close_all",
            title = "关闭所有 $count 个标签页",
            symbol = LiquidKitSymbol.Trash,
            startsSection = true,
            destructive = true,
        ),
        LiquidKitMenuItem(
            key = "close_current",
            title = "关闭当前标签页",
            symbol = LiquidKitSymbol.Close,
            destructive = true,
        ),
    )
}

internal fun handleAddressLongPressAction(
    key: String,
    state: TerraBrowserState,
    tab: BrowserTab,
    pool: TerraWebViewPool,
    clipboard: androidx.compose.ui.platform.ClipboardManager,
) {
    when {
        key == "share" -> state.showShareSheet = true
        key == "copy_url" -> {
            val target = tab.url.ifBlank { "https://terra.browser/start" }
            clipboard.setText(AnnotatedString(target))
            state.showBanner("已拷贝链接")
        }
        key == "paste_go" -> {
            val clipText = clipboard.getText()?.text?.trim().orEmpty()
            if (clipText.isNotBlank()) {
                state.navigateActiveTab(clipText)
            } else {
                state.showBanner("剪贴板为空")
            }
        }
        key == "close_current" -> {
            pool.evictTab(tab.id)
            state.closeTab(tab.id)
        }
        key == "close_all" -> {
            val ids = state.tabsInGroup(state.activeGroup.id).map { it.id }
            ids.forEach { pool.evictTab(it) }
            state.closeAllTabsInGroup(state.activeGroup.id)
        }
        key.startsWith("switch_grp:") -> state.selectGroup(key.removePrefix("switch_grp:"))
        key.startsWith("move_grp:") -> {
            val targetId = key.removePrefix("move_grp:")
            state.moveTabToGroup(tab.id, targetId)
            state.selectGroup(targetId)
        }
    }
}

internal fun buildQuickTabsMenuItems(
    state: TerraBrowserState,
    tab: BrowserTab,
): List<LiquidKitMenuItem> {
    val count = state.tabsInGroup(state.activeGroup.id).size
    val moveChildren = state.tabGroups.map { grp ->
        LiquidKitMenuItem(
            key = "move_grp:${grp.id}",
            title = grp.name,
            checked = grp.id == tab.groupId,
        )
    }
    val customGroups = state.tabGroups.filter { !it.isDefault && !it.isPrivate }
    val privateGroup = state.tabGroups.firstOrNull { it.isPrivate }
    val defaultGroup = state.tabGroups.firstOrNull { it.isDefault }

    val list = mutableListOf<LiquidKitMenuItem>()
    customGroups.forEach { grp ->
        list.add(
            LiquidKitMenuItem(
                key = "switch_grp:${grp.id}",
                title = grp.name,
                symbol = LiquidKitSymbol.SquareOnSquare,
                checked = grp.id == state.activeGroup.id,
            ),
        )
    }
    if (privateGroup != null) {
        list.add(
            LiquidKitMenuItem(
                key = "switch_grp:${privateGroup.id}",
                title = "无痕浏览",
                symbol = LiquidKitSymbol.Filter,
                checked = state.activeGroup.id == privateGroup.id,
                startsSection = customGroups.isNotEmpty(),
            ),
        )
    }
    if (defaultGroup != null) {
        val defaultCount = state.tabsInGroup(defaultGroup.id).size
        list.add(
            LiquidKitMenuItem(
                key = "switch_grp:${defaultGroup.id}",
                title = "${defaultCount}个标签页",
                symbol = LiquidKitSymbol.SquareOnSquare,
                checked = state.activeGroup.id == defaultGroup.id,
                startsSection = privateGroup == null && customGroups.isNotEmpty(),
            ),
        )
    }
    list.add(
        LiquidKitMenuItem(
            key = "close_all",
            title = "关闭所有 $count 个标签页",
            symbol = LiquidKitSymbol.Close,
            startsSection = true,
            destructive = true,
        ),
    )
    list.add(
        LiquidKitMenuItem(
            key = "close_current",
            title = "关闭此标签页",
            symbol = LiquidKitSymbol.Close,
            destructive = true,
        ),
    )
    list.add(
        LiquidKitMenuItem(
            key = "move_group",
            title = "移到标签页组",
            symbol = LiquidKitSymbol.Folder,
            children = moveChildren,
        ),
    )
    list.add(
        LiquidKitMenuItem(
            key = "new_private_tab",
            title = "新建无痕浏览标签页",
            symbol = LiquidKitSymbol.Add,
        ),
    )
    list.add(
        LiquidKitMenuItem(
            key = "new_tab",
            title = "新建标签页",
            symbol = LiquidKitSymbol.Add,
        ),
    )
    return list
}
