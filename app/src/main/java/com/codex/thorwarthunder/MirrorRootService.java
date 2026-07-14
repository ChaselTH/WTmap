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

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.lang.reflect.Method;

public final class MirrorRootService {
    private static final String TAG = "WTmapMirrorRoot";
    private static final String DESCRIPTOR = "com.codex.thorwarthunder.MIRROR_ROOT";
    private static final int TRANSACTION_START = IBinder.FIRST_CALL_TRANSACTION;
    private static final int TRANSACTION_STOP = IBinder.FIRST_CALL_TRANSACTION + 1;
    private static final int TRANSACTION_TOUCH = IBinder.FIRST_CALL_TRANSACTION + 2;
    private static final int PRIMARY_LAYER_STACK = 0;
    private static final int EV_SYN = 0;
    private static final int EV_KEY = 1;
    private static final int EV_REL = 2;
    private static final int SYN_REPORT = 0;
    private static final int REL_X = 0;
    private static final int REL_Y = 1;
    private static final int REL_RX = 3;
    private static final int REL_RY = 4;
    private static final int BTN_MOUSE = 272;
    private static Method setDisplayIdMethod;
    private static Method setActionButtonMethod;
    private static Object inputManagerInstance;
    private static Method injectInputEventMethod;

    private MirrorRootService() {
    }

    public static void main(String[] args) {
        String serviceName = args.length > 0 ? args[0] : "wtmap_mirror_v8";
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
        private FileOutputStream mouseOutput;
        private int lastMouseX;
        private int lastMouseY;
        private boolean mouseButtonDown;

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
            closeMouseDevice();
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
                String displayMouseError = injectDisplayMouseDrag(action, x, y);
                if (displayMouseError == null) {
                    return null;
                }
                Log.w(TAG, "Display mouse inject failed, falling back: " + displayMouseError);

                String mouseError = injectOdinMouseDrag(action, x, y);
                if (mouseError == null) {
                    return null;
                }
                Log.w(TAG, "Odin mouse inject failed, falling back: " + mouseError);

                long now = SystemClock.uptimeMillis();
                if (action == MotionEvent.ACTION_DOWN || touchDownTimeMs == 0L) {
                    touchDownTimeMs = now;
                }

                int mappedAction = action == MotionEvent.ACTION_CANCEL ? MotionEvent.ACTION_UP : action;
                int buttonState = mappedAction == MotionEvent.ACTION_UP ? 0 : MotionEvent.BUTTON_PRIMARY;
                float pressure = mappedAction == MotionEvent.ACTION_UP ? 0f : 1f;
                MotionEvent.PointerProperties[] properties = new MotionEvent.PointerProperties[1];
                properties[0] = new MotionEvent.PointerProperties();
                properties[0].id = 0;
                properties[0].toolType = MotionEvent.TOOL_TYPE_MOUSE;

                MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[1];
                coords[0] = new MotionEvent.PointerCoords();
                coords[0].x = x;
                coords[0].y = y;
                coords[0].pressure = pressure;
                coords[0].size = 1.0f;

                MotionEvent event = MotionEvent.obtain(
                        touchDownTimeMs,
                        now,
                        mappedAction,
                        1,
                        properties,
                        coords,
                        0,
                        buttonState,
                        1.0f,
                        1.0f,
                        0,
                        0,
                        InputDevice.SOURCE_MOUSE,
                        0
                );
                try {
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

        private String injectDisplayMouseDrag(int action, int x, int y) {
            try {
                long now = SystemClock.uptimeMillis();
                int mappedAction;
                int buttonState;
                int actionButton = MotionEvent.BUTTON_PRIMARY;
                if (action == MotionEvent.ACTION_DOWN) {
                    touchDownTimeMs = now;
                    mappedAction = MotionEvent.ACTION_BUTTON_PRESS;
                    buttonState = MotionEvent.BUTTON_PRIMARY;
                } else if (action == MotionEvent.ACTION_MOVE) {
                    if (touchDownTimeMs == 0L) {
                        touchDownTimeMs = now;
                    }
                    mappedAction = MotionEvent.ACTION_MOVE;
                    buttonState = MotionEvent.BUTTON_PRIMARY;
                } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    if (touchDownTimeMs == 0L) {
                        touchDownTimeMs = now;
                    }
                    mappedAction = MotionEvent.ACTION_BUTTON_RELEASE;
                    buttonState = 0;
                } else {
                    return null;
                }

                float pressure = buttonState == 0 ? 0f : 1f;
                MotionEvent.PointerProperties[] properties = new MotionEvent.PointerProperties[1];
                properties[0] = new MotionEvent.PointerProperties();
                properties[0].id = 0;
                properties[0].toolType = MotionEvent.TOOL_TYPE_MOUSE;

                MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[1];
                coords[0] = new MotionEvent.PointerCoords();
                coords[0].x = x;
                coords[0].y = y;
                coords[0].pressure = pressure;
                coords[0].size = 1.0f;

                MotionEvent event = MotionEvent.obtain(
                        touchDownTimeMs,
                        now,
                        mappedAction,
                        1,
                        properties,
                        coords,
                        0,
                        buttonState,
                        1.0f,
                        1.0f,
                        0,
                        0,
                        InputDevice.SOURCE_MOUSE,
                        0
                );
                try {
                    setActionButton(event, actionButton);
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
                return shortError(t);
            }
        }

        private String injectOdinMouseDrag(int action, int x, int y) {
            try {
                ensureMouseDevice();
                if (mouseOutput == null) {
                    return "mouse device not available";
                }

                if (action == MotionEvent.ACTION_DOWN) {
                    lastMouseX = x;
                    lastMouseY = y;
                    mouseButtonDown = true;
                    writeInputEvent(mouseOutput, EV_KEY, BTN_MOUSE, 1);
                    writeInputEvent(mouseOutput, EV_SYN, SYN_REPORT, 0);
                    mouseOutput.flush();
                    return null;
                }

                if (action == MotionEvent.ACTION_MOVE) {
                    int dx = clampMouseDelta(x - lastMouseX);
                    int dy = clampMouseDelta(y - lastMouseY);
                    lastMouseX = x;
                    lastMouseY = y;
                    if (!mouseButtonDown) {
                        mouseButtonDown = true;
                        writeInputEvent(mouseOutput, EV_KEY, BTN_MOUSE, 1);
                    }
                    if (dx != 0) {
                        writeInputEvent(mouseOutput, EV_REL, REL_X, dx);
                        writeInputEvent(mouseOutput, EV_REL, REL_RX, dx);
                    }
                    if (dy != 0) {
                        writeInputEvent(mouseOutput, EV_REL, REL_Y, dy);
                        writeInputEvent(mouseOutput, EV_REL, REL_RY, dy);
                    }
                    writeInputEvent(mouseOutput, EV_SYN, SYN_REPORT, 0);
                    mouseOutput.flush();
                    return null;
                }

                if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    if (mouseButtonDown) {
                        writeInputEvent(mouseOutput, EV_KEY, BTN_MOUSE, 0);
                        writeInputEvent(mouseOutput, EV_SYN, SYN_REPORT, 0);
                        mouseOutput.flush();
                    }
                    mouseButtonDown = false;
                    return null;
                }
                return null;
            } catch (Throwable t) {
                closeMouseDevice();
                return shortError(t);
            }
        }

        private void ensureMouseDevice() throws Exception {
            if (mouseOutput != null) {
                return;
            }
            String path = findInputDeviceByName("ODIN Station Virtual Mouse");
            if (path == null) {
                path = "/dev/input/event10";
            }
            mouseOutput = new FileOutputStream(path);
            Log.i(TAG, "Opened mouse device " + path);
        }

        private void closeMouseDevice() {
            if (mouseOutput != null) {
                try {
                    mouseOutput.close();
                } catch (Throwable ignored) {
                }
                mouseOutput = null;
            }
            mouseButtonDown = false;
        }
    }

    private static int clampMouseDelta(int value) {
        return Math.max(-240, Math.min(240, value));
    }

    private static String findInputDeviceByName(String expectedName) {
        File dir = new File("/dev/input");
        File[] files = dir.listFiles();
        if (files == null) {
            return null;
        }
        for (File file : files) {
            String namePath = "/sys/class/input/" + file.getName() + "/device/name";
            try {
                byte[] data = java.nio.file.Files.readAllBytes(new File(namePath).toPath());
                String name = new String(data).trim();
                if (expectedName.equals(name)) {
                    return file.getAbsolutePath();
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static void writeInputEvent(FileOutputStream output, int type, int code, int value) throws Exception {
        ByteBuffer buffer = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putLong(0L);
        buffer.putLong(0L);
        buffer.putShort((short) type);
        buffer.putShort((short) code);
        buffer.putInt(value);
        output.write(buffer.array());
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

    private static void setActionButton(MotionEvent event, int button) {
        try {
            if (setActionButtonMethod == null) {
                setActionButtonMethod = MotionEvent.class.getDeclaredMethod("setActionButton", int.class);
                setActionButtonMethod.setAccessible(true);
            }
            setActionButtonMethod.invoke(event, button);
        } catch (Throwable ignored) {
        }
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
