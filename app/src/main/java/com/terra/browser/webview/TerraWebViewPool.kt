package com.terra.browser.webview

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Parcel
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.terra.browser.downloads.TerraDownloads
import com.terra.browser.model.BrowserTab
import com.terra.browser.model.BrowserUrlUtils
import com.terra.browser.model.SiteDarkMode
import com.terra.browser.reader.ReaderArticle
import com.terra.browser.state.TerraBrowserState
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors
import kotlin.math.roundToInt

class TerraWebViewPool(
    private val context: Context,
    private val state: TerraBrowserState,
) {
    private data class PoolEntry(
        val webView: TerraWebView,
        var loadedUrl: String = "",
        var lastCommittedBaseUrl: String = "",
        var lastDesktopMode: Boolean = false,
        var lastZoom: Int = 100,
        var lastDarkCss: Boolean = false,
        var lastBottomSafeInsetDp: Int = 0,
        var lastUsedAt: Long = System.currentTimeMillis(),
        var currentLoadFinished: Boolean = true,
    )

    private val entries = LinkedHashMap<String, PoolEntry>()
    private val savedStateCache = HashMap<String, ByteArray>()
    private val lastSnapshotAtMs = HashMap<String, Long>()
    private val thumbnailUrls = HashMap<String, String>()
    private val pendingSnapshotRunnables = HashMap<String, Runnable>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val ioExecutor = Executors.newSingleThreadExecutor()
    private val thumbsDir = File(context.cacheDir, "tab_thumbs").apply { mkdirs() }
    private val faviconsDir = File(context.cacheDir, "tab_favicons").apply { mkdirs() }
    private val statesDir = File(context.cacheDir, "tab_states").apply { mkdirs() }
    private val maxPoolSize = 8

    val downloads: TerraDownloads = TerraDownloads(context)
    private var readerScriptCache: String? = null

    @Volatile
    var isTransitionActive: Boolean = false

    init {
        preloadCachedThumbnails()
    }

    private fun runIo(block: () -> Unit) {
        if (!ioExecutor.isShutdown) {
            runCatching { ioExecutor.execute(block) }
        }
    }

    private fun preloadCachedThumbnails() {
        val tabIds = state.tabs.map { it.id }
        runIo {
            val loadedThumbs = mutableListOf<Pair<String, ImageBitmap>>()
            val loadedIcons = mutableListOf<Pair<String, ImageBitmap>>()
            tabIds.forEach { id ->
                val f = File(thumbsDir, "$id.jpg")
                if (f.exists()) {
                    runCatching { BitmapFactory.decodeFile(f.absolutePath) }.getOrNull()?.let { bmp ->
                        loadedThumbs.add(id to bmp.asImageBitmap())
                    }
                }
                val iconFile = File(faviconsDir, "$id.png")
                if (iconFile.exists()) {
                    runCatching { BitmapFactory.decodeFile(iconFile.absolutePath) }.getOrNull()?.let { bmp ->
                        loadedIcons.add(id to bmp.asImageBitmap())
                    }
                }
            }
            if (loadedThumbs.isNotEmpty() || loadedIcons.isNotEmpty()) {
                mainHandler.post {
                    loadedThumbs.forEach { (id, img) ->
                        if (!state.thumbnails.containsKey(id)) {
                            state.thumbnails[id] = img
                        }
                    }
                    loadedIcons.forEach { (id, img) ->
                        if (!state.favicons.containsKey(id)) {
                            state.favicons[id] = img
                        }
                    }
                }
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun obtain(
        tab: BrowserTab,
        appDark: Boolean,
        onScrollDelta: (dy: Int, scrollY: Int) -> Unit,
    ): TerraWebView {
        val existing = entries[tab.id]
        if (existing != null) {
            existing.lastUsedAt = System.currentTimeMillis()
            existing.webView.onScrollDelta = { dy, scrollY ->
                onScrollDelta(dy, scrollY)
                if (kotlin.math.abs(dy) > 4) {
                    scheduleSnapshot(tab.id, delayMs = 450L)
                }
            }
            applyTabSettings(existing, tab, appDark)
            return existing.webView
        }

        evictIfNeeded(preserveTabId = tab.id)

        val wv = TerraWebView(context.applicationContext).apply {
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            settings.setSupportZoom(true)
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            settings.allowFileAccess = false
            settings.userAgentString = mobileUserAgent(settings.userAgentString)
            CookieManager.getInstance().setAcceptCookie(true)
            isVerticalScrollBarEnabled = true
            isHorizontalScrollBarEnabled = false
        }

        val entry = PoolEntry(
            webView = wv,
            lastDesktopMode = tab.requestDesktopSite,
            lastZoom = tab.pageZoom,
        )
        entries[tab.id] = entry

        wv.onScrollDelta = { dy, scrollY ->
            onScrollDelta(dy, scrollY)
            if (kotlin.math.abs(dy) > 4) {
                scheduleSnapshot(tab.id, delayMs = 450L)
            }
        }
        wv.onLongPressHit = { hit ->
            state.activeContextHit = hit
        }

        wv.setFindListener { activeMatchOrdinal, numberOfMatches, isDoneCounting ->
            if (isDoneCounting) {
                state.findMatchCount = numberOfMatches
                state.findMatchIndex = if (numberOfMatches > 0) activeMatchOrdinal + 1 else 0
            }
        }
        wv.setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
            val currentTab = state.tabs.firstOrNull { it.id == tab.id }
            val isPrivate = state.tabGroups.firstOrNull { it.id == currentTab?.groupId }?.isPrivate == true
            val filename = downloads.enqueue(
                url = url,
                userAgent = userAgent,
                contentDisposition = contentDisposition,
                mimeType = mimetype,
                isPrivate = isPrivate,
            )
            if (filename != null) {
                state.showBanner("已开始下载：$filename")
            } else {
                state.showBanner("无法下载此链接")
            }
        }

        wv.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (entry.currentLoadFinished && newProgress < 100) return
                if (newProgress >= 100) {
                    entry.currentLoadFinished = true
                    state.updateTab(tab.id) { current ->
                        current.copy(
                            progress = 1f,
                            isLoading = false,
                        )
                    }
                    return
                }
                val p = (newProgress / 100f).coerceIn(0.12f, 0.96f)
                state.updateTab(tab.id) { current ->
                    val monotonic = if (current.isLoading) maxOf(current.progress, p) else p
                    current.copy(
                        progress = monotonic,
                        isLoading = newProgress in 1..99,
                    )
                }
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                val clean = title?.trim().orEmpty()
                if (clean.isNotEmpty()) {
                    state.updateTab(tab.id) { current ->
                        current.copy(title = clean)
                    }
                }
            }

            override fun onReceivedIcon(view: WebView?, icon: Bitmap?) {
                val src = icon ?: return
                val copy = runCatching {
                    src.copy(Bitmap.Config.ARGB_8888, false)
                }.getOrNull() ?: return
                val img = copy.asImageBitmap()
                state.favicons[tab.id] = img
                runIo {
                    runCatching {
                        val tmp = File(faviconsDir, "${tab.id}.tmp")
                        FileOutputStream(tmp).use { out ->
                            copy.compress(Bitmap.CompressFormat.PNG, 100, out)
                        }
                        tmp.renameTo(File(faviconsDir, "${tab.id}.png"))
                    }
                }
            }
        }

        wv.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?,
            ): WebResourceResponse? {
                val host = request?.url?.host ?: return null
                val latestTab = state.tabs.firstOrNull { it.id == tab.id }
                if (latestTab?.contentBlockerEnabled == true && BrowserUrlUtils.isTrackerHost(host)) {
                    mainHandler.post { state.incrementBlockedTracker(tab.id, host) }
                    return WebResourceResponse(
                        "text/plain",
                        "utf-8",
                        204,
                        "Blocked",
                        emptyMap(),
                        ByteArrayInputStream(ByteArray(0)),
                    )
                }
                return super.shouldInterceptRequest(view, request)
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val target = request?.url?.toString() ?: return false
                return !(target.startsWith("http://") || target.startsWith("https://"))
            }

            override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                super.doUpdateVisitedHistory(view, url, isReload)
                val safeUrl = url?.takeIf { it.startsWith("http://") || it.startsWith("https://") } ?: return
                entry.loadedUrl = safeUrl
                state.updateTab(tab.id) { current ->
                    current.copy(
                        url = safeUrl,
                        host = BrowserUrlUtils.displayHost(safeUrl),
                        title = view?.title?.takeIf { it.isNotBlank() } ?: current.title,
                        accentColorArgb = BrowserUrlUtils.accentColorForUrl(safeUrl),
                        canGoBack = view?.canGoBack() ?: false,
                        canGoForward = view?.canGoForward() ?: false,
                    )
                }
                injectBottomSafeAreaCss(view, entry.lastBottomSafeInsetDp)
                saveTabState(tab.id)
                mainHandler.postDelayed({ captureSnapshot(tab.id) }, 300L)
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                val safeUrl = url ?: return
                entry.currentLoadFinished = false
                val nextBase = safeUrl.substringBefore('#')
                val isNewPage = nextBase != entry.lastCommittedBaseUrl
                entry.loadedUrl = safeUrl
                entry.lastCommittedBaseUrl = nextBase
                val twv = view as? TerraWebView
                if (isNewPage) {
                    twv?.resetTopSafeOffset()
                } else {
                    twv?.resetTopSafeOffsetIfAtTop()
                }
                state.updateTab(tab.id) { current ->
                    current.copy(
                        url = safeUrl,
                        host = BrowserUrlUtils.displayHost(safeUrl),
                        accentColorArgb = BrowserUrlUtils.accentColorForUrl(safeUrl),
                        isLoading = true,
                        progress = 0.12f,
                        canGoBack = view?.canGoBack() ?: false,
                        canGoForward = view?.canGoForward() ?: false,
                    )
                }
            }

            override fun onPageCommitVisible(view: WebView?, url: String?) {
                super.onPageCommitVisible(view, url)
                val twv = view as? TerraWebView
                twv?.resetTopSafeOffsetIfAtTop()
                if (!url.isNullOrBlank()) {
                    twv?.restoreReadingPosition(url, finished = false)
                }
                val latestTab = state.tabs.firstOrNull { it.id == tab.id }
                val shouldInjectDark = latestTab?.let { resolveDarkCss(it, appDark) } ?: false
                injectDarkModeCss(view, shouldInjectDark)
                entry.lastDarkCss = shouldInjectDark
                injectBottomSafeAreaCss(view, entry.lastBottomSafeInsetDp)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                val safeUrl = url ?: return
                entry.currentLoadFinished = true
                entry.loadedUrl = safeUrl
                entry.lastCommittedBaseUrl = safeUrl.substringBefore('#')
                val twv = view as? TerraWebView
                twv?.resetTopSafeOffsetIfAtTop()
                twv?.restoreReadingPosition(safeUrl, finished = true)
                val latestTab = state.tabs.firstOrNull { it.id == tab.id }
                val finalTitle = view?.title?.takeIf { it.isNotBlank() }
                    ?: BrowserUrlUtils.displayHost(safeUrl).ifBlank { safeUrl }
                state.updateTab(tab.id) { current ->
                    current.copy(
                        url = safeUrl,
                        host = BrowserUrlUtils.displayHost(safeUrl),
                        title = finalTitle,
                        isLoading = false,
                        progress = 1f,
                        canGoBack = view?.canGoBack() ?: false,
                        canGoForward = view?.canGoForward() ?: false,
                    )
                }
                state.recordHistory(tab.id, finalTitle, safeUrl)
                val shouldInjectDark = latestTab?.let { resolveDarkCss(it, appDark) } ?: false
                injectDarkModeCss(view, shouldInjectDark)
                entry.lastDarkCss = shouldInjectDark
                injectBottomSafeAreaCss(view, entry.lastBottomSafeInsetDp)
                if (state.extendWebsiteTint) {
                    view?.evaluateJavascript(
                        """
                        (function(){
                          var m=document.querySelector('meta[name="theme-color"]');
                          if(m&&m.content){return m.content;}
                          var b=getComputedStyle(document.body||document.documentElement).backgroundColor;
                          if(b&&b!=='rgba(0, 0, 0, 0)'&&b!=='transparent'){return b;}
                          return getComputedStyle(document.documentElement).backgroundColor||'';
                        })();
                        """.trimIndent(),
                    ) { result ->
                        val parsed = BrowserUrlUtils.parseCssColorArgb(result)
                        val resolved = if (parsed != null && shouldInjectDark) {
                            val r = ((parsed shr 16) and 0xFF).toInt()
                            val g = ((parsed shr 8) and 0xFF).toInt()
                            val b = (parsed and 0xFF).toInt()
                            if (r > 205 && g > 205 && b > 205) 0xFF141519L else parsed
                        } else {
                            parsed
                        }
                        state.updateTab(tab.id) { it.copy(themeColorArgb = resolved) }
                    }
                }
                saveTabState(tab.id)
                mainHandler.postDelayed({ captureSnapshot(tab.id) }, 250L)
                mainHandler.postDelayed({ captureSnapshot(tab.id) }, 1200L)
            }
        }

        applyTabSettings(entry, tab, appDark, skipLoadUrl = true)

        val restored = restoreTabState(tab.id, wv)
        val restoredUrl = wv.url.orEmpty()
        val matchesTarget = tab.url.isBlank() ||
            restoredUrl.equals(tab.url, ignoreCase = true) ||
            BrowserUrlUtils.displayHost(restoredUrl) == BrowserUrlUtils.displayHost(tab.url)

        if (restored && restoredUrl.isNotBlank() && matchesTarget) {
            entry.loadedUrl = restoredUrl
            entry.lastCommittedBaseUrl = restoredUrl.substringBefore('#')
            state.updateTab(tab.id) { current ->
                current.copy(
                    url = restoredUrl,
                    host = BrowserUrlUtils.displayHost(restoredUrl),
                    canGoBack = wv.canGoBack(),
                    canGoForward = wv.canGoForward(),
                )
            }
        } else if (tab.url.isNotBlank()) {
            entry.loadedUrl = tab.url
            wv.loadUrl(tab.url)
        }
        return wv
    }

    fun syncTab(tab: BrowserTab, appDark: Boolean) {
        val entry = entries[tab.id] ?: return
        applyTabSettings(entry, tab, appDark)
    }

    fun updateBottomSafeInset(tabId: String, insetDp: Int) {
        val safeDp = insetDp.coerceIn(0, 280)
        val entry = entries[tabId] ?: return
        if (entry.lastBottomSafeInsetDp == safeDp) return
        entry.lastBottomSafeInsetDp = safeDp
        injectBottomSafeAreaCss(entry.webView, safeDp)
    }

    private fun applyTabSettings(
        entry: PoolEntry,
        tab: BrowserTab,
        appDark: Boolean,
        skipLoadUrl: Boolean = false,
    ) {
        val wv = entry.webView
        if (entry.lastZoom != tab.pageZoom) {
            wv.settings.textZoom = tab.pageZoom.coerceIn(50, 200)
            entry.lastZoom = tab.pageZoom
        }
        if (entry.lastDesktopMode != tab.requestDesktopSite) {
            entry.lastDesktopMode = tab.requestDesktopSite
            wv.settings.userAgentString = if (tab.requestDesktopSite) {
                DESKTOP_USER_AGENT
            } else {
                mobileUserAgent(null)
            }
            if (!skipLoadUrl && tab.url.isNotBlank()) {
                wv.reload()
            }
        }
        val wantDarkCss = resolveDarkCss(tab, appDark)
        if (entry.lastDarkCss != wantDarkCss) {
            entry.lastDarkCss = wantDarkCss
            injectDarkModeCss(wv, wantDarkCss)
        }
        if (!skipLoadUrl && tab.url.isNotBlank() && entry.loadedUrl != tab.url && wv.url != tab.url) {
            entry.loadedUrl = tab.url
            if (tab.url.substringBefore('#') != entry.lastCommittedBaseUrl) {
                wv.resetTopSafeOffset()
            }
            wv.loadUrl(tab.url)
        }
    }

    private fun resolveDarkCss(tab: BrowserTab, appDark: Boolean): Boolean = when (tab.adaptiveDarkMode) {
        SiteDarkMode.ON -> true
        SiteDarkMode.OFF -> false
        SiteDarkMode.AUTO -> appDark
    }

    private fun injectDarkModeCss(webView: WebView?, enable: Boolean) {
        webView ?: return
        val js = if (enable) {
            """
            (function(){
              var id='terra-adaptive-dark';
              if(!document.getElementById(id)){
                var s=document.createElement('style');
                s.id=id;
                s.textContent='html{filter:invert(0.91) hue-rotate(180deg) !important;background:#111 !important;}img,video,picture,canvas,svg,[style*="background-image"]{filter:invert(1) hue-rotate(180deg) !important;}';
                (document.head||document.documentElement).appendChild(s);
              }
            })();
            """.trimIndent()
        } else {
            """
            (function(){
              var s=document.getElementById('terra-adaptive-dark');
              if(s&&s.parentNode){s.parentNode.removeChild(s);}
            })();
            """.trimIndent()
        }
        webView.evaluateJavascript(js, null)
    }

    private fun injectBottomSafeAreaCss(webView: WebView?, insetDp: Int) {
        webView ?: return
        val safeCssPx = insetDp.coerceIn(0, 280)
        val js = """
            (function(){
              try{
                var px=$safeCssPx;
                var root=document.documentElement;
                if(!root)return;
                root.style.setProperty('--terra-safe-area-inset-bottom',px+'px');
                if(document.body && !root.dataset.terraOrigPbCaptured){
                  var bp=getComputedStyle(document.body).paddingBottom||'0px';
                  root.style.setProperty('--terra-orig-body-pb',bp);
                  root.dataset.terraOrigPbCaptured='1';
                }
                var sid='terra-safe-area-style';
                var st=document.getElementById(sid);
                if(!st){
                  st=document.createElement('style');
                  st.id=sid;
                  (document.head||root).appendChild(st);
                }
                st.textContent=px>0
                  ? 'html{scroll-padding-bottom:'+px+'px !important;}body{padding-bottom:calc(var(--terra-orig-body-pb,0px) + '+px+'px) !important;}'
                  : '';
                window.__terraSafeBottomPx=px;
                if(!window.__terraApplySafeBottom){
                  var skipTags={HTML:1,BODY:1,SCRIPT:1,STYLE:1,LINK:1,META:1,NOSCRIPT:1,BR:1,SPAN:1,B:1,I:1,STRONG:1,EM:1,SMALL:1,SVG:1,PATH:1,G:1,CIRCLE:1,RECT:1,LINE:1,OPTION:1};
                  var resetEl=function(el){
                    if(!el || !el.dataset || el.dataset.terraSafeLifted!=='1')return;
                    if(el.dataset.terraSafeMode==='pb'){
                      el.style.paddingBottom=el.dataset.terraOrigPb||'';
                    }else if(el.dataset.terraSafeMode==='mb'){
                      el.style.marginBottom=el.dataset.terraOrigMb||'';
                    }else if(el.dataset.terraSafeMode==='bot'){
                      el.style.bottom=el.dataset.terraOrigBot||'';
                    }
                    delete el.dataset.terraSafeLifted;
                    delete el.dataset.terraSafeMode;
                    delete el.dataset.terraAppliedPx;
                  };
                  var hasLiftedAncestor=function(el){
                    var p=el.parentElement;
                    while(p && p!==document.body && p!==document.documentElement){
                      if(p.dataset && p.dataset.terraSafeLifted==='1')return true;
                      p=p.parentElement;
                    }
                    return false;
                  };
                  var liftNode=function(el, cs, r, safePx, vh, vw){
                    if(hasLiftedAncestor(el))return;
                    var flushSheet=(r.width>=vw*0.88 && r.bottom>=vh-8 && (parseFloat(cs.borderBottomLeftRadius)||0)<=6);
                    if(!el.dataset.terraSafeOrigSaved){
                      el.dataset.terraOrigPb=el.style.paddingBottom||'';
                      el.dataset.terraOrigMb=el.style.marginBottom||'';
                      el.dataset.terraOrigBot=el.style.bottom||'';
                      el.dataset.terraCompPb=cs.paddingBottom||'0px';
                      el.dataset.terraCompMb=cs.marginBottom||'0px';
                      el.dataset.terraCompBot=cs.bottom||'auto';
                      el.dataset.terraSafeOrigSaved='1';
                    }
                    el.dataset.terraSafeLifted='1';
                    el.dataset.terraAppliedPx=String(safePx);
                    if(flushSheet){
                      el.dataset.terraSafeMode='pb';
                      var basePb=parseFloat(el.dataset.terraCompPb)||0;
                      el.style.setProperty('padding-bottom',(basePb+safePx)+'px','important');
                    }else if(el.dataset.terraCompBot && el.dataset.terraCompBot!=='auto' && el.dataset.terraSafeMode!=='mb'){
                      el.dataset.terraSafeMode='bot';
                      var baseBot=parseFloat(el.dataset.terraCompBot)||0;
                      el.style.setProperty('bottom',(baseBot+safePx)+'px','important');
                    }else{
                      el.dataset.terraSafeMode='mb';
                      var baseMb=parseFloat(el.dataset.terraCompMb)||0;
                      el.style.setProperty('margin-bottom',(baseMb+safePx)+'px','important');
                    }
                  };
                  window.__terraApplySafeBottom=function(safePx){
                    var vh=window.innerHeight||0, vw=window.innerWidth||0;
                    if(vh<=0 || vw<=0)return;
                    var lifted=document.querySelectorAll('[data-terra-safe-lifted="1"]');
                    for(var k=0;k<lifted.length;k++){
                      if(safePx<=0 || Number(lifted[k].dataset.terraAppliedPx)!==safePx){resetEl(lifted[k]);}
                    }
                    if(safePx<=0 || !document.body)return;
                    var all=document.querySelectorAll('body > *, body > * > *');
                    var limit=Math.min(all.length,80);
                    for(var i=0;i<limit;i++){
                      var el=all[i];
                      if(skipTags[el.tagName])continue;
                      if(el.dataset && el.dataset.terraSafeLifted==='1')continue;
                      var cs=getComputedStyle(el);
                      if(cs.display==='none'||cs.visibility==='hidden'||parseFloat(cs.opacity)===0)continue;
                      if(cs.position!=='fixed' && cs.position!=='sticky')continue;
                      var r=el.getBoundingClientRect();
                      if(r.width<24 || r.height<20)continue;
                      if(r.height<vh*0.68 && r.bottom>=vh-28 && r.top>vh*0.18){
                        liftNode(el,cs,r,safePx,vh,vw);
                      }else if(r.height>=vh*0.68){
                        var kids=el.querySelectorAll(':scope > *');
                        var kidLimit=Math.min(kids.length,24);
                        for(var j=0;j<kidLimit;j++){
                          var kid=kids[j];
                          if(skipTags[kid.tagName])continue;
                          if(kid.dataset && kid.dataset.terraSafeLifted==='1')continue;
                          var kr=kid.getBoundingClientRect();
                          if(kr.height>=24 && kr.height<vh*0.65 && kr.bottom>=vh-28 && kr.top>vh*0.20){
                            var kcs=getComputedStyle(kid);
                            if(kcs.display!=='none'&&kcs.visibility!=='hidden'){
                              liftNode(kid,kcs,kr,safePx,vh,vw);
                            }
                          }
                        }
                      }
                    }
                  };
                  var resizeTimer=0;
                  var scheduleResize=function(){
                    if(resizeTimer)clearTimeout(resizeTimer);
                    resizeTimer=setTimeout(function(){
                      resizeTimer=0;
                      if(window.__terraApplySafeBottom){
                        window.__terraApplySafeBottom(window.__terraSafeBottomPx||0);
                      }
                    },250);
                  };
                  window.addEventListener('resize',scheduleResize,{passive:true});
                }
                if(window.__terraApplySafeBottom){
                  window.__terraApplySafeBottom(px);
                }
              }catch(e){}
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    fun activeCachedIds(): Set<String> = entries.keys.toSet()

    fun currentTopSafeOffsetPx(tabId: String, fallbackPx: Float): Float {
        val wv = entries[tabId]?.webView ?: return fallbackPx
        if (!wv.hasInitializedTopOffset()) return if (wv.scrollY > 0) 0f else fallbackPx
        return if (wv.scrollY > 0) 0f else wv.currentTopSafeOffsetPx
    }

    fun isTabLoadedAndAttached(tabId: String): Boolean {
        val entry = entries[tabId] ?: return false
        val wv = entry.webView
        return wv.isAttachedToWindow && wv.width > 0 && wv.height > 0 && wv.progress >= 30
    }

    fun captureSnapshotBeforeHide(tabId: String) {
        val entry = entries[tabId] ?: return
        val wv = entry.webView
        wv.rememberReadingPosition(wv.url ?: entry.loadedUrl)
        if (!wv.isAttachedToWindow || wv.visibility != View.VISIBLE || wv.width <= 0 || wv.height <= 0) {
            return
        }
        captureSnapshotSync(tabId, force = true)
    }

    fun scheduleSnapshot(tabId: String, delayMs: Long = 650L) {
        pendingSnapshotRunnables.remove(tabId)?.let { mainHandler.removeCallbacks(it) }
        val task = Runnable {
            pendingSnapshotRunnables.remove(tabId)
            captureSnapshot(tabId)
        }
        pendingSnapshotRunnables[tabId] = task
        mainHandler.postDelayed(task, delayMs)
    }

    fun hasFreshSnapshot(tabId: String, maxAgeMs: Long = 1500L): Boolean {
        val entry = entries[tabId] ?: return state.thumbnails.containsKey(tabId)
        val currentUrl = entry.webView.url.orEmpty().ifBlank { entry.loadedUrl }
        if (!state.thumbnails.containsKey(tabId) || thumbnailUrls[tabId] != currentUrl) return false
        val lastAt = lastSnapshotAtMs[tabId] ?: 0L
        return (System.currentTimeMillis() - lastAt) <= maxAgeMs
    }

    fun ensureSnapshotForTransition(tabId: String) {
        if (hasFreshSnapshot(tabId, maxAgeMs = 180L)) return
        captureSnapshotSync(tabId, force = true)
    }

    fun captureSnapshotSync(tabId: String, force: Boolean = true): ImageBitmap? {
        val entry = entries[tabId] ?: return state.thumbnails[tabId]
        val wv = entry.webView
        val w = wv.width
        val h = wv.height
        if (w <= 0 || h <= 0 || wv.contentHeight <= 0) return state.thumbnails[tabId]
        if (wv.visibility != View.VISIBLE || !wv.isAttachedToWindow) {
            return state.thumbnails[tabId]
        }
        val currentUrl = wv.url.orEmpty().ifBlank { entry.loadedUrl }
        val scale = minOf(1f, 440f / w.toFloat(), 880f / h.toFloat()).coerceIn(0.2f, 1f)
        val bmpW = (w * scale).roundToInt().coerceAtLeast(1)
        val bmpH = (h * scale).roundToInt().coerceAtLeast(1)
        val bitmap = runCatching {
            Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888).also { bmp ->
                val canvas = Canvas(bmp)
                canvas.scale(scale, scale)
                canvas.translate(-wv.scrollX.toFloat(), -wv.scrollY.toFloat())
                wv.draw(canvas)
            }
        }.getOrNull() ?: return state.thumbnails[tabId]

        if (isBitmapUniformBlank(bitmap)) {
            val sameUrlCached = state.thumbnails.containsKey(tabId) && thumbnailUrls[tabId] == currentUrl
            bitmap.recycle()
            if (!force && wv.progress in 1..99) {
                scheduleSnapshot(tabId, delayMs = 600L)
            }
            return if (sameUrlCached || force) state.thumbnails[tabId] else state.thumbnails[tabId]
        }

        val img = bitmap.asImageBitmap()
        if (state.tabs.any { it.id == tabId }) {
            state.thumbnails[tabId] = img
            thumbnailUrls[tabId] = currentUrl
            lastSnapshotAtMs[tabId] = System.currentTimeMillis()
        }
        runIo {
            runCatching {
                val tmp = File(thumbsDir, "$tabId.tmp")
                FileOutputStream(tmp).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                }
                tmp.renameTo(File(thumbsDir, "$tabId.jpg"))
            }
        }
        return img
    }

    fun captureSnapshot(tabId: String, @Suppress("UNUSED_PARAMETER") targetWidth: Int = 540, force: Boolean = false): ImageBitmap? {
        if (isTransitionActive && !force) {
            scheduleSnapshot(tabId, delayMs = 900L)
            return state.thumbnails[tabId]
        }
        val now = System.currentTimeMillis()
        val entry = entries[tabId] ?: return state.thumbnails[tabId]
        val currentUrl = entry.webView.url.orEmpty().ifBlank { entry.loadedUrl }
        if (!force && state.thumbnails.containsKey(tabId) && thumbnailUrls[tabId] == currentUrl) {
            val lastAt = lastSnapshotAtMs[tabId] ?: 0L
            if (now - lastAt < 3500L) {
                return state.thumbnails[tabId]
            }
        }
        return captureSnapshotSync(tabId, force = force)
    }

    private fun isBitmapUniformBlank(bitmap: Bitmap): Boolean {
        val bmpW = bitmap.width
        val bmpH = bitmap.height
        if (bmpW <= 0 || bmpH <= 0) return true
        val samples = IntArray(64)
        val lumaBuckets = HashSet<Int>(16)
        var idx = 0
        for (gy in 0 until 8) {
            val py = ((bmpH - 1) * (gy + 0.5f) / 8f).toInt().coerceIn(0, bmpH - 1)
            for (gx in 0 until 8) {
                val px = ((bmpW - 1) * (gx + 0.5f) / 8f).toInt().coerceIn(0, bmpW - 1)
                val pixel = bitmap.getPixel(px, py)
                samples[idx++] = pixel
                val luma = (((pixel shr 16) and 0xFF) * 3 + ((pixel shr 8) and 0xFF) * 6 + (pixel and 0xFF)) / 10
                lumaBuckets.add(luma / 16)
            }
        }
        val topUniform = BrowserUrlUtils.isUniformBlankSample(samples.copyOfRange(0, 16))
        val bottomUniform = BrowserUrlUtils.isUniformBlankSample(samples.copyOfRange(16, 64))
        return BrowserUrlUtils.isUniformBlankSample(samples) ||
            (topUniform && bottomUniform && lumaBuckets.size <= 3)
    }

    fun saveTabStateDeferred(tabId: String) {
        mainHandler.post {
            if (entries.containsKey(tabId)) {
                saveTabState(tabId)
            }
        }
    }

    fun saveTabState(tabId: String) {
        val entry = entries[tabId] ?: return
        entry.webView.rememberReadingPosition(entry.webView.url ?: entry.loadedUrl)
        val bundle = Bundle()
        entry.webView.saveState(bundle) ?: return
        val bytes = runCatching {
            val parcel = Parcel.obtain()
            try {
                bundle.writeToParcel(parcel, 0)
                parcel.marshall()
            } finally {
                parcel.recycle()
            }
        }.getOrNull() ?: return
        savedStateCache[tabId] = bytes
        runIo {
            runCatching {
                val tmp = File(statesDir, "$tabId.tmp")
                FileOutputStream(tmp).use { it.write(bytes) }
                tmp.renameTo(File(statesDir, "$tabId.bin"))
            }
        }
    }

    private fun restoreTabState(tabId: String, wv: TerraWebView): Boolean {
        val bytes = savedStateCache[tabId] ?: runCatching {
            val f = File(statesDir, "$tabId.bin")
            if (f.exists()) f.readBytes().also { savedStateCache[tabId] = it } else null
        }.getOrNull() ?: return false
        return runCatching {
            val parcel = Parcel.obtain()
            try {
                parcel.unmarshall(bytes, 0, bytes.size)
                parcel.setDataPosition(0)
                val bundle = Bundle.CREATOR.createFromParcel(parcel)
                bundle.classLoader = WebView::class.java.classLoader
                wv.restoreState(bundle) != null
            } finally {
                parcel.recycle()
            }
        }.getOrDefault(false)
    }

    fun saveAllActiveStatesAndSnapshots() {
        entries.keys.toList().forEach { id ->
            captureSnapshot(id)
            saveTabState(id)
        }
    }

    fun getBackForwardItems(tabId: String, forward: Boolean): List<Triple<Int, String, String>> {
        val wv = entries[tabId]?.webView ?: return emptyList()
        return runCatching {
            val list = wv.copyBackForwardList()
            val total = list.size
            val cur = list.currentIndex
            if (total <= 0 || cur !in 0 until total) return@runCatching emptyList()
            val result = mutableListOf<Triple<Int, String, String>>()
            if (!forward) {
                for (i in (cur - 1) downTo 0) {
                    val item = list.getItemAtIndex(i) ?: continue
                    val url = item.url.orEmpty()
                    val label = item.title?.takeIf { it.isNotBlank() }
                        ?: BrowserUrlUtils.displayHost(url).ifBlank { url }
                    result.add(Triple(i - cur, label, url))
                }
            } else {
                for (i in (cur + 1) until total) {
                    val item = list.getItemAtIndex(i) ?: continue
                    val url = item.url.orEmpty()
                    val label = item.title?.takeIf { it.isNotBlank() }
                        ?: BrowserUrlUtils.displayHost(url).ifBlank { url }
                    result.add(Triple(i - cur, label, url))
                }
            }
            result
        }.getOrDefault(emptyList())
    }

    fun goBackOrForward(tabId: String, steps: Int) {
        if (steps == 0) return
        val wv = entries[tabId]?.webView ?: return
        runCatching {
            if (wv.canGoBackOrForward(steps)) {
                wv.goBackOrForward(steps)
            }
        }
    }

    fun goBack(tabId: String): Boolean {
        val wv = entries[tabId]?.webView ?: return false
        return runCatching {
            if (wv.canGoBack()) {
                wv.goBack()
                true
            } else {
                false
            }
        }.getOrDefault(false)
    }

    fun goForward(tabId: String): Boolean {
        val wv = entries[tabId]?.webView ?: return false
        return runCatching {
            if (wv.canGoForward()) {
                wv.goForward()
                true
            } else {
                false
            }
        }.getOrDefault(false)
    }

    fun reload(tabId: String) {
        runCatching { entries[tabId]?.webView?.reload() }
    }

    fun stopLoading(tabId: String) {
        entries[tabId]?.currentLoadFinished = true
        runCatching { entries[tabId]?.webView?.stopLoading() }
        state.updateTab(tabId) { it.copy(isLoading = false, progress = 0f) }
    }

    fun findInPage(tabId: String, query: String) {
        val wv = entries[tabId]?.webView ?: return
        runCatching {
            if (query.isBlank()) {
                wv.clearMatches()
                state.findMatchCount = 0
                state.findMatchIndex = 0
            } else {
                wv.findAllAsync(query)
            }
        }
    }

    fun findNext(tabId: String, forward: Boolean) {
        runCatching { entries[tabId]?.webView?.findNext(forward) }
    }

    fun clearFind(tabId: String) {
        runCatching { entries[tabId]?.webView?.clearMatches() }
        state.findMatchCount = 0
        state.findMatchIndex = 0
    }

    fun extractReaderArticle(tabId: String, onResult: (ReaderArticle?) -> Unit) {
        val entry = entries[tabId]
        val tab = state.tabs.firstOrNull { it.id == tabId }
        if (entry == null || tab == null || tab.isStartPage) {
            onResult(null)
            return
        }
        val targetUrl = tab.url
        val fallbackTitle = tab.title.ifBlank { tab.host.ifBlank { targetUrl } }
        val script = readerScriptCache ?: runCatching {
            context.assets.open("reader-article.js").bufferedReader().use { it.readText() }
        }.getOrNull()?.also { readerScriptCache = it }
        if (script == null) {
            onResult(null)
            return
        }
        entry.webView.evaluateJavascript(script) { rawJson ->
            runIo {
                val parsed = ReaderArticle.parse(rawJson, fallbackTitle)
                mainHandler.post {
                    val latestTab = state.tabs.firstOrNull { it.id == tabId }
                    if (latestTab?.url == targetUrl) {
                        onResult(parsed)
                    } else {
                        onResult(null)
                    }
                }
            }
        }
    }

    fun evictTab(tabId: String) {
        pendingSnapshotRunnables.remove(tabId)?.let { mainHandler.removeCallbacks(it) }
        lastSnapshotAtMs.remove(tabId)
        thumbnailUrls.remove(tabId)
        val removed = entries.remove(tabId)
        if (removed != null) {
            (removed.webView.parent as? ViewGroup)?.removeView(removed.webView)
            removed.webView.destroy()
        }
        savedStateCache.remove(tabId)
        state.favicons.remove(tabId)
        runIo {
            runCatching { File(thumbsDir, "$tabId.jpg").delete() }
            runCatching { File(faviconsDir, "$tabId.png").delete() }
            runCatching { File(statesDir, "$tabId.bin").delete() }
        }
    }

    private fun evictFromMemory(tabId: String) {
        captureSnapshot(tabId)
        saveTabState(tabId)
        pendingSnapshotRunnables.remove(tabId)?.let { mainHandler.removeCallbacks(it) }
        val removed = entries.remove(tabId) ?: return
        (removed.webView.parent as? ViewGroup)?.removeView(removed.webView)
        removed.webView.destroy()
    }

    fun destroyAll() {
        pendingSnapshotRunnables.values.forEach { mainHandler.removeCallbacks(it) }
        pendingSnapshotRunnables.clear()
        mainHandler.removeCallbacksAndMessages(null)
        saveAllActiveStatesAndSnapshots()
        entries.values.forEach { entry ->
            (entry.webView.parent as? ViewGroup)?.removeView(entry.webView)
            entry.webView.destroy()
        }
        entries.clear()
        ioExecutor.shutdown()
    }

    private fun evictIfNeeded(preserveTabId: String) {
        while (entries.size >= maxPoolSize) {
            val candidate = entries.entries
                .filter { it.key != preserveTabId && it.value.webView.parent == null }
                .minByOrNull { it.value.lastUsedAt }
                ?: entries.entries.firstOrNull { it.key != preserveTabId }
                ?: break
            evictFromMemory(candidate.key)
        }
    }

    private fun mobileUserAgent(base: String?): String {
        return base?.takeIf { it.isNotBlank() }
            ?: "Mozilla/5.0 (Linux; Android 15; Terra Browser) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
    }

    companion object {
        private const val DESKTOP_USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Safari/605.1.15"
    }
}
