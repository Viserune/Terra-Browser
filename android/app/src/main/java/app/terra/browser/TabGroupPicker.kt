package app.terra.browser

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import io.liquidkit.*

private enum class GroupPanel { List, Create, Rename, Dissolve }

@Composable
internal fun TabGroupPicker(a: MainActivity, current: String, target: Page?, dark: Boolean,
                            onSelect: (String) -> Unit, dismiss: () -> Unit, createForLink: ((String) -> Unit)? = null) {
    val ink = if (dark) Color.White else Color(0xff202023)
    val blue = if (dark) Color(0xff85bcff) else Color(0xff175cad)
    val sheetTint = if (dark) Color(0xff28282c) else Color(0xfff6f6f9)
    var panel by remember { mutableStateOf(if (createForLink == null) GroupPanel.List else GroupPanel.Create) }
    var name by remember { mutableStateOf("") }
    val source = target?.groupId ?: current
    fun back() { if (panel == GroupPanel.List || createForLink != null) dismiss() else panel = GroupPanel.List }
    fun choose(id: String) {
        if (target != null) a.moveTab(target, id)
        onSelect(id)
        dismiss()
    }
    fun submit() {
        if (name.isBlank()) return
        if (createForLink != null) {
            createForLink(name.trim())
            return
        }
        val id = if (panel == GroupPanel.Create) a.createGroup(name, target) else {
            if (current !in a.tabGroups) return
            a.tabGroups[current] = name.trim()
            a.save()
            current
        }
        onSelect(id)
        dismiss()
    }
    Dialog(onDismissRequest=::back, properties=DialogProperties(usePlatformDefaultWidth=false, decorFitsSystemWindows=false)) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect { window?.setDimAmount(.18f) }
        val entry = remember { Animatable(0f) }
        LaunchedEffect(Unit) { entry.animateTo(1f, spring(dampingRatio=.87f, stiffness=420f)) }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().clickable(interactionSource=null, indication=null) { back() })
            val tablet = maxWidth >= 600.dp
            val sheetHeight = maxHeight * .7f
            Box(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(12.dp),
                contentAlignment=if (tablet) Alignment.Center else Alignment.BottomCenter) {
                LiquidKitGlassSurface(Modifier.widthIn(max=408.dp).fillMaxWidth().heightIn(max=sheetHeight)
                    .graphicsLayer {
                        alpha=entry.value
                        translationY=(1f-entry.value)*36.dp.toPx()
                        scaleX=.96f+.04f*entry.value
                        scaleY=scaleX
                    },shape=RoundedCornerShape(26.dp),tint=sheetTint.copy(alpha=.72f)) {
                    AnimatedContent(panel,label="标签组面板") { mode ->
                        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) {
                            val title = when (mode) {
                                GroupPanel.List -> if (target == null) "标签组" else "移至标签组"
                                GroupPanel.Create -> "新建标签组"
                                GroupPanel.Rename -> "重新命名"
                                GroupPanel.Dissolve -> "移除标签组"
                            }
                            Row(Modifier.fillMaxWidth().heightIn(min=44.dp),verticalAlignment=Alignment.CenterVertically) {
                                BasicText(title,Modifier.weight(1f),style=TextStyle(color=ink,fontSize=22.sp,fontWeight=FontWeight.SemiBold))
                                Box(Modifier.size(44.dp).clickable(role=Role.Button) { back() }
                                    .semantics { contentDescription="关闭标签组面板" },contentAlignment=Alignment.Center) {
                                    LiquidKitIcon(LiquidKitSymbol.Close,ink,Modifier.size(16.dp))
                                }
                            }
                            when (mode) {
                                GroupPanel.List -> {
                                    val groups = a.groupChoices()
                                    for ((id,label) in groups) {
                                        val count = a.tabs.count { !it.secret && it.groupId == id }
                                        Row(Modifier.fillMaxWidth().heightIn(min=48.dp).clip(RoundedCornerShape(12.dp))
                                            .background(if (id==source) blue.copy(alpha=.10f) else Color.Transparent)
                                            .clickable(role=Role.Button) { choose(id) }.padding(horizontal=12.dp),
                                            verticalAlignment=Alignment.CenterVertically) {
                                            LiquidKitIcon(LiquidKitSymbol.SquareOnSquare,if(id==source) blue else ink,Modifier.size(18.dp))
                                            Spacer(Modifier.width(12.dp))
                                            BasicText(label,Modifier.weight(1f),maxLines=1,overflow=TextOverflow.Ellipsis,style=TextStyle(color=ink,fontSize=16.sp))
                                            BasicText(count.toString(),style=TextStyle(color=ink.copy(alpha=.7f),fontSize=14.sp))
                                        }
                                    }
                                    GroupAction("新建标签组",blue) { name=""; panel=GroupPanel.Create }
                                    if (target==null && current in a.tabGroups) {
                                        GroupAction("重新命名",blue) { name=a.tabGroups.getValue(current); panel=GroupPanel.Rename }
                                        GroupAction("移除标签组",ink) { panel=GroupPanel.Dissolve }
                                    }
                                }
                                GroupPanel.Create, GroupPanel.Rename -> {
                                    val focus = remember { FocusRequester() }
                                    BasicTextField(name,{ name=it },singleLine=true,textStyle=TextStyle(color=ink,fontSize=18.sp),
                                        cursorBrush=SolidColor(blue),keyboardOptions=KeyboardOptions(imeAction=ImeAction.Done),
                                        keyboardActions=KeyboardActions(onDone={ submit() }),modifier=Modifier.fillMaxWidth().focusRequester(focus)
                                            .semantics { contentDescription="标签组名称" }.padding(vertical=12.dp)
                                            .background(ink.copy(alpha=.07f),RoundedCornerShape(12.dp)).padding(14.dp),
                                        decorationBox={ field -> Box {
                                            if (name.isEmpty()) BasicText("标签组名称",style=TextStyle(color=ink.copy(alpha=.55f),fontSize=18.sp))
                                            field()
                                        } })
                                    LaunchedEffect(Unit) { focus.requestFocus() }
                                    GroupAction("保存",blue,enabled=name.isNotBlank(),action=::submit)
                                }
                                GroupPanel.Dissolve -> {
                                    BasicText("保留的页面将移回普通标签。",Modifier.padding(vertical=10.dp),style=TextStyle(color=ink,fontSize=16.sp))
                                    GroupAction("保留组内标签页",blue) { a.removeGroup(current, closePages=false); onSelect(""); dismiss() }
                                    GroupAction("删除组并关闭标签页",if (dark) Color(0xffff9994) else Color(0xffa72523)) {
                                        a.removeGroup(current, closePages=true)
                                        onSelect("")
                                        dismiss()
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

@Composable
private fun GroupAction(label: String, color: Color, enabled: Boolean = true, action: () -> Unit) {
    Box(Modifier.fillMaxWidth().heightIn(min=46.dp).clickable(enabled=enabled,role=Role.Button,onClick=action)
        .padding(horizontal=12.dp),contentAlignment=Alignment.CenterStart) {
        BasicText(label,style=TextStyle(color=if(enabled) color else color.copy(alpha=.4f),fontSize=16.sp))
    }
}
