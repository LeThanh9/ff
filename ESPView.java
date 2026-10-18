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
 */
public class ESPView extends View {

    private volatile float[] players = new float[0];

    // Paints
    private final Paint boxPaint     = new Paint();
    private final Paint hpPaint      = new Paint();
    private final Paint hpBg         = new Paint();
    private final Paint textPaint    = new Paint();
    private final Paint snapPaint    = new Paint();
    private final Paint fovPaint     = new Paint();

    // Cấu hình hiển thị
    private boolean drawBox       = true;
    private boolean drawSnapline  = true;
    private boolean drawHealth    = true;
    private boolean drawDistance  = true;
    private boolean drawFovCircle = true;
    private boolean cornerBoxMode = true;
    private float   fovRadius     = 180.0f;

    // Màu sắc cố định (tránh parseColor trong loop)
    private static final int COLOR_RED        = 0xFFFF3344;
    private static final int COLOR_ORANGE     = 0xFFFF9900;
    private static final int COLOR_HP_GREEN   = 0xFF00E676;
    private static final int COLOR_HP_YELLOW  = 0xFFFFD600;
    private static final int COLOR_HP_RED     = 0xFFFF1744;
    private static final int COLOR_HP_BG      = 0xCC1A1A1A;

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
        textPaint.setTextSize(26.0f);
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
    }

    public void updatePlayers(float[] data) {
        if (data == null || data.length == 0) {
            this.players = new float[0];
        } else {
            this.players = data;
        }
        postInvalidate();
    }

    public void setOptions(boolean box, boolean snapline, boolean hp, boolean dist, boolean fov, boolean corner) {
        this.drawBox = box;
        this.drawSnapline = snapline;
        this.drawHealth = hp;
        this.drawDistance = dist;
        this.drawFovCircle = fov;
        this.cornerBoxMode = corner;
        postInvalidate();
    }

    public void setDrawBox(boolean on)        { this.drawBox = on; postInvalidate(); }
    public void setDrawSnapline(boolean on)   { this.drawSnapline = on; postInvalidate(); }
    public void setDrawHealth(boolean on)     { this.drawHealth = on; postInvalidate(); }
    public void setDrawDistance(boolean on)   { this.drawDistance = on; postInvalidate(); }
    public void setDrawFovCircle(boolean on)  { this.drawFovCircle = on; postInvalidate(); }
    public void setCornerBoxMode(boolean on)  { this.cornerBoxMode = on; postInvalidate(); }
    public void setFovRadius(float radius)    { this.fovRadius = radius; postInvalidate(); }

    public boolean isDrawBox()       { return drawBox; }
    public boolean isDrawSnapline()  { return drawSnapline; }
    public boolean isDrawHealth()    { return drawHealth; }
    public boolean isDrawDistance()  { return drawDistance; }
    public boolean isDrawFovCircle() { return drawFovCircle; }
    public boolean isCornerBoxMode() { return cornerBoxMode; }
    public float   getFovRadius()    { return fovRadius; }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        final float screenW = getWidth();
        final float screenH = getHeight();
        final float screenCX = screenW * 0.5f;
        final float screenCY = screenH * 0.5f;

        // Vẽ vòng tròn FOV Aimbot ở tâm màn hình theo bán kính động
        if (drawFovCircle) {
            canvas.drawCircle(screenCX, screenCY, fovRadius, fovPaint);
        }

        final float[] currentPlayers = this.players;
        if (currentPlayers == null || currentPlayers.length < STRIDE) return;

        final int count = currentPlayers.length / STRIDE;

        for (int i = 0; i < count; ++i) {
            final int off = i * STRIDE;

            final float x    = currentPlayers[off];
            final float y    = currentPlayers[off + 1];
            final float w    = currentPlayers[off + 2];
            final float h    = currentPlayers[off + 3];
            final float hp   = currentPlayers[off + 4];
            final float dist = currentPlayers[off + 5];
            final boolean vis = currentPlayers[off + 6] > 0.5f;

            final int teamColor = vis ? COLOR_RED : COLOR_ORANGE;
            boxPaint.setColor(teamColor);
            snapPaint.setColor(teamColor);

            final float left   = x - (w * 0.5f);
            final float top    = y - h;
            final float right  = x + (w * 0.5f);
            final float bottom = y;

            // 1. ESP Box (Corner Box hoặc Full Box)
            if (drawBox) {
                if (cornerBoxMode) {
                    drawCornerBox(canvas, left, top, right, bottom, w, h);
                } else {
                    canvas.drawRect(left, top, right, bottom, boxPaint);
                }
            }

            // 2. Snapline: Kéo từ đáy màn hình tới chân kẻ địch (không che tâm ngắm)
            if (drawSnapline) {
                canvas.drawLine(screenCX, screenH, x, bottom, snapPaint);
            }

            // 3. Thanh Máu (Health bar bên trái khung)
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

            // 4. Khoảng cách & HP hiển thị
            if (drawDistance) {
                int distInt = (int) dist;
                canvas.drawText(distInt + "m", left, top - 10.0f, textPaint);
            }
        }
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
