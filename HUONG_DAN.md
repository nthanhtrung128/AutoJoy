# AutoJoy P0 – Hướng dẫn build, cài và chạy thử

Bản P0 **chưa phải bot hoàn chỉnh**. Mục đích duy nhất: kiểm tra trên **Galaxy S25 Ultra (One UI 8.5)** với
**Soul Knight Prequel (Hiệp Sĩ Khí Nguyên Tiền Truyện)** rằng app có thể:

- giữ và kéo joystick (điều mà app auto click thông thường không làm được),
- vừa giữ joystick vừa bấm nút tấn công,
- chụp được màn hình game để sau này nhận diện boss, sương mù, đồ rơi.

---

## 1. Build ra file APK (chọn 1 trong 2 cách)

### Cách A – Dùng GitHub, không cần cài gì (khuyên dùng)
1. Tạo tài khoản GitHub miễn phí (nếu chưa có), bấm **New repository**, đặt tên `AutoJoy`, chọn **Private**, bấm **Create**.
2. Trong repo mới, bấm **uploading an existing file**, kéo **toàn bộ nội dung** thư mục `AutoJoy` vào (gồm cả thư mục ẩn `.github`), bấm **Commit changes**.
   - Nếu trình duyệt không kéo được thư mục `.github`: bấm **Add file → Create new file**, gõ tên `.github/workflows/build-apk.yml`, dán nội dung file cùng tên trong gói này.
3. Mở tab **Actions** → chờ dòng **Build APK** có dấu ✔ xanh (khoảng 3–5 phút).
4. Bấm vào lần chạy đó → mục **Artifacts** → tải **AutoJoy-P0-apk** (file zip, bên trong là `app-debug.apk`).

### Cách B – Android Studio trên máy tính
1. Cài Android Studio, chọn **Open** → mở thư mục `AutoJoy`.
2. Chờ đồng bộ xong, chọn menu **Build → Build App Bundle(s) / APK(s) → Build APK(s)**.
3. File ở `app/build/outputs/apk/debug/app-debug.apk`.

---

## 2. Cài lên S25 Ultra

Tên menu trên One UI có thể khác đôi chút, tìm theo ý nghĩa tương đương.

1. **Tạm tắt Auto Blocker** (nếu đang bật):
   Cài đặt → Bảo mật và quyền riêng tư → Auto Blocker → Tắt. Cài xong có thể bật lại.
2. Chép `app-debug.apk` vào điện thoại, mở file và cài. Cho phép "Cài ứng dụng không rõ nguồn" nếu được hỏi.
3. **Mở khoá cài đặt bị hạn chế** (Android chặn Trợ năng với app cài ngoài Play Store):
   Cài đặt → Ứng dụng → **AutoJoy P0** → nút ⋮ góc trên → **Cho phép cài đặt bị hạn chế**.
4. Mở app **AutoJoy P0**:
   - Bước **1**: bật Trợ năng → Ứng dụng đã cài đặt → **AutoJoy – điều khiển game** → Bật.
   - Bước **2**: cấp quyền chụp màn hình → chọn **Toàn bộ màn hình**.
   - Bước **3**: Pin → **Không hạn chế**.
   - Màn hình app phải hiện: Trợ năng ✔, Chụp màn hình ✔.

---

## 3. Chuẩn bị trong game

1. Vào cài đặt game, chỉnh joystick về chế độ **cố định** (không di chuyển theo ngón tay).
2. Vào chế độ **Mê Thành Pandora**, đứng trong map.
3. Bảng **AutoJoy P0** nổi trên game. Kéo chữ **✥ AutoJoy P0** để dời bảng lên phía trên giữa, không che joystick và nút tấn công. Nút **–** để thu gọn bảng.

## 4. Hiệu chỉnh

Bấm **Hiệu chỉnh** → chạm vào **tâm joystick** → chạm vào **nút tấn công** (thanh kiếm).
Dòng chữ xanh sẽ hiện toạ độ đã lưu.

## 5. Chạy thử (không chạm tay vào màn hình khi đang test)

| Bài | Bot làm gì | Kết quả mong đợi trong game |
|---|---|---|
| **T1 Bấm** | Bấm nút tấn công 3 lần | Nhân vật tấn công / tương tác 3 lần |
| **T2 Giữ** | Giữ joystick sang phải 3 giây | Nhân vật chạy sang phải **liên tục** 3 giây |
| **T3 Xoay** | Giữ joystick, xoay vòng 6 giây | Nhân vật chạy vòng tròn, không bị khựng |
| **T4 Joy+Đánh** | Chạy trái/phải + bấm tấn công mỗi 0,7 giây | Vừa chạy vừa đánh, không bị đứng lại khi bấm |
| **T5 Zigzag** | Zigzag chếch xuống 20 giây, bấm đánh mỗi 3 giây | Giống cách chơi trong video: đảo trái phải, đi dần xuống |
| **T6 Chụp** | Chụp màn hình game | Chữ xanh báo "OK, thấy được game", ảnh lưu trong Thư viện → AutoJoy |

- Nhân vật chạy quá chậm hoặc chỉ nhích: bấm **R+** rồi thử lại.
- Nhân vật chạy sai hướng hoặc không chạy: bấm **Hiệu chỉnh** lại, hoặc **R−**.
- **DỪNG**: dừng ngay bài đang chạy.

## 6. Gửi lại kết quả

Với mỗi bài T1–T6, gửi:
1. Nhân vật có làm đúng không (Đúng / Sai / Không phản ứng).
2. Dòng chữ xanh hiện ra sau khi chạy xong (chụp màn hình là được).
3. Ảnh do T6 lưu trong Thư viện → album AutoJoy.

Kết quả T2–T4 quyết định cách làm bot:
- **Đạt** → làm tiếp bot Pandora bằng Trợ năng (không cần thêm gì).
- **Không đạt** (nhân vật đứng im, hoặc báo "bị huỷ") → chuyển sang chế độ **Shizuku** (vẫn không cần root).
