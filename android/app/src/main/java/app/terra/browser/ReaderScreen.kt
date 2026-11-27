package app.terra.browser

import android.content.Context
import android.graphics.Color as AndroidColor
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewCompat
import io.liquidkit.*
import org.json.JSONObject

private class ArticleView(
    context: Context, private val page: Page, private val article: ReaderArticle,
    profile: String, initial: ReaderStyle, tone: ReaderTheme, open: (String) -> Unit
) : WebView(context) {
    private val origin = "${if (page.url.startsWith("http:",true)) "http" else "https"}://terra-reader.invalid/${page.id}/"
    private val startY = page.readerOffset
    private var ready = false
    private var appearance = initial to tone

    init {
        if (page.secret) WebViewCompat.setProfile(this, profile)
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
        settings.apply {
            javaScriptEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            if (page.secret) cacheMode = WebSettings.LOAD_NO_CACHE
        }
        setBackgroundColor(AndroidColor.parseColor(tone.paper))
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (!request.isForMainFrame) return true
                val url = request.url.toString()
                if (url.substringBefore('#') == origin && request.url.fragment != null) return false
                if (request.hasGesture() && httpAddress(url)) open(url)
                return true
            }
            override fun onPageFinished(view: WebView, url: String) {
                if (ready || url.substringBefore('#') != origin) return
                ready = true
                applyAppearance()
                postVisualStateCallback(0, object : VisualStateCallback() {
                    override fun onComplete(requestId: Long) {
                        if (page.readerArticle === article && startY > 0) scrollTo(0,startY)
                    }
                })
            }
        }
        loadDataWithBaseURL(origin, article.html(initial, tone), "text/html", "UTF-8", null)
    }

    fun restyle(style: ReaderStyle, tone: ReaderTheme) {
        if (appearance == (style to tone)) return
        appearance = style to tone
        setBackgroundColor(AndroidColor.parseColor(tone.paper))
        if (ready) applyAppearance()
    }

    fun showChapter(id: String) {
        if (ready) evaluateJavascript("document.getElementById("+JSONObject.quote(id)+")?.scrollIntoView({behavior:'smooth'})",null)
    }

    private fun applyAppearance() {
        val (style, tone) = appearance
        val variables = JSONObject.quote(ReaderArticle.variables(style, tone))
        // Keep the paragraph at the top of the viewport when new metrics reflow the article.
        evaluateJavascript("""(() => {
            const anchor = document.elementFromPoint(innerWidth / 2, 12)?.closest('p,li,h1,h2,h3,blockquote,pre,figure');
            const y = anchor?.getBoundingClientRect().top;
            document.documentElement.style.cssText = $variables;
            if (anchor) scrollBy(0, anchor.getBoundingClientRect().top - y);
        })()""", null)
    }

    override fun onScrollChanged(x: Int, y: Int, oldX: Int, oldY: Int) {
        super.onScrollChanged(x, y, oldX, oldY)
        if (ready && page.readerArticle === article) page.readerOffset = y
    }
}

@Composable internal fun ReaderScreen(activity: MainActivity, page: Page, article: ReaderArticle) {
    val style = BrowserLibrary.readerStyle
    val tone = style.theme.resolve(isSystemInDarkTheme() || page.secret)
    val paper = Color(AndroidColor.parseColor(tone.paper))
    val ink = Color(AndroidColor.parseColor(tone.ink))
    var panel by remember { mutableStateOf("") }
    var document by remember { mutableStateOf<ArticleView?>(null) }
    DisposableEffect(article) { onDispose { if (page.readerArticle === article) page.readerArticle = null } }
    val dark = tone == ReaderTheme.Black || tone == ReaderTheme.Gray
    val view = LocalView.current
    val window = (view.parent as? DialogWindowProvider)?.window
    DisposableEffect(window, dark) {
        val bars = window?.let { WindowCompat.getInsetsController(it, view) }
        val oldStatus = bars?.isAppearanceLightStatusBars
        val oldNavigation = bars?.isAppearanceLightNavigationBars
        bars?.isAppearanceLightStatusBars = !dark
        bars?.isAppearanceLightNavigationBars = !dark
        onDispose {
            if (oldStatus != null) bars.isAppearanceLightStatusBars = oldStatus
            if (oldNavigation != null) bars.isAppearanceLightNavigationBars = oldNavigation
        }
    }
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, tween(190)) }
    LiquidKitRoot(modifier=Modifier.graphicsLayer { alpha=appear.value }, backgroundColor=paper, dark=dark, edgeToEdgeSystemBars=false) {
        val capture = remember { WebGlassCapture() }
        val backdrop = rememberLayerBackdrop { with(capture) { drawSample(includePage=true) } }
        BoxWithConstraints(Modifier.fillMaxSize().background(paper).safeDrawingPadding()) {
            val wide = maxWidth >= 650.dp
            val strip = with(LocalDensity.current) { 64.dp.roundToPx() }
            WebGlassScene(capture, Modifier.fillMaxSize().liquidKitBackdrop(backdrop))
            AndroidView(factory={ ctx ->
                TabViewport(ctx,capture).apply {
                    val reader = ArticleView(ctx,page,article,activity.privateProfile,style,tone) { link ->
                        if (activity.page === page && page.readerArticle === article) activity.navigate(link)
                    }
                    document = reader
                    display(reader)
                }
            }, modifier=Modifier.fillMaxSize(), onRelease={ host ->
                val articleView = host.getChildAt(0) as? ArticleView
                if (document === articleView) document = null
                host.release()
                articleView?.stopLoading()
                articleView?.destroy()
            }, update={ host ->
                host.topClearance = if (wide) strip else 0
                host.bottomClearance = if (wide) 0 else strip
                (host.getChildAt(0) as? ArticleView)?.restyle(style, tone)
            })
            LiquidKitGlassSurface(Modifier.align(if (wide) Alignment.TopCenter else Alignment.BottomCenter)
                .padding(8.dp), backdrop=backdrop) {
                Row(Modifier.widthIn(max=410.dp).fillMaxWidth(if (wide) .76f else .86f)
                    .padding(horizontal=5.dp), verticalAlignment=Alignment.CenterVertically) {
                    Box(Modifier.size(48.dp).clickable(role=Role.Button) { panel="阅读器外观" }
                        .semantics { contentDescription="阅读器外观" },contentAlignment=Alignment.Center) {
                        BasicText("aA",style=TextStyle(color=ink,fontSize=18.sp,fontWeight=FontWeight.Medium))
                    }
                    Box(Modifier.weight(1f),contentAlignment=Alignment.Center) {
                        BasicText(android.net.Uri.parse(page.url).host.orEmpty(),maxLines=1,overflow=TextOverflow.Ellipsis,
                            style=TextStyle(color=ink,fontSize=15.sp,fontWeight=FontWeight.Medium))
                    }
                    if (article.chapters.isNotEmpty()) Box(Modifier.size(48.dp).clickable(role=Role.Button) { panel="文章目录" }
                        .semantics { contentDescription="文章目录" },contentAlignment=Alignment.Center) {
                        LiquidKitIcon(LiquidKitSymbol.ListBulletRectangle,ink,Modifier.size(18.dp))
                    }
                    Box(Modifier.size(48.dp).clickable(role=Role.Button) { activity.sheet="" }
                        .semantics { contentDescription="显示原网页" },contentAlignment=Alignment.Center) {
                        LiquidKitIcon(LiquidKitSymbol.Close,ink,Modifier.size(17.dp))
                    }
                }
            }
        }
        if (panel.isNotEmpty()) LiquidKitSheet(onDismissRequest={ panel="" }, title=panel, backdrop=backdrop) {
            if (panel=="阅读器外观") ReaderOptions(style)
            else article.chapters.forEach { entry ->
                Box(Modifier.fillMaxWidth().heightIn(min=46.dp).clickable(role=Role.Button) {
                    panel = ""
                    document?.showChapter(entry.id)
                }.padding(start=if (entry.level==3) 20.dp else 4.dp, end=8.dp, top=8.dp, bottom=8.dp),
                    contentAlignment=Alignment.CenterStart) {
                    BasicText(entry.title,style=TextStyle(color=liquidKitColors().ink,fontSize=17.sp))
                }
            }
        }
    }
}

@Composable private fun ReaderOptions(style: ReaderStyle) {
    val index = readerSizes.indexOf(style.percent)
    val change = BrowserLibrary::updateReaderStyle
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly,verticalAlignment=Alignment.CenterVertically) {
            LiquidKitActionButton("小字", { change(style.copy(percent=readerSizes[index-1])) },
                Modifier.semantics { contentDescription="缩小正文字号" }, enabled=index>0)
            LiquidKitActionButton("${style.percent}%", { change(style.copy(percent=100)) },
                Modifier.semantics { contentDescription="恢复正文字号" })
            LiquidKitActionButton("大字", { change(style.copy(percent=readerSizes[index+1])) },
                Modifier.semantics { contentDescription="放大正文字号" }, enabled=index<readerSizes.lastIndex)
        }
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf(false to "衬线体",true to "无衬线体").forEach { (sans,name) ->
                LiquidKitActionButton(name, { change(style.copy(sans=sans)) },
                    Modifier.semantics { this.selected = sans == style.sans },
                    style=if (sans==style.sans) LiquidKitActionStyle.Filled else LiquidKitActionStyle.Tinted)
            }
        }
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            ReaderTheme.entries.forEach { theme ->
                val selected = theme == style.theme
                LiquidKitActionButton(theme.label, { change(style.copy(theme=theme)) },
                    Modifier.semantics { this.selected = selected },
                    leadingSymbol=if (selected) LiquidKitSymbol.Check else null,
                    style=if (selected) LiquidKitActionStyle.Filled else LiquidKitActionStyle.Tinted)
            }
        }
    }
}
