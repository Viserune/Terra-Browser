package app.terra.browser

import android.view.MotionEvent
import android.view.Window
import androidx.compose.ui.geometry.Offset

/** A dialog receives new presses. The window where the long press began still
 * owns that original finger until it lifts. Forward only that stream to the menu. */
internal class MenuTouchSource(private val slop: Int) {
    private var down: Offset? = null
    private var consumer: ((Offset?, Boolean) -> Unit)? = null
    private var moved = false
    private var intercepted = false

    val pressOrigin get() = down
    private var reportStationaryRelease = false

    fun track(stationaryRelease: Boolean = false, callback: (Offset?, Boolean) -> Unit): () -> Unit {
        if (down != null) {
            consumer = callback
            reportStationaryRelease = stationaryRelease
        }
        return { if (consumer === callback) consumer = null }
    }

    fun stopTracking() { consumer = null }

    fun dispatch(event: MotionEvent, forward: (MotionEvent) -> Boolean): Boolean {
        val action = event.actionMasked
        val point = Offset(event.rawX, event.rawY)
        if (action == MotionEvent.ACTION_DOWN) {
            down = point
            moved = false
            intercepted = false
            consumer = null
        }
        val start = down
        val handle = consumer
        if (start != null && handle != null && action != MotionEvent.ACTION_DOWN) {
            if (!intercepted) {
                intercepted = true
                val cancel = MotionEvent.obtain(event)
                cancel.action = MotionEvent.ACTION_CANCEL
                try { forward(cancel) } finally { cancel.recycle() }
            }
            if (action == MotionEvent.ACTION_CANCEL || event.pointerCount != 1) {
                consumer = null
                handle(null, moved || reportStationaryRelease)
            } else if (action == MotionEvent.ACTION_MOVE || action == MotionEvent.ACTION_UP) {
                if ((point - start).getDistance() > slop) moved = true
                if (moved || (reportStationaryRelease && action == MotionEvent.ACTION_UP))
                    handle(point, action == MotionEvent.ACTION_UP)
            }
        }
        val consumed = intercepted
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            down = null
            consumer = null
            intercepted = false
        }
        return consumed || forward(event)
    }

    fun observe(window: Window, onKey: ((android.view.KeyEvent) -> Boolean)? = null): () -> Unit {
        val original = window.callback
        val wrapper = object : Window.Callback by original {
            override fun dispatchTouchEvent(event: MotionEvent) = dispatch(event, original::dispatchTouchEvent)
            override fun dispatchKeyEvent(event: android.view.KeyEvent) =
                onKey?.invoke(event) == true || original.dispatchKeyEvent(event)
        }
        window.callback = wrapper
        return {
            if (window.callback === wrapper) window.callback = original
            stopTracking()
            down = null
        }
    }
}
