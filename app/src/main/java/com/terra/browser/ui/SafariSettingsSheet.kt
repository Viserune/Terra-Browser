package com.terra.browser.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.rememberSolidColorBackdrop
import com.terra.browser.model.AutoCloseTabsMode
import com.terra.browser.model.DarkModePreference
import com.terra.browser.model.OpenLinkMode
import com.terra.browser.model.SearchEngine
import com.terra.browser.model.SiteDarkMode
import com.terra.browser.model.TabBarMode
import com.terra.browser.state.TerraBrowserState
import io.liquidkit.LiquidKitAlert
import io.liquidkit.LiquidKitAlertAction
import io.liquidkit.LiquidKitAlertRole
import io.liquidkit.LiquidKitColors
import io.liquidkit.LiquidKitIconButton
import io.liquidkit.LiquidKitListSection
import io.liquidkit.LiquidKitNavigationScaffold
import io.liquidkit.LiquidKitNavigationSheet
import io.liquidkit.LiquidKitNavigationTitleMode
import io.liquidkit.LiquidKitOpacitySlider
import io.liquidkit.LiquidKitSegmentedPicker
import io.liquidkit.LiquidKitSheetCloseButton
import io.liquidkit.LiquidKitSheetDetent
import io.liquidkit.LiquidKitStepper
import io.liquidkit.LiquidKitSwipeBackStyle
import io.liquidkit.LiquidKitSymbol
import io.liquidkit.LocalLiquidKitContentColor
import io.liquidkit.LocalLiquidKitModalBackdrop
import io.liquidkit.LocalLiquidKitSheetContext
import io.liquidkit.liquidKitAppearance
import io.liquidkit.liquidKitColors
import io.liquidkit.rememberLiquidKitNavStackState

private val DarkModeOptionLabels = DarkModePreference.entries.map { it.label }
private val SearchEngineOptionLabels = SearchEngine.entries.map { it.label }
private val OpenLinkOptionLabels = OpenLinkMode.entries.map { it.label }
private val AutoCloseOptionLabels = AutoCloseTabsMode.entries.map { it.label }
private val SiteDarkOptionLabels = SiteDarkMode.entries.map { it.label }

private enum class SettingsRoute(val title: String) {
    ROOT("设置"),
    APPEARANCE_TAB_BAR("外观与标签栏"),
    SEARCH_START("搜索与起始页"),
    TABS_ACTIONS("标签页与操作"),
    PRIVACY_SECURITY("隐私与安全"),
    WEB_READING("网页与阅读"),
    ABOUT("关于"),
}

@Composable
fun SafariSettingsSheet(
    state: TerraBrowserState,
    @Suppress("UNUSED_PARAMETER") backdrop: Backdrop,
    onDismiss: () -> Unit,
) {
    val isDark = liquidKitAppearance().dark
    val colors = liquidKitColors(isDark)
    val sheetBg = if (isDark) Color(0xFF1C1C1E) else Color(0xFFF2F2F7)
    val sheetSolidBackdrop = rememberSolidColorBackdrop(sheetBg)
    val modalBackdrop = LocalLiquidKitModalBackdrop.current
    val accentBlue = Color(0xFF0A84FF)
    var confirmClearData by remember { mutableStateOf(false) }
    val navState = rememberLiquidKitNavStackState(SettingsRoute.ROOT)

    BackHandler(enabled = confirmClearData) {
        confirmClearData = false
    }

    LiquidKitNavigationSheet(
        onDismissRequest = onDismiss,
        navState = navState,
        detents = listOf(LiquidKitSheetDetent.Medium, LiquidKitSheetDetent.Large),
        initialDetent = LiquidKitSheetDetent.Large,
        colors = colors,
        swipeBackStyle = LiquidKitSwipeBackStyle.ClassicParallax,
        showDragIndicator = true,
    ) { route, _ ->
        val listState = rememberLazyListState()
        val sheetContext = LocalLiquidKitSheetContext.current
        val isRoot = route == SettingsRoute.ROOT
        val scaffoldColors = colors.copy(background = sheetBg)

        LiquidKitNavigationScaffold(
            title = route.title,
            titleMode = if (isRoot) LiquidKitNavigationTitleMode.Large else LiquidKitNavigationTitleMode.Inline,
            scrollState = listState,
            colors = scaffoldColors,
            leading = { barBackdrop ->
                AnimatedContent(
                    targetState = isRoot,
                    transitionSpec = { fadeIn(tween(140)) togetherWith fadeOut(tween(110)) },
                    label = "SettingsNavLeading",
                ) { rootPage ->
                    if (rootPage) {
                        LiquidKitSheetCloseButton(
                            onClick = { sheetContext?.dismiss?.invoke() ?: onDismiss() },
                            backdrop = barBackdrop,
                        )
                    } else {
                        LiquidKitIconButton(
                            onClick = { navState.pop() },
                            backdrop = barBackdrop,
                            size = 40.dp,
                        ) {
                            TerraSymbolIcon(
                                symbol = LiquidKitSymbol.Back,
                                tint = LocalLiquidKitContentColor.current,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            },
        ) { contentPadding ->
            LazyColumn(
                state = listState,
                contentPadding = contentPadding,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 18.dp),
            ) {
                item(key = route.name) {
                    when (route) {
                        SettingsRoute.ROOT -> SettingsRootContent(
                            state = state,
                            accentBlue = accentBlue,
                            onNavigate = { navState.push(it) },
                        )
                        SettingsRoute.APPEARANCE_TAB_BAR -> AppearanceAndTabBarPage(
                            state = state,
                            backdrop = sheetSolidBackdrop,
                            colors = colors,
                            accentBlue = accentBlue,
                        )
                        SettingsRoute.SEARCH_START -> SearchAndStartPage(
                            state = state,
                            backdrop = sheetSolidBackdrop,
                            colors = colors,
                            accentBlue = accentBlue,
                        )
                        SettingsRoute.TABS_ACTIONS -> TabsAndActionsPage(
                            state = state,
                            backdrop = sheetSolidBackdrop,
                            colors = colors,
                            accentBlue = accentBlue,
                            onOpenManageGroups = {
                                sheetContext?.dismiss?.invoke() ?: onDismiss()
                                state.showManageGroupsSheet = true
                            },
                            onOpenCustomizeMenu = {
                                sheetContext?.dismiss?.invoke() ?: onDismiss()
                                state.showCustomizeMenuSheet = true
                            },
                        )
                        SettingsRoute.PRIVACY_SECURITY -> PrivacyAndSecurityPage(
                            state = state,
                            accentBlue = accentBlue,
                            onOpenPrivacyReport = {
                                sheetContext?.dismiss?.invoke() ?: onDismiss()
                                state.showPrivacyReportSheet = true
                            },
                            onRequestClearData = { confirmClearData = true },
                        )
                        SettingsRoute.WEB_READING -> WebAndReadingPage(
                            state = state,
                            backdrop = sheetSolidBackdrop,
                            colors = colors,
                            accentBlue = accentBlue,
                        )
                        SettingsRoute.ABOUT -> AboutPage(state = state, accentBlue = accentBlue)
                    }
                }
            }
        }
    }

    if (confirmClearData) {
        LiquidKitAlert(
            title = "清除浏览记录和网站资料？",
            message = "这将清除你的历史记录、最近搜索及追踪器拦截统计。",
            actions = listOf(
                LiquidKitAlertAction("取消", LiquidKitAlertRole.Cancel) { confirmClearData = false },
                LiquidKitAlertAction("清除", LiquidKitAlertRole.Destructive) {
                    confirmClearData = false
                    state.clearAllBrowsingData()
                },
            ),
            onDismissRequest = { confirmClearData = false },
            colors = colors,
            backdrop = modalBackdrop,
        )
    }
}

@Composable
private fun SettingsRootContent(
    state: TerraBrowserState,
    accentBlue: Color,
    onNavigate: (SettingsRoute) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        LiquidKitListSection {
            navigationRow(
                title = "外观与标签栏",
                leading = LiquidKitSymbol.Palette,
                tint = accentBlue,
                trailingText = state.tabBarMode.label,
                onClick = { onNavigate(SettingsRoute.APPEARANCE_TAB_BAR) },
            )
            navigationRow(
                title = "搜索与起始页",
                leading = LiquidKitSymbol.Search,
                tint = accentBlue,
                trailingText = state.searchEngine.label,
                onClick = { onNavigate(SettingsRoute.SEARCH_START) },
            )
            navigationRow(
                title = "标签页与操作",
                leading = LiquidKitSymbol.SquareOnSquare,
                tint = accentBlue,
                trailingText = state.openLinkMode.label,
                onClick = { onNavigate(SettingsRoute.TABS_ACTIONS) },
            )
            navigationRow(
                title = "隐私与安全",
                leading = LiquidKitSymbol.Filter,
                tint = accentBlue,
                trailingText = "${state.totalTrackersBlocked}",
                onClick = { onNavigate(SettingsRoute.PRIVACY_SECURITY) },
            )
            navigationRow(
                title = "网页与阅读",
                leading = LiquidKitSymbol.Document,
                tint = accentBlue,
                trailingText = "${state.activeTab.pageZoom}%",
                onClick = { onNavigate(SettingsRoute.WEB_READING) },
            )
        }

        LiquidKitListSection {
            navigationRow(
                title = "关于",
                leading = LiquidKitSymbol.InfoCircle,
                tint = accentBlue,
                trailingText = "1.0.0",
                onClick = { onNavigate(SettingsRoute.ABOUT) },
            )
        }
    }
}

@Composable
private fun AppearanceAndTabBarPage(
    state: TerraBrowserState,
    backdrop: Backdrop,
    colors: LiquidKitColors,
    accentBlue: Color,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        LiquidKitListSection(title = "标签页栏布局") {
            row(inset = true, separatorStart = 16.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    TabBarMode.entries.forEach { mode ->
                        TabBarModeWireframeOption(
                            mode = mode,
                            selected = state.tabBarMode == mode,
                            ink = colors.ink,
                            secondary = colors.secondary,
                            accentBlue = accentBlue,
                            modifier = Modifier.weight(1f),
                        ) {
                            state.tabBarMode = mode
                            state.isToolbarMinimized = false
                            state.isToolbarHidden = false
                            state.persist()
                        }
                    }
                }
            }
            toggleRow(
                title = "延伸网站颜色",
                checked = state.extendWebsiteTint,
                onCheckedChange = {
                    state.extendWebsiteTint = it
                    state.persist()
                },
                tint = Color(0xFF34C759),
            )
            toggleRow(
                title = "平板模式显示标签页栏",
                checked = state.showTabletTabBar,
                onCheckedChange = {
                    state.showTabletTabBar = it
                    state.persist()
                },
                tint = Color(0xFF34C759),
            )
        }

        LiquidKitListSection(title = "主题与液态玻璃") {
            row(inset = true, separatorStart = 16.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BasicText(
                        text = "外观模式",
                        style = TextStyle(color = colors.secondary, fontSize = 13.sp, fontWeight = FontWeight.Medium),
                    )
                    LiquidKitSegmentedPicker(
                        options = DarkModeOptionLabels,
                        selected = state.darkModePreference.label,
                        select = { label ->
                            state.darkModePreference = DarkModePreference.entries.firstOrNull { it.label == label } ?: state.darkModePreference
                            state.persist()
                        },
                        colors = colors,
                        backdrop = backdrop,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            row(inset = true, separatorStart = 16.dp) {
                LiquidKitOpacitySlider(
                    value = state.glassOpacity,
                    onValueChange = { state.glassOpacity = it },
                    onValueChangeFinished = { state.persist() },
                    label = "玻璃不透明度",
                    backdrop = backdrop,
                )
            }
        }
    }
}

@Composable
private fun SearchAndStartPage(
    state: TerraBrowserState,
    backdrop: Backdrop,
    colors: LiquidKitColors,
    accentBlue: Color,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        LiquidKitListSection(title = "搜索引擎") {
            row(inset = true, separatorStart = 16.dp) {
                LiquidKitSegmentedPicker(
                    options = SearchEngineOptionLabels,
                    selected = state.searchEngine.label,
                    select = { label ->
                        state.searchEngine = SearchEngine.entries.firstOrNull { it.label == label } ?: state.searchEngine
                        state.persist()
                    },
                    colors = colors,
                    backdrop = backdrop,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            toggleRow(
                title = "显示最近的搜索项目",
                checked = state.showRecentSearches,
                onCheckedChange = {
                    state.showRecentSearches = it
                    state.persist()
                },
                tint = Color(0xFF34C759),
            )
        }

        LiquidKitListSection(title = "起始页") {
            toggleRow(
                title = "展开全部个人收藏",
                checked = state.showAllFavorites,
                onCheckedChange = { state.showAllFavorites = it },
                tint = Color(0xFF34C759),
            )
            actionRow(
                title = "清除最近搜索 (${state.recentSearches.size})",
                destructive = true,
                enabled = state.recentSearches.isNotEmpty(),
                onClick = {
                    state.clearRecentSearches()
                    state.showBanner("已清除最近搜索")
                },
            )
        }
    }
}

@Composable
private fun TabsAndActionsPage(
    state: TerraBrowserState,
    backdrop: Backdrop,
    colors: LiquidKitColors,
    accentBlue: Color,
    onOpenManageGroups: () -> Unit,
    onOpenCustomizeMenu: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        LiquidKitListSection(title = "标签页行为") {
            row(inset = true, separatorStart = 16.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BasicText(
                        text = "打开链接",
                        style = TextStyle(color = colors.secondary, fontSize = 13.sp, fontWeight = FontWeight.Medium),
                    )
                    LiquidKitSegmentedPicker(
                        options = OpenLinkOptionLabels,
                        selected = state.openLinkMode.label,
                        select = { label ->
                            state.openLinkMode = OpenLinkMode.entries.firstOrNull { it.label == label } ?: state.openLinkMode
                            state.persist()
                        },
                        colors = colors,
                        backdrop = backdrop,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            row(inset = true, separatorStart = 16.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BasicText(
                        text = "自动关闭标签页",
                        style = TextStyle(color = colors.secondary, fontSize = 13.sp, fontWeight = FontWeight.Medium),
                    )
                    LiquidKitSegmentedPicker(
                        options = AutoCloseOptionLabels,
                        selected = state.autoCloseTabsMode.label,
                        select = { label ->
                            state.autoCloseTabsMode = AutoCloseTabsMode.entries.firstOrNull { it.label == label } ?: state.autoCloseTabsMode
                            state.persist()
                        },
                        colors = colors,
                        backdrop = backdrop,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        LiquidKitListSection(title = "快捷管理") {
            navigationRow(
                title = "管理标签页组",
                leading = LiquidKitSymbol.Folder,
                tint = accentBlue,
                trailingText = "${state.tabGroups.size} 组",
                onClick = onOpenManageGroups,
            )
            navigationRow(
                title = "自定义页面菜单",
                leading = LiquidKitSymbol.SliderHorizontal,
                tint = accentBlue,
                onClick = onOpenCustomizeMenu,
            )
        }
    }
}

@Composable
private fun PrivacyAndSecurityPage(
    state: TerraBrowserState,
    accentBlue: Color,
    onOpenPrivacyReport: () -> Unit,
    onRequestClearData: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        LiquidKitListSection(title = "防护开关") {
            toggleRow(
                title = "阻挡弹出式窗口",
                checked = state.blockPopups,
                onCheckedChange = {
                    state.blockPopups = it
                    state.persist()
                },
                tint = Color(0xFF34C759),
            )
            toggleRow(
                title = "防止跨网站追踪",
                checked = state.preventCrossSiteTracking,
                onCheckedChange = {
                    state.preventCrossSiteTracking = it
                    state.persist()
                },
                tint = Color(0xFF34C759),
            )
            toggleRow(
                title = "不安全连接警告",
                checked = state.warnInsecureConnection,
                onCheckedChange = {
                    state.warnInsecureConnection = it
                    state.persist()
                },
                tint = Color(0xFF34C759),
            )
        }

        LiquidKitListSection(title = "数据与报告") {
            navigationRow(
                title = "隐私报告详情",
                leading = LiquidKitSymbol.Filter,
                tint = accentBlue,
                trailingText = "${state.totalTrackersBlocked} 次拦截",
                onClick = onOpenPrivacyReport,
            )
            actionRow(
                title = "清除浏览记录和网站资料",
                leading = LiquidKitSymbol.Trash,
                destructive = true,
                onClick = onRequestClearData,
            )
        }
    }
}

@Composable
private fun WebAndReadingPage(
    state: TerraBrowserState,
    backdrop: Backdrop,
    colors: LiquidKitColors,
    accentBlue: Color,
) {
    val tab = state.activeTab
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        LiquidKitListSection(title = "当前标签页显示") {
            row(inset = true, separatorStart = 16.dp) {
                LiquidKitStepper(
                    label = "当前页面缩放",
                    value = tab.pageZoom,
                    onChange = { next ->
                        state.updateTab(tab.id) { it.copy(pageZoom = next) }
                        state.persist()
                    },
                    range = 50..200,
                    tint = accentBlue,
                    colors = colors,
                )
            }
            row(inset = true, separatorStart = 16.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BasicText(
                        text = "网页深色适配",
                        style = TextStyle(color = colors.secondary, fontSize = 13.sp, fontWeight = FontWeight.Medium),
                    )
                    LiquidKitSegmentedPicker(
                        options = SiteDarkOptionLabels,
                        selected = tab.adaptiveDarkMode.label,
                        select = { label ->
                            val picked = SiteDarkMode.entries.firstOrNull { it.label == label } ?: tab.adaptiveDarkMode
                            state.updateTab(tab.id) { it.copy(adaptiveDarkMode = picked) }
                            state.persist()
                        },
                        colors = colors,
                        backdrop = backdrop,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            toggleRow(
                title = "当前标签页内容拦截器",
                checked = tab.contentBlockerEnabled,
                onCheckedChange = { next ->
                    state.updateTab(tab.id) { it.copy(contentBlockerEnabled = next) }
                    state.persist()
                },
                tint = Color(0xFF34C759),
            )
            toggleRow(
                title = "请求桌面网站",
                checked = tab.requestDesktopSite,
                onCheckedChange = { next ->
                    state.updateTab(tab.id) { it.copy(requestDesktopSite = next) }
                    state.persist()
                },
                tint = Color(0xFF34C759),
            )
        }
    }
}

@Composable
private fun AboutPage(
    state: TerraBrowserState,
    accentBlue: Color,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        LiquidKitListSection(title = "应用信息") {
            valueRow(
                title = "浏览器名称",
                value = "Terra Browser",
                leading = LiquidKitSymbol.Globe,
                tint = accentBlue,
            )
            valueRow(
                title = "版本号",
                value = "1.0.0",
                leading = LiquidKitSymbol.Sparkles,
                tint = accentBlue,
            )
            valueRow(
                title = "界面引擎",
                value = "LiquidKit 2.0",
                leading = LiquidKitSymbol.Code,
                tint = accentBlue,
            )
        }

        LiquidKitListSection(title = "会话概览") {
            valueRow(
                title = "打开的标签页",
                value = "${state.tabs.size} 个",
            )
            valueRow(
                title = "标签页组",
                value = "${state.tabGroups.size} 组",
            )
            valueRow(
                title = "已保存书签 / 阅读列表",
                value = "${state.bookmarks.size} / ${state.readingList.size}",
            )
        }
    }
}

@Composable
private fun TabBarModeWireframeOption(
    mode: TabBarMode,
    selected: Boolean,
    ink: Color,
    secondary: Color,
    accentBlue: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val frameColor = if (selected) accentBlue else secondary.copy(alpha = 0.55f)
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 6.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Canvas(modifier = Modifier.size(width = 52.dp, height = 88.dp)) {
            val w = size.width
            val h = size.height
            drawRoundRect(
                color = frameColor,
                topLeft = Offset(2f, 2f),
                size = Size(w - 4f, h - 4f),
                cornerRadius = CornerRadius(14f, 14f),
                style = Stroke(width = if (selected) 3.4f else 2.2f),
            )
            drawRoundRect(
                color = frameColor.copy(alpha = 0.7f),
                topLeft = Offset(w * 0.34f, 9f),
                size = Size(w * 0.32f, 5f),
                cornerRadius = CornerRadius(3f, 3f),
            )

            when (mode) {
                TabBarMode.COMPACT -> {
                    drawCircle(color = frameColor, radius = 4f, center = Offset(w * 0.20f, h - 15f))
                    drawRoundRect(
                        color = frameColor,
                        topLeft = Offset(w * 0.30f, h - 20f),
                        size = Size(w * 0.40f, 10f),
                        cornerRadius = CornerRadius(5f, 5f),
                    )
                    drawCircle(color = frameColor, radius = 4f, center = Offset(w * 0.80f, h - 15f))
                }
                TabBarMode.BOTTOM -> {
                    drawRoundRect(
                        color = frameColor,
                        topLeft = Offset(w * 0.14f, h - 28f),
                        size = Size(w * 0.72f, 20f),
                        cornerRadius = CornerRadius(7f, 7f),
                    )
                }
                TabBarMode.TOP -> {
                    drawRoundRect(
                        color = frameColor,
                        topLeft = Offset(w * 0.14f, 20f),
                        size = Size(w * 0.72f, 9f),
                        cornerRadius = CornerRadius(4.5f, 4.5f),
                    )
                    drawRoundRect(
                        color = frameColor.copy(alpha = 0.85f),
                        topLeft = Offset(w * 0.14f, h - 19f),
                        size = Size(w * 0.72f, 9f),
                        cornerRadius = CornerRadius(4.5f, 4.5f),
                    )
                }
            }
        }

        BasicText(
            text = mode.label,
            style = TextStyle(
                color = if (selected) ink else secondary,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            ),
        )

        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(if (selected) accentBlue else Color.Transparent)
                .border(
                    width = 1.4.dp,
                    color = if (selected) accentBlue else secondary.copy(alpha = 0.45f),
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                TerraSymbolIcon(
                    symbol = LiquidKitSymbol.Check,
                    tint = Color.White,
                    modifier = Modifier.size(12.dp),
                )
            }
        }
    }
}
