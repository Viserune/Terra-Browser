package app.terra.browser

import android.app.DownloadManager
import android.view.KeyEvent
import android.text.format.Formatter
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.platform.LocalView
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.layout
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.viewinterop.AndroidView
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import io.liquidkit.*
import kotlin.math.abs
import kotlin.math.roundToInt

private val Ink = compositionLocalOf { Color.Black }
private val Muted = compositionLocalOf { Color.DarkGray }
private val Card = compositionLocalOf { Color.White }
private val Ground = compositionLocalOf { Color.White }
private val Accent = compositionLocalOf { Color(0xff245e4a) }
private typealias Icon = LiquidKitSymbol
private fun host(url: String) = Uri.parse(url).host.orEmpty()

@Composable private fun Label(s: String, size: Int = 16, color: Color = Ink.current, bold: Boolean = false, lines: Int = 1, serif: Boolean = false) {
    BasicText(s, style = TextStyle(color = color, fontSize = size.sp, fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
        fontFamily = if (serif) FontFamily.Serif else FontFamily.SansSerif), maxLines = lines, overflow = TextOverflow.Ellipsis)
}
@Composable private fun Tool(icon: Icon, label: String, enabled: Boolean = true,
                             hold: ((Int, Int) -> Unit)? = null, rotation: Float = 0f, size: Dp = 48.dp, click: () -> Unit) {
    val interaction = if (hold == null) Modifier.clickable(enabled = enabled, role = Role.Button, onClick = click)
    else {
        var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
        Modifier.onGloballyPositioned { coordinates = it }.combinedClickable(
            enabled = enabled, role = Role.Button, onLongClickLabel = "显示菜单",
            onLongClick = {
                coordinates?.takeIf { it.isAttached }?.let {
                    val anchor = it.localToScreen(Offset(it.size.width / 2f, 0f))
                    hold(anchor.x.toInt(), anchor.y.toInt())
                }
            }, onClick = click
        )
    }
    Box(Modifier.size(size).clip(RoundedCornerShape(24.dp)).then(interaction)
        .semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        LiquidKitIcon(icon, if (enabled) Ink.current else Muted.current.copy(alpha = .45f),
            if (rotation == 0f) Modifier else Modifier.graphicsLayer { rotationZ = rotation })
    }
}
@Composable private fun Action(icon: Icon, name: String, subtitle: String = "", click: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = click).heightIn(min = 54.dp).padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        LiquidKitIcon(icon, Accent.current)
        Column(Modifier.weight(1f)) {
            Label(name)
            if (subtitle.isNotBlank()) Label(subtitle, 12, Muted.current)
        }
    }
}

@Composable fun BrowserScreen(a: MainActivity) {
    val p = a.page
    val phoneLibrary = a.sheet in librarySections &&
        androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 650
    DisposableEffect(a, p.secret) {
        if (android.os.Build.VERSION.SDK_INT >= 33) a.setRecentsScreenshotEnabled(!p.secret)
        else if (p.secret) a.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        else a.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { }
    }
    val dark = isSystemInDarkTheme() || p.secret
    val ground = if (dark) Color(0xff1c1c1e) else Color(0xfff2f2f7)
    CompositionLocalProvider(Ground provides ground, Ink provides if (dark) Color(0xfff2f4ef) else Color(0xff1c1c1e),
        Card provides if (dark) Color(0xff2c2c2e) else Color.White, Muted provides if (dark) Color(0xffb6c2ba) else Color(0xff58665c),
        Accent provides if (dark) Color(0xff66adff) else Color(0xff0066cc)) {
        LiquidKitRoot(backgroundColor = ground, dark = dark) {
            val capture = remember { WebGlassCapture() }
            var tabletAddress by remember { mutableStateOf<Rect?>(null) }
            val tabletEditorBounds = remember { mutableStateOf<Rect?>(null) }
            var tabletPanelVisible by remember { mutableStateOf(false) }
            val editingTabletAddress by remember { derivedStateOf { tabletEditorBounds.value != null } }
            val measureAddress = Modifier.onGloballyPositioned { layout ->
                val origin = layout.localToScreen(Offset.Zero)
                val end = layout.localToScreen(Offset(layout.size.width.toFloat(),layout.size.height.toFloat()))
                if (origin.x.isFinite() && end.y.isFinite()) tabletAddress = Rect(origin,end)
            }
            val refresh = remember { PullRefresh() }
            val opening = a.openingPreview?.takeIf { it.page === p && it.url == p.url }
            LaunchedEffect(a.openingPreview) {
                val shown = a.openingPreview ?: return@LaunchedEffect
                kotlinx.coroutines.delay(2500)
                if (a.openingPreview === shown) a.openingPreview = null
            }
            val scene = if (p.url.isBlank() || opening != null) rememberLiquidKitBackdrop()
                else rememberLayerBackdrop { with(capture) { drawSample(includePage=true) } }
            val libraryPageScene = rememberLiquidKitBackdrop()
            val libraryScene = rememberLiquidKitBackdrop()
            val editorVisibility = remember { MutableTransitionState(false) }
            SideEffect { editorVisibility.targetState = a.bookmarkDraft != null && a.sheet == "书签" }
            val editorVisible = a.bookmarkDraft != null || editorVisibility.currentState || !editorVisibility.isIdle
            val libraryOverlay = a.sheet in librarySections && (editorVisible || a.contextMenu != null)
            val overlayScene = if (libraryOverlay) rememberCombinedLiquidKitBackdrop(libraryPageScene, libraryScene) else scene
            CompositionLocalProvider(LocalLiquidKitBackdrop provides scene) {
                BoxWithConstraints(Modifier.fillMaxSize().liquidKitBackdrop(libraryPageScene, enabled = libraryOverlay)
                    .safeDrawingPadding().imePadding().clipToBounds()) {
                    val tablet = maxWidth >= 650.dp
                    val canFold = p.url.isNotBlank() && p.error.isEmpty() && a.sheet.isEmpty() &&
                        a.prompt == null && a.bookmarkDraft == null && !a.videoFullscreen
                    val compact by remember(p,canFold) { derivedStateOf { canFold && p.toolbarFold >= 1f } }
                    val compactClearance = with(LocalDensity.current) { (if (tablet) 44.dp else 52.dp).roundToPx() }
                    var toolbarHeight by remember { mutableIntStateOf(0) }
                    var dockHeight by remember { mutableIntStateOf(0) }
                    Box(Modifier.fillMaxSize().background(ground)) {
                        if (p.url.isNotBlank() && opening == null) WebGlassScene(capture, Modifier.fillMaxSize().liquidKitBackdrop(scene))
                        if (p.url.isBlank()) Box(Modifier.fillMaxSize().liquidKitBackdrop(scene).padding(
                            top = if (tablet) 115.dp else 0.dp, bottom = if (tablet) 0.dp else 116.dp
                        )) { Home(a) }
                        else if (!a.webReady) Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) {
                            Label("正在准备浏览器…", 15, Muted.current)
                        } else AndroidView(factory = { TabViewport(it,capture,refresh) }, modifier = Modifier.fillMaxSize(),
                            onRelease = { it.release() }, update = {
                                it.topClearance = if (!tablet) 0 else if (compact) compactClearance else toolbarHeight
                                it.bottomClearance = if (tablet) 0 else if (compact) compactClearance else dockHeight
                                it.display(a.browser(p))
                                // LiquidKitSheet samples the modal host directly, including the live WebView.
                                it.fullGlassScene = editingTabletAddress || tabletPanelVisible || a.contextMenu != null ||
                                    (a.sheet.isNotEmpty() && !phoneLibrary && a.sheet != "阅读器") || a.prompt != null
                                it.readingToolbar.bind(p.takeIf { canFold })
                                refresh.bind(p.web.takeIf { canFold && opening == null && a.contextMenu == null })
                                it.historyEdges(p.back,p.forward)
                            })
                        if (opening != null) Image(opening.image, null, Modifier.fillMaxSize().liquidKitBackdrop(scene),
                            alignment = Alignment.TopCenter, contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                        if (p.error.isNotEmpty()) Column(Modifier.fillMaxSize().background(ground).padding(24.dp),
                            verticalArrangement = Arrangement.Center) {
                            Label("网页载入失败", 26, bold = true)
                            Label(p.error, 14, Muted.current, lines = 3)
                            Action(Icon.Reload, "重新载入") { p.error = ""; p.web?.reload() }
                        }
                    }
                    RefreshIndicator(refresh,if (tablet) toolbarHeight else 0,Modifier.align(Alignment.TopCenter))
                    val alignment = if (tablet) Alignment.TopCenter else Alignment.BottomCenter
                    fun fold() = if (canFold) p.toolbarFold else 0f
                    val showPill by remember(p,canFold) { derivedStateOf { fold() > .05f } }
                    val zoomOpen = a.sheet == "页面缩放"
                    val finding = a.sheet == "查找"
                    if (!zoomOpen && !finding && !tablet) {
                        Column(Modifier.align(alignment).onSizeChanged {
                            if (fold() == 0f) dockHeight = it.height
                        }.padding(bottom=4.dp)) {
                            Dock(a,::fold)
                        }
                    } else if (!zoomOpen && !finding && !compact) {
                        Column(Modifier.align(alignment).graphicsLayer {
                            val t = fold()
                            alpha = (1f-1.6f*t).coerceAtLeast(0f)
                            translationY = -size.height*.45f*t
                            scaleX = 1f-.18f*t
                        }.onSizeChanged { toolbarHeight = it.height }) {
                            TabletToolbar(a, editingTabletAddress, measureAddress, tabletEditorBounds)
                        }
                    }
                    AnimatedVisibility(visible=finding,modifier=Modifier.align(alignment),
                        enter=fadeIn(tween(150)) + slideInVertically(tween(180)) { if (tablet) -it/2 else it/2 },
                        exit=fadeOut(tween(100)) + slideOutVertically(tween(125)) { if (tablet) -it/3 else it/3 }) {
                        key(p.id,a.findSerial) { FindBar(a, Modifier.onSizeChanged {
                            if (finding) { if (tablet) toolbarHeight = it.height else dockHeight = it.height }
                        }) }
                    }
                    AnimatedVisibility(visible=zoomOpen,modifier=Modifier.align(alignment),
                        enter=fadeIn(tween(140)) + slideInVertically(tween(160)) { if (tablet) -it/2 else it/2 },
                        exit=fadeOut(tween(100))) { ZoomToolbar(a) }
                    if (!zoomOpen && tablet && showPill) {
                        val domain = addressLabel(p)
                        val restore = {
                            p.compactTools = false
                            (p.web?.parent as? TabViewport)?.readingToolbar?.expand()
                            Unit
                        }
                        Box(Modifier.align(alignment).height(44.dp).widthIn(max=280.dp)
                            .graphicsLayer { alpha = ((fold()-.36f)/.64f).coerceIn(0f,1f); scaleX = .88f+.12f*fold() }
                            .then(if (compact) addressGesture(a,p,restore) else Modifier)
                            .semantics { contentDescription = "显示工具栏" },contentAlignment=Alignment.Center) {
                            LiquidKitGlassSurface(if (compact) measureAddress else Modifier) {
                                Box(Modifier.heightIn(min=28.dp).padding(horizontal=16.dp),contentAlignment=Alignment.Center) {
                                    Label(domain,13,bold=true)
                                }
                            }
                        }
                    }
                }
                a.contextMenu?.let { menu -> key(menu) {
                    CompositionLocalProvider(LocalLiquidKitBackdrop provides overlayScene) {
                        SafariContextMenu(a, menu, dark, capture = capture.takeIf {
                            !libraryOverlay && a.sheet.isEmpty() && p.url.isNotBlank() && opening == null
                        })
                    }
                } }
                val recordLibrary = Modifier.liquidKitBackdrop(libraryScene, enabled = libraryOverlay)
                if (a.sheet == "下载项目") DownloadsPanel(a)
                else if (phoneLibrary) PhoneLibrarySheet(a, a.sheet, recordLibrary)
                else BrowserPanelWindow(a, recordLibrary, capture.takeIf { p.url.isNotBlank() && opening == null },
                    tabletAddress, tabletEditorBounds) { tabletPanelVisible = it }
                BookmarkEditor(a, overlayScene, editorVisibility)
                a.prompt?.let { prompt ->
                    Dialog(onDismissRequest = { a.answer(prompt, false) }, properties = DialogProperties(
                        usePlatformDefaultWidth = false, decorFitsSystemWindows = false
                    )) {
                        LiquidKitRoot(backgroundColor = Color.Transparent, dark = dark, edgeToEdgeSystemBars = false) {
                            LiquidKitAlert(title = prompt.title, message = prompt.message, backdrop = scene,
                                onDismissRequest = { a.answer(prompt, false) }, actions = listOf(
                                    LiquidKitAlertAction(prompt.cancel, LiquidKitAlertRole.Cancel) { a.answer(prompt, false) },
                                    LiquidKitAlertAction(prompt.accept, if (prompt.destructive) LiquidKitAlertRole.Destructive
                                        else LiquidKitAlertRole.Primary) { a.answer(prompt, true) }
                                ))
                        }
                    }
                }
            }
        }
    }
    BackHandler(enabled = a.bookmarkDraft == null && !phoneLibrary && a.sheet != "下载项目" && (a.sheet.isNotEmpty() || p.back || a.tabs.size > 1 || p.url.isNotEmpty())) {
        when {
            a.sheet.isNotEmpty() -> a.sheet = ""
            p.back -> p.web?.goBack()
            else -> a.close(p)
        }
    }
}

@Composable private fun RefreshIndicator(refresh: PullRefresh, top: Int, modifier: Modifier) {
    val distance = refresh.distance
    if (distance <= 1f) return
    val density = LocalDensity.current
    val fraction = (distance / with(density) { 60.dp.toPx() }).coerceIn(0f,1f)
    val spin = if (refresh.loading) {
        val transition = androidx.compose.animation.core.rememberInfiniteTransition(label="刷新")
        val angle by transition.animateFloat(0f,360f,
            androidx.compose.animation.core.infiniteRepeatable(tween(850,easing=androidx.compose.animation.core.LinearEasing)),
            label="刷新指示")
        angle
    } else 260f * fraction
    val size = 30.dp
    val description = when { refresh.loading -> "正在刷新"; refresh.armed -> "松开刷新"; else -> "下拉刷新" }
    LiquidKitGlassSurface(modifier.offset {
        IntOffset(0,top + ((distance-size.toPx())/2f).toInt().coerceAtLeast(0))
    }.graphicsLayer {
        alpha = fraction.coerceAtMost(1f)
        scaleX = .65f + .35f*fraction
        scaleY = scaleX
    }.semantics { contentDescription=description }) {
        Box(Modifier.size(size),contentAlignment=Alignment.Center) {
            LiquidKitIcon(Icon.Reload,Ink.current,Modifier.size(17.dp).graphicsLayer { rotationZ=spin })
        }
    }
}

private fun tabletAddressWidth(viewport: Dp) = (viewport * .44f).coerceAtMost(560.dp)

@Composable private fun SidebarButton(label: String, onClick: () -> Unit) {
    LiquidKitGlassSurface(Modifier.size(44.dp).semantics { contentDescription = label },
        interactive=true, onClick=onClick) {
        Box(Modifier.fillMaxSize(), contentAlignment=Alignment.Center) { LiquidKitIcon(Icon.Sidebar, Ink.current) }
    }
}

@Composable private fun TabletToolbar(a: MainActivity, editingAddress: Boolean, measureAddress: Modifier,
    editorBounds: State<Rect?>) {
    val p = a.page
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp)) {
        Row(Modifier.align(Alignment.CenterStart), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SidebarButton("显示侧栏") { a.sheet = a.sidebarLocation }
            LiquidKitGlassSurface {
                Row {
                    Tool(Icon.Back, "后退", p.back, size = 44.dp, hold = { x,y -> a.showNavigationHistory(p,true,x,y) }) { p.web?.goBack() }
                    if (p.forward) Tool(Icon.Forward,"前进", size = 44.dp, hold = { x,y -> a.showNavigationHistory(p,false,x,y) }) { p.web?.goForward() }
                }
            }
        }
        val addressWidth = tabletAddressWidth(maxWidth + 20.dp)
        if (!editingAddress) LiquidKitGlassSurface(Modifier.align(Alignment.Center).width(addressWidth).then(measureAddress)) {
            AddressRow(a, wide = true)
        }
        LiquidKitGlassSurface(Modifier.align(Alignment.CenterEnd)) {
            var origin by remember { mutableStateOf(Offset.Zero) }
            Row(Modifier.onGloballyPositioned { origin = it.localToScreen(Offset.Zero) }.drawWithContent {
                val panel = editorBounds.value
                if (panel == null) drawContent()
                else clipRect(panel.left-origin.x, panel.top-origin.y, panel.right-origin.x, panel.bottom-origin.y,
                    androidx.compose.ui.graphics.ClipOp.Difference) { this@drawWithContent.drawContent() }
            }) {
                Tool(Icon.Share,"共享",p.url.isNotBlank(), size = 44.dp) { a.share(p.url) }
                Tool(Icon.Add,"新建标签页", size = 44.dp) { a.add(p.secret) }
                Tool(Icon.SquareOnSquare,"标签页，${a.activeTabs.size} 个", size = 44.dp, hold = { x,y -> a.showTabActions(p,x,y) }) { a.sheet = "标签页" }
            }
        }
    }
    val group = a.activeTabs
    if (group.size < 2) return
    val list = rememberLazyListState()
    val tabScope = rememberCoroutineScope()
    LaunchedEffect(p.id) {
        if (list.layoutInfo.visibleItemsInfo.none { it.index == group.indexOf(p) }) list.animateScrollToItem(group.indexOf(p))
    }
    LiquidKitGlassSurface(Modifier.fillMaxWidth().padding(horizontal=10.dp,vertical=3.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(2.dp)) {
            val regularCount = group.count { !it.pinned }.coerceAtLeast(1)
            val tabWidth = ((maxWidth - 50.dp * group.count { it.pinned }) / regularCount).coerceAtLeast(138.dp)
            LazyRow(state=list) {
                items(group,key={ it.id }) { tab ->
                    val selected = tab === p
                    val color by animateColorAsState(if (selected) Card.current.copy(alpha=.44f) else Color.Transparent)
                    val shape = RoundedCornerShape(20.dp)
                    val rim by animateColorAsState(if (selected) Ink.current.copy(alpha=.12f) else Color.Transparent)
                    Row(Modifier.animateItem().width(if (tab.pinned) 50.dp else tabWidth).height(36.dp).clip(shape)
                        .background(color).border(.75.dp,rim,shape)
                        .then(tabMenuGesture(Role.Tab, { a.select(tab) }) { x, y ->
                            a.contextMenu = a.tabCardMenu(tab, x, y, { a.sheet.isEmpty() }) {
                                val target = a.activeTabs.indexOf(tab)
                                if (target >= 0) tabScope.launch { list.animateScrollToItem(target) }
                            }
                        }).semantics { if (tab.pinned) contentDescription = "已固定，${tab.title.ifBlank { host(tab.url) }}" },
                        verticalAlignment = Alignment.CenterVertically) {
                        if (selected && !tab.pinned) Box(Modifier.size(44.dp).clickable(role=Role.Button) { a.close(tab) }
                            .semantics { contentDescription = "关闭标签页" },contentAlignment=Alignment.Center) {
                            Box(Modifier.size(14.dp).background(Ink.current, RoundedCornerShape(7.dp)), contentAlignment=Alignment.Center) {
                                LiquidKitIcon(Icon.Close, Card.current, Modifier.size(10.dp))
                            }
                        }
                        Box(Modifier.weight(1f).padding(horizontal=6.dp),contentAlignment=Alignment.Center) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                val markSize = if (tab.pinned) 22.dp else 17.dp
                                val favicon = tab.web?.favicon
                                if (favicon != null) Image(favicon.asImageBitmap(), null, Modifier.size(markSize))
                                else Box(Modifier.size(markSize).background(Ink.current.copy(alpha=.09f), RoundedCornerShape(4.dp)),
                                    contentAlignment = Alignment.Center) {
                                    Label((host(tab.url).removePrefix("www.").ifBlank { tab.title }).take(1).uppercase()
                                        .ifBlank { "•" }, if (tab.pinned) 15 else 11, bold=true)
                                }
                                if (!tab.pinned) Label(tab.title.ifBlank { host(tab.url).ifBlank { "起始页" } },13,bold=selected)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun BrowserPanelWindow(a: MainActivity, recordLibrary: Modifier, capture: WebGlassCapture?,
    tabletAddress: Rect?, tabletEditorBounds: MutableState<Rect?>, onTabletPanelVisible: (Boolean) -> Unit) {
    if (a.sheet == "页面缩放") return
    if (a.sheet == "标签页") {
        TabOverview(a)
        return
    }
    val kind = a.sheet.takeUnless { it == "查找" }.orEmpty()
    var retained by remember { mutableStateOf("") }
    val displayed = kind.ifEmpty { retained }
    val library = displayed in librarySections
    val tablet = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 650
    val tabletPanel = tablet && (library || displayed == "页面菜单")
    val transition = remember(if (library) sidebarHome else displayed) { MutableTransitionState(false) }
    SideEffect {
        if (kind.isNotEmpty()) retained = kind
        transition.targetState = kind.isNotEmpty()
    }
    if (kind.isEmpty() && transition.isIdle && !transition.currentState) return
    DisposableEffect(tabletPanel) {
        onTabletPanelVisible(tabletPanel)
        onDispose { onTabletPanelVisible(false) }
    }
    Dialog(onDismissRequest = {
        a.sheet = if (library && displayed != sidebarHome) sidebarHome else ""
    }, properties = DialogProperties(
        usePlatformDefaultWidth = false, decorFitsSystemWindows = false
    )) {
        if (tabletPanel) {
            val window = (LocalView.current.parent as? DialogWindowProvider)?.window
            DisposableEffect(window) {
                val dim = window?.attributes?.dimAmount
                val animations = window?.attributes?.windowAnimations
                window?.setDimAmount(0f)
                window?.setWindowAnimations(0)
                onDispose {
                    if (dim != null) window.setDimAmount(dim)
                    if (animations != null) window.setWindowAnimations(animations)
                }
            }
        }
        AnimatedVisibility(visibleState = transition,
            enter = if (tabletPanel || displayed=="地址") EnterTransition.None else if (displayed=="页面菜单") fadeIn(tween(110))
                else fadeIn(tween(170)) + slideInVertically(tween(190)) { it/12 },
            exit = if (tabletPanel || displayed=="地址") ExitTransition.None else if (displayed=="页面菜单") fadeOut(tween(140))
                else fadeOut(tween(120)) + slideOutVertically(tween(150)) { it/12 }) {
            when (displayed) {
                "页面菜单" -> PageMenu(a, capture)
                "地址" -> AddressEditor(a, capture, tabletAddress, tabletEditorBounds)
                "阅读器" -> a.page.readerArticle?.let { article -> key(article) { ReaderScreen(a, a.page, article) } }
                in librarySections -> LibrarySidebar(a, displayed, recordLibrary, capture)
                else -> Panel(a, displayed)
            }
        }
    }
}

@Composable private fun ZoomToolbar(a: MainActivity) {
    val percent = a.page.siteZoom
    val steps = a.zoomSteps
    val index = steps.indexOf(percent)
    LiquidKitGlassSurface(Modifier.padding(8.dp).widthIn(max=360.dp)) {
        Row(Modifier.padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically) {
            ZoomButton("缩小网页字号",index>0, { a.setSiteZoom(steps[index-1]) }) { Label("A",15,bold=true) }
            Box(Modifier.width(78.dp).height(48.dp).clickable(role=Role.Button) { a.setSiteZoom(100) }
                .semantics { contentDescription="恢复网页字号至 100%" },contentAlignment=Alignment.Center) {
                AnimatedContent(percent,transitionSpec={ fadeIn(tween(125)) togetherWith fadeOut(tween(85)) },
                    label="网页字号") { Label("$it%",16,bold=true) }
            }
            ZoomButton("放大网页字号",index in 0 until steps.lastIndex, { a.setSiteZoom(steps[index+1]) }) { Label("A",23,bold=true) }
            Tool(Icon.Close,"完成缩放") { a.sheet = "" }
        }
    }
}

@Composable private fun ZoomButton(name: String, enabled: Boolean, click: () -> Unit, content: @Composable () -> Unit) {
    Box(Modifier.size(48.dp).clip(RoundedCornerShape(24.dp)).clickable(enabled=enabled,role=Role.Button,onClick=click)
        .semantics { contentDescription=name }.graphicsLayer { alpha=if (enabled) 1f else .35f },
        contentAlignment=Alignment.Center) { content() }
}

@Composable private fun AnimatedVisibilityScope.PageMenu(a: MainActivity, capture: WebGlassCapture?) {
    val dismiss = { a.sheet = "" }
    Box(Modifier.fillMaxSize()) {
        val interaction = remember { MutableInteractionSource() }
        Box(Modifier.fillMaxSize().clickable(interactionSource=interaction,indication=null,onClick=dismiss))
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().padding(10.dp)) {
            val tablet = maxWidth >= 630.dp
            val window = (LocalView.current.parent as? DialogWindowProvider)?.window
            if (!tablet) DisposableEffect(window) {
                val original = window?.attributes?.dimAmount
                window?.setDimAmount(.20f)
                onDispose { if (original != null) window.setDimAmount(original) }
            }
            val menuWidth = if (tablet) minOf(232.dp,maxWidth) else maxWidth
            var origin by remember { mutableStateOf(Offset.Zero) }
            val anchor = a.pageMenuAnchor
            val density = LocalDensity.current
            val x = with(density) { ((anchor?.x ?: origin.x)-origin.x).toDp() }
            val y = with(density) { ((anchor?.y ?: origin.y)-origin.y).toDp() }
            val left = (x-42.dp).coerceIn(0.dp,maxWidth-menuWidth)
            val top = (y+4.dp).coerceIn(0.dp,(maxHeight-160.dp).coerceAtLeast(0.dp))
            val position = if (tablet) Modifier.align(Alignment.TopStart).offset(x=left,y=top)
                else Modifier.align(Alignment.BottomCenter)
            Box(Modifier.matchParentSize().onGloballyPositioned { origin = it.localToScreen(Offset.Zero) })
            val motion = if (tablet) {
                val progress = transition.animateFloat(transitionSpec = {
                    if (targetState == EnterExitState.Visible) spring(dampingRatio=.76f, stiffness=850f)
                    else tween(110, easing=CubicBezierEasing(.32f,0f,.7f,.38f))
                }, label="页面菜单缩放") { if (it == EnterExitState.Visible) 1f else 0f }
                val pivot = ((x-left)/menuWidth).coerceIn(0f,1f)
                Modifier.graphicsLayer {
                    val fraction = progress.value
                    transformOrigin = TransformOrigin(pivot,0f)
                    scaleX = .28f + .72f*fraction
                    scaleY = scaleX
                    translationY = -22.dp.toPx()*(1f-fraction)
                    alpha = (fraction*1.8f).coerceIn(0f,1f)
                }
            } else Modifier.animateEnterExit(enter=fadeIn()+slideInVertically { it/3 },
                exit=fadeOut(tween(120))+slideOutVertically(tween(140)) { it/4 })
            val menuHeight = minOf(520.dp, if (tablet) maxHeight-top else maxHeight)
            Box(position.then(motion)) {
                DialogGlassSurface(capture,Modifier.width(menuWidth).heightIn(max=menuHeight),
                    shape=RoundedCornerShape(if (tablet) 18.dp else 26.dp),
                    tint=Card.current.copy(alpha=if (tablet) .72f else .32f), panelBlur=if (tablet) 16.dp else 0.dp) {
                    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(8.dp)) {
                        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                            Box(Modifier.weight(1f).padding(start=14.dp)) {
                                Label(host(a.page.url).ifBlank { "起始页" },16,bold=true)
                            }
                            Tool(Icon.Close,"完成",click=dismiss)
                        }
                        PageActions(a)
                    }
                }
            }
        }
    }
}

@Composable private fun FavoriteGrid(a: MainActivity) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = (maxWidth / 76.dp).toInt().coerceIn(3,8)
        val cellWidth = maxWidth / columns
        val favorites = BrowserLibrary.favorites
        var expanded by remember { mutableStateOf(false) }
        val sites = if (expanded) favorites else favorites.take(columns * 2)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (favorites.isEmpty()) Action(Icon.Bookmark, "查看书签", "还没有个人收藏") { a.sheet = "书签" }
            sites.chunked(columns).forEach { group ->
                Row(Modifier.fillMaxWidth()) {
                    group.forEach { site ->
                        Column(Modifier.width(cellWidth).clip(RoundedCornerShape(14.dp))
                            .then(tabMenuGesture(Role.Button, { a.navigate(site.url) }, "显示收藏菜单") { x, y ->
                                a.showBookmarkActions(site, x, y)
                            }).padding(4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)).background(Card.current),
                                contentAlignment = Alignment.Center) {
                                val mark = when (host(site.url)) {
                                    "www.wikipedia.org" -> "W"
                                    "github.com" -> "GH"
                                    else -> site.name.take(1).uppercase()
                                }
                                Label(mark, 26, bold = true, serif = mark == "W")
                            }
                            Spacer(Modifier.height(5.dp))
                            Label(site.name, 12)
                        }
                    }
                }
            }
            if (favorites.size > columns * 2) Box(Modifier.clickable(role = Role.Button) { expanded = !expanded }
                .padding(horizontal = 6.dp, vertical = 8.dp)) { Label(if (expanded) "收起收藏" else "显示全部收藏", 14, Accent.current) }
        }
    }
}

@Composable private fun Home(a: MainActivity) {
    Column(Modifier.fillMaxSize().semantics { contentDescription = if (a.page.secret) "无痕起始页" else "起始页" }.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Label(if (a.page.secret) "无痕浏览" else "个人收藏", 22, bold = true)
            Spacer(Modifier.weight(1f))
            if (!a.page.secret) Tool(Icon.Clock, "历史记录") { a.sheet = "历史记录" }
        }
        FavoriteGrid(a)
        Label("隐私", 20, bold = true)
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Card.current).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Label("阻止跨站 Cookie", 16, bold = true)
            Label(if (a.page.secret) "无痕标签使用独立的网站数据，关闭最后一张无痕标签后清除。"
                  else "网站可使用自己的 Cookie。嵌入页面的第三方网站受到限制。", 14, Muted.current, lines = 3)
        }

        Label("资料库", 20, bold = true)
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Card.current)) {
            Action(Icon.Bookmark, "书签", "${a.bookmarks.size} 个网页") { a.sheet = "书签" }
            Action(Icon.Document, "阅读列表", "${a.reading.size} 个网页") { a.sheet = "阅读列表" }
        }
        if (!a.page.secret && a.history.isNotEmpty()) {
            Label("最近浏览", 20, bold = true)
            a.history.take(3).forEach { Action(Icon.Clock, it.name, host(it.url)) { a.navigate(it.url) } }
        }
    }
}

@Composable private fun Dock(a: MainActivity, readingFraction: () -> Float) {
    val p = a.page
    var drag by remember { mutableFloatStateOf(0f) }
    var tracking by remember { mutableStateOf(false) }
    var settling by remember { mutableStateOf<Job?>(null) }
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    fun release() {
        val from = drag
        settling = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            offset.snapTo(from)
            tracking = false
            offset.animateTo(0f, spring(stiffness = 850f))
        }
    }
    val fraction = readingFraction()
    val address = addressLabel(p)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val compactWidth = remember(address,measurer,density) {
        val textWidth = measurer.measure(address,TextStyle(fontSize=13.sp,fontWeight=FontWeight.SemiBold),maxLines=1)
        with(density) { (textWidth.size.width.toDp()+32.dp).coerceIn(68.dp,280.dp) }
    }
    val threshold = with(LocalDensity.current) { 46.dp.toPx() }
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (p.progress in 1..99) Box(Modifier.fillMaxWidth().height(2.dp)) { Spacer(Modifier.fillMaxWidth(p.progress / 100f).fillMaxHeight().background(Accent.current)) }
        BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            val width = maxWidth
            val touchArea = Modifier.fillMaxWidth().pointerInput(p.id) {
                val minimumFlick = 18.dp.toPx()
                val flickSpeed = 420.dp.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val tracker = VelocityTracker()
                    tracker.addPointerInputChange(down)
                    var dragging = false
                    var startOffset = 0f
                    val viewport = p.web?.parent as? TabViewport
                    try {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (change.isConsumed || event.changes.size > 1 || a.contextMenu != null) break
                            val (dx, dy) = change.position - down.position
                            tracker.addPointerInputChange(change)
                            if (!dragging && maxOf(abs(dx),abs(dy)) >= viewConfiguration.touchSlop) {
                                settling?.cancel()
                                startOffset = offset.value
                                dragging = true
                                tracking = true
                            }
                            if (dragging) {
                                change.consume()
                                val adjacent = a.adjacentTab(p, forward = dx < 0)
                                val horizontal = abs(dx) > abs(dy)
                                val travel = if (horizontal) dx * (if (adjacent == null) .18f else .55f) else 0f
                                drag = (startOffset + travel).coerceIn(-threshold*2, threshold*2)
                                if (horizontal && dx != 0f && viewport != null) {
                                    viewport.track(adjacent?.takeIf { it.url.isNotBlank() }?.let(a::browser),dx)
                                } else viewport?.track(null,0f)
                                if (!change.pressed) {
                                    val duration = (change.uptimeMillis-down.uptimeMillis).coerceAtLeast(1)
                                    val average = dx*1000f/duration
                                    val velocity = tracker.calculateVelocity().x
                                    val along = if (dx < 0) -velocity else velocity
                                    val flick = abs(dx) >= minimumFlick &&
                                        (along >= flickSpeed || duration <= 125L && abs(average) >= flickSpeed)
                                    if (dy < -threshold && -dy > abs(dx)*1.5f) a.sheet = "标签页"
                                    else if (abs(dx) > abs(dy)*1.5f && (abs(dx) >= threshold || flick) &&
                                        along > -flickSpeed) adjacent?.let(a::select)
                                }
                            }
                            if (!change.pressed) break
                        }
                    } finally {
                        if (dragging) {
                            if (a.page === p) viewport?.retract()
                            release()
                        }
                    }
                }
            }
            val barWidth = lerp(width,compactWidth.coerceAtMost(width),fraction)
            val restore = {
                p.compactTools = false
                (p.web?.parent as? TabViewport)?.readingToolbar?.expand()
                Unit
            }
            val touch = if (fraction >= 1f) touchArea.then(addressGesture(a,p,restore))
                .semantics { contentDescription = "显示工具栏" } else touchArea
            Box(Modifier.align(Alignment.Center).width(barWidth).height(lerp(50.dp,44.dp,fraction))
                .then(touch),contentAlignment=Alignment.Center) {
                LiquidKitGlassSurface(Modifier.fillMaxWidth().graphicsLayer {
                    translationX = if (tracking) drag else offset.value
                }) { AddressRow(a,fraction) }
            }
        }
        Spacer(Modifier.height(4.dp*(1f-fraction)))
        if (fraction < 1f) LiquidKitGlassSurface(Modifier.fillMaxWidth(.87f)
            .height(48.dp*(1f-fraction)).clipToBounds().graphicsLayer { alpha=1f-fraction }) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
            Tool(Icon.Back, "后退", p.back, hold = { x, y -> a.showNavigationHistory(p, true, x, y) }) { p.web?.goBack() }
            Tool(Icon.Forward, "前进", p.forward, hold = { x, y -> a.showNavigationHistory(p, false, x, y) }) { p.web?.goForward() }
            Tool(Icon.Share, "共享", p.url.isNotEmpty()) { a.share(p.url) }
            Tool(Icon.Bookmark, "书签") { a.sheet = "书签" }
            Tool(Icon.SquareOnSquare, "标签页，${a.activeTabs.size} 个", hold = { x, y -> a.showTabActions(p, x, y) }) {
                a.sheet = "标签页"
            }
        }
    }
    }
}

private fun addressLabel(p: Page) = if (p.url.isEmpty()) "搜索或输入网址"
    else (if (p.url.startsWith("http://")) "不安全 · " else "") + host(p.url)

@Composable private fun addressGesture(a: MainActivity, p: Page, tap: () -> Unit) =
    tabMenuGesture(Role.Button, tap, "显示地址栏菜单") { x,y -> a.showAddressActions(p,x,y) }

@Composable private fun AddressRow(a: MainActivity, fold: Float = 0f, wide: Boolean = false) {
    val p = a.page
    fun Modifier.control(label: String, enabled: Boolean = true, action: () -> Unit): Modifier =
        clickable(enabled = enabled, role = Role.Button, onClick = action).semantics { contentDescription = label }
    var menuButton by remember { mutableStateOf<LayoutCoordinates?>(null) }
    fun menuAnchor(c: LayoutCoordinates) = c.localToScreen(Offset(c.size.width/2f,c.size.height.toFloat()))
    val side = Modifier.width(48.dp*(1f-fold)).fillMaxHeight().clipToBounds().graphicsLayer { alpha=1f-fold }
    Row(Modifier.fillMaxWidth().height(lerp(if (wide) 44.dp else 50.dp,28.dp,fold)), verticalAlignment = Alignment.CenterVertically) {
        if (fold < 1f) Box(side.onGloballyPositioned {
            menuButton = it
            if (a.sheet == "页面菜单") a.pageMenuAnchor = menuAnchor(it)
        }.clip(RoundedCornerShape(24.dp)).control("页面菜单") {
            a.pageMenuAnchor = menuButton?.takeIf { it.isAttached }?.let(::menuAnchor)
            a.sheet = "页面菜单"
        },
            contentAlignment = Alignment.Center) { LiquidKitIcon(if (wide) Icon.List else Icon.More, Ink.current) }
        if (fold < 1f && a.downloads.ids(p.secret).isNotEmpty()) {
            Box(side.liquidKitPopoverAnchor(a.downloadAnchor).clip(RoundedCornerShape(24.dp))
                .control("查看下载") { a.sheet = "下载项目" }, contentAlignment = Alignment.Center) {
                LiquidKitIcon(Icon.Download, Accent.current)
            }
        }
        val address = addressLabel(p)
        val edit = if (fold < 1f) addressGesture(a,p) { a.sheet = "地址" }
            .semantics { contentDescription = address } else Modifier
        Box(Modifier.weight(1f).fillMaxHeight().then(edit),contentAlignment=Alignment.Center) {
            BasicText(address, style=TextStyle(color=Ink.current,fontSize=(15f-2f*fold).sp,
                fontWeight=if (!wide && p.url.isNotEmpty()) FontWeight.SemiBold else FontWeight.Normal),
                maxLines=1,overflow=TextOverflow.Ellipsis)
        }
        val loading = p.progress < 100
        if (fold < 1f) Box(side.clip(RoundedCornerShape(24.dp))
            .control(if (loading) "停止载入" else "重新载入", p.url.isNotEmpty()) {
                p.web?.let { web ->
                    if (loading) {
                        web.stopLoading()
                        (web.parent as? TabViewport)?.pullRefresh?.loaded(web)
                    } else web.reload()
                }
            }, contentAlignment = Alignment.Center) {
            LiquidKitIcon(if (loading) Icon.Close else Icon.Reload,
                if (p.url.isNotEmpty()) Ink.current else Muted.current.copy(alpha = .45f))
        }
    }
}

@Composable private fun Input(value: TextFieldValue, hint: String, set: (TextFieldValue) -> Unit, modifier: Modifier, url: Boolean, submit: () -> Unit) {
    val focus = remember { FocusRequester() }
    BasicTextField(value, set, modifier.focusRequester(focus).semantics { contentDescription = hint }
        .padding(horizontal=8.dp,vertical=14.dp),
        singleLine = true, textStyle = TextStyle(color = Ink.current,fontSize = 17.sp), cursorBrush = SolidColor(Accent.current),
        keyboardOptions = KeyboardOptions(keyboardType = if (url) KeyboardType.Uri else KeyboardType.Text, imeAction = ImeAction.Go),
        keyboardActions = KeyboardActions(onGo = { submit() }),
        decorationBox = { field -> Box { if (value.text.isEmpty()) Label(hint, 16, Muted.current); field() } })
    LaunchedEffect(Unit) { focus.requestFocus() }
}

@Composable private fun FindBar(a: MainActivity, modifier: Modifier) {
    val web = a.page.web
    var query by remember { mutableStateOf(TextFieldValue(a.findSeed, TextRange(a.findSeed.length))) }
    SideEffect { a.findSeed = "" }
    var active by remember { mutableIntStateOf(0) }
    var total by remember { mutableIntStateOf(-1) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val searching = a.sheet == "查找"
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    // The page menu has its own window. Wait until it gives focus back before opening the IME.
    LaunchedEffect(searching, windowFocused) {
        if (searching && windowFocused) {
            focus.requestFocus()
            keyboard?.show()
        }
    }
    val text = query.text
    DisposableEffect(web, searching, text) {
        active = 0
        total = -1
        if (searching && text.isNotEmpty()) {
            web?.setFindListener { index, count, complete ->
                if (complete) {
                    total = count
                    active = if (count > 0) index + 1 else 0
                }
            }
            web?.findAllAsync(text)
        }
        onDispose {
            web?.setFindListener(null)
            web?.clearMatches()
        }
    }
    val canStep = searching && total > 1
    val finish = { keyboard?.hide(); a.sheet = "" }
    LiquidKitGlassSurface(modifier.padding(horizontal=8.dp,vertical=5.dp).widthIn(max=560.dp).fillMaxWidth()) {
        Row(Modifier.padding(horizontal=6.dp), verticalAlignment=Alignment.CenterVertically) {
            Row(Modifier.weight(1f).heightIn(min=44.dp).background(Card.current.copy(alpha=.38f),RoundedCornerShape(24.dp))
                .padding(start=12.dp,end=7.dp), verticalAlignment=Alignment.CenterVertically) {
                BasicTextField(query, { query = it }, Modifier.weight(1f).focusRequester(focus)
                    .semantics { contentDescription = "在页面中查找" }, singleLine=true,
                    textStyle=TextStyle(color=Ink.current,fontSize=16.sp),cursorBrush=SolidColor(Accent.current),
                    keyboardOptions=KeyboardOptions(imeAction=ImeAction.Next),
                    keyboardActions=KeyboardActions(onNext={ if (canStep) web?.findNext(true) }),
                    decorationBox={ field -> Box {
                        if (text.isEmpty()) Label("查找文字",16,Muted.current)
                        field()
                    } })
                if (text.isNotEmpty() && total >= 0) Label(if (total == 0) "无结果" else "$active/$total",12,Muted.current)
                if (text.isNotEmpty()) Box(Modifier.size(36.dp).clickable(role=Role.Button) {
                    query = TextFieldValue()
                    focus.requestFocus()
                    keyboard?.show()
                }.semantics { contentDescription = "清空查找文字" }, contentAlignment=Alignment.Center) {
                    LiquidKitIcon(Icon.Close, Muted.current, Modifier.size(14.dp))
                }
            }
            Tool(Icon.Back,"上一个匹配",canStep,rotation=90f) { web?.findNext(false) }
            Tool(Icon.Forward,"下一个匹配",canStep,rotation=90f) { web?.findNext(true) }
            Tool(Icon.Close,"结束查找",searching,click=finish)
        }
    }
}

@Composable private fun AnimatedVisibilityScope.AddressEditor(a: MainActivity, capture: WebGlassCapture?,
    tabletAddress: Rect?, tabletEditorBounds: MutableState<Rect?>) {
    val page = a.page
    var query by remember(page.id) { mutableStateOf(TextFieldValue(page.url, TextRange(0, page.url.length))) }
    var edited by remember(page.id) { mutableStateOf(false) }
    var keyboardChoices by remember(page.id) { mutableStateOf(false) }
    var dismissedWithEscape by remember(page.id) { mutableStateOf(false) }
    val text = query.text
    val suggestions by remember(a,page,text) { derivedStateOf { a.addressSuggestions(text) } }
    var selection by remember(page,text) { mutableStateOf<String?>(null) }
    val list = rememberLazyListState()
    val selectedIndex = suggestions.indexOfFirst { it.key == selection }
    LaunchedEffect(text,selectedIndex) {
        if (selectedIndex < 0) list.scrollToItem(0)
        else if (list.layoutInfo.visibleItemsInfo.none { it.index == selectedIndex && it.offset >= 0 &&
                it.offset + it.size <= list.layoutInfo.viewportEndOffset }) list.animateScrollToItem(selectedIndex)
    }
    fun submit() {
        val choice = suggestions.getOrNull(selectedIndex)
        if (choice == null) a.navigate(text) else choice.choose()
    }
    var panelOrigin by remember { mutableStateOf<Offset?>(null) }
    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().imePadding().onGloballyPositioned {
        panelOrigin = it.localToScreen(Offset.Zero)
    }.onPreviewKeyEvent { key ->
        val e = key.nativeKeyEvent
        if (e.action == KeyEvent.ACTION_DOWN && e.keyCode == KeyEvent.KEYCODE_ESCAPE && e.hasNoModifiers()) {
            dismissedWithEscape = true
        }
        if (e.hasNoModifiers() && suggestions.isNotEmpty()) {
            val down = e.keyCode == KeyEvent.KEYCODE_DPAD_DOWN
            if (down || e.keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                if (e.action == KeyEvent.ACTION_DOWN) {
                    keyboardChoices = true
                    val next = if (down) (selectedIndex+1).coerceAtMost(suggestions.lastIndex)
                        else (selectedIndex-1).coerceAtLeast(-1)
                    selection = suggestions.getOrNull(next)?.key
                }
                return@onPreviewKeyEvent true
            }
            if (selectedIndex >= 0 && (e.keyCode == KeyEvent.KEYCODE_ENTER || e.keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER)) {
                if (e.action == KeyEvent.ACTION_DOWN && e.repeatCount == 0) submit()
                return@onPreviewKeyEvent true
            }
        }
        a.browserKeys.handle(key.nativeKeyEvent) { event ->
            when (event.keyCode) {
                android.view.KeyEvent.KEYCODE_L -> if (!event.isShiftPressed) {
                    query = query.copy(selection = TextRange(0, query.text.length))
                    true
                } else false
                android.view.KeyEvent.KEYCODE_ENTER, android.view.KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                    a.destinationFor(query.text)?.let { destination ->
                        a.add(page.secret, destination, background = !event.isShiftPressed)
                        a.sheet = ""
                    }
                    true
                }
                else -> false
            }
        }
    }) {
        val tablet = maxWidth >= 650.dp
        val favoritesVisible = tablet && !page.secret && !keyboardChoices && BrowserLibrary.favorites.isNotEmpty() &&
            (text.isEmpty() || (!edited && text == page.url))
        val panelWidth = if (tablet) minOf(maxWidth * .75f, 620.dp) else maxWidth - 16.dp
        val suggestionsHeight = (maxHeight - 88.dp).coerceIn(0.dp, 340.dp)
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        DisposableEffect(window,tablet) {
            val original = window?.attributes?.dimAmount
            val animations = window?.attributes?.windowAnimations
            val stopObserving = if (tablet) window?.let(a.menuTouches::observe) else null
            if (tablet) {
                window?.setDimAmount(0f)
                window?.setWindowAnimations(0)
            }
            onDispose {
                stopObserving?.invoke()
                if (tablet) {
                    tabletEditorBounds.value = null
                    if (original != null) window.setDimAmount(original)
                    if (animations != null) window.setWindowAnimations(animations)
                }
            }
        }
        val sampledDark = if (tablet) capture?.darkAddressBackdrop ?: (page.secret || isSystemInDarkTheme())
            else page.secret || isSystemInDarkTheme()
        var lastDark by remember { mutableStateOf(sampledDark) }
        if (a.sheet == "地址") SideEffect { lastDark = sampledDark }
        val dark = if (a.sheet == "地址") sampledDark else lastDark
        Box(Modifier.fillMaxSize().clickable(remember { MutableInteractionSource() }, null) { a.sheet = "" })
        val field: @Composable () -> Unit = {
            Row(Modifier.fillMaxWidth().padding(horizontal=6.dp),verticalAlignment=Alignment.CenterVertically) {
                if (tablet) LiquidKitIcon(Icon.Search, Muted.current, Modifier.padding(start=12.dp).size(18.dp))
                else Tool(Icon.Check,"完成") { a.sheet = "" }
                Input(query,"网址或搜索词",{
                    if (it.text != query.text) { edited = true; keyboardChoices = false }
                    query = it
                },Modifier.weight(1f),true,::submit)
                if (text.isNotEmpty()) Tool(Icon.Close,"清空输入") {
                    edited = true
                    keyboardChoices = false
                    query = TextFieldValue()
                }
            }
        }
        val choices: @Composable () -> Unit = {
            LiquidKitList(style = LiquidKitListStyle.Plain, state = list, contentPadding = PaddingValues(0.dp),
                modifier=Modifier.heightIn(max=suggestionsHeight),
                colors = liquidKitColors(dark).copy(surface = Color.Transparent, secondary=Muted.current,
                    selected = Accent.current.copy(alpha=.17f))) {
                items(suggestions, key={it.key}) { choice ->
                    val subtitle = if (choice.detail.length > 160) choice.detail.take(159)+"…" else choice.detail
                    LiquidKitListCell(title=choice.title, subtitle=subtitle, selected=choice.key == selection,
                        onClick=choice.choose, leading={ LiquidKitIcon(choice.icon, Accent.current) },
                        modifier=Modifier.animateItem(fadeInSpec=tween(110),placementSpec=null,fadeOutSpec=null))
                }
            }
        }
        if (tablet) {
            val expansion by transition.animateFloat(transitionSpec={
                if (targetState == EnterExitState.Visible) tween(410, easing=CubicBezierEasing(.16f,.82f,.23f,1f))
                else if (dismissedWithEscape) snap()
                else tween(210, easing=CubicBezierEasing(.26f,0f,.34f,1f))
            },label="平板地址栏展开") {
                if (it == EnterExitState.Visible) 1f else 0f
            }
            val rowsReveal = transition.animateFloat(transitionSpec = {
                if (targetState == EnterExitState.Visible) tween(260, delayMillis=20, easing=CubicBezierEasing(.18f,.55f,.32f,1f))
                else if (dismissedWithEscape) snap() else tween(150)
            }, label="地址栏内容显现") { if (it == EnterExitState.Visible) 1f else 0f }
            val density = LocalDensity.current
            val initialWidth = remember { maxWidth }
            val openingAnchor = remember { tabletAddress }
            val source = openingAnchor.takeIf { initialWidth == maxWidth }
            val startWidth = with(density) { source?.width?.toDp() } ?: tabletAddressWidth(maxWidth)
            val startHeight = with(density) { source?.height?.toDp() } ?: 44.dp
            val startTop = with(density) { panelOrigin?.let { origin -> source?.let { (it.top-origin.y).toDp() } } } ?: 4.dp
            val width = lerp(startWidth.coerceIn(1.dp,maxWidth), panelWidth, expansion).coerceIn(1.dp,maxWidth)
            val screenHeight = with(density) { LocalWindowInfo.current.containerSize.height.toDp() }
            val favoritesHeight = (minOf(728.dp, screenHeight*.64f, maxHeight-12.dp)-48.dp).coerceAtLeast(0.dp)
            CompositionLocalProvider(Ink provides if (dark) Color.White else Color.Black,
                Muted provides if (dark) Color(0xffd5e5f0) else Color.DarkGray,
                Accent provides if (dark) Color(0xffe2f1ff) else Color(0xff0066cc)) {
                LiquidKitTheme(opacity=.28f,dark=dark) {
                    DialogGlassSurface(capture, Modifier.align(Alignment.TopCenter)
                        .offset(y=lerp(startTop,6.dp,expansion)).width(width).onGloballyPositioned {
                            tabletEditorBounds.value = Rect(it.localToScreen(Offset.Zero),it.size.toSize())
                        },
                        shape=RoundedCornerShape(23.dp), tint=if (dark) Color.Black.copy(alpha=.34f) else Color.White.copy(alpha=.58f),
                        panelBlur=18.dp) {
                        Column(Modifier.clipToBounds().layout { measurable, constraints ->
                            val child = measurable.measure(constraints)
                            val start = startHeight.toPx()
                            val height = constraints.constrainHeight((start+(child.height-start)*expansion).roundToInt())
                            layout(child.width,height) { child.place(0,0) }
                        }.graphicsLayer { alpha=expansion.coerceIn(0f,1f) }) {
                            field()
                            Box(Modifier.graphicsLayer {
                                val shown = rowsReveal.value
                                alpha = shown
                                translationY = -12.dp.toPx()*(1f-shown)
                                scaleX = .95f + .05f*shown
                                scaleY = scaleX
                                transformOrigin = TransformOrigin(.5f,0f)
                            }) {
                                if (favoritesVisible) {
                                    Column(Modifier.height(favoritesHeight)
                                        .wrapContentWidth(Alignment.CenterHorizontally,unbounded=true).width(panelWidth)
                                        .verticalScroll(rememberScrollState()).padding(16.dp),
                                        verticalArrangement=Arrangement.spacedBy(12.dp)) {
                                        Label("个人收藏",18,bold=true)
                                        FavoriteGrid(a)
                                    }
                                } else if (suggestions.isNotEmpty()) choices()
                            }
                        }
                    }
                }
            }
        } else Column(Modifier.align(Alignment.BottomCenter).animateEnterExit(
            enter=fadeIn(tween(170))+slideInVertically(tween(190)) { it/5 },
            exit=fadeOut(tween(120))+slideOutVertically(tween(150)) { it/5 }
        ).width(panelWidth).padding(vertical=6.dp),
            verticalArrangement=Arrangement.spacedBy(6.dp)) {
            if (suggestions.isNotEmpty()) DialogGlassSurface(capture, Modifier.fillMaxWidth(),
                shape=RoundedCornerShape(19.dp), tint=Card.current.copy(alpha=.48f)) { choices() }
            DialogGlassSurface(capture, Modifier.fillMaxWidth(), shape=LiquidKitCapsule,
                tint=Card.current.copy(alpha=.32f)) { field() }
        }
    }
}

internal const val sidebarHome = "侧栏"
private val sidebarWidth = 320.dp
internal val librarySections = setOf(sidebarHome, "书签", "阅读列表", "历史记录", "最近关闭")

@Composable private fun PhoneLibrarySheet(a: MainActivity, kind: String, recordLibrary: Modifier) {
    val backdrop = LocalLiquidKitBackdrop.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // The sheet owns the scroll gesture until it reaches the large detent.
        // Bound the lazy list inside its scrollable body, including space for the pinned header.
        val headerHeight = if (kind == "阅读列表") 152.dp else 96.dp
        val listHeight = (maxHeight * .95f - headerHeight).coerceAtLeast(48.dp)
        key(kind) {
            LiquidKitSheet(onDismissRequest = { a.sheet = "" }, modifier = recordLibrary, title = kind, backdrop = backdrop) {
                if (kind == sidebarHome) SidebarDirectory(a, Modifier.heightIn(max = listHeight))
                else LibraryList(a, kind, Modifier.heightIn(max = listHeight))
            }
        }
    }
}

@Composable private fun AnimatedVisibilityScope.LibrarySidebar(a: MainActivity, kind: String, recordLibrary: Modifier,
    capture: WebGlassCapture?) {
    val dismiss = { a.sheet = "" }
    SideEffect { if (kind == a.sheet) a.sidebarLocation = kind }
    // In the iPad recording the leading panel advances ahead of the page dim.
    val slide = transition.animateFloat(transitionSpec = {
        if (targetState == EnterExitState.Visible) tween(440, easing=CubicBezierEasing(.12f,.90f,.23f,1f))
        else tween(325, easing=CubicBezierEasing(.20f,.58f,.39f,1f))
    }, label="侧栏位移") { if (it == EnterExitState.Visible) 1f else 0f }
    val dim = transition.animateFloat(transitionSpec = {
        if (targetState == EnterExitState.Visible) tween(440, easing=CubicBezierEasing(.20f,.65f,.22f,1f))
        else tween(235, easing=CubicBezierEasing(.32f,.05f,.32f,1f))
    }, label="侧栏背景") { if (it == EnterExitState.Visible) .20f else 0f }
    val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
    DisposableEffect(dialogWindow) {
        val stopObserving = dialogWindow?.let(a.menuTouches::observe)
        onDispose { stopObserving?.invoke() }
    }
    Box(Modifier.fillMaxSize().then(recordLibrary)) {
        Box(Modifier.matchParentSize().drawBehind { drawRect(Color.Black.copy(alpha=dim.value)) }.clickable(
            interactionSource = remember { MutableInteractionSource() }, indication = null,
            onClickLabel = "关闭资料库", onClick = dismiss))
        Box(Modifier.align(Alignment.CenterStart).offset {
            IntOffset(((slide.value-1f)*sidebarWidth.toPx()).roundToInt(),0)
        }.width(sidebarWidth).fillMaxHeight()) {
            val tint = if (Card.current == Color.White) Color(0xffdce1e7) else Card.current
            DialogGlassSurface(capture, Modifier.fillMaxSize(), shape = RectangleShape, tint = tint.copy(alpha=.76f), panelBlur=18.dp) {
                var detailX by remember { mutableStateOf<Float?>(null) }
                LiquidKitSwipeBackNavigation(page = kind,
                    previousPage = sidebarHome.takeIf { kind != sidebarHome },
                    depth = { if (it == sidebarHome) 0 else 1 },
                    onNavigateBack = { if (a.sheet == kind) a.sheet = it },
                    modifier = Modifier.fillMaxSize().safeDrawingPadding().clipToBounds()) { section ->
                    // Both pages share one glass surface. Only draw the exposed part of
                    // the directory as the foreground page moves over it.
                    val pageLayer = if (section == sidebarHome) {
                        var directoryX by remember { mutableFloatStateOf(0f) }
                        Modifier.onGloballyPositioned { directoryX = it.localToScreen(Offset.Zero).x }
                            .drawWithContent {
                                val left = detailX
                                if (left == null) drawContent()
                                else if (layoutDirection == LayoutDirection.Ltr)
                                    clipRect(right = (left-directoryX).coerceIn(0f,size.width)) { this@drawWithContent.drawContent() }
                                else clipRect(left = (left+size.width-directoryX).coerceIn(0f,size.width)) { this@drawWithContent.drawContent() }
                            }
                    } else {
                        DisposableEffect(Unit) { onDispose { detailX = null } }
                        Modifier.onGloballyPositioned { detailX = it.localToScreen(Offset.Zero).x }
                    }
                    Column(Modifier.fillMaxSize().then(pageLayer).padding(horizontal = 18.dp, vertical = 8.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            if (section != sidebarHome) Tool(Icon.Back, "返回侧栏") { a.sheet = sidebarHome }
                            if (section != sidebarHome) Label(section, 22, bold = true)
                            Spacer(Modifier.weight(1f))
                            SidebarButton("完成", dismiss)
                        }
                        if (section == sidebarHome) SidebarDirectory(a, Modifier.fillMaxWidth())
                        else LibraryList(a, section)
                    }
                }
            }
        }
    }
}

@Composable private fun SidebarDirectory(a: MainActivity, modifier: Modifier) {
    LiquidKitList(style = LiquidKitListStyle.Sidebar, modifier = modifier,
        colors = liquidKitColors().copy(surface = Color.Transparent, background = Color.Transparent),
        contentPadding = PaddingValues(vertical = 8.dp)) {
        item { Label("资料库", 15, Muted.current, bold = true) }
        items(listOf("书签" to Icon.Bookmark, "阅读列表" to Icon.Document,
            "历史记录" to Icon.Clock, "最近关闭" to Icon.SquareOnSquare), key = { it.first }) { (section, icon) ->
            LiquidKitListCell(title = section, onClick = { a.sheet = section },
                leading = { LiquidKitIcon(icon, Accent.current) },
                trailing = { LiquidKitIcon(Icon.Forward, Muted.current, Modifier.size(14.dp)) })
        }
        item { Box(Modifier.padding(top = 18.dp)) { Label("标签页组", 15, Muted.current, bold = true) } }
        items(a.groupChoices(), key = { "group-${it.first}" }) { (id, name) ->
            LiquidKitListCell(title = name, selected = !a.page.secret && a.page.groupId == id,
                leading = { LiquidKitIcon(Icon.SquareOnSquare, Accent.current) },
                onClick = { a.preferredTab(false, id)?.let(a::select) ?: a.add(false, groupId = id) })
        }
        item {
            LiquidKitListCell(title = "无痕浏览", selected = a.page.secret,
                leading = { LiquidKitIcon(Icon.Moon, Accent.current) },
                onClick = { a.preferredTab(true)?.let(a::select) ?: a.add(true) })
        }
    }
}

@Composable private fun Panel(a: MainActivity, kind: String,
    modifier: Modifier = Modifier.fillMaxSize().background(Ground.current).safeDrawingPadding().imePadding()) {
    Column(modifier.pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }
        .padding(horizontal = 18.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Label(if (kind == "地址") "搜索或输入网址" else kind, 24, bold = true)
            Spacer(Modifier.weight(1f))
            Tool(Icon.Close,"完成") { a.sheet = "" }
        }
        when(kind) {
            "搜索引擎" -> listOf("Bing","DuckDuckGo","Google").forEach {
                Action(if (a.engine == it) Icon.Check else Icon.Search,it) { a.engine = it; a.save(); a.sheet = "" }
            }
            else -> LibraryList(a, kind)
        }
    }
}

@Composable private fun LibraryList(a: MainActivity, kind: String, modifier: Modifier = Modifier) {
    val readingList = kind == "阅读列表"
    val list = when(kind) {
        "书签" -> a.bookmarks
        "阅读列表" -> if (a.unreadOnly) a.reading.filterNot(BrowserLibrary::isRead) else a.reading
        else -> a.history
    }
    val closed = kind == "最近关闭"
    if (readingList) LiquidKitSegmentedPicker(options = listOf("全部", "未读"),
        selected = if (a.unreadOnly) "未读" else "全部", select = { a.unreadOnly = it == "未读" },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp))
    if (if (closed) a.recentlyClosed.isEmpty() else list.isEmpty()) Box(Modifier.padding(18.dp)) {
        Label(if (readingList && a.unreadOnly) "没有未读网页" else "还没有${kind}", 16, Muted.current)
    }
    if (kind == "历史记录" && (list.isNotEmpty() || a.recentlyClosed.isNotEmpty())) Action(Icon.Trash, "清空历史记录") {
        a.confirm("清空历史记录", "清空浏览记录和最近关闭的标签？", "清空", destructive = true) { a.clearHistory() }
    }
    LiquidKitList(style = LiquidKitListStyle.Plain, modifier = modifier, contentPadding = PaddingValues(0.dp),
        colors = liquidKitColors().copy(surface = Color.Transparent)) {
        if (closed) items(a.recentlyClosed,key={ it.id }) { entry ->
            val origin = BrowserLibrary.closedTabs.originHint(entry)
            LibraryRow(a,kind,entry.site,entry.id,subtitle=origin?.let { "来自 $it" } ?: host(entry.site.url),
                open={ a.reopen(entry) },remove={
                BrowserLibrary.closedTabs.remove(entry)
                a.save()
            })
        } else items(list,key={ it.url }) { site ->
            LibraryRow(a,kind,site,site.url,open={ if (readingList) a.openReading(site) else a.navigate(site.url) },remove={
                when (kind) {
                    "书签" -> BrowserLibrary.removeBookmark(site)
                    "阅读列表" -> BrowserLibrary.removeReading(site)
                    else -> {
                        a.history.remove(site)
                        BrowserLibrary.closedTabs.removeUrl(site.url)
                        a.save()
                    }
                }
            })
        }
    }
}

@Composable private fun LibraryRow(a: MainActivity, kind: String, site: Site, rowKey: String,
                                   subtitle: String = host(site.url),
                                   open: () -> Unit, remove: () -> Unit) {
    val actions = buildList {
        add(LiquidKitSwipeAction("删除", Icon.Trash, Color(0xffc83135)) { remove() })
        if (kind == "书签") add(LiquidKitSwipeAction("编辑", Icon.Rename, Color(0xff536879)) { a.editBookmark(site) })
        add(LiquidKitSwipeAction("共享", Icon.Share, Color(0xff346fa5)) { a.share(site.url) })
    }
    val gesture = when (kind) {
        "书签" -> tabMenuGesture(Role.Button, open, "显示书签菜单") { x,y -> a.showBookmarkActions(site,x,y,inLibrary=true) }
        "阅读列表" -> tabMenuGesture(Role.Button, open, "显示阅读列表菜单") { x,y -> a.showReadingActions(site,x,y) }
        else -> Modifier.clickable(role=Role.Button,onClick=open)
    }
    val read = kind == "阅读列表" && BrowserLibrary.isRead(site)
    val leading = if (kind == "阅读列表") listOf(LiquidKitSwipeAction(
        if (read) "标记为未读" else "标记为已读", if (read) Icon.Document else Icon.Check, Color(0xff346fa5)
    ) { BrowserLibrary.setRead(site, !read) }) else emptyList()
    LiquidKitSwipeActions(rowKey=rowKey,modifier=gesture,leading=leading,trailing=actions) {
        LiquidKitListCell(title=site.name, subtitle=subtitle, leading={
            if (kind == "阅读列表") LiquidKitIcon(if (read) Icon.Check else Icon.Document,
                if (read) Muted.current else Accent.current, Modifier.semantics { contentDescription = if (read) "已读网页" else "未读网页" })
            else LiquidKitIcon(Icon.Link,Accent.current)
        })
    }
}

@Composable private fun PageActions(a: MainActivity) {
    val p = a.page
    if (p.url.isNotEmpty()) {
        Action(Icon.Search,"页面缩放","网页字号 · ${p.siteZoom}%") { a.sheet = "页面缩放" }
        val extracted by produceState<ReaderArticle?>(null, p, p.url, p.readerRequest) {
            value = null
            value = a.extractReaderArticle(p)
        }
        extracted?.let { article ->
            Action(Icon.ListBulletRectangle,"显示阅读器") { p.readerArticle=article; a.sheet="阅读器" }
        }
        Action(Icon.Bookmark, if (a.bookmarks.any { it.url == p.url }) "编辑书签" else "添加书签") {
            a.editBookmark(Site(p.title.ifBlank { p.url }, p.url))
        }
        Action(Icon.Document,"加入阅读列表") { a.saveForLater(Site(p.title.ifBlank { p.url }, p.url)); a.sheet = "" }
        Action(Icon.Search,"在页面中查找") { a.openFind() }
        Action(Icon.Globe,if (p.desktop) "请求移动网站" else "请求桌面网站") { a.toggleDesktop(p); a.sheet = "" }
        Action(Icon.Filter,"收起工具栏") { p.compactTools = true; a.sheet = "" }
        Action(Icon.Clipboard,"复制网址") { a.copy(p.url); a.sheet = "" }
    }
    Action(Icon.Add,"新建标签页") { a.add(false) }
    Action(Icon.Moon,"新建无痕标签页") { a.add(true) }
    Action(Icon.Document,"阅读列表") { a.sheet = "阅读列表" }
    Action(Icon.Clock,"历史记录") { a.sheet = "历史记录" }
    Action(Icon.Download,"下载项目") { a.sheet = "下载项目" }
    Action(Icon.Filter,"搜索引擎",a.engine) { a.sheet = "搜索引擎" }
    if (a.webReady) Action(Icon.Trash,"清除网站数据") { a.clearWebsiteData() }
}

@Composable private fun DownloadsPanel(a: MainActivity) {
    val scene = LocalLiquidKitBackdrop.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val height = (maxHeight * .85f - 90.dp).coerceAtLeast(64.dp)
        val close = { a.sheet = "" }
        if (maxWidth >= 650.dp) {
            LiquidKitPopover(onDismissRequest = close, anchorBoundsInRoot = a.downloadAnchor.value,
                backdrop = scene, maxWidth = 380.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { Label("下载项目", 20, bold = true) }
                    Tool(Icon.Close,"完成",click = close)
                }
                DownloadRows(a, minOf(height,440.dp))
            }
        } else LiquidKitSheet(onDismissRequest = close, title = "下载项目", backdrop = scene) {
            DownloadRows(a, height)
        }
    }
}

@Composable private fun DownloadRows(a: MainActivity, height: Dp) {
    val secret = a.page.secret
    val store = a.downloads
    val ids = store.ids(secret)
    var rows by remember(secret) { mutableStateOf<List<DownloadItem>?>(null) }
    var unavailable by remember(secret) { mutableStateOf(false) }
    LaunchedEffect(secret,ids) {
        if (ids.isEmpty()) { rows = emptyList(); unavailable = false; return@LaunchedEffect }
        while (true) {
            if (a.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) {
                val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { runCatching { store.read(ids) } }
                unavailable = result.isFailure
                result.getOrNull()?.let { current ->
                    rows = current
                    val gone = ids - current.map { it.id }.toSet()
                    if (gone.isNotEmpty()) store.forget(secret,gone)
                }
            }
            kotlinx.coroutines.delay(if (rows.orEmpty().any { it.active }) 500L else 2400L)
        }
    }
    val entries = rows.orEmpty().filter { it.id in ids }
    LiquidKitList(style = LiquidKitListStyle.Plain, modifier = Modifier.fillMaxWidth().heightIn(max = height),
        colors = liquidKitColors().copy(surface = Color.Transparent), contentPadding = PaddingValues(0.dp)) {
        if (unavailable || entries.isEmpty()) item {
            Box(Modifier.fillMaxWidth().padding(18.dp)) {
                Label(when { unavailable -> "暂时无法读取下载项目"; rows == null -> "正在读取下载项目"; else -> "还没有下载项目" },
                    16, Muted.current)
            }
        }
        items(entries, key = { it.id }) { item ->
            val ready = item.status == DownloadManager.STATUS_SUCCESSFUL
            val fraction = if (item.total > 0) (item.received.toFloat()/item.total).coerceIn(0f,1f) else 0f
            val status = when (item.status) {
                DownloadManager.STATUS_SUCCESSFUL -> "已完成"
                DownloadManager.STATUS_PENDING -> "等待下载"
                DownloadManager.STATUS_PAUSED -> "等待继续"
                DownloadManager.STATUS_RUNNING -> if (item.total > 0) "下载中 · ${(fraction*100).toInt()}%" else "下载中"
                else -> "下载失败 · 点按重试"
            }
            Column {
                LiquidKitListCell(title = item.title, subtitle = status+" · "+Formatter.formatShortFileSize(a,item.received.coerceAtLeast(0)),
                    onClick = when {
                        ready -> ({ store.open(item) })
                        item.status == DownloadManager.STATUS_FAILED -> ({ a.download(a.page,item.url,null,item.mime,item) })
                        else -> null
                    },
                    leading = { LiquidKitIcon(if (ready) Icon.Document else Icon.Download, Accent.current) },
                    trailing = {
                        if (item.active) Tool(Icon.Close,"取消下载 "+item.title) { store.cancel(secret,item) }
                        else Tool(Icon.Close,"移除记录 "+item.title) { store.forget(secret,setOf(item.id)) }
                    })
                if (item.active && item.total > 0) {
                    LiquidKitProgressIndicator(fraction, Accent.current, Modifier.fillMaxWidth().padding(horizontal=16.dp))
                }
            }
        }
        item { Action(Icon.Download,"打开系统下载目录",click = store::openDirectory) }
        val ended = entries.filterNot { it.active }
        if (ended.isNotEmpty()) item {
            Action(Icon.Trash,"清除已结束的记录","下载文件会保留") { store.forget(secret,ended.map { it.id }.toSet()) }
        }
    }
}

