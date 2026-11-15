package com.ff.cheat;

import android.app.Activity;
import android.app.AppOpsManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.Process;
import android.provider.Settings;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.lang.reflect.Method;

/**
 * MainActivity — Màn hình quản trị trung tâm
 * Tối ưu hóa 100% cho máy Không Root (No-Root), Xiaomi HyperOS / MIUI, Samsung OneUI,
 * ColorOS, và Android 8.0 đến Android 14/15.
 */
public class MainActivity extends Activity {

    private Switch switchESP, switchAimbot, switchFakeLag, switchRootNetem, switchAntiBanFirewall;
    private Button btnInject, btnStop, btnDumper, btnShowMenu, btnRemovePatch, btnInstallPatch;
    private Button btnOpenAccessibility, btnXiaomiPerm, btnIgnoreBattery;
    private TextView tvStatus, tvRootStatus, tvOverlayStatus, tvAccessibilityStatus, tvProxyInfo, tvLagValue, tvPatchStatus;
    private SeekBar sbFakeLag;
    private int currentLagMs = 150;

    private static final int REQUEST_OVERLAY_PERMISSION = 1001;

    static {
        System.loadLibrary("ffcheat");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        switchESP             = findViewById(R.id.switchESP);
        switchAimbot          = findViewById(R.id.switchAimbot);
        switchFakeLag         = findViewById(R.id.switchFakeLag);
        switchRootNetem       = findViewById(R.id.switchRootNetem);
        switchAntiBanFirewall = findViewById(R.id.switchAntiBanFirewall);

        btnInject             = findViewById(R.id.btnInject);
        btnShowMenu           = findViewById(R.id.btnShowMenu);
        btnStop               = findViewById(R.id.btnStop);
        btnDumper             = findViewById(R.id.btnDumper);
        btnRemovePatch        = findViewById(R.id.btnRemovePatch);
        btnInstallPatch       = findViewById(R.id.btnInstallPatch);

        btnOpenAccessibility  = findViewById(R.id.btnOpenAccessibility);
        btnXiaomiPerm         = findViewById(R.id.btnXiaomiPerm);
        btnIgnoreBattery      = findViewById(R.id.btnIgnoreBattery);

        tvStatus              = findViewById(R.id.tvStatus);
        tvRootStatus          = findViewById(R.id.tvRootStatus);
        tvOverlayStatus       = findViewById(R.id.tvOverlayStatus);
        tvAccessibilityStatus = findViewById(R.id.tvAccessibilityStatus);
        tvProxyInfo           = findViewById(R.id.tvProxyInfo);
        tvLagValue            = findViewById(R.id.tvLagValue);
        tvPatchStatus         = findViewById(R.id.tvPatchStatus);
        sbFakeLag             = findViewById(R.id.sbFakeLag);

        refreshSystemStatus();
        updateProxyInfo();

        // ── 0. NÚT THIẾT LẬP NHANH CHO MÁY NO-ROOT & XIAOMI HYPEROS ──
        if (btnOpenAccessibility != null) {
            btnOpenAccessibility.setOnClickListener(v -> {
                AimAccessibilityService.openAccessibilitySettings(this);
                Toast.makeText(this, "Tìm 'Dịch Vụ Hỗ Trợ Free Fire' và BẬT công tắc Trợ Năng", Toast.LENGTH_LONG).show();
            });
        }

        if (btnXiaomiPerm != null) {
            btnXiaomiPerm.setOnClickListener(v -> openXiaomiPermissionsEditor(this));
        }

        if (btnIgnoreBattery != null) {
            btnIgnoreBattery.setOnClickListener(v -> requestIgnoreBatteryOptimizations(this));
        }

        // Lắng nghe sự kiện trạng thái từ FakeLagProxy
        FakeLagProxy.getInstance().setListener(new FakeLagProxy.ProxyListener() {
            @Override
            public void onStatusChanged(boolean running, int port, int delay) {
                runOnUiThread(() -> {
                    currentLagMs = delay;
                    if (switchFakeLag != null && switchFakeLag.isChecked() != running) {
                        switchFakeLag.setChecked(running);
                    }
                    updateProxyInfo();
                });
            }

            @Override
            public void onStatsUpdated(long bytes, long packets) {}
        });

        // Tự động nạp file offsets.json nếu đã từng dump thành công
        File offsetsFile = new File(getExternalFilesDir(null), "offsets.json");
        if (offsetsFile.exists()) {
            boolean loaded = CheatBridge.loadOffsets(offsetsFile.getAbsolutePath());
            if (loaded) {
                Toast.makeText(this, "Đã tự động nạp offsets.json", Toast.LENGTH_SHORT).show();
            }
        }

        // ── 1. NÚT HIỂN THỊ MENU NỔI TRỰC TIẾP ──
        btnShowMenu.setOnClickListener(v -> {
            if (!checkOverlayPermission()) {
                Toast.makeText(this, "Vui lòng cấp quyền 'Vẽ trên ứng dụng khác'!", Toast.LENGTH_LONG).show();
                return;
            }

            Intent svcIntent = new Intent(this, OverlayService.class);
            svcIntent.setAction(OverlayService.ACTION_SHOW_MENU);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(svcIntent);
            } else {
                startService(svcIntent);
            }

            tvStatus.setText("Trạng thái: MENU NỔI ĐANG HIỂN THỊ ✓");
            tvStatus.setTextColor(0xFF00FFCC);
            Toast.makeText(this, "Đã hiển thị Menu Nổi trên màn hình!", Toast.LENGTH_SHORT).show();
        });

        // ── 2. NÚT KÍCH HOẠT VÀO GAME & MỞ MENU ──
        btnInject.setOnClickListener(v -> {
            if (!checkOverlayPermission()) {
                Toast.makeText(this, "Vui lòng cấp quyền 'Vẽ trên ứng dụng khác'!", Toast.LENGTH_LONG).show();
                return;
            }

            boolean rootOk = RootUtils.isRootAvailable();
            if (rootOk) {
                RootUtils.prepareEnvironment();
                int ffPid = RootUtils.findFreeFirePid();
                if (ffPid > 0) {
                    CheatBridge.setTargetPid(ffPid);
                }
            } else {
                Toast.makeText(this, "Chế độ Không Root (No-Root): Menu Nổi, Trợ Năng Kéo Tâm và Proxy chạy trực tiếp 100%!", Toast.LENGTH_LONG).show();
            }

            boolean esp     = switchESP.isChecked();
            boolean aimbot  = switchAimbot.isChecked();
            boolean fakeLag = switchFakeLag.isChecked();

            Intent svcIntent = new Intent(this, OverlayService.class);
            svcIntent.setAction(OverlayService.ACTION_SHOW_MENU);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(svcIntent);
            } else {
                startService(svcIntent);
            }

            if (fakeLag) {
                CheatBridge.applyFakeLag(true, currentLagMs, switchRootNetem.isChecked());
                if (switchRootNetem.isChecked() || RootUtils.isRootAvailable()) {
                    RootUtils.setUdpDesyncLag(true, currentLagMs);
                }
            }

            int result = nativeInject(esp, aimbot, fakeLag);
            if (result == 0) {
                tvStatus.setText("Trạng thái: ĐANG HOẠT ĐỘNG ✓ (Đã kết nối Free Fire & Mở Menu)");
                tvStatus.setTextColor(0xFF00FF66);
                Toast.makeText(this, "Kích hoạt thành công & Đã mở Menu!", Toast.LENGTH_SHORT).show();
            } else {
                tvStatus.setText("Trạng thái: MENU ĐANG HOẠT ĐỘNG (Đang chờ Free Fire khởi động...)");
                tvStatus.setTextColor(0xFFFFCC00);
                Toast.makeText(this, "Đã mở Menu! Vui lòng mở Free Fire, hệ thống sẽ tự động liên kết.", Toast.LENGTH_LONG).show();
            }
        });

        // ── 3. NÚT DỪNG TOÀN BỘ ──
        btnStop.setOnClickListener(v -> {
            nativeStop();
            CheatBridge.applyFakeLag(false, 0, false);
            RootUtils.setUdpDesyncLag(false, 0);
            stopService(new Intent(this, OverlayService.class));
            tvStatus.setText("Trạng thái: ĐÃ DỪNG (Đã tắt tất cả)");
            tvStatus.setTextColor(0xFFAAAAAA);
            updateProxyInfo();
            Toast.makeText(this, "Đã tắt Hệ thống Hỗ trợ, Menu Nổi và Proxy", Toast.LENGTH_SHORT).show();
        });

        btnDumper.setOnClickListener(v -> {
            startActivity(new Intent(this, DumperActivity.class));
        });

        // ── 4. LẮNG NGHE ĐIỀU KHIỂN FAKE LAG & PROXY ──
        if (sbFakeLag != null) {
            sbFakeLag.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    currentLagMs = 50 + progress;
                    if (tvLagValue != null) {
                        tvLagValue.setText("Độ trễ Fake Lag: " + currentLagMs + " ms");
                    }
                    if (switchFakeLag.isChecked()) {
                        CheatBridge.setFakeLagDelay(currentLagMs);
                        if (RootUtils.isUdpDesyncActive()) {
                            RootUtils.setUdpDesyncLag(false, 0);
                            RootUtils.setUdpDesyncLag(true, currentLagMs);
                        }
                        updateProxyInfo();
                    }
                }
                @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override public void onStopTrackingTouch(SeekBar seekBar) {}
            });
        }

        switchFakeLag.setOnCheckedChangeListener((btn, isChecked) -> {
            CheatBridge.applyFakeLag(isChecked, currentLagMs, switchRootNetem.isChecked());
            if (isChecked && (switchRootNetem.isChecked() || RootUtils.isRootAvailable())) {
                RootUtils.setUdpDesyncLag(true, currentLagMs);
            } else {
                RootUtils.setUdpDesyncLag(false, 0);
            }
            updateProxyInfo();
        });

        switchRootNetem.setOnCheckedChangeListener((btn, isChecked) -> {
            if (switchFakeLag.isChecked()) {
                CheatBridge.applyFakeLag(true, currentLagMs, isChecked);
                if (isChecked) {
                    RootUtils.setUdpDesyncLag(true, currentLagMs);
                } else {
                    RootUtils.setUdpDesyncLag(false, 0);
                }
                updateProxyInfo();
            }
        });

        switchESP.setOnCheckedChangeListener((btn, isChecked) -> CheatBridge.setESP(isChecked));
        switchAimbot.setOnCheckedChangeListener((btn, isChecked) -> CheatBridge.setAimbot(isChecked));

        // ── 5. QUẢN LÝ BẢN VÁ MENU (HOTFIX IFIX OBB55) & DỌN DẸP ──
        if (btnInstallPatch != null) {
            btnInstallPatch.setOnClickListener(v -> {
                btnInstallPatch.setEnabled(false);
                if (tvStatus != null) tvStatus.setText("Trạng thái: Đang cài đặt bản vá vào Free Fire...");
                PatchInstaller.installPatch(this, new PatchInstaller.InstallCallback() {
                    @Override
                    public void onSuccess(String message) {
                        runOnUiThread(() -> {
                            btnInstallPatch.setEnabled(true);
                            if (tvStatus != null) tvStatus.setText("Trạng thái: Đã cài đặt bản vá thành công ✓");
                            updatePatchStatusUI();
                            Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
                        });
                    }

                    @Override
                    public void onError(String error) {
                        runOnUiThread(() -> {
                            btnInstallPatch.setEnabled(true);
                            if (tvStatus != null) tvStatus.setText("Trạng thái: Lỗi nạp bản vá ✗");
                            updatePatchStatusUI();
                            Toast.makeText(MainActivity.this, error, Toast.LENGTH_SHORT).show();
                        });
                    }
                });
            });
        }

        if (btnRemovePatch != null) {
            btnRemovePatch.setOnClickListener(v -> {
                btnRemovePatch.setEnabled(false);
                PatchInstaller.removePatch(this, new PatchInstaller.InstallCallback() {
                    @Override
                    public void onSuccess(String message) {
                        runOnUiThread(() -> {
                            btnRemovePatch.setEnabled(true);
                            updatePatchStatusUI();
                            Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
                        });
                    }

                    @Override
                    public void onError(String error) {
                        runOnUiThread(() -> {
                            btnRemovePatch.setEnabled(true);
                            updatePatchStatusUI();
                            Toast.makeText(MainActivity.this, error, Toast.LENGTH_SHORT).show();
                        });
                    }
                });
            });
        }

        if (switchAntiBanFirewall != null) {
            switchAntiBanFirewall.setOnCheckedChangeListener((btn, isChecked) -> {
                boolean ok = PatchInstaller.applyAntiBanFirewall(isChecked);
                if (!ok && isChecked) {
                    switchAntiBanFirewall.setChecked(false);
                    Toast.makeText(this, "Yêu cầu quyền Root để tự động kích hoạt Tường lửa! Với máy No-Root, vui lòng sử dụng app AdAway VPN.", Toast.LENGTH_LONG).show();
                } else if (ok) {
                    Toast.makeText(this, isChecked ? "Đã kích hoạt Tường lửa Anti-Ban (Chặn máy chủ Garena)!" : "Đã tắt Tường lửa", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private void updateProxyInfo() {
        if (tvProxyInfo != null) {
            if (RootUtils.isUdpDesyncActive()) {
                tvProxyInfo.setText("● UDP Ghost Desync [ĐANG CHẠY | " + currentLagMs + " ms]");
                tvProxyInfo.setTextColor(0xFF00FF66);
            } else if (FakeLagProxy.getInstance().isRunning()) {
                tvProxyInfo.setText("● Proxy SOCKS5/TCP: 127.0.0.1:8888 [ĐANG CHẠY | " + currentLagMs + " ms]");
                tvProxyInfo.setTextColor(0xFF00FFCC);
            } else {
                tvProxyInfo.setText("○ Proxy Fake Lag: [ĐÃ TẮT]");
                tvProxyInfo.setTextColor(0xFF8888AA);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshSystemStatus();
    }

    private void refreshSystemStatus() {
        // 1. Kiểm tra Root
        boolean rootOk = RootUtils.isRootAvailable();
        if (tvRootStatus != null) {
            if (rootOk) {
                tvRootStatus.setText("CHẾ ĐỘ: ĐÃ ROOT ✓");
                tvRootStatus.setTextColor(0xFF00FF66);
            } else {
                tvRootStatus.setText("CHẾ ĐỘ: KHÔNG ROOT ✓");
                tvRootStatus.setTextColor(0xFF00FFCC);
            }
        }

        // 2. Cập nhật trạng thái bản vá
        updatePatchStatusUI();

        // 3. Kiểm tra Overlay & Xiaomi Popup
        boolean overlayOk = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            overlayOk = Settings.canDrawOverlays(this);
        }
        boolean xiaomiPopupOk = checkXiaomiPopupPermission(this);

        if (tvOverlayStatus != null) {
            if (overlayOk && xiaomiPopupOk) {
                tvOverlayStatus.setText("LỚP PHỦ: ĐÃ CẤP QUYỀN ✓");
                tvOverlayStatus.setTextColor(0xFF00FF66);
            } else {
                tvOverlayStatus.setText("LỚP PHỦ: CHƯA CẤP ✗");
                tvOverlayStatus.setTextColor(0xFFFFCC00);
            }
        }

        // 4. Kiểm tra Trợ Năng AimDrag (No-Root)
        if (tvAccessibilityStatus != null) {
            boolean accessOk = AimAccessibilityService.isServiceRunning() || AimAccessibilityService.isAccessibilityEnabled(this);
            if (accessOk) {
                tvAccessibilityStatus.setText("TRỢ NĂNG (AIM): BẬT ✓");
                tvAccessibilityStatus.setTextColor(0xFF00FF66);
            } else if (rootOk) {
                tvAccessibilityStatus.setText("TRỢ NĂNG: ROOT SWIPE ✓");
                tvAccessibilityStatus.setTextColor(0xFF00FF66);
            } else {
                tvAccessibilityStatus.setText("TRỢ NĂNG: CHƯA BẬT ⚠️");
                tvAccessibilityStatus.setTextColor(0xFFFFCC00);
            }
        }
    }

    private void updatePatchStatusUI() {
        if (tvPatchStatus != null) {
            boolean hasPatchFiles = PatchInstaller.isPatchInstalled();
            if (hasPatchFiles) {
                tvPatchStatus.setText("ĐÃ CÀI BẢN VÁ OBB55 ✓");
                tvPatchStatus.setTextColor(0xFF00FFCC);
                tvPatchStatus.setBackgroundColor(0x2500FFCC);
            } else {
                tvPatchStatus.setText("CHƯA CÀI BẢN VÁ (GỐC)");
                tvPatchStatus.setTextColor(0xFF8888AA);
                tvPatchStatus.setBackgroundColor(0x258888AA);
            }
        }
    }

    private boolean checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                Intent intent = new Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName())
                );
                startActivityForResult(intent, REQUEST_OVERLAY_PERMISSION);
                return false;
            }
        }
        return true;
    }

    // ── HỖ TRỢ ĐẶC THÙ XIAOMI HYPEROS / MIUI & MÁY NO-ROOT ──

    public static boolean isXiaomiDevice() {
        String man = Build.MANUFACTURER;
        String brand = Build.BRAND;
        return (man != null && (man.equalsIgnoreCase("Xiaomi") || man.equalsIgnoreCase("Redmi") || man.equalsIgnoreCase("POCO")))
            || (brand != null && (brand.equalsIgnoreCase("Xiaomi") || brand.equalsIgnoreCase("Redmi") || brand.equalsIgnoreCase("POCO")));
    }

    public static boolean checkXiaomiPopupPermission(Context context) {
        if (!isXiaomiDevice()) return true;
        try {
            AppOpsManager ops = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
            Method method = ops.getClass().getMethod("checkOpNoThrow", int.class, int.class, String.class);
            int op = 10021; // OP_BACKGROUND_START_ACTIVITY (Hiển thị cửa sổ pop-up khi chạy dưới nền)
            int result = (int) method.invoke(ops, op, Process.myUid(), context.getPackageName());
            return result == AppOpsManager.MODE_ALLOWED;
        } catch (Exception e) {
            return true;
        }
    }

    public static void openXiaomiPermissionsEditor(Context context) {
        try {
            Intent intent = new Intent("miui.intent.action.APP_PERM_EDITOR");
            intent.setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity");
            intent.putExtra("extra_pkgname", context.getPackageName());
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            Toast.makeText(context, "Xiaomi HyperOS: Hãy bật 'Hiển thị cửa sổ pop-up khi chạy dưới nền'!", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            try {
                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                intent.setData(Uri.parse("package:" + context.getPackageName()));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            } catch (Exception ignored) {}
        }
    }

    public static void requestIgnoreBatteryOptimizations(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
                if (pm != null && !pm.isIgnoringBatteryOptimizations(context.getPackageName())) {
                    Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                    intent.setData(Uri.parse("package:" + context.getPackageName()));
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(intent);
                } else {
                    Toast.makeText(context, "Đã bỏ qua tối ưu pin (Hoạt động nền an toàn) ✓", Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                try {
                    Intent intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(intent);
                } catch (Exception ignored) {}
            }
        }
    }

    // JNI declarations
    public native int  nativeInject(boolean esp, boolean aimbot, boolean fakeLag);
    public native void nativeStop();
}
