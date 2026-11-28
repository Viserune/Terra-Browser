package app.terra.browser

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import java.io.File
import java.util.UUID

/** A separate root task. Only an opaque window identifier enters Android's task history. */
class BrowserWindowActivity : MainActivity() {
    companion object {
        private val privateLinks = mutableMapOf<String, String>()
        internal fun consumePrivateLink(id: String?) = id?.let(privateLinks::remove)

        internal fun open(source: MainActivity, url: String, secret: Boolean) {
            if (!httpAddress(url)) return
            val id = UUID.randomUUID().toString()
            BrowserLibrary.registerWindow(source, id)
            if (secret) privateLinks[id] = url
            else source.getSharedPreferences("terra-window-$id", Context.MODE_PRIVATE)
                .edit().putString("initialUrl", url).apply()
            val launch = Intent(source, BrowserWindowActivity::class.java).apply {
                data = Uri.parse("terra-window://session/$id")
                putExtra("privateWindow", secret)
                addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                if (source.isInMultiWindowMode) addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT)
            }
            try {
                source.startActivity(launch)
            } catch (_: android.content.ActivityNotFoundException) {
                privateLinks.remove(id)
                BrowserLibrary.forgetWindow(source, id)
                source.notice("无法打开新窗口")
            }
        }
    }
}

/** Shared on the main thread. Tabs, group selection and thumbnail storage belong to each window. */
internal object BrowserLibrary {
    private lateinit var store: SharedPreferences
    private val launching = mutableSetOf<String>()
    private val catalogKey = "secondaryWindows"
    val windows = mutableSetOf<MainActivity>()
    val bookmarks = mutableStateListOf<Site>()
    private val favoriteUrls = mutableStateMapOf<String, Unit>()
    val favorites by derivedStateOf { bookmarks.filter { isFavorite(it) } }
    fun isFavorite(site: Site) = site.url in favoriteUrls

    private fun loadBookmarks() {
        val rows = runCatching { JSONArray(store.getString("bookmarks", "[]")) }.getOrDefault(JSONArray())
        val seen = mutableSetOf<String>()
        for (i in 0 until rows.length()) {
            val row = rows.optJSONArray(i) ?: continue
            val url = row.optString(1)
            if (url.isBlank() || !seen.add(url)) continue
            bookmarks.add(Site(row.optString(0).ifBlank { url }, url))
            if (row.optBoolean(2, true)) favoriteUrls[url] = Unit
        }
        if (!store.getBoolean("editableFavorites", false)) {
            val starters = listOf(Site("Wikipedia", "https://www.wikipedia.org"), Site("Apple", "https://www.apple.com"),
                Site("GitHub", "https://github.com"), Site("NASA", "https://www.nasa.gov"))
            starters.filter { it.url !in seen }.forEach { site ->
                bookmarks.add(site)
                favoriteUrls[site.url] = Unit
            }
            saveBookmarks()
        }
    }

    private fun saveBookmarks() {
        val rows = JSONArray().apply {
            bookmarks.forEach { put(JSONArray().put(it.name).put(it.url).put(isFavorite(it))) }
        }
        store.edit().putString("bookmarks", rows.toString()).putBoolean("editableFavorites", true).apply()
    }

    fun putBookmark(index: Int, site: Site, favorite: Boolean) {
        if (index < 0) bookmarks.add(0, site) else favoriteUrls.remove(bookmarks.set(index, site).url)
        if (favorite) favoriteUrls[site.url] = Unit
        saveBookmarks()
    }

    fun removeBookmark(site: Site) {
        if (!bookmarks.remove(site)) return
        favoriteUrls.remove(site.url)
        saveBookmarks()
    }
    val history = mutableStateListOf<Site>()
    private val readingItems = mutableStateListOf<Site>()
    val reading: List<Site> get() = readingItems
    private val readUrls = mutableStateMapOf<String, Unit>()
    fun isRead(site: Site) = site.url in readUrls

    private fun saveReading() {
        val rows = JSONArray()
        reading.forEach { rows.put(JSONArray().put(it.name).put(it.url).put(isRead(it))) }
        store.edit().putString("reading", rows.toString()).apply()
    }

    fun addReading(site: Site) {
        val previous = reading.find { it.url == site.url }
        if (previous != null) readingItems.remove(previous)
        readingItems.add(0, if (previous != null && site.name == site.url) previous else site)
        readUrls.remove(site.url)
        saveReading()
    }

    fun setRead(site: Site, read: Boolean) {
        if (site !in reading || isRead(site) == read) return
        if (read) readUrls[site.url] = Unit else readUrls.remove(site.url)
        saveReading()
    }

    fun removeReading(site: Site) {
        if (!readingItems.remove(site)) return
        readUrls.remove(site.url)
        saveReading()
    }
    val closedTabs = ClosedTabs()
    var engine by mutableStateOf("Bing")
    var phoneTabLayout by mutableStateOf(PhoneTabLayout.Compact)
        private set

    fun setPhoneLayout(layout: PhoneTabLayout) {
        phoneTabLayout = layout
        store.edit().putString("phoneTabLayout", layout.name).apply()
    }
    var showPreviews by mutableStateOf(true)
    var readerStyle by mutableStateOf(ReaderStyle())
        private set

    fun updateReaderStyle(style: ReaderStyle) {
        readerStyle = style
        store.edit().putString("readerTheme", style.theme.name).putBoolean("readerSans", style.sans)
            .putInt("readerPercent", style.percent).apply()
    }

    fun attach(window: MainActivity) {
        windows.add(window)
        window.windowId?.let(launching::remove)
        if (::store.isInitialized) return
        store = window.getSharedPreferences("terra", Context.MODE_PRIVATE)
        val savedLayout = store.getString("phoneTabLayout", null)
        val previousInstallation = store.contains("tabs") || store.contains("bookmarks") || store.contains("engine")
        val fallbackLayout = if (previousInstallation) PhoneTabLayout.Bottom else PhoneTabLayout.Compact
        phoneTabLayout = PhoneTabLayout.entries.firstOrNull { it.name == savedLayout } ?: fallbackLayout
        if (savedLayout != phoneTabLayout.name) setPhoneLayout(phoneTabLayout)
        fun read(key: String) = runCatching {
            val rows = JSONArray(store.getString(key, "[]"))
            List(rows.length()) { i -> rows.getJSONArray(i).let { Site(it.getString(0), it.getString(1)) } }
        }.getOrDefault(emptyList())
        loadBookmarks()
        history.addAll(read("history"))
        val readingRows = runCatching { JSONArray(store.getString("reading", "[]")) }.getOrDefault(JSONArray())
        val seen = mutableSetOf<String>()
        for (i in 0 until readingRows.length()) {
            val row = readingRows.optJSONArray(i) ?: continue
            val url = row.optString(1)
            if (url.isBlank() || !seen.add(url)) continue
            readingItems.add(Site(row.optString(0).ifBlank { url }, url))
            if (row.optBoolean(2, false)) readUrls[url] = Unit
        }
        closedTabs.load(store)
        engine = store.getString("engine", "Bing") ?: "Bing"
        showPreviews = store.getBoolean("showPreviews", true)
        readerStyle = ReaderStyle(
            ReaderTheme.entries.firstOrNull { it.name == store.getString("readerTheme", null) } ?: ReaderTheme.Auto,
            store.getBoolean("readerSans", false), store.getInt("readerPercent", 100).takeIf { it in readerSizes } ?: 100
        )
    }

    fun saveSettings() {
        store.edit().putString("engine", engine).putBoolean("showPreviews", showPreviews).apply()
    }

    fun save() {
        fun encode(rows: List<Site>) = JSONArray().apply {
            rows.forEach { put(JSONArray().put(it.name).put(it.url)) }
        }.toString()
        store.edit().putString("history", encode(history))
            .also { closedTabs.save(it) }
            .putString("engine", engine).apply()
    }

    private fun knownWindows() = store.getStringSet(catalogKey, emptySet()).orEmpty().toSet()

    fun registerWindow(source: MainActivity, id: String) {
        val manager = source.getSystemService(ActivityManager::class.java)
        val current = manager.appTasks.mapNotNull { it.taskInfo?.baseIntent?.data?.lastPathSegment }.toSet()
        val active = windows.mapNotNull { it.windowId }
        (knownWindows() - current - active - launching).forEach { forgetWindow(source, it) }
        launching.add(id)
        store.edit().putStringSet(catalogKey, knownWindows() + id).apply()
    }

    fun forgetWindow(context: Context, id: String) {
        launching.remove(id)
        context.deleteSharedPreferences("terra-window-$id")
        TabPreviews(File(context.cacheDir, "windows/$id")).prune(emptySet())
        store.edit().putStringSet(catalogKey, knownWindows() - id).apply()
    }

    fun detach(window: MainActivity) {
        windows.remove(window)
        if (window.isFinishing) window.windowId?.let { forgetWindow(window, it) }
    }

    fun clearWindowSessions(context: Context) {
        val active = windows.map { it.windowId }
        (knownWindows() - active.filterNotNull().toSet()).forEach { id ->
            context.getSharedPreferences("terra-window-$id", Context.MODE_PRIVATE).edit()
                .remove("initialUrl").remove("tabs").remove("index").remove("groupSelections").apply()
            TabPreviews(File(context.cacheDir, "windows/$id")).prune(emptySet())
        }
        if (null !in active) {
            store.edit().remove("tabs").remove("index").remove("groupSelections").apply()
            TabPreviews(context.cacheDir).prune(emptySet())
        }
    }
}
