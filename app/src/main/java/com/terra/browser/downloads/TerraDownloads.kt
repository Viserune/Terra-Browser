package com.terra.browser.downloads

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.webkit.CookieManager
import android.webkit.URLUtil
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File

data class TerraDownloadItem(
    val id: Long,
    val title: String,
    val status: Int,
    val received: Long,
    val total: Long,
    val url: String,
    val mime: String?,
) {
    val active: Boolean
        get() = status == DownloadManager.STATUS_PENDING ||
            status == DownloadManager.STATUS_RUNNING ||
            status == DownloadManager.STATUS_PAUSED

    val completed: Boolean
        get() = status == DownloadManager.STATUS_SUCCESSFUL

    val failed: Boolean
        get() = status == DownloadManager.STATUS_FAILED
}

class TerraDownloads(context: Context) {
    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(DownloadManager::class.java)
    private val prefs = appContext.getSharedPreferences("terra_downloads", Context.MODE_PRIVATE)

    var revision by mutableIntStateOf(0)
        private set

    private var ordinaryIds by mutableStateOf(
        prefs.getStringSet("items", emptySet()).orEmpty().mapNotNull(String::toLongOrNull).toSet(),
    )
    private var privateIds by mutableStateOf(emptySet<Long>())

    fun ids(isPrivate: Boolean): Set<Long> = if (isPrivate) privateIds else ordinaryIds

    private fun save(isPrivate: Boolean, items: Set<Long>) {
        if (isPrivate) {
            privateIds = items
        } else {
            ordinaryIds = items
            prefs.edit().putStringSet("items", items.map(Long::toString).toSet()).apply()
        }
        revision++
    }

    fun availableName(requested: String): String {
        val safeRequested = requested.ifBlank { "download" }.substringAfterLast('/').replace('\\', '_')
        val directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val pending = mutableSetOf<String>()
        val activeMask = DownloadManager.STATUS_RUNNING or DownloadManager.STATUS_PENDING or DownloadManager.STATUS_PAUSED
        runCatching {
            manager?.query(DownloadManager.Query().setFilterByStatus(activeMask))?.use { cursor ->
                val titleIdx = cursor.getColumnIndex(DownloadManager.COLUMN_TITLE)
                if (titleIdx >= 0) {
                    while (cursor.moveToNext()) {
                        cursor.getString(titleIdx)?.let(pending::add)
                    }
                }
            }
        }
        val dot = safeRequested.lastIndexOf('.').takeIf { it > 0 } ?: safeRequested.length
        val stem = safeRequested.take(dot)
        val ext = safeRequested.drop(dot)
        var candidate = safeRequested
        var index = 2
        while (candidate in pending || (directory != null && File(directory, candidate).exists())) {
            candidate = "$stem ($index)$ext"
            index++
        }
        return candidate
    }

    fun enqueue(
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?,
        isPrivate: Boolean,
        replaceId: Long? = null,
    ): String? {
        if (!(url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true))) {
            return null
        }
        val dm = manager ?: return null
        val guessed = URLUtil.guessFileName(url, contentDisposition, mimeType)
        val filename = availableName(guessed)
        return runCatching {
            val req = DownloadManager.Request(Uri.parse(url))
                .setTitle(filename)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            CookieManager.getInstance().getCookie(url)?.let { req.addRequestHeader("Cookie", it) }
            userAgent?.takeIf { it.isNotBlank() }?.let { req.addRequestHeader("User-Agent", it) }
            mimeType?.takeIf { it.isNotBlank() }?.let(req::setMimeType)
            val id = dm.enqueue(req)
            val updated = (if (replaceId == null) ids(isPrivate) else ids(isPrivate) - replaceId) + id
            save(isPrivate, updated)
            filename
        }.getOrNull()
    }

    fun read(isPrivate: Boolean): List<TerraDownloadItem> {
        val currentIds = ids(isPrivate)
        val dm = manager ?: return emptyList()
        if (currentIds.isEmpty()) return emptyList()
        val items = mutableListOf<TerraDownloadItem>()
        for (batch in currentIds.chunked(250)) {
            runCatching {
                val query = DownloadManager.Query().setFilterById(*batch.toLongArray())
                dm.query(query)?.use { cursor ->
                    val idIdx = cursor.getColumnIndex(DownloadManager.COLUMN_ID)
                    val titleIdx = cursor.getColumnIndex(DownloadManager.COLUMN_TITLE)
                    val statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    val receivedIdx = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                    val totalIdx = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                    val uriIdx = cursor.getColumnIndex(DownloadManager.COLUMN_URI)
                    val mimeIdx = cursor.getColumnIndex(DownloadManager.COLUMN_MEDIA_TYPE)
                    while (cursor.moveToNext()) {
                        items.add(
                            TerraDownloadItem(
                                id = if (idIdx >= 0) cursor.getLong(idIdx) else 0L,
                                title = if (titleIdx >= 0) cursor.getString(titleIdx).orEmpty() else "",
                                status = if (statusIdx >= 0) cursor.getInt(statusIdx) else 0,
                                received = if (receivedIdx >= 0) cursor.getLong(receivedIdx) else 0L,
                                total = if (totalIdx >= 0) cursor.getLong(totalIdx) else -1L,
                                url = if (uriIdx >= 0) cursor.getString(uriIdx).orEmpty() else "",
                                mime = if (mimeIdx >= 0) cursor.getString(mimeIdx) else null,
                            ),
                        )
                    }
                }
            }
        }
        return items.sortedByDescending { it.id }
    }

    fun cancelOrRemove(isPrivate: Boolean, item: TerraDownloadItem) {
        runCatching {
            if (item.active) {
                manager?.remove(item.id)
            }
        }
        save(isPrivate, ids(isPrivate) - item.id)
    }

    fun clearInactive(isPrivate: Boolean) {
        val activeIds = read(isPrivate).filter { it.active }.map { it.id }.toSet()
        save(isPrivate, activeIds)
    }

    fun endPrivateSession() {
        privateIds = emptySet()
        revision++
    }

    fun open(item: TerraDownloadItem): Boolean {
        val dm = manager ?: return false
        return runCatching {
            dm.openDownloadedFile(item.id).use { }
            val uri = dm.getUriForDownloadedFile(item.id) ?: return@runCatching false
            val intent = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, dm.getMimeTypeForDownloadedFile(item.id) ?: "application/octet-stream")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            appContext.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    fun openDirectory(): Boolean {
        return runCatching {
            appContext.startActivity(
                Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            true
        }.getOrDefault(false)
    }
}
