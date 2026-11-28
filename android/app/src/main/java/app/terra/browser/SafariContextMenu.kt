package app.terra.browser

import android.net.Uri
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private fun symbol(label: String) = when (label) {
    "按标题排列" -> LiquidKitSymbol.Document
    "按网站排列" -> LiquidKitSymbol.Globe
    "打开" -> LiquidKitSymbol.Forward
    "在新窗口中打开", "在新标签中打开", "在后台打开", "在新标签中查看图片" -> LiquidKitSymbol.SquareOnSquare
    "移至标签组" -> LiquidKitSymbol.SquareOnSquare
    "新建标签页" -> LiquidKitSymbol.Add
    "所有标签页" -> LiquidKitSymbol.SquareOnSquare
    "书签" -> LiquidKitSymbol.Bookmark
    "标签栏布局" -> LiquidKitSymbol.SliderHorizontal
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
    "下载", "下载图片", "下载项目" -> LiquidKitSymbol.Download
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
    val hasPreview = menu.previewUrl != null
    val shade = remember { Animatable(0f) }
    val labelFocus = remember { Animatable(0f) }
    var laidOut by remember { mutableStateOf(false) }
    var loadPreview by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    var openingLink by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<(() -> Unit)?>(null) }
    DisposableEffect(menu) {
        onDispose { (menu.source?.parent as? TabViewport)?.linkCardClosed(menu) }
    }
    fun close(action: (() -> Unit)?) {
        if (!closing) { selected = action; closing = true }
    }
    LaunchedEffect(closing, laidOut) {
        if (hasPreview) {
            if (!closing && !laidOut) return@LaunchedEffect
            if (!closing) withFrameNanos { }
            val committing = openingLink || selected != null
            val target = if (closing) 0f else 1f
            coroutineScope {
                if (!closing) launch {
                    // Constructing a Chromium renderer can take several display frames.
                    // Let the link lift and the glass settle before mounting it.
                    snapshotFlow { progress.value }.first { it >= .96f }
                    loadPreview = true
                }
                launch {
                    val duration = if (!closing) 310 else if (committing) 155 else 275
                    val curve = if (closing) CubicBezierEasing(.27f,.08f,.26f,1f)
                        else CubicBezierEasing(.20f,.43f,.16f,1f)
                    shade.animateTo(target, tween(duration, easing = curve))
                }
                launch { labelFocus.animateTo(target, tween(if (closing) 65 else 150)) }
                if (closing) {
                    progress.animateTo(0f, tween(if (committing) 95 else 165, delayMillis=14,
                        easing=CubicBezierEasing(.32f,.04f,.23f,1f)))
                    if (openingLink) (menu.source?.parent as? TabViewport)?.linkCardClosed(menu)
                } else progress.animateTo(1f, spring(dampingRatio=.79f, stiffness=655f))
            }
        } else if (closing) progress.animateTo(0f, tween(135))
        else progress.animateTo(1f, spring(dampingRatio = .84f, stiffness = 440f))
        if (closing) {
            dismiss()
            if (menu.isCurrent()) selected?.invoke()
        }
    }
    val ink = if (dark) Color.White else Color(0xff18181b)
    val surface = if (dark && hasPreview) Color(0xff1c1c1e) else if (dark) Color(0xff242426) else Color(0xfff7f7f9)
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
        var previewPane by remember { mutableStateOf<LayoutCoordinates?>(null) }
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
            val stop = touches.track { point, released ->
                val origin = touches.pressOrigin
                if (!released && point != null && origin != null && lastRow < 0 &&
                    (point-origin).getDistance() > touchSlop * 3 && contains(previewPane,point) &&
                    menu.startLinkDrag?.invoke() == true) {
                    touches.stopTracking()
                    dismiss()
                } else handleDrag(point,released)
            }
            onDispose(stop)
        }
        val canPressRow by rememberUpdatedState<(Offset) -> Boolean> { point ->
            !closing && scroll.maxValue == 0 && rows.values.any { contains(it,point) }
        }
        val selectAcrossRows = if (!hasPreview) Modifier else Modifier.pointerInput(menu) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed=false, pass=PointerEventPass.Initial)
                fun screenPoint(point: Offset) = viewport?.takeIf { it.isAttached }?.localToScreen(point)
                val start = screenPoint(down.position) ?: return@awaitEachGesture
                if (!canPressRow(start)) return@awaitEachGesture
                down.consume()
                handleDrag(start,false)
                try {
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val finger = event.changes.singleOrNull() ?: break
                        if (finger.id != down.id || finger.isConsumed) break
                        val position = screenPoint(finger.position) ?: break
                        finger.consume()
                        handleDrag(position,!finger.pressed)
                    } while (finger.pressed)
                } finally {
                    heldPoint = null
                    hovered = -1
                }
            }
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
        DisposableEffect(window,hasPreview) {
            window?.setDimAmount(if (hasPreview) 0f else .18f)
            if (hasPreview) window?.setWindowAnimations(0)
            onDispose { }
        }
        Box(Modifier.fillMaxSize().onGloballyPositioned { if (hasPreview && !laidOut) laidOut = true }) {
            Box(Modifier.fillMaxSize().drawBehind {
                if (hasPreview) drawRect(Color.Black.copy(alpha=.48f*shade.value.coerceIn(0f,1f)))
            }.clickable(remember { MutableInteractionSource() }, null) { close(null) })
            BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().padding(10.dp)) {
                val metrics = remember(maxWidth, density.fontScale) { LiquidMenuMetrics(maxWidth, density.fontScale) }
                var origin by remember { mutableStateOf(Offset.Zero) }
                val anchorX = with(density) { (menu.x - origin.x).toDp() }
                val anchorY = with(density) { (menu.y - origin.y).toDp() }
                val wide = hasPreview && maxWidth >= 565.dp
                val portraitPad = wide && maxHeight > maxWidth
                val rowHeight = if (portraitPad && density.fontScale <= 1.05f) 38.dp else metrics.row
                val menuWidth = if (portraitPad) minOf(metrics.width, 236.dp) else minOf(maxWidth, metrics.width)
                val gap = when { portraitPad -> 20.dp; hasPreview -> 8.dp; else -> 0.dp }
                val sideInset = if (portraitPad) 10.dp else 0.dp
                val previewWidth = if (wide) minOf(if (portraitPad) 342.dp else 350.dp,
                    maxWidth-menuWidth-gap-sideInset*2) else minOf(maxWidth, 320.dp)
                val headerHeight = if (portraitPad) 44.dp else 48.dp
                val previewHeight = when {
                    !hasPreview -> 0.dp
                    !activity.showPreviews -> headerHeight
                    portraitPad -> minOf(maxHeight, previewWidth * (476f/342f))
                    wide -> minOf(maxHeight, 365.dp)
                    else -> (maxHeight * .34f).coerceAtMost(275.dp)
                }
                val availableHeight = if (wide) maxHeight else (maxHeight-previewHeight-gap).coerceAtLeast(1.dp)
                fun listHeight(count: Int) = minOf(rowHeight * count + metrics.inset * 2, availableHeight)
                val panelHeight by animateDpAsState(listHeight(entries.size),
                    LocalLiquidKitTokens.current.menu.touchFollow.dpSpec, label="子菜单高度")
                val actionHeight = panelHeight.coerceIn(1.dp,availableHeight.coerceAtLeast(1.dp))
                val width = if (wide) previewWidth + gap + menuWidth else maxOf(menuWidth, if (hasPreview) previewWidth else 0.dp)
                val height = if (wide) maxOf(previewHeight, actionHeight) else previewHeight + gap + actionHeight
                val rootHeight = if (wide) maxOf(previewHeight, listHeight(menu.items.size)) else previewHeight + gap + listHeight(menu.items.size)
                val left = (anchorX - if (portraitPad) previewWidth/2 else width*.25f)
                    .coerceIn(sideInset,(maxWidth-width-sideInset).coerceAtLeast(sideInset))
                val bottomInset = if (portraitPad) with(density) {
                    (34.dp-WindowInsets.safeDrawing.getBottom(this).toDp()).coerceAtLeast(0.dp)
                } else 0.dp
                val rootTop = (anchorY-rootHeight*(if (portraitPad) .5f else .35f))
                    .coerceIn(0.dp,(maxHeight-rootHeight-bottomInset).coerceAtLeast(0.dp))
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
                        val startScale = if (hasPreview) .16f else .68f
                        scaleX = startScale + (1f-startScale)*progress.value
                        scaleY = scaleX
                        alpha = (progress.value*(if (hasPreview) 1.7f else 1f)).coerceIn(0f,1f)
                    }) {
                        if (hasPreview) {
                            Column(Modifier.size(previewWidth,previewHeight).onGloballyPositioned { previewPane = it }
                                .clip(RoundedCornerShape(18.dp)).background(surface)) {
                                Row(Modifier.fillMaxWidth().height(headerHeight).padding(start=12.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.weight(1f)) { MenuText(Uri.parse(menu.previewUrl).host.orEmpty(), ink, 13) }
                                    Box(Modifier.clickable(role = Role.Button) { activity.togglePreviews() }.padding(12.dp)) {
                                        MenuText(if (activity.showPreviews) "隐藏预览" else "显示预览", ink, 13)
                                    }
                                }
                                if (activity.showPreviews) Box(Modifier.fillMaxSize()) {
                                    if (loadPreview) LinkPreview(menu,surface)
                                    Box(Modifier.fillMaxSize().clickable(role = Role.Button, onClickLabel = "打开预览") {
                                        if (!closing && menu.isCurrent()) {
                                            openingLink = true
                                            close(null)
                                            activity.openLinkPreview(menu)
                                        }
                                    })
                                }
                            }
                        }
                        DialogGlassSurface(capture, Modifier.offset(
                            if (wide) previewWidth + gap else 0.dp,
                            if (wide) 0.dp else previewHeight + gap
                        ).size(menuWidth,actionHeight), shape = RoundedCornerShape(metrics.radius),
                            tint = surface.copy(alpha=if (hasPreview && dark) .46f else .28f),
                            panelBlur=if (hasPreview) 14.dp else 0.dp) {
                            val tokens = LocalLiquidKitTokens.current.menu
                            val reveal = remember(branch) { Animatable(if (branch == null) 1f else 0f) }
                            LaunchedEffect(branch) { reveal.animateTo(1f, tokens.touchFollow.spec) }
                            val light = animateFloatAsState(if (hovered < 0) 0f else 1f,
                                tokens.touchLight.spec, label="菜单高亮")
                            val rowTop = if (lastRow < 0) null else animateDpAsState(metrics.inset + rowHeight * lastRow,
                                tokens.touchFollow.dpSpec, label="菜单选项跟随")
                            Column(Modifier.fillMaxSize().onGloballyPositioned { viewport = it }
                                .graphicsLayer {
                                    val focus = if (hasPreview) labelFocus.value else 1f
                                    alpha = reveal.value.coerceIn(0f,1f) * (.20f+.80f*focus)
                                    translationX = (1f-reveal.value)*18.dp.toPx()
                                    val radius = (1f-focus)*3.dp.toPx()
                                    renderEffect = if (radius>.2f && android.os.Build.VERSION.SDK_INT>=31)
                                        BlurEffect(radius,radius,TileMode.Decal) else null
                                }.then(selectAcrossRows).verticalScroll(scroll).drawBehind {
                                    if (rowTop != null) {
                                        val inset = metrics.inset.toPx()
                                        val height = rowHeight.toPx()
                                        val start = Offset(inset,rowTop.value.toPx())
                                        val bounds = Size((size.width-2*inset).coerceAtLeast(0f),height)
                                        val radius = CornerRadius(height/2)
                                        val intensity = light.value.coerceIn(0f,1f)
                                        drawRoundRect(ink.copy(alpha=intensity * (if (dark) .15f else .11f)), start,bounds,radius)
                                        if (hasPreview && intensity > 0f) {
                                            val local = heldPoint?.let { viewport?.takeIf { v -> v.isAttached }?.screenToLocal(it) }
                                            val shine = Brush.radialGradient(
                                                listOf(Color.White.copy(alpha=.64f*intensity),Color.White.copy(alpha=.12f*intensity)),
                                                Offset(local?.x ?: size.width/2,start.y+height/2),size.width*.74f)
                                            drawRoundRect(shine,start,bounds,radius)
                                        }
                                    }
                                }.padding(metrics.inset)) {
                                entries.forEachIndexed { index, entry ->
                                    val label = entry.label
                                    Row(Modifier.fillMaxWidth().height(rowHeight).onGloballyPositioned { rows[index] = it }
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
private fun LinkPreview(menu: ContextMenu, surface: Color) {
    val url = menu.previewUrl ?: return
    var painted by remember(menu) { mutableStateOf(false) }
    var progress by remember(menu) { mutableIntStateOf(0) }
    val cover = animateFloatAsState(if (painted) 0f else 1f, tween(110), label="预览网页显现")
    Box(Modifier.fillMaxSize()) {
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
                    override fun onPageCommitVisible(view: WebView, url: String) { painted = true }
                    override fun onPageFinished(view: WebView, url: String) { painted = true }
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) =
                        !httpAddress(request.url.toString())
                }
                webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView, value: Int) { progress = value }
                }
                setDownloadListener { _, _, _, _, _ -> }
                loadUrl(url)
            }
        }, onRelease = {
            it.webChromeClient = null
            it.webViewClient = WebViewClient()
            it.stopLoading()
            it.destroy()
        }, update = {})
        Spacer(Modifier.matchParentSize().drawBehind {
            if (cover.value > 0f) drawRect(surface.copy(alpha=cover.value))
        })
        if (progress in 1..99) Spacer(Modifier.align(Alignment.TopStart)
            .fillMaxWidth(progress/100f).height(2.dp).background(Color(0xff3797ff)))
    }
}
