package app.terra.browser

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.onGloballyPositioned
import com.kyant.backdrop.backdrops.rememberCanvasBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import io.liquidkit.LiquidKitGlassSurface
import io.liquidkit.LocalLiquidKitBackdrop
import io.liquidkit.rememberLiquidKitBackdrop
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

private data class SampleArea(
    val top: Int, val bottom: Int, val viewHeight: Int, val topPadding: Int, val bottomPadding: Int,
    val viewWidth: Int, val screenX: Int, val screenY: Int
)

private data class Sample(val bitmap: Bitmap, val area: SampleArea)

/** Keep the live Chromium surface in the window. LiquidKit only needs webpage pixels
 * in and around its controls, except when a menu or panel can appear anywhere. */
internal class WebGlassCapture {
    private val buffers = arrayOfNulls<Bitmap>(2)
    private val screenLocation = IntArray(2)
    private var next = 0
    private var frame by mutableStateOf<Sample?>(null)

    // The expanded tablet address field sits over the top center of the document.
    // Sample several points so a line of text cannot flip its foreground color.
    val darkAddressBackdrop by derivedStateOf {
        val sample = frame ?: return@derivedStateOf null
        val bitmap = sample.bitmap
        var dark = 0
        var opaque = 0
        for (y in listOf(.18f,.25f,.35f)) for (x in listOf(.3f,.5f,.7f)) {
            val pixel = bitmap.getPixel(((bitmap.width-1)*x).roundToInt(),((bitmap.height-1)*y).roundToInt())
            if (Color.alpha(pixel) >= 192) {
                opaque++
                if (Color.luminance(pixel) < .22f) dark++
            }
        }
        if (opaque == 0) null else dark*2 > opaque
    }

    fun darkUnder(bounds: Rect): Boolean? {
        val sample = frame ?: return null
        val a = sample.area
        val bitmap = sample.bitmap
        val scaleY = bitmap.height.toFloat() / (a.bottom - a.top)
        // The glass extends the first document row into the toolbar inset.
        val firstRow = ceil((a.topPadding - a.top) * scaleY).toInt().coerceAtLeast(0)
        val lastRow = (floor((a.viewHeight - a.bottomPadding - a.top) * scaleY).toInt() - 1).coerceAtMost(bitmap.height - 1)
        if (firstRow > lastRow) return null
        var dark = 0
        var opaque = 0
        for (i in 1..3) {
            val x = ((bounds.left + bounds.width * i / 4 - a.screenX) * bitmap.width / a.viewWidth)
                .roundToInt().coerceIn(0, bitmap.width - 1)
            for (j in 1..3) {
                val y = ((bounds.top + bounds.height * j / 4 - a.screenY - a.top) * scaleY)
                    .roundToInt().coerceIn(firstRow, lastRow)
                val pixel = bitmap.getPixel(x, y)
                if (Color.alpha(pixel) >= 192) {
                    opaque++
                    if (Color.luminance(pixel) < .22f) dark++
                }
            }
        }
        return if (opaque > 0) dark * 2 > opaque else null
    }

    fun sample(view: TabViewport) {
        val margin = (48 * view.resources.displayMetrics.density).roundToInt()
        var top = 0
        var bottom = view.height
        if (!view.fullGlassScene) {
            if (view.bottomClearance > 0 && view.topClearance == 0)
                top = (bottom-view.bottomClearance-margin).coerceAtLeast(0)
            else if (view.topClearance > 0 && view.bottomClearance == 0)
                bottom = (view.topClearance+margin).coerceAtMost(bottom)
        }
        view.getLocationOnScreen(screenLocation)
        val area = SampleArea(top,bottom,view.height,view.paddingTop,view.paddingBottom,
            view.width,screenLocation[0],screenLocation[1])
        val ratio = minOf(1f,540f/maxOf(view.width,view.height))
        val width = (view.width*ratio).roundToInt().coerceAtLeast(1)
        val height = ((bottom-top)*ratio).roundToInt().coerceAtLeast(1)
        val image = buffers[next]?.takeIf { it.width==width && it.height==height }
            ?: Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888).also { buffers[next]=it }
        image.eraseColor(Color.TRANSPARENT)
        val canvas = Canvas(image)
        canvas.scale(width.toFloat()/view.width,height.toFloat()/(bottom-top))
        canvas.translate(0f,-top.toFloat())
        view.drawGlassDocument(canvas)
        val previous = frame
        if (previous?.area==area && image.sameAs(previous.bitmap)) return
        frame = Sample(image,area)
        next = 1-next
    }

    fun clear() {
        frame = null
        buffers.fill(null)
    }

    // A modal glass surface lives in a different window from the WebView. Read
    // the capture during that window's draw pass and align it in screen space.
    fun DrawScope.drawBehindDialog(origin: Offset) {
        val area = frame?.area ?: return
        val x = area.screenX - origin.x
        val y = area.screenY - origin.y
        inset(x, y, size.width - area.viewWidth - x, size.height - area.viewHeight - y) {
            drawSample(includePage = true)
        }
    }

    fun DrawScope.drawSample(includePage: Boolean) {
        val sample = frame ?: return
        val a = sample.area
        if (!includePage && a.topPadding==0 && a.bottomPadding==0) return
        val bitmap = sample.bitmap.asImageBitmap()
        val scale = size.height/a.viewHeight
        val width = size.width.roundToInt()
        val top = (a.top*scale).roundToInt()
        val bottom = (a.bottom*scale).roundToInt()
        if (includePage && bottom>top)
            drawImage(bitmap,dstOffset=IntOffset(0,top),dstSize=IntSize(width,bottom-top))
        // At a document boundary the WebView has an inset. Extend its nearest
        // visible scanline to keep the toolbar background continuous.
        val rowsPerPixel = bitmap.height.toDouble()/(a.bottom-a.top)
        fun fillInset(row: Int, start: Int, end: Int) {
            if (end<=start || row !in 0 until bitmap.height) return
            drawImage(bitmap,srcOffset=IntOffset(0,row),srcSize=IntSize(bitmap.width,1),
                dstOffset=IntOffset(0,start),dstSize=IntSize(width,end-start))
        }
        // Select a whole sampled texel inside Chromium's content. A texel on
        // the inset boundary can contain transparent padding after downscaling.
        if (a.topPadding>0) fillInset(ceil((a.topPadding-a.top)*rowsPerPixel).toInt(),
            0,(a.topPadding*scale).roundToInt())
        if (a.bottomPadding>0) fillInset(floor((a.viewHeight-a.bottomPadding-a.top)*rowsPerPixel).toInt()-1,
            ((a.viewHeight-a.bottomPadding)*scale).roundToInt(),size.height.roundToInt())
    }
}

@Composable
internal fun WebGlassScene(capture: WebGlassCapture, modifier: Modifier) {
    Canvas(modifier) { with(capture) { drawSample(includePage=false) } }
}

@Composable
internal fun DialogGlassSurface(capture: WebGlassCapture?, modifier: Modifier, shape: Shape,
    tint: androidx.compose.ui.graphics.Color, panelBlur: Dp = 0.dp, content: @Composable BoxScope.() -> Unit) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    val glass = if (capture == null) LocalLiquidKitBackdrop.current else rememberCanvasBackdrop {
        with(capture) { drawBehindDialog(origin) }
    }
    val aligned = if (capture == null) modifier else modifier.onGloballyPositioned { origin = it.localToScreen(Offset.Zero) }
    if (panelBlur == 0.dp || capture == null) {
        LiquidKitGlassSurface(aligned, backdrop = glass, shape = shape, tint = tint, content = content)
    } else {
        // Menus cover whole paragraphs. Diffuse the document before LiquidKit
        // applies the smaller control lens, leaving text inside the panel sharp.
        val diffused = rememberLiquidKitBackdrop()
        Box(aligned, propagateMinConstraints = true) {
            Spacer(Modifier.matchParentSize().drawBackdrop(glass, shape = { shape },
                effects = { blur(panelBlur.toPx()) }, highlight = null, shadow = null, exportedBackdrop = diffused))
            LiquidKitGlassSurface(backdrop = diffused, shape = shape, tint = tint, content = content)
        }
    }
}
