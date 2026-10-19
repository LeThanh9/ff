package com.ff.cheat;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.View;

/**
 * ESPView — Bộ kết xuất Overlay tối giản, hiệu năng cao
 * Tập trung vào:
 * 1. ESP Đọc bộ nhớ (Vẽ Box & Khung xương Skeleton)
 * 2. Vòng Aimbot FOV có thể kéo chỉnh bán kính to/nhỏ
 * 3. Tự động khóa vào Cổ (Auto Neck Lock) & điều khiển xoay góc nhìn camera khi mục tiêu vào vòng
 */
public class ESPView extends View {

    private volatile float[] players = new float[0];

    // Paints
    private final Paint boxPaint        = new Paint();
    private final Paint textPaint       = new Paint();
    private final Paint fovPaint        = new Paint();
    private final Paint aimRayPaint     = new Paint();
    private final Paint aimLockPaint    = new Paint();
    private final Paint skeletonPaint   = new Paint();
    private final Paint hudBgPaint      = new Paint();
    private final Paint hudTextPaint    = new Paint();
    private final Paint centerCrossPaint= new Paint();

    // Cấu hình tính năng
    private boolean drawBox          = true;
    private boolean drawSkeleton     = true;
    private boolean drawFovCircle    = true;
    private boolean autoNeckAim      = true; // Tự động khóa cổ khi mục tiêu vào vòng
    private boolean simulationMode   = true; // Mô phỏng test preview khi chưa mở game
    private boolean isGameConnected  = false;
    private float   fovRadius        = 180.0f; // Bán kính vòng FOV người chơi tùy chỉnh

    // Màu sắc cố định
    private static final int COLOR_CYAN       = 0xFF00FFCC;
    private static final int COLOR_HP_GREEN   = 0xFF00E676;
    private static final int COLOR_AIM_RAY    = 0xFFFF1744;
    private static final int COLOR_LOCK_RING  = 0xFFFF0055;

    private static final int STRIDE = 7;

    public ESPView(Context ctx) {
        super(ctx);
        setLayerType(LAYER_TYPE_HARDWARE, null);

        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(3.0f);
        boxPaint.setColor(0xFFFF2244);
        boxPaint.setAntiAlias(true);

        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(22.0f);
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

        aimRayPaint.setStyle(Paint.Style.STROKE);
        aimRayPaint.setColor(COLOR_AIM_RAY);
        aimRayPaint.setStrokeWidth(2.5f);
        aimRayPaint.setAntiAlias(true);

        aimLockPaint.setStyle(Paint.Style.STROKE);
        aimLockPaint.setColor(COLOR_LOCK_RING);
        aimLockPaint.setStrokeWidth(3.0f);
        aimLockPaint.setAntiAlias(true);

        hudBgPaint.setColor(0xCC0A0E1A);
        hudBgPaint.setStyle(Paint.Style.FILL);

        hudTextPaint.setColor(COLOR_CYAN);
        hudTextPaint.setTextSize(18.0f);
        hudTextPaint.setTypeface(Typeface.MONOSPACE);
        hudTextPaint.setFakeBoldText(true);
        hudTextPaint.setAntiAlias(true);

        skeletonPaint.setStyle(Paint.Style.STROKE);
        skeletonPaint.setColor(0xEE00FFCC);
        skeletonPaint.setStrokeWidth(2.0f);
        skeletonPaint.setAntiAlias(true);
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

    public void setAimbotTarget(float x, float y) {
        // Hỗ trợ cập nhật tọa độ nếu nhận từ bridge ngoài
        postInvalidate();
    }

    public void setSimulationMode(boolean on)  { this.simulationMode = on; postInvalidate(); }
    public boolean isSimulationMode()         { return simulationMode; }

    public void setDrawBox(boolean on)        { this.drawBox = on; postInvalidate(); }
    public boolean isDrawBox()                { return drawBox; }

    public void setDrawSkeleton(boolean on)   { this.drawSkeleton = on; postInvalidate(); }
    public boolean isDrawSkeleton()           { return drawSkeleton; }

    public void setDrawFovCircle(boolean on)  { this.drawFovCircle = on; postInvalidate(); }
    public boolean isDrawFovCircle()          { return drawFovCircle; }

    public void setAutoNeckAim(boolean on)    { this.autoNeckAim = on; postInvalidate(); }
    public boolean isAutoNeckAim()            { return autoNeckAim; }

    public void setFovRadius(float radius)    { this.fovRadius = radius; postInvalidate(); }
    public float getFovRadius()               { return fovRadius; }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        final float screenW = getWidth();
        final float screenH = getHeight();
        if (screenW <= 0 || screenH <= 0) return;

        final float screenCX = screenW * 0.5f;
        final float screenCY = screenH * 0.5f;

        // ── 1. HUD TRẠNG THÁI GÓC TRÊN ──
        canvas.drawRect(20.0f, 15.0f, 480.0f, 55.0f, hudBgPaint);
        String statusText = isGameConnected
                ? "⚡ BỘ NHỚ: HOOK LIVE | AUTO AIM CỔ"
                : "⚡ TEST PREVIEW | VÒNG FOV: " + (int)fovRadius + "PX";
        hudTextPaint.setColor(isGameConnected ? COLOR_HP_GREEN : COLOR_CYAN);
        canvas.drawText(statusText, 30.0f, 42.0f, hudTextPaint);

        // ── 2. VẼ VÒNG AIMBOT FOV Ở TÂM MÀN HÌNH ──
        if (drawFovCircle) {
            canvas.drawCircle(screenCX, screenCY, fovRadius, fovPaint);
            // Dấu cộng tâm nhỏ
            canvas.drawLine(screenCX - 10, screenCY, screenCX + 10, screenCY, centerCrossPaint);
            canvas.drawLine(screenCX, screenCY - 10, screenCX, screenCY + 10, centerCrossPaint);
        }

        final float[] currentPlayers = this.players;
        final boolean hasLivePlayers = (currentPlayers != null && currentPlayers.length >= STRIDE);

        // Biến lưu trữ mục tiêu cổ gần tâm nhất nằm trong vòng FOV
        float bestTargetX = -1.0f;
        float bestTargetY = -1.0f;
        float bestDistFromCenter = Float.MAX_VALUE;

        // ── 3. VẼ PLAYERS & TÌM MỤC TIÊU CỔ NẰM TRONG VÒNG FOV ──
        if (hasLivePlayers) {
            final int count = currentPlayers.length / STRIDE;
            for (int i = 0; i < count; ++i) {
                final int off = i * STRIDE;
                final float x = currentPlayers[off];
                final float y = currentPlayers[off + 1];
                final float w = currentPlayers[off + 2];
                final float h = currentPlayers[off + 3];

                final float top = y - h;
                final float bottom = y;

                // Vẽ Khung Hộp
                if (drawBox) {
                    canvas.drawRect(x - (w * 0.5f), top, x + (w * 0.5f), bottom, boxPaint);
                }

                // Vẽ Khung Xương
                if (drawSkeleton) {
                    drawSkeletonLines(canvas, x, top, bottom, w, h);
                }

                // Tọa độ đốt cổ: 18% từ đỉnh đầu xuống
                float neckX = x;
                float neckY = top + (h * 0.18f);

                // Kiểm tra mục tiêu có nằm trong vòng FOV không
                float dX = neckX - screenCX;
                float dY = neckY - screenCY;
                float distFromCenter = (float) Math.hypot(dX, dY);

                if (distFromCenter <= fovRadius && distFromCenter < bestDistFromCenter) {
                    bestDistFromCenter = distFromCenter;
                    bestTargetX = neckX;
                    bestTargetY = neckY;
                }
            }
        } else if (simulationMode) {
            // Chế độ mô phỏng để người chơi kéo chỉnh và xem trước vòng Aimbot
            float animOffset = (float) Math.sin(System.currentTimeMillis() * 0.003) * 8.0f;

            // Bot 1
            float b1X = screenCX - 150.0f;
            float b1Y = screenCY + 100.0f + animOffset;
            float b1W = 80.0f;
            float b1H = 175.0f;
            float b1Top = b1Y - b1H;

            if (drawBox) canvas.drawRect(b1X - (b1W * 0.5f), b1Top, b1X + (b1W * 0.5f), b1Y, boxPaint);
            if (drawSkeleton) drawSkeletonLines(canvas, b1X, b1Top, b1Y, b1W, b1H);

            float b1NeckX = b1X;
            float b1NeckY = b1Top + (b1H * 0.18f);

            float dX = b1NeckX - screenCX;
            float dY = b1NeckY - screenCY;
            float distFromCenter = (float) Math.hypot(dX, dY);

            if (distFromCenter <= fovRadius) {
                bestTargetX = b1NeckX;
                bestTargetY = b1NeckY;
            }

            // Bot 2 (ở xa hơn bên phải)
            float b2X = screenCX + 280.0f;
            float b2Y = screenCY + 60.0f - animOffset;
            float b2W = 65.0f;
            float b2H = 140.0f;
            float b2Top = b2Y - b2H;

            if (drawBox) canvas.drawRect(b2X - (b2W * 0.5f), b2Top, b2X + (b2W * 0.5f), b2Y, boxPaint);
            if (drawSkeleton) drawSkeletonLines(canvas, b2X, b2Top, b2Y, b2W, b2H);

            float b2NeckX = b2X;
            float b2NeckY = b2Top + (b2H * 0.18f);
            float dX2 = b2NeckX - screenCX;
            float dY2 = b2NeckY - screenCY;
            float dist2 = (float) Math.hypot(dX2, dY2);

            if (dist2 <= fovRadius && dist2 < distFromCenter) {
                bestTargetX = b2NeckX;
                bestTargetY = b2NeckY;
            }
        }

        // ── 4. TỰ ĐỘNG KHÓA VÀ XOAY GÓC NHÌN VÀO CỔ KHI TRONG VÒNG FOV ──
        if (autoNeckAim && bestTargetX > 0 && bestTargetY > 0) {
            // Vẽ tia khóa đỏ từ tâm màn hình đến đốt cổ
            canvas.drawLine(screenCX, screenCY, bestTargetX, bestTargetY, aimRayPaint);
            canvas.drawCircle(bestTargetX, bestTargetY, 14.0f, aimLockPaint);
            canvas.drawText("🎯 AUTO AIM CỔ", bestTargetX - 65.0f, bestTargetY - 20.0f, textPaint);

            // Điều khiển xoay góc nhìn camera về phía đốt cổ (chạy mượt qua RootUtils)
            if (RootUtils.isAutoAimDragEnabled()) {
                RootUtils.triggerAimDrag(screenCX, screenCY, bestTargetX, bestTargetY);
            }
        }
    }

    private void drawSkeletonLines(Canvas canvas, float x, float top, float bottom, float w, float h) {
        float headR = h * 0.08f;
        float headY = top + headR;
        float neckY = top + h * 0.18f;
        float spineY = top + h * 0.45f;
        float pelvisY = top + h * 0.55f;

        // Đầu
        canvas.drawCircle(x, headY, headR, skeletonPaint);

        // Cột sống nối từ đầu qua cổ đến eo
        canvas.drawLine(x, headY + headR, x, spineY, skeletonPaint);
        canvas.drawLine(x, spineY, x, pelvisY, skeletonPaint);

        // Vai và cánh tay
        float shoulderW = w * 0.35f;
        float elbowY = top + h * 0.35f;
        float handY = top + h * 0.52f;
        // Tay trái
        canvas.drawLine(x, neckY, x - shoulderW, neckY + h * 0.04f, skeletonPaint);
        canvas.drawLine(x - shoulderW, neckY + h * 0.04f, x - shoulderW - w * 0.1f, elbowY, skeletonPaint);
        canvas.drawLine(x - shoulderW - w * 0.1f, elbowY, x - shoulderW - w * 0.15f, handY, skeletonPaint);
        // Tay phải
        canvas.drawLine(x, neckY, x + shoulderW, neckY + h * 0.04f, skeletonPaint);
        canvas.drawLine(x + shoulderW, neckY + h * 0.04f, x + shoulderW + w * 0.1f, elbowY, skeletonPaint);
        canvas.drawLine(x + shoulderW + w * 0.1f, elbowY, x + shoulderW + w * 0.15f, handY, skeletonPaint);

        // Chân trái & Chân phải
        float kneeY = top + h * 0.78f;
        float legW = w * 0.28f;
        // Chân trái
        canvas.drawLine(x, pelvisY, x - legW * 0.6f, kneeY, skeletonPaint);
        canvas.drawLine(x - legW * 0.6f, kneeY, x - legW, bottom, skeletonPaint);
        // Chân phải
        canvas.drawLine(x, pelvisY, x + legW * 0.6f, kneeY, skeletonPaint);
        canvas.drawLine(x + legW * 0.6f, kneeY, x + legW, bottom, skeletonPaint);
    }
}
