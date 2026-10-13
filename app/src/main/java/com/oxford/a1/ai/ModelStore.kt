package com.oxford.a1.ai

import android.content.Context
import android.net.Uri
import android.os.StatFs
import android.provider.OpenableColumns
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** A single replaceable model, kept outside Android cloud backup. Import commits atomically. */
class ModelStore(private val context: Context) {
    private val directory = File(context.noBackupFilesDir, "models").apply { mkdirs() }
    val model = File(directory, "active.gguf")
    private val label = File(directory, "name.txt")
    fun name(): String? = if (model.isFile) runCatching { label.readText() }.getOrDefault("本地 GGUF") else null
    fun delete() { check(!model.exists() || model.delete()) { "无法删除模型文件" }; label.delete() }

    suspend fun import(uri: Uri, progress: (Long) -> Unit) {
        var displayName = "本地 GGUF"
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) displayName = it.getString(0).orEmpty().take(160)
        }
        val partial = File(directory, "import.part")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                copyValidated(input, partial, { StatFs(directory.path).availableBytes }, progress)
            } ?: error("无法读取文件，请重新选择")
            currentCoroutineContext().ensureActive()
            // Same-filesystem atomic move: never expose a partially copied active model.
            Files.move(partial.toPath(), model.toPath(), StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING)
            runCatching { label.writeText(displayName) }
        } finally { partial.delete() }
    }

    companion object {
        const val RESERVE = 128L * 1024 * 1024
        const val MAX_BYTES = 8L * 1024 * 1024 * 1024
        suspend fun copyValidated(input: InputStream, target: File, available: () -> Long, progress: (Long) -> Unit) {
            val header = ByteArray(24)
            var read = 0
            while (read < header.size) {
                currentCoroutineContext().ensureActive()
                val n = input.read(header, read, header.size - read)
                require(n > 0) { "文件不完整：需要单文件 GGUF 模型" }
                read += n
            }
            require(header.copyOfRange(0, 4).contentEquals(byteArrayOf(71, 71, 85, 70))) { "不是 GGUF 文件；不能导入 safetensors 或 Ollama 模型名称" }
            val data = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
            require(data.getInt(4) in 2..3 && data.getLong(8) > 0 && data.getLong(16) > 0) { "不支持或损坏的 GGUF 文件头" }
            target.outputStream().use { output ->
                require(available() > RESERVE + header.size) { "存储空间不足；更新模型需要同时容纳新旧文件" }
                output.write(header)
                val buffer = ByteArray(1024 * 1024)
                var total = header.size.toLong()
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val n = input.read(buffer)
                    if (n < 0) break
                    if (n == 0) continue
                    total += n
                    require(total <= MAX_BYTES) { "模型超过 8 GiB，请选择量化小模型" }
                    require(available() > RESERVE + n) { "存储空间不足；原模型仍然保留" }
                    output.write(buffer, 0, n)
                    progress(total)
                }
                require(total > 24) { "GGUF 文件没有模型数据" }
                output.fd.sync()
            }
        }
    }
}
