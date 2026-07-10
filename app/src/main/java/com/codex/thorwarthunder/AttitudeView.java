package com.codex.thorwarthunder;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

final class AttitudeView extends View {
    private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint skyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint groundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint markPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path wingPath = new Path();
    private final Path clipPath = new Path();
    private final RectF dialRect = new RectF();

    private double pitchDeg;
    private double rollDeg;
    private boolean valid;

    AttitudeView(Context context) {
        super(context);
        backgroundPaint.setColor(Color.rgb(24, 29, 34));
        backgroundPaint.setStyle(Paint.Style.FILL);

        skyPaint.setColor(Color.rgb(38, 86, 116));
        skyPaint.setStyle(Paint.Style.FILL);

        groundPaint.setColor(Color.rgb(110, 75, 45));
        groundPaint.setStyle(Paint.Style.FILL);

        linePaint.setColor(Color.WHITE);
        linePaint.setStrokeWidth(dp(2));
        linePaint.setStyle(Paint.Style.STROKE);

        markPaint.setColor(Color.rgb(240, 210, 80));
        markPaint.setStrokeWidth(dp(3));
        markPaint.setStyle(Paint.Style.STROKE);
        markPaint.setStrokeCap(Paint.Cap.ROUND);

        textPaint.setColor(Color.rgb(180, 190, 200));
        textPaint.setTextSize(dp(11));
        textPaint.setTextAlign(Paint.Align.CENTER);
    }

    void setAttitude(FlightStatus status) {
        valid = status.hasPitch || status.hasRoll;
        pitchDeg = status.hasPitch ? status.pitchDeg : 0.0;
        rollDeg = status.hasRoll ? status.rollDeg : 0.0;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(Color.rgb(16, 20, 24));

        float w = getWidth();
        float h = getHeight();
        float cx = w * 0.5f;
        float cy = h * 0.5f;
        float r = Math.min(w, h) * 0.43f;

        dialRect.set(cx - r, cy - r, cx + r, cy + r);
        canvas.drawRoundRect(0, 0, w, h, dp(4), dp(4), backgroundPaint);

        canvas.save();
        clipPath.reset();
        clipPath.addOval(dialRect, Path.Direction.CW);
        canvas.clipPath(clipPath);
        canvas.translate(cx, cy);
        canvas.rotate((float) -rollDeg);
        float pitchOffset = (float) clamp(pitchDeg * r / 35.0, -r * 0.75, r * 0.75);

        canvas.drawRect(-r * 2.0f, -r * 2.0f + pitchOffset, r * 2.0f, pitchOffset, skyPaint);
        canvas.drawRect(-r * 2.0f, pitchOffset, r * 2.0f, r * 2.0f + pitchOffset, groundPaint);
        canvas.drawLine(-r * 1.5f, pitchOffset, r * 1.5f, pitchOffset, linePaint);

        for (int deg = -30; deg <= 30; deg += 10) {
            if (deg == 0) {
                continue;
            }
            float y = pitchOffset - deg * r / 35.0f;
            float half = Math.abs(deg) == 30 ? r * 0.28f : r * 0.20f;
            canvas.drawLine(-half, y, half, y, linePaint);
        }
        canvas.restore();

        canvas.drawOval(dialRect, linePaint);
        drawRollMarks(canvas, cx, cy, r);
        drawAircraftReference(canvas, cx, cy, r);

        String label = valid
                ? String.format(java.util.Locale.US, "P %+2.0f  R %+2.0f", pitchDeg, rollDeg)
                : "P --  R --";
        canvas.drawText(label, cx, h - dp(10), textPaint);
    }

    private void drawRollMarks(Canvas canvas, float cx, float cy, float r) {
        int[] marks = {-60, -45, -30, -20, -10, 0, 10, 20, 30, 45, 60};
        for (int mark : marks) {
            double rad = Math.toRadians(mark - 90);
            float outerX = cx + (float) Math.cos(rad) * r;
            float outerY = cy + (float) Math.sin(rad) * r;
            float inner = mark == 0 ? r - dp(12) : r - dp(7);
            float innerX = cx + (float) Math.cos(rad) * inner;
            float innerY = cy + (float) Math.sin(rad) * inner;
            canvas.drawLine(innerX, innerY, outerX, outerY, linePaint);
        }
    }

    private void drawAircraftReference(Canvas canvas, float cx, float cy, float r) {
        wingPath.reset();
        wingPath.moveTo(cx - r * 0.50f, cy);
        wingPath.lineTo(cx - r * 0.15f, cy);
        wingPath.moveTo(cx + r * 0.15f, cy);
        wingPath.lineTo(cx + r * 0.50f, cy);
        wingPath.moveTo(cx, cy - dp(7));
        wingPath.lineTo(cx, cy + dp(7));
        canvas.drawPath(wingPath, markPaint);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
