package com.ff.cheat;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * FloatingAimTrigger — Nút Kéo Tâm Trợ Lực Ảo Nổi Trên Màn Hình
 * Giải pháp kéo tâm Headshot 100% cho máy Không Root (No-Root) & Xiaomi HyperOS:
 * Người chơi kéo nút này đặt đè lên Nút Bắn (Fire Button) trong Free Fire.
 * Khi chạm vào nút để bắn, hệ thống tự động thực hiện thao tác vuốt tâm chữ J (J-Curve Drag)
 * hướng thẳng lên đầu đối thủ, tạo cú bắn Headshot hoàn hảo mà không cần can thiệp bộ nhớ game!
 */
public class FloatingAimTrigger {

    private final Context context;
    private final WindowManager wm;
    private LinearLayout container;
    private WindowManager.LayoutParams triggerParams;
    private boolean isShowing = false;
    private Vibrator vibrator;

    private static final int COLOR_BORDER  = 0xFFFF1744; // Đỏ Neon
    private static final int COLOR_BG      = 0xCC1A0A10; // Đỏ tối bán trong suốt
    private static final int COLOR_MOVE_BG = 0xEE111827; // Xám đen cho thanh cầm di chuyển
    private static final int COLOR_CYAN    = 0xFF00FFCC; // Neon Cyan

    public FloatingAimTrigger(Context context, WindowManager wm) {
        this.context = context;
        this.wm = wm;
        try {
            vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        } catch (Exception ignored) {}
        createTriggerView();
    }

    private int dp(float dp) {
        float density = context.getResources().getDisplayMetrics().density;
        return (int) (dp * density + 0.5f);
    }

    private int getOverlayType() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
    }

    private void triggerHaptic() {
        if (vibrator == null) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(18, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(18);
            }
        } catch (Exception ignored) {}
    }

    private void createTriggerView() {
        container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setGravity(Gravity.CENTER_HORIZONTAL);

        // 1. Thanh cầm di chuyển nút (Move Handle)
        TextView tvMove = new TextView(context);
        tvMove.setText("✥ DI CHUYỂN");
        tvMove.setTextColor(COLOR_CYAN);
        tvMove.setTextSize(9);
        tvMove.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        tvMove.setGravity(Gravity.CENTER);
        tvMove.setPadding(dp(8), dp(3), dp(8), dp(3));

        GradientDrawable moveBg = new GradientDrawable();
        moveBg.setShape(GradientDrawable.RECTANGLE);
        moveBg.setCornerRadius(dp(8));
        moveBg.setColor(COLOR_MOVE_BG);
        moveBg.setStroke(dp(1), COLOR_CYAN);
        tvMove.setBackground(moveBg);

        LinearLayout.LayoutParams moveLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        moveLp.setMargins(0, 0, 0, dp(4));
        container.addView(tvMove, moveLp);

        // 2. Nút Bắn & Kéo Tâm Headshot chính (Tròn, đặt đè lên Nút Bắn Free Fire)
        TextView btnFire = new TextView(context);
        btnFire.setText("🔥\nKÉO TÂM");
        btnFire.setTextColor(Color.WHITE);
        btnFire.setTextSize(10);
        btnFire.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        btnFire.setGravity(Gravity.CENTER);

        GradientDrawable fireBg = new GradientDrawable();
        fireBg.setShape(GradientDrawable.OVAL);
        fireBg.setColor(COLOR_BG);
        fireBg.setStroke(dp(2), COLOR_BORDER);
        btnFire.setBackground(fireBg);

        LinearLayout.LayoutParams fireLp = new LinearLayout.LayoutParams(dp(58), dp(58));
        container.addView(btnFire, fireLp);

        // Cấu hình LayoutParams cho WindowManager
        triggerParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                getOverlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
        );
        triggerParams.gravity = Gravity.BOTTOM | Gravity.END;
        triggerParams.x = dp(80);
        triggerParams.y = dp(140);

        // Sự kiện vuốt thanh cầm để DI CHUYỂN nút trên màn hình
        tvMove.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = triggerParams.x;
                        initialY = triggerParams.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        float deltaX = event.getRawX() - initialTouchX;
                        float deltaY = event.getRawY() - initialTouchY;
                        triggerParams.x = initialX - (int) deltaX;
                        triggerParams.y = initialY - (int) deltaY;
                        try {
                            wm.updateViewLayout(container, triggerParams);
                        } catch (Exception ignored) {}
                        return true;
                }
                return false;
            }
        });

        // Sự kiện chạm vào Nút Bắn để BẮN & KÉO TÂM HEADSHOT
        btnFire.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    triggerHaptic();

                    // Kiểm tra Trợ Năng
                    boolean hasAccessibility = AimAccessibilityService.isServiceRunning();
                    boolean hasRoot = RootUtils.isRootAvailable();

                    if (!hasAccessibility && !hasRoot) {
                        Toast.makeText(context, "Chưa bật Dịch Vụ Trợ Năng! Đang mở Cài đặt...", Toast.LENGTH_SHORT).show();
                        AimAccessibilityService.openAccessibilitySettings(context);
                        return true;
                    }

                    // Tọa độ thực tế trên màn hình của nút
                    int[] loc = new int[2];
                    v.getLocationOnScreen(loc);
                    float startX = loc[0] + (v.getWidth() * 0.5f);
                    float startY = loc[1] + (v.getHeight() * 0.5f);

                    // Tạm thời vô hiệu hóa nhận diện cảm ứng để gesture truyền thẳng xuống game bên dưới
                    triggerParams.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
                    try {
                        wm.updateViewLayout(container, triggerParams);
                    } catch (Exception ignored) {}

                    // Kích hoạt kéo tâm Bezier chữ J tự động
                    RootUtils.triggerManualAimDrag(startX, startY);

                    // Khôi phục lại quyền cảm ứng sau 65ms
                    v.postDelayed(() -> {
                        if (container != null && isShowing) {
                            triggerParams.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
                            try {
                                wm.updateViewLayout(container, triggerParams);
                            } catch (Exception ignored) {}
                        }
                    }, 65);

                    return true;
                }
                return false;
            }
        });
    }

    public void show() {
        if (!isShowing && container != null && container.getWindowToken() == null) {
            try {
                wm.addView(container, triggerParams);
                isShowing = true;
                Toast.makeText(context, "Đã mở Nút Kéo Tâm Ảo! Hãy kéo thanh '✥ DI CHUYỂN' đặt đè lên Nút Bắn trong game.", Toast.LENGTH_LONG).show();
            } catch (Exception ignored) {}
        }
    }

    public void hide() {
        if (isShowing && container != null && container.getWindowToken() != null) {
            try {
                wm.removeView(container);
                isShowing = false;
            } catch (Exception ignored) {}
        }
    }

    public boolean isShowing() {
        return isShowing;
    }

    public void destroy() {
        hide();
        container = null;
    }
}
