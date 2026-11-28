package com.terra.browser.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.terra.browser.model.BrowserLayoutProfile
import com.terra.browser.model.BrowserUrlUtils
import com.terra.browser.model.FavoriteItem
import com.terra.browser.model.ReadingListItem
import com.terra.browser.state.TerraBrowserState
import io.liquidkit.LiquidKitButton
import io.liquidkit.LiquidKitColors
import io.liquidkit.LiquidKitContextMenuHost
import io.liquidkit.LiquidKitMenuItem
import io.liquidkit.LiquidKitPressButton
import io.liquidkit.LiquidKitSymbol
import io.liquidkit.LiquidKitText
import io.liquidkit.liquidKitAppearance
import io.liquidkit.liquidKitBackdrop
import io.liquidkit.liquidKitColors
import io.liquidkit.liquidKitContextMenu
import io.liquidkit.liquidKitTypography
import io.liquidkit.rememberLiquidKitBackdrop
import io.liquidkit.rememberLiquidKitMenuState

private sealed interface StartPageContextTarget {
    data class Favorite(val item: FavoriteItem) : StartPageContextTarget
    data class Reading(val item: ReadingListItem) : StartPageContextTarget
}

private val StartPageTileShape = RoundedCornerShape(18.dp)
private val StartPageIconShape = RoundedCornerShape(16.dp)
private val StartPageCardShape = RoundedCornerShape(20.dp)

@Composable
fun StartPage(
    state: TerraBrowserState,
    onOpenUrl: (String) -> Unit,
    onOpenUrlInNewTab: (String) -> Unit,
    onOpenReadingList: () -> Unit,
    onOpenPrivacyReport: () -> Unit,
    onOpenSettings: () -> Unit = {},
    topExtraPadding: Dp = 0.dp,
    layoutProfile: BrowserLayoutProfile = BrowserLayoutProfile.PHONE_PORTRAIT,
    modifier: Modifier = Modifier,
    onScrollFractionChange: ((Float) -> Unit)? = null,
) {
    val isDark = liquidKitAppearance().dark
    val colors = liquidKitColors(isDark)
    val isPrivate = state.activeGroup.isPrivate
    val clipboard = LocalClipboardManager.current
    val density = LocalDensity.current
    val listState = rememberLazyListState()
    val scrollRampPx = with(density) { 28.dp.toPx() }.coerceAtLeast(1f)

    LaunchedEffect(listState, scrollRampPx, onScrollFractionChange) {
        val cb = onScrollFractionChange ?: return@LaunchedEffect
        snapshotFlow {
            if (listState.firstVisibleItemIndex > 0) 1f
            else (listState.firstVisibleItemScrollOffset / scrollRampPx).coerceIn(0f, 1f)
        }.collect { cb(it) }
    }

    val bgBrush = remember(isPrivate, isDark) {
        if (isPrivate) {
            Brush.verticalGradient(
                listOf(
                    Color(0xFF1A1B1F),
                    Color(0xFF141518),
                    Color(0xFF101114),
                ),
            )
        } else if (isDark) {
            Brush.verticalGradient(
                listOf(
                    Color(0xFF141820),
                    Color(0xFF111318),
                    Color(0xFF0E1014),
                ),
            )
        } else {
            Brush.verticalGradient(
                listOf(
                    Color(0xFFF3F5FA),
                    Color(0xFFEEF1F7),
                    Color(0xFFE7ECF5),
                ),
            )
        }
    }

    val cardBg = if (isDark || isPrivate) Color.White.copy(alpha = 0.085f) else Color.White.copy(alpha = 0.78f)
    val cardBorder = if (isDark || isPrivate) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.06f)
    val primaryInk = if (isPrivate) Color(0xFFF4F5F8) else colors.ink
    val secondaryInk = if (isPrivate) Color(0xFF9EA2AE) else colors.secondary
    val accentBlue = Color(0xFF0A84FF)

    val favCols = if (layoutProfile == BrowserLayoutProfile.PHONE_PORTRAIT) 4 else 6
    val collapsedFavCount = favCols * 2
    val navBarBottomDp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomClearance = navBarBottomDp + when (layoutProfile) {
        BrowserLayoutProfile.PHONE_PORTRAIT -> 150.dp
        BrowserLayoutProfile.PHONE_LANDSCAPE -> 84.dp
        BrowserLayoutProfile.TABLET_LANDSCAPE -> 36.dp
    }
    val horizontalPad = when (layoutProfile) {
        BrowserLayoutProfile.PHONE_PORTRAIT -> 18.dp
        BrowserLayoutProfile.PHONE_LANDSCAPE -> 24.dp
        BrowserLayoutProfile.TABLET_LANDSCAPE -> 32.dp
    }
    val contentConstraint = Modifier
        .fillMaxWidth()
        .widthIn(max = 760.dp)

    var contextTarget by remember { mutableStateOf<StartPageContextTarget?>(null) }
    val contextMenuItems = remember(contextTarget) {
        when (val target = contextTarget) {
            is StartPageContextTarget.Favorite -> listOf(
                LiquidKitMenuItem(key = "new_tab", title = "在新标签页中打开", symbol = LiquidKitSymbol.SquareOnSquare),
                LiquidKitMenuItem(key = "copy", title = "拷贝链接", symbol = LiquidKitSymbol.Clipboard),
                LiquidKitMenuItem(key = "remove", title = "从个人收藏移除", symbol = LiquidKitSymbol.Trash, startsSection = true, destructive = true),
            )
            is StartPageContextTarget.Reading -> listOf(
                LiquidKitMenuItem(key = "new_tab", title = "在新标签页中打开", symbol = LiquidKitSymbol.SquareOnSquare),
                LiquidKitMenuItem(
                    key = "toggle_read",
                    title = if (target.item.isRead) "标为未读" else "标为已读",
                    symbol = LiquidKitSymbol.CheckCircle,
                ),
                LiquidKitMenuItem(key = "copy", title = "拷贝链接", symbol = LiquidKitSymbol.Clipboard),
                LiquidKitMenuItem(key = "remove", title = "从阅读列表移除", symbol = LiquidKitSymbol.Trash, startsSection = true, destructive = true),
            )
            null -> emptyList()
        }
    }
    val contextMenuState = rememberLiquidKitMenuState { picked ->
        when (val target = contextTarget) {
            is StartPageContextTarget.Favorite -> when (picked.key) {
                "new_tab" -> onOpenUrlInNewTab(target.item.url)
                "copy" -> {
                    clipboard.setText(AnnotatedString(target.item.url))
                    state.showBanner("已拷贝 ${target.item.title} 链接")
                }
                "remove" -> {
                    state.removeFavorite(target.item.id)
                    state.showBanner("已移除 ${target.item.title}")
                }
            }
            is StartPageContextTarget.Reading -> when (picked.key) {
                "new_tab" -> onOpenUrlInNewTab(target.item.url)
                "toggle_read" -> state.toggleReadingItemRead(target.item.id)
                "copy" -> {
                    clipboard.setText(AnnotatedString(target.item.url))
                    state.showBanner("已拷贝链接")
                }
                "remove" -> {
                    state.removeReadingListItem(target.item.id)
                    state.showBanner("已从阅读列表移除")
                }
            }
            null -> Unit
        }
    }

    val startBackdrop = rememberLiquidKitBackdrop()

    LiquidKitContextMenuHost(
        state = contextMenuState,
        items = contextMenuItems,
        colors = colors,
        modifier = modifier.fillMaxSize(),
        preview = {
            when (val target = contextTarget) {
                is StartPageContextTarget.Favorite -> {
                    val item = target.item
                    Row(
                        modifier = Modifier
                            .padding(14.dp)
                            .width(240.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(item.colorArgb)),
                            contentAlignment = Alignment.Center,
                        ) {
                            BasicText(
                                text = item.shortGlyph,
                                style = TextStyle(color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold),
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            BasicText(
                                text = item.title,
                                style = TextStyle(color = colors.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                            )
                            BasicText(
                                text = BrowserUrlUtils.displayHost(item.url),
                                style = TextStyle(color = colors.secondary, fontSize = 12.sp),
                            )
                        }
                    }
                }
                is StartPageContextTarget.Reading -> {
                    val entry = target.item
                    Column(
                        modifier = Modifier
                            .padding(14.dp)
                            .width(260.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        BasicText(
                            text = entry.title,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            style = TextStyle(color = colors.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                        )
                        BasicText(
                            text = BrowserUrlUtils.displayHost(entry.url),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = TextStyle(color = colors.secondary, fontSize = 12.sp),
                        )
                    }
                }
                null -> Unit
            }
        },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .liquidKitBackdrop(startBackdrop)
                .background(bgBrush),
        )
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .displayCutoutPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(start = horizontalPad, end = horizontalPad, top = 14.dp + topExtraPadding, bottom = bottomClearance),
            verticalArrangement = Arrangement.spacedBy(if (layoutProfile == BrowserLayoutProfile.PHONE_LANDSCAPE) 18.dp else 22.dp),
        ) {
        if (isPrivate) {
            item("private_banner") {
                Column(
                    modifier = contentConstraint
                        .clip(RoundedCornerShape(20.dp))
                        .background(cardBg)
                        .border(0.75.dp, cardBorder, RoundedCornerShape(20.dp))
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        SafariPrivateMaskIcon(color = primaryInk, modifier = Modifier.size(24.dp))
                        BasicText(
                            text = "无痕浏览模式",
                            style = TextStyle(color = primaryInk, fontSize = 18.sp, fontWeight = FontWeight.Bold),
                        )
                    }
                    BasicText(
                        text = "Terra Browser 不会记录你访问的页面、搜索历史或自动填充信息。离开无痕标签页时，站点状态将保持隔离。",
                        style = TextStyle(color = secondaryInk, fontSize = 13.sp, lineHeight = 18.sp),
                    )
                }
            }
        }

        // Favorites section
        item("favorites_section") {
            val visibleFavorites = if (state.showAllFavorites) state.favorites else state.favorites.take(collapsedFavCount)
            Column(
                modifier = contentConstraint
                    .animateContentSize(spring(dampingRatio = 0.84f, stiffness = 420f)),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SectionHeaderRow(
                    title = "个人收藏",
                    actionLabel = if (state.favorites.size > collapsedFavCount) {
                        if (state.showAllFavorites) "收起" else "全部显示"
                    } else {
                        null
                    },
                    primaryInk = primaryInk,
                    secondaryInk = secondaryInk,
                    onAction = { state.showAllFavorites = !state.showAllFavorites },
                )

                val rows = visibleFavorites.chunked(favCols)
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    rows.forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            rowItems.forEach { fav ->
                                Box(
                                    modifier = Modifier.weight(1f),
                                    contentAlignment = Alignment.TopCenter,
                                ) {
                                    FavoriteTile(
                                        item = fav,
                                        primaryInk = primaryInk,
                                        menuState = contextMenuState,
                                        onPrepareContext = { contextTarget = StartPageContextTarget.Favorite(fav) },
                                        onTap = { onOpenUrl(fav.url) },
                                    )
                                }
                            }
                            repeat(favCols - rowItems.size) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }

        // Privacy Report section
        item("privacy_report") {
            Column(
                modifier = contentConstraint,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SectionHeaderRow(
                    title = "隐私报告",
                    actionLabel = "详情",
                    primaryInk = primaryInk,
                    secondaryInk = secondaryInk,
                    onAction = onOpenPrivacyReport,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(StartPageCardShape)
                        .background(cardBg)
                        .border(0.75.dp, cardBorder, StartPageCardShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onOpenPrivacyReport,
                        )
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        SafariShieldIcon(color = primaryInk, modifier = Modifier.size(22.dp))
                        LiquidKitText(
                            text = state.totalTrackersBlocked.toString(),
                            style = liquidKitTypography().title3,
                            color = primaryInk,
                            fontWeight = FontWeight.Bold,
                            monospacedDigits = true,
                        )
                    }
                    BasicText(
                        text = "过去 7 天中，Terra Browser 阻止了 ${state.totalTrackersBlocked} 个追踪器剖析你的信息。",
                        style = TextStyle(color = secondaryInk, fontSize = 13.sp, lineHeight = 18.sp),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        // Reading List preview section
        if (state.readingList.isNotEmpty()) {
            item("reading_list") {
                Column(
                    modifier = contentConstraint,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    SectionHeaderRow(
                        title = "阅读列表",
                        actionLabel = "全部显示",
                        primaryInk = primaryInk,
                        secondaryInk = secondaryInk,
                        onAction = onOpenReadingList,
                    )
                    val readingCols = if (layoutProfile == BrowserLayoutProfile.TABLET_LANDSCAPE) 2 else 1
                    val previewItems = state.readingList.take(if (readingCols == 2) 4 else 3)
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        previewItems.chunked(readingCols).forEach { rowEntries ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                rowEntries.forEach { entry ->
                                    Row(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(StartPageCardShape)
                                            .background(cardBg)
                                            .border(0.75.dp, cardBorder, StartPageCardShape)
                                            .pointerInput(entry.id) {
                                                awaitPointerEventScope {
                                                    while (true) {
                                                        val ev = awaitPointerEvent(PointerEventPass.Initial)
                                                        if (ev.changes.any { it.changedToDownIgnoreConsumed() }) {
                                                            contextTarget = StartPageContextTarget.Reading(entry)
                                                        }
                                                    }
                                                }
                                            }
                                            .liquidKitContextMenu(contextMenuState, sourceShape = StartPageCardShape) {
                                                contextTarget = StartPageContextTarget.Reading(entry)
                                            }
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null,
                                            ) { onOpenUrl(entry.url) }
                                            .padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(46.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(Color(BrowserUrlUtils.accentColorForUrl(entry.url)).copy(alpha = 0.20f)),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            SafariCompassIcon(
                                                color = accentBlue,
                                                modifier = Modifier.size(22.dp),
                                            )
                                        }
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(3.dp),
                                        ) {
                                            BasicText(
                                                text = entry.title,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                                style = TextStyle(color = primaryInk, fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                                            )
                                            if (entry.excerpt.isNotBlank()) {
                                                BasicText(
                                                    text = entry.excerpt,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    style = TextStyle(color = secondaryInk, fontSize = 12.sp),
                                                )
                                            }
                                            BasicText(
                                                text = BrowserUrlUtils.displayHost(entry.url),
                                                maxLines = 1,
                                                style = TextStyle(color = secondaryInk.copy(alpha = 0.8f), fontSize = 11.sp),
                                            )
                                        }
                                    }
                                }
                                repeat(readingCols - rowEntries.size) {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }

        item("start_page_settings") {
            Box(
                modifier = contentConstraint,
                contentAlignment = Alignment.Center,
            ) {
                LiquidKitButton(
                    onClick = onOpenSettings,
                    backdrop = startBackdrop,
                    height = 40.dp,
                    contentPadding = PaddingValues(horizontal = 18.dp),
                ) {
                    TerraSymbolIcon(
                        symbol = LiquidKitSymbol.SliderHorizontal,
                        tint = primaryInk,
                        modifier = Modifier.size(16.dp),
                    )
                    BasicText(
                        text = "自定义与浏览器设置",
                        style = TextStyle(color = primaryInk, fontSize = 13.5.sp, fontWeight = FontWeight.Medium),
                    )
                }
            }
        }
        }
    }
}

@Composable
private fun SectionHeaderRow(
    title: String,
    actionLabel: String?,
    primaryInk: Color,
    secondaryInk: Color,
    onAction: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LiquidKitText(
            text = title,
            style = liquidKitTypography().title2,
            color = primaryInk,
        )
        if (actionLabel != null) {
            LiquidKitPressButton(onClick = onAction) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    BasicText(
                        text = actionLabel,
                        style = TextStyle(color = secondaryInk, fontSize = 13.sp, fontWeight = FontWeight.Medium),
                    )
                    TerraSymbolIcon(
                        symbol = LiquidKitSymbol.Forward,
                        tint = secondaryInk,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun FavoriteTile(
    item: FavoriteItem,
    primaryInk: Color,
    menuState: io.liquidkit.LiquidKitMenuState,
    onPrepareContext: () -> Unit,
    onTap: () -> Unit,
) {
    val iconBrush = remember(item.colorArgb) {
        Brush.linearGradient(
            listOf(
                Color(item.colorArgb),
                Color(item.colorArgb).copy(alpha = 0.82f),
            ),
        )
    }

    Column(
        modifier = Modifier
            .clip(StartPageTileShape)
            .pointerInput(item.id) {
                awaitPointerEventScope {
                    while (true) {
                        val ev = awaitPointerEvent(PointerEventPass.Initial)
                        if (ev.changes.any { it.changedToDownIgnoreConsumed() }) {
                            onPrepareContext()
                        }
                    }
                }
            }
            .liquidKitContextMenu(menuState, sourceShape = StartPageTileShape, prepare = onPrepareContext)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onTap,
            )
            .padding(vertical = 4.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(StartPageIconShape)
                .background(iconBrush)
                .border(0.75.dp, Color.White.copy(alpha = 0.24f), StartPageIconShape),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(
                text = item.shortGlyph,
                style = TextStyle(
                    color = Color.White,
                    fontSize = if (item.shortGlyph.length > 1) 18.sp else 24.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
        }
        BasicText(
            text = item.title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(
                color = primaryInk,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            ),
        )
    }
}
