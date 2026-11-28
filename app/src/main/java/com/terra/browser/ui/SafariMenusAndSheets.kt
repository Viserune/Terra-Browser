package com.terra.browser.ui

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.rememberSolidColorBackdrop
import com.terra.browser.model.BrowserUrlUtils
import com.terra.browser.model.ContextHitType
import com.terra.browser.model.DarkModePreference
import com.terra.browser.model.LibraryTab
import com.terra.browser.model.SearchEngine
import com.terra.browser.state.TerraBrowserState
import com.terra.browser.webview.TerraWebViewPool
import io.liquidkit.LiquidKitActionButton
import io.liquidkit.LiquidKitActionStyle
import io.liquidkit.LiquidKitAlert
import io.liquidkit.LiquidKitAlertAction
import io.liquidkit.LiquidKitAlertRole
import io.liquidkit.LiquidKitAlertTextField
import io.liquidkit.LiquidKitControlSize
import io.liquidkit.LiquidKitDivider
import io.liquidkit.LiquidKitGlassSurface
import io.liquidkit.LiquidKitLabeledContent
import io.liquidkit.LiquidKitOpacitySlider
import io.liquidkit.LiquidKitPressButton
import io.liquidkit.LiquidKitSegmentedPicker
import io.liquidkit.LiquidKitSheet
import io.liquidkit.LiquidKitSheetDetent
import io.liquidkit.LiquidKitStepper
import io.liquidkit.LiquidKitSwipeAction
import io.liquidkit.LiquidKitSwipeActions
import io.liquidkit.LiquidKitSymbol
import io.liquidkit.LiquidKitToggle
import io.liquidkit.LocalLiquidKitModalBackdrop
import io.liquidkit.liquidKitAppearance
import io.liquidkit.liquidKitColors

@Composable
fun SafariModalsHost(
    state: TerraBrowserState,
    pool: TerraWebViewPool,
    backdrop: Backdrop,
    onNavigateUrl: (String) -> Unit,
    onOpenInNewTab: (String) -> Unit,
    onOpenInBackgroundTab: (String) -> Unit,
) {
    val isDark = liquidKitAppearance().dark
    val colors = liquidKitColors(isDark)
    val modalBackdrop = LocalLiquidKitModalBackdrop.current
    val sheetSolidBackdrop = rememberSolidColorBackdrop(if (isDark) Color(0xFF1C1C1E) else Color(0xFFF2F2F7))
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val accentBlue = Color(0xFF0A84FF)

    // 1. Library Sheet (Bookmarks / Reading List / History)
    if (state.showLibrarySheet) {
        var selectedLibTab by remember(state.libraryInitialTab) {
            mutableStateOf(state.libraryInitialTab)
        }
        LiquidKitSheet(
            onDismissRequest = { state.showLibrarySheet = false },
            title = "书签与历史",
            doneLabel = "完成",
            detents = listOf(LiquidKitSheetDetent.Medium, LiquidKitSheetDetent.Large),
            initialDetent = LiquidKitSheetDetent.Large,
            colors = colors,
        ) {
            // Recently saved horizontal cards
            if (state.bookmarks.isNotEmpty()) {
                BasicText(
                    text = "最近保存",
                    style = TextStyle(color = colors.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(end = 6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp),
                ) {
                    items(state.bookmarks.take(4), key = { "recent_${it.id}" }) { bm ->
                        Column(
                            modifier = Modifier
                                .width(142.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(
                                    if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.04f),
                                )
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) {
                                    state.showLibrarySheet = false
                                    onNavigateUrl(bm.url)
                                }
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(BrowserUrlUtils.accentColorForUrl(bm.url))),
                                contentAlignment = Alignment.Center,
                            ) {
                                BasicText(
                                    text = bm.title.take(1).uppercase(),
                                    style = TextStyle(color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold),
                                )
                            }
                            BasicText(
                                text = bm.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = TextStyle(color = colors.ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                            )
                            BasicText(
                                text = BrowserUrlUtils.displayHost(bm.url),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = TextStyle(color = colors.secondary, fontSize = 11.sp),
                            )
                        }
                    }
                }
            }

            LiquidKitSegmentedPicker(
                options = LibraryTab.entries.map { it.label },
                selected = selectedLibTab.label,
                select = { label ->
                    selectedLibTab = LibraryTab.entries.firstOrNull { it.label == label } ?: LibraryTab.BOOKMARKS
                },
                colors = colors,
                backdrop = sheetSolidBackdrop,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when (selectedLibTab) {
                    LibraryTab.BOOKMARKS -> {
                        if (state.bookmarks.isEmpty()) {
                            EmptyLibraryNote(
                                title = "暂无书签",
                                subtitle = "点击底栏分享或页面菜单可将网页加入书签",
                                symbol = LiquidKitSymbol.Bookmark,
                                ink = colors.ink,
                                secondary = colors.secondary,
                                accentBlue = accentBlue,
                            )
                        } else {
                            state.bookmarks.forEach { bm ->
                                key(bm.id) {
                                    LiquidKitSwipeActions(
                                        rowKey = bm.id,
                                        trailing = listOf(
                                            LiquidKitSwipeAction(
                                                label = "删除",
                                                symbol = LiquidKitSymbol.Trash,
                                                color = Color(0xFFFF3B30),
                                                onClick = { state.removeBookmark(bm.id) },
                                            ),
                                        ),
                                    ) {
                                        LibraryRowCard(
                                            title = bm.title,
                                            subtitle = "${bm.folder} · ${BrowserUrlUtils.displayHost(bm.url)}",
                                            accentArgb = BrowserUrlUtils.accentColorForUrl(bm.url),
                                            ink = colors.ink,
                                            secondary = colors.secondary,
                                            isDark = isDark,
                                            onClick = {
                                                state.showLibrarySheet = false
                                                onNavigateUrl(bm.url)
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                    LibraryTab.READING_LIST -> {
                        if (state.readingList.isEmpty()) {
                            EmptyLibraryNote(
                                title = "暂无阅读列表项目",
                                subtitle = "将长篇文章加入阅读列表，方便随时离线阅读",
                                symbol = LiquidKitSymbol.Document,
                                ink = colors.ink,
                                secondary = colors.secondary,
                                accentBlue = accentBlue,
                            )
                        } else {
                            state.readingList.forEach { item ->
                                key(item.id) {
                                    LiquidKitSwipeActions(
                                        rowKey = item.id,
                                        leading = listOf(
                                            LiquidKitSwipeAction(
                                                label = if (item.isRead) "标为未读" else "标为已读",
                                                symbol = LiquidKitSymbol.CheckCircle,
                                                color = accentBlue,
                                                onClick = { state.toggleReadingItemRead(item.id) },
                                            ),
                                        ),
                                        trailing = listOf(
                                            LiquidKitSwipeAction(
                                                label = "删除",
                                                symbol = LiquidKitSymbol.Trash,
                                                color = Color(0xFFFF3B30),
                                                onClick = { state.removeReadingListItem(item.id) },
                                            ),
                                        ),
                                    ) {
                                        LibraryRowCard(
                                            title = item.title,
                                            subtitle = (if (item.isRead) "[已读] " else "") + BrowserUrlUtils.displayHost(item.url),
                                            accentArgb = BrowserUrlUtils.accentColorForUrl(item.url),
                                            ink = colors.ink,
                                            secondary = colors.secondary,
                                            isDark = isDark,
                                            onClick = {
                                                state.showLibrarySheet = false
                                                onNavigateUrl(item.url)
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                    LibraryTab.HISTORY -> {
                        var showAllHistory by remember { mutableStateOf(false) }
                        if (state.history.isNotEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                            ) {
                                LiquidKitActionButton(
                                    label = "清除历史",
                                    onClick = { state.clearHistory() },
                                    style = LiquidKitActionStyle.Tinted,
                                    tint = Color(0xFFFF3B30),
                                    controlSize = LiquidKitControlSize.Small,
                                    destructive = true,
                                    backdrop = backdrop,
                                )
                            }
                        }
                        if (state.history.isEmpty()) {
                            EmptyLibraryNote(
                                title = "暂无浏览历史",
                                subtitle = "你访问过的网页会按时间顺序显示在这里",
                                symbol = LiquidKitSymbol.HistoryClock,
                                ink = colors.ink,
                                secondary = colors.secondary,
                                accentBlue = accentBlue,
                            )
                        } else {
                            val visibleHistory = if (showAllHistory || state.history.size <= 25) {
                                state.history
                            } else {
                                state.history.take(25)
                            }
                            visibleHistory.forEach { entry ->
                                key(entry.id) {
                                    LiquidKitSwipeActions(
                                        rowKey = entry.id,
                                        trailing = listOf(
                                            LiquidKitSwipeAction(
                                                label = "删除",
                                                symbol = LiquidKitSymbol.Trash,
                                                color = Color(0xFFFF3B30),
                                                onClick = { state.removeHistoryEntry(entry.id) },
                                            ),
                                        ),
                                    ) {
                                        LibraryRowCard(
                                            title = entry.title,
                                            subtitle = BrowserUrlUtils.displayHost(entry.url),
                                            accentArgb = BrowserUrlUtils.accentColorForUrl(entry.url),
                                            ink = colors.ink,
                                            secondary = colors.secondary,
                                            isDark = isDark,
                                            onClick = {
                                                state.showLibrarySheet = false
                                                onNavigateUrl(entry.url)
                                            },
                                        )
                                    }
                                }
                            }
                            if (!showAllHistory && state.history.size > 25) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    LiquidKitActionButton(
                                        label = "显示全部 ${state.history.size} 条历史记录",
                                        onClick = { showAllHistory = true },
                                        style = LiquidKitActionStyle.Tinted,
                                        tint = accentBlue,
                                        controlSize = LiquidKitControlSize.Small,
                                        backdrop = backdrop,
                                    )
                                }
                            }
                        }
                    }
                    LibraryTab.DOWNLOADS -> {
                        val isPrivate = state.activeGroup.isPrivate
                        val rev = pool.downloads.revision
                        val downloadsList = remember(isPrivate, rev) { pool.downloads.read(isPrivate) }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        ) {
                            LiquidKitActionButton(
                                label = "下载目录",
                                onClick = {
                                    if (!pool.downloads.openDirectory()) {
                                        state.showBanner("无法打开系统下载目录")
                                    }
                                },
                                style = LiquidKitActionStyle.Tinted,
                                tint = accentBlue,
                                controlSize = LiquidKitControlSize.Small,
                                backdrop = backdrop,
                            )
                            if (downloadsList.any { !it.active }) {
                                LiquidKitActionButton(
                                    label = "清理已完成",
                                    onClick = { pool.downloads.clearInactive(isPrivate) },
                                    style = LiquidKitActionStyle.Tinted,
                                    tint = Color(0xFFFF3B30),
                                    controlSize = LiquidKitControlSize.Small,
                                    destructive = true,
                                    backdrop = backdrop,
                                )
                            }
                        }
                        if (downloadsList.isEmpty()) {
                            EmptyLibraryNote(
                                title = if (isPrivate) "当前无痕会话暂无下载项" else "暂无下载记录",
                                subtitle = "网页中下载的文件会显示在此处",
                                symbol = LiquidKitSymbol.Download,
                                ink = colors.ink,
                                secondary = colors.secondary,
                                accentBlue = accentBlue,
                            )
                        } else {
                            downloadsList.forEach { dl ->
                                key(dl.id) {
                                    val statusText = when {
                                        dl.completed -> "已完成 · ${BrowserUrlUtils.displayHost(dl.url)}"
                                        dl.failed -> "下载失败 · 点击重试"
                                        dl.total > 0L -> {
                                            val pct = ((dl.received * 100L) / dl.total).coerceIn(0L, 100L)
                                            "下载中 $pct% · ${BrowserUrlUtils.displayHost(dl.url)}"
                                        }
                                        else -> "准备中… · ${BrowserUrlUtils.displayHost(dl.url)}"
                                    }
                                    LiquidKitSwipeActions(
                                        rowKey = "dl_${dl.id}",
                                        trailing = listOf(
                                            LiquidKitSwipeAction(
                                                label = if (dl.active) "取消" else "移除",
                                                symbol = LiquidKitSymbol.Trash,
                                                color = Color(0xFFFF3B30),
                                                onClick = { pool.downloads.cancelOrRemove(isPrivate, dl) },
                                            ),
                                        ),
                                    ) {
                                        LibraryRowCard(
                                            title = dl.title.ifBlank { BrowserUrlUtils.displayHost(dl.url) },
                                            subtitle = statusText,
                                            accentArgb = if (dl.failed) 0xFFFF3B30 else BrowserUrlUtils.accentColorForUrl(dl.url),
                                            ink = colors.ink,
                                            secondary = colors.secondary,
                                            isDark = isDark,
                                            onClick = {
                                                if (dl.completed) {
                                                    if (!pool.downloads.open(dl)) {
                                                        state.showBanner("请在系统下载目录中查看")
                                                    }
                                                } else if (dl.failed) {
                                                    pool.downloads.enqueue(dl.url, null, null, dl.mime, isPrivate, replaceId = dl.id)
                                                    state.showBanner("正在重新下载：${dl.title}")
                                                } else {
                                                    state.showBanner("正在下载中…")
                                                }
                                            },
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

    // 2. Share Sheet
    if (state.showShareSheet) {
        val tab = state.activeTab
        val isStart = tab.isStartPage
        val shareUrl = tab.url.ifBlank { "https://terra.browser/start" }
        val shareTitle = tab.title.ifBlank { "Terra Browser" }
        LiquidKitSheet(
            onDismissRequest = { state.showShareSheet = false },
            title = "共享页面",
            doneLabel = "关闭",
            detents = listOf(LiquidKitSheetDetent.Medium),
            initialDetent = LiquidKitSheetDetent.Medium,
            colors = colors,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.04f))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(tab.accentColorArgb)),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(
                        text = shareTitle.take(1).uppercase(),
                        style = TextStyle(color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold),
                    )
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    BasicText(
                        text = shareTitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(color = colors.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                    )
                    BasicText(
                        text = if (isStart) "起始页" else BrowserUrlUtils.displayHost(shareUrl).ifBlank { shareUrl },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(color = colors.secondary, fontSize = 12.sp),
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ShareActionTile(
                        title = "拷贝链接",
                        symbol = LiquidKitSymbol.Clipboard,
                        ink = colors.ink,
                        isDark = isDark,
                        modifier = Modifier.weight(1f),
                    ) {
                        clipboard.setText(AnnotatedString(shareUrl))
                        state.showShareSheet = false
                        state.showBanner("已拷贝链接")
                    }
                    ShareActionTile(
                        title = "加到书签",
                        symbol = LiquidKitSymbol.Bookmark,
                        ink = colors.ink,
                        isDark = isDark,
                        enabled = !isStart,
                        modifier = Modifier.weight(1f),
                    ) {
                        state.addBookmark(shareTitle, shareUrl)
                        state.showShareSheet = false
                        state.showBanner("已添加到书签")
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ShareActionTile(
                        title = "阅读列表",
                        symbol = LiquidKitSymbol.Document,
                        ink = colors.ink,
                        isDark = isDark,
                        enabled = !isStart,
                        iconContent = { SafariReadingGlassesIcon(color = colors.ink, modifier = Modifier.size(21.dp)) },
                        modifier = Modifier.weight(1f),
                    ) {
                        state.addReadingList(shareTitle, shareUrl)
                        state.showShareSheet = false
                        state.showBanner("已添加到阅读列表")
                    }
                    ShareActionTile(
                        title = "个人收藏",
                        symbol = LiquidKitSymbol.StarFilled,
                        ink = colors.ink,
                        isDark = isDark,
                        enabled = !isStart,
                        modifier = Modifier.weight(1f),
                    ) {
                        state.addFavorite(shareTitle, shareUrl)
                        state.showShareSheet = false
                        state.showBanner("已添加到个人收藏")
                    }
                }
                LiquidKitActionButton(
                    label = "通过系统共享…",
                    onClick = {
                        state.showShareSheet = false
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, shareTitle)
                            putExtra(Intent.EXTRA_TEXT, "$shareTitle\n$shareUrl")
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "共享网页"))
                    },
                    style = LiquidKitActionStyle.GlassProminent,
                    tint = accentBlue,
                    leadingSymbol = LiquidKitSymbol.Share,
                    backdrop = backdrop,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    // 3. Customize Page Menu Sheet
    if (state.showCustomizeMenuSheet) {
        LiquidKitSheet(
            onDismissRequest = { state.showCustomizeMenuSheet = false },
            title = "自定义菜单",
            doneLabel = "完成",
            detents = listOf(LiquidKitSheetDetent.Medium, LiquidKitSheetDetent.Large),
            initialDetent = LiquidKitSheetDetent.Large,
            colors = colors,
        ) {
            BasicText(
                text = "启用或排序页面菜单中的功能项",
                style = TextStyle(color = colors.secondary, fontSize = 13.sp),
                modifier = Modifier.padding(bottom = 10.dp),
            )
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.pageMenuItems.forEachIndexed { idx, cfg ->
                    key(cfg.id) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.04f))
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            LiquidKitToggle(
                                selected = { cfg.enabled },
                                onSelect = { state.togglePageMenuItemEnabled(cfg.id) },
                                backdrop = backdrop,
                                tint = Color(0xFF34C759),
                            )
                            BasicText(
                                text = cfg.title,
                                style = TextStyle(color = colors.ink, fontSize = 15.sp, fontWeight = FontWeight.Medium),
                                modifier = Modifier.weight(1f),
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .background(if (idx > 0) Color.Gray.copy(alpha = 0.16f) else Color.Transparent)
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                            enabled = idx > 0,
                                        ) { state.movePageMenuItem(cfg.id, -1) },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    TerraSymbolIcon(
                                        symbol = LiquidKitSymbol.Ascending,
                                        tint = if (idx > 0) colors.ink else colors.secondary.copy(alpha = 0.35f),
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .background(if (idx < state.pageMenuItems.lastIndex) Color.Gray.copy(alpha = 0.16f) else Color.Transparent)
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                            enabled = idx < state.pageMenuItems.lastIndex,
                                        ) { state.movePageMenuItem(cfg.id, +1) },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    TerraSymbolIcon(
                                        symbol = LiquidKitSymbol.Descending,
                                        tint = if (idx < state.pageMenuItems.lastIndex) colors.ink else colors.secondary.copy(alpha = 0.35f),
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // 4. Page Zoom & Appearance Sheet
    if (state.showZoomSheet) {
        val tab = state.activeTab
        LiquidKitSheet(
            onDismissRequest = { state.showZoomSheet = false },
            title = "页面缩放与显示",
            doneLabel = "完成",
            detents = listOf(LiquidKitSheetDetent.Medium),
            initialDetent = LiquidKitSheetDetent.Medium,
            colors = colors,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                LiquidKitStepper(
                    label = "文字缩放",
                    value = tab.pageZoom,
                    onChange = { next ->
                        state.updateTab(tab.id) { it.copy(pageZoom = next) }
                        state.persist()
                    },
                    range = 50..200,
                    tint = accentBlue,
                    colors = colors,
                )

                LiquidKitOpacitySlider(
                    value = state.glassOpacity,
                    onValueChange = { state.glassOpacity = it },
                    onValueChangeFinished = { state.persist() },
                    label = "玻璃不透明度",
                    backdrop = sheetSolidBackdrop,
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    BasicText(
                        text = "外观模式",
                        style = TextStyle(color = colors.secondary, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                    )
                    LiquidKitSegmentedPicker(
                        options = DarkModePreference.entries.map { it.label },
                        selected = state.darkModePreference.label,
                        select = { picked ->
                            state.darkModePreference = DarkModePreference.entries.firstOrNull { it.label == picked } ?: state.darkModePreference
                            state.persist()
                        },
                        colors = colors,
                        backdrop = sheetSolidBackdrop,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    BasicText(
                        text = "默认搜索引擎",
                        style = TextStyle(color = colors.secondary, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                    )
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(state.allSearchEngines, key = { it.id }) { engine ->
                            val selected = state.searchEngine.id == engine.id
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(99.dp))
                                    .background(
                                        if (selected) accentBlue.copy(alpha = 0.16f)
                                        else if (isDark) Color.White.copy(alpha = 0.08f)
                                        else Color.Black.copy(alpha = 0.06f),
                                    )
                                    .border(
                                        0.75.dp,
                                        if (selected) accentBlue.copy(alpha = 0.65f) else Color.Transparent,
                                        RoundedCornerShape(99.dp),
                                    )
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                    ) { state.selectSearchEngine(engine) }
                                    .padding(horizontal = 12.dp, vertical = 7.dp),
                            ) {
                                BasicText(
                                    text = engine.label,
                                    maxLines = 1,
                                    style = TextStyle(
                                        color = if (selected) accentBlue else colors.ink,
                                        fontSize = 13.sp,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                    ),
                                )
                            }
                        }
                        item("zoom_custom_engine") {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(99.dp))
                                    .background(
                                        if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f),
                                    )
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                    ) {
                                        state.showZoomSheet = false
                                        state.showSettingsSheet = true
                                    }
                                    .padding(horizontal = 12.dp, vertical = 7.dp),
                            ) {
                                BasicText(
                                    text = "+ 自定义",
                                    maxLines = 1,
                                    style = TextStyle(
                                        color = accentBlue,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // 5. Privacy Report Detail Sheet
    if (state.showPrivacyReportSheet) {
        val tab = state.activeTab
        LiquidKitSheet(
            onDismissRequest = { state.showPrivacyReportSheet = false },
            title = "隐私报告",
            doneLabel = "完成",
            detents = listOf(LiquidKitSheetDetent.Medium, LiquidKitSheetDetent.Large),
            initialDetent = LiquidKitSheetDetent.Medium,
            colors = colors,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(accentBlue.copy(alpha = 0.12f))
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    SafariShieldIcon(color = accentBlue, modifier = Modifier.size(32.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        BasicText(
                            text = "已阻止 ${state.totalTrackersBlocked} 个追踪器",
                            style = TextStyle(color = colors.ink, fontSize = 17.sp, fontWeight = FontWeight.Bold),
                        )
                        BasicText(
                            text = "智能防跟踪与内容拦截器正在保护当前浏览会话",
                            style = TextStyle(color = colors.secondary, fontSize = 12.sp),
                        )
                    }
                }

                LiquidKitLabeledContent("当前标签页内容拦截器", colors = colors) {
                    LiquidKitToggle(
                        selected = { tab.contentBlockerEnabled },
                        onSelect = { next ->
                            state.updateTab(tab.id) { it.copy(contentBlockerEnabled = next) }
                            state.persist()
                        },
                        backdrop = backdrop,
                        tint = Color(0xFF34C759),
                    )
                }

                BasicText(
                    text = "已拦截追踪域名",
                    style = TextStyle(color = colors.secondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                )

                val sortedDomains = state.blockedDomainCounts.entries.sortedByDescending { it.value }
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    sortedDomains.forEach { (domain, count) ->
                        key(domain) {
                            LiquidKitLabeledContent(
                                label = domain,
                                value = "$count 次",
                                colors = colors,
                            )
                        }
                    }
                }
            }
        }
    }

    // 6. New Tab Group Alert
    if (state.showNewGroupAlert) {
        LiquidKitAlert(
            title = "新建标签页组",
            message = "输入新标签页组名称，当前标签页可随时移入。",
            actions = listOf(
                LiquidKitAlertAction(label = "取消", role = LiquidKitAlertRole.Cancel) {
                    state.showNewGroupAlert = false
                    state.newGroupDraft = ""
                },
                LiquidKitAlertAction(label = "创建", role = LiquidKitAlertRole.Primary) {
                    val created = state.createTabGroup(state.newGroupDraft)
                    state.showNewGroupAlert = false
                    state.newGroupDraft = ""
                    state.selectGroup(created.id)
                    state.showBanner("已创建「${created.name}」")
                },
            ),
            onDismissRequest = {
                state.showNewGroupAlert = false
                state.newGroupDraft = ""
            },
            colors = colors,
            backdrop = modalBackdrop,
            field = {
                LiquidKitAlertTextField(
                    value = state.newGroupDraft,
                    onValueChange = { state.newGroupDraft = it },
                    label = "例如：工作、阅读、旅行",
                    colors = colors,
                    onConfirm = {
                        val created = state.createTabGroup(state.newGroupDraft)
                        state.showNewGroupAlert = false
                        state.newGroupDraft = ""
                        state.selectGroup(created.id)
                    },
                )
            },
        )
    }

    // 7. WebView Long-Press Image / Link Context Menu
    val activeHit = state.activeContextHit
    AnimatedVisibility(
        visible = activeHit != null,
        enter = fadeIn(tween(180)) + scaleIn(tween(220), initialScale = 0.92f),
        exit = fadeOut(tween(150)) + scaleOut(tween(150), targetScale = 0.94f),
    ) {
        val hit = activeHit ?: return@AnimatedVisibility
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.44f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { state.activeContextHit = null }
                .displayCutoutPadding()
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                LiquidKitGlassSurface(
                    backdrop = modalBackdrop,
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        BasicText(
                            text = if (hit.type == ContextHitType.IMAGE) "图片与链接预览" else "链接预览",
                            style = TextStyle(color = accentBlue, fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                        )
                        BasicText(
                            text = BrowserUrlUtils.displayHost(hit.url).ifBlank { hit.url },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = TextStyle(color = colors.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold),
                        )
                        BasicText(
                            text = hit.url,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            style = TextStyle(color = colors.secondary, fontSize = 12.sp),
                        )
                    }
                }

                LiquidKitGlassSurface(
                    backdrop = modalBackdrop,
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        ContextMenuOptionRow("在新标签页中打开", LiquidKitSymbol.SquareOnSquare, colors.ink) {
                            state.activeContextHit = null
                            onOpenInNewTab(hit.url)
                        }
                        ContextMenuOptionRow("在后台打开", LiquidKitSymbol.Add, colors.ink) {
                            state.activeContextHit = null
                            onOpenInBackgroundTab(hit.url)
                            state.showBanner("已在后台标签页打开")
                        }
                        ContextMenuOptionRow("拷贝链接", LiquidKitSymbol.Clipboard, colors.ink) {
                            clipboard.setText(AnnotatedString(hit.url))
                            state.activeContextHit = null
                            state.showBanner("已拷贝链接")
                        }
                        ContextMenuOptionRow("添加到阅读列表", LiquidKitSymbol.Bookmark, colors.ink) {
                            state.addReadingList(hit.title.ifBlank { BrowserUrlUtils.displayHost(hit.url) }, hit.url)
                            state.activeContextHit = null
                            state.showBanner("已添加到阅读列表")
                        }
                        ContextMenuOptionRow("共享…", LiquidKitSymbol.Share, colors.ink, showDivider = false) {
                            state.activeContextHit = null
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, hit.url)
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "共享链接"))
                        }
                    }
                }
            }
        }
    }

    // 8. Safari Settings Sheet
    if (state.showSettingsSheet) {
        SafariSettingsSheet(
            state = state,
            backdrop = backdrop,
            onDismiss = { state.showSettingsSheet = false },
        )
    }

    // 9. Manage Tab Groups Sheet
    if (state.showManageGroupsSheet) {
        LiquidKitSheet(
            onDismissRequest = { state.showManageGroupsSheet = false },
            title = "管理标签页组",
            doneLabel = "完成",
            detents = listOf(LiquidKitSheetDetent.Medium, LiquidKitSheetDetent.Large),
            initialDetent = LiquidKitSheetDetent.Medium,
            colors = colors,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                LiquidKitActionButton(
                    label = "新建标签页组",
                    onClick = {
                        state.showManageGroupsSheet = false
                        state.newGroupDraft = ""
                        state.showNewGroupAlert = true
                    },
                    style = LiquidKitActionStyle.GlassProminent,
                    tint = accentBlue,
                    leadingSymbol = LiquidKitSymbol.NewFolder,
                    backdrop = sheetSolidBackdrop,
                    modifier = Modifier.fillMaxWidth(),
                )

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.tabGroups.forEach { grp ->
                        key(grp.id) {
                            val count = state.tabsInGroup(grp.id).size
                            val isCurrent = grp.id == state.activeGroupId
                            val canDelete = !grp.isPrivate && !grp.isDefault
                            val rowContent: @Composable () -> Unit = {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(
                                            if (isCurrent) accentBlue.copy(alpha = 0.14f)
                                            else if (isDark) Color.White.copy(alpha = 0.08f)
                                            else Color.Black.copy(alpha = 0.045f),
                                        )
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                        ) {
                                            state.selectGroup(grp.id)
                                            state.showManageGroupsSheet = false
                                        }
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color(grp.accentColorArgb)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        if (grp.isPrivate) {
                                            SafariPrivateMaskIcon(color = Color.White, modifier = Modifier.size(18.dp))
                                        } else {
                                            TerraSymbolIcon(symbol = LiquidKitSymbol.Folder, tint = Color.White, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        BasicText(
                                            text = grp.name,
                                            style = TextStyle(color = colors.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                                        )
                                        BasicText(
                                            text = "$count 个标签页",
                                            style = TextStyle(color = colors.secondary, fontSize = 12.sp),
                                        )
                                    }
                                    if (isCurrent) {
                                        TerraSymbolIcon(symbol = LiquidKitSymbol.Check, tint = accentBlue, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }

                            if (canDelete) {
                                LiquidKitSwipeActions(
                                    rowKey = "mgr_${grp.id}",
                                    trailing = listOf(
                                        LiquidKitSwipeAction(
                                            label = "删除",
                                            symbol = LiquidKitSymbol.Trash,
                                            color = Color(0xFFFF3B30),
                                            onClick = { state.deleteTabGroup(grp.id) },
                                        ),
                                    ),
                                ) {
                                    rowContent()
                                }
                            } else {
                                rowContent()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryRowCard(
    title: String,
    subtitle: String,
    accentArgb: Long,
    ink: Color,
    secondary: Color,
    isDark: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.04f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(accentArgb).copy(alpha = 0.88f)),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(
                text = title.take(1).uppercase(),
                style = TextStyle(color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold),
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            BasicText(
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(color = ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
            )
            BasicText(
                text = subtitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(color = secondary, fontSize = 12.sp),
            )
        }
    }
}

@Composable
private fun ShareActionTile(
    title: String,
    symbol: LiquidKitSymbol,
    ink: Color,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    iconContent: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.42f)
            .clip(RoundedCornerShape(18.dp))
            .background(if (isDark) Color.White.copy(alpha = 0.09f) else Color.Black.copy(alpha = 0.05f))
            .border(0.75.dp, if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f), RoundedCornerShape(18.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .padding(vertical = 13.dp, horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (iconContent != null) {
            iconContent()
        } else {
            TerraSymbolIcon(symbol = symbol, tint = ink, modifier = Modifier.size(20.dp))
        }
        BasicText(
            text = title,
            style = TextStyle(color = ink, fontSize = 12.sp, fontWeight = FontWeight.Medium),
        )
    }
}

@Composable
private fun ContextMenuOptionRow(
    title: String,
    symbol: LiquidKitSymbol,
    ink: Color,
    showDivider: Boolean = true,
    onClick: () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                )
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            BasicText(
                text = title,
                style = TextStyle(color = ink, fontSize = 15.sp, fontWeight = FontWeight.Medium),
            )
            TerraSymbolIcon(symbol = symbol, tint = ink, modifier = Modifier.size(19.dp))
        }
        if (showDivider) {
            LiquidKitDivider()
        }
    }
}

@Composable
private fun EmptyLibraryNote(
    title: String,
    subtitle: String,
    symbol: LiquidKitSymbol,
    ink: Color,
    secondary: Color,
    accentBlue: Color,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(accentBlue.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            TerraSymbolIcon(
                symbol = symbol,
                tint = accentBlue,
                modifier = Modifier.size(21.dp),
            )
        }
        BasicText(
            text = title,
            style = TextStyle(
                color = ink,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            ),
        )
        BasicText(
            text = subtitle,
            style = TextStyle(
                color = secondary,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            ),
        )
    }
}
