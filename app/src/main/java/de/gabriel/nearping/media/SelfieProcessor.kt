package de.gabriel.nearping.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.core.graphics.scale
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.min

object SelfieProcessor {
    const val MAX_SELFIE_BYTES = 20_000

    fun createWireThumbnail(source: File): ByteArray {
        val decoded = requireNotNull(BitmapFactory.decodeFile(source.absolutePath)) {
            "Das Foto konnte nicht gelesen werden."
        }
        val oriented = rotateFromExif(decoded, source)
        val cropSize = min(oriented.width, oriented.height)
        val cropped = Bitmap.createBitmap(
            oriented,
            (oriented.width - cropSize) / 2,
            (oriented.height - cropSize) / 2,
            cropSize,
            cropSize,
        )

        var best: ByteArray? = null
        for (edge in listOf(320, 256, 192, 160, 128)) {
            val scaled = cropped.scale(edge, edge)
            for (quality in listOf(86, 76, 66, 56, 46, 36)) {
                val output = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, quality, output)
                val candidate = output.toByteArray()
                if (best == null || candidate.size < best.size) best = candidate
                if (candidate.size <= MAX_SELFIE_BYTES) {
                    recycleDistinct(decoded, oriented, cropped, scaled)
                    return candidate
                }
            }
            if (scaled !== cropped) scaled.recycle()
        }

        recycleDistinct(decoded, oriented, cropped)
        return requireNotNull(best)
    }

    private fun rotateFromExif(bitmap: Bitmap, source: File): Bitmap {
        val orientation = runCatching {
            ExifInterface(source).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
        }
        if (matrix.isIdentity) return bitmap
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun recycleDistinct(vararg bitmaps: Bitmap) {
        bitmaps.distinctBy(System::identityHashCode).forEach { bitmap ->
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }
}
