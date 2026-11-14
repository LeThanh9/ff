package com.ff.cheat;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.View;

/**
 * ESPView — Bộ kết xuất Lớp phủ ESP & Kéo tâm (Drag Headshot) hiệu năng cao
 * Tối ưu hóa 60 FPS, 100% tiếng Việt, vẽ đầy đủ Khung xương 3D, Hộp bao, Thanh máu,
 * Khoảng cách, Tâm định vị cổ (Neck Crosshair) và Vòng quét FOV.
 */
public class ESPView extends View {

    private volatile float[] players = new float[0];

    // Cọ vẽ (Paints)
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

    // Cấu hình tính năng (Mặc định bật đầy đủ)
    private boolean drawBox           = true;
    private boolean drawSkeleton      = true;
    private boolean drawSnapline      = true;
    private boolean drawHealthBar     = true;
    private boolean drawNeckCrosshair = true;
    private boolean drawFovCircle     = true;
    private boolean autoNeckAim       = true; // Tự động khóa cổ khi mục tiêu vào vòng FOV
    private boolean simulationMode    = true; // Chế độ mô phỏng xem trước khi chưa vào game
    private boolean isGameConnected   = false;
    private float   fovRadius         = 180.0f; // Bán kính vòng FOV

    // Bảng màu chuẩn hiển thị chiến trường
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

        // Khung hộp 2D
        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(2.5f);
        boxPaint.setColor(0xCCFF2244);
        boxPaint.setAntiAlias(true);

        cornerPaint.setStyle(Paint.Style.STROKE);
        cornerPaint.setStrokeWidth(3.5f);
        cornerPaint.setColor(0xFFFF2244);
        cornerPaint.setAntiAlias(true);

        // Chữ thông tin (Tên, Mét, Máu)
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(20.0f);
        textPaint.setTypeface(Typeface.MONOSPACE);
        textPaint.setFakeBoldText(true);
        textPaint.setShadowLayer(3.0f, 1.0f, 1.0f, Color.BLACK);
        textPaint.setAntiAlias(true);

        // Vòng FOV quét mục tiêu
        fovPaint.setStyle(Paint.Style.STROKE);
        fovPaint.setColor(0x6600E5FF);
        fovPaint.setStrokeWidth(2.5f);
        fovPaint.setAntiAlias(true);

        centerCrossPaint.setColor(0x8800E5FF);
        centerCrossPaint.setStrokeWidth(1.5f);
        centerCrossPaint.setAntiAlias(true);

        // Đường dẫn hướng Snapline từ đáy màn hình
        snaplinePaint.setStyle(Paint.Style.STROKE);
        snaplinePaint.setColor(COLOR_SNAPLINE);
        snaplinePaint.setStrokeWidth(1.8f);
        snaplinePaint.setAntiAlias(true);

        // Tia đỏ ngắm bắn & Vòng khóa Aimbot
        aimRayPaint.setStyle(Paint.Style.STROKE);
        aimRayPaint.setColor(COLOR_AIM_RAY);
        aimRayPaint.setStrokeWidth(2.5f);
        aimRayPaint.setAntiAlias(true);

        aimLockPaint.setStyle(Paint.Style.STROKE);
        aimLockPaint.setColor(COLOR_LOCK_RING);
        aimLockPaint.setStrokeWidth(3.0f);
        aimLockPaint.setAntiAlias(true);

        // Khung xương & Khớp nối 3D
        skeletonPaint.setStyle(Paint.Style.STROKE);
        skeletonPaint.setColor(COLOR_SKELETON);
        skeletonPaint.setStrokeWidth(2.2f);
        skeletonPaint.setAntiAlias(true);

        jointPaint.setStyle(Paint.Style.FILL);
        jointPaint.setColor(COLOR_JOINT);
        jointPaint.setAntiAlias(true);

        // Tâm định vị đốt cổ (Hỗ trợ kéo tâm vuốt tay)
        neckCrossPaint.setStyle(Paint.Style.STROKE);
        neckCrossPaint.setColor(0xFFFF1744);
        neckCrossPaint.setStrokeWidth(2.0f);
        neckCrossPaint.setAntiAlias(true);

        // Thanh máu HP Bar
        hpBgPaint.setStyle(Paint.Style.FILL);
        hpBgPaint.setColor(0x99000000);

        hpBarPaint.setStyle(Paint.Style.FILL);
        hpBarPaint.setColor(COLOR_HP_GREEN);
        hpBarPaint.setAntiAlias(true);

        // Bảng trạng thái HUD
        hudBgPaint.setColor(0xCC0A0E1A);
        hudBgPaint.setStyle(Paint.Style.FILL);

        hudTextPaint.setColor(COLOR_CYAN);
        hudTextPaint.setTextSize(18.0f);
        hudTextPaint.setTypeface(Typeface.MONOSPACE);
        hudTextPaint.setFakeBoldText(true);
        hudTextPaint.setAntiAlias(true);
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

    public void setFovRadius(float radius)      { this.fovRadius = radius; postInvalidate(); }
    public float getFovRadius()                 { return fovRadius; }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        final float screenW = getWidth();
        final float screenH = getHeight();
        if (screenW <= 0 || screenH <= 0) return;

        final float screenCX = screenW * 0.5f;
        final float screenCY = screenH * 0.5f;

        // ── 1. HUD BẢNG THÔNG TIN TRẠNG THÁI GÓC TRÊN ──
        canvas.drawRect(20.0f, 15.0f, 520.0f, 58.0f, hudBgPaint);
        String statusText = isGameConnected
                ? "⚡ TRỰC TIẾP: LIÊN KẾT BỘ NHỚ | KÉO TÂM CỔ"
                : "⚡ CHẾ ĐỘ MÔ PHỎNG | BÁN KÍNH FOV: " + (int)fovRadius + " PX";
        hudTextPaint.setColor(isGameConnected ? COLOR_HP_GREEN : COLOR_CYAN);
        canvas.drawText(statusText, 30.0f, 43.0f, hudTextPaint);

        // ── 2. VÒNG QUÉT FOV TẠI TÂM MÀN HÌNH ──
        if (drawFovCircle) {
            canvas.drawCircle(screenCX, screenCY, fovRadius, fovPaint);
            // Dấu cộng định tâm
            canvas.drawLine(screenCX - 12, screenCY, screenCX + 12, screenCY, centerCrossPaint);
            canvas.drawLine(screenCX, screenCY - 12, screenCX, screenCY + 12, centerCrossPaint);
        }

        final float[] currentPlayers = this.players;
        final boolean hasLivePlayers = (currentPlayers != null && currentPlayers.length >= STRIDE);

        float bestTargetX = -1.0f;
        float bestTargetY = -1.0f;
        float bestDistFromCenter = Float.MAX_VALUE;

        // ── 3. HIỂN THỊ CHI TIẾT NGƯỜI CHƠI TRÊN CHIẾN TRƯỜNG ──
        if (hasLivePlayers) {
            final int count = currentPlayers.length / STRIDE;
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

                // Đường Snapline dẫn hướng từ đáy màn hình
                if (drawSnapline) {
                    canvas.drawLine(screenCX, screenH, x, bottom, snaplinePaint);
                }

                // Khung Hộp
                if (drawBox) {
                    drawCornerBox(canvas, x, top, bottom, w);
                }

                // Khung Xương Đầy Đủ
                if (drawSkeleton) {
                    drawFullSkeleton(canvas, x, top, bottom, w, h);
                }

                // Tọa độ đốt sống cổ: 18% từ đỉnh đầu xuống
                final float neckX = x;
                final float neckY = top + (h * 0.18f);

                // Tâm Định Vị Cổ (Hỗ trợ kéo tâm vuốt tay)
                if (drawNeckCrosshair) {
                    drawNeckCrosshairPoint(canvas, neckX, neckY);
                }

                // Thanh Máu & Thông Tin Khoảng Cách
                if (drawHealthBar) {
                    drawHpAndTag(canvas, x, top, bottom, w, hp, dist, "ĐỊCH");
                }

                // Kiểm tra mục tiêu nằm trong vòng FOV để tự động khóa
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
            // Chế độ mô phỏng trực quan giúp kiểm tra và cân chỉnh vòng ngắm
            float animOffset = (float) Math.sin(System.currentTimeMillis() * 0.003) * 8.0f;

            // Mục tiêu 1 (Cự ly gần: ~28m)
            float b1X = screenCX - 160.0f;
            float b1Y = screenCY + 110.0f + animOffset;
            float b1W = 85.0f;
            float b1H = 180.0f;
            float b1Top = b1Y - b1H;

            if (drawSnapline) canvas.drawLine(screenCX, screenH, b1X, b1Y, snaplinePaint);
            if (drawBox) drawCornerBox(canvas, b1X, b1Top, b1Y, b1W);
            if (drawSkeleton) drawFullSkeleton(canvas, b1X, b1Top, b1Y, b1W, b1H);
            if (drawHealthBar) drawHpAndTag(canvas, b1X, b1Top, b1Y, b1W, 85.0f, 28.0f, "MỤC TIÊU 1");

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

            // Mục tiêu 2 (Cự ly xa hơn bên phải: ~65m)
            float b2X = screenCX + 290.0f;
            float b2Y = screenCY + 65.0f - animOffset;
            float b2W = 65.0f;
            float b2H = 140.0f;
            float b2Top = b2Y - b2H;

            if (drawSnapline) canvas.drawLine(screenCX, screenH, b2X, b2Y, snaplinePaint);
            if (drawBox) drawCornerBox(canvas, b2X, b2Top, b2Y, b2W);
            if (drawSkeleton) drawFullSkeleton(canvas, b2X, b2Top, b2Y, b2W, b2H);
            if (drawHealthBar) drawHpAndTag(canvas, b2X, b2Top, b2Y, b2W, 40.0f, 65.0f, "MỤC TIÊU 2");

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

        // ── 4. TỰ ĐỘNG KHÓA VÀ VUỐT TÂM VÀO ĐỐT CỔ (DRAG HEADSHOT) ──
        if (autoNeckAim && bestTargetX > 0 && bestTargetY > 0) {
            // Tia đỏ chỉ thị khóa mục tiêu
            canvas.drawLine(screenCX, screenCY, bestTargetX, bestTargetY, aimRayPaint);
            canvas.drawCircle(bestTargetX, bestTargetY, 15.0f, aimLockPaint);
            canvas.drawText("🎯 KHÓA CỔ (KÉO TÂM)", bestTargetX - 85.0f, bestTargetY - 22.0f, textPaint);

            // Kích hoạt vuốt điều hướng mượt qua RootUtils
            if (RootUtils.isAutoAimDragEnabled()) {
                RootUtils.triggerAimDrag(screenCX, screenCY, bestTargetX, bestTargetY);
            }
        }
    }

    /**
     * Vẽ Khung Hộp Góc (Corner Box) thẩm mỹ cao, không che khuất tầm nhìn
     */
    private void drawCornerBox(Canvas canvas, float x, float top, float bottom, float w) {
        float halfW = w * 0.5f;
        float left = x - halfW;
        float right = x + halfW;
        float lineLen = Math.min(w * 0.25f, 25.0f);

        // Hộp mờ bên trong
        canvas.drawRect(left, top, right, bottom, boxPaint);

        // Góc trên-trái
        canvas.drawLine(left, top, left + lineLen, top, cornerPaint);
        canvas.drawLine(left, top, left, top + lineLen, cornerPaint);
        // Góc trên-phải
        canvas.drawLine(right, top, right - lineLen, top, cornerPaint);
        canvas.drawLine(right, top, right, top + lineLen, cornerPaint);
        // Góc dưới-trái
        canvas.drawLine(left, bottom, left + lineLen, bottom, cornerPaint);
        canvas.drawLine(left, bottom, left, bottom - lineLen, cornerPaint);
        // Góc dưới-phải
        canvas.drawLine(right, bottom, right - lineLen, bottom, cornerPaint);
        canvas.drawLine(right, bottom, right, bottom - lineLen, cornerPaint);
    }

    /**
     * Vẽ Khung Xương Đầy Đủ (Full Humanoid Skeleton) kèm các khớp tròn
     */
    private void drawFullSkeleton(Canvas canvas, float x, float top, float bottom, float w, float h) {
        float headR    = h * 0.08f;
        float headY    = top + headR;
        float neckY    = top + h * 0.18f;
        float spineY   = top + h * 0.45f;
        float pelvisY  = top + h * 0.55f;

        // 1. Đầu
        canvas.drawCircle(x, headY, headR, skeletonPaint);

        // 2. Cột sống & Khung thân
        canvas.drawLine(x, headY + headR, x, spineY, skeletonPaint);
        canvas.drawLine(x, spineY, x, pelvisY, skeletonPaint);

        // 3. Xương quai xanh & Vai
        float shoulderW = w * 0.35f;
        float elbowY    = top + h * 0.35f;
        float handY     = top + h * 0.52f;

        // Khớp vai
        canvas.drawCircle(x - shoulderW, neckY + h * 0.03f, 3.5f, jointPaint);
        canvas.drawCircle(x + shoulderW, neckY + h * 0.03f, 3.5f, jointPaint);

        // Tay trái
        canvas.drawLine(x, neckY, x - shoulderW, neckY + h * 0.03f, skeletonPaint);
        canvas.drawLine(x - shoulderW, neckY + h * 0.03f, x - shoulderW - w * 0.1f, elbowY, skeletonPaint);
        canvas.drawCircle(x - shoulderW - w * 0.1f, elbowY, 3.0f, jointPaint); // Khuỷu trái
        canvas.drawLine(x - shoulderW - w * 0.1f, elbowY, x - shoulderW - w * 0.14f, handY, skeletonPaint);
        canvas.drawCircle(x - shoulderW - w * 0.14f, handY, 2.5f, jointPaint); // Bàn tay trái

        // Tay phải
        canvas.drawLine(x, neckY, x + shoulderW, neckY + h * 0.03f, skeletonPaint);
        canvas.drawLine(x + shoulderW, neckY + h * 0.03f, x + shoulderW + w * 0.1f, elbowY, skeletonPaint);
        canvas.drawCircle(x + shoulderW + w * 0.1f, elbowY, 3.0f, jointPaint); // Khuỷu phải
        canvas.drawLine(x + shoulderW + w * 0.1f, elbowY, x + shoulderW + w * 0.14f, handY, skeletonPaint);
        canvas.drawCircle(x + shoulderW + w * 0.14f, handY, 2.5f, jointPaint); // Bàn tay phải

        // 4. Khung chậu & Chân
        float kneeY = top + h * 0.77f;
        float legW  = w * 0.28f;

        // Khớp hông
        canvas.drawCircle(x - legW * 0.4f, pelvisY, 3.5f, jointPaint);
        canvas.drawCircle(x + legW * 0.4f, pelvisY, 3.5f, jointPaint);

        // Chân trái
        canvas.drawLine(x, pelvisY, x - legW * 0.6f, kneeY, skeletonPaint);
        canvas.drawCircle(x - legW * 0.6f, kneeY, 3.5f, jointPaint); // Khớp gối trái
        canvas.drawLine(x - legW * 0.6f, kneeY, x - legW, bottom, skeletonPaint);
        canvas.drawCircle(x - legW, bottom, 3.0f, jointPaint); // Bàn chân trái

        // Chân phải
        canvas.drawLine(x, pelvisY, x + legW * 0.6f, kneeY, skeletonPaint);
        canvas.drawCircle(x + legW * 0.6f, kneeY, 3.5f, jointPaint); // Khớp gối phải
        canvas.drawLine(x + legW * 0.6f, kneeY, x + legW, bottom, skeletonPaint);
        canvas.drawCircle(x + legW, bottom, 3.0f, jointPaint); // Bàn chân phải
    }

    /**
     * Vẽ Tâm Định Vị Cổ: Điểm đặt tâm lý tưởng hỗ trợ thao tác kéo tâm vuốt tay
     */
    private void drawNeckCrosshairPoint(Canvas canvas, float nx, float ny) {
        canvas.drawCircle(nx, ny, 7.0f, neckCrossPaint);
        canvas.drawLine(nx - 14.0f, ny, nx - 6.0f, ny, neckCrossPaint);
        canvas.drawLine(nx + 6.0f, ny, nx + 14.0f, ny, neckCrossPaint);
        canvas.drawLine(nx, ny - 14.0f, nx, ny - 6.0f, neckCrossPaint);
        canvas.drawLine(nx, ny + 6.0f, nx, ny + 14.0f, neckCrossPaint);
    }

    /**
     * Vẽ Thanh Máu Động và Nhãn Khoảng Cách Mét
     */
    private void drawHpAndTag(Canvas canvas, float x, float top, float bottom, float w, float hp, float dist, String label) {
        float halfW = w * 0.5f;
        float barLeft = x - halfW - 10.0f;
        float barWidth = 5.0f;
        float totalH = bottom - top;

        // Cột máu cạnh trái
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

        // Nhãn thông tin trên đỉnh đầu
        String tag = label + " [" + (int)dist + "M] " + (int)clampedHp + "%";
        float textW = textPaint.measureText(tag);
        canvas.drawText(tag, x - (textW * 0.5f), top - 10.0f, textPaint);
    }
}
