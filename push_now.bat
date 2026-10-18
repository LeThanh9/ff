@echo off
chcp 65001 >nul
cd /d "%~dp0"

echo ========================================================
echo   ĐANG ĐẨY DỰ ÁN LÊN GITHUB: LeThanh9/ff
echo ========================================================
echo.
echo Đang kết nối và đẩy mã nguồn lên nhánh main...
echo (Nếu có cửa sổ GitHub hiện lên, hãy bấm 'Sign in with your browser' để xác nhận)
echo.

git push -u origin main

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ========================================================
    echo [THÀNH CÔNG 100%] Toàn bộ dự án đã được đẩy lên GitHub!
    echo.
    echo BÂY GIỜ BẠN CHỈ CẦN:
    echo 1. Vào link: https://github.com/LeThanh9/ff/actions
    echo 2. Xem tiến trình 'Build Android APK' đang chạy màu vàng.
    echo 3. Sau 1-2 phút khi xong (màu xanh lá tích v), bấm vào đó
    echo    và tải file 'app-debug-apk' về máy để cài đặt!
    echo ========================================================
) else (
    echo.
    echo [CHÚ Ý] Nếu chưa đẩy được, hãy kiểm tra lại thông báo ở trên.
)

echo.
pause
