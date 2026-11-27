package app.terra.browser

import android.os.Bundle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.net.URI

internal class BookmarkDraft(val original: Site?, name: String, address: String, val originalFavorite: Boolean) {
    var name by mutableStateOf(name)
    var address by mutableStateOf(address)
    var favorite by mutableStateOf(originalFavorite)
    var error by mutableStateOf("")

    fun state() = Bundle().apply {
        putString("name", name)
        putString("address", address)
        putBoolean("originalFavorite", originalFavorite)
        putBoolean("favorite", favorite)
        original?.let { putString("originalName", it.name); putString("originalUrl", it.url) }
    }

    companion object {
        fun restore(state: Bundle): BookmarkDraft {
            val original = state.getString("originalUrl")?.let { Site(state.getString("originalName").orEmpty(), it) }
            return BookmarkDraft(original, state.getString("name").orEmpty(), state.getString("address").orEmpty(),
                state.getBoolean("originalFavorite", true)).also { it.favorite = state.getBoolean("favorite", true) }
        }
    }
}

internal fun MainActivity.editBookmark(site: Site) {
    val original = bookmarks.firstOrNull { it.url == site.url }
    bookmarkDraft = BookmarkDraft(original, original?.name ?: site.name, site.url,
        original?.let(BrowserLibrary::isFavorite) ?: true)
    (page.web?.parent as? TabViewport)?.readingToolbar?.expand()
    if (sheet == "页面菜单") sheet = ""
}

private fun bookmarkAddress(input: String): String? {
    val typed = input.trim()
    val address = when {
        typed.startsWith("https://", true) || typed.startsWith("http://", true) -> typed
        typed.startsWith("//") -> "https:$typed"
        else -> {
            val authority = typed.takeWhile { it !in "/?#" }
            // Allow host:port and bracketed IPv6, but never turn a mail address or another scheme into HTTP credentials.
            if ('@' in authority || (':' in authority && !authority.startsWith('[') &&
                    authority.substringAfter(':').toIntOrNull() == null)) return null
            "https://$typed"
        }
    }
    return runCatching { URI(address).toURL() }.getOrNull()
        ?.takeIf { it.host.isNotBlank() && it.port in -1..65535 }?.let { address }
}

internal fun MainActivity.commitBookmark(draft: BookmarkDraft) {
    if (draft !== bookmarkDraft) return
    val address = bookmarkAddress(draft.address)
    if (address == null) {
        draft.error = "请输入有效的 HTTP 或 HTTPS 网址"
        return
    }
    val original = draft.original
    val index = bookmarks.indexOf(original)
    if (original != null && (index == -1 || BrowserLibrary.isFavorite(original) != draft.originalFavorite)) {
        draft.error = "这条书签已在其他窗口更改，请重新打开"
        return
    }
    if (bookmarks.any { it.url == address && it != original }) {
        draft.error = "此网址已有书签"
        return
    }
    val saved = Site(draft.name.trim().ifBlank { address }, address)
    BrowserLibrary.putBookmark(index, saved, draft.favorite)
    bookmarkDraft = null
    notice("书签已保存")
}
