package com.ff.cheat;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * PatchInstaller — Tự động cài đặt Bản Vá Menu Ẩn (Hotfix IFix OBB55)
 * Cài trực tiếp vào thư mục files của Free Fire & Free Fire MAX
 */
public class PatchInstaller {

    private static final String TAG = "PatchInstaller";

    public static final String FILE_PATCH  = "Assembly-CSharp-patch.bytes";
    public static final String FILE_CONFIG = "localConfig.json";

    public static final String PKG_FF_NORMAL = "com.dts.freefireth";
    public static final String PKG_FF_MAX    = "com.dts.freefiremax";

    private static final String[] BLOCKED_HOSTS = {
            "gin.freefireind.in",
            "gin.freefiremobile.com",
            "na.gin.freefiremobile.com",
            "gamesecurity.us.freefiremobile.com",
            "garena.co.id",
            "hotro.ff.garena.vn"
    };

    public interface InstallCallback {
        void onSuccess(String message);
        void onError(String error);
    }

    /**
     * Lấy danh sách các thư mục files của Free Fire trên máy
     */
    public static List<File> getGameFilesDirs() {
        List<File> list = new ArrayList<>();
        File ext = Environment.getExternalStorageDirectory();
        if (ext != null) {
            list.add(new File(ext, "Android/data/" + PKG_FF_NORMAL + "/files"));
            list.add(new File(ext, "Android/data/" + PKG_FF_MAX + "/files"));
        }
        return list;
    }

    /**
     * Kiểm tra xem bản vá đã được cài đặt vào game hay chưa
     */
    public static boolean isPatchInstalled() {
        for (File dir : getGameFilesDirs()) {
            File patch = new File(dir, FILE_PATCH);
            File config = new File(dir, FILE_CONFIG);
            if (patch.exists() && config.exists() && patch.length() > 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * Cài đặt bản vá 1-Click:
     * - Trích xuất assets
     * - Chép vào Android/data/com.dts.freefireth/files/ và com.dts.freefiremax/files/
     * - Đồng thời xuất ra Download/FF_Menu_OBB55 để dự phòng cho người dùng No-Root
     */
    public static void installPatch(Context context, InstallCallback callback) {
        new Thread(() -> {
            try {
                // 1. Trích xuất 2 file từ assets vào cache của app
                File cacheDir = context.getCacheDir();
                File tempPatch = new File(cacheDir, FILE_PATCH);
                File tempConfig = new File(cacheDir, FILE_CONFIG);

                copyAssetToFile(context, FILE_PATCH, tempPatch);
                copyAssetToFile(context, FILE_CONFIG, tempConfig);

                boolean copiedToGame = false;
                StringBuilder sbSuccess = new StringBuilder();

                // 2. Nếu có Root: dùng su để chép trực tiếp vào Android/data với quyền đầy đủ
                if (RootUtils.isRootAvailable()) {
                    for (File targetDir : getGameFilesDirs()) {
                        String cmd = "mkdir -p \"" + targetDir.getAbsolutePath() + "\" && "
                                + "cp -f \"" + tempPatch.getAbsolutePath() + "\" \"" + targetDir.getAbsolutePath() + "/" + FILE_PATCH + "\" && "
                                + "cp -f \"" + tempConfig.getAbsolutePath() + "\" \"" + targetDir.getAbsolutePath() + "/" + FILE_CONFIG + "\" && "
                                + "chmod 666 \"" + targetDir.getAbsolutePath() + "/" + FILE_PATCH + "\" && "
                                + "chmod 666 \"" + targetDir.getAbsolutePath() + "/" + FILE_CONFIG + "\"";
                        int exitCode = RootUtils.executeCommand(cmd);
                        if (exitCode == 0) {
                            copiedToGame = true;
                            sbSuccess.append("Đã cài vào: ").append(targetDir.getParentFile().getName()).append("\n");
                        }
                    }
                } else {
                    // 3. Nếu Không có Root: thử ghi trực tiếp bằng Java I/O
                    for (File targetDir : getGameFilesDirs()) {
                        try {
                            if (!targetDir.exists()) {
                                targetDir.mkdirs();
                            }
                            if (targetDir.exists() && targetDir.canWrite()) {
                                copyFile(tempPatch, new File(targetDir, FILE_PATCH));
                                copyFile(tempConfig, new File(targetDir, FILE_CONFIG));
                                copiedToGame = true;
                                sbSuccess.append("Đã cài vào: ").append(targetDir.getParentFile().getName()).append("\n");
                            }
                        } catch (Exception e) {
                            Log.w(TAG, "Cannot write directly to " + targetDir.getAbsolutePath(), e);
                        }
                    }
                }

                // 4. Luôn trích xuất một bản sao vào thư mục Download/FF_Menu_OBB55
                File dlDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "FF_Menu_OBB55");
                dlDir.mkdirs();
                copyFile(tempPatch, new File(dlDir, FILE_PATCH));
                copyFile(tempConfig, new File(dlDir, FILE_CONFIG));

                if (copiedToGame) {
                    callback.onSuccess("CÀI ĐẶT THÀNH CÔNG 100%!\n" + sbSuccess.toString().trim() + "\n\n(Bản sao dự phòng cũng đã lưu tại: Download/FF_Menu_OBB55)");
                } else {
                    callback.onSuccess("ĐÃ XUẤT BẢN VÁ RA THƯ MỤC:\nDownload/FF_Menu_OBB55\n\n(Trên Android 11+ No-Root, vui lòng mở ZArchiver copy 2 file từ thư mục Download này dán vào Android/data/com.dts.freefireth/files)");
                }

            } catch (Exception e) {
                Log.e(TAG, "Error installing patch", e);
                callback.onError("Lỗi khi nạp bản vá: " + e.getMessage());
            }
        }).start();
    }

    /**
     * Gỡ bỏ bản vá khỏi thư mục game
     */
    public static void removePatch(Context context, InstallCallback callback) {
        new Thread(() -> {
            try {
                boolean removed = false;
                for (File dir : getGameFilesDirs()) {
                    File patch = new File(dir, FILE_PATCH);
                    File config = new File(dir, FILE_CONFIG);

                    if (RootUtils.isRootAvailable()) {
                        RootUtils.executeCommand("rm -f \"" + patch.getAbsolutePath() + "\" \"" + config.getAbsolutePath() + "\"");
                        removed = true;
                    } else {
                        if (patch.exists()) patch.delete();
                        if (config.exists()) config.delete();
                        removed = true;
                    }
                }

                if (removed) {
                    callback.onSuccess("ĐÃ GỠ BỎ BẢN VÁ THÀNH CÔNG!\nFree Fire đã được khôi phục về trạng thái gốc.");
                } else {
                    callback.onError("Không tìm thấy bản vá nào trong máy để gỡ bỏ.");
                }
            } catch (Exception e) {
                callback.onError("Lỗi khi gỡ bỏ: " + e.getMessage());
            }
        }).start();
    }

    /**
     * Bật/Tắt Tường Lửa Chặn Quét Anti-Ban (Garena Security Hosts)
     */
    public static boolean applyAntiBanFirewall(boolean enable) {
        if (!RootUtils.isRootAvailable()) {
            return false;
        }

        for (String host : BLOCKED_HOSTS) {
            if (enable) {
                RootUtils.executeCommand("iptables -I OUTPUT -d " + host + " -j DROP");
            } else {
                RootUtils.executeCommand("iptables -D OUTPUT -d " + host + " -j DROP");
            }
        }
        return true;
    }

    private static void copyAssetToFile(Context context, String assetName, File outFile) throws IOException {
        try (InputStream in = context.getAssets().open(assetName);
             OutputStream out = new FileOutputStream(outFile)) {
            byte[] buf = new byte[8192];
            int read;
            while ((read = in.read(buf)) != -1) {
                out.write(buf, 0, read);
            }
            out.flush();
        }
    }

    private static void copyFile(File src, File dst) throws IOException {
        try (InputStream in = new java.io.FileInputStream(src);
             OutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[8192];
            int read;
            while ((read = in.read(buf)) != -1) {
                out.write(buf, 0, read);
            }
            out.flush();
        }
    }
}
