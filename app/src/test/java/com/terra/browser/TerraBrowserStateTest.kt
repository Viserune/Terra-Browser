package com.terra.browser

import com.terra.browser.model.BrowserLayoutClassifier
import com.terra.browser.model.BrowserLayoutProfile
import com.terra.browser.model.BrowserSafeAreaCalculator
import com.terra.browser.model.BrowserScrollCollapseTracker
import com.terra.browser.model.BrowserTopVeilCalculator
import com.terra.browser.model.BrowserUrlUtils
import com.terra.browser.model.SearchEngine
import com.terra.browser.model.TabBarMode
import com.terra.browser.model.TabSortMode
import com.terra.browser.state.TerraBrowserState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TerraBrowserStateTest {

    @Test
    fun urlNormalizationAndHostDisplayWorkCorrectly() {
        assertEquals("https://huawei.com", BrowserUrlUtils.normalizeInputToUrl("huawei.com"))
        assertEquals("http://localhost:8080/test", BrowserUrlUtils.normalizeInputToUrl("localhost:8080/test"))
        assertEquals("https://example.org/path", BrowserUrlUtils.normalizeInputToUrl("https://example.org/path"))
        assertEquals(
            "https://www.baidu.com/s?wd=liquid+glass",
            BrowserUrlUtils.normalizeInputToUrl("liquid glass", SearchEngine.BAIDU),
        )

        assertEquals("huawei.com", BrowserUrlUtils.displayHost("https://www.huawei.com/cn/about"))
        assertEquals("github.com", BrowserUrlUtils.displayHost("https://github.com/egysoloman/LiquidKit"))
        assertTrue(BrowserUrlUtils.isTrackerHost("metrics.google-analytics.com"))
        assertTrue(BrowserUrlUtils.isTrackerHost("doubleclick.net"))
        assertFalse(BrowserUrlUtils.isTrackerHost("apple.com"))
    }

    @Test
    fun closingLastTabCreatesFreshStartPageAndAdjacentSwipeSpawnsNewTab() {
        val state = TerraBrowserState(prefs = null)
        state.selectGroup("dev")
        val initialDevTabs = state.tabsInGroup("dev")
        assertTrue(initialDevTabs.isNotEmpty())

        // Close all dev tabs one by one
        initialDevTabs.forEach { state.closeTab(it.id) }
        val remaining = state.tabsInGroup("dev")
        assertEquals(1, remaining.size)
        assertTrue(remaining.first().isStartPage)

        // Navigate it to a real page, then swipe past last tab (+1) -> should spawn a new Start Page
        state.navigateActiveTab("github.com")
        assertFalse(state.activeTab.isStartPage)
        val spawned = state.switchAdjacentTab(+1)
        assertTrue(spawned.isStartPage)
        assertEquals(2, state.tabsInGroup("dev").size)
    }

    @Test
    fun privateGroupDoesNotRecordHistoryOrRecentSearches() {
        val state = TerraBrowserState(prefs = null)
        val initialHistoryCount = state.history.size
        val initialSearchCount = state.recentSearches.size

        state.selectGroup("private")
        state.navigateActiveTab("secret query")
        state.recordHistory(state.activeTab.id, "Secret Page", "https://example.com/secret")

        assertEquals(initialSearchCount, state.recentSearches.size)
        assertEquals(initialHistoryCount, state.history.size)
    }

    @Test
    fun sortingReorderingAndFilteringWorkPreservingPinnedTabs() {
        val state = TerraBrowserState(prefs = null)
        state.selectGroup("main")
        val groupTabs = state.tabsInGroup("main")
        val pinTarget = groupTabs.first { !it.isStartPage }
        state.togglePinTab(pinTarget.id)

        state.sortTabsInGroup("main", TabSortMode.TITLE)
        val afterSort = state.tabsInGroup("main")
        assertTrue(afterSort.first().isPinned)
        assertEquals(pinTarget.id, afterSort.first().id)

        val regulars = state.regularTabsInGroup("main")
        assertTrue(regulars.size >= 2)
        val firstId = regulars[0].id
        val secondId = regulars[1].id
        state.reorderTabsInGroup("main", firstId, secondId)
        val reordered = state.regularTabsInGroup("main")
        assertEquals(secondId, reordered[0].id)
        assertEquals(firstId, reordered[1].id)

        state.overviewSearchQuery = "huawei"
        val filtered = state.tabsInGroup("main", includeSearchFilter = true)
        assertTrue(filtered.any { it.host.contains("huawei") })
        assertTrue(filtered.size < afterSort.size)
    }

    @Test
    fun tabGroupLifecycleMoveAndMenuCustomizationWork() {
        val state = TerraBrowserState(prefs = null)
        val created = state.createTabGroup("阅读")
        assertTrue(state.tabGroups.any { it.id == created.id })

        val mainTab = state.tabsInGroup("main").first()
        state.moveTabToGroup(mainTab.id, created.id)
        assertTrue(state.tabsInGroup(created.id).any { it.id == mainTab.id })

        val beforeOrder = state.pageMenuItems.map { it.id }
        state.movePageMenuItem(beforeOrder[0], +1)
        assertNotEquals(beforeOrder[0], state.pageMenuItems[0].id)
        assertEquals(beforeOrder[0], state.pageMenuItems[1].id)

        state.deleteTabGroup(created.id)
        assertFalse(state.tabGroups.any { it.id == created.id })
    }

    @Test
    fun tabBarModesFavoritesAndClearBrowsingDataWork() {
        val state = TerraBrowserState(prefs = null)
        assertEquals(TabBarMode.BOTTOM, state.tabBarMode)
        state.tabBarMode = TabBarMode.COMPACT
        assertEquals(TabBarMode.COMPACT, state.tabBarMode)
        state.tabBarMode = TabBarMode.TOP
        assertEquals(TabBarMode.TOP, state.tabBarMode)

        val favCountBefore = state.favorites.size
        state.addFavorite("Example", "https://example.com")
        assertEquals(favCountBefore + 1, state.favorites.size)
        val addedId = state.favorites.last().id
        state.removeFavorite(addedId)
        assertEquals(favCountBefore, state.favorites.size)

        state.clearAllBrowsingData()
        assertEquals(0, state.history.size)
        assertEquals(0, state.recentSearches.size)
        assertEquals(0, state.totalTrackersBlocked)

        val custom = state.createTabGroup("工作")
        state.renameTabGroup(custom.id, "工作区")
        assertEquals("工作区", state.tabGroups.first { it.id == custom.id }.name)
        val t1 = state.createTab(groupId = custom.id, url = "github.com")
        val t2 = state.createTab(groupId = custom.id, url = "apple.com")
        state.organizeTabsByWebsite(custom.id)
        assertEquals(2, state.bookmarkAllTabsInGroup(custom.id))
        state.closeOtherTabsInGroup(t2.id)
        val left = state.tabsInGroup(custom.id)
        assertEquals(1, left.size)
        assertEquals(t2.id, left.first().id)
        assertFalse(left.any { it.id == t1.id })
    }

    @Test
    fun cssColorParsingAndBlankFrameDetectionWork() {
        assertEquals(0xFF0D1117L, BrowserUrlUtils.parseCssColorArgb("\"#0d1117\""))
        assertEquals(0xFFFFFFFFL, BrowserUrlUtils.parseCssColorArgb("#fff"))
        assertEquals(0xFF123456L, BrowserUrlUtils.parseCssColorArgb("rgb(18, 52, 86)"))
        assertEquals(0xFF204060L, BrowserUrlUtils.parseCssColorArgb("rgba(32, 64, 96, 0.9)"))
        assertNull(BrowserUrlUtils.parseCssColorArgb("rgba(255, 255, 255, 0)"))
        assertNull(BrowserUrlUtils.parseCssColorArgb("transparent"))
        assertNull(BrowserUrlUtils.parseCssColorArgb(""))

        val blankSamples = IntArray(64) { 0xFFFFFFFF.toInt() }
        assertTrue(BrowserUrlUtils.isUniformBlankSample(blankSamples))

        val richPageSamples = IntArray(64) { idx ->
            if (idx % 4 == 0) 0xFF141519.toInt() else 0xFF2299DD.toInt()
        }
        assertFalse(BrowserUrlUtils.isUniformBlankSample(richPageSamples))
    }

    @Test
    fun navigateToStartPageResetsNavigationFlags() {
        val state = TerraBrowserState()
        state.navigateActiveTab("https://www.bing.com")
        state.updateTab(state.activeTab.id) { it.copy(canGoBack = true, canGoForward = true) }
        state.navigateToStartPage()
        assertTrue(state.activeTab.isStartPage)
        assertFalse(state.activeTab.canGoBack)
        assertFalse(state.activeTab.canGoForward)
    }

    @Test
    fun layoutClassifierDistinguishesPortraitFoldedLandscapeAndTabletLandscape() {
        assertEquals(BrowserLayoutProfile.PHONE_PORTRAIT, BrowserLayoutClassifier.classify(411f, 914f))
        assertEquals(BrowserLayoutProfile.PHONE_LANDSCAPE, BrowserLayoutClassifier.classify(914f, 411f))
        assertEquals(BrowserLayoutProfile.PHONE_LANDSCAPE, BrowserLayoutClassifier.classify(678f, 466f))
        assertEquals(BrowserLayoutProfile.TABLET_LANDSCAPE, BrowserLayoutClassifier.classify(841f, 701f))
        assertEquals(BrowserLayoutProfile.TABLET_LANDSCAPE, BrowserLayoutClassifier.classify(1194f, 834f))
    }

    @Test
    fun tabletSidebarAndTabBarStateDefaultsAndToggles() {
        val state = TerraBrowserState()
        assertFalse(state.isTabletSidebarOpen)
        assertTrue(state.showTabletTabBar)
        state.isTabletSidebarOpen = true
        state.showTabletTabBar = false
        assertTrue(state.isTabletSidebarOpen)
        assertFalse(state.showTabletTabBar)
    }

    @Test
    fun bottomSafeAreaAndScrollCollapseTrackerBehaveLikeSafari() {
        // Bottom dock clearance respects expanded, minimized, hidden, and adaptive layouts
        assertEquals(
            162f,
            BrowserSafeAreaCalculator.bottomSafeInsetDp(
                layoutProfile = BrowserLayoutProfile.PHONE_PORTRAIT,
                tabBarMode = TabBarMode.BOTTOM,
                isToolbarHidden = false,
                isToolbarMinimized = false,
                navigationBarBottomDp = 24f,
            ),
            0.01f,
        )
        assertEquals(
            80f,
            BrowserSafeAreaCalculator.bottomSafeInsetDp(
                layoutProfile = BrowserLayoutProfile.PHONE_PORTRAIT,
                tabBarMode = TabBarMode.BOTTOM,
                isToolbarHidden = false,
                isToolbarMinimized = true,
                navigationBarBottomDp = 24f,
            ),
            0.01f,
        )
        assertEquals(
            90f,
            BrowserSafeAreaCalculator.bottomSafeInsetDp(
                layoutProfile = BrowserLayoutProfile.PHONE_LANDSCAPE,
                tabBarMode = TabBarMode.BOTTOM,
                isToolbarHidden = false,
                isToolbarMinimized = false,
                navigationBarBottomDp = 24f,
            ),
            0.01f,
        )
        assertEquals(
            24f,
            BrowserSafeAreaCalculator.bottomSafeInsetDp(
                layoutProfile = BrowserLayoutProfile.TABLET_LANDSCAPE,
                tabBarMode = TabBarMode.BOTTOM,
                isToolbarHidden = false,
                isToolbarMinimized = false,
                navigationBarBottomDp = 24f,
            ),
            0.01f,
        )

        // Slow, steady downward scroll (10px per frame) accumulates cleanly and collapses once threshold is crossed
        val tracker = BrowserScrollCollapseTracker()
        val density = 3f
        var minimized = false
        for (step in 1..12) {
            minimized = tracker.onScroll(dyPx = 10, scrollYPx = 200 + step * 10, density = density, currentlyMinimized = minimized)
        }
        assertTrue(minimized)

        // Small jitter downward doesn't immediately reverse; deliberate upward scroll expands
        for (step in 1..8) {
            minimized = tracker.onScroll(dyPx = -10, scrollYPx = 320 - step * 10, density = density, currentlyMinimized = minimized)
        }
        assertFalse(minimized)

        // Scrolling back near document top always expands
        minimized = true
        assertFalse(tracker.onScroll(dyPx = 12, scrollYPx = 12, density = density, currentlyMinimized = minimized))
    }

    @Test
    fun scrollCollapseTrackerToleratesSmallOppositeJitterAndHandlesRapidFlickReversal() {
        val tracker = BrowserScrollCollapseTracker()
        val density = 3f
        var minimized = false

        // Accumulate 70px (~23dp) down, then inject 3px (<3dp*3=9px) upward jitter, then finish down scroll
        for (i in 1..7) {
            minimized = tracker.onScroll(dyPx = 10, scrollYPx = 220 + i * 10, density = density, currentlyMinimized = minimized)
        }
        assertFalse(minimized)
        // 3px jitter should not wipe accumulated downward progress
        minimized = tracker.onScroll(dyPx = -3, scrollYPx = 290, density = density, currentlyMinimized = minimized)
        assertFalse(minimized)
        // Next 40px down crosses 34dp (102px) threshold
        for (i in 1..4) {
            minimized = tracker.onScroll(dyPx = 10, scrollYPx = 290 + i * 10, density = density, currentlyMinimized = minimized)
        }
        assertTrue(minimized)

        // Rapid up flick should expand cleanly even after brief opposite jitter
        minimized = tracker.onScroll(dyPx = 2, scrollYPx = 330, density = density, currentlyMinimized = minimized)
        assertTrue(minimized)
        minimized = tracker.onScroll(dyPx = -80, scrollYPx = 250, density = density, currentlyMinimized = minimized)
        assertFalse(minimized)

        // Immediate rapid down flick right after expansion re-collapses once threshold is crossed
        minimized = tracker.onScroll(dyPx = 120, scrollYPx = 370, density = density, currentlyMinimized = minimized)
        assertTrue(minimized)
    }

    @Test
    fun navigateToStartPageClearsThemeColor() {
        val state = TerraBrowserState(prefs = null)
        state.navigateActiveTab("https://apple.com")
        state.updateTab(state.activeTab.id) { it.copy(themeColorArgb = 0xFF123456L) }
        assertEquals(0xFF123456L, state.activeTab.themeColorArgb)

        state.navigateToStartPage()
        assertTrue(state.activeTab.isStartPage)
        assertNull(state.activeTab.themeColorArgb)
    }

    @Test
    fun emptyNavigationStackAndSafeRangeClampingNeverThrow() {
        val state = TerraBrowserState(prefs = null)
        // Fresh StartPage has empty back/forward stacks
        assertTrue(state.activeTab.isStartPage)
        assertFalse(state.activeTab.canGoBack)
        assertFalse(state.activeTab.canGoForward)

        // Closing the only tab in private group always provisions a safe fallback StartPage
        state.selectGroup("private")
        val privateTabs = state.tabsInGroup("private")
        privateTabs.forEach { state.closeTab(it.id) }
        assertEquals(1, state.tabsInGroup("private").size)
        assertTrue(state.activeTab.isStartPage)

        // Safe bottom inset clamping under zero/negative navigation bar insets
        val inset = BrowserSafeAreaCalculator.bottomSafeInsetDp(
            layoutProfile = BrowserLayoutProfile.PHONE_PORTRAIT,
            tabBarMode = TabBarMode.COMPACT,
            isToolbarHidden = false,
            isToolbarMinimized = false,
            navigationBarBottomDp = -12f,
        )
        assertTrue(inset >= 0f)
        assertEquals(82f, inset, 0.01f)

        // Top veil is strictly 0f when empty top safe area is at rest, and fades in on scroll
        assertEquals(
            0f,
            BrowserTopVeilCalculator.intrusionFraction(
                isStartPage = false,
                startPageScrollFraction = 0f,
                topSafeOffsetPx = 96f,
                topSafeInsetPx = 96f,
            ),
            0.001f,
        )
        assertEquals(
            0.5f,
            BrowserTopVeilCalculator.intrusionFraction(
                isStartPage = false,
                startPageScrollFraction = 0f,
                topSafeOffsetPx = 48f,
                topSafeInsetPx = 96f,
            ),
            0.001f,
        )
        assertEquals(
            0f,
            BrowserTopVeilCalculator.intrusionFraction(
                isStartPage = true,
                startPageScrollFraction = 0f,
                topSafeOffsetPx = 0f,
                topSafeInsetPx = 96f,
            ),
            0.001f,
        )
        assertEquals(
            0.8f,
            BrowserTopVeilCalculator.intrusionFraction(
                isStartPage = true,
                startPageScrollFraction = 0.8f,
                topSafeOffsetPx = 0f,
                topSafeInsetPx = 96f,
            ),
            0.001f,
        )
    }
}
