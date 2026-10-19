package com.ff.cheat;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.View;

/**
 * ESPView — High-Performance Overlay Canvas Renderer
 * Thiết kế Zero-Allocation trong onDraw để đạt mượt mà 60–120 FPS
 * Tích hợp Chế độ Mô phỏng ESP (Simulation Preview) và Aimbot Lock Visual
 */
public class ESPView extends View {

    private volatile float[] players = new float[0];

    // Paints
    private final Paint boxPaint        = new Paint();
    private final Paint hpPaint         = new Paint();
    private final Paint hpBg            = new Paint();
    private final Paint textPaint       = new Paint();
    private final Paint snapPaint       = new Paint();
    private final Paint fovPaint        = new Paint();
    private final Paint aimRayPaint     = new Paint();
    private final Paint aimLockPaint    = new Paint();
    private final Paint skeletonPaint   = new Paint();
    private final Paint hudBgPaint      = new Paint();
    private final Paint hudTextPaint    = new Paint();

    // Cấu hình hiển thị
    private boolean drawBox          = true;
    private boolean drawSnapline     = true;
    private boolean drawHealth       = true;
    private boolean drawDistance     = true;
    private boolean drawName         = true;
    private boolean drawSkeleton     = true;
    private boolean drawFovCircle    = true;
    private boolean cornerBoxMode    = true;
    private boolean drawAimbotRay    = true;
    private boolean aimAtNeck        = true; // Tâm ngay cổ để hỗ trợ kéo tâm (Drag Headshot)
    private boolean simulationMode   = true; // Mặc định hiển thị mô phỏng nếu game chưa load
    private boolean isGameConnected  = false;
    private float   fovRadius        = 180.0f;

    // Aimbot target coordinates
    private float aimbotTargetX = -1.0f;
    private float aimbotTargetY = -1.0f;

    // Màu sắc cố định (tránh parseColor trong loop)
    private static final int COLOR_RED        = 0xFFFF2244;
    private static final int COLOR_CYAN       = 0xFF00FFCC;
    private static final int COLOR_ORANGE     = 0xFFFF9900;
    private static final int COLOR_HP_GREEN   = 0xFF00E676;
    private static final int COLOR_HP_YELLOW  = 0xFFFFD600;
    private static final int COLOR_HP_RED     = 0xFFFF1744;
    private static final int COLOR_HP_BG      = 0xCC1A1A1A;
    private static final int COLOR_AIM_RAY    = 0xCCFF1744;
    private static final int COLOR_LOCK_RING  = 0xFFFF0055;

    private static final int STRIDE = 7;

    public ESPView(Context ctx) {
        super(ctx);
        setLayerType(LAYER_TYPE_HARDWARE, null);

        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(3.0f);
        boxPaint.setAntiAlias(true);

        hpPaint.setStyle(Paint.Style.FILL);
        hpPaint.setAntiAlias(true);

        hpBg.setStyle(Paint.Style.FILL);
        hpBg.setColor(COLOR_HP_BG);

        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(24.0f);
        textPaint.setTypeface(Typeface.MONOSPACE);
        textPaint.setFakeBoldText(true);
        textPaint.setShadowLayer(3.0f, 1.0f, 1.0f, Color.BLACK);
        textPaint.setAntiAlias(true);

        snapPaint.setStrokeWidth(2.0f);
        snapPaint.setAntiAlias(true);

        fovPaint.setStyle(Paint.Style.STROKE);
        fovPaint.setColor(0x4400E5FF);
        fovPaint.setStrokeWidth(2.0f);
        fovPaint.setAntiAlias(true);

        aimRayPaint.setStyle(Paint.Style.STROKE);
        aimRayPaint.setColor(COLOR_AIM_RAY);
        aimRayPaint.setStrokeWidth(2.5f);
        aimRayPaint.setAntiAlias(true);

        aimLockPaint.setStyle(Paint.Style.STROKE);
        aimLockPaint.setColor(COLOR_LOCK_RING);
        aimLockPaint.setStrokeWidth(3.0f);
        aimLockPaint.setAntiAlias(true);

        hudBgPaint.setColor(0xBB0A0E1A);
        hudBgPaint.setStyle(Paint.Style.FILL);

        hudTextPaint.setColor(COLOR_CYAN);
        hudTextPaint.setTextSize(20.0f);
        hudTextPaint.setTypeface(Typeface.MONOSPACE);
        hudTextPaint.setFakeBoldText(true);
        hudTextPaint.setAntiAlias(true);

        skeletonPaint.setStyle(Paint.Style.STROKE);
        skeletonPaint.setColor(0xEEFFFFFF);
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
        this.aimbotTargetX = x;
        this.aimbotTargetY = y;
        postInvalidate();
    }

    public void setSimulationMode(boolean on)  { this.simulationMode = on; postInvalidate(); }
    public boolean isSimulationMode()         { return simulationMode; }

    public void setDrawBox(boolean on)        { this.drawBox = on; postInvalidate(); }
    public void setDrawSnapline(boolean on)   { this.drawSnapline = on; postInvalidate(); }
    public void setDrawHealth(boolean on)     { this.drawHealth = on; postInvalidate(); }
    public void setDrawDistance(boolean on)   { this.drawDistance = on; postInvalidate(); }
    public void setDrawName(boolean on)       { this.drawName = on; postInvalidate(); }
    public void setDrawSkeleton(boolean on)   { this.drawSkeleton = on; postInvalidate(); }
    public void setDrawFovCircle(boolean on)  { this.drawFovCircle = on; postInvalidate(); }
    public void setCornerBoxMode(boolean on)  { this.cornerBoxMode = on; postInvalidate(); }
    public void setDrawAimbotRay(boolean on)  { this.drawAimbotRay = on; postInvalidate(); }
    public void setAimAtNeck(boolean on)      { this.aimAtNeck = on; postInvalidate(); }
    public void setFovRadius(float radius)    { this.fovRadius = radius; postInvalidate(); }

    public boolean isDrawBox()       { return drawBox; }
    public boolean isDrawSnapline()  { return drawSnapline; }
    public boolean isDrawHealth()    { return drawHealth; }
    public boolean isDrawDistance()  { return drawDistance; }
    public boolean isDrawName()      { return drawName; }
    public boolean isDrawSkeleton()  { return drawSkeleton; }
    public boolean isDrawFovCircle() { return drawFovCircle; }
    public boolean isCornerBoxMode() { return cornerBoxMode; }
    public boolean isDrawAimbotRay() { return drawAimbotRay; }
    public boolean isAimAtNeck()     { return aimAtNeck; }
    public float   getFovRadius()    { return fovRadius; }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        final float screenW = getWidth();
        final float screenH = getHeight();
        if (screenW <= 0 || screenH <= 0) return;

        final float screenCX = screenW * 0.5f;
        final float screenCY = screenH * 0.5f;

        // ── 1. HUD TOP STATUS BADGE ──
        canvas.drawRect(20.0f, 15.0f, 440.0f, 55.0f, hudBgPaint);
        String statusText = isGameConnected
                ? "⚡ FF MASTER | LIVE HOOKED | 60 FPS"
                : "⚡ FF MASTER | PREVIEW DEMO | 60 FPS";
        hudTextPaint.setColor(isGameConnected ? COLOR_HP_GREEN : COLOR_CYAN);
        canvas.drawText(statusText, 30.0f, 42.0f, hudTextPaint);

        // ── 2. VẼ VÒNG TRÒN FOV AIMBOT Ở TÂM MÀN HÌNH ──
        if (drawFovCircle) {
            canvas.drawCircle(screenCX, screenCY, fovRadius, fovPaint);
        }

        final float[] currentPlayers = this.players;
        final boolean hasLivePlayers = (currentPlayers != null && currentPlayers.length >= STRIDE);

        // ── 3. VẼ PLAYERS (LIVE HOẶC MÔ PHỎNG TEST PREVIEW) ──
        if (hasLivePlayers) {
            renderPlayerList(canvas, currentPlayers, screenCX, screenH);
        } else if (simulationMode) {
            renderSimulatedTargets(canvas, screenCX, screenCY, screenH);
        }

        // ── 4. VẼ TIA KHÓA AIMBOT NẾU CÓ TARGET ──
        if (drawAimbotRay && aimbotTargetX > 0 && aimbotTargetY > 0) {
            canvas.drawLine(screenCX, screenCY, aimbotTargetX, aimbotTargetY, aimRayPaint);
            canvas.drawCircle(aimbotTargetX, aimbotTargetY, 14.0f, aimLockPaint);
            String lockTag = aimAtNeck ? "🎯 NECK (DRAG HEAD)" : "🎯 HEAD LOCKED";
            canvas.drawText(lockTag, aimbotTargetX - 65.0f, aimbotTargetY - 20.0f, textPaint);
        }
    }

    private void renderPlayerList(Canvas canvas, float[] pData, float screenCX, float screenH) {
        final int count = pData.length / STRIDE;
        for (int i = 0; i < count; ++i) {
            final int off = i * STRIDE;
            final float x    = pData[off];
            final float y    = pData[off + 1];
            final float w    = pData[off + 2];
            final float h    = pData[off + 3];
            final float hp   = pData[off + 4];
            final float dist = pData[off + 5];
            final boolean vis = pData[off + 6] > 0.5f;

            drawSingleEnemy(canvas, x, y, w, h, hp, dist, vis, screenCX, screenH, "Enemy");
        }
    }

    private void renderSimulatedTargets(Canvas canvas, float screenCX, float screenCY, float screenH) {
        // Hoạt ảnh nổi bồng bềnh nhẹ để chứng minh canvas đang render 60 FPS live
        float animOffset = (float) Math.sin(System.currentTimeMillis() * 0.003) * 10.0f;

        // Bot 1: Bên trái tâm màn hình, khoảng cách 38m
        float b1X = screenCX - 240.0f;
        float b1Y = screenCY + 110.0f + animOffset;
        drawSingleEnemy(canvas, b1X, b1Y, 80.0f, 175.0f, 100.0f, 38.0f, true, screenCX, screenH, "Bot 1 [Demo]");

        // Khóa Aimbot vào Bot 1 nếu đang bật tia ngắm
        if (drawAimbotRay && (aimbotTargetX < 0 || aimbotTargetY < 0)) {
            // aimAtNeck = true: Đặt tâm ở vị trí Cổ (18% từ đỉnh) để hỗ trợ vuốt tay kéo tâm lên đầu
            float targetY = aimAtNeck ? (b1Y - 175.0f + 175.0f * 0.18f) : (b1Y - 175.0f + 175.0f * 0.08f);
            String lockLabel = aimAtNeck ? "🎯 NECK (DRAG HEAD)" : "🎯 HEAD LOCK";

            canvas.drawLine(screenCX, screenCY, b1X, targetY, aimRayPaint);
            canvas.drawCircle(b1X, targetY, 12.0f, aimLockPaint);
            canvas.drawText(lockLabel, b1X - 70.0f, targetY - 16.0f, textPaint);
        }

        // Bot 2: Bên phải xa hơn, khoảng cách 72m
        float b2X = screenCX + 280.0f;
        float b2Y = screenCY + 50.0f - animOffset;
        drawSingleEnemy(canvas, b2X, b2Y, 62.0f, 135.0f, 48.0f, 72.0f, false, screenCX, screenH, "Bot 2 [Demo]");
    }

    private void drawSingleEnemy(Canvas canvas, float x, float y, float w, float h,
                                 float hp, float dist, boolean vis,
                                 float screenCX, float screenH, String label) {
        final int teamColor = vis ? COLOR_RED : COLOR_ORANGE;
        boxPaint.setColor(teamColor);
        snapPaint.setColor(teamColor);

        final float left   = x - (w * 0.5f);
        final float top    = y - h;
        final float right  = x + (w * 0.5f);
        final float bottom = y;

        // 1. ESP Box
        if (drawBox) {
            if (cornerBoxMode) {
                drawCornerBox(canvas, left, top, right, bottom, w, h);
            } else {
                canvas.drawRect(left, top, right, bottom, boxPaint);
            }
        }

        // 2. ESP Skeleton (Khung xương)
        if (drawSkeleton) {
            drawSkeletonLines(canvas, x, top, bottom, w, h);
        }

        // 3. Snapline từ đáy màn hình tới chân
        if (drawSnapline) {
            canvas.drawLine(screenCX, screenH, x, bottom, snapPaint);
        }

        // 4. Thanh Máu (HP Bar)
        if (drawHealth) {
            final float barW = 6.0f;
            final float barSpacing = 4.0f;
            final float barLeft = left - barW - barSpacing;
            final float barRight = left - barSpacing;

            final float clampedHp = Math.max(0.0f, Math.min(100.0f, hp));
            final float currentBarH = h * (clampedHp / 100.0f);

            canvas.drawRect(barLeft, top, barRight, bottom, hpBg);

            if (clampedHp > 60.0f) {
                hpPaint.setColor(COLOR_HP_GREEN);
            } else if (clampedHp > 30.0f) {
                hpPaint.setColor(COLOR_HP_YELLOW);
            } else {
                hpPaint.setColor(COLOR_HP_RED);
            }

            canvas.drawRect(barLeft, bottom - currentBarH, barRight, bottom, hpPaint);
        }

        // 5. Tên & Khoảng cách
        if (drawName || drawDistance) {
            StringBuilder sb = new StringBuilder();
            if (drawName) sb.append(label).append(" ");
            if (drawDistance) sb.append("[").append((int) dist).append("m]");
            canvas.drawText(sb.toString().trim(), left, top - 10.0f, textPaint);
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

        // Cổ và Xương sống
        canvas.drawLine(x, headY + headR, x, spineY, skeletonPaint);
        canvas.drawLine(x, spineY, x, pelvisY, skeletonPaint);

        // Vai và Cánh tay
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

    private void drawCornerBox(Canvas canvas, float l, float t, float r, float b, float w, float h) {
        float lineW = w * 0.25f;
        float lineH = h * 0.20f;

        // Top-Left
        canvas.drawLine(l, t, l + lineW, t, boxPaint);
        canvas.drawLine(l, t, l, t + lineH, boxPaint);

        // Top-Right
        canvas.drawLine(r, t, r - lineW, t, boxPaint);
        canvas.drawLine(r, t, r, t + lineH, boxPaint);

        // Bottom-Left
        canvas.drawLine(l, b, l + lineW, b, boxPaint);
        canvas.drawLine(l, b, l, b - lineH, boxPaint);

        // Bottom-Right
        canvas.drawLine(r, b, r - lineW, b, boxPaint);
        canvas.drawLine(r, b, r, b - lineH, boxPaint);
    }
}
