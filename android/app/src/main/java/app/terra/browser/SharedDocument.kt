package app.terra.browser

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.webkit.WebView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlin.coroutines.resume

internal class SharedDocument(val file: File, val name: String, val format: ShareFormat,
    val pdf: PagePdf? = null, val ink: List<PdfInk> = emptyList()) {
    fun uri(context: Context) = FileProvider.getUriForFile(context, "${context.packageName}.sharedpages", file, name)
}

internal fun Context.newShareFile(format: ShareFormat): File {
    val directory = File(cacheDir, "shared-pages")
    if (!directory.isDirectory && !directory.mkdirs() && !directory.isDirectory) throw IOException("无法创建共享文件")
    val cutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
    directory.listFiles()?.filter { it.isFile && it.lastModified()<cutoff }?.forEach { it.delete() }
    return File(directory, "${UUID.randomUUID()}.${format.extension}")
}

internal suspend fun MainActivity.prepareSharedDocument(item: SharedPage, format: ShareFormat): SharedDocument {
    val source = item.currentDocument() ?: throw IOException("网页已更改，请重新打开共享面板")
    if (format !in item.formats || format == ShareFormat.Auto) throw IOException("此网页不支持该格式")
    val title = item.site.name.replace(Regex("""[\p{Cntrl}\\/:*?"<>|]"""), " ").trim().take(100).ifBlank { "网页" }
    val name = title.removeSuffix(".${format.extension}") + ".${format.extension}"
    val file = withContext(Dispatchers.IO) { newShareFile(format) }
    var pdf: PagePdf? = null
    try {
        pdf = when (format) {
            ShareFormat.Pdf -> capturePagePdf(item)
            ShareFormat.ReaderPdf -> {
                val page = item.page ?: throw IOException("网页已关闭")
                val article = page.readerArticle ?: extractReaderArticle(page) ?: throw IOException("此网页没有可提取的阅读正文")
                capturePagePdf(item, article)
            }
            ShareFormat.Archive -> { source.saveArchive(file); null }
            ShareFormat.Auto -> error("Expected a document format")
        }
        pdf?.write(file)
        if (item.currentDocument() !== source || file.length()==0L) throw IOException("网页已更改或文件为空，请重试")
        return SharedDocument(file, name, format, pdf)
    } catch (failure: Throwable) {
        pdf?.close()
        file.delete()
        throw failure
    }
}

internal suspend fun WebView.saveArchive(file: File) {
    val ok = suspendCancellableCoroutine { continuation ->
        saveWebArchive(file.absolutePath, false) { path ->
            if (continuation.isActive) continuation.resume(path != null)
            else file.delete()
        }
    }
    if (!ok) throw IOException("无法创建网页归档")
}

internal fun MainActivity.copySharedDocument(document: SharedDocument, sensitive: Boolean) {
    val clip = ClipData.newUri(contentResolver, document.name, document.uri(this))
    if (sensitive) clip.description.extras = android.os.PersistableBundle().apply {
        putBoolean("android.content.extra.IS_SENSITIVE", true)
    }
    getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
}

internal class ShareDocumentSaver(private val activity: MainActivity) {
    private var pending: File? = null
    private val picker = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val file = pending
        pending = null
        val uri = result.data?.data
        if (file != null && result.resultCode == android.app.Activity.RESULT_OK && uri != null) {
            activity.lifecycleScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        file.inputStream().use { input ->
                            val output = activity.contentResolver.openOutputStream(uri,"wt") ?: throw IOException()
                            output.use { input.copyTo(it) }
                        }
                    }
                    activity.notice("文件已保存")
                } catch (cancel: CancellationException) { throw cancel }
                  catch (_: Exception) { activity.notice("无法写入所选位置") }
            }
        }
    }

    fun launch(document: SharedDocument) {
        if (pending != null) return
        pending = document.file
        try {
            picker.launch(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType(document.format.mime).putExtra(Intent.EXTRA_TITLE,document.name))
        } catch (_: android.content.ActivityNotFoundException) {
            pending = null
            activity.notice("无法打开文件选择器")
        }
    }

    fun restore(state: Bundle?) {
        val fileName = state?.getString("shareFileForSave") ?: return
        if (fileName.matches(Regex("[0-9a-f-]{36}\\.(pdf|mht)")))
            pending = File(File(activity.cacheDir,"shared-pages"),fileName).takeIf { it.isFile }
    }
    fun save(out: Bundle) { pending?.let { out.putString("shareFileForSave",it.name) } }
}
