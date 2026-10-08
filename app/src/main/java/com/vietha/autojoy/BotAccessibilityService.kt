package com.vietha.autojoy

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.PointF
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Dịch vụ Trợ năng: hiện bảng điều khiển nổi trên game và chạy các bài test P0.
 *
 * T1 Bấm      – bấm nút tấn công 3 lần (kiểm tra game có nhận chạm giả lập không)
 * T2 Giữ      – giữ joystick sang phải 3 giây (kiểm tra giữ được joystick)
 * T3 Xoay     – giữ joystick và xoay vòng 6 giây (kiểm tra đổi hướng khi đang giữ)
 * T4 Joy+Đánh – vừa giữ joystick trái/phải vừa bấm tấn công (kiểm tra 2 ngón cùng lúc)
 * T5 Zigzag   – zigzag chếch xuống 20 giây như bot thật trong Mê Thành Pandora
 * T6 Chụp     – chụp màn hình game (kiểm tra game có chặn chụp không)
 */
class BotAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: BotAccessibilityService? = null
    }

    private val ui = Handler(Looper.getMainLooper())
    private lateinit var wm: WindowManager
    private lateinit var engine: GestureEngine
    private var panel: View? = null
    private var body: View? = null
    private var logView: TextView? = null
    private var calibView: View? = null
    private var driver: JoystickDriver? = null
    private val gate = SessionGate()
    private val tasks = Handler(Looper.getMainLooper())
    private var activeName: String? = null
    private var destroyed = false
    private val logLines = ArrayDeque<String>()

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        engine = GestureEngine(this) { log(it) }
        showPanel()
        val (w, h) = ScreenUtil.realSize(this)
        log("Sẵn sàng. Màn hình ${w}x$h. Mở game rồi bấm Hiệu chỉnh.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {
        stopAll()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        cleanup()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        cleanup()
        super.onDestroy()
    }

    private fun cleanup() {
        destroyed = true
        stopAll()
        if (::wm.isInitialized) {
            removeView(calibView)
            removeView(panel)
        }
        calibView = null
        panel = null
        instance = null
    }

    private fun removeView(v: View?) {
        if (v == null) return
        try {
            wm.removeView(v)
        } catch (e: Exception) {
            Log.w("AutoJoy", "removeView: ${e.message}")
        }
    }

    // ---------------------------------------------------------------- log

    fun log(msg: String) {
        Log.i("AutoJoy", msg)
        ui.post {
            logLines.addLast(msg)
            while (logLines.size > 4) logLines.removeFirst()
            logView?.text = logLines.joinToString("\n")
        }
    }

    // ---------------------------------------------------------------- UI

    private fun dp(v: Float): Float = v * resources.displayMetrics.density
    private fun dpi(v: Int): Int = dp(v.toFloat()).toInt()

    private fun btn(label: String, color: Int = Color.rgb(60, 60, 78), onClick: () -> Unit): Button =
        Button(this).apply {
            text = label
            textSize = 11f
            isAllCaps = false
            setTextColor(Color.WHITE)
            minWidth = 0
            minimumWidth = 0
            minHeight = 0
            minimumHeight = 0
            setPadding(dpi(8), dpi(5), dpi(8), dpi(5))
            setBackgroundColor(color)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dpi(2), dpi(2), dpi(2), dpi(2)) }
            setOnClickListener { onClick() }
        }

    private fun row(vararg views: View): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            views.forEach { addView(it) }
        }

    private fun showPanel() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.argb(215, 25, 25, 32))
            setPadding(dpi(6), dpi(2), dpi(6), dpi(6))
        }
        val title = TextView(this).apply {
            text = "✥ AutoJoy P0.1"
            setTextColor(Color.WHITE)
            textSize = 13f
            setPadding(dpi(4), dpi(8), dpi(14), dpi(8))
        }
        root.addView(row(
            title,
            btn("–") { toggleBody() },
            btn("DỪNG", Color.rgb(190, 45, 45)) { stopAll() }
        ))

        val b = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        b.addView(row(
            btn("Hiệu chỉnh", Color.rgb(40, 110, 170)) { calibrate() },
            btn("R−") { changeRadius(-10f) },
            btn("R+") { changeRadius(10f) }
        ))
        b.addView(row(
            btn("T1 Bấm") { testTap() },
            btn("T2 Giữ") { testHold() },
            btn("T3 Xoay") { testCircle() }
        ))
        b.addView(row(
            btn("T4 Joy+Đánh") { testJoyAttack() },
            btn("T5 Zigzag") { testZigzag() },
            btn("T6 Chụp") { testCapture() }
        ))
        val lv = TextView(this).apply {
            setTextColor(Color.rgb(170, 255, 170))
            textSize = 10f
            maxWidth = dpi(270)
            setPadding(dpi(2), dpi(4), dpi(2), 0)
        }
        b.addView(lv)
        root.addView(b)
        body = b
        logView = lv

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dpi(200)
            y = dpi(4)
        }

        // Kéo tiêu đề để di chuyển bảng (đừng để bảng che joystick / nút tấn công)
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        title.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY; startX = lp.x; startY = lp.y
                }
                MotionEvent.ACTION_MOVE -> {
                    lp.x = startX + (e.rawX - downX).toInt()
                    lp.y = startY + (e.rawY - downY).toInt()
                    wm.updateViewLayout(root, lp)
                }
            }
            true
        }

        wm.addView(root, lp)
        panel = root
    }

    private fun toggleBody() {
        val b = body ?: return
        b.visibility = if (b.visibility == View.VISIBLE) View.GONE else View.VISIBLE
    }

    // ---------------------------------------------------------------- helpers

    private fun available(): Boolean {
        if (destroyed) return false
        if (gate.isBusy || driver?.isRunning == true || calibView != null) {
            log("Đang có tác vụ. Bấm DỪNG và chờ nhả joystick trước khi chạy bài khác.")
            return false
        }
        return true
    }

    private fun begin(name: String, durationMs: Long): Long? {
        if (!available()) return null
        val token = gate.begin() ?: return null
        activeName = name
        tasks.postDelayed({ if (gate.owns(token)) stopAll("hết giới hạn phiên") }, durationMs + 1_000)
        return token
    }

    private fun complete(token: Long, message: String) {
        if (!gate.finish(token)) return
        tasks.removeCallbacksAndMessages(null)
        activeName = null
        Prefs.record(this, message)
        log(message)
    }

    fun stopAll(reason: String = "người dùng dừng") {
        val name = activeName
        gate.cancel()
        tasks.removeCallbacksAndMessages(null)
        activeName = null
        driver?.stop()
        CaptureService.instance?.cancelGrab()
        removeView(calibView)
        calibView = null
        val message = if (name != null) "$name: đã dừng ($reason)" else "Đã hủy tác vụ chờ; đang nhả joystick nếu có."
        if (name != null) Prefs.record(this, message)
        log(message)
    }

    private fun fmt(p: PointF) = "(${p.x.toInt()}, ${p.y.toInt()})"

    /** Toạ độ pixel hiện tại của joystick, nút tấn công và bán kính kéo. */
    private fun points(): Triple<PointF, PointF, Float> {
        val (w, h) = ScreenUtil.realSize(this)
        val (jx, jy) = Prefs.joy(this)
        val (ax, ay) = Prefs.atk(this)
        return Triple(PointF(jx * w, jy * h), PointF(ax * w, ay * h), dp(Prefs.radiusDp(this)))
    }

    private fun changeRadius(delta: Float) {
        val r = (Prefs.radiusDp(this) + delta).coerceIn(20f, 200f)
        Prefs.setRadiusDp(this, r)
        log("Độ kéo joystick = ${r.toInt()}dp")
    }

    // ---------------------------------------------------------------- hiệu chỉnh

    private fun calibrate() {
        if (!available()) return
        removeView(calibView)
        val (w, h) = ScreenUtil.realSize(this)
        var step = 0
        val tv = TextView(this).apply {
            text = "HIỆU CHỈNH\n\nBước 1/2: chạm vào TÂM JOYSTICK"
            setTextColor(Color.WHITE)
            textSize = 18f
            gravity = Gravity.CENTER
            setBackgroundColor(Color.argb(110, 0, 0, 0))
        }
        tv.setOnTouchListener { _, e ->
            if (e.actionMasked == MotionEvent.ACTION_UP) {
                val nx = (e.rawX / w).coerceIn(0f, 1f)
                val ny = (e.rawY / h).coerceIn(0f, 1f)
                if (step == 0) {
                    Prefs.setJoy(this, nx, ny)
                    step = 1
                    tv.text = "HIỆU CHỈNH\n\nBước 2/2: chạm vào NÚT TẤN CÔNG (thanh kiếm)"
                } else {
                    Prefs.setAtk(this, nx, ny)
                    removeView(calibView)
                    calibView = null
                    val (jx, jy) = Prefs.joy(this)
                    log("Đã lưu: joystick (%.3f, %.3f), tấn công (%.3f, %.3f)".format(jx, jy, nx, ny))
                }
            }
            true
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        if (Build.VERSION.SDK_INT >= 28) {
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        wm.addView(tv, lp)
        calibView = tv
    }

    // ---------------------------------------------------------------- các bài test

    private fun testTap() {
        val token = begin("T1 Bấm", 3_000) ?: return
        val atk = points().second
        log("T1: bấm 3 lần tại ${fmt(atk)}…")
        var n = 0
        var ok = 0
        fun one() {
            if (!gate.owns(token)) return
            engine.tap(atk) { success ->
                if (!gate.owns(token)) return@tap
                n++
                if (success) ok++
                if (n < 3) tasks.postDelayed({ one() }, 500)
                else complete(token, "T1 xong: $ok/3 lần gửi thành công; cần xác nhận phản ứng trên màn hình.")
            }
        }
        one()
    }

    private fun runJoy(name: String, totalMs: Long, tapEveryMs: Long, angleAt: (Long) -> Float?) {
        val limit = minOf(totalMs, Prefs.sessionSeconds(this) * 1_000L)
        val token = begin(name, limit) ?: return
        val (joy, atk, r) = points()
        log("$name: chạy tối đa ${limit / 1000}s; kéo ${r.toInt()}px…")
        driver = JoystickDriver(engine, joy, r, limit, angleAt, tapEveryMs,
            if (tapEveryMs > 0) atk else null) { msg -> complete(token, "$name $msg") }
        driver?.start()
    }

    // giữ sang phải 3 giây
    private fun testHold() = runJoy("T2 Giữ", 3_000, 0) { 0f }

    // xoay 1 vòng mỗi 2 giây, trong 6 giây
    private fun testCircle() = runJoy("T3 Xoay", 6_000, 0) { t -> (t % 2_000L) / 2_000f * 360f }

    // trái/phải mỗi giây + bấm tấn công mỗi 0,7 giây
    private fun testJoyAttack() = runJoy("T4 Joy+Đánh", 6_000, 700) { t ->
        if ((t / 1_000L) % 2L == 0L) 0f else 180f
    }

    // zigzag chếch xuống (30° và 150°) mỗi 1,5 giây, bấm tấn công mỗi 3 giây để nhặt đồ
    private fun testZigzag() = runJoy("T5 Zigzag", 20_000, 3_000) { t ->
        if ((t / 1_500L) % 2L == 0L) 30f else 150f
    }

    private fun testCapture() {
        val cap = CaptureService.instance
        if (cap == null) { log("T6: mở AutoJoy và cấp quyền chụp ở bước 2."); return }
        val token = begin("T6 Chụp", 3_000) ?: return
        log("T6: đang chụp…")
        cap.grab { msg -> complete(token, "T6 $msg") }
    }
}
