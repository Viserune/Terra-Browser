package app.terra.browser

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import io.liquidkit.LiquidKitIcon
import io.liquidkit.LiquidKitSymbol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private typealias Symbol = LiquidKitSymbol
private val LocalShareInk = compositionLocalOf { Color.Black }

@Composable private fun Caption(text: String, size: Int, bold: Boolean = false, secondary: Boolean = false,
                               center: Boolean = false, lines: Int = 1) {
    BasicText(text, maxLines=lines, overflow=TextOverflow.Ellipsis, style=TextStyle(
        color=LocalShareInk.current.copy(alpha=if (secondary) .62f else 1f), fontSize=size.sp, lineHeight=(size+3).sp,
        fontWeight=if (bold) FontWeight.SemiBold else FontWeight.Normal,
        textAlign=if (center) TextAlign.Center else TextAlign.Start))
}

// The pointer and the body form one optical surface, including during a scale or resize.
@Composable private fun popoverShape(pointer: Dp, above: Boolean): GenericShape {
    val density = LocalDensity.current
    return remember(pointer, above, density) {
        val tip = with(density) { 14.dp.toPx() }
        val radius = with(density) { 28.dp.toPx() }
        val px = with(density) { pointer.toPx() }
        GenericShape { size, _ ->
            val w = size.width
            val h = size.height
            val r = minOf(radius, w/2, (h-tip)/2).coerceAtLeast(0f)
            val x = px.coerceIn(minOf(r+tip,w/2), maxOf(w-r-tip,w/2))
            val top = if (above) 0f else tip
            val bottom = if (above) h-tip else h
            moveTo(r,top)
            if (!above) { lineTo(x-tip,top); lineTo(x,0f); lineTo(x+tip,top) }
            lineTo(w-r,top); quadraticTo(w,top,w,top+r)
            lineTo(w,bottom-r); quadraticTo(w,bottom,w-r,bottom)
            if (above) { lineTo(x+tip,bottom); lineTo(x,h); lineTo(x-tip,bottom) }
            lineTo(r,bottom); quadraticTo(0f,bottom,0f,bottom-r)
            lineTo(0f,top+r); quadraticTo(0f,top,r,top); close()
        }
    }
}

@Composable internal fun SharePanel(a: MainActivity, dark: Boolean, capture: WebGlassCapture?) {
    val item = a.sharedPage ?: return
    key(item) {
        val visible = remember { MutableTransitionState(false).also { it.targetState=true } }
        var continuation by remember { mutableStateOf<(() -> Unit)?>(null) }
        var more by remember { mutableStateOf(false) }
        val options = remember { MutableTransitionState(false) }
        var format by remember { mutableStateOf(ShareFormat.Link) }
        val formats = item.formats
        LaunchedEffect(formats) { if (format !in formats) format=ShareFormat.Link }
        val apps by produceState(emptyList<ShareApp>(), format.mime) {
            value = withContext(Dispatchers.IO) { a.shareApps(format.mime) }
        }
        fun dismiss(next: (() -> Unit)? = null) {
            if (!visible.targetState) return
            continuation = next
            visible.targetState = false
        }
        fun back() {
            if (options.targetState) options.targetState=false else if (options.isIdle) dismiss()
        }
        LaunchedEffect(visible.isIdle, visible.currentState) {
            if (visible.isIdle && !visible.currentState && !visible.targetState && a.sharedPage === item) {
                a.sharedPage = null
                continuation?.invoke()
            }
        }
        Dialog(onDismissRequest=::back, properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
            val window = (LocalView.current.parent as? DialogWindowProvider)?.window
            SideEffect { window?.setDimAmount(0f); window?.setWindowAnimations(0) }
            val ink = if (dark) Color(0xfff5f5f7) else Color(0xff171719)
            val surface = if (dark) Color(0xff29292c) else Color(0xfffafafa)
            CompositionLocalProvider(LocalShareInk provides ink) {
                val noRipple = remember { MutableInteractionSource() }
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.matchParentSize().clickable(noRipple, indication=null, onClick=::back)
                        .clearAndSetSemantics {})
                    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal=8.dp)) {
                        var origin by remember { mutableStateOf(Offset.Zero) }
                        Box(Modifier.matchParentSize().onGloballyPositioned { origin=it.localToScreen(Offset.Zero) })
                        val density = LocalDensity.current
                        val tablet = maxWidth+16.dp >= 650.dp
                        val width = if (tablet) minOf(378.dp,maxWidth) else minOf(500.dp,maxWidth)
                        val anchor = item.anchor ?: a.shareAnchor
                        val x = with(density) { anchor?.let { (it.x-origin.x).toDp() } } ?: maxWidth-98.dp
                        val y = with(density) { anchor?.let { (it.y-origin.y).toDp() } } ?: 48.dp
                        val above = tablet && item.anchor != null && y > maxHeight*.5f
                        val left = (x-width*.69f).coerceIn(0.dp,(maxWidth-width).coerceAtLeast(0.dp))
                        val pivot = (x-left).coerceIn(0.dp,width)
                        val tip = if (tablet) 14.dp else 0.dp
                        val anchorY = (y + if (above) (-3).dp else 3.dp).coerceIn(0.dp,maxHeight)
                        val room = (if (!tablet) maxHeight-10.dp else if (above) anchorY-6.dp else maxHeight-anchorY-8.dp)
                            .coerceAtLeast(tip+1.dp)
                        val bodyHeight by animateDpAsState(if (more) room-tip else minOf(518.dp,room-tip),
                            spring(dampingRatio=.88f,stiffness=520f), label="共享面板高度")
                        var drag by remember { mutableFloatStateOf(0f) }
                        var dragging by remember { mutableStateOf(false) }
                        val offset by animateFloatAsState(drag, if (dragging) snap() else spring(stiffness=620f),label="共享拖动")
                        val height = (bodyHeight+tip+with(density){(-offset).coerceAtLeast(0f).toDp()}).coerceAtMost(room)
                        val placement = if (tablet) Modifier.align(Alignment.TopStart)
                            .offset(x=left,y=if (above) anchorY-height else anchorY)
                            else Modifier.align(Alignment.BottomCenter).padding(bottom=4.dp)
                        val shape = if (tablet) popoverShape(pivot,above) else RoundedCornerShape(30.dp)
                        val blocked = options.currentState || options.targetState || !options.isIdle
                        AnimatedVisibility(visibleState=visible, modifier=placement,enter=EnterTransition.None,exit=ExitTransition.None) {
                            val extent by transition.animateFloat(transitionSpec={
                                if (targetState==EnterExitState.Visible) spring(dampingRatio=.84f,stiffness=570f)
                                else tween(190,easing=FastOutSlowInEasing)
                            },label="共享展开") { if (it==EnterExitState.Visible) 1f else 0f }
                            val panelModifier = Modifier.width(width).height(height).graphicsLayer {
                                if (tablet) {
                                    transformOrigin=TransformOrigin(pivot/width,if (above) 1f else 0f)
                                    scaleX=.14f+.86f*extent; scaleY=scaleX
                                } else translationY=(1f-extent)*size.height+offset.coerceAtLeast(0f)
                                alpha=(extent*2.6f).coerceIn(0f,1f)
                            }.shadow(16.dp,shape=shape,clip=false,ambientColor=Color.Black.copy(alpha=.20f),
                                spotColor=Color.Black.copy(alpha=.30f))
                                .then(if (blocked) Modifier.clearAndSetSemantics {} else Modifier.semantics { paneTitle="共享面板" })
                            DialogGlassSurface(capture,panelModifier,shape,surface.copy(alpha=.89f),panelBlur=20.dp) {
                                Column(Modifier.fillMaxSize().padding(top=if (tablet && !above) tip else 0.dp,
                                    bottom=if (above) tip else 0.dp).pointerInput(Unit) { detectTapGestures {} }
                                    .semantics { isTraversalGroup=true }) {
                                    if (!tablet) Box(Modifier.fillMaxWidth().height(24.dp).semantics {
                                        contentDescription="共享面板把手"
                                        onClick(label=if (more) "收起操作" else "展开操作") { more=!more; true }
                                    }.pointerInput(more) {
                                        detectVerticalDragGestures(onDragStart={ dragging=true },onDragCancel={ dragging=false; drag=0f },
                                            onDragEnd={
                                                when {
                                                    drag < -34.dp.toPx() -> more=true
                                                    drag > 34.dp.toPx() && more -> more=false
                                                    drag > 44.dp.toPx() -> dismiss()
                                                }
                                                dragging=false; drag=0f
                                            }) { change, delta -> change.consume(); drag+=delta }
                                    },contentAlignment=Alignment.Center) {
                                        Box(Modifier.size(38.dp,5.dp).background(ink.copy(alpha=.22f),CircleShape))
                                    }
                                    ShareContents(a,item,apps,more, { more=!more }, { options.targetState=true },
                                        { target -> dismiss { a.sendSharedPage(item,format,target) } },::dismiss)
                                }
                            }
                        }
                    }
                    AnimatedVisibility(visibleState=options,enter=fadeIn(tween(190)),exit=fadeOut(tween(150))) {
                        BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black.copy(alpha=.25f))
                            .clickable(noRipple,indication=null) { options.targetState=false }.safeDrawingPadding().padding(16.dp),
                            contentAlignment=Alignment.Center) {
                            DialogGlassSurface(capture,Modifier.width(minOf(540.dp,maxWidth)).height(minOf(738.dp,maxHeight))
                                .animateEnterExit(enter=scaleIn(tween(230),initialScale=.89f)+ slideInVertically(tween(230)) { it/9 },
                                    exit=scaleOut(tween(140),targetScale=.94f))
                                .semantics { paneTitle="共享选项" },RoundedCornerShape(30.dp),surface.copy(alpha=.98f),panelBlur=18.dp) {
                                Column(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures {} }
                                    .verticalScroll(rememberScrollState()).padding(14.dp)) {
                                    Box(Modifier.fillMaxWidth().height(44.dp),contentAlignment=Alignment.Center) {
                                        Caption("选项",18,true)
                                        Box(Modifier.align(Alignment.CenterStart).size(44.dp).clip(CircleShape)
                                            .clickable(role=Role.Button) { options.targetState=false }
                                            .semantics { contentDescription="关闭共享选项" },contentAlignment=Alignment.Center) {
                                            LiquidKitIcon(Symbol.Close,ink)
                                        }
                                    }
                                    Spacer(Modifier.height(26.dp))
                                    Caption("发送形式",14,secondary=true)
                                    Spacer(Modifier.height(6.dp))
                                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(ink.copy(alpha=.055f))) {
                                        formats.forEach { candidate ->
                                            Row(Modifier.fillMaxWidth().clickable(role=Role.RadioButton) { format=candidate }
                                                .semantics { selected=(format==candidate) }.heightIn(min=52.dp).padding(horizontal=18.dp),
                                                verticalAlignment=Alignment.CenterVertically) {
                                                Box(Modifier.weight(1f)) { Caption(candidate.label,16) }
                                                if (format==candidate) LiquidKitIcon(Symbol.Check,Color(0xff1674dc))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun ShareContents(a: MainActivity, item: SharedPage, apps: List<ShareApp>, more: Boolean,
    toggle: () -> Unit, options: () -> Unit, send: (android.content.ComponentName?) -> Unit,
    dismiss: ((() -> Unit)?) -> Unit) {
    val ink = LocalShareInk.current
    val scroll = rememberScrollState()
    LaunchedEffect(more) { if (!more) scroll.animateScrollTo(0) }
    val favicon = remember(item) { item.currentDocument()?.favicon?.asImageBitmap() }
    val separator = ink.copy(alpha=.10f)
    Column(Modifier.fillMaxWidth().verticalScroll(scroll).padding(horizontal=15.dp,vertical=12.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min=116.dp).padding(horizontal=7.dp),verticalAlignment=Alignment.CenterVertically) {
            val tileColor = if (ink.luminance()<.3f) Color.White.copy(alpha=.88f) else ink.copy(alpha=.09f)
            Box(Modifier.size(64.dp).background(tileColor,RoundedCornerShape(14.dp)),contentAlignment=Alignment.Center) {
                if (favicon!=null) Image(favicon,null,Modifier.size(34.dp)) else LiquidKitIcon(Symbol.Globe,ink.copy(alpha=.65f))
            }
            Column(Modifier.weight(1f).padding(start=14.dp)) {
                Caption(item.site.name,16,true,lines=2)
                Caption(item.domain,14,secondary=true)
                Box(Modifier.padding(top=5.dp).clip(CircleShape).background(ink.copy(alpha=.055f))
                    .clickable(role=Role.Button,onClick=options).padding(horizontal=14.dp,vertical=6.dp)) { Caption("选项 ›",14,true) }
            }
        }
        Spacer(Modifier.fillMaxWidth().height(1.dp).background(separator))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).heightIn(min=126.dp),
            verticalAlignment=Alignment.CenterVertically) {
            Tile("系统推荐",Symbol.Share) { send(null) }
            if (!item.secret) a.recentShareApps.mapNotNull { key -> apps.find { it.component==key } }.forEach { app ->
                AppTile(app) { send(app.component) }
            }
        }
        Spacer(Modifier.fillMaxWidth().height(1.dp).background(separator))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).heightIn(min=115.dp),
            verticalAlignment=Alignment.CenterVertically) {
            apps.forEach { app -> AppTile(app) { send(app.component) } }
            Tile("更多应用",Symbol.More) { send(null) }
        }
        Spacer(Modifier.fillMaxWidth().height(1.dp).background(separator))
        Row(Modifier.fillMaxWidth().padding(top=13.dp),horizontalArrangement=Arrangement.SpaceEvenly) {
            Tile("拷贝",Symbol.Clipboard,small=true,modifier=Modifier.weight(1f)) { dismiss { a.copy(item.site.url) } }
            if (!item.secret) {
                Tile("添加到书签",Symbol.Bookmark,small=true,modifier=Modifier.weight(1f)) { dismiss { a.editBookmark(item.site) } }
                Tile("添加到阅读列表",Symbol.ListBulletRectangle,small=true,modifier=Modifier.weight(1f)) { dismiss { a.saveForLater(item.site) } }
            }
            Tile(if (more) "查看更少" else "查看更多",Symbol.Forward,small=true,modifier=Modifier.weight(1f),
                rotation=if (more) -90f else 90f,click=toggle)
        }
        AnimatedVisibility(visible=more,enter=fadeIn(tween(195))+expandVertically(expandFrom=Alignment.Top),
            exit=fadeOut(tween(135))+shrinkVertically(shrinkTowards=Alignment.Top)) {
            Column(Modifier.fillMaxWidth().padding(top=16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(ink.copy(alpha=.07f))) {
                    if (!item.secret) {
                        ActionRow("添加到个人收藏",Symbol.Bookmark) { dismiss {
                            a.editBookmark(item.site); a.bookmarkDraft?.favorite=true
                        } }
                        if (a.getSystemService(android.content.pm.ShortcutManager::class.java).isRequestPinShortcutSupported)
                            ActionRow("添加到主屏幕",Symbol.Add) { dismiss { a.pinSharedPage(item.site) } }
                    }
                    if (item.currentDocument()!=null) ActionRow("在页面上查找",Symbol.Search) { dismiss { a.openFind() } }
                    ActionRow("在新标签页中打开",Symbol.SquareOnSquare) { dismiss { a.add(item.secret,item.site.url) } }
                }
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(ink.copy(alpha=.07f))) {
                    if (item.currentDocument()!=null) ActionRow("打印或存为 PDF",Symbol.Document) { dismiss { a.printSharedPage(item) } }
                    ActionRow("在新窗口中打开",Symbol.SquareOnSquare) { dismiss { BrowserWindowActivity.open(a,item.site.url,item.secret) } }
                    ActionRow("使用系统共享",Symbol.Share) { send(null) }
                }
            }
        }
    }
}

@Composable private fun AppTile(app: ShareApp, click: () -> Unit) {
    Column(Modifier.width(84.dp).clip(RoundedCornerShape(16.dp)).clickable(role=Role.Button,onClick=click)
        .padding(vertical=6.dp),horizontalAlignment=Alignment.CenterHorizontally) {
        Image(app.icon,null,Modifier.size(58.dp))
        Spacer(Modifier.height(6.dp)); Caption(app.label,12,center=true,lines=2)
    }
}

@Composable private fun Tile(label: String, icon: Symbol, modifier: Modifier=Modifier, small: Boolean=false,
    rotation: Float=0f, click: () -> Unit) {
    val ink = LocalShareInk.current
    val angle by animateFloatAsState(rotation,tween(210),label="操作图标方向")
    Column(modifier.then(if (small) Modifier else Modifier.width(84.dp)).clip(RoundedCornerShape(16.dp))
        .clickable(role=Role.Button,onClick=click).padding(vertical=6.dp,horizontal=2.dp),
        horizontalAlignment=Alignment.CenterHorizontally) {
        Box(Modifier.size(if (small) 56.dp else 60.dp).background(ink.copy(alpha=.085f),
            if (small) CircleShape else RoundedCornerShape(15.dp)),contentAlignment=Alignment.Center) {
            LiquidKitIcon(icon,ink,Modifier.graphicsLayer { rotationZ=angle })
        }
        Spacer(Modifier.height(6.dp)); Caption(label,12,center=true,lines=2)
    }
}

@Composable private fun ActionRow(label: String, symbol: Symbol, action: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(role=Role.Button,onClick=action).heightIn(min=51.dp)
        .padding(horizontal=16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
        LiquidKitIcon(symbol,LocalShareInk.current); Caption(label,16)
    }
}
