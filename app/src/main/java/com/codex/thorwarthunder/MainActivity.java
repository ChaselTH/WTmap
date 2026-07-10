package com.codex.thorwarthunder;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.content.Context;
import android.text.InputType;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.view.inputmethod.InputMethodManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public final class MainActivity extends Activity {
    private static final String PREF_IP = "war_thunder_pc_ip";
    private static final String DEFAULT_IP = "192.168.0.224";
    private static final long POLL_INTERVAL_MS = 50L;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

    private EditText ipInput;
    private Button connectButton;
    private TextView connectionToggleButton;
    private Button aimButton;
    private TextView connectionText;
    private ThorMapView mapView;
    private FrameLayout mapSlot;
    private LinearLayout connectionControls;
    private LinearLayout contentPanel;
    private LinearLayout flightPanel;
    private AttitudeView attitudeView;
    private MetricTile aoaTile;
    private MetricTile aosTile;
    private MetricTile gTile;
    private MetricTile throttleTile;
    private MetricTile verticalSpeedTile;
    private MetricTile thrustTile;
    private MetricTile engineTile;
    private MetricTile fuelTile;
    private MetricTile fuelFlowTile;
    private MetricTile pitchTile;
    private MetricTile rollTile;
    private MetricTile nozzleTile;
    private MetricTile flapsTile;
    private MetricTile gearTile;
    private MetricTile airbrakeTile;

    private ScheduledFuture<?> pollTask;
    private String currentBaseUrl = "";
    private long nextIconLoadAttemptMs;
    private int currentMapGeneration = Integer.MIN_VALUE;
    private Bitmap currentMapBitmap;
    private boolean aircraftLayoutActive;
    private boolean connectionControlsCollapsed;
    private boolean connectionEverSucceeded;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();

        String savedIp = getPreferences(MODE_PRIVATE).getString(PREF_IP, DEFAULT_IP);
        ipInput.setText(savedIp);
        startConnection(savedIp);
    }

    @Override
    protected void onDestroy() {
        if (pollTask != null) {
            pollTask.cancel(true);
        }
        executor.shutdownNow();
        RootInputBridge.shutdown();
        super.onDestroy();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(16, 20, 24));
        setContentView(root);

        HorizontalScrollView topScroll = new HorizontalScrollView(this);
        topScroll.setHorizontalScrollBarEnabled(false);
        topScroll.setFillViewport(false);
        topScroll.setBackgroundColor(Color.rgb(20, 25, 30));
        root.addView(topScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout topBar = new LinearLayout(this);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(dp(10), dp(8), dp(10), dp(8));
        topScroll.addView(topBar, new HorizontalScrollView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        connectionToggleButton = new TextView(this);
        connectionToggleButton.setText(">");
        connectionToggleButton.setTextSize(18);
        connectionToggleButton.setTextColor(Color.WHITE);
        connectionToggleButton.setGravity(Gravity.CENTER);
        connectionToggleButton.setPadding(0, 0, 0, dp(2));
        connectionToggleButton.setOnClickListener(v -> setConnectionControlsCollapsed(!connectionControlsCollapsed));
        topBar.addView(connectionToggleButton, new LinearLayout.LayoutParams(dp(34), dp(44)));

        connectionControls = new LinearLayout(this);
        connectionControls.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams controlsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        controlsParams.setMargins(dp(6), 0, 0, 0);
        topBar.addView(connectionControls, controlsParams);

        ipInput = new EditText(this);
        ipInput.setSingleLine(true);
        ipInput.setHint("PC IP");
        ipInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        ipInput.setTextColor(Color.WHITE);
        ipInput.setHintTextColor(Color.rgb(135, 145, 155));
        ipInput.setTextSize(16);
        ipInput.setSelectAllOnFocus(true);
        ipInput.setPadding(dp(10), 0, dp(10), 0);
        connectionControls.addView(ipInput, new LinearLayout.LayoutParams(dp(220), dp(44)));

        connectButton = new Button(this);
        connectButton.setText("连接");
        connectButton.setTextSize(15);
        connectButton.setAllCaps(false);
        connectButton.setOnClickListener(v -> startConnection(ipInput.getText().toString()));
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(dp(92), dp(44));
        buttonParams.setMargins(dp(8), 0, 0, 0);
        connectionControls.addView(connectButton, buttonParams);

        connectionText = new TextView(this);
        connectionText.setText("未连接");
        connectionText.setTextColor(Color.rgb(175, 185, 195));
        connectionText.setTextSize(13);
        connectionText.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(dp(170), dp(44));
        statusParams.setMargins(dp(8), 0, 0, 0);
        topBar.addView(connectionText, statusParams);

        aimButton = new Button(this);
        aimButton.setText("\u7784\u51c6");
        aimButton.setTextSize(15);
        aimButton.setAllCaps(false);
        aimButton.setOnClickListener(v -> toggleAimMode());
        LinearLayout.LayoutParams aimParams = new LinearLayout.LayoutParams(dp(92), dp(44));
        aimParams.setMargins(dp(8), 0, 0, 0);
        topBar.addView(aimButton, aimParams);

        contentPanel = new LinearLayout(this);
        contentPanel.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(contentPanel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));

        mapSlot = new FrameLayout(this);
        contentPanel.addView(mapSlot, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        mapView = new ThorMapView(this);
        mapSlot.addView(mapView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        flightPanel = new LinearLayout(this);
        flightPanel.setOrientation(LinearLayout.VERTICAL);
        flightPanel.setPadding(dp(8), dp(8), dp(8), dp(8));
        flightPanel.setBackgroundColor(Color.rgb(16, 20, 24));
        flightPanel.setVisibility(View.GONE);
        contentPanel.addView(flightPanel, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.MATCH_PARENT,
                0.85f
        ));

        attitudeView = new AttitudeView(this);
        flightPanel.addView(attitudeView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(105)
        ));

        GridLayout metrics = new GridLayout(this);
        metrics.setColumnCount(3);
        metrics.setBackgroundColor(Color.rgb(16, 20, 24));
        flightPanel.addView(metrics, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));

        aoaTile = addTile(metrics, "迎角");
        aosTile = addTile(metrics, "侧滑");
        gTile = addTile(metrics, "过载");
        throttleTile = addTile(metrics, "油门");
        verticalSpeedTile = addTile(metrics, "垂速");
        thrustTile = addTile(metrics, "推力");
        engineTile = addTile(metrics, "发动机");
        fuelTile = addTile(metrics, "燃油");
        fuelFlowTile = addTile(metrics, "油耗");
        pitchTile = addTile(metrics, "俯仰");
        rollTile = addTile(metrics, "横滚");
        nozzleTile = addTile(metrics, "喷口");
        flapsTile = addTile(metrics, "襟翼");
        gearTile = addTile(metrics, "起落架");
        airbrakeTile = addTile(metrics, "减速板");
    }

    private MetricTile addTile(GridLayout parent, String label) {
        MetricTile tile = new MetricTile(this, label);
        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = 0;
        params.height = dp(50);
        params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        params.setMargins(dp(4), dp(2), dp(4), dp(2));
        parent.addView(tile, params);
        return tile;
    }

    private void startConnection(String rawInput) {
        String baseUrl = buildBaseUrl(rawInput);
        String hostText = stripBaseUrlForInput(baseUrl);
        ipInput.setText(hostText);
        getPreferences(MODE_PRIVATE).edit().putString(PREF_IP, hostText).apply();

        if (pollTask != null) {
            pollTask.cancel(true);
        }
        currentBaseUrl = baseUrl;
        currentMapGeneration = Integer.MIN_VALUE;
        currentMapBitmap = null;
        connectionText.setText("连接中");
        connectButton.setEnabled(false);
        mapView.resetView();

        pollTask = executor.scheduleWithFixedDelay(() -> pollOnce(baseUrl), 0, POLL_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    private void setConnectionControlsCollapsed(boolean collapsed) {
        connectionControlsCollapsed = collapsed;
        if (connectionControls != null) {
            connectionControls.setVisibility(collapsed ? View.GONE : View.VISIBLE);
        }
    }

    private void toggleAimMode() {
        hideSoftKeyboard();
        RootInputBridge.sendKeyZ();
    }

    private void hideSoftKeyboard() {
        ipInput.clearFocus();
        View decorView = getWindow().getDecorView();
        decorView.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(decorView.getWindowToken(), 0);
        }
    }

    private void pollOnce(String baseUrl) {
        try {
            ensureIcons(baseUrl);

            JSONObject state = fetchJsonObject(baseUrl + "/state");
            JSONObject indicators = fetchJsonObject(baseUrl + "/indicators");
            FlightStatus status = FlightStatus.from(state, indicators);

            JSONObject mapInfo = fetchJsonObject(baseUrl + "/map_info.json");
            int mapGeneration = mapInfo.optInt("map_generation", Integer.MIN_VALUE);
            boolean mapChanged = false;
            if (mapInfo.has("map_generation")) {
                if (currentMapBitmap == null || mapGeneration != currentMapGeneration) {
                    Bitmap map = fetchBitmap(baseUrl + "/map.img?gen=" + mapGeneration);
                    currentMapBitmap = map;
                    currentMapGeneration = mapGeneration;
                    mapChanged = true;
                }
            } else if (currentMapBitmap == null) {
                Bitmap map = fetchBitmap(baseUrl + "/map.img");
                currentMapBitmap = map;
                mapChanged = true;
            }

            JSONArray objects = fetchJsonArray(baseUrl + "/map_obj.json");
            List<MapObject> parsedObjects = MapObject.collapseNearbyRespawns(MapObject.fromArray(objects));
            Bitmap bitmapForDisplay = currentMapBitmap;
            boolean resetViewport = mapChanged;
            mainHandler.post(() -> {
                mapView.setMapData(bitmapForDisplay, mapInfo, parsedObjects, resetViewport);
                renderStatus(status);
                connectionText.setText("已连接 " + shortTime());
                connectButton.setEnabled(true);
                if (!connectionEverSucceeded) {
                    connectionEverSucceeded = true;
                    setConnectionControlsCollapsed(true);
                }
            });
        } catch (Exception e) {
            mainHandler.post(() -> {
                connectionText.setText("连接失败");
                connectButton.setEnabled(true);
            });
        }
    }

    private void ensureIcons(String baseUrl) {
        if (mapView.hasIconTypeface()) {
            return;
        }
        long now = SystemClock.elapsedRealtime();
        if (now < nextIconLoadAttemptMs) {
            return;
        }
        nextIconLoadAttemptMs = now + 5000L;

        File fontFile = new File(getCacheDir(), "wt-icons.ttf");
        try {
            if (fontFile.isFile() && fontFile.length() > 0) {
                Typeface cachedTypeface = Typeface.createFromFile(fontFile);
                mainHandler.post(() -> mapView.setIconTypeface(cachedTypeface));
                return;
            }

            byte[] data = fetchBytes(baseUrl + "/icons.ttf");
            File tempFile = new File(getCacheDir(), "wt-icons.tmp");
            FileOutputStream out = new FileOutputStream(tempFile);
            out.write(data);
            out.close();
            if (fontFile.exists() && !fontFile.delete()) {
                throw new IllegalStateException("无法替换图标字体缓存");
            }
            if (!tempFile.renameTo(fontFile)) {
                throw new IllegalStateException("无法保存图标字体缓存");
            }
            Typeface typeface = Typeface.createFromFile(fontFile);
            mainHandler.post(() -> mapView.setIconTypeface(typeface));
        } catch (Exception ignored) {
            // Keep the icon typeface unset so a temporary network/font failure can retry.
        }
    }

    private void renderStatus(FlightStatus status) {
        setAircraftLayout(status.isAircraft);
        if (!status.isAircraft) {
            return;
        }
        attitudeView.setAttitude(status);
        aoaTile.setValues(status.hasAoa ? format(status.aoa, 1) + " deg" : "--", "");
        aosTile.setValues(status.hasAos ? format(status.aos, 1) + " deg" : "--", "");
        gTile.setValues(status.hasNy ? format(status.ny, 2) + " G" : "--", "");
        String throttleSub = status.hasWepTime ? "WEP " + formatSeconds(status.wepSeconds) : "WEP --";
        throttleTile.setValues(status.hasThrottle ? format(status.throttlePercent, 0) + "%" : "--", throttleSub);
        verticalSpeedTile.setValues(status.hasVerticalSpeed ? signed(status.verticalSpeed, 1) + " m/s" : "--", "");

        if (status.hasThrust) {
            thrustTile.setValues(format(status.thrustKg, 0) + " kgf", format(status.thrustKn(), 1) + " kN");
        } else {
            thrustTile.setValues("--", "");
        }

        String engineMain = status.hasRpm ? "RPM " + format(status.rpm, 0) : "--";
        String engineSub = "";
        if (status.hasOilTemp) {
            engineSub = "油温 " + format(status.oilTemp, 0) + " C";
        } else if (status.hasWaterTemp) {
            engineSub = "水温 " + format(status.waterTemp, 0) + " C";
        }
        engineTile.setValues(engineMain, engineSub);

        if (status.hasFuel) {
            String sub = status.hasFuelPercent ? format(status.fuelPercent, 0) + "% 剩余" : "";
            fuelTile.setValues(format(status.fuelKg, 0) + " kg", sub);
        } else {
            fuelTile.setValues("--", "");
        }

        if (status.hasFuelConsume) {
            String sub = status.hasFuelTime ? "续航 " + formatDuration(status.fuelMinutes) : "";
            fuelFlowTile.setValues(format(status.fuelConsume, 1), sub);
        } else {
            fuelFlowTile.setValues("--", "");
        }

        pitchTile.setValues(status.hasPitch ? signed(status.pitchDeg, 1) + " deg" : "--", "");
        rollTile.setValues(status.hasRoll ? signed(status.rollDeg, 0) + " deg" : "--", "");
        nozzleTile.setValues(status.hasNozzleAngle ? format(status.nozzleAngle, 0) + " deg" : "--", "");
        flapsTile.setValues(status.hasFlaps ? format(status.flapsPercent, 0) + "%" : "--", "");
        gearTile.setValues(status.hasGear ? format(status.gearPercent, 0) + "%" : "--", "");
        airbrakeTile.setValues(status.hasAirbrake ? format(status.airbrakePercent, 0) + "%" : "--", "");

    }

    private void setAircraftLayout(boolean isAircraft) {
        if (aircraftLayoutActive == isAircraft) {
            return;
        }
        aircraftLayoutActive = isAircraft;
        applyContentLayout();
    }

    private void applyContentLayout() {
        LinearLayout.LayoutParams mapParams = (LinearLayout.LayoutParams) mapSlot.getLayoutParams();
        if (aircraftLayoutActive) {
            flightPanel.setVisibility(View.VISIBLE);
            mapParams.width = 0;
            mapParams.height = ViewGroup.LayoutParams.MATCH_PARENT;
            mapParams.weight = 1.15f;
            mapView.setAlignMapTopLeft(true);
        } else {
            flightPanel.setVisibility(View.GONE);
            mapParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
            mapParams.height = ViewGroup.LayoutParams.MATCH_PARENT;
            mapParams.weight = 0.0f;
            mapView.setAlignMapTopLeft(false);
        }
        mapSlot.setLayoutParams(mapParams);
    }

    private static String buildBaseUrl(String rawInput) {
        String text = rawInput == null ? "" : rawInput.trim();
        if (text.isEmpty()) {
            text = DEFAULT_IP;
        }
        text = text.replace("https://", "").replace("http://", "");
        int slash = text.indexOf('/');
        if (slash >= 0) {
            text = text.substring(0, slash);
        }
        if (!text.contains(":")) {
            text = text + ":8111";
        }
        return "http://" + text;
    }

    private static String stripBaseUrlForInput(String baseUrl) {
        String text = baseUrl.replace("http://", "");
        if (text.endsWith(":8111")) {
            text = text.substring(0, text.length() - 5);
        }
        return text;
    }

    private static JSONObject fetchJsonObject(String url) throws Exception {
        return new JSONObject(fetchString(url));
    }

    private static JSONArray fetchJsonArray(String url) throws Exception {
        return new JSONArray(fetchString(url));
    }

    private static String fetchString(String url) throws Exception {
        return new String(fetchBytes(url), "UTF-8");
    }

    private static Bitmap fetchBitmap(String url) throws Exception {
        HttpURLConnection connection = openConnection(url);
        try {
            InputStream input = new BufferedInputStream(connection.getInputStream());
            try {
                Bitmap bitmap = BitmapFactory.decodeStream(input);
                if (bitmap == null) {
                    throw new IllegalStateException("Bitmap decode failed");
                }
                return bitmap;
            } finally {
                input.close();
            }
        } finally {
            connection.disconnect();
        }
    }

    private static byte[] fetchBytes(String url) throws Exception {
        HttpURLConnection connection = openConnection(url);
        try {
            InputStream input = new BufferedInputStream(connection.getInputStream());
            try {
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                byte[] buffer = new byte[32 * 1024];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    output.write(buffer, 0, read);
                }
                return output.toByteArray();
            } finally {
                input.close();
            }
        } finally {
            connection.disconnect();
        }
    }

    private static HttpURLConnection openConnection(String url) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(700);
        connection.setReadTimeout(900);
        connection.setUseCaches(false);
        int code = connection.getResponseCode();
        if (code < 200 || code >= 300) {
            throw new IllegalStateException("HTTP " + code);
        }
        return connection;
    }

    private static String format(double value, int digits) {
        return String.format(Locale.US, "%." + digits + "f", value);
    }

    private static String signed(double value, int digits) {
        return String.format(Locale.US, "%+." + digits + "f", value);
    }

    private static String formatDuration(double minutes) {
        int totalSeconds = Math.max(0, (int) Math.round(minutes * 60.0));
        int mm = totalSeconds / 60;
        int ss = totalSeconds % 60;
        return String.format(Locale.US, "%d:%02d", mm, ss);
    }

    private static String formatSeconds(double seconds) {
        int totalSeconds = Math.max(0, (int) Math.round(seconds));
        int mm = totalSeconds / 60;
        int ss = totalSeconds % 60;
        return String.format(Locale.US, "%d:%02d", mm, ss);
    }

    private static String shortTime() {
        return new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static final class MetricTile extends LinearLayout {
        private final TextView value;
        private final TextView subValue;

        MetricTile(Activity activity, String label) {
            super(activity);
            setOrientation(VERTICAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setPadding(dp(activity, 6), dp(activity, 4), dp(activity, 6), dp(activity, 4));
            setBackgroundColor(Color.rgb(24, 29, 34));

            TextView labelView = new TextView(activity);
            labelView.setText(label);
            labelView.setTextColor(Color.rgb(150, 160, 170));
            labelView.setTextSize(9);
            labelView.setSingleLine(true);
            addView(labelView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

            value = new TextView(activity);
            value.setText("--");
            value.setTextColor(Color.WHITE);
            value.setTextSize(13);
            value.setSingleLine(true);
            addView(value, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));

            subValue = new TextView(activity);
            subValue.setText("");
            subValue.setTextColor(Color.rgb(145, 155, 165));
            subValue.setTextSize(8);
            subValue.setSingleLine(true);
            addView(subValue, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        }

        void setValues(String main, String sub) {
            value.setText(main);
            subValue.setText(sub);
        }

        private static int dp(Activity activity, float value) {
            return Math.round(value * activity.getResources().getDisplayMetrics().density);
        }
    }
}
