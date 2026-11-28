package app.terra.browser

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.liquidkit.LiquidKitIcon
import io.liquidkit.LiquidKitSymbol

internal enum class PhoneTabLayout(val label: String) {
    Compact("紧凑"), Bottom("底部"), Top("顶部")
}

@Composable
internal fun PhonePageMenuIcon(ink: Color) {
    Canvas(Modifier.size(20.dp)) {
        val line = 1.7.dp.toPx()
        drawRoundRect(ink, Offset(2.dp.toPx(),2.dp.toPx()), Size(16.dp.toPx(),8.dp.toPx()),
            CornerRadius(2.dp.toPx()), style=Stroke(line))
        for ((y,end) in listOf(14 to 18, 18 to 13)) {
            drawLine(ink, Offset(2.dp.toPx(),y.dp.toPx()), Offset(end.dp.toPx(),y.dp.toPx()), line, StrokeCap.Round)
        }
    }
}

@Composable
internal fun PhoneLayoutChoices(ink: Color, accent: Color, choose: (PhoneTabLayout) -> Unit) {
    Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.SpaceEvenly) {
        for (layout in PhoneTabLayout.entries) {
            val selected = layout == BrowserLibrary.phoneTabLayout
            val color = if (selected) accent else ink.copy(alpha = .58f)
            Column(Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                .selectable(selected, role = Role.RadioButton) { choose(layout) }.padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Canvas(Modifier.size(38.dp, 72.dp)) {
                    val stroke = 2.dp.toPx()
                    drawRoundRect(color, Offset(stroke,stroke), Size(size.width-2*stroke, size.height-2*stroke),
                        CornerRadius(5.dp.toPx()), style = Stroke(stroke))
                    fun bar(x: Float, y: Float, width: Float, height: Float) {
                        drawRoundRect(color, Offset(size.width*x,size.height*y), Size(size.width*width,size.height*height),
                            CornerRadius(size.height*height/2))
                    }
                    bar(.36f,.065f,.28f,.03f)
                    when (layout) {
                        PhoneTabLayout.Compact -> {
                            bar(.15f,.85f,.10f,.05f)
                            bar(.31f,.85f,.38f,.05f)
                            bar(.75f,.85f,.10f,.05f)
                        }
                        PhoneTabLayout.Bottom -> {
                            bar(.16f,.80f,.68f,.05f)
                            bar(.23f,.88f,.54f,.025f)
                        }
                        PhoneTabLayout.Top -> {
                            bar(.16f,.14f,.68f,.05f)
                            bar(.23f,.88f,.54f,.025f)
                        }
                    }
                }
                BasicText(layout.label, style = TextStyle(color = ink, fontSize = 16.sp))
                LiquidKitIcon(if (selected) LiquidKitSymbol.CheckCircle else LiquidKitSymbol.Unchecked,
                    color, Modifier.size(20.dp))
            }
        }
    }
}
