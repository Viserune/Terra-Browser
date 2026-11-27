package app.terra.browser

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import io.liquidkit.*

@Composable internal fun BookmarkEditor(activity: MainActivity, backdrop: LiquidKitBackdrop, visible: MutableTransitionState<Boolean>) {
    val draft = activity.bookmarkDraft
    var retained by remember { mutableStateOf<BookmarkDraft?>(null) }
    SideEffect {
        if (draft != null || (visible.isIdle && !visible.currentState)) retained = draft
    }
    if (draft == null && visible.isIdle && !visible.currentState) return
    val shown = draft ?: retained ?: return
    val cancel = { if (activity.bookmarkDraft === shown) activity.bookmarkDraft = null }
    val save = { activity.commitBookmark(shown) }
    if (activity.sheet != "书签") {
        if (draft != null) {
            LiquidKitSheet(onDismissRequest = cancel, modifier = Modifier.imePadding(), doneLabel = null) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BookmarkFields(shown, cancel, save)
                }
            }
        }
        return
    }
    Dialog(onDismissRequest = cancel, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        DisposableEffect(window) {
            val previous = window?.attributes?.dimAmount
            window?.setDimAmount(.20f)
            onDispose { if (previous != null) window.setDimAmount(previous) }
        }
        val colors = liquidKitColors()
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(12.dp)) {
            Box(Modifier.matchParentSize().clickable(interactionSource = null, indication = null,
                onClickLabel = "关闭书签编辑器", onClick = cancel))
            val wide = maxWidth >= 626.dp
            val anchor = if (wide) Alignment.Center else Alignment.BottomCenter
            AnimatedVisibility(visible, Modifier.align(anchor),
                enter = fadeIn(tween(140)) + slideInVertically(spring(dampingRatio = .88f, stiffness = 480f)) { if (wide) it/8 else it },
                exit = fadeOut(tween(110)) + slideOutVertically(tween(150)) { if (wide) it/8 else it }) {
                LiquidKitGlassSurface(Modifier.widthIn(max = 460.dp).fillMaxWidth(),
                    shape = RoundedCornerShape(26.dp), backdrop = backdrop, tint = colors.surface.copy(alpha = .65f)) {
                    Column(Modifier.fillMaxWidth().pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }
                        .verticalScroll(rememberScrollState()).padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        BookmarkFields(shown, cancel, save)
                    }
                }
            }
        }
    }
}

@Composable private fun BookmarkFields(shown: BookmarkDraft, cancel: () -> Unit, save: () -> Unit) {
    val colors = liquidKitColors()
    val focus = LocalFocusManager.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween) {
        LiquidKitActionButton("取消", cancel)
        BasicText(if (shown.original == null) "添加书签" else "编辑书签", Modifier.semantics { heading() },
            style = TextStyle(color = colors.ink, fontSize = 20.sp, fontWeight = FontWeight.SemiBold))
        LiquidKitActionButton("保存", save, style = LiquidKitActionStyle.Filled)
    }
    if (shown.error.isNotEmpty()) BasicText(shown.error,
        Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        style = TextStyle(color = colors.ink, fontSize = 14.sp))
    BasicText("名称", style = TextStyle(color = colors.secondary, fontSize = 14.sp))
    LiquidKitTextField(shown.name, { shown.name = it; shown.error = "" }, "书签名称",
        style = LiquidKitTextFieldStyle.RoundedBorder, onDone = { focus.moveFocus(FocusDirection.Next) })
    BasicText("网址", style = TextStyle(color = colors.secondary, fontSize = 14.sp))
    LiquidKitTextField(shown.address, { shown.address = it; shown.error = "" }, "书签网址",
        style = LiquidKitTextFieldStyle.RoundedBorder, onDone = save)
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        BasicText("在个人收藏中显示", Modifier.weight(1f), style = TextStyle(color = colors.ink, fontSize = 16.sp))
        LiquidKitToggle(selected = { shown.favorite }, onSelect = { shown.favorite = it },
            modifier = Modifier.semantics { contentDescription = "起始页收藏开关" })
    }

}
