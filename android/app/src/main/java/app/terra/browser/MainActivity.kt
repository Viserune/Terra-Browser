package app.terra.browser

import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.ImageBitmap
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.json.JSONArray
import java.util.UUID

data class Site(val name: String, val url: String)
internal data class OpeningPreview(val page: Page, val url: String, val image: ImageBitmap)

class Page(val secret: Boolean, initial: String = "", label: String = "", val id: String = UUID.randomUUID().toString()) {
    internal var pendingHistory: org.json.JSONObject? = null
    var documentVisible = false
    internal var savedPosition: ReadingPosition? = null
    var url by mutableStateOf(initial)
    var title by mutableStateOf(label)
    var groupId by mutableStateOf("")
    var pinned by mutableStateOf(false)
    var toolbarFold by mutableFloatStateOf(0f)
    var compactTools by mutableStateOf(false)
    var progress by mutableIntStateOf(100)
    var back by mutableStateOf(false)
    var forward by mutableStateOf(false)
    var error by mutableStateOf("")
    var siteZoom by mutableIntStateOf(100)
    var baseTextZoom = 100
    internal var readerArticle by mutableStateOf<ReaderArticle?>(null)
    internal var readerRequest by mutableIntStateOf(0)
    internal var readerOffset = 0
    var desktop by mutableStateOf(false)
    var web: WebView? = null
    var mobileAgent = ""
}

fun httpAddress(s: String?): Boolean {
    val u = s?.let(Uri::parse) ?: return false
    return u.scheme?.lowercase() in setOf("http", "https") && !u.host.isNullOrBlank()
}

internal class BrowserPrompt(val title: String, val message: String, val accept: String,
    val cancel: String, val destructive: Boolean, val onCancel: () -> Unit, val onAccept: () -> Unit)

open class MainActivity : ComponentActivity() {
    internal val menuTouches by lazy { MenuTouchSource(android.view.ViewConfiguration.get(this).scaledTouchSlop) }
    override fun dispatchTouchEvent(event: android.view.MotionEvent): Boolean =
        menuTouches.dispatch(event) { super.dispatchTouchEvent(it) }
    internal var bookmarkDraft by mutableStateOf<BookmarkDraft?>(null)
    internal var prompt by mutableStateOf<BrowserPrompt?>(null)
    internal fun confirm(title: String, message: String, accept: String, cancel: String = "取消",
                         destructive: Boolean = false, onCancel: () -> Unit = {}, onAccept: () -> Unit) {
        prompt?.onCancel?.invoke()
        prompt = BrowserPrompt(title, message, accept, cancel, destructive, onCancel, onAccept)
    }
    internal fun answer(question: BrowserPrompt, accepted: Boolean) {
        if (prompt !== question) return
        prompt = null
        if (accepted) question.onAccept() else question.onCancel()
    }

    internal val windowId by lazy {
        if (this is BrowserWindowActivity) UUID.fromString(intent.data!!.lastPathSegment).toString() else null
    }
    private val prefs by lazy { getSharedPreferences(windowId?.let { "terra-window-$it" } ?: "terra", MODE_PRIVATE) }
    internal val previews by lazy {
        TabPreviews(windowId?.let { java.io.File(cacheDir, "windows/$it") } ?: cacheDir)
    }
    internal var openingPreview by mutableStateOf<OpeningPreview?>(null)
    val tabs = mutableStateListOf<Page>()
    val bookmarks get() = BrowserLibrary.bookmarks
    val history get() = BrowserLibrary.history
    val reading get() = BrowserLibrary.reading
    var unreadOnly by mutableStateOf(false)
    internal val recentlyClosed get() = BrowserLibrary.closedTabs.entries
    var index by mutableIntStateOf(0)
    val page get() = tabs[index]
    private val lastTabId = mutableMapOf<Boolean, String>()
    internal val tabGroups = mutableStateMapOf<String, String>()
    internal var moveTarget by mutableStateOf<Page?>(null)
    private val lastGroupTab = mutableMapOf<String, String>()

    private fun rememberCurrentTab() {
        lastTabId[page.secret] = page.id
        if (!page.secret) lastGroupTab[page.groupId] = page.id
    }

    internal fun preferredTab(secret: Boolean, groupId: String? = null): Page? {
        if (!secret && groupId != null) {
            fun matches(tab: Page) = !tab.secret && tab.groupId == groupId
            return tabs.getOrNull(index)?.takeIf(::matches)
                ?: tabs.firstOrNull { matches(it) && it.id == lastGroupTab[groupId] }
                ?: tabs.lastOrNull(::matches)
        }
        return tabs.getOrNull(index)?.takeIf { it.secret == secret }
            ?: tabs.firstOrNull { it.id == lastTabId[secret] }
            ?: tabs.lastOrNull { it.secret == secret }
    }

    internal fun orderedTabs(secret: Boolean, groupId: String) = tabs.filter { it.secret == secret && (secret || it.groupId == groupId) }.sortedByDescending { it.pinned }
    internal val activeTabs by derivedStateOf { orderedTabs(page.secret, page.groupId) }
    internal fun adjacentTab(current: Page, forward: Boolean): Page? {
        val group = activeTabs
        val position = group.indexOf(current)
        return if (position < 0) null else group.getOrNull(position + if (forward) 1 else -1)
    }
    internal var findSeed = ""
    internal var findSerial by mutableIntStateOf(0)
        private set
    internal fun openFind(text: String = "") {
        findSeed = text
        findSerial++
        sheet = "查找"
    }

    internal val browserKeys = BrowserKeys(this)
    internal var addressKeyHandler: ((KeyEvent) -> Boolean)? = null
    // An unhandled key must continue through ComponentActivity's normal dispatch.
    @android.annotation.SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent) =
        (addressKeyHandler?.invoke(event) ?: browserKeys.handle(event)) || super.dispatchKeyEvent(event)

    var sheet by mutableStateOf("")
    internal var sidebarLocation = sidebarHome
    internal var pageMenuAnchor by mutableStateOf<androidx.compose.ui.geometry.Offset?>(null)
    internal val downloadAnchor = mutableStateOf<androidx.compose.ui.geometry.Rect?>(null)
    internal val downloads by lazy { BrowserDownloads(this) }
    var engine by BrowserLibrary::engine

    var webReady by mutableStateOf(false)
    var privateProfile = ""
    private val startupWorker = java.util.concurrent.Executors.newSingleThreadExecutor()
    private var upload: ValueCallback<Array<Uri>>? = null
    private var permission: PermissionRequest? = null
    internal var contextMenu by mutableStateOf<ContextMenu?>(null)
    internal var showPreviews by BrowserLibrary::showPreviews
    internal fun togglePreviews() {
        showPreviews = !showPreviews
        BrowserLibrary.saveSettings()
    }
    private class FullscreenSession(
        val page: Page, val view: View, val callback: WebChromeClient.CustomViewCallback,
        val browserView: View, val accessibility: Int, val visibleBars: Int, val barBehavior: Int
    )
    private var fullscreen by mutableStateOf<FullscreenSession?>(null)
    val videoFullscreen get() = fullscreen != null
    private val fullscreenBack = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() = leaveFullscreen()
    }
    private val media = mapOf(
        PermissionRequest.RESOURCE_VIDEO_CAPTURE to android.Manifest.permission.CAMERA,
        PermissionRequest.RESOURCE_AUDIO_CAPTURE to android.Manifest.permission.RECORD_AUDIO
    )
    private val mediaPermission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val req = permission ?: return@registerForActivityResult
        permission = null
        val allowed = req.resources.filter { r -> media[r]?.let {
            checkSelfPermission(it) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } == true }
        if (allowed.isEmpty() || !hasWindowFocus() && !lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) req.deny()
        else req.grant(allowed.toTypedArray())
    }
    private fun enterFullscreen(p: Page, view: View, callback: WebChromeClient.CustomViewCallback) {
        if (fullscreen != null || p !== page || isFinishing || isDestroyed) {
            callback.onCustomViewHidden()
            return
        }
        val content = findViewById<android.widget.FrameLayout>(android.R.id.content)
        val browserView = content.getChildAt(0)
        val bars = WindowCompat.getInsetsController(window, window.decorView)
        val insets = ViewCompat.getRootWindowInsets(content)
        val types = listOf(WindowInsetsCompat.Type.statusBars(), WindowInsetsCompat.Type.navigationBars())
        val visibleBars = types.filter { insets?.isVisible(it) != false }.fold(0, Int::or)
        fullscreen = FullscreenSession(p, view, callback, browserView, browserView.importantForAccessibility,
            visibleBars, bars.systemBarsBehavior)
        browserView.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        content.addView(view, android.widget.FrameLayout.LayoutParams(-1, -1))
        bars.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        bars.hide(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
        onBackPressedDispatcher.addCallback(fullscreenBack)
        notice("${Uri.parse(p.url).host.orEmpty()} · 返回可退出全屏")
    }
    private fun leaveFullscreen() {
        val session = fullscreen ?: return
        fullscreen = null
        fullscreenBack.remove()
        (session.view.parent as? ViewGroup)?.removeView(session.view)
        session.browserView.importantForAccessibility = session.accessibility
        val bars = WindowCompat.getInsetsController(window, window.decorView)
        bars.systemBarsBehavior = session.barBehavior
        if (session.visibleBars != 0) bars.show(session.visibleBars)
        session.callback.onCustomViewHidden()
    }
    private val chooser = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        upload?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(it.resultCode, it.data))
        upload = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        BrowserLibrary.attach(this)
        val groups = runCatching { org.json.JSONObject(prefs.getString("tabGroups", "{}")) }.getOrDefault(org.json.JSONObject())
        groups.keys().forEach { id ->
            val name = groups.optString(id).trim()
            if (id.isNotEmpty() && name.isNotEmpty()) tabGroups[id] = name
        }
        val savedTabs = runCatching { JSONArray(prefs.getString("tabs", "[]")) }.getOrDefault(JSONArray())
        val restoredIds = mutableSetOf<String>()
        for (i in 0 until savedTabs.length()) {
            val row = savedTabs.optJSONArray(i) ?: continue
            val candidate = row.optString(4)
            val id = candidate.takeIf {
                it.length == 36 && runCatching { UUID.fromString(it).toString() == it }.getOrDefault(false) && it !in restoredIds
            } ?: UUID.randomUUID().toString()
            restoredIds.add(id)
            tabs.add(Page(false, row.optString(1), row.optString(0), id).apply {
                pinned = row.optBoolean(2)
                desktop = row.optBoolean(7)
                pendingHistory = row.optJSONObject(8)
                groupId = row.optString(3).takeIf(tabGroups::containsKey).orEmpty()
                val x = row.optDouble(5, 0.0).toFloat()
                val y = row.optDouble(6, 0.0).toFloat()
                if (x.isFinite() && y.isFinite() && x >= 0 && y >= 0 && (x > 0 || y > 0))
                    savedPosition = ReadingPosition(url, x, y)
            })
        }
        previews.prune(restoredIds)
        val positions = runCatching { org.json.JSONObject(prefs.getString("groupSelections", "{}")) }.getOrDefault(org.json.JSONObject())
        positions.keys().forEach { group ->
            tabs.getOrNull(positions.optInt(group, -1))?.takeIf { it.groupId == group }?.let { lastGroupTab[group] = it.id }
        }
        if (tabs.isEmpty()) {
            val launch = BrowserWindowActivity.consumePrivateLink(windowId)
            val secret = this is BrowserWindowActivity && intent.getBooleanExtra("privateWindow", false) && profilesAvailable()
            val url = if (secret) launch.orEmpty() else prefs.getString("initialUrl", "").orEmpty()
            tabs.add(Page(secret, url))
        }
        index = prefs.getInt("index", 0).coerceIn(tabs.indices)
        if (windowId != null) save()
        if (savedInstanceState == null) openIntent(intent)
        else savedInstanceState.getString("librarySection")?.takeIf { it in librarySections }?.let { sheet = it }
        savedInstanceState?.getBundle("bookmarkDraft")?.let { bookmarkDraft = BookmarkDraft.restore(it) }
        setContent { BrowserScreen(this) }
        window.decorView.postOnAnimation { prepareEngine() }
    }

    @androidx.annotation.OptIn(androidx.webkit.WebViewCompat.ExperimentalAsyncStartUp::class)
    private fun prepareEngine() {
        val config = androidx.webkit.WebViewStartUpConfig.Builder(startupWorker).build()
        WebViewCompat.startUpWebView(applicationContext, config) {
            if (!isDestroyed && !isFinishing) {
                if (profilesAvailable()) {
                    val store = ProfileStore.getInstance()
                    store.allProfileNames.filter { it.startsWith("terra-private-") && it !in BrowserLibrary.windows.map { window -> window.privateProfile } }
                        .forEach { runCatching { store.deleteProfile(it) } }
                }
                webReady = true
            }
            startupWorker.shutdown()
        }
    }
    private fun openIntent(incoming: Intent) {
        if (incoming.action == Intent.ACTION_VIEW && httpAddress(incoming.dataString)) {
            contextMenu = null
            bookmarkDraft = null
            add(false, incoming.dataString!!)
        }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openIntent(intent)
    }

    private fun groupSelections(normal: List<Page>): String {
        val positions = org.json.JSONObject()
        normal.forEachIndexed { i, tab ->
            if (lastGroupTab[tab.groupId] == tab.id) positions.put(tab.groupId, i)
        }
        return positions.toString()
    }

    private fun encodeTabs(normal: List<Page>) = JSONArray().apply {
        normal.forEach { tab ->
            val row = JSONArray().put(tab.title).put(tab.url).put(tab.pinned).put(tab.groupId).put(tab.id)
            tab.savedPosition?.takeIf { it.url == tab.url }?.let { row.put(it.x.toDouble()).put(it.y.toDouble()) }
            if (tab.desktop) row.put(7,true)
            tab.pendingHistory?.let { row.put(8,it) }
            put(row)
        }
    }.toString()

    private fun rememberViewport(tab: Page) {
        val web = tab.web as? BrowserWebView ?: return
        if (tab.documentVisible && web.url == tab.url && tab.error.isBlank())
            tab.savedPosition = web.readingPosition(tab.url)
        previews.capture(tab)
    }

    fun save() {
        val normal = tabs.filterNot { it.secret }
        prefs.edit().remove("initialUrl").putString("tabs", encodeTabs(normal))
            .putString("tabGroups", org.json.JSONObject(tabGroups.toMap()).toString())
            .putString("groupSelections", groupSelections(normal))
            .putInt("index", normal.indexOf(preferredTab(false)).coerceAtLeast(0))
            .apply()
        BrowserLibrary.save()
    }

    fun notice(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
    private fun profilesAvailable() = WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)
    fun add(secret: Boolean, url: String = "", background: Boolean = false,
            groupId: String = if (secret) "" else preferredTab(false)?.groupId.orEmpty()) {
        if (secret && !webReady) { notice("正在准备浏览器，请稍后再试"); return }
        if (secret && !profilesAvailable()) { notice("请更新系统 WebView 以启用隔离的无痕标签"); return }
        insertPage(Page(secret,url),groupId,background)
    }

    private fun insertPage(newPage: Page, groupId: String, background: Boolean) {
        if (!background) {
            openingPreview = null
            rememberViewport(page)
            rememberCurrentTab()
        }
        if (!newPage.secret && groupId in tabGroups) newPage.groupId = groupId
        tabs.add(newPage)
        if (background) {
            notice("已在后台打开")
        } else {
            leaveFullscreen()
            index = tabs.lastIndex
            sheet = ""
        }
        save()
    }
    fun select(p: Page) = activateTab(p, dismiss=true)

    private fun activateTab(p: Page, dismiss: Boolean) {
        if (p !in tabs) return
        openingPreview = null
        rememberCurrentTab()
        if (p !== page) {
            rememberViewport(page)
            leaveFullscreen()
        }
        index = tabs.indexOf(p)
        if (dismiss) sheet = ""
        val normal = tabs.filterNot { it.secret }
        val normalIndex = normal.indexOf(preferredTab(false)).coerceAtLeast(0)
        prefs.edit().putInt("index", normalIndex).putString("groupSelections", groupSelections(normal))
            .putString("tabs", encodeTabs(normal)).apply()
    }

    internal fun prepareOverviewCard(tab: Page, image: ImageBitmap?) {
        activateTab(tab, dismiss=false)
        if (image != null && !tab.documentVisible && httpAddress(tab.url) && tab.error.isBlank())
            openingPreview = OpeningPreview(tab, tab.url, image)
    }
    internal fun groupChoices() = listOf("" to "普通标签") + tabGroups.toList().sortedBy { it.second }

    private fun registerGroup(name: String): String {
        require(name.isNotBlank())
        val id = UUID.randomUUID().toString()
        tabGroups[id] = name.trim()
        return id
    }

    internal fun openLinkInNewGroup(name: String, link: String) {
        add(false, link, groupId=registerGroup(name))
    }

    internal fun createGroup(name: String, tab: Page?): String {
        val id = registerGroup(name)
        if (tab != null && tab in tabs && !tab.secret) tab.groupId = id
        save()
        return id
    }

    internal fun moveTab(tab: Page, destination: String) {
        if (tab !in tabs || tab.secret || destination.isNotEmpty() && destination !in tabGroups) return
        tab.groupId = destination
        lastGroupTab[destination] = tab.id
        save()
    }

    internal fun removeGroup(id: String, closePages: Boolean) {
        if (tabGroups.remove(id) == null) return
        val members = tabs.filter { !it.secret && it.groupId == id }
        for (tab in members) {
            if (closePages) close(tab, persist = false) else tab.groupId = ""
        }
        lastGroupTab.remove(id)
        save()
    }

    internal fun closeOverviewTabs(requested: List<Page>, secret: Boolean, groupId: String) {
        for (tab in requested) {
            if (!tab.pinned && tab.secret == secret && (secret || tab.groupId == groupId)) close(tab, persist=false)
        }
        save()
    }

    internal fun reopen(entry: ClosedTab) {
        if (!page.secret) reopenInGroup(entry, page.groupId)
    }
    internal fun reopenInGroup(entry: ClosedTab, groupId: String) {
        val restored = BrowserLibrary.closedTabs.take(entry) ?: return
        insertPage(restored,groupId,background=false)
    }
    fun clearHistory() {
        history.clear()
        BrowserLibrary.closedTabs.clear()
        save()
    }
    fun close(p: Page, rememberClosed: Boolean = true, persist: Boolean = true) {
        if (p !in tabs) return
        if (openingPreview?.page === p) openingPreview = null
        previews.forget(p)
        if (fullscreen?.page === p) leaveFullscreen()
        val selected = page
        val old = index
        if (rememberClosed) BrowserLibrary.closedTabs.remember(p)
        p.web?.let { (it.parent as? ViewGroup)?.removeView(it); it.destroy() }
        p.web = null
        tabs.remove(p)
        if (p === selected && p.groupId in tabGroups && sheet.isEmpty() && rememberClosed &&
            tabs.none { it.groupId == p.groupId }) {
            tabs.add(Page(false).also { it.groupId = p.groupId })
        }
        if (tabs.isEmpty() || (!p.secret && tabs.all { it.secret })) tabs.add(Page(false))
        index = if (selected in tabs) tabs.indexOf(selected) else {
            tabs.indices.filter { tabs[it].secret == p.secret && tabs[it].groupId == p.groupId }.minByOrNull { kotlin.math.abs(it-old) }
                ?: tabs.indexOfLast { !it.secret }.takeIf { it >= 0 } ?: old.coerceIn(tabs.indices)
        }
        if (p.secret && tabs.none { it.secret }) downloads.endPrivateSession()
        if (p.secret && tabs.none { it.secret } && privateProfile.isNotEmpty()) {
            privateZoom.clear()
            val name = privateProfile
            privateProfile = ""
            runCatching { ProfileStore.getInstance().deleteProfile(name) }
        }
        if (persist) save()
    }
    internal fun destinationFor(input: String): String? {
        val s = input.trim()
        if (s.isEmpty()) return null
        val maybeUrl = if (s.contains("://")) s else "https://$s"
        val isHost = !s.any(Char::isWhitespace) && (s.contains("://") || s.substringBefore('/').contains('.') || s.substringBefore(':') == "localhost")
        val endpoint = when (engine) {
            "DuckDuckGo" -> "https://duckduckgo.com/?q="
            "Google" -> "https://www.google.com/search?q="
            else -> "https://www.bing.com/search?q="
        }
        return if (isHost && httpAddress(maybeUrl)) maybeUrl else endpoint + Uri.encode(s)
    }
    fun navigate(input: String) {
        val destination = destinationFor(input) ?: return
        loadDestination(destination)
    }

    internal fun openLinkPreview(menu: ContextMenu) {
        val url = menu.previewUrl ?: return
        if (page.web === menu.source && menu.isCurrent()) loadDestination(url, menu)
    }

    private fun loadDestination(destination: String, preview: ContextMenu? = null) {
        openingPreview = null
        page.error = ""
        sheet = ""
        page.url = destination
        page.web?.let { web ->
            val viewport = web.parent as? TabViewport
            val source = viewport?.historySwipe?.remember(web)
            viewport?.beginLinkNavigation(web, source, preview)
            web.loadUrl(destination)
        }
        save()
    }
    fun share(url: String) {
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url), null))
    }
    fun copy(url: String) {
        getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("网址", url))
    }
    fun saveForLater(site: Site) {
        BrowserLibrary.addReading(site)
        notice("已加入阅读列表")
    }

    fun openReading(site: Site, newTab: Boolean = false) {
        if (!page.secret) BrowserLibrary.setRead(site, true)
        if (newTab) add(page.secret, site.url) else navigate(site.url)
    }
    internal fun download(p: Page, url: String, name: String?, type: String?, retry: DownloadItem? = null) {
        if (!httpAddress(url)) { notice("此下载链接不受支持"); return }
        val requested = (retry?.title ?: URLUtil.guessFileName(url, name, type)).substringAfterLast('/').replace('\\', '_')
        confirm("下载文件", requested, "保存到下载目录") {
                runCatching {
                    val filename = downloads.availableName(requested)
                    val req = DownloadManager.Request(Uri.parse(url)).setTitle(filename)
                        .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename)
                        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    val cookies = if (p.secret) p.web?.let { WebViewCompat.getProfile(it).cookieManager } else CookieManager.getInstance()
                    cookies?.getCookie(url)?.let { req.addRequestHeader("Cookie", it) }
                    p.web?.settings?.userAgentString?.let { req.addRequestHeader("User-Agent", it) }
                    type?.takeIf { it.isNotBlank() }?.let(req::setMimeType)
                    downloads.add(p.secret, getSystemService(DownloadManager::class.java).enqueue(req), retry?.id)
                    notice("已开始下载")
                }.onFailure { notice("下载失败") }
        }
    }
    internal val zoomSteps = listOf(50,75,85,100,115,125,150,175,200,250,300)
    private val zoomPrefs by lazy { getSharedPreferences("site-text-zoom", MODE_PRIVATE) }
    private val privateZoom = mutableMapOf<String,Int>()

    private fun zoomSite(url: String) = if (httpAddress(url))
        Uri.parse(url).host?.lowercase(java.util.Locale.ROOT)?.trimEnd('.') else null

    private fun applySiteZoom(tab: Page, host: String?) {
        val percent = if (host == null) 100 else if (tab.secret) privateZoom[host] ?: 100
            else zoomPrefs.getInt(host,100)
        tab.siteZoom = percent
        tab.web?.settings?.let { settings ->
            val scaled = (tab.baseTextZoom * percent + 50) / 100
            if (settings.textZoom != scaled) settings.textZoom = scaled
        }
    }

    fun setSiteZoom(percent: Int) {
        if (percent !in zoomSteps) return
        val tab = page
        val host = zoomSite(tab.url) ?: return
        if (tab.secret) {
            if (percent == 100) privateZoom.remove(host) else privateZoom[host] = percent
        } else {
            val edit = zoomPrefs.edit()
            if (percent == 100) edit.remove(host) else edit.putInt(host,percent)
            edit.apply()
        }
        tabs.filter { it.secret == tab.secret && zoomSite(it.url) == host }.forEach { applySiteZoom(it,host) }
    }

    fun clearWebsiteData() {
        confirm("清除网站数据", "将关闭普通标签页，清除浏览历史、Cookie、缓存和本地网站数据。书签、阅读列表和下载文件会保留。", "清除", destructive = true) {
                zoomPrefs.edit().clear().apply()
                BrowserLibrary.closedTabs.clear()
                BrowserLibrary.clearWindowSessions(this)
                BrowserLibrary.windows.toList().forEach { browser ->
                    browser.tabs.filterNot { it.secret }.toList().forEach { browser.close(it, rememberClosed = false, persist = false) }
                    browser.save()
                }
                clearHistory()
                sheet = ""
                val storage = WebStorage.getInstance()
                if (WebViewFeature.isFeatureSupported(WebViewFeature.DELETE_BROWSING_DATA)) {
                    androidx.webkit.WebStorageCompat.deleteBrowsingData(storage) { notice("网站数据已清除") }
                } else {
                    WebView(this).also { it.clearCache(true); it.destroy() }
                    storage.deleteAllData()
                    CookieManager.getInstance().removeAllCookies { CookieManager.getInstance().flush(); notice("网站数据已清除") }
                }
        }
    }
    fun toggleDesktop(p: Page) {
        p.desktop = !p.desktop
        p.web?.settings?.userAgentString = pageAgent(p)
        p.web?.reload()
    }

    private fun pageAgent(p: Page) = if (p.desktop) "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) " +
        (Regex("Chrome/[0-9.]+").find(p.mobileAgent)?.value ?: "Chrome/124.0.0.0") + " Safari/537.36" else p.mobileAgent

    fun browser(p: Page): WebView {
        p.web?.let { return it }
        val web = BrowserWebView(this)
        web.pendingPosition = p.savedPosition?.takeIf { it.url == p.url }
        if (p.secret) {
            if (privateProfile.isEmpty()) privateProfile = "terra-private-" + UUID.randomUUID()
            WebViewCompat.setProfile(web, privateProfile)
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, false)
        p.web = web
        p.baseTextZoom = web.settings.textZoom
        applySiteZoom(p, zoomSite(p.url))
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            setSupportMultipleWindows(true)
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            useWideViewPort = true
            loadWithOverviewMode = true
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            p.mobileAgent = userAgentString
            if (p.desktop) userAgentString = pageAgent(p)
        }
        fun navigation(v: WebView) {
            p.back = v.canGoBack()
            p.forward = v.canGoForward()
        }
        fun showRestoredDocument(url: String, finished: Boolean) {
            val shown = openingPreview?.takeIf { it.page === p && it.url == url }
            web.restorePosition(url, finished) {
                if (shown != null) web.postOnAnimation { if (openingPreview === shown) openingPreview = null }
            }
        }
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(v: WebView, request: WebResourceRequest): Boolean {
                val link = request.url.toString()
                if (httpAddress(link)) {
                    if (request.isForMainFrame && request.hasGesture()) (v.parent as? TabViewport)?.historySwipe?.remember(v)
                    return false
                }
                if (request.isForMainFrame && request.hasGesture() && request.url.scheme in setOf("mailto", "tel")) {
                    runCatching { startActivity(Intent(Intent.ACTION_VIEW, request.url)) }.onFailure { notice("没有可用的应用") }
                }
                return true
            }
            override fun onPageStarted(v: WebView, url: String, icon: android.graphics.Bitmap?) {
                web.overviewPosition = null
                (v.parent as? TabViewport)?.pullRefresh?.documentStarted(v)
                if (p.savedPosition?.url != url) {
                    p.savedPosition = null
                    web.pendingPosition = null
                }
                if (openingPreview?.page === p && openingPreview?.url != url) openingPreview = null
                p.documentVisible = false
                p.url = url
                applySiteZoom(p, zoomSite(url))
                if ((sheet == "页面缩放" || sheet == "查找") && page === p) sheet = ""
                (v.parent as? TabViewport)?.readingToolbar?.resetDocument(p) ?: run { p.toolbarFold = 0f }
                p.compactTools = false
                p.title = ""
                p.readerRequest++
                p.readerOffset = 0
                p.readerArticle = null
                if (page === p && sheet == "阅读器") sheet = ""
                p.error = ""
                navigation(v)
            }
            override fun doUpdateVisitedHistory(v: WebView, url: String, reload: Boolean) {
                if (httpAddress(url)) {
                    if (p.url != url) {
                        p.readerRequest++
                        p.readerOffset = 0
                        p.readerArticle = null
                        if (page === p && sheet == "阅读器") sheet = ""
                    }
                    p.url = url
                    applySiteZoom(p, zoomSite(url))
                }
                navigation(v)
            }
            override fun onPageCommitVisible(v: WebView, url: String) {
                if (v.url == url) p.documentVisible = true
                if (v.url == url) (v.parent as? TabViewport)?.revealLinkDestination(v)
                if (v.url == url) (v.parent as? TabViewport)?.pullRefresh?.loaded(v)
                if (v.url == url) showRestoredDocument(url, finished=false)
                (v.parent as? TabViewport)?.historySwipe?.visualCommit(v)
            }
            override fun onFormResubmission(v: WebView, dontResend: android.os.Message, resend: android.os.Message) {
                (v.parent as? TabViewport)?.pullRefresh?.loaded(v)
                if (page !== p || prompt != null) {
                    dontResend.sendToTarget()
                    return
                }
                val revision = p.readerRequest
                confirm("重新提交表单？", "重新载入此页会再次发送上一次提交的内容。", "重新提交",
                    onCancel = { dontResend.sendToTarget() }) {
                    if (page === p && p.web === v && p.readerRequest == revision) resend.sendToTarget()
                    else dontResend.sendToTarget()
                }
            }
            override fun onPageFinished(v: WebView, url: String) {
                if (p !in tabs || !httpAddress(url) || url != v.url) return
                (v.parent as? TabViewport)?.revealLinkDestination(v)
                (v.parent as? TabViewport)?.pullRefresh?.loaded(v)
                if (web.pendingPosition != null) showRestoredDocument(url, finished=true)
                (v.parent as? TabViewport)?.historySwipe?.apply {
                    visualCommit(v)
                    rememberWhenDrawn(v,url)
                }
                p.title = v.title.orEmpty()
                navigation(v)
                if (!p.secret && p.error.isBlank()) {
                    history.removeAll { it.url == url }
                    history.add(0, Site(p.title.ifBlank { url }, url))
                    if (history.size > 300) history.removeAt(history.lastIndex)
                }
                save()
            }
            override fun onReceivedError(v: WebView, r: WebResourceRequest, e: WebResourceError) {
                if (r.isForMainFrame) {
                    (v.parent as? TabViewport)?.cancelLinkNavigation(v)
                    if (openingPreview?.page === p) openingPreview = null
                    p.error = e.description.toString()
                }
            }
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onShowCustomView(view: android.view.View, cb: CustomViewCallback) {
                enterFullscreen(p, view, cb)
            }
            override fun onHideCustomView() { if (fullscreen?.page === p) leaveFullscreen() }
            override fun onPermissionRequest(request: PermissionRequest) {
                val wanted = request.resources.mapNotNull(media::get).distinct()
                if (request.origin.scheme != "https" || wanted.isEmpty() || p != page || permission != null) {
                    request.deny(); return
                }
                permission = request
                val names = wanted.joinToString("、") { if (it == android.Manifest.permission.CAMERA) "相机" else "麦克风" }
                confirm(request.origin.host.orEmpty(), "允许此网站使用$names？", "允许", "拒绝",
                    onCancel = { if (permission === request) { permission = null; request.deny() } }) {
                        if (permission === request) {
                            if (p == page) mediaPermission.launch(wanted.toTypedArray())
                            else { permission = null; request.deny() }
                        }
                }
            }
            override fun onPermissionRequestCanceled(request: PermissionRequest) {
                if (permission === request) permission = null
            }
            override fun onProgressChanged(v: WebView, progress: Int) { p.progress = progress }
            override fun onReceivedTitle(v: WebView, title: String?) { p.title = title.orEmpty() }
            override fun onCreateWindow(v: WebView, dialog: Boolean, gesture: Boolean, result: android.os.Message): Boolean {
                if (!gesture) return false
                // Chromium supplies the requested URL through a temporary child WebView.
                val child = WebView(this@MainActivity)
                if (p.secret) WebViewCompat.setProfile(child, privateProfile)
                child.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        if (httpAddress(request.url.toString())) add(p.secret, request.url.toString(), groupId = p.groupId)
                        view.post { view.destroy() }
                        return true
                    }
                }
                (result.obj as WebView.WebViewTransport).webView = child
                result.sendToTarget()
                return true
            }
            override fun onShowFileChooser(v: WebView, cb: ValueCallback<Array<Uri>>, params: FileChooserParams): Boolean {
                upload?.onReceiveValue(null)
                upload = cb
                runCatching { chooser.launch(params.createIntent()) }.onFailure { upload = null; cb.onReceiveValue(null) }
                return true
            }
        }
        web.setDownloadListener { url, _, name, type, _ -> download(p, url, name, type) }
        bindPageMenu(p, web)
        val history = p.pendingHistory
        val initialUrl = p.url
        p.pendingHistory = null
        if (history != null && TabNavigationState.restore(web,history,initialUrl)) navigation(web)
        else {
            // A provider can partially restore a state it can no longer interpret.
            // Start the fallback URL in a fresh WebView so no unrelated entry survives.
            if (history != null && web.copyBackForwardList().size != 0) {
                web.webViewClient = WebViewClient()
                web.webChromeClient = null
                p.web = null
                p.url = initialUrl
                web.destroy()
                return browser(p)
            }
            web.loadUrl(initialUrl)
        }
        return web
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (!page.secret && sheet in librarySections) outState.putString("librarySection", sheet)
        if (!page.secret) bookmarkDraft?.let { outState.putBundle("bookmarkDraft", it.state()) }
        super.onSaveInstanceState(outState)
    }

    override fun onStop() {
        rememberViewport(page)
        contextMenu = null
        prompt?.let { answer(it, false) }
        save()
        super.onStop()
    }
    override fun onDestroy() {
        leaveFullscreen()
        tabs.forEach { p -> p.web?.let { (it.parent as? ViewGroup)?.removeView(it); it.destroy() }; p.web = null }
        if (privateProfile.isNotEmpty()) runCatching { ProfileStore.getInstance().deleteProfile(privateProfile) }
        BrowserLibrary.detach(this)
        super.onDestroy()
    }
}

