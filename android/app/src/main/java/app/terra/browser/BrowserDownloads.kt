package app.terra.browser

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.os.Environment
import java.io.File
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

internal data class DownloadItem(val id: Long, val title: String, val status: Int, val received: Long, val total: Long,
    val url: String, val mime: String?) {
    val active get() = status == DownloadManager.STATUS_PENDING || status == DownloadManager.STATUS_RUNNING ||
        status == DownloadManager.STATUS_PAUSED
}

internal class BrowserDownloads(private val activity: MainActivity) {
    private val manager = activity.getSystemService(DownloadManager::class.java)
    private val prefs = activity.getSharedPreferences("download-list", Context.MODE_PRIVATE)
    // All ordinary windows share the list. Private windows only retain IDs for their own session.
    companion object { private var ordinary by mutableStateOf<Set<Long>?>(null) }
    private var privateIds by mutableStateOf(emptySet<Long>())

    init {
        if (ordinary == null) ordinary = prefs.getStringSet("items", emptySet()).orEmpty().mapNotNull(String::toLongOrNull).toSet()
    }

    fun ids(secret: Boolean) = if (secret) privateIds else ordinary.orEmpty()

    private fun save(secret: Boolean, items: Set<Long>) {
        if (secret) privateIds = items else {
            ordinary = items
            prefs.edit().putStringSet("items",items.map(Long::toString).toSet()).apply()
        }
    }

    fun add(secret: Boolean, id: Long, replaces: Long?) {
        val current = ids(secret)
        save(secret, (if (replaces == null) current else current - replaces) + id)
    }
    fun forget(secret: Boolean, idsToForget: Set<Long>) = save(secret, ids(secret) - idsToForget)
    fun endPrivateSession() { privateIds = emptySet() }

    fun availableName(requested: String): String {
        val directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val pending = mutableSetOf<String>()
        val active = DownloadManager.STATUS_RUNNING or DownloadManager.STATUS_PENDING or DownloadManager.STATUS_PAUSED
        manager.query(DownloadManager.Query().setFilterByStatus(active))?.use { cursor ->
            val title = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE)
            while (cursor.moveToNext()) cursor.getString(title)?.let(pending::add)
        }
        val dot = requested.lastIndexOf('.').takeIf { it > 0 } ?: requested.length
        val stem = requested.take(dot)
        val extension = requested.drop(dot)
        var candidate = requested
        var number = 2
        while (candidate in pending || File(directory,candidate).exists()) {
            candidate = "$stem ($number)$extension"
            number++
        }
        return candidate
    }

    fun read(ids: Set<Long>): List<DownloadItem> {
        val items = mutableListOf<DownloadItem>()
        for (batch in ids.chunked(250)) {
            val query = DownloadManager.Query().setFilterById(*batch.toLongArray())
            manager.query(query)?.use { cursor ->
                val id = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_ID)
                val title = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE)
                val status = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
                val received = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                val total = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                val url = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_URI)
                val mime = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_MEDIA_TYPE)
                while (cursor.moveToNext()) items.add(DownloadItem(cursor.getLong(id),cursor.getString(title).orEmpty(),
                    cursor.getInt(status),cursor.getLong(received),cursor.getLong(total),cursor.getString(url).orEmpty(),cursor.getString(mime)))
            } ?: error("Download provider unavailable")
        }
        return items.sortedByDescending { it.id }
    }

    fun cancel(secret: Boolean, item: DownloadItem) {
        activity.confirm("取消下载", "停止下载并移除这项任务的文件？\n"+item.title, "停止下载", destructive=true) {
            runCatching {
                if (read(setOf(item.id)).firstOrNull()?.active == true) {
                    manager.remove(item.id)
                    forget(secret,setOf(item.id))
                } else activity.notice("下载状态已更新")
            }.onFailure { activity.notice("无法取消下载") }
        }
    }

    fun open(item: DownloadItem) {
        runCatching {
            manager.openDownloadedFile(item.id).use { }
            val uri = requireNotNull(manager.getUriForDownloadedFile(item.id))
            activity.startActivity(Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, manager.getMimeTypeForDownloadedFile(item.id) ?: "application/octet-stream")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        }.onFailure { activity.notice("无法打开文件，请在下载目录中查看") }
    }

    fun openDirectory() {
        runCatching { activity.startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)) }
            .onFailure { activity.notice("无法打开下载目录") }
    }
}
