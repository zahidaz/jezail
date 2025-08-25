package com.azzahid.jezail.core.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.Base64
import android.util.Log
import androidx.core.graphics.createBitmap
import java.io.ByteArrayOutputStream

class DrawableEncoder {

    companion object {
        private const val TAG = "DrawableEncoder"
        private const val ICON_DEFAULT_SIZE = 96
        private const val BITMAP_QUALITY = 90
    }

    fun encodeDrawableToBase64(drawable: Drawable): String? {
        return try {
            val bitmap = drawableToBitmap(drawable)
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, BITMAP_QUALITY, outputStream)
            val imageBytes = outputStream.toByteArray()
            "data:image/png;base64," + Base64.encodeToString(imageBytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to encode drawable to base64", e)
            null
        }
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