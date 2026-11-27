package app.terra.browser

import androidx.compose.foundation.lazy.grid.LazyGridItemInfo
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal class LiftedTab(
    val tab: Page, val members: List<Page>, val preview: State<ImageBitmap?>,
    val size: IntSize, val grip: Offset, point: Offset
) {
    var point by mutableStateOf(point)
    var destination by mutableIntStateOf(members.indexOf(tab))
    var landing by mutableStateOf<Offset?>(null)
    val origin get() = point - grip

    fun order(): List<Page> = members.toMutableList().apply {
        remove(tab)
        add(destination, tab)
    }
}

/** The original window owns a held pointer even after the context menu opens. */
internal class GridTabReorder(
    val grid: LazyGridState, private val touches: MenuTouchSource,
    private val activity: MainActivity, private val scope: CoroutineScope
) {
    var coordinates: LayoutCoordinates? = null
    var lifted by mutableStateOf<LiftedTab?>(null)
        private set
    private var pendingMenu: Job? = null
    private var releaseTouch: (() -> Unit)? = null

    private fun originOf(item: LazyGridItemInfo) =
        Offset(item.offset.x.toFloat(), (item.offset.y - grid.layoutInfo.viewportStartOffset).toFloat())

    fun arm(tab: Page, members: List<Page>, image: State<ImageBitmap?>, menu: ContextMenu, show: () -> Unit) {
        val press = touches.pressOrigin
        val coords = coordinates
        val item = grid.layoutInfo.visibleItemsInfo.firstOrNull { it.key == tab.id }
        if (press == null || coords == null || !coords.isAttached || item == null ||
            members.count { it.pinned == tab.pinned } < 2) {
            show()
            return
        }
        val grip = coords.screenToLocal(press) - originOf(item)
        fun start(point: Offset): Boolean {
            if (touches.pressOrigin != press || !coords.isAttached || tab !in activity.tabs) return false
            pendingMenu?.cancel()
            lifted = LiftedTab(tab, members, image, item.size, grip, coords.screenToLocal(point))
            updateTarget()
            releaseTouch = touches.track(stationaryRelease = true) { next, up -> move(next, up) }
            return true
        }
        menu.dragAway = ::start
        pendingMenu?.cancel()
        pendingMenu = scope.launch { delay(220); show() }
        releaseTouch = touches.track(stationaryRelease = true) { point, up ->
            if (point == null) cancel()
            else if (up) { pendingMenu?.cancel(); show() }
            else start(point)
        }
    }

    fun cancel() {
        pendingMenu?.cancel()
        releaseTouch?.invoke()
        releaseTouch = null
        lifted = null
    }

    private fun move(screen: Offset?, up: Boolean) {
        val drag = lifted ?: return
        val coords = coordinates
        if (screen == null || coords == null || !coords.isAttached) { cancel(); return }
        drag.point = coords.screenToLocal(screen)
        updateTarget()
        if (!up) return
        val target = drag.members[drag.destination]
        if (drag.point.x !in 0f..coords.size.width.toFloat() ||
            drag.point.y !in 0f..coords.size.height.toFloat() || target !in activity.tabs) {
            cancel()
            return
        }
        // Bind to a slot, not the animated neighbour currently crossing that slot.
        val slot = grid.layoutInfo.visibleItemsInfo.firstOrNull { it.index == drag.destination }
        activity.moveOverviewTab(drag.tab, target)
        if (slot == null) cancel() else drag.landing = originOf(slot)
    }

    fun updateTarget() {
        val drag = lifted ?: return
        val point = drag.point
        val candidate = grid.layoutInfo.visibleItemsInfo.firstOrNull {
            val origin = originOf(it)
            point.x >= origin.x && point.x < origin.x + it.size.width &&
                point.y >= origin.y && point.y < origin.y + it.size.height
        } ?: return
        if (candidate.index != drag.destination && drag.members.getOrNull(candidate.index)?.pinned == drag.tab.pinned) {
            // Keep the viewport in place if its first visible key is the card being moved.
            grid.requestScrollToItem(grid.firstVisibleItemIndex, grid.firstVisibleItemScrollOffset)
            drag.destination = candidate.index
        }
    }

    suspend fun scrollAtEdge(edge: Float, footer: Float, speed: Float) {
        var before = withFrameNanos { it }
        while (lifted?.landing == null && lifted != null) {
            val now = withFrameNanos { it }
            val seconds = ((now-before)/1_000_000_000f).coerceAtMost(.05f)
            before = now
            val y = lifted?.point?.y ?: break
            val height = coordinates?.size?.height ?: break
            val direction = ((y-height+footer+edge)/edge).coerceIn(0f,1f) -
                ((edge-y)/edge).coerceIn(0f,1f)
            if (direction != 0f && grid.dispatchRawDelta(speed * direction * seconds) != 0f) updateTarget()
        }
    }
}
