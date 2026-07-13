package com.codex.thorwarthunder;

import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.Locale;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

final class RootInputBridge {
    private static final String TAG = "WTmapInput";
    static final int TARGET_DISPLAY_ID = 0;

    private static final ExecutorService INPUT_EXECUTOR = Executors.newSingleThreadExecutor();
    private static final RootShell INPUT_SHELL = new RootShell("wtmap-input-root");

    private RootInputBridge() {
    }

    static void sendKeyZ() {
        sendKey("Z");
    }

    static void sendKey(String keyName) {
        String normalizedKey = normalizeKeyName(keyName);
        if (normalizedKey == null) {
            normalizedKey = "Z";
        }
        int linuxCode = linuxKeyCode(normalizedKey);
        int androidCode = androidKeyCode(normalizedKey);
        if (linuxCode <= 0 || androidCode <= 0) {
            runAsync("input keyboard -d " + TARGET_DISPLAY_ID + " keyevent 54", "send " + normalizedKey);
            return;
        }

        // Android keyevent is accepted by the shell but Wine can ignore it because the event has
        // no real evdev scan code. Thor's upper touch/input device exposes KEY_Z, so prefer a
        // low-level EV_KEY press. If the built-in device does not expose the key (for example G),
        // create a short-lived uinput keyboard instead of falling back to Android keyevent.
        String linuxLabel = linuxKeyLabel(normalizedKey);
        String command =
                "DEV=/dev/input/event6; FOUND=0; " +
                "if [ -e \"$DEV\" ] && getevent -lp \"$DEV\" 2>/dev/null | grep -qw " + linuxLabel + "; then " +
                "  FOUND=1; " +
                "else " +
                "  for d in /dev/input/event*; do " +
                "    if getevent -lp \"$d\" 2>/dev/null | grep -qw " + linuxLabel + "; then DEV=\"$d\"; FOUND=1; break; fi; " +
                "  done; " +
                "fi; " +
                "if [ \"$FOUND\" = \"1\" ]; then " +
                "  sendevent \"$DEV\" 1 " + linuxCode + " 1; sendevent \"$DEV\" 0 0 0; " +
                "  sleep 0.06; " +
                "  sendevent \"$DEV\" 1 " + linuxCode + " 0; sendevent \"$DEV\" 0 0 0; " +
                "else " +
                uinputKeyCommand(linuxLabel, androidCode) +
                "fi";
        runAsync(command, "send " + normalizedKey + " hardware");
    }

    static boolean isSupportedKey(String keyName) {
        String normalizedKey = normalizeKeyName(keyName);
        return normalizedKey != null && androidKeyCode(normalizedKey) > 0 && linuxKeyCode(normalizedKey) > 0;
    }

    static String normalizeKeyName(String keyName) {
        if (keyName == null) {
            return null;
        }
        String key = keyName.trim().toUpperCase(Locale.US);
        if (key.length() == 1) {
            char c = key.charAt(0);
            if ((c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')) {
                return key;
            }
        }
        if (" ".equals(key) || "SPACEBAR".equals(key)) {
            return "SPACE";
        }
        if ("ESCAPE".equals(key)) {
            return "ESC";
        }
        if ("RETURN".equals(key)) {
            return "ENTER";
        }
        if ("UP".equals(key)) {
            return "UP";
        }
        if ("DOWN".equals(key)) {
            return "DOWN";
        }
        if ("LEFT".equals(key)) {
            return "LEFT";
        }
        if ("RIGHT".equals(key)) {
            return "RIGHT";
        }
        switch (key) {
            case "SPACE":
            case "ENTER":
            case "ESC":
            case "TAB":
            case "SHIFT":
            case "CTRL":
            case "ALT":
                return key;
            default:
                return null;
        }
    }

    static void sendTouch(String action, int x, int y) {
        String command = String.format(
                Locale.US,
                "input touchscreen -d %d motionevent %s %d %d",
                TARGET_DISPLAY_ID,
                action,
                x,
                y
        );
        runAsync(command, "touch " + action + " " + x + "," + y);
    }

    static void runAsync(String command) {
        runAsync(command, command);
    }

    static void runAsync(String command, String label) {
        INPUT_EXECUTOR.execute(() -> {
            boolean ok = INPUT_SHELL.run(command, 2500L);
            Log.i(TAG, label + " -> " + (ok ? "ok" : "failed"));
        });
    }

    static String shQuote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }

    static void shutdown() {
        INPUT_EXECUTOR.shutdownNow();
        INPUT_SHELL.close();
    }

    private static int androidKeyCode(String key) {
        if (key.length() == 1) {
            char c = key.charAt(0);
            if (c >= 'A' && c <= 'Z') {
                return 29 + (c - 'A');
            }
            if (c >= '0' && c <= '9') {
                return c == '0' ? 7 : 8 + (c - '1');
            }
        }
        switch (key) {
            case "SPACE":
                return 62;
            case "ENTER":
                return 66;
            case "ESC":
                return 111;
            case "TAB":
                return 61;
            case "SHIFT":
                return 59;
            case "CTRL":
                return 113;
            case "ALT":
                return 57;
            case "UP":
                return 19;
            case "DOWN":
                return 20;
            case "LEFT":
                return 21;
            case "RIGHT":
                return 22;
            default:
                return -1;
        }
    }

    private static int linuxKeyCode(String key) {
        if (key.length() == 1) {
            switch (key.charAt(0)) {
                case 'A':
                    return 30;
                case 'B':
                    return 48;
                case 'C':
                    return 46;
                case 'D':
                    return 32;
                case 'E':
                    return 18;
                case 'F':
                    return 33;
                case 'G':
                    return 34;
                case 'H':
                    return 35;
                case 'I':
                    return 23;
                case 'J':
                    return 36;
                case 'K':
                    return 37;
                case 'L':
                    return 38;
                case 'M':
                    return 50;
                case 'N':
                    return 49;
                case 'O':
                    return 24;
                case 'P':
                    return 25;
                case 'Q':
                    return 16;
                case 'R':
                    return 19;
                case 'S':
                    return 31;
                case 'T':
                    return 20;
                case 'U':
                    return 22;
                case 'V':
                    return 47;
                case 'W':
                    return 17;
                case 'X':
                    return 45;
                case 'Y':
                    return 21;
                case 'Z':
                    return 44;
                case '1':
                    return 2;
                case '2':
                    return 3;
                case '3':
                    return 4;
                case '4':
                    return 5;
                case '5':
                    return 6;
                case '6':
                    return 7;
                case '7':
                    return 8;
                case '8':
                    return 9;
                case '9':
                    return 10;
                case '0':
                    return 11;
                default:
                    return -1;
            }
        }
        switch (key) {
            case "SPACE":
                return 57;
            case "ENTER":
                return 28;
            case "ESC":
                return 1;
            case "TAB":
                return 15;
            case "SHIFT":
                return 42;
            case "CTRL":
                return 29;
            case "ALT":
                return 56;
            case "UP":
                return 103;
            case "DOWN":
                return 108;
            case "LEFT":
                return 105;
            case "RIGHT":
                return 106;
            default:
                return -1;
        }
    }

    private static String linuxKeyLabel(String key) {
        if (key.length() == 1) {
            return "KEY_" + key;
        }
        switch (key) {
            case "SPACE":
                return "KEY_SPACE";
            case "ENTER":
                return "KEY_ENTER";
            case "ESC":
                return "KEY_ESC";
            case "TAB":
                return "KEY_TAB";
            case "SHIFT":
                return "KEY_LEFTSHIFT";
            case "CTRL":
                return "KEY_LEFTCTRL";
            case "ALT":
                return "KEY_LEFTALT";
            case "UP":
                return "KEY_UP";
            case "DOWN":
                return "KEY_DOWN";
            case "LEFT":
                return "KEY_LEFT";
            case "RIGHT":
                return "KEY_RIGHT";
            default:
                return "KEY_Z";
        }
    }

    private static String uinputKeyCommand(String linuxLabel, int androidCode) {
        String file = "/data/local/tmp/wtmap_uinput_key_$$.json";
        String json = "{\"id\":1,\"command\":\"register\",\"name\":\"WTmap Virtual Keyboard\","
                + "\"vid\":6354,\"pid\":11330,\"bus\":\"usb\","
                + "\"configuration\":[{\"type\":\"UI_SET_EVBIT\",\"data\":[\"EV_KEY\"]},"
                + "{\"type\":\"UI_SET_KEYBIT\",\"data\":[\"" + linuxLabel + "\"]}]}\n"
                + "{\"id\":1,\"command\":\"delay\",\"duration\":220}\n"
                + "{\"id\":1,\"command\":\"inject\",\"events\":[\"EV_KEY\",\"" + linuxLabel + "\",1,"
                + "\"EV_SYN\",\"SYN_REPORT\",0,"
                + "\"EV_KEY\",\"" + linuxLabel + "\",0,"
                + "\"EV_SYN\",\"SYN_REPORT\",0]}\n";
        return "  printf '%s' " + shQuote(json) + " > " + file + "; "
                + "  /system/bin/uinput " + file + " >/dev/null 2>&1; RESULT=$?; "
                + "  rm -f " + file + "; "
                + "  if [ \"$RESULT\" != \"0\" ]; then input keyboard -d " + TARGET_DISPLAY_ID + " keyevent " + androidCode + "; fi; "
                + "  test \"$RESULT\" = \"0\"; ";
    }

    private static final class RootShell {
        private final String name;
        private final BlockingQueue<String> outputLines = new LinkedBlockingQueue<>();
        private Process process;
        private BufferedWriter writer;
        private Thread readerThread;
        private int commandCounter;

        RootShell(String name) {
            this.name = name;
        }

        synchronized boolean run(String command, long timeoutMs) {
            try {
                ensureStarted();
                String marker = "__WTMAP_DONE_" + (++commandCounter) + "__";
                writer.write(command);
                writer.newLine();
                writer.write("echo " + marker + ":$?");
                writer.newLine();
                writer.flush();

                long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
                while (System.nanoTime() < deadline) {
                    long waitMs = Math.max(1L, TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime()));
                    String line = outputLines.poll(Math.min(waitMs, 250L), TimeUnit.MILLISECONDS);
                    if (line == null) {
                        continue;
                    }
                    if (line.startsWith(marker + ":")) {
                        return "0".equals(line.substring((marker + ":").length()).trim());
                    }
                }
                restart();
                return false;
            } catch (Exception ignored) {
                restart();
                return false;
            }
        }

        synchronized void close() {
            if (writer != null) {
                try {
                    writer.write("exit");
                    writer.newLine();
                    writer.flush();
                } catch (Exception ignored) {
                }
            }
            if (process != null) {
                process.destroy();
            }
            writer = null;
            process = null;
            readerThread = null;
            outputLines.clear();
        }

        private void ensureStarted() throws Exception {
            if (process != null && process.isAlive() && writer != null) {
                return;
            }
            outputLines.clear();
            process = new ProcessBuilder("su").redirectErrorStream(true).start();
            writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), "UTF-8"));
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), "UTF-8"));
            readerThread = new Thread(() -> {
                try {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        outputLines.offer(line);
                    }
                } catch (Exception ignored) {
                }
            }, name);
            readerThread.setDaemon(true);
            readerThread.start();
        }

        private void restart() {
            close();
        }
    }
}
