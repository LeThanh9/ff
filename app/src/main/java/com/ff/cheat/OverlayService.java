package com.ff.cheat;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.WindowManager;

/**
 * OverlayService — Dịch vụ nền hiển thị ESP vẽ đè lên Free Fire
 * Tương thích Android 8.0 đến Android 14 (Foreground Service + Notch Cutout)
 */
public class OverlayService extends Service {

    public static final String ACTION_SHOW_MENU   = "com.ff.cheat.ACTION_SHOW_MENU";
    public static final String ACTION_HIDE_MENU   = "com.ff.cheat.ACTION_HIDE_MENU";
    public static final String ACTION_TOGGLE_MENU = "com.ff.cheat.ACTION_TOGGLE_MENU";

    private static final String CHANNEL_ID = "FF_OVERLAY_CHANNEL";
    private static final int NOTIF_ID = 1337;

    private static volatile OverlayService instance;

    private WindowManager wm;
    private ESPView espView;
    private FloatingMenu floatingMenu;
    private Thread renderThread;
    private volatile boolean isRunning = false;

    public static OverlayService getInstance() {
        return instance;
    }

    public static boolean isServiceRunning() {
        return instance != null && instance.isRunning;
    }

    public FloatingMenu getFloatingMenu() {
        return floatingMenu;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;

        createNotificationChannel();
        startForeground(NOTIF_ID, buildNotification());

        wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);

        // Lấy kích thước thực của màn hình truyền vào Native C++
        DisplayMetrics metrics = new DisplayMetrics();
        if (wm != null && wm.getDefaultDisplay() != null) {
            wm.getDefaultDisplay().getRealMetrics(metrics);
            CheatBridge.setScreenSize(metrics.widthPixels, metrics.heightPixels);
        }

        espView = new ESPView(this);

        int overlayType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                overlayType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
        );

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }

        params.gravity = Gravity.TOP | Gravity.START;

        if (wm != null) {
            wm.addView(espView, params);
            // Khởi tạo Floating Mod Menu tương tác nổi trên Free Fire
            floatingMenu = new FloatingMenu(this, wm, espView);
        }

        // Vòng lặp lấy tọa độ người chơi & Aimbot từ Native Engine (~60 FPS)
        isRunning = true;
        renderThread = new Thread(() -> {
            int loopCounter = 0;
            while (isRunning && !Thread.currentThread().isInterrupted()) {
                try {
                    // Cứ mỗi 60 chu kỳ (~1 giây), tự động tìm PID Free Fire qua Root nếu chưa kết nối
                    loopCounter++;
                    if (loopCounter % 60 == 0) {
                        if (!CheatBridge.isGameConnected() && RootUtils.isRootAvailable()) {
                            int ffPid = RootUtils.findFreeFirePid();
                            if (ffPid > 0) {
                                CheatBridge.setTargetPid(ffPid);
                            }
                        }
                    }

                    // 1. Lấy dữ liệu ESP Player Positions
                    float[] playerData = CheatBridge.getPlayerPositions();
                    if (espView != null) {
                        espView.updatePlayers(playerData);
                    }

                    // 2. Lấy dữ liệu Aimbot Head Lock Target
                    float[] aimTarget = CheatBridge.getAimbotTarget();
                    if (aimTarget != null && aimTarget.length >= 2) {
                        float targetX = aimTarget[0];
                        float targetY = aimTarget[1];
                        if (espView != null) {
                            espView.setAimbotTarget(targetX, targetY);
                        }
                        if (RootUtils.isAutoAimDragEnabled()) {
                            int scrW = espView != null ? espView.getWidth() : 1920;
                            int scrH = espView != null ? espView.getHeight() : 1080;
                            RootUtils.triggerAimDrag(scrW * 0.5f, scrH * 0.5f, targetX, targetY);
                        }
                    } else {
                        if (espView != null) {
                            espView.setAimbotTarget(-1.0f, -1.0f);
                        }
                    }

                    Thread.sleep(16);
                } catch (InterruptedException e) {
                    break;
                } catch (Exception ignored) {}
            }
        });
        renderThread.start();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "FF Overlay Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Running Free Fire ESP Overlay");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification buildNotification() {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);

        return builder.setContentTitle("FF Overlay Engine")
                .setContentText("Overlay đang hoạt động")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            String action = intent.getAction();
            if (ACTION_SHOW_MENU.equals(action)) {
                if (floatingMenu != null) {
                    floatingMenu.openMenu();
                }
            } else if (ACTION_HIDE_MENU.equals(action)) {
                if (floatingMenu != null) {
                    floatingMenu.closeMenu();
                }
            } else if (ACTION_TOGGLE_MENU.equals(action)) {
                if (floatingMenu != null) {
                    floatingMenu.toggleMenu();
                }
            }
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        isRunning = false;
        instance = null;

        // Dừng FakeLag Proxy nếu đang chạy
        FakeLagProxy.getInstance().stopProxy();
        RootUtils.setUdpDesyncLag(false, 0);

        if (renderThread != null) {
            renderThread.interrupt();
            renderThread = null;
        }

        if (floatingMenu != null) {
            floatingMenu.destroy();
            floatingMenu = null;
        }

        if (wm != null && espView != null) {
            try {
                wm.removeView(espView);
            } catch (Exception ignored) {}
            espView = null;
        }

        super.onDestroy();
    }
}
