package app.terra.browser

import android.os.Handler
import android.content.ClipboardManager
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.webkit.WebView
import android.webkit.WebView.HitTestResult

internal sealed interface MenuEntry { val label: String }
internal class MenuAction(override val label: String, val run: () -> Unit) : MenuEntry
internal class GroupDestinations(val groups: List<MenuAction>, val create: (String) -> Unit) : MenuEntry {
    override val label = "在标签页组中打开"
}
internal object ParentMenu : MenuEntry { override val label = "返回链接菜单" }
internal object NewLinkedGroup : MenuEntry { override val label = "新建标签组" }
private fun MutableList<MenuEntry>.item(label: String, action: () -> Unit) { add(MenuAction(label, action)) }

internal class ContextMenu(
    val x: Int, val y: Int,
    val previewUrl: String?, val source: WebView?,
    val items: List<MenuEntry>,
    val isCurrent: () -> Boolean
) {
    var dragAway: ((androidx.compose.ui.geometry.Offset) -> Boolean)? = null
    var startLinkDrag: (() -> Boolean)? = null
}

private fun MainActivity.showAnchoredActions(
    screenX: Int, screenY: Int,
    isCurrent: () -> Boolean,
    previewUrl: String? = null,
    source: WebView? = null,
    populate: MutableList<MenuEntry>.() -> Unit
) {
    if (!isCurrent()) return
    val items = buildList(populate)
    window.decorView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    contextMenu = ContextMenu(screenX, screenY, previewUrl, source, items, isCurrent)
}

internal fun MainActivity.tabCardMenu(tab: Page, x: Int, y: Int, visible: () -> Boolean,
    afterPin: () -> Unit = {}): ContextMenu {
    val actions = buildList<MenuEntry> {
        item(if (tab.pinned) "取消固定" else "固定标签页") {
            tab.pinned = !tab.pinned
            save()
            afterPin()
        }
        if (!tab.secret) item("移至标签组") { moveTarget = tab; sheet = "标签页" }
        item("关闭标签页") { close(tab) }
        if (windowId != null) item("关闭窗口") { finishAndRemoveTask() }
    }
    return ContextMenu(x, y, null, null, actions) { tab in tabs && visible() }
}

internal fun MainActivity.showBookmarkActions(site: Site, x: Int, y: Int, inLibrary: Boolean = false) {
    val source = page
    val saved = bookmarks.firstOrNull { it.url == site.url }
    val inAddress = sheet == "地址"
    showAnchoredActions(x, y, {
        !isFinishing && !isDestroyed && page === source &&
            (if (inLibrary) sheet == "书签" && saved != null else if (inAddress) sheet == "地址" && BrowserLibrary.isFavorite(site)
                else page.url.isEmpty() && sheet.isEmpty()) &&
            bookmarks.firstOrNull { it.url == site.url } == saved
    }) {
        item("打开") { navigate(site.url) }
        item("在新标签中打开") { add(source.secret, site.url) }
        item(if (saved == null) "添加书签" else "编辑书签") {
            if (inAddress) sheet = ""
            editBookmark(site)
        }
        item("复制地址") { copy(site.url) }
        item("共享") { share(site.url) }
        if (saved != null) item("删除书签") {
            confirm("删除书签", "删除「${saved.name}」？", "删除", destructive = true) {
                BrowserLibrary.removeBookmark(saved)
            }
        }
    }
}

internal fun MainActivity.showTabActions(tab: Page, x: Int, y: Int) {
    showAnchoredActions(x, y, { !isDestroyed && !isFinishing && tabs.getOrNull(index) === tab && sheet.isEmpty() }) {
        item("新建标签页") { add(false) }
        item("新建无痕标签页") { add(true) }
        if (!tab.secret && recentlyClosed.isNotEmpty()) item("最近关闭") { sheet = "最近关闭" }
        item("关闭标签页") { close(tab) }
        if (windowId != null) item("关闭窗口") { finishAndRemoveTask() }
    }
}

internal fun MainActivity.showAddressActions(tab: Page, x: Int, y: Int) {
    val url = tab.url
    val clipboard = getSystemService(ClipboardManager::class.java)
    showAnchoredActions(x, y, {
        !isFinishing && !isDestroyed && tabs.getOrNull(index) === tab && tab.url == url && sheet.isEmpty()
    }) {
        if (url.isNotBlank()) item("复制网址") { copy(url) }
        item("编辑地址") { sheet = "地址" }
        if (clipboard.primaryClipDescription?.hasMimeType("text/*") == true) {
            item("粘贴并前往") {
                val clip = clipboard.primaryClip
                val first = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)
                val input = first?.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }
                    ?: first?.uri?.toString()?.takeIf(::httpAddress)
                if (input == null) notice("剪贴板中没有可用的网址或文字") else navigate(input)
            }
        }
    }
}

internal fun MainActivity.showReadingActions(site: Site, x: Int, y: Int) {
    val source = page
    val read = BrowserLibrary.isRead(site)
    showAnchoredActions(x, y, {
        !isDestroyed && !isFinishing && page === source && sheet == "阅读列表" &&
            site in reading && BrowserLibrary.isRead(site) == read
    }) {
        item("打开") { openReading(site) }
        item("在新标签中打开") { openReading(site, newTab = true) }
        item(if (read) "标记为未读" else "标记为已读") { BrowserLibrary.setRead(site, !read) }
        item("复制地址") { copy(site.url) }
        item("共享") { share(site.url) }
        item("删除") { BrowserLibrary.removeReading(site) }
    }
}

internal fun MainActivity.showNavigationHistory(tab: Page, backwards: Boolean, x: Int, y: Int) {
    val web = tab.web ?: return
    val history = web.copyBackForwardList()
    val start = history.currentIndex
    val entries = if (backwards) (start - 1 downTo 0) else (start + 1 until history.size)
    if (entries.isEmpty()) return
    showAnchoredActions(x, y, { !isDestroyed && web.isAttachedToWindow && tabs.getOrNull(index) === tab }) {
        for (target in entries) {
            val entry = history.getItemAtIndex(target)
            item(entry.title?.takeIf { it.isNotBlank() } ?: entry.url) {
                val now = web.copyBackForwardList()
                if (now.currentIndex == start && target < now.size && now.getItemAtIndex(target).url == entry.url) {
                    web.goBackOrForward(target - start)
                }
            }
        }
    }
}

internal fun MainActivity.bindPageMenu(tab: Page, web: WebView) {
    var x = 0
    var y = 0
    var press = 0
    web.setOnTouchListener { _, event ->
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            press++
            x = event.rawX.toInt()
            y = event.rawY.toInt()
        }
        false
    }
    web.setOnLongClickListener {
        val hit = web.hitTestResult
        val fallback = hit.extra?.takeIf(::httpAddress)
        val document = web.url
        val gesture = press
        fun current() = gesture == press && !isFinishing && !isDestroyed &&
            web.isAttachedToWindow && tabs.getOrNull(index) === tab && web.url == document && sheet.isEmpty()

        fun show(link: String?, image: String?, title: String? = null) {
            if (!current() || (link == null && image == null)) return
            val tablet = resources.configuration.screenWidthDp >= 650
            showAnchoredActions(x, y, ::current, link ?: image, web) {
                if (link != null) {
                    val foreground = MenuAction("在新标签中打开") { add(tab.secret, link) }
                    val file = if (image == null) MenuAction("下载") { download(tab, link, null, null) } else null
                    item("打开") { navigate(link) }
                    item("在后台打开") { add(tab.secret, link, background = true) }
                    if (!tablet) add(foreground)
                    if (!tab.secret) {
                        val destinations = groupChoices().map { (id, name) ->
                            MenuAction(name) {
                                if (id.isEmpty() || id in tabGroups) this@bindPageMenu.add(false, link, groupId=id)
                            }
                        }
                        add(GroupDestinations(destinations) { name ->
                            openLinkInNewGroup(name, link)
                        })
                    }
                    item("在新窗口中打开") { BrowserWindowActivity.open(this@bindPageMenu, link, tab.secret) }
                    if (tablet && file != null) add(file)
                    if (!tab.secret) item("添加到阅读列表") { saveForLater(Site(link, link)) }
                    item("复制地址") { copy(link) }
                    item("共享") { share(link) }
                    if (tablet) add(foreground) else if (file != null) add(file)
                }
                if (image != null) {
                    item("在新标签中查看图片") { add(tab.secret, image) }
                    item("复制图片地址") { copy(image) }
                    item("下载图片") { download(tab, image, null, null) }
                    if (link == null) item("共享图片地址") { share(image) }
                }
            }
            if (link != null && tablet) {
                val menu = contextMenu ?: return
                var label = title
                if (label.isNullOrBlank()) {
                    val reply = Handler(Looper.getMainLooper()) { message ->
                        if (contextMenu === menu && message.data.getString("url") == link)
                            label = message.data.getString("title")
                        true
                    }
                    web.requestFocusNodeHref(reply.obtainMessage())
                }
                menu.startLinkDrag = { current() && web.dragPageLink(link, label, tab.secret) }
            }
        }

        when (hit.type) {
            HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> {
                // WebView returns the anchor href and the image src separately for a linked image.
                val response = Handler(Looper.getMainLooper()) { message ->
                    val link = message.data.getString("url")?.takeIf(::httpAddress)
                    val image = message.data.getString("src")?.takeIf(::httpAddress)
                    show(link, image ?: fallback?.takeUnless { it == link }, message.data.getString("title"))
                    true
                }
                web.requestFocusNodeHref(response.obtainMessage())
                true
            }
            HitTestResult.SRC_ANCHOR_TYPE -> {
                show(fallback, null)
                fallback != null
            }
            HitTestResult.IMAGE_TYPE -> {
                show(null, fallback)
                fallback != null
            }
            else -> false
        }
    }
}
