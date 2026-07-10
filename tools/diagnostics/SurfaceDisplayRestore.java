import android.graphics.Rect;
import android.os.IBinder;

import java.lang.reflect.Method;

/**
 * Small adb/app_process diagnostic helper for restoring Thor's lower physical display
 * after SurfaceFlinger projection experiments.
 *
 * This is intentionally not part of the Android app. Build it manually with javac + d8,
 * push the dex to /data/local/tmp, and run it through su/app_process.
 */
public final class SurfaceDisplayRestore {
    private SurfaceDisplayRestore() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 11) {
            throw new IllegalArgumentException(
                    "Usage: SurfaceDisplayRestore <physicalDisplayId> <layerStack> <orientation> "
                            + "<lsLeft> <lsTop> <lsRight> <lsBottom> <dispLeft> <dispTop> <dispRight> <dispBottom>"
            );
        }

        long physicalDisplayId = Long.parseLong(args[0]);
        int layerStack = Integer.parseInt(args[1]);
        int orientation = Integer.parseInt(args[2]);
        Rect layerStackRect = new Rect(
                Integer.parseInt(args[3]),
                Integer.parseInt(args[4]),
                Integer.parseInt(args[5]),
                Integer.parseInt(args[6])
        );
        Rect displayRect = new Rect(
                Integer.parseInt(args[7]),
                Integer.parseInt(args[8]),
                Integer.parseInt(args[9]),
                Integer.parseInt(args[10])
        );

        IBinder token = getPhysicalDisplayToken(physicalDisplayId);
        if (token == null) {
            throw new IllegalStateException("No display token for " + physicalDisplayId);
        }

        Class<?> transactionClass = Class.forName("android.view.SurfaceControl$Transaction");
        Object transaction = transactionClass.getConstructor().newInstance();
        transactionClass.getMethod("setDisplayLayerStack", IBinder.class, int.class)
                .invoke(transaction, token, layerStack);
        transactionClass.getMethod("setDisplayProjection", IBinder.class, int.class, Rect.class, Rect.class)
                .invoke(transaction, token, orientation, layerStackRect, displayRect);
        transactionClass.getMethod("apply").invoke(transaction);
    }

    private static IBinder getPhysicalDisplayToken(long physicalDisplayId) throws Exception {
        String[] classNames = {
                "com.android.server.display.DisplayControl",
                "android.view.SurfaceControl"
        };
        for (String className : classNames) {
            try {
                Class<?> cls = Class.forName(className);
                Method method = cls.getDeclaredMethod("getPhysicalDisplayToken", long.class);
                method.setAccessible(true);
                Object token = method.invoke(null, physicalDisplayId);
                if (token instanceof IBinder) {
                    return (IBinder) token;
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }
}
