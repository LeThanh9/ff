package com.ff.cheat;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;

/**
 * RootUtils — Helper kiểm tra và thực thi lệnh qua Root Shell (Magisk / KernelSU / APatch)
 */
public class RootUtils {

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
     * Thiết lập quyền truy cập bộ nhớ cho process_vm_readv
     */
    public static void prepareEnvironment() {
        // Cấu hình SELinux sang permissive và cho phép ptrace bộ nhớ nếu cần thiết
        executeCommand("setenforce 0");
        executeCommand("echo 0 > /proc/sys/kernel/yama/ptrace_scope");
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

    /**
     * Xóa bỏ toàn bộ quy tắc netem trên các card mạng
     */
    public static boolean removeKernelLag() {
        if (!isRootAvailable()) return false;
        String cmd = "tc qdisc del dev wlan0 root netem 2>/dev/null;"
                   + "tc qdisc del dev rmnet_data0 root netem 2>/dev/null;"
                   + "tc qdisc del dev rmnet0 root netem 2>/dev/null";
        return executeCommand(cmd);
    }

    /**
     * Chuyển hướng lưu lượng TCP sang cổng Proxy Local qua iptables
     */
    public static boolean redirectTrafficToProxy(int proxyPort) {
        if (!isRootAvailable()) return false;
        String cmd = "iptables -t nat -A OUTPUT -p tcp --dport 80 -j REDIRECT --to-ports " + proxyPort + " 2>/dev/null;"
                   + "iptables -t nat -A OUTPUT -p tcp --dport 443 -j REDIRECT --to-ports " + proxyPort + " 2>/dev/null";
        return executeCommand(cmd);
    }

    /**
     * Xóa quy tắc chuyển hướng iptables
     */
    public static boolean removeProxyRedirection(int proxyPort) {
        if (!isRootAvailable()) return false;
        String cmd = "iptables -t nat -D OUTPUT -p tcp --dport 80 -j REDIRECT --to-ports " + proxyPort + " 2>/dev/null;"
                   + "iptables -t nat -D OUTPUT -p tcp --dport 443 -j REDIRECT --to-ports " + proxyPort + " 2>/dev/null";
        return executeCommand(cmd);
    }
}
