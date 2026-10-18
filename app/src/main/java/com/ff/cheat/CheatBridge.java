package com.ff.cheat;

/**
 * CheatBridge — Cầu nối JNI giữa UI Android và Native Engine libffcheat.so
 */
public class CheatBridge {

    static {
        System.loadLibrary("ffcheat");
    }

    /**
     * Đồng bộ độ phân giải màn hình chính xác để tính ma trận WorldToScreen
     */
    public static native void setScreenSize(int width, int height);

    /**
     * Lấy dữ liệu mảng float danh sách người chơi (7 floats/player):
     * [0] screenX, [1] screenY, [2] boxW, [3] boxH, [4] health, [5] distance, [6] isVisible
     */
    public static native float[] getPlayerPositions();

    /**
     * Bật / tắt các tính năng trong Native Engine
     */
    public static native void setESP(boolean on);
    public static native void setAimbot(boolean on);
    public static native void setAimbotFov(float fov);
    public static native void setFakeLag(boolean on, int lagMs);

    /**
     * Đồng bộ kích hoạt Fake Lag qua cả Java SOCKS5/TCP Proxy và Native Memory desync
     */
    public static void applyFakeLag(boolean on, int lagMs) {
        applyFakeLag(on, lagMs, RootUtils.isRootAvailable());
    }

    public static void applyFakeLag(boolean on, int lagMs, boolean useRootNetem) {
        try {
            setFakeLag(on, lagMs);
        } catch (Throwable ignored) {}

        if (on) {
            FakeLagProxy.getInstance().startProxy(8888, lagMs, useRootNetem);
        } else {
            FakeLagProxy.getInstance().stopProxy();
        }
    }

    public static void setFakeLagDelay(int lagMs) {
        try {
            setFakeLag(FakeLagProxy.getInstance().isRunning(), lagMs);
        } catch (Throwable ignored) {}
        FakeLagProxy.getInstance().setDelayMs(lagMs);
    }

    public static boolean isFakeLagRunning() {
        return FakeLagProxy.getInstance().isRunning();
    }

    /**
     * Lấy tọa độ tâm đầu [x, y] của kẻ địch trong FOV để khóa tâm Aimbot
     */
    public static native float[] getAimbotTarget();

    /**
     * Nạp file offsets JSON vào bộ nhớ
     */
    public static native boolean loadOffsets(String jsonPath);
}
