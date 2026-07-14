package com.codex.thorwarthunder;

import android.content.Context;
import android.graphics.Rect;
import android.os.IBinder;
import android.os.Parcel;
import android.os.SystemClock;
import android.util.Log;
import android.view.Surface;

import java.lang.reflect.Method;

final class MirrorRootBridge {
    private static final String TAG = "WTmapMirror";
    private static final String DESCRIPTOR = "com.codex.thorwarthunder.MIRROR_ROOT";
    private static final String SERVICE_NAME = "wtmap_mirror_v9";
    private static final int TRANSACTION_START = IBinder.FIRST_CALL_TRANSACTION;
    private static final int TRANSACTION_STOP = IBinder.FIRST_CALL_TRANSACTION + 1;
    private static final int TRANSACTION_TOUCH = IBinder.FIRST_CALL_TRANSACTION + 2;
    private static final long SERVICE_WAIT_MS = 1200L;

    private static Process rootProcess;
    private static IBinder cachedService;
    private static Method getServiceMethod;
    private static long lastStartAttemptMs;

    private MirrorRootBridge() {
    }

    static String startMirror(Context context, Surface surface, int width, int height, Rect sourceRect, Rect destRect) {
        if (surface == null || !surface.isValid()) {
            return "surface is not valid";
        }
        IBinder service = ensureService(context);
        if (service == null) {
            return "root mirror service not available";
        }

        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(DESCRIPTOR);
            surface.writeToParcel(data, 0);
            data.writeInt(width);
            data.writeInt(height);
            writeRect(data, sourceRect);
            writeRect(data, destRect);
            service.transact(TRANSACTION_START, data, reply, 0);
            reply.readException();
            String error = reply.readString();
            return error == null || error.length() == 0 ? null : error;
        } catch (Throwable t) {
            return shortError(t);
        } finally {
            reply.recycle();
            data.recycle();
        }
    }

    static String stopMirror(Context context) {
        IBinder service = getService();
        if (service == null) {
            return null;
        }

        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(DESCRIPTOR);
            service.transact(TRANSACTION_STOP, data, reply, 0);
            reply.readException();
            String error = reply.readString();
            return error == null || error.length() == 0 ? null : error;
        } catch (Throwable t) {
            return shortError(t);
        } finally {
            reply.recycle();
            data.recycle();
        }
    }

    static boolean sendTouch(Context context, int action, int x, int y) {
        IBinder service = ensureService(context);
        if (service == null) {
            return false;
        }

        Parcel data = Parcel.obtain();
        try {
            data.writeInterfaceToken(DESCRIPTOR);
            data.writeInt(action);
            data.writeInt(x);
            data.writeInt(y);
            service.transact(TRANSACTION_TOUCH, data, null, IBinder.FLAG_ONEWAY);
            return true;
        } catch (Throwable t) {
            cachedService = null;
            Log.w(TAG, "Root touch inject failed: " + shortError(t), t);
            return false;
        } finally {
            data.recycle();
        }
    }

    private static IBinder ensureService(Context context) {
        IBinder service = getService();
        if (service != null) {
            return service;
        }

        long now = SystemClock.uptimeMillis();
        if (now - lastStartAttemptMs > SERVICE_WAIT_MS) {
            lastStartAttemptMs = now;
            startRootProcess(context);
        }

        long deadline = SystemClock.uptimeMillis() + SERVICE_WAIT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            service = getService();
            if (service != null) {
                return service;
            }
            SystemClock.sleep(80L);
        }
        return getService();
    }

    private static void startRootProcess(Context context) {
        try {
            String apkPath = context.getApplicationInfo().sourceDir;
            String command = "CLASSPATH=" + shellQuote(apkPath)
                    + " app_process / " + MirrorRootService.class.getName()
                    + " " + SERVICE_NAME;
            rootProcess = new ProcessBuilder("su", "-c", command)
                    .redirectErrorStream(true)
                    .start();
            Log.i(TAG, "Started root mirror helper");
        } catch (Throwable t) {
            Log.w(TAG, "Failed to start root mirror helper: " + shortError(t), t);
        }
    }

    private static IBinder getService() {
        try {
            if (cachedService != null && cachedService.isBinderAlive()) {
                return cachedService;
            }
            Class<?> cls = Class.forName("android.os.ServiceManager");
            if (getServiceMethod == null) {
                getServiceMethod = cls.getDeclaredMethod("getService", String.class);
                getServiceMethod.setAccessible(true);
            }
            cachedService = (IBinder) getServiceMethod.invoke(null, SERVICE_NAME);
            return cachedService;
        } catch (Throwable t) {
            cachedService = null;
            Log.w(TAG, "ServiceManager.getService failed: " + shortError(t), t);
            return null;
        }
    }

    private static void writeRect(Parcel parcel, Rect rect) {
        parcel.writeInt(rect.left);
        parcel.writeInt(rect.top);
        parcel.writeInt(rect.right);
        parcel.writeInt(rect.bottom);
    }

    private static String shellQuote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }

    private static String shortError(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        String name = cause.getClass().getSimpleName();
        String message = cause.getMessage();
        return message == null || message.length() == 0 ? name : name + ": " + message;
    }
}
