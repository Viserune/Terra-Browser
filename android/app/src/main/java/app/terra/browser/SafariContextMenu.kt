package app.terra.browser

import android.net.Uri
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.window.DialogProperties
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import io.liquidkit.LiquidKitIcon
import io.liquidkit.LiquidKitSymbol
import io.liquidkit.LocalLiquidKitTokens
import com.kyant.backdrop.components.LiquidMenuMetrics

private fun symbol(label: String) = when (label) {
    "按标题排列" -> LiquidKitSymbol.Document
    "按网站排列" -> LiquidKitSymbol.Globe
    "打开" -> LiquidKitSymbol.Forward
    "在新窗口中打开", "在新标签中打开", "在后台打开", "在新标签中查看图片" -> LiquidKitSymbol.SquareOnSquare
    "移至标签组" -> LiquidKitSymbol.SquareOnSquare
    "新建标签页" -> LiquidKitSymbol.Add
    "新建无痕标签页" -> LiquidKitSymbol.Moon
    "关闭窗口", "关闭标签页" -> LiquidKitSymbol.Close
    "添加书签" -> LiquidKitSymbol.Bookmark
    "编辑书签" -> LiquidKitSymbol.Rename
    "删除", "删除书签" -> LiquidKitSymbol.Trash
    "标记为已读" -> LiquidKitSymbol.Check
    "标记为未读" -> LiquidKitSymbol.Document
    "添加到阅读列表" -> LiquidKitSymbol.Document
    "复制地址", "复制图片地址", "复制网址" -> LiquidKitSymbol.Clipboard
    "编辑地址" -> LiquidKitSymbol.Rename
    "粘贴并前往" -> LiquidKitSymbol.Forward
    "下载", "下载图片" -> LiquidKitSymbol.Download
    "共享", "共享图片地址" -> LiquidKitSymbol.Share
    else -> LiquidKitSymbol.Clock
}

@Composable
private fun MenuText(text: String, color: Color, size: Int) {
    BasicText(text, style = TextStyle(color = color, fontSize = size.sp),
        maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
internal fun SafariContextMenu(activity: MainActivity, menu: ContextMenu, dark: Boolean,
    touches: MenuTouchSource = activity.menuTouches,
    capture: WebGlassCapture? = null,
    dismiss: () -> Unit = { if (activity.contextMenu === menu) activity.contextMenu = null }) {
    val progress = remember { Animatable(0f) }
    var closing by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<(() -> Unit)?>(null) }
    fun close(action: (() -> Unit)?) {
        if (!closing) { selected = action; closing = true }
    }
    LaunchedEffect(closing) {
        if (closing) {
            progress.animateTo(0f, tween(135))
            dismiss()
            if (menu.isCurrent()) selected?.invoke()
        } else progress.animateTo(1f, spring(dampingRatio = .84f, stiffness = 440f))
    }
    val ink = if (dark) Color.White else Color(0xff18181b)
    val surface = if (dark) Color(0xff242426) else Color(0xfff7f7f9)
    var branch by remember(menu) { mutableStateOf<GroupDestinations?>(null) }
    var naming by remember(menu) { mutableStateOf(false) }
    val entries = remember(branch, menu) {
        branch?.let { listOf(ParentMenu) + it.groups + NewLinkedGroup } ?: menu.items
    }
    Dialog(onDismissRequest = { if (branch != null) branch = null else close(null) }, properties = DialogProperties(
        usePlatformDefaultWidth = false, decorFitsSystemWindows = false
    )) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        val view = LocalView.current
        val rows = remember(entries) { mutableMapOf<Int, LayoutCoordinates>() }
        var viewport by remember { mutableStateOf<LayoutCoordinates?>(null) }
        var hovered by remember(entries) { mutableIntStateOf(-1) }
        var lastRow by remember(entries) { mutableIntStateOf(-1) }
        var heldPoint by remember { mutableStateOf<Offset?>(null) }
        var rearmFrom by remember { mutableStateOf<Offset?>(null) }
        val scroll = key(entries) { rememberScrollState() }
        val touchSlop = androidx.compose.ui.platform.LocalViewConfiguration.current.touchSlop
        fun activate(entry: MenuEntry?) {
            when (entry) {
                is GroupDestinations -> branch = entry
                ParentMenu -> branch = null
                NewLinkedGroup -> naming = true
                is MenuAction -> close(entry.run)
                null -> close(null)
            }
        }
        fun contains(c: LayoutCoordinates?, point: Offset): Boolean {
            if (c == null || !c.isAttached) return false
            val local = c.screenToLocal(point)
            return local.x >= 0 && local.y >= 0 && local.x < c.size.width && local.y < c.size.height
        }
        fun highlight(point: Offset?): Int {
            val next = if (point != null && contains(viewport, point))
                rows.entries.firstOrNull { contains(it.value, point) }?.key ?: -1 else -1
            if (next != hovered) {
                hovered = next
                if (next >= 0) {
                    lastRow = next
                    view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                }
            }
            return next
        }
        fun leavingCardMenu(point: Offset): Boolean {
            val bounds = viewport?.takeIf { it.isAttached } ?: return false
            val start = touches.pressOrigin ?: return false
            fun distance(p: Offset): Float {
                val local = bounds.screenToLocal(p)
                val nearest = Offset(local.x.coerceIn(0f, bounds.size.width.toFloat()),
                    local.y.coerceIn(0f, bounds.size.height.toFloat()))
                return (local-nearest).getDistance()
            }
            val gap = distance(point)
            return gap > touchSlop && (lastRow >= 0 || gap + touchSlop >= distance(start))
        }
        val handleDrag by rememberUpdatedState<(Offset?, Boolean) -> Unit> { point, released ->
            if (point != null && !released && menu.dragAway != null && leavingCardMenu(point) &&
                menu.dragAway?.invoke(point) == true) {
                dismiss()
                return@rememberUpdatedState
            }
            heldPoint = if (released) null else point
            val origin = rearmFrom
            if (origin == null || point == null || (point-origin).getDistance() > touchSlop) {
                rearmFrom = null
                val index = highlight(point)
                if (released) activate(entries.getOrNull(index))
            }
            if (released) rearmFrom = null
        }
        DisposableEffect(menu, touches) {
            val stop = touches.track { point, released -> handleDrag(point,released) }
            onDispose(stop)
        }
        val disclosure = entries.getOrNull(hovered) as? GroupDestinations
        LaunchedEffect(disclosure, heldPoint != null) {
            if (disclosure != null && heldPoint != null) {
                kotlinx.coroutines.delay(420)
                rearmFrom = heldPoint
                branch = disclosure
            }
        }
        val density = LocalDensity.current
        LaunchedEffect(heldPoint, scroll) {
            val point = heldPoint?.takeIf { rearmFrom == null } ?: return@LaunchedEffect
            val edge = with(density) { 24.dp.toPx() }
            val speed = with(density) { 250.dp.toPx() }
            var previous = withFrameNanos { it }
            while (true) {
                val bounds = viewport ?: break
                if (!contains(bounds, point)) break
                val y = bounds.screenToLocal(point).y
                val strength = ((y - bounds.size.height + edge) / edge).coerceIn(0f,1f) -
                    ((edge-y) / edge).coerceIn(0f,1f)
                if (strength == 0f) break
                val now = withFrameNanos { it }
                val seconds = ((now - previous) / 1_000_000_000f).coerceAtMost(.05f)
                previous = now
                highlight(point)
                if (scroll.dispatchRawDelta(speed * strength * seconds) == 0f) break
            }
        }
        SideEffect { window?.setDimAmount(.18f) }
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().clickable(remember { MutableInteractionSource() }, null) { close(null) })
            BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().padding(10.dp)) {
                val metrics = remember(maxWidth, density.fontScale) { LiquidMenuMetrics(maxWidth, density.fontScale) }
                var origin by remember { mutableStateOf(Offset.Zero) }
                val anchorX = with(density) { (menu.x - origin.x).toDp() }
                val anchorY = with(density) { (menu.y - origin.y).toDp() }
                val hasPreview = menu.previewUrl != null
                val wide = hasPreview && maxWidth >= 565.dp
                val portraitPad = wide && maxHeight > maxWidth
                val menuWidth = if (portraitPad) minOf(metrics.width, 236.dp) else minOf(maxWidth, metrics.width)
                val gap = when { portraitPad -> 28.dp; hasPreview -> 8.dp; else -> 0.dp }
                val sideInset = if (portraitPad) 10.dp else 0.dp
                val previewWidth = if (wide) minOf(if (portraitPad) 335.dp else 350.dp,
                    maxWidth-menuWidth-gap-sideInset*2) else minOf(maxWidth, 320.dp)
                val headerHeight = if (portraitPad) 44.dp else 48.dp
                val previewHeight = when {
                    !hasPreview -> 0.dp
                    !activity.showPreviews -> headerHeight
                    portraitPad -> minOf(maxHeight, previewWidth * (476f/335f))
                    wide -> minOf(maxHeight, 365.dp)
                    else -> (maxHeight * .34f).coerceAtMost(275.dp)
                }
                val availableHeight = if (wide) maxHeight else (maxHeight-previewHeight-gap).coerceAtLeast(1.dp)
                fun listHeight(count: Int) = minOf(metrics.row * count + metrics.inset * 2, availableHeight)
                val panelHeight by animateDpAsState(listHeight(entries.size),
                    LocalLiquidKitTokens.current.menu.touchFollow.dpSpec, label="子菜单高度")
                val actionHeight = panelHeight.coerceIn(1.dp,availableHeight.coerceAtLeast(1.dp))
                val width = if (wide) previewWidth + gap + menuWidth else maxOf(menuWidth, if (hasPreview) previewWidth else 0.dp)
                val height = if (wide) maxOf(previewHeight, actionHeight) else previewHeight + gap + actionHeight
                val rootHeight = if (wide) maxOf(previewHeight, listHeight(menu.items.size)) else previewHeight + gap + listHeight(menu.items.size)
                val left = (anchorX-width*.25f).coerceIn(sideInset,(maxWidth-width-sideInset).coerceAtLeast(sideInset))
                val rootTop = (anchorY-rootHeight*.35f).coerceIn(0.dp,(maxHeight-rootHeight).coerceAtLeast(0.dp))
                val preferredTop = when {
                    hasPreview -> rootTop
                    anchorY < actionHeight + 8.dp && maxHeight - anchorY >= actionHeight + 8.dp -> anchorY + 8.dp
                    else -> anchorY - actionHeight - 8.dp
                }
                val top = preferredTop.coerceIn(0.dp,(maxHeight-height).coerceAtLeast(0.dp))
                Box(Modifier.fillMaxSize().onGloballyPositioned { origin = it.localToScreen(Offset.Zero) }) {
                    Box(Modifier.offset(left, top).size(width,height).graphicsLayer {
                        transformOrigin = TransformOrigin(((anchorX-left)/width).coerceIn(0f,1f),
                            ((anchorY-top)/height).coerceIn(0f,1f))
                        scaleX = .68f + .32f*progress.value
                        scaleY = scaleX
                        alpha = progress.value.coerceIn(0f,1f)
                    }) {
                        if (hasPreview) {
                            Column(Modifier.size(previewWidth,previewHeight).clip(RoundedCornerShape(18.dp)).background(surface)) {
                                Row(Modifier.fillMaxWidth().height(headerHeight).padding(start=12.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.weight(1f)) { MenuText(Uri.parse(menu.previewUrl).host.orEmpty(), ink, 13) }
                                    Box(Modifier.clickable(role = Role.Button) { activity.togglePreviews() }.padding(12.dp)) {
                                        MenuText(if (activity.showPreviews) "隐藏预览" else "显示预览", ink, 13)
                                    }
                                }
                                if (activity.showPreviews) Box(Modifier.fillMaxSize()) {
                                    LinkPreview(menu)
                                    Box(Modifier.fillMaxSize().clickable(role = Role.Button, onClickLabel = "打开预览") {
                                        close { activity.navigate(menu.previewUrl!!) }
                                    })
                                }
                            }
                        }
                        DialogGlassSurface(capture, Modifier.offset(
                            if (wide) previewWidth + gap else 0.dp,
                            if (wide) 0.dp else previewHeight + gap
                        ).size(menuWidth,actionHeight), shape = RoundedCornerShape(metrics.radius), tint = surface.copy(alpha=.28f)) {
                            val tokens = LocalLiquidKitTokens.current.menu
                            val reveal = remember(branch) { Animatable(if (branch == null) 1f else 0f) }
                            LaunchedEffect(branch) { reveal.animateTo(1f, tokens.touchFollow.spec) }
                            val light = animateFloatAsState(if (hovered < 0) 0f else if (dark) .15f else .11f,
                                tokens.touchLight.spec, label="菜单高亮")
                            val rowTop = if (lastRow < 0) null else animateDpAsState(metrics.inset + metrics.row * lastRow,
                                tokens.touchFollow.dpSpec, label="菜单选项跟随")
                            Column(Modifier.fillMaxSize().onGloballyPositioned { viewport = it }
                                .graphicsLayer {
                                    alpha = reveal.value.coerceIn(0f,1f)
                                    translationX = (1f-reveal.value)*18.dp.toPx()
                                }.verticalScroll(scroll).drawBehind {
                                    if (rowTop != null) {
                                        val inset = metrics.inset.toPx()
                                        val height = metrics.row.toPx()
                                        drawRoundRect(ink.copy(alpha=light.value), Offset(inset,rowTop.value.toPx()),
                                            Size((size.width-2*inset).coerceAtLeast(0f),height),CornerRadius(height/2))
                                    }
                                }.padding(metrics.inset)) {
                                entries.forEachIndexed { index, entry ->
                                    val label = entry.label
                                    Row(Modifier.fillMaxWidth().height(metrics.row).onGloballyPositioned { rows[index] = it }
                                        .clickable(role = Role.Button) { activate(entry) }
                                        .padding(horizontal=14.dp), verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        if (entry === ParentMenu) LiquidKitIcon(LiquidKitSymbol.Back,ink,Modifier.size(19.dp))
                                        else if (entry === NewLinkedGroup) LiquidKitIcon(LiquidKitSymbol.Add,ink,Modifier.size(19.dp))
                                        else if (branch != null || entry is GroupDestinations) LiquidKitIcon(LiquidKitSymbol.SquareOnSquare,ink,Modifier.size(19.dp))
                                        else if (label == "固定标签页" || label == "取消固定") PinIcon(ink, Modifier.size(19.dp))
                                        else LiquidKitIcon(symbol(label), ink, Modifier.size(19.dp))
                                        Box(Modifier.weight(1f)) { MenuText(label, ink, 16) }
                                        if (entry is GroupDestinations) LiquidKitIcon(LiquidKitSymbol.Forward,ink,Modifier.size(15.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (naming) TabGroupPicker(activity, "", null, dark, onSelect={}, dismiss={ naming=false }, createForLink={ name ->
            val destination = branch
            naming = false
            close { destination?.create?.invoke(name) }
        })
    }
}

@Composable
private fun LinkPreview(menu: ContextMenu) {
    val url = menu.previewUrl ?: return
    AndroidView(modifier = Modifier.fillMaxSize(), factory = { context ->
        WebView(context).apply {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
                menu.source?.let { WebViewCompat.setProfile(this, WebViewCompat.getProfile(it).name) }
            }
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            isFocusable = false
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) =
                    !httpAddress(request.url.toString())
            }
            setDownloadListener { _, _, _, _, _ -> }
            loadUrl(url)
        }
    }, onRelease = { it.stopLoading(); it.destroy() }, update = {})
}
