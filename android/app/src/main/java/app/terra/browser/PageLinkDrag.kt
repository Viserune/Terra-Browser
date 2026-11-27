package app.terra.browser

import android.content.ClipData
import android.content.ClipDescription
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.text.TextUtils
import android.view.View
import android.webkit.WebView
import android.widget.TextView
import kotlin.math.roundToInt

internal fun WebView.dragPageLink(url: String, title: String?, privateTab: Boolean): Boolean {
    val uri = Uri.parse(url)
    val label = title?.trim()?.takeIf { it.isNotEmpty() } ?: uri.host ?: url
    val density = resources.displayMetrics.density
    fun dp(value: Int) = (value * density).roundToInt()
    val badge = TextView(context).apply {
        text = label
        textSize = 15f
        setTextColor(Color.WHITE)
        isSingleLine = true
        ellipsize = TextUtils.TruncateAt.END
        setPadding(dp(12), dp(7), dp(12), dp(7))
        background = GradientDrawable().apply {
            setColor(0xeb286d8a.toInt())
            cornerRadius = dp(10).toFloat()
        }
        measure(View.MeasureSpec.makeMeasureSpec(dp(230), View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        layout(0, 0, measuredWidth, measuredHeight)
    }
    val content = ClipData(label, arrayOf(ClipDescription.MIMETYPE_TEXT_URILIST, ClipDescription.MIMETYPE_TEXT_PLAIN),
        ClipData.Item(url, null, null, uri))
    val flags = if (privateTab) 0 else View.DRAG_FLAG_GLOBAL
    return startDragAndDrop(content, View.DragShadowBuilder(badge), null, flags)
}
