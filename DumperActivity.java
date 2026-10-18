package com.ff.cheat;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;

public class DumperActivity extends Activity {

    private TextView tvLog;
    private ScrollView scroll;
    private ProgressBar progress;
    private Button btnDump, btnSave, btnApply;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final StringBuilder logBuffer = new StringBuilder();
    private boolean isFlushing = false;

    static {
        System.loadLibrary("ffcheat");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dumper);

        tvLog    = findViewById(R.id.tvLog);
        scroll   = findViewById(R.id.scroll);
        progress = findViewById(R.id.progress);
        btnDump  = findViewById(R.id.btnDump);
        btnSave  = findViewById(R.id.btnSave);
        btnApply = findViewById(R.id.btnApply);

        btnDump.setOnClickListener(v -> startDumping());

        btnSave.setOnClickListener(v -> {
            File dest = new File(getExternalFilesDir(null), "offsets.json");
            String result = nativeSaveOffsets(dest.getAbsolutePath());
            appendLog("\n[SAVED] " + result + " -> " + dest.getAbsolutePath());
            Toast.makeText(this, "Đã lưu offsets.json", Toast.LENGTH_SHORT).show();
        });

        if (btnApply != null) {
            btnApply.setOnClickListener(v -> {
                nativeApplyOffsets();
                appendLog("\n[APPLIED] Offsets áp dụng trực tiếp vào Cheat Engine thành công!");
                Toast.makeText(this, "Đã áp dụng Offsets!", Toast.LENGTH_SHORT).show();
            });
        }
    }

    private void startDumping() {
        tvLog.setText("");
        progress.setVisibility(ProgressBar.VISIBLE);
        btnDump.setEnabled(false);
        if (btnSave != null) btnSave.setEnabled(false);
        if (btnApply != null) btnApply.setEnabled(false);

        new Thread(() -> {
            // Chuẩn bị môi trường root
            RootUtils.prepareEnvironment();

            // Chạy dump từ native C++
            nativeDump(this::appendLog);

            handler.post(() -> {
                flushLogBuffer();
                progress.setVisibility(ProgressBar.GONE);
                btnDump.setEnabled(true);
                if (btnSave != null) btnSave.setEnabled(true);
                if (btnApply != null) btnApply.setEnabled(true);
            });
        }).start();
    }

    /**
     * Gom cụm dòng log (Batching) để tránh spam UI thread làm đứng màn hình
     */
    private synchronized void appendLog(String line) {
        logBuffer.append(line).append("\n");
        if (!isFlushing) {
            isFlushing = true;
            handler.postDelayed(this::flushLogBuffer, 60);
        }
    }

    private synchronized void flushLogBuffer() {
        if (logBuffer.length() > 0) {
            tvLog.append(logBuffer.toString());
            logBuffer.setLength(0);
            scroll.post(() -> scroll.fullScroll(ScrollView.FOCUS_DOWN));
        }
        isFlushing = false;
    }

    // JNI Callbacks
    public interface LogCallback {
        void log(String line);
    }

    public native void nativeDump(LogCallback cb);
    public native String nativeSaveOffsets(String path);
    public native void nativeApplyOffsets();
}
