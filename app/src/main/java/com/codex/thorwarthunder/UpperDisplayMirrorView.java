package com.codex.thorwarthunder;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.os.SystemClock;
import android.util.Log;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

final class UpperDisplayMirrorView extends FrameLayout implements TextureView.SurfaceTextureListener {
    private static final String TAG = "WTmapMirror";
    private static final int PRIMARY_WIDTH = 1920;
    private static final int PRIMARY_HEIGHT = 1080;
    private static final long MOVE_INTERVAL_MS = 8L;

    private final TextureView textureView;
    private final TextView statusView;
    private final Rect sourceRect = new Rect(0, 0, PRIMARY_WIDTH, PRIMARY_HEIGHT);
    private final Rect mirrorRect = new Rect();
    private final Rect displayDestRect = new Rect();

    private Surface mirrorSurface;
    private boolean mirrorActive;
    private long lastMoveAtMs;

    UpperDisplayMirrorView(Context context) {
        super(context);
        setWillNotDraw(true);
        setBackgroundColor(Color.BLACK);

        textureView = new TextureView(context);
        textureView.setOpaque(true);
        textureView.setSurfaceTextureListener(this);
        textureView.setOnTouchListener((view, event) -> handleMirrorTouch(event));
        addView(textureView, new FrameLayout.LayoutParams(1, 1));

        statusView = new TextView(context);
        statusView.setTextColor(Color.rgb(220, 230, 240));
        statusView.setTextSize(14f);
        statusView.setPadding(dp(12), dp(10), dp(12), dp(10));
        statusView.setBackgroundColor(Color.argb(180, 0, 0, 0));
        statusView.setVisibility(View.GONE);
        addView(statusView, new FrameLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT
        ));
    }

    void setMirrorActive(boolean active) {
        mirrorActive = active;
        if (active) {
            showStatus(null);
            updateMirrorLayout();
            createOrUpdateMirror();
        } else {
            destroyMirror();
            showStatus(null);
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        updateMirrorLayout();
        if (mirrorActive) {
            createOrUpdateMirror();
        }
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height) {
        surfaceTexture.setDefaultBufferSize(Math.max(1, width), Math.max(1, height));
        mirrorSurface = new Surface(surfaceTexture);
        if (mirrorActive) {
            createOrUpdateMirror();
        }
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int width, int height) {
        surfaceTexture.setDefaultBufferSize(Math.max(1, width), Math.max(1, height));
        if (mirrorActive) {
            createOrUpdateMirror();
        }
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
        destroyMirror();
        releaseMirrorSurface();
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {
    }

    private void createOrUpdateMirror() {
        if (mirrorSurface == null || !mirrorSurface.isValid()) {
            showStatus("Waiting for mirror surface...");
            return;
        }

        updateMirrorLayout();
        int surfaceWidth = Math.max(1, textureView.getWidth());
        int surfaceHeight = Math.max(1, textureView.getHeight());
        displayDestRect.set(0, 0, surfaceWidth, surfaceHeight);

        String error = MirrorRootBridge.startMirror(getContext(), mirrorSurface,
                surfaceWidth, surfaceHeight, sourceRect, displayDestRect);
        if (error == null) {
            showStatus(null);
            Log.i(TAG, "Root mirror active texture=" + surfaceWidth + "x" + surfaceHeight);
        } else {
            showStatus("Mirror unavailable: " + error);
            Log.w(TAG, "Mirror unavailable: " + error);
        }
    }

    private void destroyMirror() {
        String error = MirrorRootBridge.stopMirror(getContext());
        if (error != null) {
            Log.w(TAG, "Mirror stop warning: " + error);
        }
    }

    private void releaseMirrorSurface() {
        if (mirrorSurface != null) {
            try {
                mirrorSurface.release();
            } catch (Throwable ignored) {
            }
            mirrorSurface = null;
        }
    }

    private void updateMirrorLayout() {
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) {
            mirrorRect.setEmpty();
            return;
        }

        float scale = Math.min(width / (float) PRIMARY_WIDTH, height / (float) PRIMARY_HEIGHT);
        int mirrorWidth = Math.max(1, Math.round(PRIMARY_WIDTH * scale));
        int mirrorHeight = Math.max(1, Math.round(PRIMARY_HEIGHT * scale));
        int left = (width - mirrorWidth) / 2;
        int top = (height - mirrorHeight) / 2;
        mirrorRect.set(left, top, left + mirrorWidth, top + mirrorHeight);

        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) textureView.getLayoutParams();
        if (params.width != mirrorWidth || params.height != mirrorHeight
                || params.leftMargin != left || params.topMargin != top) {
            params.width = mirrorWidth;
            params.height = mirrorHeight;
            params.leftMargin = left;
            params.topMargin = top;
            textureView.setLayoutParams(params);
        }

        SurfaceTexture surfaceTexture = textureView.getSurfaceTexture();
        if (surfaceTexture != null) {
            surfaceTexture.setDefaultBufferSize(mirrorWidth, mirrorHeight);
        }
    }

    private boolean handleMirrorTouch(MotionEvent event) {
        if (!mirrorActive) {
            return false;
        }

        int action = event.getActionMasked();
        if (action != MotionEvent.ACTION_DOWN
                && action != MotionEvent.ACTION_MOVE
                && action != MotionEvent.ACTION_UP
                && action != MotionEvent.ACTION_CANCEL) {
            return true;
        }

        long now = SystemClock.uptimeMillis();
        if (action == MotionEvent.ACTION_MOVE && now - lastMoveAtMs < MOVE_INTERVAL_MS) {
            return true;
        }
        if (action == MotionEvent.ACTION_MOVE) {
            lastMoveAtMs = now;
        }

        float nx = clamp(event.getX() / Math.max(1, textureView.getWidth()), 0f, 1f);
        float ny = clamp(event.getY() / Math.max(1, textureView.getHeight()), 0f, 1f);
        int targetX = Math.round(nx * (PRIMARY_WIDTH - 1));
        int targetY = Math.round(ny * (PRIMARY_HEIGHT - 1));
        if (!MirrorRootBridge.sendTouch(getContext(), action, targetX, targetY)) {
            RootInputBridge.sendTouch(actionName(action), targetX, targetY);
        }
        return true;
    }

    private void showStatus(String status) {
        if (status == null || status.length() == 0) {
            statusView.setText("");
            statusView.setVisibility(View.GONE);
        } else {
            statusView.setText(status);
            statusView.setVisibility(View.VISIBLE);
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static String actionName(int action) {
        if (action == MotionEvent.ACTION_DOWN) {
            return "DOWN";
        }
        if (action == MotionEvent.ACTION_UP) {
            return "UP";
        }
        if (action == MotionEvent.ACTION_CANCEL) {
            return "CANCEL";
        }
        return "MOVE";
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
