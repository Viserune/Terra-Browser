package app.terra.browser

import android.view.KeyEvent
import android.view.KeyEvent.*
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

internal fun KeyEvent.isOverviewShortcut() = keyCode == KEYCODE_BACKSLASH &&
    (hasModifiers(META_CTRL_ON or META_SHIFT_ON) || hasModifiers(META_META_ON or META_SHIFT_ON))

/** The address dialog and the page have separate windows but share the same key sequences. */
internal class BrowserKeys(private val activity: MainActivity) {
    private val claimed = mutableSetOf<Pair<Int, Int>>()

    fun handle(event: KeyEvent, addressAction: ((KeyEvent) -> Boolean)? = null): Boolean {
        val key = event.deviceId to event.keyCode
        if (event.action == ACTION_UP) return claimed.remove(key)
        if (event.action != ACTION_DOWN) return false
        if (event.repeatCount > 0) return key in claimed
        claimed.remove(key)
        val handled = with(activity) {
            if (tabs.isEmpty() || prompt != null || bookmarkDraft != null || contextMenu != null || videoFullscreen)
                return false
            val code = event.keyCode
            if (code == KEYCODE_ESCAPE && event.hasNoModifiers() && (sheet == "地址" || sheet == "查找")) {
                WindowCompat.getInsetsController(window, window.decorView).hide(WindowInsetsCompat.Type.ime())
                sheet = ""
                return@with true
            }
            val modifiers = (if (event.isCtrlPressed) META_CTRL_ON else META_META_ON) or
                (if (event.isShiftPressed) META_SHIFT_ON else 0)
            if (!event.hasModifiers(modifiers)) return false
            if (sheet == "查找") {
                when (code) {
                    KEYCODE_G -> page.web?.findNext(!event.isShiftPressed)
                    KEYCODE_F -> if (event.isShiftPressed) return false
                    KEYCODE_L -> if (!event.isShiftPressed) sheet = "地址" else return false
                    else -> return false
                }
                return@with true
            }
            if (sheet.isNotEmpty() && sheet != "地址") return false
            if (sheet == "地址" && addressAction?.invoke(event) == true) return@with true
            if (event.isOverviewShortcut()) {
                sheet = "标签页"
                return@with true
            }
            when (code) {
                KEYCODE_T -> if (event.isShiftPressed) {
                    if (!page.secret) recentlyClosed.firstOrNull()?.let(::reopen)
                } else {
                    add(page.secret)
                    sheet = "地址"
                }
                KEYCODE_TAB -> {
                    if (!event.isCtrlPressed) return false
                    cycle(!event.isShiftPressed)
                }
                KEYCODE_LEFT_BRACKET, KEYCODE_RIGHT_BRACKET -> {
                    val forwards = code == KEYCODE_RIGHT_BRACKET
                    if (event.isShiftPressed) cycle(forwards)
                    else {
                        sheet = ""
                        page.web?.let { if (forwards && it.canGoForward()) it.goForward()
                            else if (!forwards && it.canGoBack()) it.goBack() }
                    }
                }
                else -> {
                    if (event.isShiftPressed) return false
                    when (code) {
                        KEYCODE_L -> sheet = "地址"
                        KEYCODE_W -> { sheet = ""; close(page) }
                        KEYCODE_R -> { sheet = ""; page.web?.reload() }
                        KEYCODE_F -> if (page.url.isNotBlank()) openFind() else return false
                        in KEYCODE_1..KEYCODE_8 -> activeTabs.getOrNull(code - KEYCODE_1)?.let(::select)
                        KEYCODE_9 -> select(activeTabs.last())
                        else -> return false
                    }
                }
            }
            true
        }
        if (handled) claimed.add(key)
        return handled
    }

    private fun cycle(forwards: Boolean) = with(activity) {
        val group = activeTabs
        val step = if (forwards) 1 else -1
        select(group[Math.floorMod(group.indexOf(page) + step, group.size)])
    }
}
