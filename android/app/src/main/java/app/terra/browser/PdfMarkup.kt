package app.terra.browser

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.animation.*
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.liquidkit.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private fun renderPage(file: File,index: Int,width: Int): ImageBitmap =
    ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
        PdfRenderer(fd).use { renderer -> renderer.openPage(index).use { page ->
            val image=Bitmap.createBitmap(width,width*page.height/page.width,Bitmap.Config.ARGB_8888)
            image.eraseColor(android.graphics.Color.WHITE)
            page.render(image,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            image.asImageBitmap()
        } }
    }

@Composable internal fun PdfMarkup(a: MainActivity, document: SharedDocument, dark: Boolean,
    close: ()->Unit, apply: (SharedDocument)->Unit) {
    val pdf=document.pdf ?: return
    val visible=remember { MutableTransitionState(false).also { it.targetState=true } }
    val strokes=remember { mutableStateListOf<PdfInk>() }
    val redo=remember { mutableStateListOf<PdfInk>() }
    var color by remember { mutableIntStateOf(0xff141416.toInt()) }
    var page by remember { mutableIntStateOf(0) }
    var confirm by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<SharedDocument?>(null) }
    val scope=rememberCoroutineScope()
    fun cancel() { if (!busy) { if (strokes.isEmpty()) visible.targetState=false else confirm=true } }
    fun finish() {
        if (busy) return
        if (strokes.isEmpty()) { visible.targetState=false; return }
        busy=true
        scope.launch {
            var file: File?=null
            try {
                val next=withContext(Dispatchers.IO) { a.newShareFile(document.format) }
                file=next
                val all=document.ink+strokes.toList()
                pdf.write(next,all)
                result=SharedDocument(next,document.name,document.format,pdf,all)
                visible.targetState=false
            } catch (cancel: CancellationException) { file?.delete(); throw cancel }
              catch (_: Exception) { file?.delete(); a.notice("无法保存标记，请重试") }
            finally { busy=false }
        }
    }
    LaunchedEffect(visible.currentState,visible.isIdle) {
        if (visible.isIdle && !visible.targetState && !visible.currentState) result?.let(apply) ?: close()
    }
    Dialog(onDismissRequest=::cancel,properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
        val ground=if (dark) Color(0xff29292c) else Color(0xffe9e9ed)
        LiquidKitRoot(backgroundColor=Color.Transparent,dark=dark,edgeToEdgeSystemBars=false) {
            AnimatedVisibility(visibleState=visible,enter=slideInVertically(tween(320)) { it }+fadeIn(tween(130)),
                exit=slideOutVertically(tween(240)) { it }+fadeOut(tween(230))) {
                Column(Modifier.fillMaxSize().background(ground).safeDrawingPadding().padding(8.dp)
                    .semantics { paneTitle="PDF 标记" },horizontalAlignment=Alignment.CenterHorizontally) {
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        LiquidKitActionButton("取消",::cancel)
                        Box(Modifier.weight(1f),contentAlignment=Alignment.Center) { ShareCaption(document.name,16,true) }
                        LiquidKitActionButton(if (busy) "正在保存" else "完成",::finish,style=LiquidKitActionStyle.Filled)
                    }
                    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(vertical=10.dp),contentAlignment=Alignment.Center) {
                        val width=minOf(maxWidth,maxHeight*(PagePdf.width.toFloat()/PagePdf.height))
                        val px=with(LocalDensity.current) { width.roundToPx().coerceIn(1,1800) }
                        key(page,px) {
                            var attempt by remember { mutableIntStateOf(0) }
                            val preview by produceState<Result<ImageBitmap>?>(null,document,attempt) {
                                value=null
                                value=withContext(Dispatchers.IO) {
                                    try { Result.success(renderPage(document.file,page,px)) }
                                    catch (error: java.io.IOException) { Result.failure(error) }
                                    catch (error: IllegalStateException) { Result.failure(error) }
                                }
                            }
                            val bitmap=preview?.getOrNull()
                            val current=remember { mutableStateListOf<Offset>() }
                            Box(Modifier.size(width,width*PagePdf.height/PagePdf.width).background(Color.White)
                                .semantics { contentDescription="标记画布，第 ${page+1} 页" }.pointerInput(page,color,bitmap,busy) {
                                    if (bitmap==null || busy) return@pointerInput
                                    fun point(p: Offset)=Offset(p.x.coerceIn(0f,size.width.toFloat())*PagePdf.width/size.width,
                                        p.y.coerceIn(0f,size.height.toFloat())*PagePdf.height/size.height)
                                    awaitEachGesture {
                                        val down=awaitFirstDown(); down.consume()
                                        current.clear(); current.add(point(down.position))
                                        do {
                                            val change=awaitPointerEvent().changes.firstOrNull { it.id==down.id } ?: break
                                            if (change.isConsumed) { current.clear(); break }
                                            val next=point(change.position)
                                            if (next!=current.last()) current.add(next)
                                            change.consume()
                                        } while (change.pressed)
                                        if (current.isNotEmpty()) {
                                            strokes.add(PdfInk(page,color,current.toList())); redo.clear(); current.clear()
                                        }
                                    }
                                }) {
                                bitmap?.let { Image(it,null,Modifier.fillMaxSize()) }
                                if (preview?.isFailure==true) Box(Modifier.align(Alignment.Center)) {
                                    LiquidKitActionButton("重新载入此页",{ attempt++ })
                                }
                                Canvas(Modifier.matchParentSize()) {
                                    drawIntoCanvas { target ->
                                        val c=target.nativeCanvas; val save=c.save()
                                        c.scale(size.width/PagePdf.width,size.height/PagePdf.height)
                                        strokes.forEach { if (it.page==page) it.paint(c) }
                                        if (current.isNotEmpty()) PdfInk(page,color,current).paint(c)
                                        c.restoreToCount(save)
                                    }
                                }
                            }
                        }
                    }
                    LiquidKitGlassSurface(shape=RoundedCornerShape(23.dp)) {
                        Row(Modifier.padding(horizontal=5.dp),verticalAlignment=Alignment.CenterVertically) {
                            MarkControl("上一页",LiquidKitSymbol.Back,page>0) { page-- }
                            ShareCaption("${page+1} / ${pdf.pageCount}",14)
                            MarkControl("下一页",LiquidKitSymbol.Forward,page+1<pdf.pageCount) { page++ }
                            listOf("黑色" to 0xff141416.toInt(),"红色" to 0xffed3b36.toInt(),"蓝色" to 0xff1578eb.toInt()).forEach { (name,value) ->
                                Box(Modifier.size(38.dp).clip(CircleShape).clickable(role=Role.RadioButton) { color=value }
                                    .semantics { contentDescription="${name}画笔"; selected=color==value },contentAlignment=Alignment.Center) {
                                    Box(Modifier.size(if (color==value) 24.dp else 18.dp).background(Color(value),CircleShape)
                                        .border(if (color==value) 2.dp else .5.dp,Color.White,CircleShape))
                                }
                            }
                            MarkControl("撤销",LiquidKitSymbol.Back,strokes.isNotEmpty()) { redo.add(strokes.removeAt(strokes.lastIndex)) }
                            MarkControl("重做",LiquidKitSymbol.Forward,redo.isNotEmpty()) { strokes.add(redo.removeAt(redo.lastIndex)) }
                        }
                    }
                }
            }
            if (confirm) LiquidKitAlert(title="放弃这次标记？",message="本次添加的笔迹将被移除。",
                onDismissRequest={ confirm=false },actions=listOf(
                    LiquidKitAlertAction("继续标记",LiquidKitAlertRole.Cancel) { confirm=false },
                    LiquidKitAlertAction("放弃",LiquidKitAlertRole.Destructive) { confirm=false; visible.targetState=false }))
        }
    }
}

@Composable private fun MarkControl(label: String,symbol: LiquidKitSymbol,enabled: Boolean,click: ()->Unit) {
    Box(Modifier.size(40.dp).clip(CircleShape).clickable(enabled=enabled,role=Role.Button,onClick=click)
        .semantics { contentDescription=label },contentAlignment=Alignment.Center) {
        LiquidKitIcon(symbol,LocalShareInk.current.copy(alpha=if (enabled) 1f else .30f))
    }
}
