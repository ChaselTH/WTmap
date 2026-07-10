package com.codex.thorwarthunder;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

final class ThorMapView extends View {
    private final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    private final Paint textFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint playerFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint playerStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint groundFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint groundStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path playerPath = new Path();
    private final RectF groundRect = new RectF();

    private final ScaleGestureDetector scaleDetector;
    private final GestureDetector gestureDetector;

    private Bitmap mapBitmap;
    private Typeface iconTypeface;
    private List<MapObject> objects = new ArrayList<>();
    private JSONObject mapInfo;

    private float userScale = 1.0f;
    private float panX = 0.0f;
    private float panY = 0.0f;
    private float baseLeft = 0.0f;
    private float baseTop = 0.0f;
    private float baseWidth = 1.0f;
    private float baseHeight = 1.0f;
    private boolean localMapMode = false;

    ThorMapView(Context context) {
        super(context);
        setBackgroundColor(Color.rgb(8, 10, 12));

        textFillPaint.setColor(Color.WHITE);
        textFillPaint.setTextAlign(Paint.Align.CENTER);
        textFillPaint.setTextSize(dp(24));
        textFillPaint.setFakeBoldText(true);

        textStrokePaint.setColor(Color.BLACK);
        textStrokePaint.setTextAlign(Paint.Align.CENTER);
        textStrokePaint.setTextSize(dp(24));
        textStrokePaint.setStyle(Paint.Style.STROKE);
        textStrokePaint.setStrokeWidth(Math.max(1.0f, dp(1)));
        textStrokePaint.setFakeBoldText(true);

        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setStrokeWidth(dp(3));

        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(1.0f);
        gridPaint.setColor(Color.argb(120, 30, 36, 42));

        gridTextPaint.setColor(Color.argb(180, 20, 24, 28));
        gridTextPaint.setTextSize(dp(10));

        playerFillPaint.setColor(Color.WHITE);
        playerFillPaint.setStyle(Paint.Style.FILL);

        playerStrokePaint.setColor(Color.rgb(45, 48, 52));
        playerStrokePaint.setStyle(Paint.Style.STROKE);
        playerStrokePaint.setStrokeWidth(dp(2));
        playerStrokePaint.setStrokeJoin(Paint.Join.ROUND);

        groundFillPaint.setStyle(Paint.Style.FILL);

        groundStrokePaint.setColor(Color.rgb(20, 22, 24));
        groundStrokePaint.setStyle(Paint.Style.STROKE);
        groundStrokePaint.setStrokeWidth(dp(2));
        groundStrokePaint.setStrokeJoin(Paint.Join.ROUND);

        scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                float oldScale = userScale;
                float nextScale = clamp(oldScale * detector.getScaleFactor(), 0.75f, 8.0f);
                float factor = nextScale / oldScale;
                float focusX = detector.getFocusX();
                float focusY = detector.getFocusY();
                panX = focusX - baseLeft - (focusX - baseLeft - panX) * factor;
                panY = focusY - baseTop - (focusY - baseTop - panY) * factor;
                userScale = nextScale;
                clampPan();
                invalidate();
                return true;
            }
        });

        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                panX -= distanceX;
                panY -= distanceY;
                clampPan();
                invalidate();
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                if (userScale > 1.05f) {
                    userScale = 1.0f;
                    panX = 0.0f;
                    panY = 0.0f;
                } else {
                    float oldScale = userScale;
                    userScale = 2.0f;
                    float factor = userScale / oldScale;
                    float focusX = e.getX();
                    float focusY = e.getY();
                    panX = focusX - baseLeft - (focusX - baseLeft - panX) * factor;
                    panY = focusY - baseTop - (focusY - baseTop - panY) * factor;
                }
                clampPan();
                invalidate();
                return true;
            }
        });
    }

    Bitmap getMapBitmap() {
        return mapBitmap;
    }

    boolean hasIconTypeface() {
        return iconTypeface != null;
    }

    void setIconTypeface(Typeface typeface) {
        iconTypeface = typeface;
        textFillPaint.setTypeface(typeface);
        textStrokePaint.setTypeface(typeface);
        invalidate();
    }

    void setMapBitmap(Bitmap bitmap) {
        mapBitmap = bitmap;
        requestLayout();
        invalidate();
    }

    void setObjects(List<MapObject> nextObjects) {
        objects = nextObjects == null ? new ArrayList<>() : nextObjects;
        if (localMapMode) {
            centerLocalMapOnPlayer();
        }
        invalidate();
    }

    void setMapInfo(JSONObject info) {
        mapInfo = info;
        invalidate();
    }

    void resetView() {
        userScale = 1.0f;
        panX = 0.0f;
        panY = 0.0f;
        objects = new ArrayList<>();
        mapInfo = null;
        mapBitmap = null;
        invalidate();
    }

    boolean isLocalMapMode() {
        return localMapMode;
    }

    void setLocalMapMode(boolean enabled) {
        localMapMode = enabled;
        if (enabled) {
            userScale = Math.max(userScale, 3.0f);
            centerLocalMapOnPlayer();
        } else {
            userScale = 1.0f;
            panX = 0.0f;
            panY = 0.0f;
            clampPan();
        }
        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        scaleDetector.onTouchEvent(event);
        gestureDetector.onTouchEvent(event);
        return true;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        updateBaseBounds();
        clampPan();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        updateBaseBounds();
        if (localMapMode) {
            centerLocalMapOnPlayer();
        }

        RectF content = contentRect();
        if (mapBitmap != null) {
            canvas.drawBitmap(mapBitmap, null, content, bitmapPaint);
        } else {
            Paint placeholder = new Paint();
            placeholder.setColor(Color.rgb(12, 16, 20));
            canvas.drawRect(content, placeholder);
        }

        drawGrid(canvas, content);

        MapObject player = null;
        boolean hasBlink = false;
        for (MapObject object : objects) {
            if (object.blink != 0) {
                hasBlink = true;
            }
            if ("airfield".equals(object.type) && object.hasLine) {
                drawAirfield(canvas, content, object);
            } else if ("Player".equals(object.icon)) {
                player = object;
            } else {
                drawMapObject(canvas, content, object);
            }
        }
        if (player != null) {
            drawPlayer(canvas, content, player);
        }
        if (hasBlink) {
            postInvalidateDelayed(50);
        }
    }

    private void updateBaseBounds() {
        int viewWidth = Math.max(1, getWidth());
        int viewHeight = Math.max(1, getHeight());
        int bitmapWidth = mapBitmap == null ? 1 : Math.max(1, mapBitmap.getWidth());
        int bitmapHeight = mapBitmap == null ? 1 : Math.max(1, mapBitmap.getHeight());
        float fitScale = Math.min(viewWidth / (float) bitmapWidth, viewHeight / (float) bitmapHeight);
        baseWidth = bitmapWidth * fitScale;
        baseHeight = bitmapHeight * fitScale;
        baseLeft = (viewWidth - baseWidth) * 0.5f;
        baseTop = (viewHeight - baseHeight) * 0.5f;
    }

    private RectF contentRect() {
        float width = baseWidth * userScale;
        float height = baseHeight * userScale;
        return new RectF(baseLeft + panX, baseTop + panY, baseLeft + panX + width, baseTop + panY + height);
    }

    private void clampPan() {
        int viewWidth = Math.max(1, getWidth());
        int viewHeight = Math.max(1, getHeight());
        float width = baseWidth * userScale;
        float height = baseHeight * userScale;

        if (width <= viewWidth) {
            panX = (viewWidth - width) * 0.5f - baseLeft;
        } else {
            float minPan = viewWidth - width - baseLeft;
            float maxPan = -baseLeft;
            panX = clamp(panX, minPan, maxPan);
        }

        if (height <= viewHeight) {
            panY = (viewHeight - height) * 0.5f - baseTop;
        } else {
            float minPan = viewHeight - height - baseTop;
            float maxPan = -baseTop;
            panY = clamp(panY, minPan, maxPan);
        }
    }

    private void centerLocalMapOnPlayer() {
        updateBaseBounds();
        MapObject player = findPlayer();
        if (player == null || !player.hasPoint) {
            clampPan();
            return;
        }
        userScale = clamp(userScale, 2.0f, 8.0f);
        float width = baseWidth * userScale;
        float height = baseHeight * userScale;
        panX = getWidth() * 0.5f - baseLeft - player.x * width;
        panY = getHeight() * 0.5f - baseTop - player.y * height;
        clampPan();
    }

    private MapObject findPlayer() {
        for (MapObject object : objects) {
            if ("Player".equals(object.icon)) {
                return object;
            }
        }
        return null;
    }

    private void drawGrid(Canvas canvas, RectF content) {
        if (mapInfo == null) {
            return;
        }
        JSONArray mapMin = mapInfo.optJSONArray("map_min");
        JSONArray mapMax = mapInfo.optJSONArray("map_max");
        JSONArray gridSteps = mapInfo.optJSONArray("grid_steps");
        if (mapMin == null || mapMax == null || gridSteps == null) {
            return;
        }

        double minX = mapMin.optDouble(0, -32768);
        double minY = mapMin.optDouble(1, -32768);
        double maxX = mapMax.optDouble(0, 32768);
        double maxY = mapMax.optDouble(1, 32768);
        double stepX = Math.max(1.0, gridSteps.optDouble(0, 6500));
        double stepY = Math.max(1.0, gridSteps.optDouble(1, 6500));

        for (double x = minX; x <= maxX; x += stepX) {
            float nx = (float) ((x - minX) / (maxX - minX));
            float sx = content.left + nx * content.width();
            canvas.drawLine(sx, content.top, sx, content.bottom, gridPaint);
        }
        for (double y = minY; y <= maxY; y += stepY) {
            float ny = (float) ((y - minY) / (maxY - minY));
            float sy = content.top + ny * content.height();
            canvas.drawLine(content.left, sy, content.right, sy, gridPaint);
        }
    }

    private void drawAirfield(Canvas canvas, RectF content, MapObject object) {
        linePaint.setColor(resolveColor(object));
        linePaint.setStrokeWidth(dp(3) * (float) Math.sqrt(userScale));
        canvas.drawLine(
                content.left + object.sx * content.width(),
                content.top + object.sy * content.height(),
                content.left + object.ex * content.width(),
                content.top + object.ey * content.height(),
                linePaint
        );
    }

    private void drawMapObject(Canvas canvas, RectF content, MapObject object) {
        if (!object.hasPoint || object.icon == null || object.icon.length() == 0 || "none".equals(object.icon)) {
            return;
        }

        float x = content.left + object.x * content.width();
        float y = content.top + object.y * content.height();
        if (object.isGroundUnit()) {
            drawGroundUnit(canvas, object, x, y);
            return;
        }

        String glyph = glyphFor(object.icon);
        textFillPaint.setColor(resolveColor(object));
        float textSize = dp(24);
        textFillPaint.setTextSize(textSize);
        textStrokePaint.setTextSize(textSize);

        boolean rotate = "respawn_base_fighter".equals(object.type) || "respawn_base_bomber".equals(object.type);
        float baseline = y - (textFillPaint.ascent() + textFillPaint.descent()) * 0.5f;
        if (rotate) {
            float heading = (float) Math.atan2(object.dx, -object.dy);
            canvas.save();
            canvas.rotate((float) Math.toDegrees(heading), x, y);
            canvas.drawText(glyph, x, baseline, textStrokePaint);
            canvas.drawText(glyph, x, baseline, textFillPaint);
            canvas.restore();
        } else {
            canvas.drawText(glyph, x, baseline, textStrokePaint);
            canvas.drawText(glyph, x, baseline, textFillPaint);
        }
    }

    private void drawGroundUnit(Canvas canvas, MapObject object, float x, float y) {
        float width = dp(10);
        float height = dp(5);
        groundRect.set(x - width * 0.5f, y - height * 0.5f, x + width * 0.5f, y + height * 0.5f);
        groundFillPaint.setColor(resolveColor(object));
        float radius = dp(1);
        canvas.drawRoundRect(groundRect, radius, radius, groundFillPaint);
        canvas.drawRoundRect(groundRect, radius, radius, groundStrokePaint);
    }

    private void drawPlayer(Canvas canvas, RectF content, MapObject object) {
        if (!object.hasPoint) {
            return;
        }
        float x = content.left + object.x * content.width();
        float y = content.top + object.y * content.height();
        float dx = object.dx;
        float dy = object.dy;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length < 0.001f) {
            dx = 0.0f;
            dy = -1.0f;
        } else {
            dx /= length;
            dy /= length;
        }

        float w = dp(7);
        float l = dp(25);
        x -= l * 0.5f * dx;
        y -= l * 0.5f * dy;

        playerPath.reset();
        playerPath.moveTo(x - w * dy, y + w * dx);
        playerPath.lineTo(x + w * dy, y - w * dx);
        playerPath.lineTo(x + l * dx, y + l * dy);
        playerPath.close();
        canvas.drawPath(playerPath, playerFillPaint);
        canvas.drawPath(playerPath, playerStrokePaint);
    }

    private int resolveColor(MapObject object) {
        int[] base = object.colorArray;
        if (base == null) {
            int parsed = Color.WHITE;
            try {
                parsed = Color.parseColor(object.color);
            } catch (Exception ignored) {
                return Color.WHITE;
            }
            base = new int[]{Color.red(parsed), Color.green(parsed), Color.blue(parsed)};
        }

        if (object.blink == 0) {
            return Color.rgb(base[0], base[1], base[2]);
        }

        double period = object.blink == 2 ? 1.2 : 2.0;
        double t = (SystemClock.uptimeMillis() % (long) (period * 1000.0)) / 1000.0;
        double value = Math.exp(-Math.pow(5.0 * t - 2.0, 4.0));
        int r = (int) lerp(base[0], 255, value);
        int g = (int) lerp(base[1], 255, value);
        int b = (int) lerp(base[2], 0, value);
        return Color.rgb(clampColor(r), clampColor(g), clampColor(b));
    }

    private static String glyphFor(String icon) {
        if ("Airdefence".equals(icon)) {
            return "4";
        }
        if ("Structure".equals(icon)) {
            return "5";
        }
        if ("waypoint".equals(icon)) {
            return "6";
        }
        if ("capture_zone".equals(icon)) {
            return "7";
        }
        if ("bombing_point".equals(icon)) {
            return "8";
        }
        if ("defending_point".equals(icon)) {
            return "9";
        }
        if ("respawn_base_tank".equals(icon)) {
            return "0";
        }
        if ("respawn_base_fighter".equals(icon)) {
            return ".";
        }
        if ("respawn_base_bomber".equals(icon)) {
            return ":";
        }
        return icon.substring(0, 1);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static int clampColor(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
