package com.ff.cheat;

import android.content.Context;
import android.content.Intent;
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
 * FloatingMenu — In-Game Floating Mod Menu cho Free Fire
 * Hỗ trợ ESP Visuals, Aimbot Head Lock, UDP Desync Fake Lag, và Chế độ Thử nghiệm Test Preview
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

    // Màu sắc Cyberpunk Neon
    private static final int COLOR_BG_PANEL     = 0xF00D0E1A; // Nền tối bóng mờ
    private static final int COLOR_HEADER       = 0xFF14152A;
    private static final int COLOR_BORDER       = 0xFF00FFCC; // Viền neon xanh ngọc
    private static final int COLOR_ACCENT       = 0xFF7C4DFF; // Tím neon
    private static final int COLOR_TEXT_WHITE   = 0xFFFFFFFF;
    private static final int COLOR_TEXT_MUTED   = 0xFF8888AA;
    private static final int COLOR_GREEN        = 0xFF00E676;

    // Trạng thái Proxy & Fake Lag
    private int currentLagMs = 150;
    private boolean isProxyLagActive = false;
    private boolean isRootNetemActive = false;
    private TextView tvProxyStatus;
    private TextView tvGameStatus;

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

    // ─── 2. FLOATING MENU PANEL MỞ RỘNG (GIAO DIỆN CHÍNH) ──────────────────────
    private void createMenuView() {
        LinearLayout panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(COLOR_BG_PANEL);
        bg.setCornerRadius(dp(14));
        bg.setStroke(dp(1.5f), COLOR_BORDER);
        panel.setBackground(bg);
        panel.setPadding(dp(12), dp(10), dp(12), dp(12));

        // ── Header Bar ──
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, 0, 0, dp(6));

        TextView tvTitle = new TextView(context);
        tvTitle.setText("⚡ FF MOD MENU v2.0");
        tvTitle.setTextColor(COLOR_BORDER);
        tvTitle.setTextSize(15);
        tvTitle.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        header.addView(tvTitle, titleLp);

        // Nút Thu nhỏ
        Button btnMin = new Button(context);
        btnMin.setText("─");
        btnMin.setTextSize(13);
        btnMin.setTextColor(Color.WHITE);
        btnMin.setBackgroundColor(Color.TRANSPARENT);
        btnMin.setPadding(0, 0, 0, 0);
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(dp(36), dp(32));
        btnMin.setOnClickListener(v -> closeMenu());
        header.addView(btnMin, btnLp);

        panel.addView(header);

        // Kéo thả Menu thông qua Header
        header.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = menuParams.x;
                        initialY = menuParams.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        menuParams.x = initialX + (int) (event.getRawX() - initialTouchX);
                        menuParams.y = initialY + (int) (event.getRawY() - initialTouchY);
                        wm.updateViewLayout(menuView, menuParams);
                        return true;
                }
                return false;
            }
        });

        // ── Trạng thái kết nối Free Fire ──
        tvGameStatus = new TextView(context);
        tvGameStatus.setTextSize(11);
        tvGameStatus.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        tvGameStatus.setPadding(dp(2), 0, dp(2), dp(4));
        panel.addView(tvGameStatus);
        updateGameStatusBanner();

        // Nút Quét PID Free Fire
        Button btnScanPid = new Button(context);
        btnScanPid.setText("🔍 QUÉT PID FREE FIRE (ROOT / MÁY ẢO)");
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
                Toast.makeText(context, "Đã tìm thấy Free Fire PID: " + pid, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(context, "Chưa tìm thấy PID. Hãy mở game trước!", Toast.LENGTH_SHORT).show();
            }
            updateGameStatusBanner();
        });
        panel.addView(btnScanPid, scanLp);

        // ── Danh sách tính năng (ScrollView) ──
        ScrollView scroll = new ScrollView(context);
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);

        // Section: ESP VISUALS
        addSectionHeader(content, "ESP VISUALS");
        addSwitch(content, "ESP Master Switch", true, isChecked -> {
            CheatBridge.setESP(isChecked);
            if (espView != null) {
                espView.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            }
        });
        addSwitch(content, "🧪 Chế độ Mô phỏng (Test Preview)", espView != null && espView.isSimulationMode(), isChecked -> {
            if (espView != null) espView.setSimulationMode(isChecked);
        });
        addSwitch(content, "ESP Box", true, isChecked -> {
            if (espView != null) espView.setDrawBox(isChecked);
        });
        addSwitch(content, "Corner Box Style", true, isChecked -> {
            if (espView != null) espView.setCornerBoxMode(isChecked);
        });
        addSwitch(content, "Snapline (Bottom -> Feet)", true, isChecked -> {
            if (espView != null) espView.setDrawSnapline(isChecked);
        });
        addSwitch(content, "Health Bar (HP)", true, isChecked -> {
            if (espView != null) espView.setDrawHealth(isChecked);
        });
        addSwitch(content, "Distance Indicator", true, isChecked -> {
            if (espView != null) espView.setDrawDistance(isChecked);
        });
        addSwitch(content, "ESP Player Name (Tên kẻ địch)", espView != null && espView.isDrawName(), isChecked -> {
            if (espView != null) espView.setDrawName(isChecked);
        });
        addSwitch(content, "ESP Skeleton (Khung xương người)", espView != null && espView.isDrawSkeleton(), isChecked -> {
            if (espView != null) espView.setDrawSkeleton(isChecked);
        });
        addSwitch(content, "Aimbot FOV Circle", true, isChecked -> {
            if (espView != null) espView.setDrawFovCircle(isChecked);
        });
        addSwitch(content, "Aimbot Lock Ray (Tia ngắm đỏ)", true, isChecked -> {
            if (espView != null) espView.setDrawAimbotRay(isChecked);
        });

        // Section: AIMBOT ENGINE
        addSectionHeader(content, "AIMBOT ENGINE");
        addSwitch(content, "Aimbot Lock (Auto Head)", true, isChecked -> {
            CheatBridge.setAimbot(isChecked);
        });
        addSwitch(content, "Auto Drag Aim (Kéo tâm khi có Root)", RootUtils.isAutoAimDragEnabled(), isChecked -> {
            RootUtils.setAutoAimDrag(isChecked);
            if (isChecked && !RootUtils.isRootAvailable()) {
                Toast.makeText(context, "Cần cấp quyền Root để kích hoạt vuốt cảm ứng!", Toast.LENGTH_SHORT).show();
            }
        });

        // SeekBar chỉnh FOV Radius
        TextView tvFov = new TextView(context);
        tvFov.setText("FOV Radius: 180 px");
        tvFov.setTextColor(COLOR_TEXT_WHITE);
        tvFov.setTextSize(13);
        tvFov.setTypeface(Typeface.MONOSPACE);
        tvFov.setPadding(dp(4), dp(6), 0, 0);
        content.addView(tvFov);

        SeekBar sbFov = new SeekBar(context);
        sbFov.setMax(300); // 50 to 350
        sbFov.setProgress(130); // 180px
        sbFov.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float fov = 50 + progress;
                tvFov.setText("FOV Radius: " + (int) fov + " px");
                CheatBridge.setAimbotFov(fov);
                if (espView != null) espView.setFovRadius(fov);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        content.addView(sbFov);

        // Section: NETWORK PROXY & FAKE LAG
        addSectionHeader(content, "NETWORK PROXY & FAKE LAG");

        tvProxyStatus = new TextView(context);
        tvProxyStatus.setText("○ FAKE LAG: ĐÃ TẮT");
        tvProxyStatus.setTextColor(COLOR_TEXT_MUTED);
        tvProxyStatus.setTextSize(12);
        tvProxyStatus.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        tvProxyStatus.setPadding(dp(4), dp(4), dp(4), dp(4));
        content.addView(tvProxyStatus);

        addSwitch(content, "⚡ UDP Desync Ghost Lag (Thật 100%)", RootUtils.isUdpDesyncActive(), isChecked -> {
            RootUtils.setUdpDesyncLag(isChecked, currentLagMs);
            if (isChecked && !RootUtils.isRootAvailable()) {
                Toast.makeText(context, "UDP Desync cần Root hoặc Máy ảo (VMOS / VPhoneGaga)!", Toast.LENGTH_LONG).show();
            }
            updateProxyStatusText();
        });

        addSwitch(content, "SOCKS5 Proxy (127.0.0.1:8888)", CheatBridge.isFakeLagRunning(), isChecked -> {
            isProxyLagActive = isChecked;
            CheatBridge.applyFakeLag(isChecked, currentLagMs, isRootNetemActive);
            updateProxyStatusText();
        });

        TextView tvLagVal = new TextView(context);
        tvLagVal.setText("Độ trễ Fake Lag: " + currentLagMs + " ms");
        tvLagVal.setTextColor(COLOR_TEXT_WHITE);
        tvLagVal.setTextSize(13);
        tvLagVal.setTypeface(Typeface.MONOSPACE);
        tvLagVal.setPadding(dp(4), dp(6), 0, 0);
        content.addView(tvLagVal);

        SeekBar sbLag = new SeekBar(context);
        sbLag.setMax(950); // 50ms đến 1000ms
        sbLag.setProgress(currentLagMs - 50);
        sbLag.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                currentLagMs = 50 + progress;
                tvLagVal.setText("Độ trễ Fake Lag: " + currentLagMs + " ms");
                if (isProxyLagActive) {
                    CheatBridge.setFakeLagDelay(currentLagMs);
                }
                if (RootUtils.isUdpDesyncActive()) {
                    RootUtils.setUdpDesyncLag(false, 0);
                    RootUtils.setUdpDesyncLag(true, currentLagMs);
                }
                updateProxyStatusText();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        content.addView(sbLag);

        // Nút preset độ trễ nhanh
        LinearLayout presetRow = new LinearLayout(context);
        presetRow.setOrientation(LinearLayout.HORIZONTAL);
        presetRow.setPadding(dp(4), dp(2), dp(4), dp(6));

        int[] presets = {150, 300, 600, 900};
        for (int p : presets) {
            Button btnP = new Button(context);
            btnP.setText(p + "ms");
            btnP.setTextSize(11);
            btnP.setTextColor(Color.WHITE);
            btnP.setTypeface(Typeface.MONOSPACE);
            btnP.setPadding(0, 0, 0, 0);

            GradientDrawable pBg = new GradientDrawable();
            pBg.setColor(0xFF1E213D);
            pBg.setCornerRadius(dp(6));
            pBg.setStroke(dp(1), COLOR_BORDER);
            btnP.setBackground(pBg);

            LinearLayout.LayoutParams pLp = new LinearLayout.LayoutParams(0, dp(32), 1f);
            pLp.setMargins(dp(2), 0, dp(2), 0);
            btnP.setOnClickListener(v -> {
                currentLagMs = p;
                sbLag.setProgress(p - 50);
                tvLagVal.setText("Độ trễ Fake Lag: " + currentLagMs + " ms");
                if (isProxyLagActive) {
                    CheatBridge.setFakeLagDelay(currentLagMs);
                }
                if (RootUtils.isUdpDesyncActive()) {
                    RootUtils.setUdpDesyncLag(false, 0);
                    RootUtils.setUdpDesyncLag(true, currentLagMs);
                }
                updateProxyStatusText();
            });
            presetRow.addView(btnP, pLp);
        }
        content.addView(presetRow);

        addSwitch(content, "Kernel Netem (Linux tc)", isRootNetemActive, isChecked -> {
            isRootNetemActive = isChecked;
            if (isProxyLagActive) {
                CheatBridge.applyFakeLag(true, currentLagMs, isRootNetemActive);
            }
        });

        // Section: MISC & TOOLS
        addSectionHeader(content, "MISC & TOOLS");

        // Nút Mở Offset Dumper
        Button btnOpenDumper = new Button(context);
        btnOpenDumper.setText("MỞ OFFSET DUMPER");
        btnOpenDumper.setTextSize(13);
        btnOpenDumper.setTextColor(Color.WHITE);
        btnOpenDumper.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        GradientDrawable btnBg = new GradientDrawable();
        btnBg.setColor(COLOR_ACCENT);
        btnBg.setCornerRadius(dp(8));
        btnOpenDumper.setBackground(btnBg);
        LinearLayout.LayoutParams btnDumpLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40));
        btnDumpLp.setMargins(dp(4), dp(6), dp(4), dp(4));
        btnOpenDumper.setOnClickListener(v -> {
            Intent intent = new Intent(context, DumperActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        });
        content.addView(btnOpenDumper, btnDumpLp);

        scroll.addView(content);
        panel.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(300)));

        menuParams = new WindowManager.LayoutParams(
                dp(280), ViewGroup.LayoutParams.WRAP_CONTENT,
                getOverlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        menuParams.gravity = Gravity.TOP | Gravity.START;
        menuParams.x = dp(20);
        menuParams.y = dp(100);

        menuView = panel;
    }

    private void addSectionHeader(LinearLayout container, String title) {
        TextView tv = new TextView(context);
        tv.setText("── " + title + " ──");
        tv.setTextColor(COLOR_TEXT_MUTED);
        tv.setTextSize(11);
        tv.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        tv.setPadding(dp(4), dp(10), 0, dp(4));
        container.addView(tv);
    }

    private void addSwitch(LinearLayout container, String label, boolean def, SwitchCallback cb) {
        Switch sw = new Switch(context);
        sw.setText(label);
        sw.setTextColor(COLOR_TEXT_WHITE);
        sw.setTextSize(13);
        sw.setChecked(def);
        sw.setPadding(dp(4), dp(4), dp(4), dp(4));
        sw.setOnCheckedChangeListener((btn, isChecked) -> cb.onToggle(isChecked));
        container.addView(sw);
    }

    private interface SwitchCallback {
        void onToggle(boolean isChecked);
    }

    // ─── HIỂN THỊ / ẨN ────────────────────────────────────────────────────────
    public void showBubble() {
        if (bubbleView != null && bubbleView.getWindowToken() == null) {
            wm.addView(bubbleView, bubbleParams);
        }
    }

    public void openMenu() {
        if (menuView != null) {
            updateGameStatusBanner();
            menuParams.x = Math.max(dp(10), bubbleParams.x);
            menuParams.y = Math.max(dp(20), bubbleParams.y);

            if (bubbleView != null && bubbleView.getWindowToken() != null) {
                wm.removeView(bubbleView);
            }
            if (menuView.getWindowToken() == null) {
                wm.addView(menuView, menuParams);
            }
            isMenuShowing = true;
        }
    }

    public void closeMenu() {
        if (menuView != null && menuView.getWindowToken() != null) {
            wm.removeView(menuView);
        }
        if (bubbleView != null && bubbleView.getWindowToken() == null) {
            bubbleParams.x = menuParams.x;
            bubbleParams.y = menuParams.y;
            wm.addView(bubbleView, bubbleParams);
        }
        isMenuShowing = false;
    }

    public boolean isMenuShowing() {
        return isMenuShowing;
    }

    public void toggleMenu() {
        if (isMenuShowing) {
            closeMenu();
        } else {
            openMenu();
        }
    }

    private void updateGameStatusBanner() {
        if (tvGameStatus == null) return;
        boolean connected = CheatBridge.isGameConnected();
        int pid = CheatBridge.getGamePid();
        if (connected && pid > 0) {
            tvGameStatus.setText("● FREE FIRE: ĐÃ HOOK GAME (PID: " + pid + ")");
            tvGameStatus.setTextColor(COLOR_GREEN);
        } else if (pid > 0) {
            tvGameStatus.setText("○ FREE FIRE: ĐÃ TÌM PID " + pid + " (Chờ Offsets...)");
            tvGameStatus.setTextColor(0xFFFFD600);
        } else {
            tvGameStatus.setText("○ FREE FIRE: ĐANG MÔ PHỎNG (Chưa vào trận)");
            tvGameStatus.setTextColor(COLOR_TEXT_MUTED);
        }
    }

    private void updateProxyStatusText() {
        if (tvProxyStatus != null) {
            if (RootUtils.isUdpDesyncActive()) {
                tvProxyStatus.setText("● UDP GHOST DESYNC: BẬT [" + currentLagMs + "ms]");
                tvProxyStatus.setTextColor(COLOR_GREEN);
            } else if (isProxyLagActive) {
                tvProxyStatus.setText("● SOCKS5 PROXY: 127.0.0.1:8888 [" + currentLagMs + "ms]");
                tvProxyStatus.setTextColor(COLOR_GREEN);
            } else {
                tvProxyStatus.setText("○ FAKE LAG: ĐÃ TẮT");
                tvProxyStatus.setTextColor(COLOR_TEXT_MUTED);
            }
        }
    }

    public void destroy() {
        try {
            if (menuView != null && menuView.getWindowToken() != null) {
                wm.removeView(menuView);
            }
            if (bubbleView != null && bubbleView.getWindowToken() != null) {
                wm.removeView(bubbleView);
            }
        } catch (Exception ignored) {}
    }
}
