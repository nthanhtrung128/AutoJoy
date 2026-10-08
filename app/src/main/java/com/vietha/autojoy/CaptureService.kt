package com.vietha.autojoy

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ContentValues
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min

/**
 * Chụp màn hình bằng MediaProjection (chạy nền, có thông báo).
 * Ảnh được thu nhỏ tối đa 1280px để xử lý nhanh, tiết kiệm pin.
 */
class CaptureService : Service() {

    companion object {
        @Volatile
        var instance: CaptureService? = null
        const val EXTRA_CODE = "code"
        const val EXTRA_DATA = "data"
    }

    private var projection: MediaProjection? = null
    private var vdisplay: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var rw = 0
    private var rh = 0
    private val thread = HandlerThread("autojoy-capture").apply { start() }
    private val bg = Handler(thread.looper)
    private val main = Handler(Looper.getMainLooper())
    private var pending: ((String) -> Unit)? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startAsForeground()
        if (projection != null) return START_NOT_STICKY

        val code = intent?.getIntExtra(EXTRA_CODE, 0) ?: 0
        @Suppress("DEPRECATION")
        val data: Intent? = intent?.getParcelableExtra(EXTRA_DATA)
        if (data == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val p = mpm.getMediaProjection(code, data)
        if (p == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        // Android 14+: phải đăng ký callback trước khi tạo VirtualDisplay
        p.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                main.post {
                    release()
                    stopSelf()
                }
            }
        }, bg)
        projection = p
        bg.post { setupDisplay() }
        instance = this
        return START_NOT_STICKY
    }

    private fun startAsForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("capture", "Chụp màn hình", NotificationManager.IMPORTANCE_LOW)
        )
        val n = Notification.Builder(this, "capture")
            .setContentTitle("AutoJoy đang chụp màn hình")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(1, n)
        }
    }

    /** Tạo / đổi kích thước màn hình ảo theo hướng xoay hiện tại (chạy trên luồng nền). */
    private fun setupDisplay() {
        val p = projection ?: return
        val (w, h) = ScreenUtil.realSize(this)
        val scale = min(1f, 1280f / max(w, h).toFloat())
        val cw = ((w * scale).toInt() / 2) * 2
        val ch = ((h * scale).toInt() / 2) * 2
        val dpi = resources.displayMetrics.densityDpi
        val newReader = ImageReader.newInstance(cw, ch, PixelFormat.RGBA_8888, 2)
        newReader.setOnImageAvailableListener({ r -> onImage(r) }, bg)
        val d = vdisplay
        if (d == null) {
            vdisplay = p.createVirtualDisplay(
                "autojoy", cw, ch, dpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                newReader.surface, null, bg
            )
        } else {
            d.resize(cw, ch, dpi)
            d.surface = newReader.surface
        }
        reader?.close()
        reader = newReader
        rw = cw
        rh = ch
    }

    /** Lấy khung hình kế tiếp, phân tích và lưu ảnh. cb chạy trên luồng giao diện. */
    fun grab(cb: (String) -> Unit) {
        bg.post {
            val (w, h) = ScreenUtil.realSize(this)
            if ((w > h) != (rw > rh)) setupDisplay()
            pending = cb
            bg.postDelayed({
                val c = pending
                if (c != null) {
                    pending = null
                    main.post { c("không có khung hình mới trong 2 giây — thử lại khi game đang chạy") }
                }
            }, 2_000)
        }
    }

    private fun onImage(r: ImageReader) {
        val img = try {
            r.acquireLatestImage()
        } catch (e: Exception) {
            null
        } ?: return
        val cb: (String) -> Unit = pending ?: run {
            img.close()
            return
        }
        pending = null
        val result = try {
            val plane = img.planes[0]
            val fullW = plane.rowStride / plane.pixelStride
            val full = Bitmap.createBitmap(fullW, img.height, Bitmap.Config.ARGB_8888)
            full.copyPixelsFromBuffer(plane.buffer)
            val bmp = Bitmap.createBitmap(full, 0, 0, img.width, img.height)
            analyze(bmp)
        } catch (e: Exception) {
            "lỗi đọc ảnh: ${e.message}"
        } finally {
            img.close()
        }
        main.post { cb(result) }
    }

    private fun analyze(bmp: Bitmap): String {
        var sum = 0L
        var lit = 0
        var n = 0
        val stepX = max(1, bmp.width / 64)
        val stepY = max(1, bmp.height / 36)
        for (y in 0 until bmp.height step stepY) {
            for (x in 0 until bmp.width step stepX) {
                val c = bmp.getPixel(x, y)
                val l = (Color.red(c) * 299 + Color.green(c) * 587 + Color.blue(c) * 114) / 1000
                sum += l
                if (l > 20) lit++
                n++
            }
        }
        val avg = if (n > 0) sum / n else 0
        val pct = if (n > 0) lit * 100 / n else 0
        val saved = save(bmp)
        val verdict = if (pct < 5) "ẢNH ĐEN → game chặn chụp màn hình" else "OK, thấy được game"
        return "${bmp.width}x${bmp.height}, độ sáng TB $avg, $pct% điểm có hình → $verdict. Lưu: $saved"
    }

    private fun save(bmp: Bitmap): String {
        val name = "autojoy_${System.currentTimeMillis()}.png"
        return try {
            if (Build.VERSION.SDK_INT >= 29) {
                val cv = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, name)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/AutoJoy")
                }
                val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv)
                    ?: return "không lưu được"
                contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                "Ảnh/Pictures/AutoJoy/$name"
            } else {
                val f = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), name)
                FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                f.absolutePath
            }
        } catch (e: Exception) {
            Log.w("AutoJoy", "save: ${e.message}")
            "không lưu được (${e.message})"
        }
    }

    private fun release() {
        instance = null
        val p = projection
        projection = null
        try {
            vdisplay?.release()
        } catch (e: Exception) {
            Log.w("AutoJoy", "release display: ${e.message}")
        }
        vdisplay = null
        try {
            reader?.close()
        } catch (e: Exception) {
            Log.w("AutoJoy", "release reader: ${e.message}")
        }
        reader = null
        try {
            p?.stop()
        } catch (e: Exception) {
            Log.w("AutoJoy", "stop projection: ${e.message}")
        }
    }

    override fun onDestroy() {
        release()
        thread.quitSafely()
        super.onDestroy()
    }
}
