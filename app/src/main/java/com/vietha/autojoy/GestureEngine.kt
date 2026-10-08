package com.vietha.autojoy

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.PointF
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Gửi thao tác chạm qua Accessibility. */
class GestureEngine(private val svc: AccessibilityService, private val log: (String) -> Unit) {
    val main = Handler(Looper.getMainLooper())

    fun dispatch(g: GestureDescription, done: ((Boolean) -> Unit)? = null) {
        val accepted = svc.dispatchGesture(g, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                done?.invoke(true)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                done?.invoke(false)
            }
        }, main)
        if (!accepted) {
            log("Hệ thống từ chối gửi thao tác (dispatchGesture = false)")
            main.post { done?.invoke(false) }
        }
    }

    fun tap(p: PointF, durationMs: Long = 60, done: ((Boolean) -> Unit)? = null) {
        val path = Path().apply { moveTo(p.x, p.y) }
        val g = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
            .build()
        dispatch(g, done)
    }
}

/**
 * Joystick ảo: đặt ngón ảo xuống tâm joystick, GIỮ NGUYÊN không nhấc,
 * và đổi hướng liên tục mỗi 100ms bằng "nét vuốt nối tiếp" (continueStroke).
 * Đây là điều các app auto click thông thường không làm được.
 *
 * angleAt(t): góc theo độ (0 = phải, 90 = xuống, 180 = trái, 270 = lên), null = nhả.
 * Có thể kèm một ngón thứ hai bấm nút tấn công định kỳ trong khi vẫn giữ joystick.
 */
class JoystickDriver(
    private val engine: GestureEngine,
    private val center: PointF,
    private val radius: Float,
    private val totalMs: Long,
    private val angleAt: (Long) -> Float?,
    private val tapEveryMs: Long,
    private val tapPoint: PointF?,
    private val onDone: (String) -> Unit
) {
    private var stroke: GestureDescription.StrokeDescription? = null
    private var last = PointF(center.x, center.y)
    private var startAt = 0L
    private var lastTapAt = -1_000_000L
    private var running = false
    private var stopRequested = false
    private var jitter = false
    private var segments = 0
    private var cancels = 0
    private var taps = 0

    val isRunning: Boolean get() = running

    fun start() {
        running = true
        startAt = SystemClock.uptimeMillis()
        step()
    }

    /** Yêu cầu dừng: đoạn kế tiếp sẽ kéo joystick về tâm rồi nhấc ngón. */
    fun stop() {
        stopRequested = true
    }

    private fun step() {
        if (!running) return
        val t = SystemClock.uptimeMillis() - startAt
        val angle: Float? = if (stopRequested || t >= totalMs) null else angleAt(t)
        val release = angle == null

        val target = if (angle == null) {
            PointF(center.x, center.y)
        } else {
            val r = Math.toRadians(angle.toDouble())
            PointF(
                (center.x + radius * cos(r).toFloat()).coerceAtLeast(0f),
                (center.y + radius * sin(r).toFloat()).coerceAtLeast(0f)
            )
        }
        // Đoạn đường không được dài 0 → lệch 1px qua lại khi giữ nguyên hướng
        if (abs(target.x - last.x) < 0.5f && abs(target.y - last.y) < 0.5f) {
            target.x += if (jitter) 1f else -1f
            jitter = !jitter
        }

        val path = Path().apply {
            moveTo(last.x, last.y)
            lineTo(target.x, target.y)
        }
        val prev = stroke
        val s = if (prev == null) {
            GestureDescription.StrokeDescription(path, 0, SEG_MS, !release)
        } else {
            prev.continueStroke(path, 0, SEG_MS, !release)
        }
        val builder = GestureDescription.Builder().addStroke(s)

        if (!release && tapPoint != null && tapEveryMs > 0 && t - lastTapAt >= tapEveryMs) {
            lastTapAt = t
            taps++
            val tp = Path().apply { moveTo(tapPoint.x, tapPoint.y) }
            builder.addStroke(GestureDescription.StrokeDescription(tp, 0, 60))
        }

        stroke = s
        last = target
        segments++

        engine.dispatch(builder.build()) { ok ->
            if (!ok) {
                cancels++
                stroke = null
                last = PointF(center.x, center.y)
                if (stopRequested) {
                    running = false
                    onDone("đã dừng ($segments đoạn, $taps lần bấm, bị huỷ $cancels lần)")
                    return@dispatch
                }
                if (cancels >= 8) {
                    running = false
                    onDone("THẤT BẠI: bị huỷ $cancels lần → máy/game không cho giữ joystick")
                    return@dispatch
                }
            }
            if (release) {
                running = false
                onDone("xong: $segments đoạn, $taps lần bấm, bị huỷ $cancels lần")
            } else {
                step()
            }
        }
    }

    companion object {
        const val SEG_MS = 100L
    }
}
