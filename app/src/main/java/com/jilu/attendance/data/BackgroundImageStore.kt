package com.jilu.attendance.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import kotlin.math.max

/**
 * 自定义背景图：选来的照片压缩后写到应用私有目录，只保留一张。
 *
 * 读取相册 Uri 在国产 ROM 上并不总是稳：有的相册 provider 只给 FileDescriptor、
 * 不给 stream，或者只允许打开一次。所以这里先把原始字节整个拷到缓存文件，
 * 后续解码 / 压缩都基于本地文件，一次性读取、不留二次打开的坑。
 */
class BackgroundImageStore(private val context: Context) {

    companion object {
        const val FILE_NAME = "background.jpg"
        private const val TAG = "BackgroundImageStore"
        private const val MAX_SIDE = 1600
        private const val JPEG_QUALITY = 85
        private const val RAW_TEMP = "background_pick.tmp"
        private const val JPEG_TEMP = "background_tmp.jpg"
    }

    fun file(name: String = FILE_NAME): File = File(context.filesDir, name)

    fun exists(name: String): Boolean = File(context.filesDir, name).exists()

    fun load(name: String, maxSide: Int = MAX_SIDE): Bitmap? {
        val target = File(context.filesDir, name)
        if (!target.exists()) return null

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(target.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxSide)
        }
        return runCatching { BitmapFactory.decodeFile(target.absolutePath, options) }.getOrNull()
    }

    /** 从相册选中的 Uri 复制、缩放并保存，返回保存后的文件名。 */
    suspend fun saveFromUri(uri: Uri): String = withContext(Dispatchers.IO) {
        val raw = File(context.cacheDir, RAW_TEMP)
        try {
            copyUriToFile(uri, raw)
            val bitmap = decodeSampled(raw)
            writeJpeg(bitmap, file(FILE_NAME))
            FILE_NAME
        } catch (throwable: Throwable) {
            Log.e(TAG, "saveFromUri failed for $uri", throwable)
            throw throwable
        } finally {
            if (raw.exists()) {
                raw.delete()
            }
        }
    }

    fun delete(name: String) {
        val target = File(context.filesDir, name)
        if (target.exists()) {
            target.delete()
        }
    }

    // ---- 读取 Uri ----

    private fun copyUriToFile(uri: Uri, target: File) {
        val input = openStream(uri)
        input.use { source ->
            target.outputStream().use { output ->
                source.copyTo(output)
            }
        }
        if (target.length() <= 0L) {
            throw IOException("所选文件是空的，可能不是一张有效图片")
        }
    }

    /** 先试 openInputStream，失败再退回 FileDescriptor；两条路都失败才报错。 */
    private fun openStream(uri: Uri): InputStream {
        try {
            context.contentResolver.openInputStream(uri)?.let { return it }
        } catch (security: SecurityException) {
            throw IOException("系统拒绝了读取这张图片的权限（SecurityException），请换一张或在文件管理器里先复制到本机")
        } catch (io: IOException) {
            // 落到 FileDescriptor 兜底
        }

        try {
            val descriptor = context.contentResolver.openFileDescriptor(uri, "r")
            if (descriptor != null) {
                return FileInputStream(descriptor.fileDescriptor)
            }
        } catch (security: SecurityException) {
            throw IOException("系统拒绝了读取这张图片的权限（SecurityException），请换一张或在文件管理器里先复制到本机")
        } catch (io: IOException) {
            throw IOException("无法打开所选图片：${io.message}")
        }

        throw IOException("无法读取所选图片，请换一张，或从「文件」里挑一张本地图片")
    }

    // ---- 解码与压缩 ----

    private fun decodeSampled(source: File): Bitmap {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val bitmap = runCatching {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(source)) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.setTargetSampleSize(sampleSize(info.size.width, info.size.height, MAX_SIDE))
                }
            }.getOrNull()
            if (bitmap != null) return bitmap
        }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw IOException("无法解码这张图片，可能是本机不支持的格式（例如 HEIC），换一张 JPG/PNG 试试")
        }

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, MAX_SIDE)
        }
        val bitmap = BitmapFactory.decodeFile(source.absolutePath, options)
            ?: throw IOException("无法解码这张图片，可能是本机不支持的格式（例如 HEIC），换一张 JPG/PNG 试试")

        return applyExifRotation(source, bitmap)
    }

    private fun sampleSize(width: Int, height: Int, maxSide: Int): Int {
        var sample = 1
        val longest = max(width, height)
        while (longest / sample > maxSide) {
            sample *= 2
        }
        return sample
    }

    /** 相机拍的图常带旋转信息，BitmapFactory 不认，得手动转正，否则背景会躺着。 */
    private fun applyExifRotation(source: File, bitmap: Bitmap): Bitmap {
        val orientation = runCatching {
            ExifInterface(source.absolutePath)
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return bitmap
        }

        val rotated = runCatching {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }.getOrNull() ?: return bitmap

        if (rotated !== bitmap) {
            bitmap.recycle()
        }
        return rotated
    }

    private fun writeJpeg(bitmap: Bitmap, target: File) {
        val temp = File(context.filesDir, JPEG_TEMP)
        try {
            FileOutputStream(temp).use { output ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)) {
                    throw IOException("背景图压缩失败")
                }
            }
        } finally {
            bitmap.recycle()
        }

        if (target.exists() && !target.delete()) {
            temp.delete()
            throw IOException("无法覆盖旧背景图")
        }
        if (!temp.renameTo(target)) {
            temp.delete()
            throw IOException("背景图保存失败，可能是存储空间不足")
        }
    }
}
