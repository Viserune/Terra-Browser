package app.terra.browser

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import io.liquidkit.LiquidKitIcon
import io.liquidkit.LiquidKitSymbol

@Composable internal fun ShareFormatOptions(visible: MutableTransitionState<Boolean>, formats: List<ShareFormat>,
    selected: ShareFormat, capture: WebGlassCapture?, surface: Color, select: (ShareFormat)->Unit, close: ()->Unit) {
    val ink=LocalShareInk.current
    val dark=ink.luminance()>.5f
    val groupColor=if (dark) Color(0xff363638) else Color.White
    val noRipple=remember { MutableInteractionSource() }
    AnimatedVisibility(visibleState=visible,enter=EnterTransition.None,exit=ExitTransition.None) {
        val shade by transition.animateFloat(transitionSpec={
            if (targetState==EnterExitState.Visible) tween(350)
            else tween(215,easing=CubicBezierEasing(.333f,.72f,.667f,.98f))
        },
            label="选项背景") { if (it==EnterExitState.Visible) .21f else 0f }
        val travel by transition.animateFloat(transitionSpec={
            if (targetState==EnterExitState.Visible) tween(530,easing=CubicBezierEasing(.16f,.88f,.22f,1f))
            else tween(260,easing=CubicBezierEasing(.333f,.667f,.667f,1f))
        },label="选项窗口位移") { if (it==EnterExitState.Visible) 0f else 1f }
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.matchParentSize().graphicsLayer { alpha=shade }.background(Color.Black)
                .clickable(noRipple,indication=null,onClick=close).clearAndSetSemantics {})
            val insets=WindowInsets.safeDrawing.asPaddingValues()
            val vertical=maxOf(insets.calculateTopPadding(),insets.calculateBottomPadding())+16.dp
            BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                .padding(horizontal=16.dp,vertical=vertical),contentAlignment=Alignment.Center) {
                val height=minOf(738.dp,maxHeight)
                val distance=(maxHeight+height)/2+vertical
                DialogGlassSurface(capture,Modifier.width(minOf(540.dp,maxWidth)).height(height).graphicsLayer {
                    translationY=distance.toPx()*travel
                }.semantics { paneTitle="共享选项" },RoundedCornerShape(30.dp),
                    (if (dark) surface else Color(0xfff6f6f6)).copy(alpha=.98f),panelBlur=18.dp) {
                    Column(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures {} }
                        .verticalScroll(rememberScrollState()).padding(horizontal=16.dp,vertical=10.dp)) {
                        Box(Modifier.fillMaxWidth().height(44.dp),contentAlignment=Alignment.Center) {
                            ShareCaption("选项",18,true)
                            Box(Modifier.align(Alignment.CenterStart).offset(x=(-6).dp).size(44.dp).clip(CircleShape)
                                .border(.7.dp,ink.copy(alpha=.18f),CircleShape).clickable(role=Role.Button,onClick=close)
                                .semantics { contentDescription="关闭共享选项" },contentAlignment=Alignment.Center) {
                                LiquidKitIcon(LiquidKitSymbol.Close,ink,Modifier.size(27.dp))
                            }
                        }
                        Spacer(Modifier.height(38.dp))
                        Box(Modifier.padding(start=19.dp,bottom=5.dp)) { ShareCaption("发送形式",15,true,secondary=true) }
                        Column(Modifier.fillMaxWidth().selectableGroup().clip(RoundedCornerShape(24.dp)).background(groupColor)) {
                            formats.forEachIndexed { index,option ->
                                Row(Modifier.fillMaxWidth().selectable(selected==option,role=Role.RadioButton) { select(option) }
                                    .heightIn(min=52.dp).padding(horizontal=19.dp),verticalAlignment=Alignment.CenterVertically) {
                                    Box(Modifier.weight(1f)) { ShareCaption(option.label,17) }
                                    if (option==selected) LiquidKitIcon(LiquidKitSymbol.Check,Color(0xff087eff),Modifier.size(26.dp))
                                }
                                if (index<formats.lastIndex) Spacer(Modifier.fillMaxWidth().padding(horizontal=19.dp)
                                    .height(.6.dp).background(ink.copy(alpha=.14f)))
                            }
                        }
                        Box(Modifier.padding(horizontal=19.dp,vertical=8.dp)) {
                            ShareCaption("“自动”会根据所选应用或操作选择合适的格式。",13,secondary=true,lines=3)
                        }
                    }
                }
            }
        }
    }
}

@Composable internal fun ShareFileIcon(format: ShareFormat) {
    val ink=LocalShareInk.current
    Box(Modifier.size(42.dp,52.dp),contentAlignment=Alignment.BottomCenter) {
        Canvas(Modifier.matchParentSize()) {
            val w=size.width; val h=size.height; val fold=w*.30f
            val paper=Path().apply { moveTo(2f,1f); lineTo(w-fold,1f); lineTo(w-2,h*.23f); lineTo(w-2,h-2)
                lineTo(2f,h-2); close() }
            drawPath(paper,if (ink.luminance()<.4f) Color.White else Color(0xff414145))
            drawPath(paper,ink.copy(alpha=.24f),style=Stroke(1.dp.toPx()))
            drawLine(ink.copy(alpha=.24f),Offset(w-fold,1f),Offset(w-fold,h*.23f),1.dp.toPx())
            drawLine(ink.copy(alpha=.24f),Offset(w-fold,h*.23f),Offset(w-2,h*.23f),1.dp.toPx())
            for (line in 0..2) drawLine(ink.copy(alpha=.28f),Offset(w*.20f,h*(.40f+line*.09f)),
                Offset(w*if(line==2) .65f else .80f,h*(.40f+line*.09f)),1.4.dp.toPx())
        }
        Box(Modifier.padding(bottom=4.dp)) { ShareCaption(if (format.pdf) "PDF" else "MHT",8,true,secondary=true) }
    }
}
