package app.terra.browser

import android.icu.text.Collator
import android.icu.text.RuleBasedCollator
import android.net.Uri
import androidx.compose.runtime.snapshots.Snapshot
import java.net.IDN
import java.util.Locale

internal enum class TabArrangement(val label: String) {
    Title("按标题排列"), Website("按网站排列")
}

internal fun MainActivity.arrangeTabs(secret: Boolean, groupId: String, by: TabArrangement) {
    val slots = tabs.indices.filter { i ->
        val tab = tabs[i]
        tab.secret == secret && (secret || tab.groupId == groupId) && !tab.pinned
    }
    val collator = Collator.getInstance().apply {
        strength = Collator.SECONDARY
        if (this is RuleBasedCollator) numericCollation = true
    }
    val ordered = slots.map { tabs[it] }
    val keys = ordered.associateWith { tab ->
        val host = Uri.parse(tab.url).host.orEmpty().lowercase(Locale.ROOT).removePrefix("www.")
        val website = IDN.toUnicode(host)
        if (by == TabArrangement.Title) tab.title.trim().ifEmpty { website } else website
    }
    // Equal titles or sites keep their previous reading order. Empty start pages come last.
    val sorted = ordered.sortedWith(compareBy<Page> { it.url.isBlank() || keys.getValue(it).isEmpty() }
        .thenComparator { a, b -> collator.compare(keys.getValue(a), keys.getValue(b)) })
    if (sorted == ordered) return
    applyTabOrder(slots, sorted)
}

internal fun MainActivity.moveOverviewTab(tab: Page, target: Page) {
    if (tab === target || tab !in tabs || target !in tabs) return
    val slots = tabs.indices.filter { i ->
        val other = tabs[i]
        other.secret == tab.secret && (tab.secret || other.groupId == tab.groupId) && other.pinned == tab.pinned
    }
    val order = slots.map { tabs[it] }.toMutableList()
    val index = order.indexOf(target)
    if (index < 0) return
    order.remove(tab)
    order.add(index, tab)
    applyTabOrder(slots, order)
}

private fun MainActivity.applyTabOrder(slots: List<Int>, sorted: List<Page>) {
    val selected = page
    Snapshot.withMutableSnapshot {
        slots.forEachIndexed { i, slot -> tabs[slot] = sorted[i] }
        index = tabs.indexOf(selected)
    }
    if (!sorted.first().secret) save()
}
