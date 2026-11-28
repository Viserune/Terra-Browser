package com.terra.browser.reader

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color as AndroidColor
import android.text.TextUtils
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import com.kyant.backdrop.backdrops.rememberSolidColorBackdrop
import com.terra.browser.model.BrowserTab
import com.terra.browser.ui.SafariReadingGlassesIcon
import com.terra.browser.ui.TerraSymbolIcon
import io.liquidkit.LiquidKitActionButton
import io.liquidkit.LiquidKitActionStyle
import io.liquidkit.LiquidKitControlSize
import io.liquidkit.LiquidKitGlassSurface
import io.liquidkit.LiquidKitIconButton
import io.liquidkit.LiquidKitSymbol
import org.json.JSONObject

enum class ReaderTheme(
    val label: String,
    val paperHex: String,
    val inkHex: String,
    val linkHex: String,
) {
    AUTO("自动", "", "", ""),
    WHITE("白色", "#FFFFFF", "#222222", "#125CAA"),
    SEPIA("纸色", "#F3E8D2", "#3E3227", "#78502B"),
    GRAY("深灰", "#35363A", "#F1F0ED", "#A6CBFF"),
    BLACK("黑色", "#111112", "#E9E8E5", "#95BEFF");

    fun resolve(appDark: Boolean): ReaderTheme = if (this != AUTO) this else if (appDark) BLACK else WHITE
}

val ReaderFontSizes = listOf(80, 90, 100, 110, 125, 150, 175, 200)

data class ReaderStyle(
    val theme: ReaderTheme = ReaderTheme.AUTO,
    val sans: Boolean = false,
    val percent: Int = 100,
)

data class ReaderChapter(
    val id: String,
    val title: String,
    val level: Int,
)

class ReaderArticle(
    val title: String,
    private val lang: String,
    private val body: String,
    val chapters: List<ReaderChapter>,
) {
    fun html(style: ReaderStyle, tone: ReaderTheme): String = """
        <!doctype html><html lang="${esc(lang)}"><head><meta charset="utf-8">
        <meta http-equiv="Content-Security-Policy" content="default-src 'none'; img-src https: http:; style-src 'unsafe-inline'; base-uri 'none'; form-action 'none'">
        <meta name="referrer" content="no-referrer"><meta name="viewport" content="width=device-width,initial-scale=1">
        <title>${esc(title)}</title><style>
        :root{${variables(style, tone)}}*{box-sizing:border-box}
        html,body{margin:0;background:var(--paper);color:var(--ink)}
        main{max-width:42rem;margin:auto;padding:24px clamp(20px,5vw,54px) 112px;overflow-wrap:anywhere;
        font-family:var(--face);font-size:var(--size);line-height:1.65}
        h2,h3{scroll-margin-top:24px}
        h1,h2,h3,h4,h5,h6{line-height:1.24;margin:1.15em 0 .5em}h1{font-size:1.85em;margin:0 0 .8em}
        p{margin:0 0 1em}blockquote{margin:1em 0;border-inline-start:3px solid var(--link);padding-inline-start:1em}
        ul,ol{padding-inline-start:1.6em}li{margin:.28em 0}a{color:var(--link)}
        figure{margin:1.2em 0}img{display:block;max-width:100%;height:auto;margin:.6em auto}
        figcaption{font-size:.84em}pre{overflow:auto;white-space:pre;padding:.6em;background:#8882}
        table{display:block;overflow-x:auto;border-collapse:collapse}td,th{padding:.35em .65em;border:1px solid #8887}
        </style></head><body><main><h1>${esc(title)}</h1>$body</main></body></html>
    """.trimIndent()

    companion object {
        private fun esc(s: String) = TextUtils.htmlEncode(s)
        private val allowedTags = setOf(
            "div", "p", "h1", "h2", "h3", "h4", "h5", "h6", "blockquote",
            "ul", "ol", "li", "pre", "code", "strong", "b", "em", "i", "a",
            "sub", "sup", "figure", "figcaption", "table", "thead", "tbody",
            "tr", "th", "td", "time",
        )

        private fun isHttpUrl(url: String): Boolean =
            url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)

        fun variables(style: ReaderStyle, tone: ReaderTheme): String =
            "--paper:${tone.paperHex};--ink:${tone.inkHex};--link:${tone.linkHex};--face:${if (style.sans) "sans-serif" else "serif"};--size:${19.0 * style.percent / 100}px;"

        fun parse(json: String?, fallback: String): ReaderArticle? {
            if (json == null || json.length > 2_400_000) return null
            val data = runCatching { JSONObject(json) }.getOrNull() ?: return null
            val events = data.optJSONArray("events") ?: return null
            val stack = ArrayDeque<String>()
            val chapters = mutableListOf<ReaderChapter>()
            var chapterId = ""
            var chapterTag = ""
            val chapterText = StringBuilder()
            var characters = 0
            var truncated = data.optBoolean("truncated") || events.length() > 24000
            val html = buildString {
                for (i in 0 until events.length().coerceAtMost(24000)) {
                    if (length > 600_000) {
                        truncated = true
                        break
                    }
                    val event = events.optJSONArray(i) ?: continue
                    val kind = event.optString(0)
                    val value = event.optString(1)
                    when (kind) {
                        "text" -> {
                            val text = value.take(180_000 - characters)
                            characters += text.length
                            append(esc(text))
                            if (chapterTag.isNotEmpty() && chapterText.length < 320) {
                                chapterText.append(text.take(320 - chapterText.length))
                            }
                        }
                        "open" -> if (value in allowedTags && stack.size < 64) {
                            append("<$value")
                            val rawId = event.optString(2).take(256)
                            var elementId = if (rawId.isEmpty()) "" else "r$rawId"
                            if (value == "h2" || value == "h3") {
                                if (elementId.isEmpty()) elementId = "s$i"
                                chapterId = elementId
                                chapterTag = value
                                chapterText.clear()
                            }
                            if (elementId.isNotEmpty()) append(" id=\"${esc(elementId)}\"")
                            val attr = event.optString(3).take(4096)
                            if (value == "a" && attr.startsWith("#")) {
                                append(" href=\"#r${esc(android.net.Uri.encode(attr.drop(1)))}\"")
                            } else if (value == "a" && isHttpUrl(attr)) {
                                append(" href=\"${esc(attr)}\"")
                            }
                            if (value == "ol") {
                                attr.toIntOrNull()?.let { append(" start=\"$it\"") }
                            }
                            append(">")
                            stack.addLast(value)
                        }
                        "close" -> if (stack.lastOrNull() == value) {
                            append("</${stack.removeLast()}>")
                            if (value == chapterTag) {
                                val name = chapterText.trim().toString()
                                if (name.isNotEmpty()) {
                                    chapters.add(ReaderChapter(chapterId, name, if (value == "h2") 2 else 3))
                                }
                                chapterTag = ""
                            }
                        }
                        "img" -> if (value.length <= 4096 && isHttpUrl(value)) {
                            append("<img loading=\"lazy\" src=\"${esc(value)}\" alt=\"${esc(event.optString(2).take(512))}\"")
                            val width = event.optInt(3)
                            val height = event.optInt(4)
                            if (width in 1..65535 && height in 1..65535) {
                                append(" width=\"$width\" height=\"$height\"")
                            }
                            append(">")
                        }
                        "br", "hr" -> append("<$kind>")
                    }
                }
                while (stack.isNotEmpty()) {
                    append("</${stack.removeLast()}>")
                }
                if (truncated) {
                    append("<p>这篇文章较长，请返回原网页继续阅读。</p>")
                }
            }
            if (characters == 0) return null
            return ReaderArticle(
                title = data.optString("title").take(512).ifBlank { fallback },
                lang = data.optString("lang").take(32),
                body = html,
                chapters = chapters,
            )
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
private class ReaderWebView(
    context: Context,
    private val tabId: String,
    url: String,
    private val article: ReaderArticle,
    initialStyle: ReaderStyle,
    initialTone: ReaderTheme,
    private val onOpenUrl: (String) -> Unit,
) : WebView(context) {
    private val origin = "${if (url.startsWith("http:", ignoreCase = true)) "http" else "https"}://terra-reader.invalid/$tabId/"
    private var ready = false
    private var currentStyle = initialStyle
    private var currentTone = initialTone

    init {
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
        settings.apply {
            javaScriptEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }
        setBackgroundColor(AndroidColor.parseColor(initialTone.paperHex))
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (!request.isForMainFrame) return true
                val nextUrl = request.url.toString()
                if (nextUrl.substringBefore('#') == origin && request.url.fragment != null) return false
                if (request.hasGesture() && (nextUrl.startsWith("http://", true) || nextUrl.startsWith("https://", true))) {
                    onOpenUrl(nextUrl)
                }
                return true
            }

            override fun onPageFinished(view: WebView, loadedUrl: String) {
                if (ready || loadedUrl.substringBefore('#') != origin) return
                ready = true
                applyAppearance()
            }
        }
        loadDataWithBaseURL(origin, article.html(initialStyle, initialTone), "text/html", "UTF-8", null)
    }

    fun restyle(style: ReaderStyle, tone: ReaderTheme) {
        if (currentStyle == style && currentTone == tone) return
        currentStyle = style
        currentTone = tone
        setBackgroundColor(AndroidColor.parseColor(tone.paperHex))
        if (ready) applyAppearance()
    }

    fun scrollToChapter(chapterId: String) {
        if (ready) {
            evaluateJavascript(
                "document.getElementById(${JSONObject.quote(chapterId)})?.scrollIntoView({behavior:'smooth'});",
                null,
            )
        }
    }

    private fun applyAppearance() {
        val variables = JSONObject.quote(ReaderArticle.variables(currentStyle, currentTone))
        evaluateJavascript(
            """(() => {
                const anchor = document.elementFromPoint(innerWidth / 2, 12)?.closest('p,li,h1,h2,h3,blockquote,pre,figure');
                const y = anchor?.getBoundingClientRect().top;
                document.documentElement.style.cssText = $variables;
                if (anchor && y != null) window.scrollBy(0, anchor.getBoundingClientRect().top - y);
            })();""".trimIndent(),
            null,
        )
    }
}

@Composable
fun SafariReaderOverlay(
    tab: BrowserTab,
    article: ReaderArticle,
    readerStyle: ReaderStyle,
    appDark: Boolean,
    onUpdateStyle: (ReaderStyle) -> Unit,
    onOpenUrl: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val resolvedTone = readerStyle.theme.resolve(appDark)
    val paperColor = Color(AndroidColor.parseColor(resolvedTone.paperHex))
    val inkColor = Color(AndroidColor.parseColor(resolvedTone.inkHex))
    val readerBackdrop = rememberSolidColorBackdrop(paperColor)
    val accentBlue = Color(0xFF0A84FF)
    var activePanel by remember { mutableStateOf("") } // "" | "options" | "chapters"
    var readerWebView by remember { mutableStateOf<ReaderWebView?>(null) }

    val appear = remember { Animatable(0f) }
    LaunchedEffect(article) {
        appear.snapTo(0f)
        appear.animateTo(1f, tween(190))
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .zIndex(35f)
            .graphicsLayer { alpha = appear.value }
            .background(paperColor),
    ) {
        AndroidView(
            factory = { ctx ->
                ReaderWebView(
                    context = ctx,
                    tabId = tab.id,
                    url = tab.url,
                    article = article,
                    initialStyle = readerStyle,
                    initialTone = resolvedTone,
                    onOpenUrl = onOpenUrl,
                ).also { readerWebView = it }
            },
            update = { view ->
                view.restyle(readerStyle, resolvedTone)
            },
            onRelease = { view ->
                if (readerWebView === view) readerWebView = null
                view.stopLoading()
                view.destroy()
            },
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .displayCutoutPadding(),
        )

        // Expanded Reader Options or Chapters Glass Card
        AnimatedVisibility(
            visible = activePanel.isNotEmpty(),
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(120)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .displayCutoutPadding()
                .padding(horizontal = 18.dp, vertical = 78.dp),
        ) {
            LiquidKitGlassSurface(
                backdrop = readerBackdrop,
                shape = RoundedCornerShape(24.dp),
                tint = paperColor.copy(alpha = 0.94f),
                modifier = Modifier
                    .widthIn(max = 460.dp)
                    .fillMaxWidth()
                    .border(0.75.dp, inkColor.copy(alpha = 0.14f), RoundedCornerShape(24.dp)),
            ) {
                Box(modifier = Modifier.padding(16.dp)) {
                    if (activePanel == "options") {
                        ReaderOptionsPanel(
                            style = readerStyle,
                            ink = inkColor,
                            accentBlue = accentBlue,
                            backdrop = readerBackdrop,
                            onChange = onUpdateStyle,
                        )
                    } else if (activePanel == "chapters") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 320.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            BasicText(
                                text = "目录 (${article.chapters.size})",
                                style = TextStyle(color = inkColor.copy(alpha = 0.7f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                            )
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                items(article.chapters, key = { it.id }) { chapter ->
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null,
                                            ) {
                                                activePanel = ""
                                                readerWebView?.scrollToChapter(chapter.id)
                                            }
                                            .padding(
                                                start = if (chapter.level >= 3) 20.dp else 8.dp,
                                                end = 8.dp,
                                                top = 8.dp,
                                                bottom = 8.dp,
                                            ),
                                    ) {
                                        BasicText(
                                            text = chapter.title,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            style = TextStyle(color = inkColor, fontSize = 15.sp, fontWeight = FontWeight.Medium),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Bottom Safari Reader Control Pill
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .displayCutoutPadding()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LiquidKitIconButton(
                onClick = onClose,
                backdrop = readerBackdrop,
                size = 46.dp,
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    TerraSymbolIcon(symbol = LiquidKitSymbol.Close, tint = inkColor, modifier = Modifier.size(18.dp))
                }
            }

            LiquidKitGlassSurface(
                backdrop = readerBackdrop,
                shape = CircleShape,
                tint = inkColor.copy(alpha = 0.08f),
                modifier = Modifier
                    .height(46.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        activePanel = if (activePanel == "options") "" else "options"
                    },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SafariReadingGlassesIcon(color = inkColor, modifier = Modifier.size(18.dp))
                    BasicText(
                        text = tab.host.ifBlank { "阅读器视图" },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(color = inkColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.widthIn(max = 180.dp),
                    )
                    BasicText(
                        text = "Aa",
                        style = TextStyle(
                            color = if (activePanel == "options") accentBlue else inkColor.copy(alpha = 0.78f),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                }
            }

            if (article.chapters.isNotEmpty()) {
                LiquidKitIconButton(
                    onClick = { activePanel = if (activePanel == "chapters") "" else "chapters" },
                    backdrop = readerBackdrop,
                    size = 46.dp,
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        TerraSymbolIcon(
                            symbol = LiquidKitSymbol.List,
                            tint = if (activePanel == "chapters") accentBlue else inkColor,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReaderOptionsPanel(
    style: ReaderStyle,
    ink: Color,
    accentBlue: Color,
    backdrop: com.kyant.backdrop.Backdrop,
    onChange: (ReaderStyle) -> Unit,
) {
    val sizeIdx = ReaderFontSizes.indexOf(style.percent).let { if (it >= 0) it else 2 }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LiquidKitActionButton(
                label = "A-",
                onClick = { if (sizeIdx > 0) onChange(style.copy(percent = ReaderFontSizes[sizeIdx - 1])) },
                enabled = sizeIdx > 0,
                style = LiquidKitActionStyle.Tinted,
                tint = ink,
                controlSize = LiquidKitControlSize.Small,
                backdrop = backdrop,
            )
            LiquidKitActionButton(
                label = "${style.percent}%",
                onClick = { onChange(style.copy(percent = 100)) },
                style = LiquidKitActionStyle.Plain,
                tint = accentBlue,
                controlSize = LiquidKitControlSize.Small,
                backdrop = backdrop,
            )
            LiquidKitActionButton(
                label = "A+",
                onClick = { if (sizeIdx < ReaderFontSizes.lastIndex) onChange(style.copy(percent = ReaderFontSizes[sizeIdx + 1])) },
                enabled = sizeIdx < ReaderFontSizes.lastIndex,
                style = LiquidKitActionStyle.Tinted,
                tint = ink,
                controlSize = LiquidKitControlSize.Small,
                backdrop = backdrop,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(false to "衬线体", true to "无衬线体").forEach { (sans, label) ->
                val selected = style.sans == sans
                LiquidKitActionButton(
                    label = label,
                    onClick = { onChange(style.copy(sans = sans)) },
                    style = if (selected) LiquidKitActionStyle.Filled else LiquidKitActionStyle.Tinted,
                    tint = if (selected) accentBlue else ink,
                    controlSize = LiquidKitControlSize.Small,
                    backdrop = backdrop,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ReaderTheme.entries.forEach { tone ->
                val selected = tone == style.theme
                LiquidKitActionButton(
                    label = tone.label,
                    onClick = { onChange(style.copy(theme = tone)) },
                    leadingSymbol = if (selected) LiquidKitSymbol.Check else null,
                    style = if (selected) LiquidKitActionStyle.Filled else LiquidKitActionStyle.Tinted,
                    tint = if (selected) accentBlue else ink,
                    controlSize = LiquidKitControlSize.Small,
                    backdrop = backdrop,
                )
            }
        }
    }
}
