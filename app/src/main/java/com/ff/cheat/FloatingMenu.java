package com.ff.cheat;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

/**
 * FloatingMenu — Menu Nổi Tinh Gọn cho Free Fire
 * 1. ESP Đọc Bộ Nhớ (Vẽ Box & Skeleton)
 * 2. Vòng Aimbot FOV có thể kéo chỉnh bán kính to/nhỏ
 * 3. Tự động xoay góc nhìn camera khóa vào Cổ (Auto Neck View Rotation) khi đối thủ vào vòng
 */
public class FloatingMenu {

    private final Context context;
    private final WindowManager wm;
    private final ESPView espView;

    private View bubbleView;
    private View menuView;
    private WindowManager.LayoutParams bubbleParams;
    private WindowManager.LayoutParams menuParams;

    private boolean isMenuShowing = false;
    private TextView tvGameStatus;

    // Màu sắc Cyberpunk
    private static final int COLOR_BG_PANEL     = 0xF20D0E1A; // Nền tối bóng mờ
    private static final int COLOR_HEADER       = 0xFF14152A;
    private static final int COLOR_BORDER       = 0xFF00FFCC; // Neon Cyan
    private static final int COLOR_ACCENT       = 0xFFFF1744; // Đỏ Neon
    private static final int COLOR_TEXT_WHITE   = 0xFFFFFFFF;
    private static final int COLOR_TEXT_MUTED   = 0xFF8888AA;
    private static final int COLOR_GREEN        = 0xFF00E676;

    public FloatingMenu(Context context, WindowManager wm, ESPView espView) {
        this.context = context;
        this.wm = wm;
        this.espView = espView;

        createBubbleView();
        createMenuView();
        showBubble();
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

    // ─── 1. BUBBLE ICON NỔI NHỎ GỌN TRÊN MÀN HÌNH ────────────────────────────
    private void createBubbleView() {
        TextView bubble = new TextView(context);
        bubble.setText("⚡FF");
        bubble.setTextColor(Color.WHITE);
        bubble.setTextSize(14);
        bubble.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        bubble.setGravity(Gravity.CENTER);

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(0xE614142B);
        bg.setStroke(dp(2), COLOR_BORDER);
        bubble.setBackground(bg);

        bubbleParams = new WindowManager.LayoutParams(
                dp(52), dp(52),
                getOverlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        bubbleParams.gravity = Gravity.TOP | Gravity.START;
        bubbleParams.x = dp(20);
        bubbleParams.y = dp(120);

        bubble.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = bubbleParams.x;
                        initialY = bubbleParams.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        bubbleParams.x = initialX + (int) (event.getRawX() - initialTouchX);
                        bubbleParams.y = initialY + (int) (event.getRawY() - initialTouchY);
                        wm.updateViewLayout(bubbleView, bubbleParams);
                        return true;

                    case MotionEvent.ACTION_UP:
                        float diffX = Math.abs(event.getRawX() - initialTouchX);
                        float diffY = Math.abs(event.getRawY() - initialTouchY);
                        if (diffX < 10 && diffY < 10) {
                            openMenu();
                        }
                        return true;
                }
                return false;
            }
        });

        bubbleView = bubble;
    }

    // ─── 2. FLOATING MENU PANEL TINH GỌN (GIAO DIỆN CHÍNH) ───────────────────
    private void createMenuView() {
        LinearLayout panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(14), dp(12), dp(14), dp(14));

        GradientDrawable panelBg = new GradientDrawable();
        panelBg.setColor(COLOR_BG_PANEL);
        panelBg.setCornerRadius(dp(14));
        panelBg.setStroke(dp(2), COLOR_BORDER);
        panel.setBackground(panelBg);

        // Header Bar
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, 0, 0, dp(8));

        TextView title = new TextView(context);
        title.setText("⚡ FF | ESP & AUTO AIM CỔ");
        title.setTextColor(COLOR_BORDER);
        title.setTextSize(14);
        title.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        header.addView(title, titleLp);

        // Nút thu nhỏ
        Button btnClose = new Button(context);
        btnClose.setText("✕");
        btnClose.setTextColor(Color.WHITE);
        btnClose.setTextSize(14);
        btnClose.setTypeface(Typeface.DEFAULT_BOLD);
        btnClose.setPadding(0, 0, 0, 0);
        GradientDrawable closeBg = new GradientDrawable();
        closeBg.setColor(0xFF2E1C2B);
        closeBg.setCornerRadius(dp(12));
        closeBg.setStroke(dp(1), COLOR_ACCENT);
        btnClose.setBackground(closeBg);
        LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(dp(28), dp(28));
        btnClose.setOnClickListener(v -> closeMenu());
        header.addView(btnClose, closeLp);

        panel.addView(header);

        // Trạng thái kết nối Free Fire
        tvGameStatus = new TextView(context);
        tvGameStatus.setTextSize(11);
        tvGameStatus.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        tvGameStatus.setPadding(dp(2), 0, dp(2), dp(4));
        panel.addView(tvGameStatus);
        updateGameStatusBanner();

        // Nút Quét PID Free Fire
        Button btnScanPid = new Button(context);
        btnScanPid.setText("🔍 QUÉT PID BỘ NHỚ FREE FIRE");
        btnScanPid.setTextSize(11);
        btnScanPid.setTextColor(Color.WHITE);
        btnScanPid.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        GradientDrawable scanBg = new GradientDrawable();
        scanBg.setColor(0xFF1B2038);
        scanBg.setCornerRadius(dp(6));
        scanBg.setStroke(dp(1), COLOR_BORDER);
        btnScanPid.setBackground(scanBg);
        LinearLayout.LayoutParams scanLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(32));
        scanLp.setMargins(dp(2), 0, dp(2), dp(6));
        btnScanPid.setOnClickListener(v -> {
            int pid = RootUtils.findFreeFirePid();
            if (pid > 0) {
                CheatBridge.setTargetPid(pid);
                Toast.makeText(context, "Đã kết nối PID Free Fire: " + pid, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(context, "Chưa tìm thấy PID. Hãy mở game trước!", Toast.LENGTH_SHORT).show();
            }
            updateGameStatusBanner();
        });
        panel.addView(btnScanPid, scanLp);

        // Scrollable content
        ScrollView scroll = new ScrollView(context);
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);

        // ══════════════════════════════════════════════════════════════
        // PHẦN 1: ESP ĐỌC BỘ NHỚ
        // ══════════════════════════════════════════════════════════════
        addSectionHeader(content, "1. ESP ĐỌC BỘ NHỚ");
        addSwitch(content, "Bật ESP Đọc Bộ Nhớ", true, isChecked -> {
            CheatBridge.setESP(isChecked);
            if (espView != null) {
                espView.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            }
        });
        addSwitch(content, "🧪 Chế độ Mô phỏng (Test Preview)", espView != null && espView.isSimulationMode(), isChecked -> {
            if (espView != null) espView.setSimulationMode(isChecked);
        });
        addSwitch(content, "ESP Khung Hộp (Box)", espView != null && espView.isDrawBox(), isChecked -> {
            if (espView != null) espView.setDrawBox(isChecked);
        });
        addSwitch(content, "ESP Khung Xương (Skeleton)", espView != null && espView.isDrawSkeleton(), isChecked -> {
            if (espView != null) espView.setDrawSkeleton(isChecked);
        });

        // ══════════════════════════════════════════════════════════════
        // PHẦN 2: VÒNG AIMBOT & TỰ ĐỘNG KHÓA CỔ (XOAY GÓC NHÌN)
        // ══════════════════════════════════════════════════════════════
        addSectionHeader(content, "2. AIMBOT XOAY GÓC NHÌN VÀO CỔ");

        addSwitch(content, "🎯 Vòng Aimbot FOV", espView != null && espView.isDrawFovCircle(), isChecked -> {
            if (espView != null) espView.setDrawFovCircle(isChecked);
        });

        // Thanh trượt điều chỉnh bán kính Vòng FOV
        int currentFov = espView != null ? (int) espView.getFovRadius() : 180;
        TextView tvFov = new TextView(context);
        tvFov.setText("Bán kính Vòng FOV: " + currentFov + " px");
        tvFov.setTextColor(COLOR_TEXT_WHITE);
        tvFov.setTextSize(12);
        tvFov.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        tvFov.setPadding(dp(4), dp(6), 0, 0);
        content.addView(tvFov);

        SeekBar sbFov = new SeekBar(context);
        sbFov.setMax(350); // 50px đến 400px
        sbFov.setProgress(currentFov - 50);
        sbFov.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float fov = 50 + progress;
                tvFov.setText("Bán kính Vòng FOV: " + (int) fov + " px");
                CheatBridge.setAimbotFov(fov);
                if (espView != null) espView.setFovRadius(fov);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        content.addView(sbFov);

        // Công tắc tự động xoay góc nhìn camera khóa vào Cổ
        addSwitch(content, "⚡ Tự động Xoay Góc Nhìn vào Cổ", RootUtils.isAutoAimDragEnabled(), isChecked -> {
            RootUtils.setAutoAimDrag(isChecked);
            if (espView != null) espView.setAutoNeckAim(isChecked);
            CheatBridge.setAimbot(isChecked);

            if (isChecked && !RootUtils.isRootAvailable()) {
                Toast.makeText(context, "Cần cấp quyền Root / Máy ảo để thực thi xoay góc nhìn!", Toast.LENGTH_LONG).show();
            }
        });

        TextView tvHelp = new TextView(context);
        tvHelp.setText("ℹ️ Khi đối thủ bước vào trong Vòng FOV, hệ thống sẽ tự động xoay góc nhìn camera hướng thẳng vào đốt sống cổ để hỗ trợ bạn vuốt bắn 100% Headshot.");
        tvHelp.setTextColor(COLOR_TEXT_MUTED);
        tvHelp.setTextSize(10);
        tvHelp.setPadding(dp(4), dp(4), dp(4), dp(8));
        content.addView(tvHelp);

        scroll.addView(content);
        panel.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));

        menuParams = new WindowManager.LayoutParams(
                dp(280), dp(370),
                getOverlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        menuParams.gravity = Gravity.CENTER;

        menuView = panel;
    }

    private void addSectionHeader(LinearLayout parent, String title) {
        TextView tv = new TextView(context);
        tv.setText(title);
        tv.setTextColor(COLOR_BORDER);
        tv.setTextSize(11);
        tv.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        tv.setPadding(dp(2), dp(8), 0, dp(4));
        parent.addView(tv);

        View line = new View(context);
        line.setBackgroundColor(0x3300FFCC);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        lp.setMargins(0, 0, 0, dp(4));
        parent.addView(line, lp);
    }

    private void addSwitch(LinearLayout parent, String text, boolean defaultVal, SwitchListener listener) {
        Switch sw = new Switch(context);
        sw.setText(text);
        sw.setTextColor(COLOR_TEXT_WHITE);
        sw.setTextSize(12);
        sw.setChecked(defaultVal);
        sw.setPadding(dp(2), dp(4), dp(2), dp(4));
        sw.setOnCheckedChangeListener((btn, isChecked) -> listener.onChanged(isChecked));
        parent.addView(sw);
    }

    public void updateGameStatusBanner() {
        if (tvGameStatus == null) return;
        boolean connected = CheatBridge.isGameConnected();
        int pid = CheatBridge.getTargetPid();
        if (connected && pid > 0) {
            tvGameStatus.setText("● BỘ NHỚ: ĐÃ KẾT NỐI (PID " + pid + ")");
            tvGameStatus.setTextColor(COLOR_GREEN);
        } else {
            tvGameStatus.setText("○ BỘ NHỚ: ĐANG CHỜ GAME / ROOT...");
            tvGameStatus.setTextColor(COLOR_TEXT_MUTED);
        }
    }

    public void showBubble() {
        if (bubbleView != null && bubbleView.getWindowToken() == null) {
            wm.addView(bubbleView, bubbleParams);
        }
    }

    public void openMenu() {
        if (isMenuShowing) return;
        updateGameStatusBanner();
        if (menuView != null && menuView.getWindowToken() == null) {
            wm.addView(menuView, menuParams);
            isMenuShowing = true;
        }
    }

    public void closeMenu() {
        if (!isMenuShowing) return;
        if (menuView != null && menuView.getWindowToken() != null) {
            wm.removeView(menuView);
            isMenuShowing = false;
        }
    }

    public void toggleMenu() {
        if (isMenuShowing) {
            closeMenu();
        } else {
            openMenu();
        }
    }

    public void destroy() {
        closeMenu();
        if (bubbleView != null && bubbleView.getWindowToken() != null) {
            wm.removeView(bubbleView);
        }
    }

    private interface SwitchListener {
        void onChanged(boolean isChecked);
    }
}
