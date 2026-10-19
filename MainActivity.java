package com.ff.cheat;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;

public class MainActivity extends Activity {

    private Switch switchESP, switchAimbot, switchFakeLag, switchRootNetem, switchAntiBanFirewall;
    private Button btnInject, btnStop, btnDumper, btnShowMenu, btnInstallPatch, btnRemovePatch;
    private TextView tvStatus, tvRootStatus, tvOverlayStatus, tvProxyInfo, tvLagValue, tvPatchStatus;
    private android.widget.SeekBar sbFakeLag;
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
        btnInstallPatch       = findViewById(R.id.btnInstallPatch);
        btnRemovePatch        = findViewById(R.id.btnRemovePatch);
        tvStatus              = findViewById(R.id.tvStatus);
        tvRootStatus          = findViewById(R.id.tvRootStatus);
        tvOverlayStatus       = findViewById(R.id.tvOverlayStatus);
        tvProxyInfo           = findViewById(R.id.tvProxyInfo);
        tvLagValue            = findViewById(R.id.tvLagValue);
        tvPatchStatus         = findViewById(R.id.tvPatchStatus);
        sbFakeLag             = findViewById(R.id.sbFakeLag);

        refreshSystemStatus();
        updateProxyInfo();

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

        // Tự động load file offsets.json nếu đã từng dump thành công
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

            tvStatus.setText("Status: MENU NỔI ĐANG HIỂN THỊ ✓");
            tvStatus.setTextColor(0xFF00FFCC);
            Toast.makeText(this, "Đã hiển thị Menu Nổi trên màn hình!", Toast.LENGTH_SHORT).show();
        });

        // ── 2. NÚT INJECT VÀO GAME & MỞ MENU ──
        btnInject.setOnClickListener(v -> {
            if (!checkOverlayPermission()) {
                Toast.makeText(this, "Vui lòng cấp quyền 'Vẽ trên ứng dụng khác'!", Toast.LENGTH_LONG).show();
                return;
            }

            // Chuẩn bị môi trường nếu có root
            boolean rootOk = RootUtils.isRootAvailable();
            if (rootOk) {
                RootUtils.prepareEnvironment();
                int ffPid = RootUtils.findFreeFirePid();
                if (ffPid > 0) {
                    CheatBridge.setTargetPid(ffPid);
                }
            } else {
                Toast.makeText(this, "Chạy chế độ No-Root: Dùng Không Gian Ảo cho ESP hoặc Wi-Fi Proxy cho Fake Lag!", Toast.LENGTH_SHORT).show();
            }

            boolean esp     = switchESP.isChecked();
            boolean aimbot  = switchAimbot.isChecked();
            boolean fakeLag = switchFakeLag.isChecked();

            // Khởi động Overlay Service hiển thị Menu Nổi ngay lập tức
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
                tvStatus.setText("Status: ACTIVE ✓ (Đã gắn vào Free Fire & Mở Menu)");
                tvStatus.setTextColor(0xFF00FF66);
                Toast.makeText(this, "Inject Thành Công & Đã Mở Menu!", Toast.LENGTH_SHORT).show();
            } else {
                tvStatus.setText("Status: MENU ACTIVE (Đang chờ Free Fire khởi động...)");
                tvStatus.setTextColor(0xFFFFCC00);
                Toast.makeText(this, "Đã mở Menu! Hãy mở Free Fire, engine sẽ tự động hook.", Toast.LENGTH_LONG).show();
            }
        });

        // ── 3. NÚT TẮT TOÀN BỘ ──
        btnStop.setOnClickListener(v -> {
            nativeStop();
            CheatBridge.applyFakeLag(false, 0, false);
            RootUtils.setUdpDesyncLag(false, 0);
            stopService(new Intent(this, OverlayService.class));
            tvStatus.setText("Status: STOPPED (Đã tắt tất cả)");
            tvStatus.setTextColor(0xFFAAAAAA);
            updateProxyInfo();
            Toast.makeText(this, "Đã tắt Cheat Engine, Menu Nổi và Proxy", Toast.LENGTH_SHORT).show();
        });

        btnDumper.setOnClickListener(v -> {
            startActivity(new Intent(this, DumperActivity.class));
        });

        // ── 4. LẮNG NGHE ĐIỀU KHIỂN FAKE LAG & PROXY ──
        if (sbFakeLag != null) {
            sbFakeLag.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(android.widget.SeekBar seekBar, int progress, boolean fromUser) {
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
                @Override public void onStartTrackingTouch(android.widget.SeekBar seekBar) {}
                @Override public void onStopTrackingTouch(android.widget.SeekBar seekBar) {}
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

        // ── 5. QUẢN LÝ BẢN VÁ MENU ẨN OBB55 (HOTFIX IFIX) ──
        if (btnInstallPatch != null) {
            btnInstallPatch.setOnClickListener(v -> {
                btnInstallPatch.setEnabled(false);
                btnInstallPatch.setText("ĐANG CÀI ĐẶT...");
                PatchInstaller.installPatch(this, new PatchInstaller.InstallCallback() {
                    @Override
                    public void onSuccess(String message) {
                        runOnUiThread(() -> {
                            btnInstallPatch.setEnabled(true);
                            btnInstallPatch.setText("⚡ CÀI VÀO GAME (1-CLICK)");
                            updatePatchStatusUI();
                            new android.app.AlertDialog.Builder(MainActivity.this)
                                    .setTitle("Cài Đặt Bản Vá")
                                    .setMessage(message)
                                    .setPositiveButton("OK", null)
                                    .show();
                        });
                    }

                    @Override
                    public void onError(String error) {
                        runOnUiThread(() -> {
                            btnInstallPatch.setEnabled(true);
                            btnInstallPatch.setText("⚡ CÀI VÀO GAME (1-CLICK)");
                            updatePatchStatusUI();
                            Toast.makeText(MainActivity.this, error, Toast.LENGTH_LONG).show();
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
                tvProxyInfo.setText("● UDP Ghost Desync [ĐANG CHẠY | " + currentLagMs + "ms]");
                tvProxyInfo.setTextColor(0xFF00FF66);
            } else if (FakeLagProxy.getInstance().isRunning()) {
                tvProxyInfo.setText("● SOCKS5/TCP Proxy: 127.0.0.1:8888 [ĐANG CHẠY | " + currentLagMs + "ms]");
                tvProxyInfo.setTextColor(0xFF00FFCC);
            } else {
                tvProxyInfo.setText("○ Fake Lag Proxy: [ĐÃ TẮT]");
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
        // Kiểm tra Root / Chế độ hoạt động
        boolean rootOk = RootUtils.isRootAvailable();
        if (tvRootStatus != null) {
            if (rootOk) {
                tvRootStatus.setText("CHẾ ĐỘ: ROOT ✓");
                tvRootStatus.setTextColor(0xFF00FF66);
            } else {
                tvRootStatus.setText("CHẾ ĐỘ: NO-ROOT ✓");
                tvRootStatus.setTextColor(0xFF00FFCC);
            }
        }

        // Cập nhật trạng thái bản vá
        updatePatchStatusUI();

        // Kiểm tra Overlay
        boolean overlayOk = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            overlayOk = Settings.canDrawOverlays(this);
        }
        if (tvOverlayStatus != null) {
            if (overlayOk) {
                tvOverlayStatus.setText("OVERLAY: ĐÃ CẤP ✓");
                tvOverlayStatus.setTextColor(0xFF00FF66);
            } else {
                tvOverlayStatus.setText("OVERLAY: CHƯA CẤP ✗");
                tvOverlayStatus.setTextColor(0xFFFFCC00);
            }
        }
    }

    private void updatePatchStatusUI() {
        if (tvPatchStatus != null) {
            boolean hasPatchFiles = PatchInstaller.isPatchInstalled();
            if (hasPatchFiles) {
                tvPatchStatus.setText("CÓ FILE VÁ (DỄ BỊ BAN) ⚠️");
                tvPatchStatus.setTextColor(0xFFFF4444);
                tvPatchStatus.setBackgroundColor(0x25FF4444);
            } else {
                tvPatchStatus.setText("GAME GỐC AN TOÀN ✓");
                tvPatchStatus.setTextColor(0xFF00FF66);
                tvPatchStatus.setBackgroundColor(0x2500FF66);
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

    // JNI declarations
    public native int  nativeInject(boolean esp, boolean aimbot, boolean fakeLag);
    public native void nativeStop();
}
