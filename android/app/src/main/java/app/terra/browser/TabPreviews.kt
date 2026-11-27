package app.terra.browser

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.util.LruCache
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.Executors
import kotlin.math.roundToInt

internal class TabPreviews(cacheDir: File) {
    private val directory = File(cacheDir, "tab-previews")
    private data class Entry(val url: String, val bitmap: Bitmap)
    private val memory = object : LruCache<String, Entry>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Entry) = value.bitmap.allocationByteCount
    }

    companion object {
        // Preserve write / close order even when the Activity is recreated.
        private val worker = Executors.newSingleThreadExecutor()
        private val dispatcher = worker.asCoroutineDispatcher()
        private fun digest(url: String) = MessageDigest.getInstance("SHA-256").digest(url.toByteArray())
    }

    fun cached(page: Page): Bitmap? = memory.get(page.id)?.takeIf { it.url == page.url }?.bitmap

    fun snapshot(page: Page): Bitmap? {
        val web = page.web ?: return null
        if (!page.documentVisible || web.url != page.url || page.error.isNotEmpty() ||
            web.width <= 0 || web.height <= 0 || web.contentHeight <= 0) return null
        val scale = minOf(1f, 500f / web.width, 950f / web.height)
        val bitmap = Bitmap.createBitmap((web.width * scale).roundToInt().coerceAtLeast(1),
            (web.height * scale).roundToInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            scale(scale, scale)
            translate(-web.scrollX.toFloat(), -web.scrollY.toFloat())
            web.draw(this)
        }
        return bitmap
    }

    fun capture(page: Page) {
        val bitmap = snapshot(page) ?: return
        val id = page.id
        val url = page.url
        memory.put(id, Entry(url, bitmap))
        if (page.secret) return
        worker.execute {
            val staging = File(directory, "$id.tmp")
            try {
                directory.mkdirs()
                staging.outputStream().buffered().use { out ->
                    out.write(digest(url))
                    if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)) return@execute
                }
                Files.move(staging.toPath(), File(directory, id).toPath(), StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING)
                var retained = 0L
                directory.listFiles()?.sortedByDescending { it.lastModified() }?.forEach {
                    retained += it.length()
                    if (retained > 20 * 1024 * 1024) it.delete()
                }
            } catch (_: IOException) {
                // The OS may evict this cache or run out of space while saving.
            } finally {
                staging.delete()
            }
        }
    }

    suspend fun restore(page: Page): Bitmap? {
        cached(page)?.let { return it }
        if (page.secret || !httpAddress(page.url)) return null
        val url = page.url
        val file = File(directory, page.id)
        val image = withContext(dispatcher) {
            try {
                DataInputStream(file.inputStream().buffered()).use { input ->
                    val stored = ByteArray(32)
                    input.readFully(stored)
                    if (stored.contentEquals(digest(url))) BitmapFactory.decodeStream(input) else null
                }
            } catch (_: IOException) { null }
        }
        if (page.url != url) return null
        return cached(page) ?: image?.also { memory.put(page.id, Entry(url, it)) }
    }

    fun forget(page: Page) {
        memory.remove(page.id)
        if (!page.secret) worker.execute { File(directory, page.id).delete() }
    }

    fun prune(openIds: Set<String>) {
        worker.execute { directory.listFiles()?.filter { it.name !in openIds }?.forEach { it.delete() } }
    }
}
