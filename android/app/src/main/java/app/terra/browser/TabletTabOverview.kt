package app.terra.browser

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.liquidkit.*

@Composable
internal fun TabletOverviewToolbar(
    privateGroup: Boolean, ordinaryName: String, ordinaryCount: Int, enabled: Boolean, ink: Color,
    chooseGroup: (Boolean) -> Unit, showGroups: () -> Unit, options: Modifier,
    add: Modifier, search: () -> Unit, done: Modifier,
) {
    BoxWithConstraints(Modifier.fillMaxWidth().height(64.dp).padding(horizontal=9.dp)) {
        Row(Modifier.align(Alignment.CenterStart), horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            LiquidKitGlassSurface(Modifier.size(44.dp).semantics { contentDescription="标签组" },
                interactive=true, onClick=showGroups) {
                Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) { LiquidKitIcon(LiquidKitSymbol.Sidebar,ink) }
            }
            LiquidKitGlassSurface(Modifier.size(44.dp).then(options), interactive=true) {
                Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) { LiquidKitIcon(LiquidKitSymbol.More,ink) }
            }
        }
        val segmentWidth = (maxWidth * .132f).coerceIn(93.dp,126.dp)
        LiquidKitGlassSurface(Modifier.align(Alignment.Center)) {
            Box(Modifier.padding(3.dp)) {
                val offset by animateDpAsState(if (privateGroup) 0.dp else segmentWidth,
                    spring(dampingRatio=.85f,stiffness=520f),label="浏览组选择")
                Box(Modifier.offset(x=offset).size(segmentWidth,37.dp)
                    .background(ink.copy(alpha=.12f),CircleShape))
                Row {
                    listOf(true,false).forEach { secret ->
                        val label = if (secret) "无痕浏览" else ordinaryName.ifEmpty { "${ordinaryCount} 个标签页" }
                        Box(Modifier.size(segmentWidth,37.dp).clip(CircleShape)
                            .selectable(selected=secret==privateGroup,enabled=enabled,role=Role.Tab) { chooseGroup(secret) }
                            .semantics { contentDescription=if (secret) "无痕浏览" else "普通标签" },
                            contentAlignment=Alignment.Center) {
                            BasicText(label,maxLines=1,overflow=TextOverflow.Ellipsis,
                                style=TextStyle(color=ink.copy(alpha=if (secret==privateGroup) 1f else .68f),
                                    fontSize=16.sp,fontWeight=if (secret==privateGroup) FontWeight.SemiBold else FontWeight.Normal))
                        }
                    }
                }
            }
        }
        Row(Modifier.align(Alignment.CenterEnd),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            LiquidKitGlassSurface(interactive=true) {
                Row {
                    Box(Modifier.size(44.dp).then(add).semantics {
                        contentDescription=if (privateGroup) "新建无痕标签页" else "新建标签页"
                    },contentAlignment=Alignment.Center) { LiquidKitIcon(LiquidKitSymbol.Add,ink) }
                    Box(Modifier.size(44.dp).clickable(enabled=enabled,role=Role.Button,onClick=search)
                        .semantics { contentDescription="搜索标签页" },contentAlignment=Alignment.Center) {
                        LiquidKitIcon(LiquidKitSymbol.Search,ink)
                    }
                }
            }
            LiquidKitGlassSurface(Modifier.size(44.dp).then(done).semantics { contentDescription="完成" },
                interactive=true,tint=Color(0xff3587ec)) {
                Box(Modifier.fillMaxSize().padding(.75.dp).background(Color(0xff3084ea).copy(alpha=.94f),CircleShape),
                    contentAlignment=Alignment.Center) { LiquidKitIcon(LiquidKitSymbol.Check,Color.White) }
            }
        }
    }
}

@Composable
internal fun TabletOverviewCard(tab: Page, label: String, preview: ImageBitmap?, aspect: Float,
    surface: Color, ink: Color, faceBounds: Modifier, flying: Boolean=false, captionAlpha: Float=1f,
    close: (() -> Unit)?=null) {
    val shape = RoundedCornerShape(15.dp)
    Box(Modifier.fillMaxWidth().aspectRatio(aspect).then(faceBounds)
        .graphicsLayer { alpha=if (flying) 0f else 1f }.shadow(3.dp,shape).clip(shape)) {
        PageFace(tab,preview,surface,ink)
        Box(Modifier.align(Alignment.TopEnd).size(44.dp)
            .then(if (close != null) Modifier.clickable(role=Role.Button,onClick=close)
                .semantics { contentDescription="关闭标签页" } else Modifier),
            contentAlignment=Alignment.TopEnd) {
            Box(Modifier.padding(4.dp).size(23.dp).background(Color.White.copy(alpha=.82f),CircleShape),
                contentAlignment=Alignment.Center) { LiquidKitIcon(LiquidKitSymbol.Close,Color(0xff333336),Modifier.size(13.dp)) }
        }
    }
    Row(Modifier.fillMaxWidth().height(31.dp).padding(horizontal=4.dp).graphicsLayer { alpha=captionAlpha },
        horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically) {
        val icon=tab.web?.favicon
        if (icon!=null) Image(icon.asImageBitmap(),null,Modifier.size(14.dp))
        else LiquidKitIcon(LiquidKitSymbol.Globe,ink.copy(alpha=.58f),Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        BasicText(label,maxLines=1,overflow=TextOverflow.Ellipsis,style=TextStyle(color=ink,fontSize=13.sp,fontWeight=FontWeight.Medium))
    }
}
