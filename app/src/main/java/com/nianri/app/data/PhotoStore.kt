package com.nianri.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.Executors

object PhotoStore {
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun fileFor(context: Context, name: String): File {
        return if (name.startsWith("/")) {
            File(name)
        } else {
            File(File(context.filesDir, "photos"), name)
        }
    }

    fun exists(context: Context, name: String?): Boolean {
        if (name.isNullOrBlank()) return false
        return fileFor(context, name).isFile
    }

    /**
     * 把用户选择的图片复制进应用私有目录，最长边限制在 2200px，
     * 修正 EXIF 方向并压缩。返回内部文件名。
     */
    private fun animatedExtensionByHeader(file: File): String? {
        return runCatching {
            val header = ByteArray(16)
            java.io.FileInputStream(file).use { input ->
                val read = input.read(header)
                if (read < 12) return@use null
            }
            if (header[0] == 'G'.code.toByte() &&
                header[1] == 'I'.code.toByte() &&
                header[2] == 'F'.code.toByte() &&
                header[3] == '8'.code.toByte()
            ) {
                return "gif"
            }
            if (header[0] == 'R'.code.toByte() &&
                header[1] == 'I'.code.toByte() &&
                header[2] == 'F'.code.toByte() &&
                header[3] == 'F'.code.toByte() &&
                header[8] == 'W'.code.toByte() &&
                header[9] == 'E'.code.toByte() &&
                header[10] == 'B'.code.toByte() &&
                header[11] == 'P'.code.toByte()
            ) {
                return "webp"
            }
            null
        }.getOrNull()
    }

    fun importImage(
        context: Context,
        uri: Uri,
        onFinished: (name: String?, error: String?) -> Unit,
    ) {
        io.execute {
            var decoded: Bitmap? = null
            var output: File? = null
            var temp: File? = null
            try {
                val resolver = context.contentResolver
                // 一些系统照片提供者返回的流不支持 BitmapFactory 的
                // 二次读取/边界探测，先复制到缓存文件再解码最稳。
                temp = File.createTempFile("photo_import_", ".tmp", context.cacheDir)
                var copied = 0L
                resolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(temp).use { stream ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            copied += read
                            if (copied > 100L * 1024 * 1024) {
                                throw IllegalArgumentException("这张图片太大了")
                            }
                            stream.write(buffer, 0, read)
                        }
                        stream.flush()
                    }
                } ?: throw IllegalArgumentException("无法读取这张图片")
                if (copied == 0L) throw IllegalArgumentException("图片内容为空")

                val mime = resolver.getType(uri).orEmpty()
                val displayName = queryDisplayName(resolver, uri)

                val animatedExt = animatedExtensionByHeader(temp) ?: when {
                    mime == "image/gif" || displayName.endsWith(".gif", true) -> "gif"
                    mime == "image/webp" || displayName.endsWith(".webp", true) -> "webp"
                    else -> null
                }
                if (animatedExt != null) {
                    val dir = File(context.filesDir, "photos").apply { mkdirs() }
                    val animated = File(
                        dir,
                        "anim_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}.$animatedExt",
                    )
                    temp.copyTo(animated, overwrite = true)
                    output = animated
                    main.post { onFinished(animated.name, null) }
                    return@execute
                }

                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(temp.absolutePath, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                    throw IllegalArgumentException("图片格式暂不支持")
                }

                var sample = 1
                val longest = maxOf(bounds.outWidth, bounds.outHeight)
                while (longest / sample > 2200) sample *= 2

                val options = BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                decoded = BitmapFactory.decodeFile(temp.absolutePath, options)
                    ?: throw IllegalArgumentException("图片读取失败")

                var oriented = applyExifOrientation(temp, decoded)
                if (oriented !== decoded) {
                    decoded.recycle()
                }
                decoded = oriented

                val flattened = Bitmap.createBitmap(
                    oriented.width,
                    oriented.height,
                    Bitmap.Config.ARGB_8888,
                )
                val canvas = Canvas(flattened)
                canvas.drawColor(Color.rgb(246, 241, 232))
                canvas.drawBitmap(oriented, 0f, 0f, null)
                if (flattened !== oriented) oriented.recycle()
                decoded = flattened

                val dir = File(context.filesDir, "photos").apply { mkdirs() }
                output = File(dir, "photo_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}.jpg")
                FileOutputStream(output).use { stream ->
                    flattened.compress(Bitmap.CompressFormat.JPEG, 88, stream)
                    stream.flush()
                }
                flattened.recycle()
                decoded = null
                val done = output
                main.post { onFinished(done.name, null) }
            } catch (e: Throwable) {
                output?.delete()
                decoded?.recycle()
                val message = when (e) {
                    is OutOfMemoryError -> "这张图片太大了，请换一张试试"
                    is SecurityException -> "没有读取这张图片的权限"
                    else -> e.message ?: "照片处理失败"
                }
                main.post { onFinished(null, message) }
            } finally {
                temp?.delete()
            }
        }
    }

    fun isAnimated(name: String?): Boolean {
        if (name.isNullOrBlank()) return false
        return name.endsWith(".gif", true) || name.endsWith(".webp", true)
    }

    /**
     * 只有 API 28+ 才能用 ImageDecoder 播放动画；低版本回退到第一帧。
     */
    fun loadAnimated(
        context: Context,
        name: String,
        maxSide: Int,
        onLoaded: (Drawable?) -> Unit,
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            main.post { onLoaded(null) }
            return
        }
        io.execute {
            try {
                val file = fileFor(context, name)
                if (!file.isFile) {
                    main.post { onLoaded(null) }
                    return@execute
                }
                val source = ImageDecoder.createSource(file)
                val drawable = ImageDecoder.decodeDrawable(source) { decoder, info, _ ->
                    val longest = maxOf(info.size.width, info.size.height)
                    if (longest > maxSide) {
                        val scale = maxSide.toFloat() / longest
                        decoder.setTargetSize(
                            (info.size.width * scale).toInt().coerceAtLeast(1),
                            (info.size.height * scale).toInt().coerceAtLeast(1),
                        )
                    }
                }
                if (drawable is AnimatedImageDrawable) {
                    drawable.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
                }
                main.post { onLoaded(drawable) }
            } catch (_: Throwable) {
                main.post { onLoaded(null) }
            }
        }
    }

    private fun queryDisplayName(
        resolver: android.content.ContentResolver,
        uri: Uri,
    ): String {
        var name = ""
        runCatching {
            resolver.query(
                uri,
                arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) name = cursor.getString(index).orEmpty()
                }
            }
        }
        return name
    }

    fun loadImage(
        context: Context,
        name: String?,
        width: Int,
        height: Int,
        onLoaded: (Bitmap?) -> Unit,
    ) {
        if (name.isNullOrBlank()) {
            onLoaded(null)
            return
        }
        val reqW = width.coerceAtLeast(1)
        val reqH = height.coerceAtLeast(1)
        val key = "$name@${reqW / 64}x${reqH / 64}"
        cache.get(key)?.let {
            onLoaded(it)
            return
        }
        io.execute {
            val file = fileFor(context, name)
            if (!file.isFile) {
                main.post { onLoaded(null) }
                return@execute
            }
            val bitmap = try {
                decodeSampled(file, reqW, reqH)
            } catch (_: Throwable) {
                null
            }
            if (bitmap != null) cache.put(key, bitmap)
            main.post { onLoaded(bitmap) }
        }
    }

    fun delete(context: Context, name: String?) {
        if (name.isNullOrBlank()) return
        val file = fileFor(context, name)
        if (file.exists()) file.delete()
        val keys = cache.snapshot().keys.filter { it.startsWith("$name@") }
        keys.forEach(cache::remove)
    }

    /**
     * 清理上次进程中断后可能留下的未引用照片。只处理超过 10 分钟的文件，
     * 避免误伤正在编辑中的新照片。
     */
    fun cleanupOrphans(context: Context, referencedNames: Set<String>) {
        io.execute {
            val dir = File(context.filesDir, "photos")
            val cutoff = System.currentTimeMillis() - 10 * 60 * 1000L
            dir.listFiles()?.forEach { file ->
                if (file.isFile &&
                    file.name !in referencedNames &&
                    file.lastModified() < cutoff
                ) {
                    if (file.delete()) {
                        val keys = cache.snapshot().keys.filter { it.startsWith("${file.name}@") }
                        keys.forEach(cache::remove)
                    }
                }
            }
        }
    }

    private fun applyExifOrientation(file: File, bitmap: Bitmap): Bitmap {
        val orientation = try {
            ExifInterface(file.absolutePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        } catch (_: Throwable) {
            ExifInterface.ORIENTATION_NORMAL
        }
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
                matrix.setRotate(180f)
                matrix.postScale(-1f, 1f)
            }
            else -> return bitmap
        }
        val transformed = Bitmap.createBitmap(
            bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true,
        )
        return transformed
    }

    private fun decodeSampled(file: File, reqWidth: Int, reqHeight: Int): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= reqWidth &&
            bounds.outHeight / (sample * 2) >= reqHeight
        ) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeFile(file.absolutePath, options)
            ?: throw IllegalStateException("decode failed")
    }
}
