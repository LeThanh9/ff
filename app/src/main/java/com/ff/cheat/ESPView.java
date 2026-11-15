package com.ff.cheat;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.View;

/**
 * ESPView — Bộ kết xuất Lớp phủ ESP & Kéo tâm (Drag Headshot) hiệu năng cao
 * Nâng cấp bổ sung:
 * 1. Radar 2D Chiến Thuật Mini-Map (360 độ góc nhìn)
 * 2. Cảnh báo Kẻ Địch Cận Chiến (< 25m) nhấp nháy chống bị móc lốp
 * 3. Hỗ trợ Rung phản hồi (Haptic Pulse) khi khóa mục tiêu
 * 4. Tương thích 100% Android No-Root & Xiaomi HyperOS 60/90/120/144 FPS
 */
public class ESPView extends View {

    private volatile float[] players = new float[0];

    // Cọ vẽ chính
    private final Paint boxPaint         = new Paint();
    private final Paint cornerPaint      = new Paint();
    private final Paint textPaint        = new Paint();
    private final Paint fovPaint         = new Paint();
    private final Paint aimRayPaint      = new Paint();
    private final Paint aimLockPaint     = new Paint();
    private final Paint skeletonPaint    = new Paint();
    private final Paint jointPaint       = new Paint();
    private final Paint neckCrossPaint   = new Paint();
    private final Paint snaplinePaint    = new Paint();
    private final Paint hpBgPaint        = new Paint();
    private final Paint hpBarPaint       = new Paint();
    private final Paint hudBgPaint       = new Paint();
    private final Paint hudTextPaint     = new Paint();
    private final Paint centerCrossPaint = new Paint();

    // Cọ vẽ Radar 2D & Cảnh Báo
    private final Paint radarBgPaint     = new Paint();
    private final Paint radarBorderPaint = new Paint();
    private final Paint radarGridPaint   = new Paint();
    private final Paint radarBlipPaint   = new Paint();
    private final Paint radarSelfPaint   = new Paint();
    private final Paint alertBgPaint     = new Paint();
    private final Paint alertTextPaint   = new Paint();

    // Cấu hình tính năng
    private boolean drawBox              = true;
    private boolean drawSkeleton         = true;
    private boolean drawSnapline         = true;
    private boolean drawHealthBar        = true;
    private boolean drawNeckCrosshair    = true;
    private boolean drawFovCircle        = true;
    private boolean autoNeckAim          = true;
    private boolean drawRadar            = true;  // Radar 2D thu nhỏ góc trái
    private boolean drawProximityAlert   = true;  // Cảnh báo kẻ địch áp sát
    private boolean enableHapticFeedback = true;  // Rung phản hồi khi khóa
    private boolean simulationMode       = true;
    private boolean isGameConnected      = false;
    private float   fovRadius            = 180.0f;

    private Vibrator vibrator;
    private long lastHapticTime = 0;

    // Bảng màu chuẩn hiển thị
    private static final int COLOR_CYAN       = 0xFF00FFCC;
    private static final int COLOR_HP_GREEN   = 0xFF00E676;
    private static final int COLOR_HP_YELLOW  = 0xFFFFD600;
    private static final int COLOR_HP_RED     = 0xFFFF1744;
    private static final int COLOR_AIM_RAY    = 0xFFFF1744;
    private static final int COLOR_LOCK_RING  = 0xFFFF0055;
    private static final int COLOR_SNAPLINE   = 0x8800E5FF;
    private static final int COLOR_SKELETON   = 0xEE00FFCC;
    private static final int COLOR_JOINT      = 0xFFFFFF00;

    private static final int STRIDE = 7;

    public ESPView(Context ctx) {
        super(ctx);
        setLayerType(LAYER_TYPE_HARDWARE, null);

        try {
            vibrator = (Vibrator) ctx.getSystemService(Context.VIBRATOR_SERVICE);
        } catch (Exception ignored) {}

        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(2.5f);
        boxPaint.setColor(0xCCFF2244);
        boxPaint.setAntiAlias(true);

        cornerPaint.setStyle(Paint.Style.STROKE);
        cornerPaint.setStrokeWidth(3.5f);
        cornerPaint.setColor(0xFFFF2244);
        cornerPaint.setAntiAlias(true);

        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(20.0f);
        textPaint.setTypeface(Typeface.MONOSPACE);
        textPaint.setFakeBoldText(true);
        textPaint.setShadowLayer(3.0f, 1.0f, 1.0f, Color.BLACK);
        textPaint.setAntiAlias(true);

        fovPaint.setStyle(Paint.Style.STROKE);
        fovPaint.setColor(0x6600E5FF);
        fovPaint.setStrokeWidth(2.5f);
        fovPaint.setAntiAlias(true);

        centerCrossPaint.setColor(0x8800E5FF);
        centerCrossPaint.setStrokeWidth(1.5f);
        centerCrossPaint.setAntiAlias(true);

        snaplinePaint.setStyle(Paint.Style.STROKE);
        snaplinePaint.setColor(COLOR_SNAPLINE);
        snaplinePaint.setStrokeWidth(1.8f);
        snaplinePaint.setAntiAlias(true);

        aimRayPaint.setStyle(Paint.Style.STROKE);
        aimRayPaint.setColor(COLOR_AIM_RAY);
        aimRayPaint.setStrokeWidth(2.5f);
        aimRayPaint.setAntiAlias(true);

        aimLockPaint.setStyle(Paint.Style.STROKE);
        aimLockPaint.setColor(COLOR_LOCK_RING);
        aimLockPaint.setStrokeWidth(3.0f);
        aimLockPaint.setAntiAlias(true);

        skeletonPaint.setStyle(Paint.Style.STROKE);
        skeletonPaint.setColor(COLOR_SKELETON);
        skeletonPaint.setStrokeWidth(2.2f);
        skeletonPaint.setAntiAlias(true);

        jointPaint.setStyle(Paint.Style.FILL);
        jointPaint.setColor(COLOR_JOINT);
        jointPaint.setAntiAlias(true);

        neckCrossPaint.setStyle(Paint.Style.STROKE);
        neckCrossPaint.setColor(0xFFFF1744);
        neckCrossPaint.setStrokeWidth(2.0f);
        neckCrossPaint.setAntiAlias(true);

        hpBgPaint.setStyle(Paint.Style.FILL);
        hpBgPaint.setColor(0x99000000);

        hpBarPaint.setStyle(Paint.Style.FILL);
        hpBarPaint.setColor(COLOR_HP_GREEN);
        hpBarPaint.setAntiAlias(true);

        hudBgPaint.setColor(0xCC0A0E1A);
        hudBgPaint.setStyle(Paint.Style.FILL);

        hudTextPaint.setColor(COLOR_CYAN);
        hudTextPaint.setTextSize(18.0f);
        hudTextPaint.setTypeface(Typeface.MONOSPACE);
        hudTextPaint.setFakeBoldText(true);
        hudTextPaint.setAntiAlias(true);

        // Khởi tạo màu Radar
        radarBgPaint.setColor(0xDD0D1122);
        radarBgPaint.setStyle(Paint.Style.FILL);

        radarBorderPaint.setColor(COLOR_CYAN);
        radarBorderPaint.setStyle(Paint.Style.STROKE);
        radarBorderPaint.setStrokeWidth(2.0f);
        radarBorderPaint.setAntiAlias(true);

        radarGridPaint.setColor(0x4400FFCC);
        radarGridPaint.setStyle(Paint.Style.STROKE);
        radarGridPaint.setStrokeWidth(1.2f);
        radarGridPaint.setAntiAlias(true);

        radarBlipPaint.setColor(COLOR_HP_RED);
        radarBlipPaint.setStyle(Paint.Style.FILL);
        radarBlipPaint.setAntiAlias(true);

        radarSelfPaint.setColor(COLOR_HP_GREEN);
        radarSelfPaint.setStyle(Paint.Style.FILL);
        radarSelfPaint.setAntiAlias(true);

        alertBgPaint.setColor(0xCCB71C1C);
        alertBgPaint.setStyle(Paint.Style.FILL);

        alertTextPaint.setColor(Color.WHITE);
        alertTextPaint.setTextSize(17.0f);
        alertTextPaint.setTypeface(Typeface.MONOSPACE);
        alertTextPaint.setFakeBoldText(true);
        alertTextPaint.setAntiAlias(true);
    }

    public void updatePlayers(float[] data) {
        if (data == null || data.length == 0) {
            this.players = new float[0];
            this.isGameConnected = false;
        } else {
            this.players = data;
            this.isGameConnected = true;
        }
        postInvalidate();
    }

    public void setSimulationMode(boolean on)    { this.simulationMode = on; postInvalidate(); }
    public boolean isSimulationMode()           { return simulationMode; }

    public void setDrawBox(boolean on)          { this.drawBox = on; postInvalidate(); }
    public boolean isDrawBox()                  { return drawBox; }

    public void setDrawSkeleton(boolean on)     { this.drawSkeleton = on; postInvalidate(); }
    public boolean isDrawSkeleton()             { return drawSkeleton; }

    public void setDrawSnapline(boolean on)     { this.drawSnapline = on; postInvalidate(); }
    public boolean isDrawSnapline()             { return drawSnapline; }

    public void setDrawHealthBar(boolean on)    { this.drawHealthBar = on; postInvalidate(); }
    public boolean isDrawHealthBar()            { return drawHealthBar; }

    public void setDrawNeckCrosshair(boolean on){ this.drawNeckCrosshair = on; postInvalidate(); }
    public boolean isDrawNeckCrosshair()        { return drawNeckCrosshair; }

    public void setDrawFovCircle(boolean on)    { this.drawFovCircle = on; postInvalidate(); }
    public boolean isDrawFovCircle()            { return drawFovCircle; }

    public void setAutoNeckAim(boolean on)      { this.autoNeckAim = on; postInvalidate(); }
    public boolean isAutoNeckAim()              { return autoNeckAim; }

    public void setDrawRadar(boolean on)        { this.drawRadar = on; postInvalidate(); }
    public boolean isDrawRadar()                { return drawRadar; }

    public void setDrawProximityAlert(boolean on){ this.drawProximityAlert = on; postInvalidate(); }
    public boolean isDrawProximityAlert()       { return drawProximityAlert; }

    public void setEnableHapticFeedback(boolean on){ this.enableHapticFeedback = on; }
    public boolean isEnableHapticFeedback()     { return enableHapticFeedback; }

    public void setFovRadius(float radius)      { this.fovRadius = radius; postInvalidate(); }
    public float getFovRadius()                 { return fovRadius; }

    private void triggerHapticPulse() {
        if (!enableHapticFeedback || vibrator == null) return;
        long now = System.currentTimeMillis();
        if (now - lastHapticTime < 500) return;
        lastHapticTime = now;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(20);
            }
        } catch (Exception ignored) {}
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        final float screenW = getWidth();
        final float screenH = getHeight();
        if (screenW <= 0 || screenH <= 0) return;

        final float screenCX = screenW * 0.5f;
        final float screenCY = screenH * 0.5f;

        // ── 1. HUD BẢNG TRẠNG THÁI GÓC TRÊN ──
        canvas.drawRect(20.0f, 15.0f, 540.0f, 58.0f, hudBgPaint);
        String statusText = isGameConnected
                ? "⚡ TRỰC TIẾP: LIÊN KẾT BỘ NHỚ | KÉO TÂM CỔ"
                : "⚡ MÔ PHỎNG XEM TRƯỚC | FOV: " + (int)fovRadius + "PX";
        hudTextPaint.setColor(isGameConnected ? COLOR_HP_GREEN : COLOR_CYAN);
        canvas.drawText(statusText, 30.0f, 43.0f, hudTextPaint);

        // ── 2. VÒNG QUÉT FOV TẠI TÂM MÀN HÌNH ──
        if (drawFovCircle) {
            canvas.drawCircle(screenCX, screenCY, fovRadius, fovPaint);
            canvas.drawLine(screenCX - 12, screenCY, screenCX + 12, screenCY, centerCrossPaint);
            canvas.drawLine(screenCX, screenCY - 12, screenCX, screenCY + 12, centerCrossPaint);
        }

        final float[] currentPlayers = this.players;
        final boolean hasLivePlayers = (currentPlayers != null && currentPlayers.length >= STRIDE);

        float bestTargetX = -1.0f;
        float bestTargetY = -1.0f;
        float bestDistFromCenter = Float.MAX_VALUE;

        int nearbyEnemyCount = 0;
        float minEnemyDistance = Float.MAX_VALUE;

        // Mảng lưu tọa độ radar (dx, dy tương đối)
        float[] radarOffsets = null;
        int radarCount = 0;

        // ── 3. HIỂN THỊ CHI TIẾT NGƯỜI CHƠI TRÊN CHIẾN TRƯỜNG ──
        if (hasLivePlayers) {
            final int count = currentPlayers.length / STRIDE;
            radarOffsets = new float[count * 2];
            radarCount = count;

            for (int i = 0; i < count; ++i) {
                final int off = i * STRIDE;
                final float x = currentPlayers[off];
                final float y = currentPlayers[off + 1];
                final float w = currentPlayers[off + 2];
                final float h = currentPlayers[off + 3];
                final float hp = (off + 4 < currentPlayers.length) ? currentPlayers[off + 4] : 100.0f;
                final float dist = (off + 5 < currentPlayers.length) ? currentPlayers[off + 5] : 0.0f;

                final float top = y - h;
                final float bottom = y;

                if (dist > 0 && dist < minEnemyDistance) minEnemyDistance = dist;
                if (dist > 0 && dist <= 28.0f) nearbyEnemyCount++;

                // Tính tọa độ điểm trên radar
                float relX = (x - screenCX) / screenCX;
                float relDist = Math.min(1.0f, dist / 120.0f);
                radarOffsets[i * 2]     = relX * 50.0f;
                radarOffsets[i * 2 + 1] = -relDist * 50.0f;

                if (drawSnapline) {
                    canvas.drawLine(screenCX, screenH, x, bottom, snaplinePaint);
                }

                if (drawBox) {
                    drawCornerBox(canvas, x, top, bottom, w);
                }

                if (drawSkeleton) {
                    drawFullSkeleton(canvas, x, top, bottom, w, h);
                }

                final float neckX = x;
                final float neckY = top + (h * 0.18f);

                if (drawNeckCrosshair) {
                    drawNeckCrosshairPoint(canvas, neckX, neckY);
                }

                if (drawHealthBar) {
                    drawHpAndTag(canvas, x, top, bottom, w, hp, dist, "ĐỊCH");
                }

                float dX = neckX - screenCX;
                float dY = neckY - screenCY;
                float distCenter = (float) Math.hypot(dX, dY);

                if (distCenter <= fovRadius && distCenter < bestDistFromCenter) {
                    bestDistFromCenter = distCenter;
                    bestTargetX = neckX;
                    bestTargetY = neckY;
                }
            }
        } else if (simulationMode) {
            float animOffset = (float) Math.sin(System.currentTimeMillis() * 0.003) * 8.0f;

            radarOffsets = new float[4];
            radarCount = 2;

            // Mục tiêu 1 (Cự ly gần: ~22m)
            float b1X = screenCX - 150.0f;
            float b1Y = screenCY + 110.0f + animOffset;
            float b1W = 85.0f;
            float b1H = 180.0f;
            float b1Top = b1Y - b1H;

            nearbyEnemyCount++;
            minEnemyDistance = 22.0f;

            radarOffsets[0] = -25.0f;
            radarOffsets[1] = -18.0f;

            if (drawSnapline) canvas.drawLine(screenCX, screenH, b1X, b1Y, snaplinePaint);
            if (drawBox) drawCornerBox(canvas, b1X, b1Top, b1Y, b1W);
            if (drawSkeleton) drawFullSkeleton(canvas, b1X, b1Top, b1Y, b1W, b1H);
            if (drawHealthBar) drawHpAndTag(canvas, b1X, b1Top, b1Y, b1W, 85.0f, 22.0f, "MỤC TIÊU 1");

            float b1NeckX = b1X;
            float b1NeckY = b1Top + (b1H * 0.18f);
            if (drawNeckCrosshair) drawNeckCrosshairPoint(canvas, b1NeckX, b1NeckY);

            float dX1 = b1NeckX - screenCX;
            float dY1 = b1NeckY - screenCY;
            float dist1 = (float) Math.hypot(dX1, dY1);

            if (dist1 <= fovRadius) {
                bestTargetX = b1NeckX;
                bestTargetY = b1NeckY;
                bestDistFromCenter = dist1;
            }

            // Mục tiêu 2 (Cự ly xa hơn: ~58m)
            float b2X = screenCX + 280.0f;
            float b2Y = screenCY + 65.0f - animOffset;
            float b2W = 65.0f;
            float b2H = 140.0f;
            float b2Top = b2Y - b2H;

            radarOffsets[2] = 38.0f;
            radarOffsets[3] = -42.0f;

            if (drawSnapline) canvas.drawLine(screenCX, screenH, b2X, b2Y, snaplinePaint);
            if (drawBox) drawCornerBox(canvas, b2X, b2Top, b2Y, b2W);
            if (drawSkeleton) drawFullSkeleton(canvas, b2X, b2Top, b2Y, b2W, b2H);
            if (drawHealthBar) drawHpAndTag(canvas, b2X, b2Top, b2Y, b2W, 40.0f, 58.0f, "MỤC TIÊU 2");

            float b2NeckX = b2X;
            float b2NeckY = b2Top + (b2H * 0.18f);
            if (drawNeckCrosshair) drawNeckCrosshairPoint(canvas, b2NeckX, b2NeckY);

            float dX2 = b2NeckX - screenCX;
            float dY2 = b2NeckY - screenCY;
            float dist2 = (float) Math.hypot(dX2, dY2);

            if (dist2 <= fovRadius && dist2 < bestDistFromCenter) {
                bestTargetX = b2NeckX;
                bestTargetY = b2NeckY;
            }
        }

        // ── 4. RADAR 2D CHIẾN THUẬT MINI-MAP ──
        if (drawRadar) {
            drawTacticalRadar(canvas, radarOffsets, radarCount);
        }

        // ── 5. CẢNH BÁO KẺ ĐỊCH CẬN CHIẾN (< 28M) ──
        if (drawProximityAlert && nearbyEnemyCount > 0 && minEnemyDistance < Float.MAX_VALUE) {
            drawNearbyAlertBanner(canvas, screenCX, nearbyEnemyCount, minEnemyDistance);
        }

        // ── 6. TỰ ĐỘNG KHÓA VÀ VUỐT TÂM VÀO ĐỐT CỔ (DRAG HEADSHOT) ──
        if (autoNeckAim && bestTargetX > 0 && bestTargetY > 0) {
            canvas.drawLine(screenCX, screenCY, bestTargetX, bestTargetY, aimRayPaint);
            canvas.drawCircle(bestTargetX, bestTargetY, 15.0f, aimLockPaint);
            canvas.drawText("🎯 KHÓA CỔ (KÉO TÂM CHỮ J)", bestTargetX - 105.0f, bestTargetY - 22.0f, textPaint);

            triggerHapticPulse();

            if (RootUtils.isAutoAimDragEnabled()) {
                RootUtils.triggerAimDrag(screenCX, screenCY, bestTargetX, bestTargetY);
            }
        }
    }

    /**
     * Vẽ Radar 2D Chiến Thuật Mini-Map ở góc trên bên trái
     */
    private void drawTacticalRadar(Canvas canvas, float[] radarOffsets, int count) {
        final float radarCX = 75.0f;
        final float radarCY = 145.0f;
        final float radarR  = 55.0f;

        // Nền đen tròn radar
        canvas.drawCircle(radarCX, radarCY, radarR, radarBgPaint);
        // Vành viền Cyan
        canvas.drawCircle(radarCX, radarCY, radarR, radarBorderPaint);
        // Vòng cự ly 50m bên trong
        canvas.drawCircle(radarCX, radarCY, radarR * 0.5f, radarGridPaint);
        // Trục chữ thập định hướng
        canvas.drawLine(radarCX - radarR, radarCY, radarCX + radarR, radarCY, radarGridPaint);
        canvas.drawLine(radarCX, radarCY - radarR, radarCX, radarCY + radarR, radarGridPaint);

        // Chấm tâm (Vị trí bản thân - Xanh lá)
        canvas.drawCircle(radarCX, radarCY, 3.5f, radarSelfPaint);

        // Vẽ các chấm mục tiêu kẻ địch (Đỏ)
        if (radarOffsets != null && count > 0) {
            for (int i = 0; i < count; i++) {
                float rx = radarCX + radarOffsets[i * 2];
                float ry = radarCY + radarOffsets[i * 2 + 1];

                // Giới hạn trong bán kính radar
                float dx = rx - radarCX;
                float dy = ry - radarCY;
                float dist = (float) Math.hypot(dx, dy);
                if (dist > radarR - 4.0f) {
                    rx = radarCX + (dx / dist) * (radarR - 4.0f);
                    ry = radarCY + (dy / dist) * (radarR - 4.0f);
                }
                canvas.drawCircle(rx, ry, 3.5f, radarBlipPaint);
            }
        }
    }

    /**
     * Vẽ Cảnh Báo Kẻ Địch Cận Chiến Nhấp Nháy (< 28m)
     */
    private void drawNearbyAlertBanner(Canvas canvas, float screenCX, int count, float minDist) {
        float bannerW = 340.0f;
        float bannerH = 34.0f;
        float left = screenCX - (bannerW * 0.5f);
        float top = 18.0f;

        canvas.drawRect(left, top, left + bannerW, top + bannerH, alertBgPaint);

        String alertMsg = "⚠️ CẢNH BÁO: " + count + " ĐỊCH GẦN [" + (int)minDist + "M]";
        float textW = alertTextPaint.measureText(alertMsg);
        canvas.drawText(alertMsg, screenCX - (textW * 0.5f), top + 23.0f, alertTextPaint);
    }

    private void drawCornerBox(Canvas canvas, float x, float top, float bottom, float w) {
        float halfW = w * 0.5f;
        float left = x - halfW;
        float right = x + halfW;
        float lineLen = Math.min(w * 0.25f, 25.0f);

        canvas.drawRect(left, top, right, bottom, boxPaint);

        canvas.drawLine(left, top, left + lineLen, top, cornerPaint);
        canvas.drawLine(left, top, left, top + lineLen, cornerPaint);
        canvas.drawLine(right, top, right - lineLen, top, cornerPaint);
        canvas.drawLine(right, top, right, top + lineLen, cornerPaint);
        canvas.drawLine(left, bottom, left + lineLen, bottom, cornerPaint);
        canvas.drawLine(left, bottom, left, bottom - lineLen, cornerPaint);
        canvas.drawLine(right, bottom, right - lineLen, bottom, cornerPaint);
        canvas.drawLine(right, bottom, right, bottom - lineLen, cornerPaint);
    }

    private void drawFullSkeleton(Canvas canvas, float x, float top, float bottom, float w, float h) {
        float headR    = h * 0.08f;
        float headY    = top + headR;
        float neckY    = top + h * 0.18f;
        float spineY   = top + h * 0.45f;
        float pelvisY  = top + h * 0.55f;

        canvas.drawCircle(x, headY, headR, skeletonPaint);
        canvas.drawLine(x, headY + headR, x, spineY, skeletonPaint);
        canvas.drawLine(x, spineY, x, pelvisY, skeletonPaint);

        float shoulderW = w * 0.35f;
        float elbowY    = top + h * 0.35f;
        float handY     = top + h * 0.52f;

        canvas.drawCircle(x - shoulderW, neckY + h * 0.03f, 3.5f, jointPaint);
        canvas.drawCircle(x + shoulderW, neckY + h * 0.03f, 3.5f, jointPaint);

        canvas.drawLine(x, neckY, x - shoulderW, neckY + h * 0.03f, skeletonPaint);
        canvas.drawLine(x - shoulderW, neckY + h * 0.03f, x - shoulderW - w * 0.1f, elbowY, skeletonPaint);
        canvas.drawCircle(x - shoulderW - w * 0.1f, elbowY, 3.0f, jointPaint);
        canvas.drawLine(x - shoulderW - w * 0.1f, elbowY, x - shoulderW - w * 0.14f, handY, skeletonPaint);
        canvas.drawCircle(x - shoulderW - w * 0.14f, handY, 2.5f, jointPaint);

        canvas.drawLine(x, neckY, x + shoulderW, neckY + h * 0.03f, skeletonPaint);
        canvas.drawLine(x + shoulderW, neckY + h * 0.03f, x + shoulderW + w * 0.1f, elbowY, skeletonPaint);
        canvas.drawCircle(x + shoulderW + w * 0.1f, elbowY, 3.0f, jointPaint);
        canvas.drawLine(x + shoulderW + w * 0.1f, elbowY, x + shoulderW + w * 0.14f, handY, skeletonPaint);
        canvas.drawCircle(x + shoulderW + w * 0.14f, handY, 2.5f, jointPaint);

        float kneeY = top + h * 0.77f;
        float legW  = w * 0.28f;

        canvas.drawCircle(x - legW * 0.4f, pelvisY, 3.5f, jointPaint);
        canvas.drawCircle(x + legW * 0.4f, pelvisY, 3.5f, jointPaint);

        canvas.drawLine(x, pelvisY, x - legW * 0.6f, kneeY, skeletonPaint);
        canvas.drawCircle(x - legW * 0.6f, kneeY, 3.5f, jointPaint);
        canvas.drawLine(x - legW * 0.6f, kneeY, x - legW, bottom, skeletonPaint);
        canvas.drawCircle(x - legW, bottom, 3.0f, jointPaint);

        canvas.drawLine(x, pelvisY, x + legW * 0.6f, kneeY, skeletonPaint);
        canvas.drawCircle(x + legW * 0.6f, kneeY, 3.5f, jointPaint);
        canvas.drawLine(x + legW * 0.6f, kneeY, x + legW, bottom, skeletonPaint);
        canvas.drawCircle(x + legW, bottom, 3.0f, jointPaint);
    }

    private void drawNeckCrosshairPoint(Canvas canvas, float nx, float ny) {
        canvas.drawCircle(nx, ny, 7.0f, neckCrossPaint);
        canvas.drawLine(nx - 14.0f, ny, nx - 6.0f, ny, neckCrossPaint);
        canvas.drawLine(nx + 6.0f, ny, nx + 14.0f, ny, neckCrossPaint);
        canvas.drawLine(nx, ny - 14.0f, nx, ny - 6.0f, neckCrossPaint);
        canvas.drawLine(nx, ny + 6.0f, nx, ny + 14.0f, neckCrossPaint);
    }

    private void drawHpAndTag(Canvas canvas, float x, float top, float bottom, float w, float hp, float dist, String label) {
        float halfW = w * 0.5f;
        float barLeft = x - halfW - 10.0f;
        float barWidth = 5.0f;
        float totalH = bottom - top;

        canvas.drawRect(barLeft, top, barLeft + barWidth, bottom, hpBgPaint);

        float clampedHp = Math.max(0.0f, Math.min(100.0f, hp));
        float currentHpHeight = totalH * (clampedHp / 100.0f);
        float hpTop = bottom - currentHpHeight;

        if (clampedHp > 50.0f) {
            hpBarPaint.setColor(COLOR_HP_GREEN);
        } else if (clampedHp > 20.0f) {
            hpBarPaint.setColor(COLOR_HP_YELLOW);
        } else {
            hpBarPaint.setColor(COLOR_HP_RED);
        }
        canvas.drawRect(barLeft, hpTop, barLeft + barWidth, bottom, hpBarPaint);

        String tag = label + " [" + (int)dist + "M] " + (int)clampedHp + "%";
        float textW = textPaint.measureText(tag);
        canvas.drawText(tag, x - (textW * 0.5f), top - 10.0f, textPaint);
    }
}
