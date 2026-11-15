package site.hikomorebi.wardrobe.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.net.Uri
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object PhotoProcessor {
    fun captureFile(context: Context): File =
        File(context.cacheDir, "photos")
            .apply { mkdirs() }
            .let { File(it, "capture-${UUID.randomUUID()}.jpg") }

    suspend fun prepare(context: Context, uri: Uri): File =
        withContext(Dispatchers.IO) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            // ImageDecoder applies EXIF orientation before resizing. Re-encoding drops source
            // metadata.
            val decoded =
                try {
                    ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                        val scale = minOf(1f, 2048f / maxOf(info.size.width, info.size.height))
                        decoder.setTargetSize(
                            maxOf(1, (info.size.width * scale).toInt()),
                            maxOf(1, (info.size.height * scale).toInt()),
                        )
                    }
                } catch (error: java.io.IOException) {
                    throw IllegalArgumentException("无法读取这张图片，请重新选择 JPEG、PNG、WebP 或 HEIC 图片", error)
                }
            val opaque = Bitmap.createBitmap(decoded.width, decoded.height, Bitmap.Config.ARGB_8888)
            Canvas(opaque).apply {
                drawColor(Color.WHITE)
                drawBitmap(decoded, 0f, 0f, null)
            }
            val result =
                File(context.cacheDir, "photos")
                    .apply { mkdirs() }
                    .let { File(it, "upload-${UUID.randomUUID()}.jpg") }
            try {
                result.outputStream().use {
                    check(opaque.compress(Bitmap.CompressFormat.JPEG, 85, it)) { "无法处理这张图片，请更换图片" }
                }
                result
            } catch (error: java.io.IOException) {
                result.delete()
                throw IllegalArgumentException("无法保存处理后的图片，请检查手机存储空间", error)
            } finally {
                decoded.recycle()
                opaque.recycle()
            }
        }

    fun clean(context: Context, all: Boolean = false) {
        val cutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1000
        File(context.cacheDir, "photos")
            .listFiles()
            ?.filter { all || it.lastModified() < cutoff }
            ?.forEach { it.delete() }
    }
}
