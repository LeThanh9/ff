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
 * FloatingMenu — Menu Nổi Hiện Đại Trong Trận (100% Tiếng Việt)
 * Quản lý toàn bộ chức năng:
 * 1. ESP Khung xương 3D, Hộp góc, Tâm định vị cổ, Thanh máu, Snapline
 * 2. Vòng quét FOV & Tự động kéo tâm khóa cổ (Drag Headshot)
 * 3. Quét nhanh PID Free Fire cho máy Root / Máy ảo
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

    // Bảng màu Cyberpunk Neon cao cấp
    private static final int COLOR_BG_PANEL     = 0xF20D0E1A;
    private static final int COLOR_BORDER       = 0xFF00FFCC; // Neon Cyan
    private static final int COLOR_ACCENT       = 0xFFFF1744; // Đỏ Neon
    private static final int COLOR_TEXT_WHITE   = 0xFFFFFFFF;
    private static final int COLOR_TEXT_MUTED   = 0xFF9999BB;
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

    // ─── 1. BONG BÓNG BIỂU TƯỢNG NỔI NHỎ GỌN TRÊN MÀN HÌNH ─────────────────
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
                        if (diffX < 12 && diffY < 12) {
                            openMenu();
                        }
                        return true;
                }
                return false;
            }
        });

        bubbleView = bubble;
    }

    // ─── 2. BẢNG ĐIỀU KHIỂN MENU NỔI (100% TIẾNG VIỆT) ─────────────────────
    private void createMenuView() {
        LinearLayout panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(14), dp(12), dp(14), dp(14));

        GradientDrawable panelBg = new GradientDrawable();
        panelBg.setColor(COLOR_BG_PANEL);
        panelBg.setCornerRadius(dp(14));
        panelBg.setStroke(dp(2), COLOR_BORDER);
        panel.setBackground(panelBg);

        // Thanh Tiêu Đề
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, 0, 0, dp(8));

        TextView title = new TextView(context);
        title.setText("⚡ MENU HỖ TRỢ FREE FIRE PRO");
        title.setTextColor(COLOR_BORDER);
        title.setTextSize(13);
        title.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        header.addView(title, titleLp);

        // Nút Đóng Thu Nhỏ
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

        // Trạng thái kết nối bộ nhớ game
        tvGameStatus = new TextView(context);
        tvGameStatus.setTextSize(11);
        tvGameStatus.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        tvGameStatus.setPadding(dp(2), 0, dp(2), dp(4));
        panel.addView(tvGameStatus);
        updateGameStatusBanner();

        // Nút Quét PID Free Fire
        Button btnScanPid = new Button(context);
        btnScanPid.setText("🔍 QUÉT PID FREE FIRE (GLOBAL / MAX / VNG)");
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
                Toast.makeText(context, "Đã liên kết tiến trình Free Fire: PID " + pid, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(context, "Chưa tìm thấy tiến trình. Hãy mở game trước!", Toast.LENGTH_SHORT).show();
            }
            updateGameStatusBanner();
        });
        panel.addView(btnScanPid, scanLp);

        // Nội dung cuộn
        ScrollView scroll = new ScrollView(context);
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);

        // ══════════════════════════════════════════════════════════════
        // PHẦN 1: ESP HIỂN THỊ CHIẾN TRƯỜNG
        // ══════════════════════════════════════════════════════════════
        addSectionHeader(content, "1. ESP HIỂN THỊ CHIẾN TRƯỜNG");
        addSwitch(content, "Bật Lớp Phủ ESP", true, isChecked -> {
            CheatBridge.setESP(isChecked);
            if (espView != null) {
                espView.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            }
        });
        addSwitch(content, "🧪 Chế độ Mô phỏng Xem Trước", espView != null && espView.isSimulationMode(), isChecked -> {
            if (espView != null) espView.setSimulationMode(isChecked);
        });
        addSwitch(content, "Vẽ Khung Xương Đầy Đủ (Skeleton 3D)", espView != null && espView.isDrawSkeleton(), isChecked -> {
            if (espView != null) espView.setDrawSkeleton(isChecked);
        });
        addSwitch(content, "Vẽ Tâm Định Vị Cổ (Neck Crosshair)", espView != null && espView.isDrawNeckCrosshair(), isChecked -> {
            if (espView != null) espView.setDrawNeckCrosshair(isChecked);
        });
        addSwitch(content, "Vẽ Khung Hộp Góc (Corner Box)", espView != null && espView.isDrawBox(), isChecked -> {
            if (espView != null) espView.setDrawBox(isChecked);
        });
        addSwitch(content, "Vẽ Thanh Máu & Khoảng Cách Mét", espView != null && espView.isDrawHealthBar(), isChecked -> {
            if (espView != null) espView.setDrawHealthBar(isChecked);
        });
        addSwitch(content, "Vẽ Đường Dẫn Hướng (Snapline từ đáy)", espView != null && espView.isDrawSnapline(), isChecked -> {
            if (espView != null) espView.setDrawSnapline(isChecked);
        });

        // ══════════════════════════════════════════════════════════════
        // PHẦN 2: TỰ ĐỘNG KHÓA CỔ & KÉO TÂM (AIMBOT)
        // ══════════════════════════════════════════════════════════════
        addSectionHeader(content, "2. TỰ ĐỘNG KHÓA CỔ & KÉO TÂM (AIMBOT)");

        addSwitch(content, "🎯 Hiển Thị Vòng Tròn FOV", espView != null && espView.isDrawFovCircle(), isChecked -> {
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

        // Công tắc tự động vuốt kéo tâm khóa vào Cổ
        addSwitch(content, "⚡ Tự Động Vuốt Kéo Tâm Khóa Cổ", RootUtils.isAutoAimDragEnabled(), isChecked -> {
            RootUtils.setAutoAimDrag(isChecked);
            if (espView != null) espView.setAutoNeckAim(isChecked);
            CheatBridge.setAimbot(isChecked);

            if (isChecked && !RootUtils.isAimDragSupported()) {
                Toast.makeText(context, "Lưu ý máy No-Root / Xiaomi: Hãy bật 'Dịch Vụ Trợ Năng' để tự động vuốt tâm!", Toast.LENGTH_LONG).show();
            }
        });

        // Nút mở nhanh Trợ Năng cho máy No-Root / Xiaomi HyperOS
        Button btnQuickAccess = new Button(context);
        btnQuickAccess.setText("⚙️ CÀI ĐẶT TRỢ NĂNG (CHO MÁY NO-ROOT)");
        btnQuickAccess.setTextSize(10);
        btnQuickAccess.setTextColor(Color.WHITE);
        btnQuickAccess.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        GradientDrawable accBg = new GradientDrawable();
        accBg.setColor(0xFF1B2E4B);
        accBg.setCornerRadius(dp(6));
        accBg.setStroke(dp(1), 0xFF00AAFF);
        btnQuickAccess.setBackground(accBg);
        LinearLayout.LayoutParams accLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(30));
        accLp.setMargins(dp(2), dp(2), dp(2), dp(6));
        btnQuickAccess.setOnClickListener(v -> {
            AimAccessibilityService.openAccessibilitySettings(context);
            Toast.makeText(context, "Bật 'Dịch Vụ Hỗ Trợ Free Fire' để vuốt tâm không cần Root", Toast.LENGTH_SHORT).show();
        });
        content.addView(btnQuickAccess, accLp);

        // Thanh trượt điều chỉnh độ nhạy vuốt kéo tâm
        float currentSens = RootUtils.getAimDragSensitivity();
        TextView tvSens = new TextView(context);
        tvSens.setText("Độ Nhạy Kéo Tâm: " + String.format("%.1fx", currentSens));
        tvSens.setTextColor(COLOR_TEXT_WHITE);
        tvSens.setTextSize(12);
        tvSens.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        tvSens.setPadding(dp(4), dp(6), 0, 0);
        content.addView(tvSens);

        SeekBar sbSens = new SeekBar(context);
        sbSens.setMax(20); // 0.5x đến 2.5x
        int sensProgress = (int) Math.round((currentSens - 0.5f) * 10);
        sbSens.setProgress(Math.max(0, Math.min(20, sensProgress)));
        sbSens.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float sens = 0.5f + (progress * 0.1f);
                tvSens.setText("Độ Nhạy Kéo Tâm: " + String.format("%.1fx", sens));
                RootUtils.setAimDragSensitivity(sens);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        content.addView(sbSens);

        TextView tvHelp = new TextView(context);
        tvHelp.setText("ℹ️ Cơ chế Drag Headshot: Khi địch bước vào Vòng FOV, tâm ngắm được định vị chuẩn xác tại đốt cổ và hỗ trợ lực vuốt nhẹ hướng lên đầu, mang lại tỉ lệ 100% Headshot tự nhiên.");
        tvHelp.setTextColor(COLOR_TEXT_MUTED);
        tvHelp.setTextSize(10);
        tvHelp.setPadding(dp(4), dp(6), dp(4), dp(10));
        content.addView(tvHelp);

        scroll.addView(content);
        panel.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));

        menuParams = new WindowManager.LayoutParams(
                dp(290), dp(390),
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
        int pid = CheatBridge.getGamePid();
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
