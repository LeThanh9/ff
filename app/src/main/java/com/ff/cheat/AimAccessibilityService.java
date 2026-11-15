package com.ff.cheat;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Context;
import android.content.Intent;
import android.graphics.Path;
import android.os.Build;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

/**
 * AimAccessibilityService — Dịch vụ Trợ Năng mô phỏng thao tác vuốt vuốt tâm (AimDrag)
 * Giải pháp chuẩn của Google Android giúp chạy 100% trên thiết bị KHÔNG ROOT (No-Root),
 * Xiaomi HyperOS / MIUI, Samsung OneUI, ColorOS từ Android 7.0 đến Android 14/15.
 */
public class AimAccessibilityService extends AccessibilityService {

    private static final String TAG = "AimAccessibility";
    private static volatile AimAccessibilityService instance = null;

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        Log.i(TAG, "AimAccessibilityService đã kết nối thành công (Chế độ No-Root)");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        instance = null;
        Log.i(TAG, "AimAccessibilityService đã ngắt kết nối");
    }

    public static boolean isServiceRunning() {
        return instance != null;
    }

    public static AimAccessibilityService getInstance() {
        return instance;
    }

    /**
     * Thực hiện thao tác vuốt vuốt nhẹ màn hình mà không cần quyền ROOT
     * Tương thích hoàn hảo với màn hình tần số quét cao (90Hz / 120Hz / 144Hz)
     */
    public static boolean performSwipe(float startX, float startY, float endX, float endY, long durationMs) {
        if (instance == null) {
            return false;
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return false;
        }

        try {
            Path path = new Path();
            path.moveTo(startX, startY);
            path.lineTo(endX, endY);

            GestureDescription.Builder builder = new GestureDescription.Builder();
            long safeDuration = Math.max(15, Math.min(100, durationMs));
            builder.addStroke(new GestureDescription.StrokeDescription(path, 0, safeDuration));

            return instance.dispatchGesture(builder.build(), null, null);
        } catch (Exception e) {
            Log.e(TAG, "Lỗi thực thi dispatchGesture: " + e.getMessage());
            return false;
        }
    }

    /**
     * Kiểm tra xem người dùng đã bật dịch vụ trợ năng này trong Cài Đặt chưa
     */
    public static boolean isAccessibilityEnabled(Context context) {
        if (isServiceRunning()) return true;
        if (context == null) return false;

        String expectedServiceName = context.getPackageName() + "/" + AimAccessibilityService.class.getName();
        try {
            int enabled = Settings.Secure.getInt(
                    context.getContentResolver(),
                    Settings.Secure.ACCESSIBILITY_ENABLED
            );
            if (enabled == 1) {
                String settingValue = Settings.Secure.getString(
                        context.getContentResolver(),
                        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                );
                if (settingValue != null) {
                    TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter(':');
                    splitter.setString(settingValue);
                    while (splitter.hasNext()) {
                        String accessService = splitter.next();
                        if (accessService.equalsIgnoreCase(expectedServiceName)) {
                            return true;
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Lỗi kiểm tra trạng thái trợ năng: " + e.getMessage());
        }
        return false;
    }

    /**
     * Mở trực tiếp màn hình cài đặt Dịch Vụ Trợ Năng
     */
    public static void openAccessibilitySettings(Context context) {
        if (context == null) return;
        try {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Không thể mở trang Accessibility: " + e.getMessage());
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Không thu thập dữ liệu người dùng
    }

    @Override
    public void onInterrupt() {
        Log.w(TAG, "AimAccessibilityService bị ngắt quãng");
    }
}
