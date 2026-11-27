package app.terra.browser

import android.content.SharedPreferences
import android.os.Bundle
import android.util.Base64
import android.webkit.WebView
import androidx.compose.runtime.mutableStateListOf
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** WebView owns the contents of its navigation state. Keep its byte arrays opaque and
 * fall back to the published URL when the provider changes or declines a restore. */
internal object TabNavigationState {
    private const val maxBytes = 96 * 1024

    private fun provider(web: WebView): String? =
        WebViewCompat.getCurrentWebViewPackage(web.context)?.let { it.packageName + "/" + it.longVersionCode }

    @androidx.annotation.OptIn(WebViewCompat.ExperimentalSaveState::class)
    fun capture(web: WebView, url: String): JSONObject? = runCatching {
        if (web.url != url) return null
        val version = provider(web) ?: return null
        val bundle = Bundle()
        if (WebViewFeature.isFeatureSupported(WebViewFeature.SAVE_STATE))
            WebViewCompat.saveState(web, bundle, maxBytes, true)
        else if (web.saveState(bundle) == null) return null
        val fields = JSONObject()
        var remaining = maxBytes
        for (key in bundle.keySet()) {
            val bytes = bundle.getByteArray(key) ?: return null
            if (bytes.size > remaining) return null
            remaining -= bytes.size
            fields.put(key, Base64.encodeToString(bytes, Base64.NO_WRAP))
        }
        if (fields.length()==0) null else JSONObject().put("provider",version).put("fields",fields)
    }.getOrNull()

    fun restore(web: WebView, state: JSONObject, url: String): Boolean = runCatching {
        if (state.optString("provider") != provider(web)) return false
        val fields = state.getJSONObject("fields")
        val bundle = Bundle()
        var remaining = maxBytes
        for (key in fields.keys()) {
            val encoded = fields.getString(key)
            if (encoded.length > ((remaining+2)/3)*4) return false
            val bytes = Base64.decode(encoded,Base64.DEFAULT)
            if (bytes.size > remaining) return false
            remaining -= bytes.size
            bundle.putByteArray(key,bytes)
        }
        web.restoreState(bundle)?.currentItem?.url == url
    }.getOrDefault(false)
}

internal data class ClosedTab(val id: String, val site: Site, val detail: JSONObject?)

internal class ClosedTabs {
    private val items = mutableStateListOf<ClosedTab>()
    val entries: List<ClosedTab> get() = items
    private var dirty = false

    fun load(store: SharedPreferences) {
        val rows = runCatching { JSONArray(store.getString("recentlyClosed","[]")) }.getOrNull() ?: return
        val seen = mutableSetOf<String>()
        for (i in 0 until rows.length()) {
            val row = rows.optJSONArray(i) ?: continue
            val url = row.optString(1)
            if (!httpAddress(url)) continue
            val id = row.optString(3).takeIf { it.isNotBlank() && seen.add(it) } ?: UUID.randomUUID().toString()
            items.add(ClosedTab(id,Site(row.optString(0).ifBlank { url },url),row.optJSONObject(2)))
            if (items.size == 20) break
        }
    }

    fun remember(page: Page) {
        if (page.secret || !httpAddress(page.url)) return
        val site = Site(page.title.ifBlank { page.url },page.url)
        val detail = JSONObject()
        if (page.desktop) detail.put("desktop",true)
        val web = page.web as? BrowserWebView
        val trail = web?.copyBackForwardList()
        if (trail != null && trail.currentIndex > 0 && trail.currentItem?.url == page.url) {
            val previous = trail.getItemAtIndex(trail.currentIndex-1)
            detail.put("from",previous.title?.takeIf { it.isNotBlank() }?.take(160)
                ?: android.net.Uri.parse(previous.url).host.orEmpty())
        }
        val position = if (web?.url == page.url && page.documentVisible && page.error.isBlank())
            web.readingPosition(page.url) else page.savedPosition
        position?.takeIf { it.url == page.url }?.let { detail.put("x",it.x.toDouble()).put("y",it.y.toDouble()) }
        val state = web?.takeIf { trail?.currentItem?.url == page.url }
            ?.let { TabNavigationState.capture(it,page.url) } ?: page.pendingHistory
        state?.let { detail.put("history",it) }
        items.add(0,ClosedTab(UUID.randomUUID().toString(),site,detail))
        if (items.size > 20) items.removeAt(items.lastIndex)
        dirty = true
    }

    fun take(entry: ClosedTab): Page? {
        if (!items.remove(entry)) return null
        dirty = true
        val site = entry.site
        val detail = entry.detail
        val page = Page(false,site.url,site.name)
        page.desktop = detail?.optBoolean("desktop") == true
        page.pendingHistory = detail?.optJSONObject("history")
        val x = detail?.optDouble("x",0.0)?.toFloat() ?: 0f
        val y = detail?.optDouble("y",0.0)?.toFloat() ?: 0f
        if (x.isFinite() && y.isFinite() && (x>0f || y>0f)) page.savedPosition = ReadingPosition(site.url,x,y)
        return page
    }

    fun remove(entry: ClosedTab) { if (items.remove(entry)) dirty = true }

    fun originHint(entry: ClosedTab): String? = entry.detail?.optString("from")?.takeIf { text ->
        text.isNotBlank() && items.any { it.id != entry.id && it.site.url == entry.site.url }
    }

    fun removeUrl(url: String) { if (items.removeAll { it.site.url == url }) dirty = true }

    fun clear() {
        items.clear()
        dirty = true
    }

    fun save(editor: SharedPreferences.Editor) {
        if (!dirty) return
        val rows = JSONArray()
        items.forEach { entry ->
            rows.put(JSONArray().put(entry.site.name).put(entry.site.url).put(entry.detail ?: JSONObject.NULL).put(entry.id))
        }
        editor.putString("recentlyClosed",rows.toString())
        dirty = false
    }
}
