@echo off
chcp 65001 >nul
echo ========================================================
echo   HƯỚNG DẪN ĐẨY DỰ ÁN LÊN GITHUB ĐỂ TỰ ĐỘNG BUILD APK
echo ========================================================
echo.
echo Bước 1: Mở trình duyệt vào link: https://github.com/new
echo Bước 2: Đặt tên Repository (ví dụ: ffmaster) rồi bấm "Create repository".
echo Bước 3: Copy đường link của Repository vừa tạo.
echo         (Ví dụ: https://github.com/thanhle/ffmaster.git)
echo.
set /p REPO_URL="Dán đường link GitHub của bạn vào đây và bấm Enter: "

if "%REPO_URL%"=="" (
    echo [LỖI] Bạn chưa nhập link GitHub. Vui lòng chạy lại file!
    pause
    exit /b
)

echo.
echo [1/4] Khởi tạo Git...
git init
echo [2/4] Chuẩn bị đóng gói các file...
git add .
git commit -m "Auto build Android APK with GitHub Actions"
git branch -M main
git remote remove origin 2>nul
git remote add origin %REPO_URL%
echo [3/4] Đang đẩy code lên GitHub...
git push -u origin main

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ========================================================
    echo [THÀNH CÔNG] Code đã được đẩy lên GitHub!
    echo.
    echo HƯỚNG DẪN TẢI FILE APK:
    echo 1. Mở trang GitHub của bạn trên trình duyệt.
    echo 2. Bấm vào tab "Actions" trên thanh menu.
    echo 3. Bạn sẽ thấy tiến trình "Build Android APK" đang chạy màu vàng.
    echo 4. Chờ khoảng 1-2 phút khi chuyển sang màu xanh lá tích (v).
    echo 5. Bấm vào đó, kéo xuống dưới mục "Artifacts" để tải file:
    echo    app-debug-apk (chứa file APK hoàn chỉnh bên trong)!
    echo ========================================================
) else (
    echo.
    echo [CHÚ Ý] Nếu bị hỏi đăng nhập, hãy làm theo hướng dẫn trên màn hình để xác thực GitHub.
)

pause
