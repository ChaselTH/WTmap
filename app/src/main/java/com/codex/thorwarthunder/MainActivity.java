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
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
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
    private static final String PREF_AIM_KEY = "action_aim_key";
    private static final String PREF_GEAR_KEY = "action_gear_key";
    private static final String PREF_ACTIONS = "action_buttons_json";
    private static final String DEFAULT_IP = "192.168.0.224";
    private static final String DEFAULT_AIM_KEY = "Z";
    private static final String DEFAULT_GEAR_KEY = "";
    private static final long POLL_INTERVAL_MS = 50L;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

    private EditText ipInput;
    private Button connectButton;
    private TextView connectionToggleButton;
    private TextView actionMenuToggleButton;
    private Button aimButton;
    private Button gearButton;
    private Button settingsButton;
    private TextView connectionText;
    private ThorMapView mapView;
    private UpperDisplayMirrorView mirrorView;
    private LinearLayout mainPage;
    private LinearLayout rootLayout;
    private LinearLayout settingsPage;
    private LinearLayout actionButtonsBar;
    private LinearLayout settingsTable;
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
    private boolean aimModeActive;
    private boolean actionButtonsCollapsed = true;
    private String aimKey = DEFAULT_AIM_KEY;
    private String gearKey = DEFAULT_GEAR_KEY;
    private int selectedActionIndex = -1;
    private final List<ActionButton> actionButtons = new ArrayList<>();
    private final List<ActionRowBinding> actionRowBindings = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        aimKey = RootInputBridge.normalizeKeyName(
                getPreferences(MODE_PRIVATE).getString(PREF_AIM_KEY, DEFAULT_AIM_KEY)
        );
        if (aimKey == null) {
            aimKey = DEFAULT_AIM_KEY;
        }
        gearKey = loadKeyPreference(PREF_GEAR_KEY, DEFAULT_GEAR_KEY);
        loadActionButtons();
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
        if (mirrorView != null) {
            mirrorView.setMirrorActive(false);
        }
        executor.shutdownNow();
        RootInputBridge.shutdown();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (settingsPage != null && settingsPage.getVisibility() == View.VISIBLE) {
            hideSettingsPage();
            return;
        }
        super.onBackPressed();
    }

    private String loadKeyPreference(String prefName, String fallback) {
        String raw = getPreferences(MODE_PRIVATE).getString(prefName, fallback);
        if (raw == null || raw.trim().isEmpty()) {
            return fallback == null ? "" : fallback;
        }
        String normalized = RootInputBridge.normalizeKeyName(raw);
        return normalized == null ? (fallback == null ? "" : fallback) : normalized;
    }

    private void loadActionButtons() {
        actionButtons.clear();
        String stored = getPreferences(MODE_PRIVATE).getString(PREF_ACTIONS, null);
        if (stored != null && stored.trim().length() > 0) {
            try {
                JSONArray array = new JSONArray(stored);
                for (int i = 0; i < array.length(); i++) {
                    JSONObject object = array.optJSONObject(i);
                    if (object == null) {
                        continue;
                    }
                    String id = object.optString("id", "custom_" + i);
                    String name = object.optString("name", "\u6309\u94ae" + (i + 1));
                    String key = loadStoredKey(object.optString("key", ""));
                    String type = object.optString("type", "key");
                    actionButtons.add(new ActionButton(id, name, key, type));
                }
            } catch (Exception ignored) {
                actionButtons.clear();
            }
        }

        if (actionButtons.isEmpty()) {
            actionButtons.add(new ActionButton("aim", "\u7784\u51c6", aimKey, "aim"));
            actionButtons.add(new ActionButton("gear", "\u8d77\u843d\u67b6", gearKey, "key"));
        } else if (findAimAction() == null) {
            actionButtons.add(0, new ActionButton("aim", "\u7784\u51c6", aimKey, "aim"));
        }
        syncLegacyActionKeys();
    }

    private String loadStoredKey(String key) {
        if (key == null || key.trim().isEmpty()) {
            return "";
        }
        String normalized = RootInputBridge.normalizeKeyName(key);
        return normalized == null ? "" : normalized;
    }

    private void saveActionButtons() {
        try {
            JSONArray array = new JSONArray();
            for (ActionButton action : actionButtons) {
                JSONObject object = new JSONObject();
                object.put("id", action.id);
                object.put("name", action.name);
                object.put("key", action.key == null ? "" : action.key);
                object.put("type", action.type);
                array.put(object);
            }
            getPreferences(MODE_PRIVATE).edit()
                    .putString(PREF_ACTIONS, array.toString())
                    .putString(PREF_AIM_KEY, aimKey)
                    .putString(PREF_GEAR_KEY, gearKey)
                    .apply();
        } catch (Exception ignored) {
        }
    }

    private void syncLegacyActionKeys() {
        ActionButton aim = findAimAction();
        aimKey = aim == null ? DEFAULT_AIM_KEY : (aim.key == null || aim.key.length() == 0 ? "" : aim.key);
        ActionButton gear = findActionById("gear");
        gearKey = gear == null ? "" : (gear.key == null ? "" : gear.key);
    }

    private ActionButton findAimAction() {
        for (ActionButton action : actionButtons) {
            if (action.isAim()) {
                return action;
            }
        }
        return null;
    }

    private ActionButton findActionById(String id) {
        for (ActionButton action : actionButtons) {
            if (id.equals(action.id)) {
                return action;
            }
        }
        return null;
    }

    private void buildUi() {
        FrameLayout appRoot = new FrameLayout(this);
        appRoot.setBackgroundColor(Color.rgb(16, 20, 24));
        setContentView(appRoot);

        LinearLayout root = new LinearLayout(this);
        mainPage = root;
        rootLayout = root;
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(16, 20, 24));
        root.setFocusable(true);
        root.setFocusableInTouchMode(true);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
                | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        appRoot.addView(root, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        FrameLayout topFrame = new FrameLayout(this);
        topFrame.setBackgroundColor(Color.rgb(20, 25, 30));
        root.addView(topFrame, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        HorizontalScrollView topScroll = new HorizontalScrollView(this);
        topScroll.setHorizontalScrollBarEnabled(false);
        topScroll.setFillViewport(false);
        topScroll.setBackgroundColor(Color.rgb(20, 25, 30));
        topFrame.addView(topScroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout topBar = new LinearLayout(this);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(dp(10), dp(8), dp(88), dp(8));
        topScroll.addView(topBar, new HorizontalScrollView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        settingsButton = new Button(this);
        settingsButton.setText("设置");
        settingsButton.setTextSize(13);
        settingsButton.setAllCaps(false);
        settingsButton.setOnClickListener(v -> showSettingsPage());
        FrameLayout.LayoutParams settingsParams = new FrameLayout.LayoutParams(
                dp(72),
                dp(44),
                Gravity.RIGHT | Gravity.CENTER_VERTICAL
        );
        settingsParams.setMargins(0, dp(8), dp(8), dp(8));
        topFrame.addView(settingsButton, settingsParams);

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

        actionMenuToggleButton = new TextView(this);
        actionMenuToggleButton.setText("\u6309\u94ae");
        actionMenuToggleButton.setTextSize(15);
        actionMenuToggleButton.setTextColor(Color.WHITE);
        actionMenuToggleButton.setGravity(Gravity.CENTER);
        actionMenuToggleButton.setBackgroundColor(Color.rgb(34, 42, 50));
        actionMenuToggleButton.setOnClickListener(v -> setActionButtonsCollapsed(!actionButtonsCollapsed));
        LinearLayout.LayoutParams actionToggleParams = new LinearLayout.LayoutParams(dp(70), dp(44));
        actionToggleParams.setMargins(dp(8), 0, 0, 0);
        topBar.addView(actionMenuToggleButton, actionToggleParams);

        actionButtonsBar = new LinearLayout(this);
        actionButtonsBar.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams actionBarParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        actionBarParams.setMargins(dp(6), 0, 0, 0);
        topBar.addView(actionButtonsBar, actionBarParams);
        rebuildActionButtonsBar();

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

        mirrorView = new UpperDisplayMirrorView(this);
        mirrorView.setVisibility(View.GONE);
        mapSlot.addView(mirrorView, new FrameLayout.LayoutParams(
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
        buildSettingsPage(appRoot);
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

    private void setActionButtonsCollapsed(boolean collapsed) {
        actionButtonsCollapsed = collapsed;
        if (actionButtonsBar != null) {
            actionButtonsBar.setVisibility(collapsed ? View.GONE : View.VISIBLE);
        }
        if (actionMenuToggleButton != null) {
            actionMenuToggleButton.setText(collapsed ? "\u6309\u94ae>" : "\u6309\u94ae<");
        }
    }

    private void rebuildActionButtonsBar() {
        if (actionButtonsBar == null) {
            return;
        }
        actionButtonsBar.removeAllViews();
        aimButton = null;
        gearButton = null;
        for (ActionButton action : actionButtons) {
            Button button = new Button(this);
            button.setText(actionLabel(action));
            button.setTextSize(15);
            button.setAllCaps(false);
            button.setFocusable(false);
            button.setFocusableInTouchMode(false);
            button.setOnClickListener(v -> triggerAction(action));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(96), dp(44));
            params.setMargins(dp(6), 0, 0, 0);
            actionButtonsBar.addView(button, params);
            if (action.isAim()) {
                aimButton = button;
            }
            if ("gear".equals(action.id)) {
                gearButton = button;
            }
        }
        setActionButtonsCollapsed(actionButtonsCollapsed);
    }

    private String actionLabel(ActionButton action) {
        if (action.isAim() && aimModeActive) {
            return action.name + "*";
        }
        return action.name;
    }

    private void triggerAction(ActionButton action) {
        hideSoftKeyboard();
        if (action.isAim()) {
            String normalized = RootInputBridge.normalizeKeyName(action.key);
            if (normalized != null) {
                RootInputBridge.sendKey(normalized);
            }
            setAimMode(!aimModeActive);
            mainHandler.postDelayed(this::hideSoftKeyboard, 120L);
            return;
        }
        triggerMappedKey(action.key, action.name, true);
    }

    private void setAimMode(boolean enabled) {
        if (aimModeActive == enabled) {
            return;
        }
        aimModeActive = enabled;
        rebuildActionButtonsBar();
        applyContentLayout();
        mapView.setVisibility(enabled ? View.GONE : View.VISIBLE);
        mirrorView.setVisibility(enabled ? View.VISIBLE : View.GONE);
        mirrorView.setMirrorActive(enabled);
    }

    private void buildSettingsPage(FrameLayout appRoot) {
        settingsPage = new LinearLayout(this);
        settingsPage.setOrientation(LinearLayout.VERTICAL);
        settingsPage.setBackgroundColor(Color.rgb(16, 20, 24));
        settingsPage.setVisibility(View.GONE);
        appRoot.addView(settingsPage, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(10), dp(8), dp(10), dp(8));
        header.setBackgroundColor(Color.rgb(20, 25, 30));
        settingsPage.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        Button backButton = new Button(this);
        backButton.setText("< \u8fd4\u56de");
        backButton.setTextSize(14);
        backButton.setAllCaps(false);
        backButton.setOnClickListener(v -> hideSettingsPage());
        header.addView(backButton, new LinearLayout.LayoutParams(dp(92), dp(44)));

        TextView title = new TextView(this);
        title.setText("\u6309\u952e\u8bbe\u7f6e");
        title.setTextColor(Color.WHITE);
        title.setTextSize(18);
        title.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, dp(44), 1f);
        titleParams.setMargins(dp(12), 0, 0, 0);
        header.addView(title, titleParams);

        Button addButton = new Button(this);
        addButton.setText("\u65b0\u589e");
        addButton.setTextSize(13);
        addButton.setAllCaps(false);
        addButton.setOnClickListener(v -> addActionButton());
        header.addView(addButton, new LinearLayout.LayoutParams(dp(76), dp(44)));

        Button deleteButton = new Button(this);
        deleteButton.setText("\u5220\u9664");
        deleteButton.setTextSize(13);
        deleteButton.setAllCaps(false);
        deleteButton.setOnClickListener(v -> deleteSelectedActionButton());
        LinearLayout.LayoutParams deleteParams = new LinearLayout.LayoutParams(dp(76), dp(44));
        deleteParams.setMargins(dp(6), 0, 0, 0);
        header.addView(deleteButton, deleteParams);

        ScrollView tableScroll = new ScrollView(this);
        tableScroll.setFillViewport(false);
        settingsPage.addView(tableScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));

        LinearLayout table = new LinearLayout(this);
        table.setOrientation(LinearLayout.VERTICAL);
        table.setPadding(dp(12), dp(12), dp(12), 0);
        tableScroll.addView(table, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        settingsTable = table;
        rebuildSettingsTable();

        TextView help = new TextView(this);
        help.setText("\u70b9\u7b2c\u4e8c\u5217\u5355\u5143\u683c\u8f93\u5165\u6309\u952e\uff0c\u4f8b\u5982 Z\u3001F\u3001SPACE\u3001ENTER\u3002\u7559\u7a7a\u8868\u793a\u672a\u8bbe\u7f6e\u3002");
        help.setTextColor(Color.rgb(145, 155, 165));
        help.setTextSize(12);
        help.setPadding(dp(16), dp(12), dp(16), 0);
        settingsPage.addView(help, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
    }

    private void addSettingsHeaderRow(LinearLayout table) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundColor(Color.rgb(32, 38, 45));
        table.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(42)
        ));
        row.addView(settingsHeaderCell("\u6309\u94ae\u540d\u79f0"),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        row.addView(settingsHeaderCell("\u6620\u5c04\u6309\u952e"),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
    }

    private TextView settingsHeaderCell(String text) {
        TextView cell = new TextView(this);
        cell.setText(text);
        cell.setTextColor(Color.rgb(175, 185, 195));
        cell.setTextSize(13);
        cell.setGravity(Gravity.CENTER_VERTICAL);
        cell.setPadding(dp(12), 0, dp(12), 0);
        return cell;
    }

    private void rebuildSettingsTable() {
        if (settingsTable == null) {
            return;
        }
        settingsTable.removeAllViews();
        actionRowBindings.clear();
        addSettingsHeaderRow(settingsTable);
        for (int i = 0; i < actionButtons.size(); i++) {
            addActionSettingsRow(settingsTable, i, actionButtons.get(i));
        }
        if (selectedActionIndex < 0 && !actionButtons.isEmpty()) {
            selectedActionIndex = 0;
        }
        if (selectedActionIndex >= actionButtons.size()) {
            selectedActionIndex = actionButtons.size() - 1;
        }
        updateSelectedActionRow();
    }

    private void addActionSettingsRow(LinearLayout table, int index, ActionButton action) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundColor(Color.rgb(24, 29, 34));
        row.setOnClickListener(v -> selectActionRow(index));
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(54)
        );
        rowParams.setMargins(0, dp(1), 0, 0);
        table.addView(row, rowParams);

        EditText nameCell = new EditText(this);
        nameCell.setSingleLine(true);
        nameCell.setText(action.name);
        nameCell.setHint("\u6309\u94ae");
        nameCell.setTextColor(Color.WHITE);
        nameCell.setHintTextColor(Color.rgb(120, 130, 140));
        nameCell.setTextSize(15);
        nameCell.setGravity(Gravity.CENTER_VERTICAL);
        nameCell.setSelectAllOnFocus(true);
        nameCell.setPadding(dp(12), 0, dp(12), 0);
        nameCell.setInputType(InputType.TYPE_CLASS_TEXT);
        nameCell.setBackgroundColor(Color.rgb(24, 29, 34));
        nameCell.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                selectActionRow(index);
            }
        });
        row.addView(nameCell, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        EditText keyCell = new EditText(this);
        keyCell.setSingleLine(true);
        keyCell.setText(action.key == null ? "" : action.key);
        keyCell.setHint("\u672a\u8bbe\u7f6e");
        keyCell.setTextColor(Color.WHITE);
        keyCell.setHintTextColor(Color.rgb(120, 130, 140));
        keyCell.setTextSize(15);
        keyCell.setGravity(Gravity.CENTER_VERTICAL);
        keyCell.setSelectAllOnFocus(true);
        keyCell.setPadding(dp(12), 0, dp(12), 0);
        keyCell.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        keyCell.setBackgroundColor(Color.rgb(30, 36, 42));
        keyCell.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                selectActionRow(index);
            }
        });
        row.addView(keyCell, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        actionRowBindings.add(new ActionRowBinding(row, nameCell, keyCell));
    }

    private void selectActionRow(int index) {
        selectedActionIndex = index;
        updateSelectedActionRow();
    }

    private void updateSelectedActionRow() {
        for (int i = 0; i < actionRowBindings.size(); i++) {
            ActionRowBinding binding = actionRowBindings.get(i);
            int color = i == selectedActionIndex ? Color.rgb(43, 57, 72) : Color.rgb(24, 29, 34);
            binding.row.setBackgroundColor(color);
            binding.nameCell.setBackgroundColor(color);
        }
    }

    private void addActionButton() {
        saveSettingsPageEdits(false);
        ActionButton action = new ActionButton(
                "custom_" + SystemClock.elapsedRealtime(),
                "\u65b0\u6309\u94ae",
                "",
                "key"
        );
        actionButtons.add(action);
        selectedActionIndex = actionButtons.size() - 1;
        rebuildSettingsTable();
        rebuildActionButtonsBar();
        saveActionButtons();
    }

    private void deleteSelectedActionButton() {
        saveSettingsPageEdits(false);
        if (selectedActionIndex < 0 || selectedActionIndex >= actionButtons.size()) {
            Toast.makeText(this, "\u5148\u9009\u4e2d\u4e00\u4e2a\u6309\u94ae", Toast.LENGTH_SHORT).show();
            return;
        }
        ActionButton action = actionButtons.get(selectedActionIndex);
        if (action.isAim()) {
            Toast.makeText(this, "\u7784\u51c6\u662f\u6295\u5c4f\u5165\u53e3\uff0c\u5148\u4e0d\u5141\u8bb8\u5220\u9664", Toast.LENGTH_SHORT).show();
            return;
        }
        actionButtons.remove(selectedActionIndex);
        if (selectedActionIndex >= actionButtons.size()) {
            selectedActionIndex = actionButtons.size() - 1;
        }
        rebuildSettingsTable();
        rebuildActionButtonsBar();
        saveActionButtons();
    }

    private void showSettingsPage() {
        rebuildSettingsTable();
        if (mainPage != null) {
            mainPage.setVisibility(View.GONE);
        }
        if (settingsPage != null) {
            settingsPage.setVisibility(View.VISIBLE);
        }
    }

    private void hideSettingsPage() {
        saveSettingsPage();
        hideSoftKeyboard();
        if (settingsPage != null) {
            settingsPage.setVisibility(View.GONE);
        }
        if (mainPage != null) {
            mainPage.setVisibility(View.VISIBLE);
        }
    }

    private void saveSettingsPage() {
        saveSettingsPageEdits(true);
        rebuildActionButtonsBar();
        saveActionButtons();
    }

    private void saveSettingsPageEdits(boolean showInvalidToast) {
        int count = Math.min(actionButtons.size(), actionRowBindings.size());
        for (int i = 0; i < count; i++) {
            ActionButton action = actionButtons.get(i);
            ActionRowBinding binding = actionRowBindings.get(i);

            String name = binding.nameCell.getText().toString().trim();
            if (name.length() == 0) {
                name = "\u6309\u94ae" + (i + 1);
            }
            action.name = name;

            String rawKey = binding.keyCell.getText().toString();
            if (rawKey == null || rawKey.trim().isEmpty()) {
                action.key = "";
                continue;
            }
            String normalized = RootInputBridge.normalizeKeyName(rawKey);
            if (normalized == null || !RootInputBridge.isSupportedKey(normalized)) {
                if (showInvalidToast) {
                    Toast.makeText(this, action.name + " \u6309\u952e\u65e0\u6548\uff0c\u5df2\u4fdd\u6301 " + action.key, Toast.LENGTH_SHORT).show();
                }
                binding.keyCell.setText(action.key);
            } else {
                action.key = normalized;
                binding.keyCell.setText(normalized);
            }
        }
        syncLegacyActionKeys();
    }

    private void triggerMappedKey(String key, String actionName, boolean warnIfEmpty) {
        String normalized = RootInputBridge.normalizeKeyName(key);
        if (normalized == null || normalized.length() == 0) {
            if (warnIfEmpty) {
                Toast.makeText(this, actionName + " \u8fd8\u6ca1\u6709\u8bbe\u7f6e\u6309\u952e", Toast.LENGTH_SHORT).show();
            }
            return;
        }
        RootInputBridge.sendKey(normalized);
    }

    private void hideSoftKeyboard() {
        ipInput.clearFocus();
        if (rootLayout != null) {
            rootLayout.requestFocus();
        }
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(ipInput.getWindowToken(), 0);
            if (aimButton != null) {
                imm.hideSoftInputFromWindow(aimButton.getWindowToken(), 0);
            }
            View decorView = getWindow().getDecorView();
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
        if (aimModeActive) {
            flightPanel.setVisibility(View.GONE);
            mapParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
            mapParams.height = ViewGroup.LayoutParams.MATCH_PARENT;
            mapParams.weight = 0.0f;
        } else if (aircraftLayoutActive) {
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

    private static final class ActionButton {
        final String id;
        String name;
        String key;
        final String type;

        ActionButton(String id, String name, String key, String type) {
            this.id = id == null || id.length() == 0 ? "custom" : id;
            this.name = name == null || name.trim().length() == 0 ? "\u6309\u94ae" : name;
            this.key = key == null ? "" : key;
            this.type = type == null || type.length() == 0 ? "key" : type;
        }

        boolean isAim() {
            return "aim".equals(id) || "aim".equals(type);
        }
    }

    private static final class ActionRowBinding {
        final LinearLayout row;
        final EditText nameCell;
        final EditText keyCell;

        ActionRowBinding(LinearLayout row, EditText nameCell, EditText keyCell) {
            this.row = row;
            this.nameCell = nameCell;
            this.keyCell = keyCell;
        }
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
