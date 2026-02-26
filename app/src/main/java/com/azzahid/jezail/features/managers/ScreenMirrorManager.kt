package com.azzahid.jezail.features.managers

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.HandlerThread
import android.util.DisplayMetrics
import com.azzahid.jezail.JezailApp
import com.topjohnwu.superuser.Shell
import java.io.ByteArrayOutputStream

object ScreenMirrorManager {
    @Volatile var latestFrame: ByteArray? = null; private set
    @Volatile var isActive: Boolean = false; private set

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var handlerThread: HandlerThread? = null
    private var handler: Handler? = null
    private var quality: Int = 50

    fun autoGrant() {
        Shell.cmd("appops set com.azzahid.jezail PROJECT_MEDIA allow").exec()
    }

    fun start(resultCode: Int, data: Intent, metrics: DisplayMetrics, quality: Int = 50, scale: Float = 0.5f) {
        stop()
        this.quality = quality

        val mpm = JezailApp.appContext.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = mpm.getMediaProjection(resultCode, data)

        val captureW = (metrics.widthPixels * scale).toInt().coerceAtLeast(1)
        val captureH = (metrics.heightPixels * scale).toInt().coerceAtLeast(1)
        val density = metrics.densityDpi

        handlerThread = HandlerThread("ScreenMirror").also { it.start() }
        handler = Handler(handlerThread!!.looper)

        imageReader = ImageReader.newInstance(captureW, captureH, PixelFormat.RGBA_8888, 2)
        imageReader!!.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
            try {
                val plane = image.planes[0]
                val buffer = plane.buffer
                val pixelStride = plane.pixelStride
                val rowStride = plane.rowStride
                val rowPadding = rowStride - pixelStride * captureW

                val bitmapWidth = captureW + rowPadding / pixelStride
                val bitmap = Bitmap.createBitmap(bitmapWidth, captureH, Bitmap.Config.ARGB_8888)
                bitmap.copyPixelsFromBuffer(buffer)

                val finalBitmap = if (bitmapWidth != captureW) {
                    val c = Bitmap.createBitmap(bitmap, 0, 0, captureW, captureH)
                    bitmap.recycle()
                    c
                } else bitmap

                val out = ByteArrayOutputStream()
                finalBitmap.compress(Bitmap.CompressFormat.JPEG, this.quality, out)
                finalBitmap.recycle()
                latestFrame = out.toByteArray()
            } finally {
                image.close()
            }
        }, handler)

        virtualDisplay = mediaProjection!!.createVirtualDisplay(
            "JezailMirror",
            captureW, captureH, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader!!.surface, null, null
        )

        isActive = true
    }

    fun stop() {
        isActive = false
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
        handlerThread?.quitSafely()
        virtualDisplay = null
        imageReader = null
        mediaProjection = null
        handler = null
        handlerThread = null
        latestFrame = null
    }
}
