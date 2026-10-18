package com.ff.cheat;

import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * FakeLagProxy — Local SOCKS5 / TCP Proxy Server với độ trễ nhân tạo (Artificial Delay / Desync Engine)
 * Chức năng:
 * 1. Chạy Local Proxy Server tại 127.0.0.1:<port> (mặc định 8888)
 * 2. Hỗ trợ giao thức SOCKS5 tiêu chuẩn (RFC 1928) & HTTP CONNECT tunnel proxy
 * 3. Mô phỏng độ trễ nhân tạo (Fake Lag Desync) tùy biến từ 50ms đến 2000ms
 * 4. Tích hợp Traffic Control Kernel (tc netem) và iptables redirect nếu thiết bị có Root
 */
public class FakeLagProxy {

    private static final String TAG = "FakeLagProxy";
    private static volatile FakeLagProxy instance;

    private ServerSocket serverSocket;
    private ExecutorService threadPool;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final AtomicInteger delayMs = new AtomicInteger(150);
    private final AtomicInteger currentPort = new AtomicInteger(8888);
    private final AtomicLong totalBytesTransferred = new AtomicLong(0);
    private final AtomicLong delayedPacketsCount = new AtomicLong(0);
    private boolean useRootNetem = false;

    private ProxyListener listener;

    @FunctionalInterface
    public interface ProxyListener {
        void onStatusChanged(boolean running, int port, int delayMs);
        default void onStatsUpdated(long bytes, long packets) {}
    }

    private FakeLagProxy() {}

    public static FakeLagProxy getInstance() {
        if (instance == null) {
            synchronized (FakeLagProxy.class) {
                if (instance == null) {
                    instance = new FakeLagProxy();
                }
            }
        }
        return instance;
    }

    public void setListener(ProxyListener listener) {
        this.listener = listener;
    }

    /**
     * Khởi động Proxy Server và thiết lập bộ định tuyến trễ
     */
    public synchronized boolean startProxy(int port, int delay, boolean enableRootNetem) {
        if (isRunning.get()) {
            setDelayMs(delay);
            return true;
        }

        this.delayMs.set(delay);
        this.currentPort.set(port);
        this.useRootNetem = enableRootNetem;

        try {
            serverSocket = new ServerSocket();
            serverSocket.setReuseAddress(true);
            serverSocket.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), port));
            isRunning.set(true);
            threadPool = Executors.newCachedThreadPool();

            // Thread lắng nghe kết nối đến proxy
            threadPool.execute(this::acceptLoop);

            // Kích hoạt Kernel Netem & iptables nếu thiết bị có Root
            if (enableRootNetem && RootUtils.isRootAvailable()) {
                RootUtils.applyKernelLag(delay);
                RootUtils.redirectTrafficToProxy(port);
            }

            Log.i(TAG, "FakeLag Proxy started on 127.0.0.1:" + port + " with delay=" + delay + "ms (RootNetem=" + enableRootNetem + ")");
            notifyStatus();
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Failed to start FakeLag Proxy on port " + port, e);
            stopProxy();
            return false;
        }
    }

    /**
     * Dừng Proxy Server và dọn dẹp các rule mạng đã tạo
     */
    public synchronized void stopProxy() {
        if (!isRunning.get() && serverSocket == null) return;

        isRunning.set(false);

        // Xóa sạch iptables & kernel netem rule nếu đã áp dụng
        if (useRootNetem && RootUtils.isRootAvailable()) {
            RootUtils.removeKernelLag();
            RootUtils.removeProxyRedirection(currentPort.get());
        }

        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException ignored) {}
        serverSocket = null;

        if (threadPool != null) {
            threadPool.shutdownNow();
            threadPool = null;
        }

        Log.i(TAG, "FakeLag Proxy stopped cleanly");
        notifyStatus();
    }

    /**
     * Cập nhật độ trễ (ms) tức thì không cần restart proxy
     */
    public void setDelayMs(int delay) {
        int safeDelay = Math.max(0, delay);
        this.delayMs.set(safeDelay);
        if (isRunning.get() && useRootNetem && RootUtils.isRootAvailable()) {
            RootUtils.applyKernelLag(safeDelay);
        }
        notifyStatus();
    }

    public int getDelayMs() {
        return delayMs.get();
    }

    public int getPort() {
        return currentPort.get();
    }

    public boolean isRunning() {
        return isRunning.get();
    }

    public long getTotalBytesTransferred() {
        return totalBytesTransferred.get();
    }

    public long getDelayedPacketsCount() {
        return delayedPacketsCount.get();
    }

    private void notifyStatus() {
        if (listener != null) {
            listener.onStatusChanged(isRunning.get(), currentPort.get(), delayMs.get());
        }
    }

    private void acceptLoop() {
        while (isRunning.get() && serverSocket != null && !serverSocket.isClosed()) {
            try {
                Socket clientSocket = serverSocket.accept();
                clientSocket.setTcpNoDelay(true);
                clientSocket.setSoTimeout(30000);
                if (threadPool != null && !threadPool.isShutdown()) {
                    threadPool.execute(() -> handleClient(clientSocket));
                }
            } catch (IOException e) {
                if (!isRunning.get()) break;
            }
        }
    }

    private void handleClient(Socket clientSocket) {
        Socket targetSocket = null;
        try {
            InputStream in = clientSocket.getInputStream();
            OutputStream out = clientSocket.getOutputStream();

            int firstByte = in.read();
            if (firstByte == -1) {
                clientSocket.close();
                return;
            }

            if (firstByte == 0x05) {
                // ── SOCKS5 Handshake (RFC 1928) ──
                int numMethods = in.read();
                byte[] methods = new byte[numMethods > 0 ? numMethods : 1];
                int readMethods = in.read(methods);
                if (readMethods <= 0) {
                    clientSocket.close();
                    return;
                }

                // Server đồng ý: No Authentication Required (0x00)
                out.write(new byte[]{0x05, 0x00});
                out.flush();

                // Đọc Connection Request: VER, CMD, RSV, ATYP
                int ver = in.read();
                int cmd = in.read();
                int rsv = in.read();
                int atyp = in.read();

                if (cmd != 0x01) {
                    // Chỉ hỗ trợ CMD 0x01 (TCP CONNECT)
                    out.write(new byte[]{0x05, 0x07, 0x00, 0x01, 0, 0, 0, 0, 0, 0});
                    out.flush();
                    clientSocket.close();
                    return;
                }

                String targetHost = "";
                if (atyp == 0x01) {
                    // IPv4 (4 bytes)
                    byte[] ip = new byte[4];
                    int n = in.read(ip);
                    if (n < 4) { clientSocket.close(); return; }
                    targetHost = InetAddress.getByAddress(ip).getHostAddress();
                } else if (atyp == 0x03) {
                    // Domain Name
                    int domainLen = in.read();
                    byte[] domain = new byte[domainLen];
                    int n = in.read(domain);
                    if (n < domainLen) { clientSocket.close(); return; }
                    targetHost = new String(domain);
                } else if (atyp == 0x04) {
                    // IPv6 (16 bytes)
                    byte[] ip6 = new byte[16];
                    int n = in.read(ip6);
                    if (n < 16) { clientSocket.close(); return; }
                    targetHost = InetAddress.getByAddress(ip6).getHostAddress();
                }

                int portHigh = in.read();
                int portLow = in.read();
                int targetPort = ((portHigh & 0xFF) << 8) | (portLow & 0xFF);

                targetSocket = new Socket();
                targetSocket.setTcpNoDelay(true);
                targetSocket.connect(new InetSocketAddress(targetHost, targetPort), 8000);

                // Phản hồi SOCKS5 Connection Succeeded
                out.write(new byte[]{0x05, 0x00, 0x00, 0x01, 127, 0, 0, 1, (byte) (portHigh), (byte) (portLow)});
                out.flush();

            } else {
                // ── HTTP CONNECT Tunneling ──
                StringBuilder headerBuilder = new StringBuilder();
                headerBuilder.append((char) firstByte);
                while (headerBuilder.length() < 2048) {
                    int b = in.read();
                    if (b == -1) break;
                    headerBuilder.append((char) b);
                    if (headerBuilder.toString().contains("\r\n\r\n")) break;
                }
                String header = headerBuilder.toString();
                if (header.startsWith("CONNECT ")) {
                    int spaceIdx = header.indexOf(' ', 8);
                    if (spaceIdx > 8) {
                        String hostPort = header.substring(8, spaceIdx);
                        String[] parts = hostPort.split(":");
                        String host = parts[0];
                        int port = parts.length > 1 ? Integer.parseInt(parts[1]) : 443;

                        targetSocket = new Socket();
                        targetSocket.setTcpNoDelay(true);
                        targetSocket.connect(new InetSocketAddress(host, port), 8000);

                        out.write("HTTP/1.1 200 Connection Established\r\n\r\n".getBytes());
                        out.flush();
                    }
                }
            }

            if (targetSocket != null && targetSocket.isConnected()) {
                Socket s1 = clientSocket;
                Socket s2 = targetSocket;
                Thread t1 = new Thread(() -> pipeStreamWithDelay(s1, s2));
                Thread t2 = new Thread(() -> pipeStreamWithDelay(s2, s1));
                t1.start();
                t2.start();
                t1.join();
                t2.join();
            } else {
                clientSocket.close();
            }

        } catch (Exception ignored) {
        } finally {
            try { clientSocket.close(); } catch (Exception ignored) {}
            if (targetSocket != null) {
                try { targetSocket.close(); } catch (Exception ignored) {}
            }
        }
    }

    /**
     * Chuyển tiếp luồng dữ liệu hai chiều có chèn độ trễ nhân tạo (Artificial Delay)
     */
    private void pipeStreamWithDelay(Socket src, Socket dst) {
        byte[] buffer = new byte[8192];
        try {
            InputStream in = src.getInputStream();
            OutputStream out = dst.getOutputStream();
            int read;
            while (isRunning.get() && (read = in.read(buffer)) != -1) {
                int lag = delayMs.get();
                if (lag > 0) {
                    try {
                        Thread.sleep(lag);
                    } catch (InterruptedException e) {
                        break;
                    }
                }
                out.write(buffer, 0, read);
                out.flush();
                totalBytesTransferred.addAndGet(read);
                delayedPacketsCount.incrementAndGet();
            }
        } catch (Exception ignored) {
        } finally {
            try { src.close(); } catch (Exception ignored) {}
            try { dst.close(); } catch (Exception ignored) {}
        }
    }
}
