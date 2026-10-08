# AutoJoy – bot auto cho Soul Knight Prequel (Android)

Trả lời người dùng bằng **tiếng Việt**. Cách làm việc ưa thích: hỏi xác nhận hướng đi bằng câu hỏi có cấu trúc trước, chốt xong thì tự làm tiếp. File đầu ra luôn ghi đè đúng tên chuẩn (vd. `AutoJoy_P0.zip`).

## 1. Bối cảnh

- Người dùng (VietHa1) xây app cho **một người bạn** ("Bình xuôi") cần auto farm game.
- Game: **Hiệp Sĩ Khí Nguyên Tiền Truyện / Soul Knight Prequel** (ChillyRoom, Unity, đồ hoạ pixel, góc nhìn từ trên xuống, màn hình ngang).
- Chế độ cần auto: **Mê Thành Pandora**. Không tốn vé, chạy khi người dùng yêu cầu (không cần lịch hẹn giờ).
- Thiết bị: **Samsung Galaxy S25 Ultra, One UI 8.5**, điện thoại thật, không root.
- Joystick trong game **chỉnh được** (đặt chế độ cố định cho bot).
- Video tham khảo: https://www.youtube.com/watch?v=0jc_Q7gqics (3:27, đúng 1 lượt map, có thuyết minh).

## 2. Yêu cầu bot (từ tin nhắn + thuyết minh video)

1. Vào map: chọn Mê Thành Pandora → bấm **Khiêu Chiến**.
2. Skill đã **tự đánh** → bot chỉ cần **di chuyển** bằng joystick.
3. Đi tới bãi quái (vị trí ngẫu nhiên), **đảo trái ↔ phải** liên tục để skill trúng quái.
4. **Không để dính tường / vật cản** (sẽ đứng im).
5. **Sương mù kéo từ trên xuống** liên tục → phải đi dần xuống, nhanh hơn sương. Sương phủ hết = hết ải.
6. **Boss** có trong map, được định vị (minimap) → giết được thì càng tốt.
7. **Nhặt đồ không tự động**: khi đồ rơi (sau boss, cuối map) phải **bấm nút tấn công** để nhặt.
8. Mỗi map ~3–4 phút, lặp lại N lượt.

Vấn đề gốc: các app auto click thông thường **không kéo/giữ được joystick** ("auto click k đc, kéo di chuyển nó k kéo").

## 3. Quan sát giao diện (từ video, toạ độ tỉ lệ 0..1, màn ngang)

| Thành phần | Vị trí |
|---|---|
| Joystick | ~ (0.18, 0.72), góc dưới trái |
| Nút tấn công (thanh kiếm) | ~ (0.87, 0.84), góc dưới phải, skill xếp quanh |
| Minimap (tím) | góc trên phải, x 0.82–0.96, y 0–0.21 |
| Thanh máu boss | giữa trên, x 0.40–0.62, y 0.03–0.10 |
| Bảng nhiệm vụ | bên trái, tiêu đề "Mê Thành Pandora" |

- Boss đã thấy: "Thú Sương Mù", "Thị Nữ Minh Thần" (có **banner giới thiệu** che cả màn hình).
- Nhân vật luôn ở giữa màn hình (camera đi theo) → phát hiện kẹt bằng so sánh nền giữa 2 khung hình.
- Thông báo "Đã nhận được…" hiện liên tục ở giữa dưới → bỏ qua.

## 4. Kiến trúc đã chốt

- **APK chạy trên điện thoại**, không cần PC. Console tuỳ chỉnh nằm trong APK (bảng nổi + màn hình cài đặt).
- **Gửi thao tác**: `AccessibilityService.dispatchGesture` với **`StrokeDescription.continueStroke`** (Android 8+) → giữ ngón ảo trên joystick, đổi hướng mỗi 100ms, kèm ngón thứ hai bấm tấn công.
  - Dự phòng nếu game lờ thao tác Accessibility: **Shizuku** (inject MotionEvent mức shell, không root).
- **Nhìn màn hình**: MediaProjection + ImageReader (thu nhỏ ≤1280px). Nhận diện chủ yếu bằng **màu + ảnh mẫu** (OpenCV về sau), OCR (ML Kit) chỉ khi cần.
- **Toạ độ chuẩn hoá 0..1** + hiệu chỉnh bằng chạm → chạy được nhiều máy.
- Về lâu dài: profile theo game, Game Pack (.zip) xuất/nhập, editor kịch bản dạng khối + JSON/YAML.

### State machine bot Pandora (cho P1–P2)

| Trạng thái | Nhận biết | Hành động |
|---|---|---|
| Màn chọn ải | ảnh mẫu nút "Khiêu Chiến" | bấm |
| Đang tải | chưa thấy joystick | chờ |
| Càn quét (mặc định) | thấy joystick + minimap | giữ joystick, đảo 30° ↔ 150° (chếch xuống) mỗi 1–2s |
| Đi tới boss | icon boss trên minimap | đẩy joystick theo vector mình → boss trên minimap |
| Đánh boss | thanh máu boss hiện | đi vòng nhỏ quanh boss, bấm tấn công định kỳ |
| Banner boss | banner lớn | nhả joystick, chờ/chạm bỏ qua |
| Né sương | màu sương ở dải trên vượt ngưỡng | ưu tiên đi thẳng xuống, bỏ boss nếu boss trong sương |
| Kẹt tường | nền quanh nhân vật không đổi 1–1.5s dù đang giữ joystick | đổi hướng ngược/vuông góc |
| Nhặt đồ / tổng kết | màn tổng kết hoặc boss vừa chết | bấm tấn công liên tục vài giây, đóng bảng, lượt mới |

## 5. Trạng thái hiện tại – P0 (kiểm tra khả thi)

Mã nguồn đã viết xong, **CHƯA BUILD / CHƯA CHẠY THỬ** (môi trường cũ chặn tải Android SDK). Chỉ mới kiểm tra cú pháp Kotlin.

| File | Vai trò |
|---|---|
| `app/src/main/java/com/vietha/autojoy/GestureEngine.kt` | `GestureEngine` (tap) + `JoystickDriver` (giữ/xoay joystick bằng continueStroke, kèm tap tấn công) |
| `.../BotAccessibilityService.kt` | Bảng nổi trên game: Hiệu chỉnh, R−/R+, T1–T6, DỪNG, log |
| `.../CaptureService.kt` | Foreground service MediaProjection, chụp + phân tích độ sáng (phát hiện game chặn chụp), lưu `Pictures/AutoJoy` |
| `.../MainActivity.kt` | Màn hình cấp quyền + hướng dẫn |
| `.../Prefs.kt` | Lưu toạ độ hiệu chỉnh + `ScreenUtil.realSize` |
| `.github/workflows/build-apk.yml` | Build APK debug trên GitHub Actions (Gradle 8.11.1, JDK 17) |
| `HUONG_DAN.md` | Hướng dẫn người dùng cuối: build, cài S25 Ultra, chạy T1–T6, gửi kết quả |

- Stack: Kotlin 2.0.21, AGP 8.7.3, compileSdk/targetSdk 35, minSdk 26, **không dùng thư viện ngoài** (chỉ API Android gốc, UI dựng bằng code, không XML layout).
- **Chưa có Gradle wrapper** (không tạo được offline). Nếu có mạng: chạy `gradle wrapper --gradle-version 8.11.1` ở thư mục gốc.
- Bài test: T1 bấm tấn công, T2 giữ joystick 3s, T3 xoay 6s, T4 joystick + bấm tấn công, T5 zigzag chếch xuống 20s, T6 chụp màn hình.

## 6. Việc tiếp theo

1. **Build được APK** (sửa lỗi biên dịch nếu có), thêm Gradle wrapper.
2. Người dùng cài lên S25 Ultra, chạy T1–T6, gửi kết quả.
   - T2–T4 đạt → làm **P1 bot Pandora** bằng Accessibility.
   - Không đạt (nhân vật đứng im / "bị huỷ") → thêm chế độ **Shizuku**.
   - T6 ra ảnh đen → game chặn chụp, phải điều hướng "mù" theo thời gian + minimap không khả dụng.
3. P1 (2–3 tuần): vào map, càn quét zigzag, né kẹt, né sương, nhặt đồ cuối map, lặp N lượt.
4. P2 (1–2 tuần): boss qua minimap, thanh máu boss, banner.
5. P3 (2 tuần): console chỉnh tham số, hiệu chỉnh nhanh máy mới, xuất/nhập cấu hình.
6. P4: tổng quát hoá cho game khác (editor kịch bản).

## 7. Lưu ý khi cài trên One UI 8.5

- Tạm tắt **Auto Blocker** để cài APK ngoài.
- App info → ⋮ → **Cho phép cài đặt bị hạn chế**, rồi mới bật Trợ năng được.
- Pin: **Không hạn chế**. Quyền chụp: chọn **Toàn bộ màn hình**.
- Game có thể cấm bot trong điều khoản → người dùng tự chịu rủi ro khoá tài khoản.
