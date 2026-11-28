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
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import java.io.File
import java.util.UUID

internal class SharedPage(val site: Site, val page: Page?, val anchor: Offset?, val secret: Boolean) {
    val domain = site.url.toUri().host.orEmpty().ifBlank { site.url }
    val formats get() = if (currentDocument() != null && !secret) ShareFormat.entries else listOf(ShareFormat.Link, ShareFormat.WithTitle)
    fun currentDocument() = page?.web?.takeIf { page.url == site.url && it.url == site.url }
}

internal enum class ShareFormat(val label: String, val mime: String) {
    Link("网页链接", "text/plain"), WithTitle("标题和链接", "text/plain"), Archive("网页归档", "multipart/related")
}

internal data class ShareApp(val component: ComponentName, val label: String, val icon: ImageBitmap)

internal fun MainActivity.share(url: String, title: String? = null, anchor: Offset? = null) {
    if (url.isBlank()) return
    val source = page.takeIf { it.url == url }
    val name = title?.takeIf { it.isNotBlank() && it != url } ?: source?.title?.takeIf { it.isNotBlank() }
        ?: url.toUri().host ?: url
    sharedPage = SharedPage(Site(name, url), source, anchor, page.secret)
}

internal fun MainActivity.shareApps(mime: String): List<ShareApp> {
    val pm = packageManager
    return pm.queryIntentActivities(Intent(Intent.ACTION_SEND).setType(mime), PackageManager.MATCH_DEFAULT_ONLY)
        .filter { it.activityInfo.exported && it.activityInfo.packageName != packageName }
        .sortedWith(ResolveInfo.DisplayNameComparator(pm))
        .mapNotNull {
            runCatching { ShareApp(ComponentName(it.activityInfo.packageName, it.activityInfo.name),
                it.loadLabel(pm).toString(), it.loadIcon(pm).toBitmap(96, 96).asImageBitmap()) }.getOrNull()
        }.distinctBy { it.component }
}

internal fun MainActivity.sendSharedPage(item: SharedPage, format: ShareFormat, target: ComponentName?) {
    fun launch(intent: Intent) {
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return
        try {
            if (target == null) startActivity(Intent.createChooser(intent, null))
            else {
                startActivity(intent.setComponent(target))
                if (!item.secret) {
                    recentShareApps.remove(target)
                    recentShareApps.add(0, target)
                    if (recentShareApps.size > 4) recentShareApps.removeAt(recentShareApps.lastIndex)
                }
            }
        } catch (_: ActivityNotFoundException) { notice("此共享目标已不可用") }
          catch (_: SecurityException) { notice("此应用无法接收共享内容") }
    }
    val site = item.site
    val send = Intent(Intent.ACTION_SEND).setType(format.mime).putExtra(Intent.EXTRA_SUBJECT, site.name)
    if (format != ShareFormat.Archive) {
        val text = if (format == ShareFormat.WithTitle && site.name != site.url) site.name + "\n" + site.url else site.url
        launch(send.putExtra(Intent.EXTRA_TEXT, text))
        return
    }
    val web = item.currentDocument()
    if (web == null || item.secret) { notice("网页已更改，请重新打开共享面板"); return }
    val directory = File(cacheDir, "shared-pages")
    if (!directory.isDirectory && !directory.mkdirs()) { notice("无法创建网页归档"); return }
    val cutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
    directory.listFiles()?.filter { it.isFile && it.lastModified() < cutoff }?.forEach { it.delete() }
    val file = File(directory, "${UUID.randomUUID()}.mht")
    web.saveWebArchive(file.absolutePath, false) { result ->
        if (result == null || file.length() == 0L || item.currentDocument() !== web) {
            file.delete()
            if (!isDestroyed) notice("无法共享此网页归档")
        } else {
            val uri = FileProvider.getUriForFile(this, "$packageName.sharedpages", file)
            send.clipData = ClipData.newUri(contentResolver, site.name, uri)
            launch(send.putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        }
    }
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
