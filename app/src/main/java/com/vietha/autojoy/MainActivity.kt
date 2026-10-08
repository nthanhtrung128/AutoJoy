package com.vietha.autojoy

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (16 * resources.displayMetrics.density).toInt()
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        col.addView(TextView(this).apply {
            text = "AutoJoy P0 – kiểm tra thao tác cho Soul Knight Prequel"
            textSize = 20f
        })
        status = TextView(this).apply {
            textSize = 15f
            setPadding(0, pad / 2, 0, pad / 2)
        }
        col.addView(status)

        col.addView(button("1. Bật Trợ năng cho AutoJoy") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        col.addView(button("2. Cấp quyền chụp màn hình") { requestCapture() })
        col.addView(button("3. Cài đặt app (Pin → Không hạn chế)") {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
            )
        })
        col.addView(button("Tắt chụp màn hình") {
            stopService(Intent(this, CaptureService::class.java))
            status.postDelayed({ refresh() }, 500)
        })
        col.addView(TextView(this).apply {
            text = HELP
            textSize = 14f
            setPadding(0, pad, 0, 0)
        })

        setContentView(ScrollView(this).apply { addView(col) })

        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun button(label: String, onClick: () -> Unit): Button =
        Button(this).apply {
            text = label
            isAllCaps = false
            setOnClickListener { onClick() }
        }

    private fun refresh() {
        val acc = BotAccessibilityService.instance != null
        val cap = CaptureService.instance != null
        status.text = "Trợ năng: " + (if (acc) "ĐANG BẬT ✔" else "chưa bật ✘") +
            "\nChụp màn hình: " + (if (cap) "ĐANG BẬT ✔" else "chưa bật ✘")
    }

    private fun requestCapture() {
        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(mpm.createScreenCaptureIntent(), REQ_CAPTURE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_CAPTURE && resultCode == RESULT_OK && data != null) {
            val i = Intent(this, CaptureService::class.java)
                .putExtra(CaptureService.EXTRA_CODE, resultCode)
                .putExtra(CaptureService.EXTRA_DATA, data)
            startForegroundService(i)
            status.postDelayed({ refresh() }, 800)
        }
    }

    companion object {
        private const val REQ_CAPTURE = 1001

        private const val HELP = """CÁCH CHẠY THỬ
1) Làm đủ 3 bước trên. Khi cấp quyền chụp, chọn "Toàn bộ màn hình" (hoặc chọn đúng game).
2) Mở Soul Knight Prequel, chỉnh joystick về chế độ CỐ ĐỊNH, vào Mê Thành Pandora.
3) Bảng AutoJoy nổi trên game. Kéo dòng "✥ AutoJoy P0" để dời bảng, đừng che joystick/nút đánh.
4) Bấm "Hiệu chỉnh": chạm tâm joystick, rồi chạm nút tấn công.
5) Chạy lần lượt T1 → T6, KHÔNG chạm tay vào màn hình khi đang test.
   • Nhân vật chạy quá ít → bấm R+ ; chạy lệch/không chạy → R− rồi thử lại.
6) Ghi kết quả từng bài (nhân vật có làm đúng không + dòng chữ xanh hiện ra) và gửi lại."""
    }
}
