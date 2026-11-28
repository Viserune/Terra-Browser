package com.terra.browser.model

import androidx.compose.runtime.Immutable
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale

enum class SiteDarkMode(val label: String) {
    AUTO("自动"),
    ON("打开"),
    OFF("关闭"),
}

enum class DarkModePreference(val label: String) {
    SYSTEM("跟随系统"),
    LIGHT("浅色"),
    DARK("深色"),
}

enum class TabBarMode(val label: String) {
    COMPACT("精简"),
    BOTTOM("下方"),
    TOP("上方"),
}

enum class OpenLinkMode(val label: String) {
    NEW_TAB("新标签页"),
    BACKGROUND("在后台"),
}

enum class AutoCloseTabsMode(val label: String) {
    MANUAL("手动"),
    ONE_DAY("一天后"),
    ONE_WEEK("一周后"),
    ONE_MONTH("一个月后"),
}

enum class TabSortMode(val label: String) {
    MANUAL("手动排序"),
    TITLE("按标题排列"),
    WEBSITE("按网站排列"),
}

@Immutable
data class SearchEngine(
    val id: String,
    val label: String,
    val queryTemplate: String,
    val isCustom: Boolean = false,
) {
    companion object {
        val BAIDU = SearchEngine("baidu", "百度", "https://www.baidu.com/s?wd=%s")
        val DOUYIN = SearchEngine("douyin", "抖音", "https://so.douyin.com/s?keyword=%s")
        val SOGOU = SearchEngine("sogou", "搜狗", "https://www.sogou.com/web?query=%s")
        val SO360 = SearchEngine("so360", "360 搜索", "https://www.so.com/s?q=%s")
        val BING = SearchEngine("bing", "必应", "https://www.bing.com/search?q=%s")
        val GOOGLE = SearchEngine("google", "Google", "https://www.google.com/search?q=%s")
        val YAHOO = SearchEngine("yahoo", "Yahoo", "https://search.yahoo.com/search?p=%s")
        val DUCKDUCKGO = SearchEngine("duckduckgo", "DuckDuckGo", "https://duckduckgo.com/?q=%s")
        val ECOSIA = SearchEngine("ecosia", "Ecosia", "https://www.ecosia.org/search?q=%s")
        val YANDEX = SearchEngine("yandex", "Yandex", "https://yandex.com/search/?text=%s")
        val KAGI = SearchEngine("kagi", "Kagi", "https://kagi.com/search?q=%s")
        val PERPLEXITY = SearchEngine("perplexity", "Perplexity", "https://www.perplexity.ai/search?q=%s")

        val builtIn: List<SearchEngine> = listOf(
            BAIDU, DOUYIN, SOGOU, SO360, BING, GOOGLE, YAHOO, DUCKDUCKGO, ECOSIA, YANDEX, KAGI, PERPLEXITY,
        )

        fun findById(id: String?, customEngines: List<SearchEngine> = emptyList()): SearchEngine? {
            val key = id?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            return (builtIn + customEngines).firstOrNull { it.id.equals(key, ignoreCase = true) }
        }
    }
}

enum class BrowserLayoutProfile {
    PHONE_PORTRAIT,
    PHONE_LANDSCAPE,
    TABLET_LANDSCAPE,
}

object BrowserLayoutClassifier {
    fun classify(widthDp: Float, heightDp: Float): BrowserLayoutProfile = when {
        widthDp >= 600f && heightDp >= 520f -> BrowserLayoutProfile.TABLET_LANDSCAPE
        widthDp > heightDp && heightDp < 520f -> BrowserLayoutProfile.PHONE_LANDSCAPE
        else -> BrowserLayoutProfile.PHONE_PORTRAIT
    }
}

object BrowserSafeAreaCalculator {
    /**
     * Computes bottom clearance (in dp) so webpages, scroll endpoints, and fixed bottom
     * modals stay unobstructed above the active floating browser chrome.
     */
    fun bottomSafeInsetDp(
        layoutProfile: BrowserLayoutProfile,
        tabBarMode: TabBarMode,
        isToolbarHidden: Boolean,
        isToolbarMinimized: Boolean,
        navigationBarBottomDp: Float,
    ): Float {
        val nav = navigationBarBottomDp.coerceAtLeast(0f)
        if (layoutProfile == BrowserLayoutProfile.TABLET_LANDSCAPE) return nav
        if (isToolbarHidden) return nav + 46f
        return when (layoutProfile) {
            BrowserLayoutProfile.PHONE_LANDSCAPE ->
                nav + if (isToolbarMinimized) 46f else 66f
            BrowserLayoutProfile.PHONE_PORTRAIT -> when (tabBarMode) {
                TabBarMode.BOTTOM -> nav + if (isToolbarMinimized) 56f else 138f
                TabBarMode.COMPACT -> nav + if (isToolbarMinimized) 54f else 82f
                TabBarMode.TOP -> nav + if (isToolbarMinimized) 14f else 82f
            }
            BrowserLayoutProfile.TABLET_LANDSCAPE -> nav
        }
    }
}

object BrowserTopVeilCalculator {
    /**
     * Computes top progressive-blur intrusion fraction (`0f` when empty top safe area is at rest,
     * ramping smoothly toward `1f` only after page content scrolls under the status bar).
     */
    fun intrusionFraction(
        isStartPage: Boolean,
        startPageScrollFraction: Float,
        topSafeOffsetPx: Float,
        topSafeInsetPx: Float,
    ): Float {
        if (isStartPage) {
            return startPageScrollFraction.coerceIn(0f, 1f)
        }
        if (topSafeInsetPx <= 1f) return 0f
        if (topSafeOffsetPx >= topSafeInsetPx - 1f) return 0f
        return ((topSafeInsetPx - topSafeOffsetPx) / topSafeInsetPx).coerceIn(0f, 1f)
    }
}

/**
 * Tracks cumulative vertical scroll delta rather than single-event `dy` spikes so slow,
 * steady downward scrolling collapses the browser chrome smoothly just like Safari.
 */
class BrowserScrollCollapseTracker {
    private var accumulatedDyPx: Float = 0f
    private var oppositeJitterPx: Float = 0f

    fun reset() {
        accumulatedDyPx = 0f
        oppositeJitterPx = 0f
    }

    fun onScroll(
        dyPx: Int,
        scrollYPx: Int,
        density: Float,
        currentlyMinimized: Boolean,
    ): Boolean {
        val safeDensity = density.coerceAtLeast(1f)
        if (scrollYPx <= (18f * safeDensity).toInt()) {
            accumulatedDyPx = 0f
            oppositeJitterPx = 0f
            return false
        }
        if (dyPx == 0) return currentlyMinimized
        val jitterTolerancePx = 3f * safeDensity
        if ((dyPx > 0 && accumulatedDyPx < 0f) || (dyPx < 0 && accumulatedDyPx > 0f)) {
            oppositeJitterPx += kotlin.math.abs(dyPx)
            if (oppositeJitterPx <= jitterTolerancePx) {
                return currentlyMinimized
            }
            accumulatedDyPx = if (dyPx > 0) oppositeJitterPx else -oppositeJitterPx
            oppositeJitterPx = 0f
        } else {
            oppositeJitterPx = 0f
            accumulatedDyPx += dyPx
        }
        val collapseThreshold = 34f * safeDensity
        val expandThreshold = -24f * safeDensity
        val minScrollToCollapse = (68f * safeDensity).toInt()
        return when {
            !currentlyMinimized && accumulatedDyPx >= collapseThreshold && scrollYPx >= minScrollToCollapse -> {
                accumulatedDyPx = 0f
                oppositeJitterPx = 0f
                true
            }
            currentlyMinimized && accumulatedDyPx <= expandThreshold -> {
                accumulatedDyPx = 0f
                oppositeJitterPx = 0f
                false
            }
            else -> currentlyMinimized
        }
    }
}

enum class LibraryTab(val label: String) {
    BOOKMARKS("书签"),
    READING_LIST("阅读列表"),
    HISTORY("历史记录"),
    DOWNLOADS("下载项"),
}

enum class ContextHitType {
    IMAGE,
    LINK,
}

@Immutable
data class ContextHitInfo(
    val type: ContextHitType,
    val url: String,
    val title: String = "",
)

@Immutable
data class BrowserTab(
    val id: String,
    val groupId: String,
    val url: String = "",
    val title: String = "起始页",
    val host: String = "",
    val accentColorArgb: Long = 0xFF0A84FF,
    val themeColorArgb: Long? = null,
    val isPinned: Boolean = false,
    val isLoading: Boolean = false,
    val progress: Float = 0f,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val requestDesktopSite: Boolean = false,
    val adaptiveDarkMode: SiteDarkMode = SiteDarkMode.AUTO,
    val contentBlockerEnabled: Boolean = true,
    val pageZoom: Int = 100,
    val blockedTrackers: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val lastActiveAt: Long = System.currentTimeMillis(),
) {
    val isStartPage: Boolean
        get() = url.isBlank()
}

@Immutable
data class TabGroup(
    val id: String,
    val name: String,
    val isPrivate: Boolean = false,
    val isDefault: Boolean = false,
    val accentColorArgb: Long = 0xFF0A84FF,
)

@Immutable
data class FavoriteItem(
    val id: String,
    val title: String,
    val url: String,
    val shortGlyph: String,
    val colorArgb: Long,
)

@Immutable
data class BookmarkItem(
    val id: String,
    val title: String,
    val url: String,
    val folder: String = "个人收藏",
    val createdAt: Long = System.currentTimeMillis(),
)

@Immutable
data class ReadingListItem(
    val id: String,
    val title: String,
    val url: String,
    val excerpt: String = "",
    val isRead: Boolean = false,
    val addedAt: Long = System.currentTimeMillis(),
)

@Immutable
data class HistoryEntry(
    val id: String,
    val title: String,
    val url: String,
    val visitedAt: Long = System.currentTimeMillis(),
)

@Immutable
data class PageMenuItemConfig(
    val id: String,
    val title: String,
    val enabled: Boolean = true,
    val order: Int = 0,
)

object BrowserUrlUtils {
    private val trackerDomains = listOf(
        "doubleclick.net",
        "googlesyndication.com",
        "google-analytics.com",
        "googletagmanager.com",
        "googleadservices.com",
        "adservice.google.com",
        "facebook.net",
        "connect.facebook.net",
        "analytics.twitter.com",
        "ads-twitter.com",
        "cpro.baidu.com",
        "hm.baidu.com",
        "pos.baidu.com",
        "cnzz.com",
        "umeng.com",
        "scorecardresearch.com",
        "quantserve.com",
        "taboola.com",
        "outbrain.com",
        "criteo.com",
        "hotjar.com",
        "mixpanel.com",
        "segment.io",
        "amplitude.com",
        "sentry.io",
        "byteoversea.com",
        "pangle.io",
    )

    private val schemeRegex = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://.*")
    private val hostPortOrDomainRegex = Regex(
        "^(localhost(:\\d+)?(/.*)?|(\\d{1,3}\\.){3}\\d{1,3}(:\\d+)?(/.*)?|([\\w-]+\\.)+[a-zA-Z]{2,}(:\\d+)?(/.*)?)$",
    )

    fun normalizeSearchTemplate(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""
        val withScheme = if (schemeRegex.matches(trimmed)) trimmed else "https://$trimmed"
        return if (withScheme.contains("%s") || withScheme.contains("{searchTerms}")) {
            withScheme
        } else {
            "$withScheme%s"
        }
    }

    fun isValidSearchEngineTemplate(raw: String): Boolean {
        val normalized = normalizeSearchTemplate(raw)
        if (normalized.isEmpty()) return false
        val sample = normalized
            .replace("%s", "test")
            .replace("{searchTerms}", "test")
        val parsed = runCatching { URI(sample) }.getOrNull() ?: return false
        val scheme = parsed.scheme?.lowercase(Locale.ROOT) ?: return false
        if (scheme != "http" && scheme != "https") return false
        val host = parsed.host?.trim().orEmpty()
        return host.isNotEmpty() && (host.contains('.') || host.equals("localhost", ignoreCase = true))
    }

    fun buildSearchUrl(queryTemplate: String, encodedQuery: String): String {
        val trimmed = queryTemplate.trim()
        if (trimmed.isEmpty()) return "https://www.bing.com/search?q=$encodedQuery"
        val withScheme = if (schemeRegex.matches(trimmed)) trimmed else "https://$trimmed"
        return when {
            withScheme.contains("%s") -> withScheme.replace("%s", encodedQuery)
            withScheme.contains("{searchTerms}") -> withScheme.replace("{searchTerms}", encodedQuery)
            else -> "$withScheme$encodedQuery"
        }
    }

    fun normalizeInputToUrl(raw: String, searchEngine: SearchEngine = SearchEngine.BING): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""
        if (schemeRegex.matches(trimmed)) {
            return trimmed
        }
        if (!trimmed.contains(' ') && hostPortOrDomainRegex.matches(trimmed)) {
            val lower = trimmed.lowercase(Locale.ROOT)
            val scheme = if (lower.startsWith("localhost") || lower.firstOrNull()?.isDigit() == true) "http" else "https"
            return "$scheme://$trimmed"
        }
        val encoded = URLEncoder.encode(trimmed, StandardCharsets.UTF_8.name())
        return buildSearchUrl(searchEngine.queryTemplate, encoded)
    }

    fun displayHost(url: String): String {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return ""
        val parsedHost = runCatching {
            val withScheme = if (schemeRegex.matches(trimmed)) trimmed else "https://$trimmed"
            URI(withScheme).host
        }.getOrNull()
        val rawHost = parsedHost?.takeIf { it.isNotBlank() }
            ?: trimmed.substringAfter("://").substringBefore('/').substringBefore('?').substringBefore(':')
        return rawHost.removePrefix("www.").lowercase(Locale.ROOT)
    }

    fun isTrackerHost(host: String): Boolean {
        val normalized = host.trim().lowercase(Locale.ROOT)
        if (normalized.isEmpty()) return false
        return trackerDomains.any { tracker ->
            normalized == tracker || normalized.endsWith(".$tracker")
        }
    }

    fun accentColorForUrl(url: String): Long {
        val host = displayHost(url)
        return when {
            host.contains("apple") -> 0xFF1C1C1E
            host.contains("github") -> 0xFF24292F
            host.contains("huawei") -> 0xFFC7000B
            host.contains("baidu") -> 0xFF2932E1
            host.contains("bing") -> 0xFF00809D
            host.contains("wikipedia") -> 0xFF3366CC
            host.contains("android") -> 0xFF3DDC84
            host.contains("deepseek") -> 0xFF4D6BFE
            else -> 0xFF0A84FF
        }
    }

    fun parseCssColorArgb(raw: String?): Long? {
        val cleaned = raw?.trim()?.trim('"', '\'')?.trim()?.lowercase(Locale.ROOT) ?: return null
        if (cleaned.isEmpty() || cleaned == "null" || cleaned == "transparent" || cleaned == "none") return null
        if (cleaned.startsWith("#")) {
            val hex = cleaned.removePrefix("#")
            return when (hex.length) {
                3 -> {
                    val r = hex[0].toString().repeat(2).toIntOrNull(16) ?: return null
                    val g = hex[1].toString().repeat(2).toIntOrNull(16) ?: return null
                    val b = hex[2].toString().repeat(2).toIntOrNull(16) ?: return null
                    (0xFFL shl 24) or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong()
                }
                6 -> {
                    val rgb = hex.toLongOrNull(16) ?: return null
                    0xFF000000L or rgb
                }
                8 -> {
                    // #RRGGBBAA
                    val r = hex.substring(0, 2).toIntOrNull(16) ?: return null
                    val g = hex.substring(2, 4).toIntOrNull(16) ?: return null
                    val b = hex.substring(4, 6).toIntOrNull(16) ?: return null
                    val a = hex.substring(6, 8).toIntOrNull(16) ?: return null
                    if (a < 13) return null
                    (a.toLong() shl 24) or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong()
                }
                else -> null
            }
        }
        if (cleaned.startsWith("rgb(") || cleaned.startsWith("rgba(")) {
            val inner = cleaned.substringAfter('(').substringBefore(')')
            val parts = inner.split(',', '/', ' ').map { it.trim() }.filter { it.isNotEmpty() }
            if (parts.size < 3) return null
            val r = parts[0].toFloatOrNull()?.toInt()?.coerceIn(0, 255) ?: return null
            val g = parts[1].toFloatOrNull()?.toInt()?.coerceIn(0, 255) ?: return null
            val b = parts[2].toFloatOrNull()?.toInt()?.coerceIn(0, 255) ?: return null
            val alpha = parts.getOrNull(3)?.toFloatOrNull()?.coerceIn(0f, 1f) ?: 1f
            if (alpha <= 0.05f) return null
            return (0xFFL shl 24) or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong()
        }
        return null
    }

    fun isUniformBlankSample(pixels: IntArray): Boolean {
        if (pixels.isEmpty()) return true
        val base = pixels[0]
        val baseR = (base shr 16) and 0xFF
        val baseG = (base shr 8) and 0xFF
        val baseB = base and 0xFF
        var matchCount = 0
        for (px in pixels) {
            val r = (px shr 16) and 0xFF
            val g = (px shr 8) and 0xFF
            val b = px and 0xFF
            if (kotlin.math.abs(r - baseR) <= 6 &&
                kotlin.math.abs(g - baseG) <= 6 &&
                kotlin.math.abs(b - baseB) <= 6
            ) {
                matchCount++
            }
        }
        return matchCount * 100 >= pixels.size * 96
    }
}
