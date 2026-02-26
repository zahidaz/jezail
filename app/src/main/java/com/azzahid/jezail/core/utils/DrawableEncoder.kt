package com.azzahid.jezail.core.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.Base64
import android.util.Log
import androidx.core.graphics.createBitmap
import java.io.ByteArrayOutputStream
import java.util.Collections

class DrawableEncoder {

    companion object {
        private const val TAG = "DrawableEncoder"
        private const val ICON_DEFAULT_SIZE = 96
        private const val BITMAP_QUALITY = 90
        private const val MAX_CACHE_SIZE = 512
    }

    private val cache: MutableMap<String, String?> = Collections.synchronizedMap(
        object : LinkedHashMap<String, String?>(64, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String?>?): Boolean =
                size > MAX_CACHE_SIZE
        }
    )

    fun encodeDrawableToBase64(drawable: Drawable, cacheKey: String? = null): String? {
        cacheKey?.let { key ->
            cache[key]?.let { return it }
        }

        return try {
            val bitmap = drawableToBitmap(drawable)
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, BITMAP_QUALITY, outputStream)
            val imageBytes = outputStream.toByteArray()
            val encoded = "data:image/png;base64," + Base64.encodeToString(imageBytes, Base64.NO_WRAP)
            cacheKey?.let { cache[it] = encoded }
            encoded
        } catch (e: Exception) {
            Log.w(TAG, "Failed to encode drawable to base64", e)
            null
        }
    }

    fun invalidate(cacheKey: String) {
        cache.remove(cacheKey)
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return drawable.bitmap
        }

        val bitmap = createBitmap(
            drawable.intrinsicWidth.takeIf { it > 0 } ?: ICON_DEFAULT_SIZE,
            drawable.intrinsicHeight.takeIf { it > 0 } ?: ICON_DEFAULT_SIZE
        )

        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }
}
