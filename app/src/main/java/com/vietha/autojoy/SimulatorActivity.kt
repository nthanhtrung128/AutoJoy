package com.vietha.autojoy

import android.app.Activity
import android.os.Bundle
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import kotlin.math.hypot

/** Receives actual dispatchGesture input without opening a game or changing calibration. */
class SimulatorActivity : Activity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        setContentView(Pad())
    }
    override fun onPause() {
        BotAccessibilityService.instance?.stopAll("rời màn mô phỏng")
        super.onPause()
    }
    private inner class Pad : View(this@SimulatorActivity) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var pointer = -1
        private var dx = 0f
        private var dy = 0f
        private var attacks = 0
        private var heldSince = 0L
        private var heldMs = 0L
        private var releases = 0
        private val location = IntArray(2)
        private fun controls(): FloatArray {
            getLocationOnScreen(location)
            val (w, h) = ScreenUtil.realSize(context)
            val (jx, jy) = Prefs.joy(context)
            val (ax, ay) = Prefs.atk(context)
            return floatArrayOf(jx * w - location[0], jy * h - location[1],
                ax * w - location[0], ay * h - location[1])
        }
        override fun onDraw(c: Canvas) {
            c.drawColor(Color.rgb(15, 22, 34))
            val p = controls()
            val r = Prefs.radiusDp(context) * resources.displayMetrics.density
            paint.style = Paint.Style.FILL
            paint.color = Color.rgb(38, 65, 91)
            c.drawCircle(p[0], p[1], r + 12, paint)
            paint.color = Color.CYAN
            c.drawCircle(p[0] + dx, p[1] + dy, 20f, paint)
            paint.color = Color.rgb(187, 93, 62)
            c.drawCircle(p[2], p[3], 48f * resources.displayMetrics.density, paint)
            paint.color = Color.WHITE
            paint.textSize = 16f * resources.displayMetrics.scaledDensity
            c.drawText("MÔ PHỎNG — chạy T1–T5 bằng bảng nổi; Back để thoát", 20f, 45f, paint)
            val current = heldMs + if (pointer >= 0) android.os.SystemClock.uptimeMillis() - heldSince else 0
            c.drawText("Bấm đánh: $attacks | Giữ: ${current / 1000f}s | Nhả: $releases", 20f, 90f, paint)
            c.drawText("Vector: (${dx.toInt()}, ${dy.toInt()}) — chỉ xác nhận Android gửi thao tác", 20f, 135f, paint)
            if (pointer >= 0) postInvalidateDelayed(100)
        }
        override fun onTouchEvent(e: MotionEvent): Boolean {
            val p = controls()
            val r = Prefs.radiusDp(context) * resources.displayMetrics.density
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                    val i = e.actionIndex
                    val x = e.getX(i); val y = e.getY(i)
                    if (hypot(x - p[2], y - p[3]) < 48f * resources.displayMetrics.density) attacks++
                    else if (pointer == -1 && hypot(x - p[0], y - p[1]) < r + 30) {
                        pointer = e.getPointerId(i)
                        heldSince = android.os.SystemClock.uptimeMillis()
                        dx = x - p[0]; dy = y - p[1]
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    val i = e.findPointerIndex(pointer)
                    if (i >= 0) { dx = e.getX(i) - p[0]; dy = e.getY(i) - p[1] }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                    if (pointer >= 0 && (e.actionMasked == MotionEvent.ACTION_CANCEL ||
                        e.getPointerId(e.actionIndex) == pointer)) {
                        heldMs += android.os.SystemClock.uptimeMillis() - heldSince
                        pointer = -1; dx = 0f; dy = 0f; releases++
                    }
                }
            }
            invalidate()
            return true
        }
    }
}
