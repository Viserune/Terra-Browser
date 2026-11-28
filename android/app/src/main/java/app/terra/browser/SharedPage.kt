package app.terra.browser

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.print.PrintAttributes
import android.print.PrintManager
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import java.util.UUID

internal class SharedPage(val site: Site, val page: Page?, val anchor: Offset?, val secret: Boolean) {
    private val revision = page?.readerRequest
    private val source = page?.web
    val domain = site.url.toUri().host.orEmpty().ifBlank { site.url }
    val formats get() = when {
        currentDocument() == null -> listOf(ShareFormat.Auto)
        secret -> listOf(ShareFormat.Auto, ShareFormat.Pdf, ShareFormat.ReaderPdf)
        else -> ShareFormat.entries
    }
    fun currentDocument() = source?.takeIf { page?.web === it && page.readerRequest == revision && it.url == site.url }
}

internal enum class ShareFormat(val label: String, val mime: String, val extension: String) {
    Auto("自动", "text/plain", ""), Pdf("PDF", "application/pdf", "pdf"),
    ReaderPdf("阅读器视图版 PDF", "application/pdf", "pdf"), Archive("网页归档", "multipart/related", "mht");
    val pdf get() = this == Pdf || this == ReaderPdf
}

internal data class ShareApp(val component: ComponentName, val label: String, val icon: ImageBitmap, val format: ShareFormat)

internal fun MainActivity.share(url: String, title: String? = null, anchor: Offset? = null) {
    if (url.isBlank()) return
    val source = page.takeIf { it.url == url }
    val name = title?.takeIf { it.isNotBlank() && it != url } ?: source?.title?.takeIf { it.isNotBlank() }
        ?: url.toUri().host ?: url
    sharedPage = SharedPage(Site(name, url), source, anchor, page.secret)
}

internal fun MainActivity.shareApps(format: ShareFormat, canExportPage: Boolean): List<ShareApp> {
    val pm = packageManager
    val types = if (format == ShareFormat.Auto && canExportPage) listOf(format, ShareFormat.Pdf) else listOf(format)
    return types.flatMap { type ->
        pm.queryIntentActivities(Intent(Intent.ACTION_SEND).setType(type.mime), PackageManager.MATCH_DEFAULT_ONLY)
            .filter { it.activityInfo.exported && it.activityInfo.packageName != packageName }
            .sortedWith(ResolveInfo.DisplayNameComparator(pm))
            .mapNotNull {
                runCatching { ShareApp(ComponentName(it.activityInfo.packageName, it.activityInfo.name),
                    it.loadLabel(pm).toString(), it.loadIcon(pm).toBitmap(96, 96).asImageBitmap(), type) }.getOrNull()
            }
    }.distinctBy { it.component }
}

internal fun MainActivity.sendSharedPage(item: SharedPage, document: SharedDocument?, target: ComponentName?,
    includeTitle: Boolean = false) {
    if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return
    val site = item.site
    val send = Intent(Intent.ACTION_SEND).setType(document?.format?.mime ?: "text/plain")
        .putExtra(Intent.EXTRA_SUBJECT, site.name).putExtra(Intent.EXTRA_TITLE, site.name)
    if (document == null) {
        val text = if (includeTitle && site.name != site.url) site.name + "\n" + site.url else site.url
        send.putExtra(Intent.EXTRA_TEXT, text)
    } else {
        val uri = document.uri(this)
        send.clipData = ClipData.newUri(contentResolver, document.name, uri)
        send.putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        if (target == null) startActivity(Intent.createChooser(send, null))
        else {
            startActivity(send.setComponent(target))
            if (!item.secret) {
                recentShareApps.remove(target)
                recentShareApps.add(0, target)
                if (recentShareApps.size > 4) recentShareApps.removeAt(recentShareApps.lastIndex)
            }
        }
    } catch (_: ActivityNotFoundException) { notice("此共享目标已不可用") }
      catch (_: SecurityException) { notice("此应用无法接收共享内容") }
}

internal fun MainActivity.printSharedPage(item: SharedPage) {
    val web = item.currentDocument()
    if (web == null) { notice("网页已更改，请重新打开共享面板"); return }
    val name = item.site.name
    runCatching { getSystemService(PrintManager::class.java).print(name, web.createPrintDocumentAdapter(name),
        PrintAttributes.Builder().build()) }.onFailure { notice("无法打开打印界面") }
}

internal fun MainActivity.pinSharedPage(site: Site) {
    val manager = getSystemService(ShortcutManager::class.java)
    val shortcut = ShortcutInfo.Builder(this, UUID.nameUUIDFromBytes(site.url.toByteArray()).toString())
        .setShortLabel(site.name).setIcon(Icon.createWithResource(this, R.drawable.terra_icon))
        .setIntent(Intent(Intent.ACTION_VIEW, site.url.toUri(), this, MainActivity::class.java)).build()
    if (!manager.requestPinShortcut(shortcut, null)) notice("当前桌面不支持添加快捷方式")
}
