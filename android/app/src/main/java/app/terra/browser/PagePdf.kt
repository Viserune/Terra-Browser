package app.terra.browser

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.compose.ui.geometry.Offset
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

internal data class PdfInk(val page: Int, val color: Int, val points: List<Offset>) {
    fun paint(canvas: Canvas) {
        val first = points.firstOrNull() ?: return
        val brush = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = this@PdfInk.color
            strokeWidth = 2.7f
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
        }
        if (points.size == 1) {
            canvas.drawCircle(first.x, first.y, brush.strokeWidth / 2, brush)
            return
        }
        brush.style = Paint.Style.STROKE
        val path = Path().apply {
            moveTo(first.x, first.y)
            points.drop(1).forEach { lineTo(it.x, it.y) }
        }
        canvas.drawPath(path, brush)
    }
}

internal data class PdfTile(val file: File, val bounds: RectF)
private data class TextLine(val rect: RectF, val text: String)

private fun removeTiles(directory: File) {
    directory.listFiles()?.forEach { it.delete() }
    directory.delete()
}

internal class PagePdf(
    private val directory: File, private val tiles: List<PdfTile>,
    private val contentWidth: Int, contentHeight: Int, singleViewport: Boolean, layout: JSONObject, cssScale: Float
) : Closeable {
    companion object {
        const val width = 595
        const val height = 842
        private const val margin = 22
    }
    private val widthScale = (width - 2f * margin) / contentWidth
    private val scale = if (singleViewport) minOf(widthScale, (height - 2f * margin) / contentHeight) else widthScale
    private val lines = mutableListOf<TextLine>()
    private val cuts = mutableListOf(0)
    val pageCount get() = cuts.size - 1

    init {
        val length = if (singleViewport) contentHeight else floor((height - 2 * margin) / scale).toInt().coerceAtLeast(1)
        val bands = mutableListOf<IntRange>()
        fun avoid(top: Float, bottom: Float) {
            if (bottom > top && bottom - top < length) bands.add(floor(top).toInt()..ceil(bottom).toInt())
        }
        val text = layout.getJSONArray("lines")
        for (i in 0 until text.length()) {
            val line = text.getJSONArray(i)
            val left = line.getDouble(0).toFloat() * cssScale
            val top = line.getDouble(1).toFloat() * cssScale
            val rect = RectF(left, top, left + line.getDouble(2).toFloat() * cssScale, top + line.getDouble(3).toFloat() * cssScale)
            lines.add(TextLine(rect, line.getString(4)))
            avoid(rect.top, rect.bottom)
        }
        val figures = layout.getJSONArray("figures")
        for (i in 0 until figures.length()) {
            val rect = figures.getJSONArray(i)
            avoid(rect.getDouble(0).toFloat() * cssScale, rect.getDouble(1).toFloat() * cssScale)
        }
        while (cuts.last() < contentHeight) {
            val top = cuts.last()
            var bottom = minOf(contentHeight.toLong(), top.toLong() + length).toInt()
            if (bottom < contentHeight) {
                var next: Int
                do {
                    next = bottom
                    for (band in bands) {
                        if (band.first > top + length / 5 && band.first < bottom && bottom < band.last) bottom = band.first
                    }
                } while (next != bottom)
            }
            cuts.add(bottom)
        }
    }

    suspend fun write(file: File, ink: List<PdfInk> = emptyList()) = withContext(Dispatchers.IO) {
        val output = PdfDocument()
        val text = Paint(Paint.ANTI_ALIAS_FLAG)
        val filter = Paint(Paint.FILTER_BITMAP_FLAG)
        try {
            for (index in 0 until pageCount) {
                currentCoroutineContext().ensureActive()
                val page = output.startPage(PdfDocument.PageInfo.Builder(width, height, index + 1).create())
                try {
                    val canvas = page.canvas
                    val top = cuts[index].toFloat()
                    val bottom = cuts[index + 1].toFloat()
                    canvas.drawColor(Color.WHITE)
                    val checkpoint = canvas.save()
                    canvas.translate((width - contentWidth * scale) / 2, margin.toFloat())
                    canvas.scale(scale, scale)
                    canvas.translate(0f, -top)
                    canvas.clipRect(0f, top, contentWidth.toFloat(), bottom)
                    // Place searchable Unicode below the opaque web rendering, at the same line coordinates.
                    for ((rect, value) in lines) {
                        if (rect.centerY() < top || rect.centerY() >= bottom) continue
                        text.textSize = rect.height()
                        text.textScaleX = 1f
                        text.textScaleX = rect.width() / text.measureText(value).coerceAtLeast(.01f)
                        canvas.drawText(value, rect.left, rect.bottom - text.descent(), text)
                    }
                    for (tile in tiles) {
                        if (tile.bounds.bottom <= top || tile.bounds.top >= bottom) continue
                        val bitmap = BitmapFactory.decodeFile(tile.file.path) ?: throw IOException("无法读取网页画面")
                        canvas.drawBitmap(bitmap, null, tile.bounds, filter)
                        bitmap.recycle()
                    }
                    canvas.restoreToCount(checkpoint)
                    ink.forEach { if (it.page == index) it.paint(canvas) }
                } finally { output.finishPage(page) }
            }
            file.outputStream().buffered().use(output::writeTo)
        } finally { output.close() }
    }

    override fun close() = removeTiles(directory)
}

private class ExportWebView(context: Context) : WebView(context) {
    val documentWidth get() = maxOf(width, computeHorizontalScrollRange())
    val documentHeight get() = maxOf(height, computeVerticalScrollRange())
    suspend fun painted() {
        withTimeoutOrNull<Unit>(15000) {
            suspendCancellableCoroutine<Unit> { continuation ->
                postVisualStateCallback(0, object : VisualStateCallback() {
                    override fun onComplete(requestId: Long) {
                        if (continuation.isActive) continuation.resume(Unit)
                    }
                })
            }
        } ?: throw IOException("网页绘制超时，请重试")
    }
}

internal suspend fun MainActivity.capturePagePdf(item: SharedPage, article: ReaderArticle? = null): PagePdf {
    val source = item.currentDocument() ?: throw IOException("网页已更改")
    val host = findViewById<FrameLayout>(android.R.id.content)
    val directory = withContext(Dispatchers.IO) {
        val root = File(cacheDir, "pdf-render")
        val cutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        root.listFiles()?.filter { it.isDirectory && it.lastModified() < cutoff }?.forEach(::removeTiles)
        File(root, UUID.randomUUID().toString()).apply { if (!mkdirs()) throw IOException("无法创建网页画面缓存") }
    }
    val view = ExportWebView(this)
    val archive = File(directory, "source.mht")
    try {
        if (item.secret) {
            if (privateProfile.isEmpty() || !WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE))
                throw IOException("无痕会话已关闭")
            WebViewCompat.setProfile(view, privateProfile)
        }
        view.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        view.isFocusable = false
        view.isVerticalScrollBarEnabled = false
        view.isHorizontalScrollBarEnabled = false
        val density = resources.displayMetrics.density
        val viewWidth = if (article == null) source.width else (735 * density).roundToInt()
        val viewHeight = if (article == null) source.height else (1024 * density).roundToInt()
        if (viewWidth < 1 || viewHeight < 1) throw IOException("网页尚未完成绘制")
        view.settings.apply {
            javaScriptEnabled = article != null
            allowFileAccess = article == null
            allowContentAccess = false
            blockNetworkLoads = article == null
            offscreenPreRaster = true
            useWideViewPort = source.settings.useWideViewPort
            loadWithOverviewMode = source.settings.loadWithOverviewMode
            if (article == null) textZoom = source.settings.textZoom
            cacheMode = if (item.secret) WebSettings.LOAD_NO_CACHE else WebSettings.LOAD_DEFAULT
        }
        host.addView(view, 0, FrameLayout.LayoutParams(viewWidth, viewHeight))
        val archiveUrl = Uri.fromFile(archive).toString()
        if (article == null) source.saveArchive(archive)
        val origin = if (item.site.url.startsWith("http:", true)) "http://terra-reader.invalid/" else "https://terra-reader.invalid/"
        suspendCancellableCoroutine { continuation ->
            view.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = true
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                    if (request.url.scheme == "file" && request.url.toString() != archiveUrl)
                        return WebResourceResponse("text/plain", "UTF-8", null)
                    return null
                }
                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    if (request.isForMainFrame && continuation.isActive)
                        continuation.resumeWithException(IOException("无法打开用于导出的网页副本"))
                }
                override fun onPageFinished(view: WebView, url: String) {
                    if (continuation.isActive) continuation.resume(Unit)
                }
            }
            if (article == null) view.loadUrl(archiveUrl)
            else view.loadDataWithBaseURL(origin, article.html(BrowserLibrary.readerStyle, ReaderTheme.White, forPdf = true), "text/html", "UTF-8", null)
        }
        val script = withContext(Dispatchers.IO) { assets.open("share-pdf-layout.js").bufferedReader().use { it.readText() } }
        view.painted()
        // Web archives deliberately disable JavaScript. Read line bounds from the matching live DOM.
        val textSource = if (article == null) source else view
        val raw = suspendCancellableCoroutine { continuation ->
            textSource.evaluateJavascript(script) { if (continuation.isActive) continuation.resume(it) }
        }
        val layout = withContext(Dispatchers.Default) {
            runCatching { JSONObject(raw) }.getOrElse { throw IOException("无法读取网页排版，请刷新后重试") }
        }
        val cssScale = view.width / layout.getDouble("viewport").coerceAtLeast(1.0).toFloat()
        val contentWidth = view.documentWidth
        val contentHeight = view.documentHeight
        val tiles = mutableListOf<PdfTile>()
        for (y in 0 until contentHeight step view.height) {
            for (x in 0 until contentWidth step view.width) {
                currentCoroutineContext().ensureActive()
                view.scrollTo(minOf(x,contentWidth-view.width), minOf(y,contentHeight-view.height))
                view.painted()
                val width = minOf(view.width, contentWidth - x)
                val height = minOf(view.height, contentHeight - y)
                // WebView pins the final scroll position to the bottom/right of its viewport.
                // Capture that viewport intact, then crop the portion beyond the previous tile.
                val viewport = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                try {
                    viewport.eraseColor(Color.WHITE)
                    Canvas(viewport).apply { translate(-view.scrollX.toFloat(), -view.scrollY.toFloat()); view.draw(this) }
                    val left = (x - view.scrollX).coerceIn(0, view.width - width)
                    val top = (y - view.scrollY).coerceIn(0, view.height - height)
                    val tile = File(directory, "tile-" + tiles.size + ".png")
                    withContext(Dispatchers.IO) {
                        val bitmap = Bitmap.createBitmap(viewport, left, top, width, height)
                        try {
                            tile.outputStream().buffered().use {
                                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) throw IOException("无法保存网页画面")
                            }
                        } finally { if (bitmap !== viewport) bitmap.recycle() }
                    }
                    tiles.add(PdfTile(tile, RectF(x.toFloat(), y.toFloat(), (x+width).toFloat(), (y+height).toFloat())))
                } finally { viewport.recycle() }
            }
        }
        val singleViewport = contentHeight <= view.height + 1
        return withContext(Dispatchers.Default) {
            PagePdf(directory, tiles, contentWidth, contentHeight, singleViewport, layout, cssScale)
        }
    } catch (failure: Throwable) {
        removeTiles(directory)
        throw failure
    } finally {
        (view.parent as? ViewGroup)?.removeView(view)
        view.stopLoading()
        view.destroy()
        archive.delete()
    }
}
