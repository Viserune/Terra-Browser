package com.terra.browser.state

import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import com.terra.browser.model.AutoCloseTabsMode
import com.terra.browser.model.BookmarkItem
import com.terra.browser.model.BrowserTab
import com.terra.browser.model.BrowserUrlUtils
import com.terra.browser.model.ContextHitInfo
import com.terra.browser.model.DarkModePreference
import com.terra.browser.model.FavoriteItem
import com.terra.browser.model.HistoryEntry
import com.terra.browser.model.LibraryTab
import com.terra.browser.model.OpenLinkMode
import com.terra.browser.model.PageMenuItemConfig
import com.terra.browser.model.ReadingListItem
import com.terra.browser.model.SearchEngine
import com.terra.browser.model.SiteDarkMode
import com.terra.browser.model.TabBarMode
import com.terra.browser.model.TabGroup
import com.terra.browser.model.TabSortMode
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors

@Stable
class TerraBrowserState(
    private val prefs: SharedPreferences? = null,
) {
    val tabGroups = mutableStateListOf<TabGroup>()
    val tabs = mutableStateListOf<BrowserTab>()
    val favorites = mutableStateListOf<FavoriteItem>()
    val bookmarks = mutableStateListOf<BookmarkItem>()
    val readingList = mutableStateListOf<ReadingListItem>()
    val history = mutableStateListOf<HistoryEntry>()
    val recentSearches = mutableStateListOf<String>()
    val pageMenuItems = mutableStateListOf<PageMenuItemConfig>()

    val activeTabIdByGroup = mutableStateMapOf<String, String>()
    val thumbnails = mutableStateMapOf<String, ImageBitmap>()
    val favicons = mutableStateMapOf<String, ImageBitmap>()
    val blockedDomainCounts = mutableStateMapOf<String, Int>()

    var activeGroupId by mutableStateOf("main")
    var searchEngine by mutableStateOf(SearchEngine.BING)
    var isOverviewOpen by mutableStateOf(false)
    var isAddressEditing by mutableStateOf(false)
    var addressDraft by mutableStateOf("")
    var isToolbarMinimized by mutableStateOf(false)
    var isToolbarHidden by mutableStateOf(false)

    var findInPageVisible by mutableStateOf(false)
    var findQuery by mutableStateOf("")
    var findMatchIndex by mutableIntStateOf(0)
    var findMatchCount by mutableIntStateOf(0)

    var sortMode by mutableStateOf(TabSortMode.MANUAL)
    var overviewSearchQuery by mutableStateOf("")
    var isOverviewSearchActive by mutableStateOf(false)

    var glassOpacity by mutableFloatStateOf(0.72f)
    var darkModePreference by mutableStateOf(DarkModePreference.SYSTEM)
    var totalTrackersBlocked by mutableIntStateOf(32)

    var tabBarMode by mutableStateOf(TabBarMode.BOTTOM)
    var extendWebsiteTint by mutableStateOf(true)
    var showRecentSearches by mutableStateOf(true)
    var blockPopups by mutableStateOf(true)
    var preventCrossSiteTracking by mutableStateOf(true)
    var warnInsecureConnection by mutableStateOf(false)
    var openLinkMode by mutableStateOf(OpenLinkMode.NEW_TAB)
    var autoCloseTabsMode by mutableStateOf(AutoCloseTabsMode.MANUAL)

    var showLibrarySheet by mutableStateOf(false)
    var libraryInitialTab by mutableStateOf(LibraryTab.BOOKMARKS)
    var showShareSheet by mutableStateOf(false)
    var showCustomizeMenuSheet by mutableStateOf(false)
    var showNewGroupAlert by mutableStateOf(false)
    var showZoomSheet by mutableStateOf(false)
    var showPrivacyReportSheet by mutableStateOf(false)
    var showSettingsSheet by mutableStateOf(false)
    var showManageGroupsSheet by mutableStateOf(false)
    var activeContextHit by mutableStateOf<ContextHitInfo?>(null)
    var showAllFavorites by mutableStateOf(false)
    var statusBannerMessage by mutableStateOf<String?>(null)

    var isTabletSidebarOpen by mutableStateOf(false)
    var showTabletTabBar by mutableStateOf(true)

    var newGroupDraft by mutableStateOf("")
    var onBackDismissChromeMenu: (() -> Boolean)? = null

    private val persistExecutor = if (prefs != null) Executors.newSingleThreadExecutor() else null
    private val persistHandler = if (prefs != null) runCatching { Handler(Looper.getMainLooper()) }.getOrNull() else null
    private val pendingPersistRunnable = Runnable { serializeAndApplyAsync() }

    private fun buildStateJsonObject(): JSONObject {
        val snapshotGroups = tabGroups.toList()
        val snapshotTabs = tabs.toList()
        val snapshotActiveMap = activeTabIdByGroup.toMap()
        val snapshotBlocked = blockedDomainCounts.toMap()
        val snapshotFavorites = favorites.toList()
        val snapshotBookmarks = bookmarks.toList()
        val snapshotReading = readingList.toList()
        val snapshotHistory = history.take(50)
        val snapshotSearches = recentSearches.toList()
        val snapshotMenuItems = pageMenuItems.toList()
        val root = JSONObject()
        root.put("activeGroupId", activeGroupId)
        root.put("searchEngine", searchEngine.name)
        root.put("glassOpacity", glassOpacity.toDouble())
        root.put("darkModePreference", darkModePreference.name)
        root.put("totalTrackersBlocked", totalTrackersBlocked)
        root.put("tabBarMode", tabBarMode.name)
        root.put("extendWebsiteTint", extendWebsiteTint)
        root.put("showRecentSearches", showRecentSearches)
        root.put("blockPopups", blockPopups)
        root.put("preventCrossSiteTracking", preventCrossSiteTracking)
        root.put("warnInsecureConnection", warnInsecureConnection)
        root.put("openLinkMode", openLinkMode.name)
        root.put("autoCloseTabsMode", autoCloseTabsMode.name)
        root.put("showTabletTabBar", showTabletTabBar)

        val activeMapJson = JSONObject()
        snapshotActiveMap.forEach { (k, v) -> activeMapJson.put(k, v) }
        root.put("activeTabIdByGroup", activeMapJson)

        val blockedJson = JSONObject()
        snapshotBlocked.forEach { (k, v) -> blockedJson.put(k, v) }
        root.put("blockedDomainCounts", blockedJson)

        root.put("tabGroups", JSONArray().apply {
            snapshotGroups.forEach { g ->
                put(JSONObject().apply {
                    put("id", g.id)
                    put("name", g.name)
                    put("isPrivate", g.isPrivate)
                    put("isDefault", g.isDefault)
                    put("accentColorArgb", g.accentColorArgb)
                })
            }
        })

        root.put("tabs", JSONArray().apply {
            snapshotTabs.forEach { t ->
                put(JSONObject().apply {
                    put("id", t.id)
                    put("groupId", t.groupId)
                    put("url", t.url)
                    put("title", t.title)
                    put("host", t.host)
                    put("accentColorArgb", t.accentColorArgb)
                    put("isPinned", t.isPinned)
                    put("requestDesktopSite", t.requestDesktopSite)
                    put("adaptiveDarkMode", t.adaptiveDarkMode.name)
                    put("contentBlockerEnabled", t.contentBlockerEnabled)
                    put("pageZoom", t.pageZoom)
                    put("blockedTrackers", t.blockedTrackers)
                })
            }
        })

        root.put("favorites", JSONArray().apply {
            snapshotFavorites.forEach { f ->
                put(JSONObject().apply {
                    put("id", f.id)
                    put("title", f.title)
                    put("url", f.url)
                    put("shortGlyph", f.shortGlyph)
                    put("colorArgb", f.colorArgb)
                })
            }
        })

        root.put("bookmarks", JSONArray().apply {
            snapshotBookmarks.forEach { b ->
                put(JSONObject().apply {
                    put("id", b.id)
                    put("title", b.title)
                    put("url", b.url)
                    put("folder", b.folder)
                })
            }
        })

        root.put("readingList", JSONArray().apply {
            snapshotReading.forEach { r ->
                put(JSONObject().apply {
                    put("id", r.id)
                    put("title", r.title)
                    put("url", r.url)
                    put("excerpt", r.excerpt)
                    put("isRead", r.isRead)
                })
            }
        })

        root.put("history", JSONArray().apply {
            snapshotHistory.forEach { h ->
                put(JSONObject().apply {
                    put("id", h.id)
                    put("title", h.title)
                    put("url", h.url)
                    put("visitedAt", h.visitedAt)
                })
            }
        })

        root.put("recentSearches", JSONArray().apply {
            snapshotSearches.forEach { put(it) }
        })

        root.put("pageMenuItems", JSONArray().apply {
            snapshotMenuItems.forEachIndexed { idx, cfg ->
                put(JSONObject().apply {
                    put("id", cfg.id)
                    put("enabled", cfg.enabled)
                    put("order", idx)
                })
            }
        })
        return root
    }

    private fun serializeAndApplyAsync() {
        val targetPrefs = prefs ?: return
        val exec = persistExecutor ?: return
        val root = runCatching { buildStateJsonObject() }.getOrNull() ?: return
        runCatching {
            exec.execute {
                runCatching {
                    targetPrefs.edit().putString(PREF_KEY, root.toString()).apply()
                }
            }
        }
    }

    init {
        if (!restore()) {
            seedDefaults()
        }
        ensureGroupActiveTabs()
    }

    val activeGroup: TabGroup
        get() = tabGroups.firstOrNull { it.id == activeGroupId }
            ?: tabGroups.firstOrNull { it.isDefault }
            ?: tabGroups.firstOrNull()
            ?: TabGroup(id = "main", name = "标签页", isPrivate = false, isDefault = true).also {
                tabGroups.add(it)
            }

    val activeTab: BrowserTab
        get() {
            val gId = activeGroup.id
            val tabId = activeTabIdByGroup[gId]
            return tabs.firstOrNull { it.id == tabId && it.groupId == gId }
                ?: tabs.firstOrNull { it.groupId == gId }
                ?: createTab(groupId = gId, url = "", select = true)
        }

    fun tabsInGroup(groupId: String, includeSearchFilter: Boolean = false): List<BrowserTab> {
        val groupTabs = tabs.filter { it.groupId == groupId }
        val filtered = if (includeSearchFilter && overviewSearchQuery.isNotBlank()) {
            val q = overviewSearchQuery.trim().lowercase(Locale.ROOT)
            groupTabs.filter {
                it.title.lowercase(Locale.ROOT).contains(q) ||
                    it.host.lowercase(Locale.ROOT).contains(q) ||
                    it.url.lowercase(Locale.ROOT).contains(q)
            }
        } else {
            groupTabs
        }
        return filtered.sortedByDescending { it.isPinned }
    }

    fun pinnedTabsInGroup(groupId: String, includeSearchFilter: Boolean = false): List<BrowserTab> =
        tabsInGroup(groupId, includeSearchFilter).filter { it.isPinned }

    fun regularTabsInGroup(groupId: String, includeSearchFilter: Boolean = false): List<BrowserTab> =
        tabsInGroup(groupId, includeSearchFilter).filter { !it.isPinned }

    fun groupTitle(group: TabGroup): String {
        val count = tabs.count { it.groupId == group.id }
        return if (group.isDefault) "${count}个标签页" else group.name
    }

    fun selectGroup(groupId: String) {
        if (tabGroups.none { it.id == groupId }) return
        activeGroupId = groupId
        ensureGroupActiveTabs()
        persist()
    }

    fun selectTab(tabId: String) {
        val tab = tabs.firstOrNull { it.id == tabId } ?: return
        activeGroupId = tab.groupId
        activeTabIdByGroup[tab.groupId] = tab.id
        updateTab(tab.id) { it.copy(lastActiveAt = System.currentTimeMillis()) }
        isToolbarMinimized = false
        isToolbarHidden = false
        persist()
    }

    fun createTab(
        groupId: String = activeGroup.id,
        url: String = "",
        title: String? = null,
        select: Boolean = true,
        insertAfterActive: Boolean = false,
    ): BrowserTab {
        val normalized = if (url.isBlank()) "" else BrowserUrlUtils.normalizeInputToUrl(url, searchEngine)
        val host = BrowserUrlUtils.displayHost(normalized)
        val newTab = BrowserTab(
            id = "tab_${UUID.randomUUID().toString().take(8)}",
            groupId = groupId,
            url = normalized,
            title = title ?: if (normalized.isBlank()) "起始页" else host.ifBlank { normalized },
            host = host,
            accentColorArgb = BrowserUrlUtils.accentColorForUrl(normalized),
        )
        if (insertAfterActive) {
            val activeId = activeTabIdByGroup[groupId]
            val idx = tabs.indexOfFirst { it.id == activeId }
            if (idx >= 0) tabs.add(idx + 1, newTab) else tabs.add(newTab)
        } else {
            tabs.add(newTab)
        }
        if (select) {
            activeGroupId = groupId
            activeTabIdByGroup[groupId] = newTab.id
            isToolbarMinimized = false
            isToolbarHidden = false
        }
        persist()
        return newTab
    }

    fun closeTab(tabId: String) {
        val idx = tabs.indexOfFirst { it.id == tabId }
        if (idx < 0) return
        val closing = tabs[idx]
        val gId = closing.groupId
        val wasActive = activeTabIdByGroup[gId] == tabId
        val groupListBefore = tabs.filter { it.groupId == gId }
        val posInGroup = groupListBefore.indexOfFirst { it.id == tabId }

        tabs.removeAt(idx)
        thumbnails.remove(tabId)
        favicons.remove(tabId)

        val remaining = tabs.filter { it.groupId == gId }
        if (remaining.isEmpty()) {
            val fresh = createTab(groupId = gId, url = "", select = true)
            activeTabIdByGroup[gId] = fresh.id
        } else if (wasActive) {
            val fallbackIndex = (posInGroup - 1).coerceIn(0, remaining.lastIndex.coerceAtLeast(0))
            activeTabIdByGroup[gId] = remaining[fallbackIndex].id
        }
        persist()
    }

    fun closeAllTabsInGroup(groupId: String = activeGroup.id) {
        val ids = tabs.filter { it.groupId == groupId }.map { it.id }
        tabs.removeAll { it.groupId == groupId }
        ids.forEach {
            thumbnails.remove(it)
            favicons.remove(it)
        }
        val fresh = createTab(groupId = groupId, url = "", select = true)
        activeTabIdByGroup[groupId] = fresh.id
        persist()
    }

    fun closeOtherTabsInGroup(keepTabId: String) {
        val keeper = tabs.firstOrNull { it.id == keepTabId } ?: return
        val gId = keeper.groupId
        val toRemove = tabs.filter { it.groupId == gId && it.id != keepTabId }.map { it.id }
        tabs.removeAll { it.groupId == gId && it.id != keepTabId }
        toRemove.forEach {
            thumbnails.remove(it)
            favicons.remove(it)
        }
        activeTabIdByGroup[gId] = keepTabId
        persist()
    }

    fun renameTabGroup(groupId: String, newName: String) {
        val clean = newName.trim()
        if (clean.isEmpty()) return
        val idx = tabGroups.indexOfFirst { it.id == groupId }
        if (idx >= 0 && !tabGroups[idx].isPrivate && !tabGroups[idx].isDefault) {
            tabGroups[idx] = tabGroups[idx].copy(name = clean)
            persist()
        }
    }

    fun organizeTabsByWebsite(groupId: String = activeGroup.id) {
        sortTabsInGroup(groupId, TabSortMode.WEBSITE)
        showBanner("已按网站整理标签页")
    }

    fun bookmarkAllTabsInGroup(groupId: String = activeGroup.id): Int {
        val existingUrls = bookmarks.map { it.url }.toMutableSet()
        val groupName = tabGroups.firstOrNull { it.id == groupId }?.name ?: "标签页"
        var added = 0
        tabs.filter { it.groupId == groupId && !it.isStartPage && it.url.isNotBlank() }.forEach { tab ->
            if (existingUrls.add(tab.url)) {
                bookmarks.add(
                    0,
                    BookmarkItem(
                        id = "bm_${UUID.randomUUID().toString().take(8)}",
                        title = tab.title.ifBlank { tab.host.ifBlank { tab.url } },
                        url = tab.url,
                        folder = groupName,
                    ),
                )
                added++
            }
        }
        if (added > 0) {
            persist()
            showBanner("已为 $added 个标签页添加书签")
        } else {
            showBanner("当前标签页均已在书签中")
        }
        return added
    }

    /**
     * Switches to adjacent tab in current group (`delta = +1` next, `-1` previous).
     * Swiping past the last tab (`delta > 0` at end) spawns a fresh Start Page tab like iOS Safari.
     */
    fun switchAdjacentTab(delta: Int): BrowserTab {
        val groupTabs = tabsInGroup(activeGroup.id)
        if (groupTabs.isEmpty()) return createTab(groupId = activeGroup.id, url = "", select = true)
        val currentIdx = groupTabs.indexOfFirst { it.id == activeTab.id }.coerceAtLeast(0)
        val targetIdx = currentIdx + delta
        return when {
            targetIdx > groupTabs.lastIndex -> {
                if (activeTab.isStartPage) {
                    activeTab
                } else {
                    createTab(groupId = activeGroup.id, url = "", select = true)
                }
            }
            targetIdx < 0 -> groupTabs.first()
            else -> {
                val target = groupTabs[targetIdx]
                selectTab(target.id)
                target
            }
        }
    }

    fun peekAdjacentTab(delta: Int): BrowserTab? {
        val groupTabs = tabsInGroup(activeGroup.id)
        if (groupTabs.isEmpty()) return null
        val currentIdx = groupTabs.indexOfFirst { it.id == activeTab.id }.coerceAtLeast(0)
        val targetIdx = currentIdx + delta
        return when {
            targetIdx in groupTabs.indices -> groupTabs[targetIdx]
            targetIdx > groupTabs.lastIndex && !activeTab.isStartPage -> BrowserTab(
                id = "preview_new_tab",
                groupId = activeGroup.id,
                url = "",
                title = "起始页",
            )
            else -> null
        }
    }

    fun navigateActiveTab(rawInput: String) {
        val targetUrl = BrowserUrlUtils.normalizeInputToUrl(rawInput, searchEngine)
        if (targetUrl.isBlank()) return
        val trimmed = rawInput.trim()
        if (!activeGroup.isPrivate && trimmed.isNotEmpty() && !trimmed.contains("://")) {
            recentSearches.removeAll { it.equals(trimmed, ignoreCase = true) }
            recentSearches.add(0, trimmed)
            while (recentSearches.size > 12) {
                recentSearches.removeAt(recentSearches.lastIndex)
            }
        }
        val tab = activeTab
        updateTab(tab.id) {
            it.copy(
                url = targetUrl,
                host = BrowserUrlUtils.displayHost(targetUrl),
                title = BrowserUrlUtils.displayHost(targetUrl).ifBlank { targetUrl },
                accentColorArgb = BrowserUrlUtils.accentColorForUrl(targetUrl),
                isLoading = true,
                progress = 0.12f,
                lastActiveAt = System.currentTimeMillis(),
            )
        }
        isAddressEditing = false
        isOverviewOpen = false
        isToolbarMinimized = false
        isToolbarHidden = false
        persist()
    }

    fun navigateToStartPage() {
        val tab = activeTab
        updateTab(tab.id) {
            it.copy(
                url = "",
                title = "起始页",
                host = "",
                themeColorArgb = null,
                isLoading = false,
                progress = 0f,
                canGoBack = false,
                canGoForward = false,
            )
        }
        persist()
    }

    fun updateTab(tabId: String, transform: (BrowserTab) -> BrowserTab) {
        val idx = tabs.indexOfFirst { it.id == tabId }
        if (idx >= 0) {
            tabs[idx] = transform(tabs[idx])
        }
    }

    fun incrementBlockedTracker(tabId: String, host: String = "") {
        updateTab(tabId) { it.copy(blockedTrackers = it.blockedTrackers + 1) }
        totalTrackersBlocked += 1
        val cleanHost = host.trim().lowercase(Locale.ROOT)
        if (cleanHost.isNotEmpty()) {
            blockedDomainCounts[cleanHost] = (blockedDomainCounts[cleanHost] ?: 0) + 1
        }
    }

    fun togglePinTab(tabId: String) {
        updateTab(tabId) { it.copy(isPinned = !it.isPinned) }
        persist()
    }

    fun moveTabToGroup(tabId: String, targetGroupId: String) {
        if (tabGroups.none { it.id == targetGroupId }) return
        val idx = tabs.indexOfFirst { it.id == tabId }
        if (idx < 0) return
        val oldGroupId = tabs[idx].groupId
        if (oldGroupId == targetGroupId) return
        tabs[idx] = tabs[idx].copy(groupId = targetGroupId, isPinned = false)
        activeTabIdByGroup[targetGroupId] = tabId
        ensureGroupActiveTabs()
        persist()
    }

    fun reorderTabsInGroup(groupId: String, fromId: String, toId: String) {
        if (fromId == toId) return
        val fromIdx = tabs.indexOfFirst { it.id == fromId && it.groupId == groupId }
        val toIdx = tabs.indexOfFirst { it.id == toId && it.groupId == groupId }
        if (fromIdx < 0 || toIdx < 0) return
        val item = tabs.removeAt(fromIdx)
        tabs.add(toIdx, item)
        sortMode = TabSortMode.MANUAL
        persist()
    }

    fun sortTabsInGroup(groupId: String, mode: TabSortMode) {
        sortMode = mode
        if (mode == TabSortMode.MANUAL) return
        val groupTabs = tabs.filter { it.groupId == groupId }
        val sorted = when (mode) {
            TabSortMode.TITLE -> groupTabs.sortedWith(
                compareByDescending<BrowserTab> { it.isPinned }.thenBy { it.title.lowercase(Locale.ROOT) },
            )
            TabSortMode.WEBSITE -> groupTabs.sortedWith(
                compareByDescending<BrowserTab> { it.isPinned }
                    .thenBy { it.host.ifBlank { "zzz" } }
                    .thenBy { it.title.lowercase(Locale.ROOT) },
            )
            TabSortMode.MANUAL -> groupTabs
        }
        val indices = tabs.indices.filter { tabs[it].groupId == groupId }
        indices.forEachIndexed { pos, masterIdx ->
            tabs[masterIdx] = sorted[pos]
        }
        persist()
    }

    fun createTabGroup(name: String): TabGroup {
        val clean = name.trim().ifBlank { "标签页组 ${tabGroups.size}" }
        val palette = listOf(0xFF0A84FF, 0xFF30D158, 0xFFFF9F0A, 0xFFBF5AF2, 0xFFFF375F)
        val group = TabGroup(
            id = "grp_${UUID.randomUUID().toString().take(8)}",
            name = clean,
            isPrivate = false,
            isDefault = false,
            accentColorArgb = palette[tabGroups.size % palette.size],
        )
        tabGroups.add(group)
        createTab(groupId = group.id, url = "", select = true)
        persist()
        return group
    }

    fun deleteTabGroup(groupId: String) {
        val target = tabGroups.firstOrNull { it.id == groupId } ?: return
        if (target.isDefault || target.isPrivate) return
        val removedTabIds = tabs.filter { it.groupId == groupId }.map { it.id }
        tabs.removeAll { it.groupId == groupId }
        removedTabIds.forEach {
            thumbnails.remove(it)
            favicons.remove(it)
        }
        tabGroups.removeAll { it.id == groupId }
        activeTabIdByGroup.remove(groupId)
        if (activeGroupId == groupId) {
            activeGroupId = tabGroups.firstOrNull { it.isDefault }?.id
                ?: tabGroups.firstOrNull()?.id
                ?: "main"
        }
        ensureGroupActiveTabs()
        persist()
    }

    fun addFavorite(title: String, url: String) {
        if (url.isBlank()) return
        if (favorites.any { it.url == url }) return
        val host = BrowserUrlUtils.displayHost(url)
        val cleanTitle = title.ifBlank { host }
        val glyph = cleanTitle.trim().take(1).uppercase(Locale.ROOT).ifBlank { "W" }
        favorites.add(
            FavoriteItem(
                id = "fav_${UUID.randomUUID().toString().take(8)}",
                title = cleanTitle,
                url = url,
                shortGlyph = glyph,
                colorArgb = BrowserUrlUtils.accentColorForUrl(url),
            ),
        )
        persist()
    }

    fun removeFavorite(id: String) {
        favorites.removeAll { it.id == id }
        persist()
    }

    fun addBookmark(title: String, url: String, folder: String = "个人收藏") {
        if (url.isBlank()) return
        if (bookmarks.any { it.url == url }) return
        bookmarks.add(
            0,
            BookmarkItem(
                id = "bm_${UUID.randomUUID().toString().take(8)}",
                title = title.ifBlank { BrowserUrlUtils.displayHost(url) },
                url = url,
                folder = folder,
            ),
        )
        persist()
    }

    fun removeBookmark(id: String) {
        bookmarks.removeAll { it.id == id }
        persist()
    }

    fun addReadingList(title: String, url: String, excerpt: String = "") {
        if (url.isBlank()) return
        if (readingList.any { it.url == url }) return
        readingList.add(
            0,
            ReadingListItem(
                id = "rl_${UUID.randomUUID().toString().take(8)}",
                title = title.ifBlank { BrowserUrlUtils.displayHost(url) },
                url = url,
                excerpt = excerpt.ifBlank { BrowserUrlUtils.displayHost(url) },
            ),
        )
        persist()
    }

    fun toggleReadingItemRead(id: String) {
        val idx = readingList.indexOfFirst { it.id == id }
        if (idx >= 0) {
            readingList[idx] = readingList[idx].copy(isRead = !readingList[idx].isRead)
            persist()
        }
    }

    fun removeReadingListItem(id: String) {
        readingList.removeAll { it.id == id }
        persist()
    }

    fun recordHistory(tabId: String, title: String, url: String) {
        if (url.isBlank()) return
        val ownerGroupId = tabs.firstOrNull { it.id == tabId }?.groupId ?: activeGroupId
        val isPrivate = tabGroups.firstOrNull { it.id == ownerGroupId }?.isPrivate == true
        if (isPrivate) return
        val last = history.firstOrNull()
        if (last != null && last.url == url && System.currentTimeMillis() - last.visitedAt < 4_000L) {
            history[0] = last.copy(title = title.ifBlank { last.title })
            return
        }
        history.add(
            0,
            HistoryEntry(
                id = "his_${UUID.randomUUID().toString().take(8)}",
                title = title.ifBlank { BrowserUrlUtils.displayHost(url) },
                url = url,
            ),
        )
        while (history.size > 80) {
            history.removeAt(history.lastIndex)
        }
        persist()
    }

    fun removeHistoryEntry(id: String) {
        history.removeAll { it.id == id }
        persist()
    }

    fun clearHistory() {
        history.clear()
        persist()
    }

    fun clearRecentSearches() {
        recentSearches.clear()
        persist()
    }

    fun clearAllBrowsingData() {
        history.clear()
        recentSearches.clear()
        blockedDomainCounts.clear()
        totalTrackersBlocked = 0
        persist()
        showBanner("已清除浏览记录和网站资料")
    }

    fun togglePageMenuItemEnabled(id: String) {
        val idx = pageMenuItems.indexOfFirst { it.id == id }
        if (idx >= 0) {
            pageMenuItems[idx] = pageMenuItems[idx].copy(enabled = !pageMenuItems[idx].enabled)
            persist()
        }
    }

    fun movePageMenuItem(id: String, delta: Int) {
        val idx = pageMenuItems.indexOfFirst { it.id == id }
        val target = idx + delta
        if (idx < 0 || target !in pageMenuItems.indices) return
        val item = pageMenuItems.removeAt(idx)
        pageMenuItems.add(target, item)
        pageMenuItems.forEachIndexed { orderIdx, cfg ->
            pageMenuItems[orderIdx] = cfg.copy(order = orderIdx)
        }
        persist()
    }

    fun showBanner(message: String) {
        statusBannerMessage = message
    }

    private fun ensureGroupActiveTabs() {
        for (group in tabGroups) {
            val current = activeTabIdByGroup[group.id]
            val valid = tabs.firstOrNull { it.id == current && it.groupId == group.id }
            if (valid == null) {
                val firstInGroup = tabs.firstOrNull { it.groupId == group.id }
                if (firstInGroup != null) {
                    activeTabIdByGroup[group.id] = firstInGroup.id
                } else {
                    val created = BrowserTab(
                        id = "tab_${UUID.randomUUID().toString().take(8)}",
                        groupId = group.id,
                        url = "",
                        title = if (group.isPrivate) "无痕浏览" else "起始页",
                    )
                    tabs.add(created)
                    activeTabIdByGroup[group.id] = created.id
                }
            }
        }
    }

    private fun seedDefaults() {
        tabGroups.clear()
        tabGroups.addAll(
            listOf(
                TabGroup(id = "private", name = "无痕浏览", isPrivate = true, isDefault = false, accentColorArgb = 0xFF636366),
                TabGroup(id = "main", name = "标签页", isPrivate = false, isDefault = true, accentColorArgb = 0xFF0A84FF),
                TabGroup(id = "dev", name = "dev", isPrivate = false, isDefault = false, accentColorArgb = 0xFF30D158),
                TabGroup(id = "ai", name = "AI Tools", isPrivate = false, isDefault = false, accentColorArgb = 0xFFBF5AF2),
            ),
        )

        tabs.clear()
        tabs.addAll(
            listOf(
                BrowserTab(
                    id = "tab_private_1",
                    groupId = "private",
                    url = "",
                    title = "无痕起始页",
                    host = "",
                    accentColorArgb = 0xFF636366,
                ),
                BrowserTab(
                    id = "tab_main_1",
                    groupId = "main",
                    url = "https://www.apple.com",
                    title = "Apple",
                    host = "apple.com",
                    accentColorArgb = 0xFF1C1C1E,
                ),
                BrowserTab(
                    id = "tab_main_2",
                    groupId = "main",
                    url = "https://www.huawei.com",
                    title = "管理层信息 - 关于华为",
                    host = "huawei.com",
                    accentColorArgb = 0xFFC7000B,
                ),
                BrowserTab(
                    id = "tab_main_3",
                    groupId = "main",
                    url = "https://www.wikipedia.org",
                    title = "Wikipedia",
                    host = "wikipedia.org",
                    accentColorArgb = 0xFF3366CC,
                ),
                BrowserTab(
                    id = "tab_main_4",
                    groupId = "main",
                    url = "https://www.bing.com",
                    title = "必应",
                    host = "bing.com",
                    accentColorArgb = 0xFF00809D,
                ),
                BrowserTab(
                    id = "tab_main_start",
                    groupId = "main",
                    url = "",
                    title = "起始页",
                    host = "",
                    accentColorArgb = 0xFF0A84FF,
                ),
                BrowserTab(
                    id = "tab_dev_1",
                    groupId = "dev",
                    url = "https://github.com",
                    title = "GitHub",
                    host = "github.com",
                    accentColorArgb = 0xFF24292F,
                ),
                BrowserTab(
                    id = "tab_dev_2",
                    groupId = "dev",
                    url = "https://developer.android.com",
                    title = "Android Developers",
                    host = "developer.android.com",
                    accentColorArgb = 0xFF3DDC84,
                ),
                BrowserTab(
                    id = "tab_ai_1",
                    groupId = "ai",
                    url = "https://platform.deepseek.com",
                    title = "DeepSeek 开放平台",
                    host = "platform.deepseek.com",
                    accentColorArgb = 0xFF4D6BFE,
                ),
            ),
        )
        activeGroupId = "main"
        activeTabIdByGroup["main"] = "tab_main_start"
        activeTabIdByGroup["private"] = "tab_private_1"
        activeTabIdByGroup["dev"] = "tab_dev_1"
        activeTabIdByGroup["ai"] = "tab_ai_1"

        favorites.clear()
        favorites.addAll(
            listOf(
                FavoriteItem("fav_apple", "Apple", "https://www.apple.com", "A", 0xFF1C1C1E),
                FavoriteItem("fav_icloud", "iCloud", "https://www.icloud.com", "☁", 0xFF2E9AFE),
                FavoriteItem("fav_baidu", "百度", "https://www.baidu.com", "度", 0xFF2932E1),
                FavoriteItem("fav_bing", "Bing", "https://www.bing.com", "b", 0xFF00809D),
                FavoriteItem("fav_wiki", "Wikipedia", "https://www.wikipedia.org", "W", 0xFF5856D6),
                FavoriteItem("fav_github", "GitHub", "https://github.com", "GH", 0xFF24292F),
                FavoriteItem("fav_android", "Android", "https://developer.android.com", "AD", 0xFF34C759),
                FavoriteItem("fav_huawei", "华为", "https://www.huawei.com", "华", 0xFFC7000B),
                FavoriteItem("fav_deepseek", "DeepSeek", "https://platform.deepseek.com", "DS", 0xFF4D6BFE),
                FavoriteItem("fav_kotlin", "Kotlin", "https://kotlinlang.org", "K", 0xFF7F52FF),
            ),
        )

        bookmarks.clear()
        bookmarks.addAll(
            listOf(
                BookmarkItem("bm_1", "Apple (中国大陆)", "https://www.apple.com.cn", "个人收藏"),
                BookmarkItem("bm_2", "GitHub — LiquidKit", "https://github.com/egysoloman/LiquidKit", "开发"),
                BookmarkItem("bm_3", "Android Developers", "https://developer.android.com", "开发"),
                BookmarkItem("bm_4", "Wikipedia", "https://www.wikipedia.org", "个人收藏"),
            ),
        )

        readingList.clear()
        readingList.addAll(
            listOf(
                ReadingListItem(
                    id = "rl_1",
                    title = "Apple Liquid Glass Design Language",
                    url = "https://developer.apple.com/design/",
                    excerpt = "光学折射材质、连续圆角与层级浮层交互规范。",
                ),
                ReadingListItem(
                    id = "rl_2",
                    title = "Jetpack Compose Shared Elements & Physics",
                    url = "https://developer.android.com/develop/ui/compose/animation/shared-elements",
                    excerpt = "构建 120Hz 共享元素缩放与弹性手势过渡。",
                ),
            ),
        )

        history.clear()
        history.addAll(
            listOf(
                HistoryEntry("his_1", "管理层信息 - 关于华为", "https://www.huawei.com"),
                HistoryEntry("his_2", "DeepSeek 开放平台", "https://platform.deepseek.com"),
                HistoryEntry("his_3", "GitHub", "https://github.com"),
            ),
        )

        recentSearches.clear()
        recentSearches.addAll(
            listOf("LiquidKit", "Android Compose", "huawei.com", "DeepSeek API"),
        )

        blockedDomainCounts.clear()
        blockedDomainCounts["doubleclick.net"] = 14
        blockedDomainCounts["google-analytics.com"] = 11
        blockedDomainCounts["hm.baidu.com"] = 7
        totalTrackersBlocked = 32

        pageMenuItems.clear()
        pageMenuItems.addAll(
            listOf(
                PageMenuItemConfig("find", "在页面上查找", true, 0),
                PageMenuItemConfig("zoom", "页面缩放", true, 1),
                PageMenuItemConfig("blocker", "内容拦截器", true, 2),
                PageMenuItemConfig("hide_toolbar", "隐藏工具栏", true, 3),
                PageMenuItemConfig("desktop", "请求桌面网站", true, 4),
                PageMenuItemConfig("tab_ops", "标签页操作", true, 5),
                PageMenuItemConfig("privacy", "隐私与安全", true, 6),
            ),
        )
    }

    fun persist() {
        prefs ?: return
        val h = persistHandler
        if (h != null) {
            h.removeCallbacks(pendingPersistRunnable)
            h.postDelayed(pendingPersistRunnable, 150L)
        } else {
            serializeAndApplyAsync()
        }
    }

    fun persistNow() {
        val targetPrefs = prefs ?: return
        persistHandler?.removeCallbacks(pendingPersistRunnable)
        runCatching {
            val json = buildStateJsonObject().toString()
            targetPrefs.edit().putString(PREF_KEY, json).apply()
        }
    }

    private fun restore(): Boolean {
        val raw = prefs?.getString(PREF_KEY, null) ?: return false
        return runCatching {
            seedDefaults()
            val root = JSONObject(raw)
            activeGroupId = root.optString("activeGroupId", activeGroupId)
            searchEngine = SearchEngine.entries.firstOrNull { it.name == root.optString("searchEngine") } ?: searchEngine
            glassOpacity = root.optDouble("glassOpacity", glassOpacity.toDouble()).toFloat().coerceIn(0.1f, 1f)
            darkModePreference = DarkModePreference.entries.firstOrNull { it.name == root.optString("darkModePreference") } ?: darkModePreference
            totalTrackersBlocked = root.optInt("totalTrackersBlocked", totalTrackersBlocked)
            tabBarMode = TabBarMode.entries.firstOrNull { it.name == root.optString("tabBarMode") } ?: tabBarMode
            extendWebsiteTint = root.optBoolean("extendWebsiteTint", extendWebsiteTint)
            showRecentSearches = root.optBoolean("showRecentSearches", showRecentSearches)
            blockPopups = root.optBoolean("blockPopups", blockPopups)
            preventCrossSiteTracking = root.optBoolean("preventCrossSiteTracking", preventCrossSiteTracking)
            warnInsecureConnection = root.optBoolean("warnInsecureConnection", warnInsecureConnection)
            openLinkMode = OpenLinkMode.entries.firstOrNull { it.name == root.optString("openLinkMode") } ?: openLinkMode
            autoCloseTabsMode = AutoCloseTabsMode.entries.firstOrNull { it.name == root.optString("autoCloseTabsMode") } ?: autoCloseTabsMode
            showTabletTabBar = root.optBoolean("showTabletTabBar", true)

            root.optJSONObject("blockedDomainCounts")?.let { mapObj ->
                blockedDomainCounts.clear()
                mapObj.keys().forEach { k ->
                    blockedDomainCounts[k] = mapObj.optInt(k, 1)
                }
            }

            root.optJSONArray("tabGroups")?.let { arr ->
                if (arr.length() > 0) {
                    tabGroups.clear()
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        tabGroups.add(
                            TabGroup(
                                id = o.getString("id"),
                                name = o.getString("name"),
                                isPrivate = o.optBoolean("isPrivate", false),
                                isDefault = o.optBoolean("isDefault", false),
                                accentColorArgb = o.optLong("accentColorArgb", 0xFF0A84FF),
                            ),
                        )
                    }
                }
            }

            root.optJSONArray("tabs")?.let { arr ->
                if (arr.length() > 0) {
                    tabs.clear()
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        tabs.add(
                            BrowserTab(
                                id = o.getString("id"),
                                groupId = o.getString("groupId"),
                                url = o.optString("url", ""),
                                title = o.optString("title", "起始页"),
                                host = o.optString("host", ""),
                                accentColorArgb = o.optLong("accentColorArgb", 0xFF0A84FF),
                                isPinned = o.optBoolean("isPinned", false),
                                requestDesktopSite = o.optBoolean("requestDesktopSite", false),
                                adaptiveDarkMode = SiteDarkMode.entries.firstOrNull { it.name == o.optString("adaptiveDarkMode") } ?: SiteDarkMode.AUTO,
                                contentBlockerEnabled = o.optBoolean("contentBlockerEnabled", true),
                                pageZoom = o.optInt("pageZoom", 100),
                                blockedTrackers = o.optInt("blockedTrackers", 0),
                            ),
                        )
                    }
                }
            }

            root.optJSONObject("activeTabIdByGroup")?.let { mapObj ->
                activeTabIdByGroup.clear()
                mapObj.keys().forEach { k ->
                    activeTabIdByGroup[k] = mapObj.getString(k)
                }
            }

            root.optJSONArray("favorites")?.let { arr ->
                if (arr.length() > 0) {
                    favorites.clear()
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        favorites.add(
                            FavoriteItem(
                                id = o.getString("id"),
                                title = o.getString("title"),
                                url = o.getString("url"),
                                shortGlyph = o.optString("shortGlyph", "W"),
                                colorArgb = o.optLong("colorArgb", 0xFF0A84FF),
                            ),
                        )
                    }
                }
            }

            root.optJSONArray("bookmarks")?.let { arr ->
                bookmarks.clear()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    bookmarks.add(
                        BookmarkItem(
                            id = o.getString("id"),
                            title = o.getString("title"),
                            url = o.getString("url"),
                            folder = o.optString("folder", "个人收藏"),
                        ),
                    )
                }
            }

            root.optJSONArray("readingList")?.let { arr ->
                readingList.clear()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    readingList.add(
                        ReadingListItem(
                            id = o.getString("id"),
                            title = o.getString("title"),
                            url = o.getString("url"),
                            excerpt = o.optString("excerpt", ""),
                            isRead = o.optBoolean("isRead", false),
                        ),
                    )
                }
            }

            root.optJSONArray("history")?.let { arr ->
                history.clear()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    history.add(
                        HistoryEntry(
                            id = o.getString("id"),
                            title = o.getString("title"),
                            url = o.getString("url"),
                            visitedAt = o.optLong("visitedAt", System.currentTimeMillis()),
                        ),
                    )
                }
            }

            root.optJSONArray("recentSearches")?.let { arr ->
                recentSearches.clear()
                for (i in 0 until arr.length()) {
                    recentSearches.add(arr.getString(i))
                }
            }

            root.optJSONArray("pageMenuItems")?.let { arr ->
                if (arr.length() > 0) {
                    val defaultsById = pageMenuItems.associateBy { it.id }
                    val restored = mutableListOf<PageMenuItemConfig>()
                    val seen = HashSet<String>()
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        val id = o.optString("id", "").takeIf { it.isNotBlank() } ?: continue
                        val base = defaultsById[id] ?: continue
                        if (seen.add(id)) {
                            restored.add(
                                base.copy(
                                    enabled = o.optBoolean("enabled", base.enabled),
                                    order = o.optInt("order", restored.size),
                                ),
                            )
                        }
                    }
                    defaultsById.values.forEach { def ->
                        if (seen.add(def.id)) {
                            restored.add(def.copy(order = restored.size))
                        }
                    }
                    if (restored.isNotEmpty()) {
                        pageMenuItems.clear()
                        restored.sortedBy { it.order }.forEachIndexed { idx, cfg ->
                            pageMenuItems.add(cfg.copy(order = idx))
                        }
                    }
                }
            }
            true
        }.getOrDefault(false)
    }

    companion object {
        private const val PREF_KEY = "terra_browser_state_v1"
    }
}
