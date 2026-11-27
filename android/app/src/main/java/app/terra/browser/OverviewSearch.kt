package app.terra.browser

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.liquidkit.LiquidKitGlassSurface
import io.liquidkit.LiquidKitIcon
import io.liquidkit.LiquidKitSymbol
import kotlin.math.abs

internal fun Modifier.revealTabSearch(state: LazyGridState, onProgress: (Float) -> Unit, onReveal: () -> Unit) =
    pointerInput(state) {
        val threshold = 62.dp.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed=false, pass=PointerEventPass.Initial)
            if (state.canScrollBackward) return@awaitEachGesture
            try {
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.singleOrNull() ?: break
                    if (change.id != down.id) break
                    val delta: Offset = change.position - down.position
                    if (abs(delta.x) > viewConfiguration.touchSlop && abs(delta.x) > abs(delta.y)) break
                    val distance = (delta.y - viewConfiguration.touchSlop).coerceAtLeast(0f)
                    onProgress((distance / threshold).coerceAtMost(1f))
                    if (!change.pressed && distance >= threshold) onReveal()
                } while (change.pressed)
            } finally {
                onProgress(0f)
            }
        }
    }

@Composable
internal fun OverviewSearchField(value: String, update: (String) -> Unit, ink: Color, surface: Color, onCancel: () -> Unit) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val style = TextStyle(color=ink,fontSize=16.sp)
    Row(Modifier.fillMaxWidth().height(54.dp), verticalAlignment=Alignment.CenterVertically) {
        LiquidKitGlassSurface(Modifier.weight(1f).height(40.dp),shape=RoundedCornerShape(20.dp),tint=surface.copy(alpha=.52f)) {
            Row(Modifier.fillMaxSize().padding(start=12.dp),verticalAlignment=Alignment.CenterVertically) {
                LiquidKitIcon(LiquidKitSymbol.Search,ink.copy(alpha=.6f),Modifier.size(18.dp))
                BasicTextField(value,update,Modifier.weight(1f).fillMaxHeight().focusRequester(focus)
                    .semantics { contentDescription="搜索当前组的标签页" }.padding(horizontal=8.dp),
                    singleLine=true,textStyle=style,cursorBrush=SolidColor(ink),
                    keyboardOptions=KeyboardOptions(imeAction=ImeAction.Search,autoCorrectEnabled=false),
                    keyboardActions=KeyboardActions(onSearch={ keyboard?.hide() }),
                    decorationBox={ input -> Box(Modifier.fillMaxSize(),contentAlignment=Alignment.CenterStart) {
                        if (value.isEmpty()) BasicText("搜索标签页",style=style.copy(color=ink.copy(alpha=.55f)))
                        input()
                    } })
                if (value.isNotEmpty()) Box(Modifier.size(40.dp).clickable(role=Role.Button) { update(""); focus.requestFocus(); keyboard?.show() }
                    .semantics { contentDescription="清除搜索" },contentAlignment=Alignment.Center) {
                    LiquidKitIcon(LiquidKitSymbol.Close,ink.copy(alpha=.65f),Modifier.size(15.dp))
                }
            }
        }
        Box(Modifier.height(48.dp).clickable(role=Role.Button) { keyboard?.hide(); onCancel() }.padding(horizontal=10.dp),
            contentAlignment=Alignment.Center) { BasicText("取消",style=style) }
    }
    LaunchedEffect(Unit) { focus.requestFocus(); keyboard?.show() }
}
