package com.ff.cheat;

import android.util.Log;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * RootUtils — Helper kiểm tra và thực thi lệnh qua Root Shell (Magisk / KernelSU / APatch / VMOS / Giả lập)
 */
public class RootUtils {

    private static final String TAG = "RootUtils";
    private static final AtomicBoolean isUdpDesyncActive = new AtomicBoolean(false);
    private static Thread udpPulseThread = null;
    private static volatile boolean autoAimDragEnabled = false;
    private static long lastAimTime = 0;

    /**
     * Kiểm tra thiết bị đã Root chưa và có thể cấp quyền su hay không
     */
    public static boolean isRootAvailable() {
        Process process = null;
        try {
            process = Runtime.getRuntime().exec(new String[]{"which", "su"});
            BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream()));
            return in.readLine() != null;
        } catch (Exception e) {
            return false;
        } finally {
            if (process != null) process.destroy();
        }
    }

    /**
     * Yêu cầu quyền Root và thực thi lệnh
     */
    public static boolean executeCommand(String command) {
        Process process = null;
        DataOutputStream os = null;
        try {
            process = Runtime.getRuntime().exec("su");
            os = new DataOutputStream(process.getOutputStream());
            os.writeBytes(command + "\n");
            os.writeBytes("exit\n");
            os.flush();
            process.waitFor();
            return process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        } finally {
            try {
                if (os != null) os.close();
                if (process != null) process.destroy();
            } catch (Exception ignored) {}
        }
    }

    /**
     * Thực thi lệnh Root và đọc kết quả đầu ra dạng String
     */
    public static String executeCommandWithOutput(String command) {
        Process process = null;
        DataOutputStream os = null;
        BufferedReader in = null;
        StringBuilder output = new StringBuilder();
        try {
            process = Runtime.getRuntime().exec("su");
            os = new DataOutputStream(process.getOutputStream());
            in = new BufferedReader(new InputStreamReader(process.getInputStream()));

            os.writeBytes(command + "\n");
            os.writeBytes("exit\n");
            os.flush();

            String line;
            while ((line = in.readLine()) != null) {
                output.append(line).append("\n");
            }
            process.waitFor();
            return output.toString().trim();
        } catch (Exception e) {
            return "";
        } finally {
            try {
                if (os != null) os.close();
                if (in != null) in.close();
                if (process != null) process.destroy();
            } catch (Exception ignored) {}
        }
    }

    /**
     * Tìm PID của Free Fire bằng lệnh Root Shell (hỗ trợ cả máy ảo VMOS / VPhoneGaga / LDPlayer / Root máy thật)
     */
    public static int findFreeFirePid() {
        if (!isRootAvailable()) return 0;

        String[] checkCmds = {
            "pidof com.dts.freefireth com.vng.freefireth com.dts.freefiremax",
            "pgrep -f com.dts.freefireth",
            "pgrep -f com.vng.freefireth",
            "pgrep -f com.dts.freefiremax",
            "pgrep -f freefire"
        };

        for (String cmd : checkCmds) {
            String out = executeCommandWithOutput(cmd);
            if (!out.isEmpty()) {
                String[] tokens = out.split("\\s+");
                for (String token : tokens) {
                    try {
                        int pid = Integer.parseInt(token.trim());
                        if (pid > 0) {
                            Log.i(TAG, "Found Free Fire PID via root command (" + cmd + "): " + pid);
                            return pid;
                        }
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        return 0;
    }

    /**
     * Thiết lập quyền truy cập bộ nhớ cho process_vm_readv
     */
    public static void prepareEnvironment() {
        if (!isRootAvailable()) return;
        executeCommand("setenforce 0 2>/dev/null");
        executeCommand("echo 0 > /proc/sys/kernel/yama/ptrace_scope 2>/dev/null");
    }

    /**
     * KÍCH HOẠT FAKE LAG UDP DESYNC THẬT 100% CHO FREE FIRE:
     * Chặn các gói tin gameplay UDP trong khoảng delayMs (500-1000ms), sau đó nhả ra.
     * Tạo hiệu ứng desync/bất tử/ghost lag kinh điển của game Free Fire!
     */
    public static synchronized void setUdpDesyncLag(boolean enable, int delayMs) {
        if (!isRootAvailable()) return;

        if (enable) {
            if (isUdpDesyncActive.get()) return;
            isUdpDesyncActive.set(true);

            final int safeDelay = Math.max(100, Math.min(1500, delayMs));
            udpPulseThread = new Thread(() -> {
                while (isUdpDesyncActive.get() && !Thread.currentThread().isInterrupted()) {
                    try {
                        // 1. Chặn UDP gửi lên server (Drop packet làm server mất đồng bộ vị trí)
                        executeCommand("iptables -I OUTPUT -p udp -j DROP 2>/dev/null");
                        Thread.sleep(safeDelay);

                        // 2. Nhả gói tin để đồng bộ lại
                        executeCommand("iptables -D OUTPUT -p udp -j DROP 2>/dev/null");
                        Thread.sleep(200);
                    } catch (InterruptedException e) {
                        break;
                    } catch (Exception ignored) {}
                }
                // Đảm bảo dọn dẹp khi thread kết thúc
                executeCommand("iptables -D OUTPUT -p udp -j DROP 2>/dev/null");
            });
            udpPulseThread.start();
            Log.i(TAG, "UDP Desync Fake Lag activated with pulse delay=" + delayMs + "ms");
        } else {
            isUdpDesyncActive.set(false);
            if (udpPulseThread != null) {
                udpPulseThread.interrupt();
                udpPulseThread = null;
            }
            executeCommand("iptables -D OUTPUT -p udp -j DROP 2>/dev/null");
            Log.i(TAG, "UDP Desync Fake Lag deactivated");
        }
    }

    public static boolean isUdpDesyncActive() {
        return isUdpDesyncActive.get();
    }

    /**
     * Tự động kéo tâm Aimbot nhẹ về phía đầu mục tiêu (Hỗ trợ máy Root / Giả lập)
     */
    public static void setAutoAimDrag(boolean enable) {
        autoAimDragEnabled = enable;
    }

    public static boolean isAutoAimDragEnabled() {
        return autoAimDragEnabled;
    }

    public static void triggerAimDrag(float screenCX, float screenCY, float targetX, float targetY) {
        if (!autoAimDragEnabled || !isRootAvailable()) return;

        long now = System.currentTimeMillis();
        if (now - lastAimTime < 80) return; // Giới hạn tần số kéo 12 lần/giây để mượt mà
        lastAimTime = now;

        float dx = targetX - screenCX;
        float dy = targetY - screenCY;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        if (dist < 10.0f || dist > 350.0f) return;

        // Tính bước kéo tâm nhẹ (Smooth step ~15-25px)
        float step = Math.min(25.0f, dist * 0.35f);
        float endX = screenCX + (dx / dist) * step;
        float endY = screenCY + (dy / dist) * step;

        new Thread(() -> {
            executeCommand("input swipe " + (int)screenCX + " " + (int)screenCY + " " + (int)endX + " " + (int)endY + " 25");
        }).start();
    }

    /**
     * Áp dụng mô phỏng độ trễ qua Traffic Control (tc netem) ở cấp Kernel Linux
     */
    public static boolean applyKernelLag(int delayMs) {
        if (!isRootAvailable()) return false;
        String cmd = "tc qdisc replace dev wlan0 root netem delay " + delayMs + "ms 10ms distribution normal 2>/dev/null;"
                   + "tc qdisc replace dev rmnet_data0 root netem delay " + delayMs + "ms 10ms distribution normal 2>/dev/null;"
                   + "tc qdisc replace dev rmnet0 root netem delay " + delayMs + "ms 10ms distribution normal 2>/dev/null";
        return executeCommand(cmd);
    }

    public static boolean removeKernelLag() {
        if (!isRootAvailable()) return false;
        String cmd = "tc qdisc del dev wlan0 root netem 2>/dev/null;"
                   + "tc qdisc del dev rmnet_data0 root netem 2>/dev/null;"
                   + "tc qdisc del dev rmnet0 root netem 2>/dev/null";
        return executeCommand(cmd);
    }

    public static boolean redirectTrafficToProxy(int proxyPort) {
        if (!isRootAvailable()) return false;
        String cmd = "iptables -t nat -A OUTPUT -p tcp --dport 80 -j REDIRECT --to-ports " + proxyPort + " 2>/dev/null;"
                   + "iptables -t nat -A OUTPUT -p tcp --dport 443 -j REDIRECT --to-ports " + proxyPort + " 2>/dev/null";
        return executeCommand(cmd);
    }

    public static boolean removeProxyRedirection(int proxyPort) {
        if (!isRootAvailable()) return false;
        String cmd = "iptables -t nat -D OUTPUT -p tcp --dport 80 -j REDIRECT --to-ports " + proxyPort + " 2>/dev/null;"
                   + "iptables -t nat -D OUTPUT -p tcp --dport 443 -j REDIRECT --to-ports " + proxyPort + " 2>/dev/null";
        return executeCommand(cmd);
    }
}
