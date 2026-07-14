package com.codex.thorwarthunder;

import android.graphics.Rect;
import android.os.Binder;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.os.SystemClock;
import android.util.Log;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.MotionEvent;
import android.view.Surface;

import java.lang.reflect.Method;

public final class MirrorRootService {
    private static final String TAG = "WTmapMirrorRoot";
    private static final String DESCRIPTOR = "com.codex.thorwarthunder.MIRROR_ROOT";
    private static final int TRANSACTION_START = IBinder.FIRST_CALL_TRANSACTION;
    private static final int TRANSACTION_STOP = IBinder.FIRST_CALL_TRANSACTION + 1;
    private static final int TRANSACTION_TOUCH = IBinder.FIRST_CALL_TRANSACTION + 2;
    private static final int PRIMARY_LAYER_STACK = 0;
    private static Method setDisplayIdMethod;
    private static Object inputManagerInstance;
    private static Method injectInputEventMethod;

    private MirrorRootService() {
    }

    public static void main(String[] args) {
        String serviceName = args.length > 0 ? args[0] : "wtmap_mirror_v4";
        try {
            Looper.prepare();
            addService(serviceName, new MirrorBinder());
            Log.i(TAG, "Registered " + serviceName);
            Looper.loop();
        } catch (Throwable t) {
            Log.e(TAG, "Root mirror service failed: " + shortError(t), t);
        }
    }

    private static void addService(String serviceName, IBinder binder) throws Exception {
        Class<?> cls = Class.forName("android.os.ServiceManager");
        Method method = cls.getDeclaredMethod("addService", String.class, IBinder.class);
        method.setAccessible(true);
        method.invoke(null, serviceName, binder);
    }

    private static final class MirrorBinder extends Binder {
        private IBinder displayToken;
        private Surface currentSurface;
        private long touchDownTimeMs;

        @Override
        protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) {
            try {
                data.enforceInterface(DESCRIPTOR);
                if (code == TRANSACTION_START) {
                    Surface surface = Surface.CREATOR.createFromParcel(data);
                    int width = data.readInt();
                    int height = data.readInt();
                    Rect sourceRect = readRect(data);
                    Rect destRect = readRect(data);
                    String error = startMirror(surface, width, height, sourceRect, destRect);
                    reply.writeNoException();
                    reply.writeString(error);
                    return true;
                }
                if (code == TRANSACTION_STOP) {
                    String error = stopMirror();
                    reply.writeNoException();
                    reply.writeString(error);
                    return true;
                }
                if (code == TRANSACTION_TOUCH) {
                    int action = data.readInt();
                    int x = data.readInt();
                    int y = data.readInt();
                    String error = injectTouch(action, x, y);
                    if (reply != null) {
                        reply.writeNoException();
                        reply.writeString(error);
                    } else if (error != null && error.length() > 0) {
                        Log.w(TAG, "Async touch inject failed: " + error);
                    }
                    return true;
                }
            } catch (Throwable t) {
                Log.w(TAG, "Binder transaction failed: " + shortError(t), t);
                try {
                    reply.writeNoException();
                    reply.writeString(shortError(t));
                } catch (Throwable ignored) {
                }
                return true;
            }
            return false;
        }

        private String startMirror(Surface surface, int width, int height, Rect sourceRect, Rect destRect) {
            try {
                if (surface == null || !surface.isValid()) {
                    return "surface is not valid in root service";
                }
                if (displayToken == null) {
                    displayToken = createDisplay();
                    if (displayToken == null) {
                        return "root createDisplay returned null";
                    }
                    Log.i(TAG, "Created virtual display token");
                }

                applyDisplayTransaction(surface, width, height, sourceRect, destRect);
                Surface oldSurface = currentSurface;
                currentSurface = surface;
                if (oldSurface != null && oldSurface != surface) {
                    oldSurface.release();
                }
                Log.i(TAG, "Mirror active size=" + width + "x" + height + " dest=" + destRect);
                return null;
            } catch (Throwable t) {
                Log.w(TAG, "Mirror start failed: " + shortError(t), t);
                try {
                    surface.release();
                } catch (Throwable ignored) {
                }
                return shortError(t);
            }
        }

        private String stopMirror() {
            try {
                destroyDisplay();
                return null;
            } catch (Throwable t) {
                Log.w(TAG, "Mirror stop failed: " + shortError(t), t);
                return shortError(t);
            }
        }

        private IBinder createDisplay() throws Exception {
            Class<?> cls = Class.forName("android.view.SurfaceControl");
            return (IBinder) invokeStatic(cls, "createDisplay",
                    new Class<?>[]{String.class, boolean.class},
                    "WTmapUpperMirror", false);
        }

        private void destroyDisplay() {
            if (displayToken != null) {
                try {
                    Class<?> cls = Class.forName("android.view.SurfaceControl");
                    invokeStatic(cls, "destroyDisplay",
                            new Class<?>[]{IBinder.class},
                            displayToken);
                } catch (Throwable ignored) {
                }
                displayToken = null;
            }
            if (currentSurface != null) {
                try {
                    currentSurface.release();
                } catch (Throwable ignored) {
                }
                currentSurface = null;
            }
        }

        private void applyDisplayTransaction(Surface surface, int width, int height,
                                             Rect sourceRect, Rect destRect) throws Exception {
            Class<?> transactionClass = Class.forName("android.view.SurfaceControl$Transaction");
            Object transaction = transactionClass.getDeclaredConstructor().newInstance();
            try {
                invokeInstance(transactionClass, transaction, "setDisplaySurface",
                        new Class<?>[]{IBinder.class, Surface.class},
                        displayToken, surface);
                invokeInstance(transactionClass, transaction, "setDisplayLayerStack",
                        new Class<?>[]{IBinder.class, int.class},
                        displayToken, PRIMARY_LAYER_STACK);
                invokeInstance(transactionClass, transaction, "setDisplaySize",
                        new Class<?>[]{IBinder.class, int.class, int.class},
                        displayToken, Math.max(1, width), Math.max(1, height));
                invokeInstance(transactionClass, transaction, "setDisplayProjection",
                        new Class<?>[]{IBinder.class, int.class, Rect.class, Rect.class},
                        displayToken, 0, sourceRect, destRect);
                invokeInstance(transactionClass, transaction, "apply", new Class<?>[]{});
            } finally {
                try {
                    invokeInstance(transactionClass, transaction, "close", new Class<?>[]{});
                } catch (Throwable ignored) {
                }
            }
        }

        private String injectTouch(int action, int x, int y) {
            try {
                long now = SystemClock.uptimeMillis();
                if (action == MotionEvent.ACTION_DOWN || touchDownTimeMs == 0L) {
                    touchDownTimeMs = now;
                }

                float pressure = (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) ? 0f : 1f;
                MotionEvent event = MotionEvent.obtain(
                        touchDownTimeMs,
                        now,
                        action,
                        x,
                        y,
                        pressure,
                        1.0f,
                        0,
                        1.0f,
                        1.0f,
                        0,
                        0
                );
                try {
                    event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
                    setDisplayId(event, 0);
                    if (!injectInputEvent(event)) {
                        return "injectInputEvent returned false";
                    }
                    return null;
                } finally {
                    event.recycle();
                    if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                        touchDownTimeMs = 0L;
                    }
                }
            } catch (Throwable t) {
                Log.w(TAG, "Touch inject failed: " + shortError(t), t);
                return shortError(t);
            }
        }
    }

    private static Rect readRect(Parcel parcel) {
        return new Rect(parcel.readInt(), parcel.readInt(), parcel.readInt(), parcel.readInt());
    }

    private static Object invokeStatic(Class<?> cls, String name, Class<?>[] parameterTypes, Object... args)
            throws Exception {
        Method method = cls.getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        return method.invoke(null, args);
    }

    private static Object invokeInstance(Class<?> cls, Object target, String name, Class<?>[] parameterTypes,
                                         Object... args) throws Exception {
        Method method = cls.getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        return method.invoke(target, args);
    }

    private static void setDisplayId(InputEvent event, int displayId) throws Exception {
        if (setDisplayIdMethod == null) {
            setDisplayIdMethod = InputEvent.class.getDeclaredMethod("setDisplayId", int.class);
            setDisplayIdMethod.setAccessible(true);
        }
        setDisplayIdMethod.invoke(event, displayId);
    }

    private static boolean injectInputEvent(InputEvent event) throws Exception {
        if (inputManagerInstance == null || injectInputEventMethod == null) {
            Class<?> inputManagerClass = Class.forName("android.hardware.input.InputManager");
            Method getInstance = inputManagerClass.getDeclaredMethod("getInstance");
            getInstance.setAccessible(true);
            inputManagerInstance = getInstance.invoke(null);
            injectInputEventMethod = inputManagerClass.getDeclaredMethod("injectInputEvent", InputEvent.class, int.class);
            injectInputEventMethod.setAccessible(true);
        }
        Object result = injectInputEventMethod.invoke(inputManagerInstance, event, 0);
        return Boolean.TRUE.equals(result);
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
