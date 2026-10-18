# FF Mod Tool & ESP Engine — Build & Setup Guide

Dự án Android Native kết hợp Java & C++ (NDK) hỗ trợ vẽ Canvas Overlay ESP, Fake Lag Network Proxy Server và quét/dump offset bộ nhớ game Free Fire.

---

## 1. Yêu Cầu Môi Trường (Prerequisites)
- **Android Studio**: Hedgehog+ (2023.1+)
- **Android NDK**: 25+ (r25c / r26b)
- **CMake**: 3.22.1+
- **Android SDK**: CompileSdk 34, MinSdk 26
- **Thiết bị chạy**: Hỗ trợ cả **NO-ROOT** và **ROOT**:
  - **No-Root**: Hoạt động trực tiếp 100% cho Menu Nổi & Fake Lag SOCKS5 Proxy. Nếu muốn đọc RAM/ESP trên máy không Root, chạy app qua **Không Gian Ảo (Virtual Space / VMOS / F1VM / VPhoneGaga / DualSpace)**.
  - **Root**: Hoạt động trực tiếp mọi tính năng với Magisk / KernelSU / APatch.

---

## 2. Cấu Trúc Dự Án Chuẩn Android Studio & Gradle
```text
ff/
├── settings.gradle          # Khai báo Root Project và module ':app'
├── build.gradle             # Project-level Gradle build script
├── gradle.properties        # Cấu hình tối ưu bộ nhớ JVM & AndroidX
├── build_apk.bat            # Script đóng gói APK nhanh trên Windows
└── app/                     # Module ứng dụng chính
    ├── build.gradle         # Module Gradle build script, SDK 34, NDK CMake
    └── src/
        └── main/
            ├── AndroidManifest.xml
            ├── java/com/ff/cheat/
            │   ├── MainActivity.java
            │   ├── DumperActivity.java
            │   ├── FloatingMenu.java
            │   ├── OverlayService.java
            │   ├── CheatBridge.java
            │   ├── RootUtils.java
            │   ├── FakeLagProxy.java
            │   └── ESPView.java
            ├── res/
            │   ├── layout/
            │   │   ├── activity_main.xml
            │   │   └── activity_dumper.xml
            │   └── values/
            │       ├── strings.xml
            │       └── colors.xml
            └── cpp/
                ├── CMakeLists.txt
                ├── ffcheat.cpp
                ├── dumper.cpp
                └── offsets.h
```

---

## 3. Các Cải Tiến Quan Trọng Đã Được Nâng Cấp (Changelog)

### A. Hiệu năng & Ổn định (Performance & Zero-Allocation)
1. **Khử hoàn toàn rác bộ nhớ (Zero Allocation in `onDraw`)**:
   - Thay thế `String.format` và `Color.parseColor` trong vòng lặp vẽ 60 FPS bằng màu sắc hằng số và int text format, loại bỏ hiện tượng giật lag / khựng hình (GC freeze).
2. **Batching Log trong Dumper**:
   - Gom log thành các batch 60ms thay vì gọi `handler.post()` và `fullScroll()` hàng trăm lần làm treo máy/đơ giao diện.
3. **Quản lý Vòng đời & Thread an toàn**:
   - Dừng triệt để `renderThread` khi tắt Overlay (`OverlayService.onDestroy()`), tránh rò rỉ RAM và xung đột JNI.

### B. Độ chính xác & Tính năng Aimbot / ESP
1. **Đồng bộ độ phân giải thật (Real Screen Resolution Sync)**:
   - Truyền `DisplayMetrics.getRealMetrics` từ Android vào Native C++ qua `setScreenSize(w, h)`, khắc phục lỗi lệch tâm khi điện thoại có tai thỏ / đục lỗ hoặc màn hình 20:9.
2. **Khóa đầu chuẩn xác (Accurate Head Lock)**:
   - Tính toán và lưu trực tiếp `headX`, `headY` trong Native struct để Aimbot khóa thẳng vào đầu kẻ địch trong vòng tròn FOV 180px.
3. **Corner Box & Bottom Snapline**:
   - Thêm chế độ vẽ hộp góc (Corner Box) thẩm mỹ và đường snapline kéo từ đáy màn hình thay vì kéo từ giữa tâm (không làm che khuất tầm nhìn).

### C. Cơ Chế Quét Offset Tự Động (Dynamic Memory Dumper)
1. **Cấu trúc `offsets.h` Dynamic**:
   - Không còn dùng `constexpr` cứng nhắc. Dumper quét xong có thể bấm **APPLY** để áp dụng trực tiếp ngay lập tức mà không cần compile lại APK.
2. **Sửa lỗi quét ARM64 ADRP/ADD**:
   - Quét đúng phân vùng mã máy (`.text` / `r-xp`) thay vì quét nhầm vào vùng data/rodata.
   - Hỗ trợ giải mã chuẩn 21-bit immediate có sign extension của tập lệnh ARM64.
3. **Hỗ trợ đa phiên bản Free Fire**:
   - Tự nhận diện cả Free Fire Quốc tế (`com.dts.freefireth`), Free Fire MAX (`com.dts.freefiremax`) và Free Fire VNG (`com.vng.freefireth`).

### D. Hệ Thống Fake Lag Proxy & Điều Khiển Menu Nổi (Mới)
1. **Local SOCKS5 / TCP Proxy Server (`FakeLagProxy.java`)**:
   - Chạy local proxy server tại `127.0.0.1:8888` hỗ trợ SOCKS5 (RFC 1928) & HTTP CONNECT.
   - Chèn độ trễ nhân tạo (Artificial Delay / Desync) tùy biến từ 50ms đến 2000ms.
   - Thao tác thay đổi độ trễ tức thì qua SeekBar hoặc các nút preset (100ms, 250ms, 500ms, 800ms).
2. **Tích hợp Kernel Traffic Control (tc netem & iptables)**:
   - Với quyền Root, tự động điều khiển kernel Linux qua `tc qdisc netem delay` trên các giao diện mạng (`wlan0`, `rmnet_data0`), làm trễ trực tiếp các gói tin gameplay UDP/TCP của game.
3. **Nút "HIỂN THỊ MENU NỔI" Độc Lập**:
   - Cho phép mở Floating Menu ngay từ MainActivity mà không cần phải vào game trước để test vị trí, cấu hình ESP, Aimbot và Proxy.
   - Engine tự động chạy watcher background thread để tự kết nối ngay khi game Free Fire khởi động.

---

## 4. Hướng Dẫn Sử Dụng (Dành Cho Cả No-Root & Root)

### A. Chế độ NO-ROOT (Điện thoại chưa Root)
1. Cài đặt app **FF Master Service**.
2. Cấp quyền **"Hiển thị trên các ứng dụng khác" (Display over other apps)**.
3. **Mở Menu Nổi**: Bấm nút **HIỂN THỊ MENU NỔI** để icon `⚡FF` xuất hiện trên màn hình.
4. **Sử dụng Fake Lag Proxy (No-Root)**:
   - Bật **FakeLag Proxy** trong Menu nổi hoặc trên MainActivity.
   - Vào Cài đặt điện thoại -> **Wi-Fi** -> Chỉnh sửa mạng đang kết nối -> Chọn **Proxy Thủ công**:
     - Tên máy chủ proxy: `127.0.0.1`
     - Cổng proxy: `8888`
   - Tất cả dữ liệu game sẽ tự động đi qua Local Proxy Server và được chèn độ trễ theo mức bạn đã chọn (`100ms`, `250ms`, `500ms`...).
5. **Đọc RAM ESP & Aimbot (No-Root)**:
   - Vì Android bảo mật không cho 2 app đọc RAM của nhau nếu không có root, bạn chỉ cần cài đặt app này cùng Free Fire vào một ứng dụng **Không Gian Ảo (Virtual Space)** như:
     * **VMOS** (ROM ảo có sẵn root/ptrace)
     * **F1VM / VPhoneGaga / Twoyi**
     * **DualSpace / VirtualXposed**
   - Khi chạy chung container, engine sẽ tự động dùng `memcpy` đọc bộ nhớ mà không cần can thiệp hệ điều hành gốc của máy.

### B. Chế độ ROOT (Điện thoại đã Root Magisk / KernelSU / APatch)
1. Mở app và cấp quyền Superuser (su).
2. Bấm **HIỂN THỊ MENU NỔI** hoặc **START INJECT**.
3. Bật thêm tính năng **Kernel Netem** để điều khiển trực tiếp tầng kernel packet latency trên `wlan0`.
4. Mở Free Fire, mở Dumper để quét offsets nếu game cập nhật bản mới.

---

## 5. Hướng Dẫn Đóng Gói Ra File APK (Build APK)

### Cách 1: Đóng gói bằng Android Studio (Khuyên dùng)
1. Mở **Android Studio**.
2. Chọn **File -> Open...** -> Duyệt và chọn thư mục `ff` này.
3. Đợi Android Studio tự động đồng bộ (Gradle Sync) trong khoảng 1–2 phút.
4. Trên thanh menu, chọn:
   **Build -> Build Bundle(s) / APK(s) -> Build APK(s)**
5. Sau khi build xong, file APK sẽ nằm tại:
   `app/build/outputs/apk/debug/app-debug.apk`

### Cách 2: Đóng gói bằng File Batch (Windows)
* Nhấp đúp chuột vào file **`build_apk.bat`** trong thư mục gốc để tự động gọi Gradle biên dịch và xuất file APK.

### Cách 3: Đóng gói trực tiếp trên Điện Thoại (AIDE / MT Manager)
1. Chép toàn bộ thư mục `ff` vào bộ nhớ điện thoại.
2. Mở ứng dụng **AIDE - IDE for Android Java C++** -> Chọn **Open Project** -> Chọn thư mục `ff`.
3. Bấm nút **Run (Play)**, AIDE sẽ tự động biên dịch mã nguồn Java, C++ và đóng gói cài đặt file APK trực tiếp lên máy!

