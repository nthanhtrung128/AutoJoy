package com.vietha.autojoy

import android.content.Context
import android.content.SharedPreferences
import android.hardware.display.DisplayManager
import android.util.DisplayMetrics
import android.view.Display

/** Lưu toạ độ đã hiệu chỉnh, dạng tỉ lệ 0..1 để chạy được trên mọi độ phân giải. */
object Prefs {
    private fun sp(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences("autojoy", Context.MODE_PRIVATE)

    // Giá trị mặc định đo từ video Soul Knight Prequel (màn hình ngang)
    fun joy(ctx: Context): Pair<Float, Float> =
        sp(ctx).getFloat("joyX", 0.18f) to sp(ctx).getFloat("joyY", 0.72f)

    fun atk(ctx: Context): Pair<Float, Float> =
        sp(ctx).getFloat("atkX", 0.87f) to sp(ctx).getFloat("atkY", 0.84f)

    fun sessionSeconds(ctx: Context): Int = sp(ctx).getInt("sessionSeconds", 30).coerceIn(5, 60)
    fun setSessionSeconds(ctx: Context, value: Int) { sp(ctx).edit().putInt("sessionSeconds", value.coerceIn(5, 60)).apply() }
    fun history(ctx: Context): String = sp(ctx).getString("history", "") ?: ""
    fun record(ctx: Context, message: String) {
        val time = java.text.SimpleDateFormat("dd/MM HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        val lines = (history(ctx).lines().filter { it.isNotBlank() } + "$time — $message").takeLast(40)
        sp(ctx).edit().putString("history", lines.joinToString("\n")).apply()
    }

    fun radiusDp(ctx: Context): Float = sp(ctx).getFloat("radiusDp", 60f)

    fun setJoy(ctx: Context, x: Float, y: Float) =
        sp(ctx).edit().putFloat("joyX", x).putFloat("joyY", y).apply()

    fun setAtk(ctx: Context, x: Float, y: Float) =
        sp(ctx).edit().putFloat("atkX", x).putFloat("atkY", y).apply()

    fun setRadiusDp(ctx: Context, r: Float) =
        sp(ctx).edit().putFloat("radiusDp", r).apply()
}

object ScreenUtil {
    /** Kích thước thật của màn hình theo hướng xoay hiện tại (pixel). */
    fun realSize(ctx: Context): Pair<Int, Int> {
        val dm = DisplayMetrics()
        val dmgr = ctx.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        dmgr.getDisplay(Display.DEFAULT_DISPLAY).getRealMetrics(dm)
        return dm.widthPixels to dm.heightPixels
    }
}
