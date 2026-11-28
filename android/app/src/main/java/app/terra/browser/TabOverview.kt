package app.terra.browser

import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.*
import androidx.core.view.WindowCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import io.liquidkit.*
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt

private data class OverviewCard(val tab: Page, val image: State<ImageBitmap?>, val rect: Rect)
private class OverviewFlight(val card: OverviewCard, val openingPage: Boolean) {
    private val drawn=CompletableDeferred<Unit>()
    private val committed=CompletableDeferred<Unit>()

    fun presentedBy(view: View) {
        if (!drawn.complete(Unit)) return
        val observer=view.viewTreeObserver
        if (view.isHardwareAccelerated && observer.isAlive) {
            observer.registerFrameCommitCallback { committed.complete(Unit) }
            view.postInvalidateOnAnimation()
        } else committed.complete(Unit)
    }

    suspend fun awaitSourceFrame() {
        drawn.await()
        withTimeoutOrNull(300) { committed.await() }
    }
}

@Composable
private fun overviewImage(a: MainActivity, tab: Page, current: Boolean): State<ImageBitmap?> {
    val url = tab.url
    return key(tab, url) {
        val initial = remember {
            val live = if (current) a.previews.snapshot(tab) else null
            (live ?: a.previews.cached(tab))?.asImageBitmap()
        }
        produceState(initial, tab, url) {
            if (value == null && httpAddress(url)) {
                value = (a.previews.restore(tab) ?: a.previews.snapshot(tab))?.asImageBitmap()
            }
        }
    }
}

private suspend fun captureBrowserFrame(a: MainActivity, contentOnly: Boolean): ImageBitmap? {
    val root = a.window.decorView
    if (root.width == 0 || root.height == 0 || !root.isAttachedToWindow) return null
    val bars = if (contentOnly) ViewCompat.getRootWindowInsets(root)?.getInsets(
        WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()) else null
    val source = android.graphics.Rect(bars?.left ?: 0,bars?.top ?: 0,
        root.width-(bars?.right ?: 0),root.height-(bars?.bottom ?: 0))
    if (source.isEmpty) return null
    val scale = minOf(1f, 640f / source.width(), 1000f / source.height())
    val image = Bitmap.createBitmap((source.width()*scale).roundToInt().coerceAtLeast(1),
        (source.height()*scale).roundToInt().coerceAtLeast(1),Bitmap.Config.ARGB_8888)
    return withTimeoutOrNull(250) {
        suspendCancellableCoroutine { continuation ->
            try {
                PixelCopy.request(a.window,source,image,{ status ->
                    if (continuation.isActive) continuation.resume(if(status == PixelCopy.SUCCESS) image.asImageBitmap() else null)
                },Handler(Looper.getMainLooper()))
            } catch (_: IllegalArgumentException) { continuation.resume(null) }
        }
    }
}

private fun title(tab: Page) = tab.title.ifBlank { Uri.parse(tab.url).host ?: if (tab.secret) "无痕起始页" else "起始页" }

@Composable
private fun OverviewText(text: String, color: Color, size: Int = 15, bold: Boolean = false) {
    BasicText(text, style = TextStyle(color=color,fontSize=size.sp,
        fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal),
        maxLines=1,overflow=TextOverflow.Ellipsis)
}

@Composable
private fun OverviewHeading(label: String, ink: Color, progress: () -> Float) {
    val target = progress()
    val pull by animateFloatAsState(target, if (target == 0f) spring(stiffness=520f) else snap(), label="下拉搜索")
    Box(contentAlignment=Alignment.CenterStart) {
        Box(Modifier.graphicsLayer { alpha=1f-pull; translationY=-8.dp.toPx()*pull }) {
            OverviewText(label,ink,23,true)
        }
        if (pull > 0f) Row(Modifier.graphicsLayer {
            alpha=pull
            translationY=(1f-pull)*12.dp.toPx()
        },verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            LiquidKitIcon(LiquidKitSymbol.Search,ink,Modifier.size(18.dp))
            OverviewText("搜索标签页",ink,18)
        }
    }
}

@Composable
internal fun tabMenuGesture(role: Role, open: () -> Unit, menuLabel: String = "显示标签页菜单", hold: (Int, Int) -> Unit): Modifier {
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    return Modifier.onGloballyPositioned { coordinates = it }.combinedClickable(role=role,
        onLongClickLabel=menuLabel, onLongClick={
            coordinates?.takeIf { it.isAttached }?.let {
                val point = it.localToScreen(Offset(it.size.width / 2f, it.size.height / 2f))
                hold(point.x.roundToInt(), point.y.roundToInt())
            }
        }, onClick=open)
}

@Composable
internal fun PinIcon(color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val unit = size.minDimension / 24f
        drawContext.transform.rotate(35f)
        val body = Path().apply {
            moveTo(7*unit, 3*unit)
            lineTo(17*unit, 3*unit)
            lineTo(15*unit, 6*unit)
            lineTo(15*unit, 11*unit)
            lineTo(18*unit, 15*unit)
            lineTo(6*unit, 15*unit)
            lineTo(9*unit, 11*unit)
            lineTo(9*unit, 6*unit)
            close()
        }
        drawPath(body, color)
        drawLine(color, Offset(12*unit,14*unit), Offset(12*unit,21*unit), 1.8f*unit, StrokeCap.Round)
    }
}

@Composable
private fun overviewOptions(show: (Int, Int) -> Unit): Modifier {
    var anchor by remember { mutableStateOf<LayoutCoordinates?>(null) }
    return Modifier.onGloballyPositioned { anchor = it }
        .clickable(role = Role.Button) {
            anchor?.takeIf { it.isAttached }?.let {
                val point = it.localToScreen(Offset(it.size.width / 2f, it.size.height.toFloat()))
                show(point.x.roundToInt(), point.y.roundToInt())
            }
        }.semantics { contentDescription = "排列标签页" }
}

@Composable
internal fun PageFace(tab: Page, image: ImageBitmap?, surface: Color, ink: Color) {
    Box(Modifier.fillMaxSize().background(surface),contentAlignment=Alignment.Center) {
        if (image != null) Image(image,null,Modifier.fillMaxSize(),alignment=Alignment.TopCenter,contentScale=ContentScale.Crop)
        else Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(8.dp)) {
            LiquidKitIcon(if (tab.secret) LiquidKitSymbol.Moon else LiquidKitSymbol.Globe,ink)
            OverviewText(title(tab),ink,14)
        }
    }
}

@Composable
private fun swipeToClose(tab: Page, close: () -> Unit): Modifier {
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    var dragging by remember { mutableStateOf(false) }
    var distance by remember { mutableFloatStateOf(0f) }
    var settle by remember { mutableStateOf<Job?>(null) }
    val onClose by rememberUpdatedState(close)
    return Modifier.pointerInput(tab.id) {
        val speed = VelocityTracker()
        var lastMove = 0L
        fun finish(commit: Boolean, velocity: Float = 0f) {
            settle = scope.launch {
                offset.snapTo(distance)
                dragging = false
                offset.animateTo(if (commit) -size.width.toFloat() else 0f,
                    spring(dampingRatio = 1f, stiffness = if (commit) 880f else 580f), initialVelocity = velocity)
                if (commit) onClose()
            }
        }
        detectHorizontalDragGestures(onDragStart = {
            settle?.cancel()
            speed.resetTracking()
            distance = offset.value
            dragging = true
        }, onDragCancel = { finish(false) }, onDragEnd = {
            val velocity = if (android.os.SystemClock.uptimeMillis() - lastMove <= 100)
                speed.calculateVelocity().x.coerceIn(-3600.dp.toPx(), 3600.dp.toPx()) else 0f
            val quick = distance < -28.dp.toPx() && velocity < -800.dp.toPx()
            finish(velocity < 600.dp.toPx() && (-distance > size.width*.43f || quick), velocity)
        }) { change, delta ->
            change.consume()
            lastMove = change.uptimeMillis
            speed.addPointerInputChange(change)
            distance = (distance + delta).coerceIn(-size.width.toFloat(),0f)
        }
    }.graphicsLayer {
        translationX = (if (dragging) distance else offset.value).coerceIn(-size.width, 0f)
        alpha = (1f + translationX / size.width.coerceAtLeast(1f) * .65f).coerceIn(.35f,1f)
    }
}

@Composable
private fun CardHeader(tab: Page, ink: Color, modifier: Modifier = Modifier, close: (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().height(44.dp).padding(start=12.dp), verticalAlignment=Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) { OverviewText(title(tab), ink, 14, true) }
        val action = if (close != null && !tab.pinned) Modifier.clickable(role=Role.Button, onClick=close)
            .semantics { contentDescription="关闭标签页" } else Modifier
        Box(Modifier.size(44.dp).then(action), contentAlignment=Alignment.Center) {
            if (tab.pinned) PinIcon(ink, Modifier.size(16.dp))
            else LiquidKitIcon(LiquidKitSymbol.Close, ink, Modifier.size(15.dp))
        }
    }
}

@Composable
private fun DraggedTab(controller: GridTabReorder, drag: LiftedTab, surface: Color, ink: Color, tablet: Boolean, aspect: Float) {
    val density = LocalDensity.current
    val landed = remember(drag) { Animatable(0f) }
    val lift = remember(drag) { Animatable(0f) }
    LaunchedEffect(drag) { lift.animateTo(1f, spring(dampingRatio=.85f, stiffness=600f)) }
    val target = drag.landing
    LaunchedEffect(drag, target) {
        if (target == null) with(density) { controller.scrollAtEdge(45.dp.toPx(), 64.dp.toPx(), 580.dp.toPx()) }
        else {
            landed.animateTo(1f, spring(dampingRatio = .9f, stiffness = 650f))
            controller.cancel()
        }
    }
    val shape = RoundedCornerShape(17.dp)
    Column(Modifier.offset {
        val pos = if (target == null) drag.origin else androidx.compose.ui.geometry.lerp(drag.origin, target, landed.value)
        IntOffset(pos.x.roundToInt(), pos.y.roundToInt())
    }.requiredSize(with(density) { drag.size.width.toDp() }, with(density) { drag.size.height.toDp() })
        .graphicsLayer {
            scaleX = 1f + .035f * lift.value * (1f-landed.value)
            scaleY = scaleX
            shadowElevation = 10.dp.toPx()*lift.value*(1f-landed.value)
            this.shape = shape
            clip = !tablet || drag.tab.pinned
        }.background(if (tablet && !drag.tab.pinned) Color.Transparent else surface)) {
        if (tablet && !drag.tab.pinned) TabletOverviewCard(drag.tab,title(drag.tab),drag.preview.value,aspect,surface,ink,Modifier)
        else {
            CardHeader(drag.tab, ink)
            if (!drag.tab.pinned) Box(Modifier.fillMaxSize().padding(2.dp)) { PageFace(drag.tab, drag.preview.value, surface, ink) }
        }
    }
}

@Composable
internal fun TabOverview(a: MainActivity) {
    val tablet = LocalConfiguration.current.screenWidthDp >= 650
    var privateGroup by remember { mutableStateOf(a.page.secret) }
    var groupId by remember { mutableStateOf(a.preferredTab(false)?.groupId.orEmpty()) }
    val group = a.orderedTabs(privateGroup, groupId)
    var searching by remember(privateGroup,groupId) { mutableStateOf(false) }
    var query by remember(privateGroup,groupId) { mutableStateOf("") }
    var reveal by remember(privateGroup,groupId) { mutableFloatStateOf(0f) }
    val text = query.trim()
    val shown = if (text.isEmpty()) group else group.filter { tab ->
        title(tab).contains(text, ignoreCase=true) || tab.url.contains(text, ignoreCase=true) ||
            Uri.decode(tab.url).contains(text, ignoreCase=true)
    }
    fun cancelSearch() {
        query = ""
        searching = false
        reveal = 0f
    }
    var picking by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { a.moveTarget = null } }
    var menu by remember { mutableStateOf<ContextMenu?>(null) }
    val initial = remember { a.page }
    DisposableEffect(initial) {
        val web = initial.web as? BrowserWebView
        val url = initial.url
        // Editing a group name can resize the window and scroll Chromium to
        // its previously focused form control behind the overview.
        if (web != null && initial.documentVisible && web.url == url) {
            web.overviewPosition = web.readingPosition(url)
        }
        onDispose {
            if (web != null && initial === a.page && web.isAttachedToWindow) web.resumeFromOverview()
        }
    }
    val homeImage = remember { mutableStateOf<ImageBitmap?>(null) }
    var windowFrame by remember { mutableStateOf<ImageBitmap?>(null) }
    var frameOwner by remember { mutableStateOf<Page?>(null) }
    var newTab by remember { mutableStateOf<Page?>(null) }
    var pendingOpen by remember { mutableStateOf<Page?>(null) }
    val newHomeImage = remember { mutableStateOf<ImageBitmap?>(null) }
    var waiting by remember { mutableStateOf(true) }
    var flight by remember { mutableStateOf<OverviewFlight?>(null) }
    val visible = remember { mutableStateMapOf<String, OverviewCard>() }
    val motion = remember { Animatable(0f) }
    fun open(tab: Page) {
        if (flight?.openingPage == true || tab !in a.tabs) return
        waiting = false
        val card = visible[tab.id]
        if (card == null) {
            cancelSearch()
            pendingOpen=tab
        } else {
            pendingOpen=null
            flight=OverviewFlight(card,true)
        }
    }
    fun addTab() {
        if (waiting || flight != null || newTab != null) return
        cancelSearch()
        val previous=a.page
        a.add(privateGroup,groupId=groupId,keepOverview=tablet)
        if (tablet && a.page !== previous) {
            newTab=a.page
            pendingOpen=a.page
        }
    }
    fun done() {
        val chosen = a.preferredTab(privateGroup, groupId)
        if (chosen != null) open(chosen) else if (!privateGroup) addTab() else a.sheet = ""
    }
    LaunchedEffect(initial) {
        if (tablet || initial.url.isBlank()) {
            val image = captureBrowserFrame(a,tablet)
            if (initial.url.isBlank()) homeImage.value = image
            if (tablet) { windowFrame = image; frameOwner = initial }
        }
        val card = snapshotFlow { visible[initial.id] }.first { it != null } ?: return@LaunchedEffect
        if (waiting) { waiting = false; flight = OverviewFlight(card,false) }
    }
    LaunchedEffect(flight,tablet) {
        val current = flight ?: return@LaunchedEffect
        if (tablet) current.awaitSourceFrame()
        if (current.openingPage) {
            if (current.card.tab in a.tabs) a.prepareOverviewCard(current.card.tab, current.card.image.value)
            if (tablet && frameOwner !== current.card.tab) {
                withFrameNanos { }
                withFrameNanos { }
                if (a.page === current.card.tab) {
                    windowFrame = captureBrowserFrame(a,true)
                    frameOwner = current.card.tab
                }
            }
            motion.animateTo(0f,if (tablet) tween(388,easing=CubicBezierEasing(.19f,.04f,.16f,1f))
                else tween(300,easing=FastOutSlowInEasing))
            a.sheet = if (tablet && newTab === current.card.tab && a.page === newTab) "地址" else ""
        } else {
            motion.animateTo(1f,if (tablet) tween(365,easing=CubicBezierEasing(.22f,.02f,.15f,1f))
                else tween(330,easing=FastOutSlowInEasing))
            flight = null
        }
    }
    val dark = isSystemInDarkTheme() || privateGroup
    val ground = if (dark) Color(0xff18181a) else Color(0xffeeeef3)
    val surface = if (dark) Color(0xff323235) else Color.White
    val ink = if (dark) Color.White else Color(0xff202023)
    Dialog(onDismissRequest={ if (searching) cancelSearch() else done() }, properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
        val keyboard = LocalSoftwareKeyboardController.current
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        val touches = remember { MenuTouchSource(android.view.ViewConfiguration.get(a).scaledTouchSlop) }
        val handleKey by rememberUpdatedState<(android.view.KeyEvent) -> Boolean> { key ->
            val escape = key.keyCode == android.view.KeyEvent.KEYCODE_ESCAPE && key.hasNoModifiers()
            if (menu != null || picking || a.moveTarget != null || !(escape || key.isOverviewShortcut())) false
            else {
                if (key.action == android.view.KeyEvent.ACTION_DOWN && key.repeatCount == 0) {
                    if (searching && escape) cancelSearch() else done()
                }
                true
            }
        }
        DisposableEffect(window, touches) {
            val remove = window?.let { touches.observe(it) { key -> handleKey(key) } }
            onDispose { remove?.invoke() }
        }
        SideEffect {
            window?.let {
                it.setDimAmount(0f)
                it.setWindowAnimations(0)
                val bars = WindowCompat.getInsetsController(it, it.decorView)
                bars.isAppearanceLightStatusBars = !dark
                bars.isAppearanceLightNavigationBars = !dark
            }
        }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val size = constraints
            val insetTop = WindowInsets.safeDrawing.getTop(LocalDensity.current)
            val insetBottom = WindowInsets.safeDrawing.getBottom(LocalDensity.current)
            val full = Rect(0f,insetTop.toFloat(),size.maxWidth.toFloat(),(size.maxHeight-insetBottom).toFloat())
            val aspect = (maxWidth / (maxHeight-132.dp).coerceAtLeast(1.dp)).coerceIn(.68f,1.85f)
            Box(Modifier.fillMaxSize().graphicsLayer {
                alpha = if (tablet) { if (waiting) 0f else 1f }
                    else if (flight?.openingPage == true && flight?.card?.tab !== initial) 1f else motion.value
            }) {
                LiquidKitRoot(backgroundColor=ground,dark=dark,edgeToEdgeSystemBars=false) {
                    val scene = rememberLiquidKitBackdrop()
                    val menuScene = rememberLiquidKitBackdrop()
                    val sampleOverview = picking || a.moveTarget != null || menu != null
                    val menuCapture = if (sampleOverview) Modifier.liquidKitBackdrop(menuScene) else Modifier
                    val toolbarBackdrop = if (tablet) rememberLiquidKitBackdrop() else scene
                    val background = remember(dark,tablet) {
                        if (!tablet) SolidColor(ground) else Brush.linearGradient(if (dark)
                            listOf(Color(0xff302c2e),Color(0xff25252a),Color(0xff363337))
                        else listOf(Color(0xffded8d3),Color(0xffeae6e3),Color(0xffd8d7da),Color(0xffe9e6e8)))
                    }
                    if (tablet) Box(Modifier.fillMaxSize().liquidKitBackdrop(toolbarBackdrop).background(background))
                    CompositionLocalProvider(LocalLiquidKitBackdrop provides toolbarBackdrop) {
                        val addGesture = if (privateGroup || a.recentlyClosed.isEmpty()) Modifier.clickable(role=Role.Button,onClick=::addTab)
                            else tabMenuGesture(Role.Button,::addTab) { x,y ->
                                val actions = a.recentlyClosed.map { entry ->
                                    val title = entry.site.name.ifBlank { entry.site.url }
                                    val origin = BrowserLibrary.closedTabs.originHint(entry)
                                    MenuAction(origin?.let { "$title · $it" } ?: title) { a.reopenInGroup(entry,groupId) }
                                }
                                menu = ContextMenu(x,y,null,null,actions) { a.sheet=="标签页" && !privateGroup }
                            }
                        val doneGesture = tabMenuGesture(Role.Button,::done) { x,y ->
                            val closable = shown.filterNot { it.pinned }
                            if (closable.isNotEmpty()) {
                                val label = if (text.isEmpty()) "关闭组内 ${closable.size} 个标签页" else "关闭 ${closable.size} 个匹配标签页"
                                menu = ContextMenu(x,y,null,null,listOf(MenuAction(label) {
                                    a.confirm("关闭标签页",label+"？","关闭",destructive=true) {
                                        a.closeOverviewTabs(closable,privateGroup,groupId)
                                        cancelSearch()
                                    }
                                })) { a.sheet=="标签页" }
                            }
                        }
                        val options = overviewOptions { x,y ->
                            val actions = buildList {
                                if (group.count { !it.pinned } > 1) TabArrangement.entries.forEach { order ->
                                    add(MenuAction(order.label) { a.arrangeTabs(privateGroup,groupId,order) })
                                }
                                if (tablet && !privateGroup && a.recentlyClosed.isNotEmpty())
                                    add(MenuAction("最近关闭") { a.sheet="最近关闭" })
                                if (tablet) add(MenuAction(if (privateGroup) "新建无痕标签页" else "新建标签页") { addTab() })
                            }
                            if (actions.isNotEmpty()) menu=ContextMenu(x,y,null,null,actions) { a.sheet=="标签页" }
                        }
                        val controlsLayer = if (tablet) Modifier.graphicsLayer {
                            val p = motion.value.coerceIn(0f,1f)
                            alpha = (.18f + .82f*p)
                            scaleX = .94f+.06f*p
                            scaleY = scaleX
                            val blur = 7.dp.toPx()*(1f-p)
                            renderEffect = if (android.os.Build.VERSION.SDK_INT >= 31 && blur > .15f)
                                BlurEffect(blur,blur,TileMode.Clamp) else null
                        } else Modifier
                        Column(Modifier.fillMaxSize().then(menuCapture).then(controlsLayer)
                            .then(if (tablet) Modifier else Modifier.background(ground))
                            .safeDrawingPadding().imePadding().padding(horizontal=if (tablet) 0.dp else 14.dp)) {
                            Crossfade(searching,animationSpec=tween(150),label="标签搜索") { editing ->
                                if (editing) {
                                    OverviewSearchField(query, { query=it }, ink, surface, onCancel=::cancelSearch)
                                } else if (tablet) TabletOverviewToolbar(privateGroup,a.tabGroups[groupId].orEmpty(),
                                    a.orderedTabs(false,groupId).size,!waiting && flight==null,ink,
                                    chooseGroup={ secret ->
                                        cancelSearch()
                                        if (!secret && !privateGroup) picking=true else privateGroup=secret
                                    },showGroups={ if (!waiting && flight==null) picking=true },options=options,
                                    add=addGesture,search={ searching=true },done=doneGesture)
                                else Row(Modifier.fillMaxWidth().height(54.dp),verticalAlignment=Alignment.CenterVertically) {
                                    Box(Modifier.weight(1f)) {
                                        OverviewHeading(if (privateGroup) "无痕标签页" else a.tabGroups[groupId] ?: "标签页",ink) { reveal }
                                    }
                                    Box(Modifier.size(48.dp).clickable(enabled=!waiting && flight==null, role=Role.Button) { searching=true }
                                        .semantics { contentDescription="搜索标签页" }, contentAlignment=Alignment.Center) {
                                        LiquidKitIcon(LiquidKitSymbol.Search,ink)
                                    }
                                    if (group.count { !it.pinned } > 1) Box(Modifier.size(48.dp).then(options),contentAlignment=Alignment.Center) {
                                        LiquidKitIcon(LiquidKitSymbol.ChevronUpDown,ink)
                                    }
                                    if (!privateGroup) Box(Modifier.size(48.dp).clickable(role=Role.Button) { picking=true }
                                        .semantics { contentDescription="标签组" }, contentAlignment=Alignment.Center) {
                                        LiquidKitIcon(LiquidKitSymbol.SquareOnSquare,ink)
                                    }
                                    if (!privateGroup && a.recentlyClosed.isNotEmpty()) {
                                        Box(Modifier.size(48.dp).clickable(role=Role.Button) { a.sheet="最近关闭" }
                                            .semantics { contentDescription="最近关闭" },contentAlignment=Alignment.Center) {
                                            LiquidKitIcon(LiquidKitSymbol.Clock,ink)
                                        }
                                    }
                                }
                            }
                            Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal=if (tablet) 18.dp else 0.dp)) {
                                key(privateGroup,groupId) {
                                    val browseState = rememberLazyGridState(initialFirstVisibleItemIndex=group.indexOf(a.preferredTab(privateGroup, groupId)).coerceAtLeast(0))
                                    val searchState = rememberLazyGridState()
                                    val state = if (text.isEmpty()) browseState else searchState
                                    LaunchedEffect(text) { if (text.isNotEmpty()) searchState.scrollToItem(0) }
                                    LaunchedEffect(pendingOpen) {
                                        val target=pendingOpen ?: return@LaunchedEffect
                                        val index=a.orderedTabs(privateGroup,groupId).indexOf(target)
                                        if (index < 0) return@LaunchedEffect
                                        if (browseState.layoutInfo.visibleItemsInfo.none { it.key==target.id })
                                            browseState.animateScrollToItem(index)
                                        snapshotFlow { visible[target.id] }.first { it != null }
                                        if (target === newTab && a.page===target) {
                                            withFrameNanos { }
                                            val image=captureBrowserFrame(a,true)
                                            windowFrame=image
                                            frameOwner=target
                                            newHomeImage.value=image
                                        }
                                        if (pendingOpen===target) open(target)
                                    }
                                    val scope = rememberCoroutineScope()
                                    val reorder = remember(state, touches) { GridTabReorder(state, touches, a, scope) }
                                    DisposableEffect(reorder) { onDispose { reorder.cancel() } }
                                    val drag = reorder.lifted
                                    val displayed = if (drag != null && drag.landing == null && drag.members == group) drag.order() else shown
                                    androidx.activity.compose.BackHandler(enabled = drag != null) { reorder.cancel() }
                                    Box(Modifier.fillMaxSize().liquidKitBackdrop(scene).then(if (tablet) Modifier else Modifier.background(ground))) {
                                        LazyVerticalGrid(columns=GridCells.Adaptive(if (tablet) 232.dp else 146.dp),state=state,modifier=Modifier.fillMaxSize().onGloballyPositioned { reorder.coordinates = it }
                                            .then(if (searching || flight!=null || waiting) Modifier else Modifier.revealTabSearch(browseState,
                                                onProgress={ reveal=it }, onReveal={ searching=true })),
                                            contentPadding=PaddingValues(top=if (tablet) 7.dp else 8.dp,bottom=if (tablet) 24.dp else 72.dp),
                                            horizontalArrangement=Arrangement.spacedBy(if (tablet) 16.dp else 12.dp),
                                            verticalArrangement=Arrangement.spacedBy(if (tablet) 8.dp else 16.dp)) {
                                            if (text.isNotEmpty() && shown.isEmpty()) {
                                                item(span={ GridItemSpan(maxLineSpan) }) {
                                                    Box(Modifier.fillMaxWidth().height(112.dp),contentAlignment=Alignment.Center) {
                                                        OverviewText("没有匹配的标签页",ink.copy(alpha=.65f))
                                                    }
                                                }
                                            }
                                            items(displayed,key={it.id}, span = { tab -> GridItemSpan(if (tab.pinned) maxLineSpan else 1) }) { tab ->
                                                val preview = if (tab === newTab) newHomeImage else if (tab === initial && tab.url.isBlank()) homeImage
                                                    else overviewImage(a,tab,tab===initial)
                                                val bounds = Modifier.onGloballyPositioned {
                                                    visible[tab.id]=OverviewCard(tab,preview,Rect(it.positionInRoot(),it.size.toSize()))
                                                }
                                                val tabletCard = tablet && !tab.pinned
                                                DisposableEffect(tab) { onDispose { visible.remove(tab.id) } }
                                                val gestures = tabMenuGesture(Role.Button, { keyboard?.hide(); open(tab) }) { x, y ->
                                                    val actions = a.tabCardMenu(tab, x, y, { a.sheet == "标签页" && tab.secret == privateGroup && (privateGroup || tab.groupId == groupId) }) {
                                                        val ordered = a.orderedTabs(privateGroup, groupId)
                                                        val next = if (text.isEmpty()) ordered.indexOf(tab) else ordered.filter { it in shown }.indexOf(tab)
                                                        if (next >= 0) scope.launch { state.animateScrollToItem(next) }
                                                    }
                                                    if (!searching && flight == null && !waiting) {
                                                        reorder.arm(tab, group, preview, actions) { menu = actions }
                                                    } else menu = actions
                                                }
                                                val accessibleOrder = if (searching) Modifier else Modifier.semantics {
                                                    val peers = group.filter { it.pinned == tab.pinned }
                                                    val position = peers.indexOf(tab)
                                                    customActions = buildList {
                                                        peers.getOrNull(position-1)?.let { previous ->
                                                            add(CustomAccessibilityAction("前移一个位置") { a.moveOverviewTab(tab, previous); true })
                                                        }
                                                        peers.getOrNull(position+1)?.let { next ->
                                                            add(CustomAccessibilityAction("后移一个位置") { a.moveOverviewTab(tab, next); true })
                                                        }
                                                    }
                                                }
                                                val itemMotion=if (tabletCard) Modifier.animateItem(fadeOutSpec=tween(260))
                                                    else Modifier.animateItem()
                                                Column(itemMotion.fillMaxWidth().graphicsLayer { alpha = if (drag?.tab === tab) 0f else 1f }
                                                    .then(if (tab.pinned) Modifier.pointerInput(tab.id) {
                                                        detectHorizontalDragGestures { change, _ -> change.consume() }
                                                    } else swipeToClose(tab) { a.close(tab) }).then(if (tabletCard) Modifier else Modifier.clip(RoundedCornerShape(17.dp)).background(surface)
                                                        .border(if (tab===a.page) 2.dp else 1.dp,
                                                            if(tab===a.page) Color(0xff478ff2) else ink.copy(alpha=.12f),RoundedCornerShape(17.dp)))
                                                    .then(gestures).then(accessibleOrder).then(if (tab.pinned) Modifier.semantics { contentDescription = "已固定，${title(tab)}" }
                                                        else Modifier)) {
                                                    if (tabletCard) {
                                                        val moving=flight?.card?.tab === tab
                                                        TabletOverviewCard(tab,title(tab),preview.value,aspect,surface,ink,bounds,
                                                            flying=moving,captionAlpha=if (moving) motion.value else 1f) { a.close(tab) }
                                                    } else {
                                                        CardHeader(tab,ink,if (tab.pinned) bounds else Modifier) { a.close(tab) }
                                                        if (!tab.pinned) Box(Modifier.fillMaxWidth().aspectRatio(1.05f).then(bounds)
                                                            .padding(2.dp)) { PageFace(tab,preview.value,surface,ink) }
                                                    }
                                                    if (text.isNotEmpty() && tab.url.isNotBlank()) {
                                                        Box(Modifier.fillMaxWidth().padding(horizontal=12.dp, vertical=5.dp)) {
                                                            OverviewText(Uri.decode(tab.url),ink.copy(alpha=.7f),12)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        if (drag != null) DraggedTab(reorder,drag,surface,ink,tablet,aspect)
                                    }
                                }
                                if (!tablet) Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(64.dp),verticalAlignment=Alignment.CenterVertically,
                                    horizontalArrangement=Arrangement.SpaceBetween) {
                                    LiquidKitGlassSurface(Modifier.size(46.dp).then(addGesture)) {
                                        Box(Modifier.fillMaxSize().semantics { contentDescription=if(privateGroup) "新建无痕标签页" else "新建标签页" },
                                            contentAlignment=Alignment.Center) { LiquidKitIcon(LiquidKitSymbol.Add,ink) }
                                    }
                                    LiquidKitGlassSurface {
                                        Row(Modifier.padding(3.dp)) {
                                            listOf(false,true).forEach { secret ->
                                                Box(Modifier.clip(RoundedCornerShape(22.dp))
                                                    .background(if(secret==privateGroup) surface else Color.Transparent)
                                                    .clickable(role=Role.Tab,enabled=!waiting && flight==null) {
                                                        if (!secret && !privateGroup) { cancelSearch(); picking=true } else privateGroup=secret
                                                    }
                                                    .heightIn(min=42.dp).padding(horizontal=12.dp),contentAlignment=Alignment.Center) {
                                                    Box(Modifier.widthIn(max=96.dp)) {
                                                    OverviewText(if(secret) "无痕浏览" else if(privateGroup) "普通标签" else a.tabGroups[groupId] ?: "普通标签",ink,14,secret==privateGroup)
                                                }
                                                }
                                            }
                                        }
                                    }
                                    LiquidKitGlassSurface(Modifier.then(doneGesture)) {
                                        Box(Modifier.height(46.dp).padding(horizontal=14.dp),contentAlignment=Alignment.Center) { OverviewText("完成",ink,15,true) }
                                    }
                                }
                            }
                        }
                        if (sampleOverview) CompositionLocalProvider(LocalLiquidKitBackdrop provides menuScene) {
                            if (picking || a.moveTarget != null) {
                                TabGroupPicker(a, groupId, a.moveTarget, dark,
                                    onSelect = { id -> cancelSearch(); groupId=id; privateGroup=false },
                                    dismiss = { picking=false; a.moveTarget=null })
                            }
                            menu?.let { current -> key(current) {
                                SafariContextMenu(a, current, dark, touches, dismiss = { menu = null })
                            } }
                        }
                    }
                }
            }
            flight?.let { current ->
                val density = LocalDensity.current
                val view = LocalView.current
                val card=current.card.rect
                val floating = if (tablet) Modifier.requiredSize(with(density){ card.width.toDp() },
                    with(density){ card.height.toDp() }).graphicsLayer {
                    val p=motion.value.coerceIn(0f,1f)
                    val rect=lerp(full,card,p)
                    transformOrigin=TransformOrigin(0f,0f)
                    translationX=rect.left
                    translationY=rect.top
                    scaleX=rect.width/card.width.coerceAtLeast(1f)
                    scaleY=rect.height/card.height.coerceAtLeast(1f)
                    shape=RoundedCornerShape(15.dp*(p/scaleX))
                    clip=true
                }.drawWithContent {
                    drawContent()
                    current.presentedBy(view)
                } else {
                    val progress=motion.value
                    val rect=lerp(full,card,progress)
                    Modifier.offset { IntOffset(rect.left.roundToInt(),rect.top.roundToInt()) }
                        .requiredSize(with(density){ rect.width.toDp() },with(density){ rect.height.toDp() })
                        .clip(RoundedCornerShape((16*progress).dp))
                }
                Box(floating) {
                    PageFace(current.card.tab,current.card.image.value,surface,ink)
                    if (tablet && frameOwner === current.card.tab) windowFrame?.let { frame ->
                        Image(frame,null,Modifier.fillMaxSize().graphicsLayer {
                            alpha=((.88f-motion.value)/.68f).coerceIn(0f,1f)
                        },contentScale=ContentScale.FillBounds)
                    }
                    if (tablet && !current.card.tab.pinned) Box(Modifier.align(Alignment.TopEnd).padding(4.dp)
                        .size(23.dp).graphicsLayer {
                            val p=motion.value
                            alpha=((p-.38f)/.62f).coerceIn(0f,1f)
                            transformOrigin=TransformOrigin(1f,0f)
                            scaleX=card.width/(full.width+(card.width-full.width)*p).coerceAtLeast(1f)
                            scaleY=card.height/(full.height+(card.height-full.height)*p).coerceAtLeast(1f)
                        }
                        .background(Color.White.copy(alpha=.82f),androidx.compose.foundation.shape.CircleShape),
                        contentAlignment=Alignment.Center) {
                        LiquidKitIcon(LiquidKitSymbol.Close,Color(0xff333336),Modifier.size(13.dp))
                    }
                }
            }
        }
    }
}
