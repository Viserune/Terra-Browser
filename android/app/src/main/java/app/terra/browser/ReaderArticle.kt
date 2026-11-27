package app.terra.browser

import android.text.TextUtils
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

internal enum class ReaderTheme(val label: String, val paper: String, val ink: String, val link: String) {
    Auto("自动", "", "", ""),
    White("白色", "#ffffff", "#222222", "#125caa"),
    Sepia("纸色", "#f3e8d2", "#3e3227", "#78502b"),
    Gray("深灰", "#35363a", "#f1f0ed", "#a6cbff"),
    Black("黑色", "#111112", "#e9e8e5", "#95beff");
    fun resolve(dark: Boolean) = if (this != Auto) this else if (dark) Black else White
}

internal val readerSizes = listOf(80, 90, 100, 110, 125, 150, 175, 200)
internal data class ReaderStyle(val theme: ReaderTheme = ReaderTheme.Auto, val sans: Boolean = false, val percent: Int = 100)

internal data class ReaderChapter(val id: String, val title: String, val level: Int)
internal class ReaderArticle(val title: String, private val lang: String, private val body: String, val chapters: List<ReaderChapter>) {
    fun html(style: ReaderStyle, tone: ReaderTheme): String = """
        <!doctype html><html lang="${esc(lang)}"><head><meta charset="utf-8">
        <meta http-equiv="Content-Security-Policy" content="default-src 'none'; img-src https: http:; style-src 'unsafe-inline'; base-uri 'none'; form-action 'none'">
        <meta name="referrer" content="no-referrer"><meta name="viewport" content="width=device-width,initial-scale=1">
        <title>${esc(title)}</title><style>
        :root{${variables(style,tone)}}*{box-sizing:border-box}
        html,body{margin:0;background:var(--paper);color:var(--ink)}
        main{max-width:42rem;margin:auto;padding:36px clamp(20px,5vw,54px) 104px;overflow-wrap:anywhere;
        font-family:var(--face);font-size:var(--size);line-height:1.65}
        h2,h3{scroll-margin-top:9px}@media(min-width:650px){main{padding-top:86px;padding-bottom:40px}h2,h3{scroll-margin-top:76px}}
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
        private val tags = "div p h1 h2 h3 h4 h5 h6 blockquote ul ol li pre code strong b em i a sub sup figure figcaption table thead tbody tr th td time".split(" ").toSet()
        fun variables(style: ReaderStyle, tone: ReaderTheme) =
            "--paper:${tone.paper};--ink:${tone.ink};--link:${tone.link};--face:${if(style.sans) "sans-serif" else "serif"};--size:${19.0 * style.percent / 100}px;"

        fun parse(json: String?, fallback: String): ReaderArticle? {
            if (json == null || json.length > 2400000) return null
            val data = runCatching { JSONObject(json) }.getOrNull() ?: return null
            val events = data.optJSONArray("events") ?: return null
            val stack = ArrayDeque<String>()
            val chapters = mutableListOf<ReaderChapter>()
            var chapterId = ""
            var chapterTag = ""
            val chapterText = StringBuilder()
            var characters = 0
            var truncated = data.optBoolean("truncated") || events.length() > 24000
            // The page controls the extraction result. Construct markup only from known elements and escaped text.
            val html = buildString {
                for (i in 0 until events.length().coerceAtMost(24000)) {
                    if (length > 600000) { truncated = true; break }
                    val event = events.optJSONArray(i) ?: continue
                    val value = event.optString(1)
                    when (event.optString(0)) {
                        "text" -> {
                            val text = value.take(180000-characters)
                            characters += text.length
                            append(esc(text))
                            if (chapterTag.isNotEmpty() && chapterText.length < 320) chapterText.append(text.take(320-chapterText.length))
                        }
                        "open" -> if (value in tags && stack.size < 64) {
                            append("<$value")
                            val id = event.optString(2).take(256)
                            var elementId = if (id.isEmpty()) "" else "r$id"
                            if (value == "h2" || value == "h3") {
                                if (elementId.isEmpty()) elementId = "s$i"
                                chapterId = elementId
                                chapterTag = value
                                chapterText.clear()
                            }
                            if (elementId.isNotEmpty()) append(" id=\"${esc(elementId)}\"")
                            val attr = event.optString(3).take(4096)
                            if (value == "a" && attr.startsWith("#")) append(" href=\"#r${esc(android.net.Uri.encode(attr.drop(1)))}\"")
                            else if (value == "a" && httpAddress(attr)) append(" href=\"${esc(attr)}\"")
                            if (value == "ol") attr.toIntOrNull()?.let { append(" start=\"$it\"") }
                            append(">"); stack.addLast(value)
                        }
                        "close" -> if (stack.lastOrNull() == value) {
                            append("</${stack.removeLast()}>")
                            if (value == chapterTag) {
                                val name = chapterText.trim().toString()
                                if (name.isNotEmpty()) chapters.add(ReaderChapter(chapterId,name,if (value=="h2") 2 else 3))
                                chapterTag = ""
                            }
                        }
                        "img" -> if (value.length <= 4096 && httpAddress(value)) {
                            append("<img loading=\"lazy\" src=\"${esc(value)}\" alt=\"${esc(event.optString(2).take(512))}\"")
                            val width = event.optInt(3)
                            val height = event.optInt(4)
                            if (width in 1..65535 && height in 1..65535) append(" width=\"$width\" height=\"$height\"")
                            append(">")
                        }
                        "br", "hr" -> append("<${event.getString(0)}>")
                    }
                }
                while (stack.isNotEmpty()) append("</${stack.removeLast()}>")
                if (truncated) append("<p>这篇文章较长，请返回原网页继续阅读。</p>")
            }
            if (characters == 0) return null
            return ReaderArticle(data.optString("title").take(512).ifBlank { fallback },data.optString("lang").take(32),html,chapters)
        }
    }
}

internal suspend fun MainActivity.extractReaderArticle(page: Page): ReaderArticle? {
    val web = page.web ?: return null
    val url = page.url
    val revision = page.readerRequest
    val fallback = page.title.ifBlank { android.net.Uri.parse(url).host.orEmpty() }
    val script = withContext(Dispatchers.IO) { assets.open("reader-article.js").bufferedReader().use { it.readText() } }
    val result = suspendCancellableCoroutine<String?> { continuation ->
        web.evaluateJavascript(script) { if (continuation.isActive) continuation.resume(it) }
    }
    val article = withContext(Dispatchers.Default) { ReaderArticle.parse(result, fallback) }
    return article?.takeIf { page.web === web && page.url == url && page.readerRequest == revision }
}
