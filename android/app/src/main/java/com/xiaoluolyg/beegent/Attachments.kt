package com.xiaoluolyg.beegent

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

internal class Attachments(private val activity: Activity, private val log: Diagnostics) {
    private val directory = File(activity.cacheDir, "attachments").apply { mkdirs(); listFiles()?.forEach(File::delete) }
    private val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(120, TimeUnit.SECONDS).build()
    private val imageSlots = Semaphore(2)
    private fun filename(value: String) = value.replace(Regex("[\\\\/:*?\"<>|\\x00-\\x1f]"), "_").takeLast(120).ifBlank { "attachment" }
    private suspend fun fetch(file: Attachment, limit: Long): File = withContext(Dispatchers.IO) {
        require(file.available) { "服务端未提供可下载内容" }
        require(file.size <= limit) { "附件超过本次处理大小上限" }
        val operation = id()
        val target = File(directory, "${operation}-${filename(file.name)}")
        log.record("attachment.action.start", obj("operationId" to operation, "fileId" to file.id, "name" to file.name, "mime" to file.mime, "url" to file.url))
        try {
            if (file.base64.isNotEmpty()) {
                require(file.base64.length <= minOf(70L * 1024 * 1024, limit * 4 / 3 + 1024)) { "附件内容过大，请在电脑端保存" }
                val bytes = Base64.decode(file.base64.substringAfter(',', file.base64), Base64.DEFAULT)
                require(bytes.size.toLong() <= limit) { "附件超过本次处理大小上限" }
                target.writeBytes(bytes)
                log.record("attachment.base64.decoded", obj("operationId" to operation, "bytes" to bytes.size))
            } else {
                val request = Request.Builder().url(file.url).get().build()
                client.newCall(request).execute().use { response ->
                    require(response.isSuccessful) { "附件下载失败（HTTP ${response.code}）" }
                    val body = response.body ?: throw Exception("附件响应为空")
                    body.byteStream().use { input -> target.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024); var total = 0L
                        while (true) {
                            val n = input.read(buffer); if (n < 0) break
                            total += n; require(total <= limit) { "附件超过本次处理大小上限" }
                            output.write(buffer, 0, n)
                        }
                        log.record("attachment.http.response", obj("operationId" to operation, "status" to response.code, "bytes" to total))
                    } }
                }
            }
            target
        } catch (e: Exception) { target.delete(); log.record("attachment.action.failed", obj("operationId" to operation, "error" to e.message), "error"); throw e }
    }
    suspend fun image(file: Attachment, edge: Int): Bitmap = imageSlots.withPermit {
        try {
            val target = fetch(file, 32L * 1024 * 1024)
            try { withContext(Dispatchers.IO) {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(target.path, bounds)
                require(bounds.outWidth > 0 && bounds.outHeight > 0) { "无法解码图片" }
                var sample = 1; while (maxOf(bounds.outWidth, bounds.outHeight) / sample > edge * 2) sample *= 2
                BitmapFactory.decodeFile(target.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: throw Exception("无法解码图片")
            }.also { log.record("attachment.preview.ready", obj("fileId" to file.id, "action" to "image")) } }
            finally { target.delete() }
        } catch (e: Exception) { log.record("attachment.preview.failed", obj("fileId" to file.id, "error" to e.message), "error"); throw e }
    }
    suspend fun html(file: Attachment): String {
        try {
            val target = fetch(file, 4L * 1024 * 1024)
            return try { withContext(Dispatchers.IO) { target.readText(Charsets.UTF_8).removePrefix("\uFEFF") }
                .also { log.record("attachment.preview.ready", obj("fileId" to file.id, "action" to "html", "characters" to it.length)) } }
            finally { target.delete() }
        } catch (e: Exception) { log.record("attachment.preview.failed", obj("fileId" to file.id, "error" to e.message), "error"); throw e }
    }
    suspend fun save(file: Attachment, uri: Uri) {
        val target = fetch(file, 128L * 1024 * 1024)
        try { withContext(Dispatchers.IO) {
            val output = activity.contentResolver.openOutputStream(uri, "wt") ?: throw Exception("无法写入所选位置")
            output.use { target.inputStream().use { input -> input.copyTo(it) } }
            log.record("attachment.saved", obj("fileId" to file.id, "bytes" to target.length()))
        } } finally { target.delete() }
    }
    suspend fun handoff(file: Attachment, share: Boolean) {
        val target = fetch(file, 128L * 1024 * 1024)
        val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.files", target)
        val intent = if (share) Intent(Intent.ACTION_SEND).apply { type = file.mime; putExtra(Intent.EXTRA_STREAM, uri) }
        else Intent(Intent.ACTION_VIEW).apply { setDataAndType(uri, file.mime) }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            activity.startActivity(Intent.createChooser(intent, if (share) "转发附件" else "打开附件"))
            log.record(if (share) "attachment.share.returned" else "attachment.system_open.accepted", obj("fileId" to file.id))
        } catch (e: Exception) { target.delete(); throw e }
    }
}
