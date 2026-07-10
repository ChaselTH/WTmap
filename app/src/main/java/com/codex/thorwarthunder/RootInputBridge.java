package com.codex.thorwarthunder;

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
    static final int TARGET_DISPLAY_ID = 0;

    private static final ExecutorService INPUT_EXECUTOR = Executors.newSingleThreadExecutor();
    private static final RootShell INPUT_SHELL = new RootShell("wtmap-input-root");

    private RootInputBridge() {
    }

    static void sendKeyZ() {
        runAsync("input -d " + TARGET_DISPLAY_ID + " keyevent KEYCODE_Z");
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
        runAsync(command);
    }

    static void runAsync(String command) {
        INPUT_EXECUTOR.execute(() -> INPUT_SHELL.run(command, 2500L));
    }

    static String shQuote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }

    static void shutdown() {
        INPUT_EXECUTOR.shutdownNow();
        INPUT_SHELL.close();
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
