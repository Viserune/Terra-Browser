package app.terra.browser

import android.net.Uri
import io.liquidkit.LiquidKitSymbol

internal class AddressSuggestion(val key: String, val icon: LiquidKitSymbol, val title: String,
    val detail: String, val choose: () -> Unit)

internal fun MainActivity.addressSuggestions(input: String): List<AddressSuggestion> = buildList {
    val term = input.trim()
    val current = page
    val openUrls = mutableSetOf<String>()
    if (term.isNotEmpty()) {
        val matches = tabs.filter { it !== current && it.secret == current.secret && it.url.isNotBlank() &&
            (it.title.contains(term,true) || it.url.contains(term,true)) }
            .sortedWith(compareByDescending<Page> { it.url.equals(term,true) || it.title.equals(term,true) }
                .thenByDescending { it.groupId == current.groupId }
                .thenByDescending { it.title.startsWith(term,true) })
            .take(6)
        for (tab in matches) {
            openUrls.add(tab.url)
            val group = if (tab.secret) "无痕浏览" else tabGroups[tab.groupId] ?: "普通标签"
            val title = tab.title.ifBlank { tab.url }
            val address = tab.url.substringAfter("://")
            val sameAddress = matches.any { it !== tab && it.groupId == tab.groupId && it.url == tab.url }
            val location = if (sameAddress) {
                val position = orderedTabs(tab.secret,tab.groupId).indexOf(tab) + 1
                "$group · 第 ${position} 个标签"
            } else group
            add(AddressSuggestion("tab/"+tab.id, LiquidKitSymbol.SquareOnSquare, title,
                "切换到标签页 · $location · $address") { select(tab) })
        }
        add(AddressSuggestion("go", LiquidKitSymbol.Search, "前往", term) { navigate(term) })
        if (current.url.isNotEmpty() && term != current.url)
            add(AddressSuggestion("find", LiquidKitSymbol.Search, "在此页查找", term) { openFind(term) })
    }
    val saved = bookmarks.asSequence() + (if (current.secret) emptySequence() else history.asSequence())
    for (site in saved.filter { it.url !in openUrls && (term.isEmpty() || it.name.contains(term,true) || it.url.contains(term,true)) }
        .distinctBy { it.url }.take(8)) {
        add(AddressSuggestion("site/"+site.url, LiquidKitSymbol.Link,site.name,Uri.parse(site.url).host.orEmpty()) { navigate(site.url) })
    }
}
