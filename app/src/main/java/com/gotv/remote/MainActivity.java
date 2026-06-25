package com.gotv.remote;

import android.bluetooth.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import java.util.*;

public class MainActivity extends AppCompatActivity {

    private BluetoothAdapter btAdapter;
    private BluetoothHidDevice hidDevice;
    private BluetoothDevice targetDevice;
    private TextView statusText;

    private static final byte[] HID_DESCRIPTOR = {
        0x05, 0x01, 0x09, 0x06, (byte)0xa1, 0x01,
        0x05, 0x07, 0x19, (byte)0xe0, 0x29, (byte)0xe7,
        0x15, 0x00, 0x25, 0x01, 0x75, 0x01, (byte)0x95, 0x08,
        (byte)0x81, 0x02, (byte)0x95, 0x01, 0x75, 0x08,
        (byte)0x81, 0x03, (byte)0x95, 0x05, 0x75, 0x01,
        0x05, 0x08, 0x19, 0x01, 0x29, 0x05,
        (byte)0x91, 0x02, (byte)0x95, 0x01, 0x75, 0x03,
        (byte)0x91, 0x03, (byte)0x95, 0x06, 0x75, 0x08,
        0x15, 0x00, 0x25, (byte)0x65, 0x05, 0x07,
        0x19, 0x00, 0x29, (byte)0x65, (byte)0x81, 0x00,
        (byte)0xc0
    };

    // Keycodes
    static final byte KEY_UP = 0x52, KEY_DOWN = 0x51;
    static final byte KEY_LEFT = 0x50, KEY_RIGHT = 0x4F;
    static final byte KEY_ENTER = 0x28, KEY_ESC = 0x29;
    static final byte KEY_HOME = 0x4A;

    private final BluetoothHidDevice.Callback hidCallback = new BluetoothHidDevice.Callback() {
        @Override
        public void onConnectionStateChanged(BluetoothDevice device, int state) {
            runOnUiThread(() -> {
                if (state == BluetoothProfile.STATE_CONNECTED) {
                    targetDevice = device;
                    statusText.setText("מחובר: " + device.getName());
                } else {
                    targetDevice = null;
                    statusText.setText("מנותק");
                }
            });
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        btAdapter = BluetoothAdapter.getDefaultAdapter();
        buildUI();
        registerHid();
    }

    private void registerHid() {
        BluetoothHidDevice.AppSdpSettings sdp = new BluetoothHidDevice.AppSdpSettings(
            "GoTV Remote", "BT Remote", "GoTV",
            BluetoothHidDevice.SUBCLASS1_COMBO, HID_DESCRIPTOR
        );
        btAdapter.getProfileProxy(this, new BluetoothProfile.ServiceListener() {
            public void onServiceConnected(int profile, BluetoothProfile proxy) {
                hidDevice = (BluetoothHidDevice) proxy;
                hidDevice.registerApp(sdp, null, null, getMainExecutor(), hidCallback);
            }
            public void onServiceDisconnected(int profile) {}
        }, BluetoothProfile.HID_DEVICE);
    }

    private void sendKey(byte keyCode) {
        if (hidDevice == null || targetDevice == null) {
            Toast.makeText(this, "לא מחובר", Toast.LENGTH_SHORT).show();
            return;
        }
        byte[] down = {0, 0, keyCode, 0, 0, 0, 0, 0};
        byte[] up   = {0, 0, 0, 0, 0, 0, 0, 0};
        hidDevice.sendReport(targetDevice, 1, down);
        new Handler().postDelayed(() -> hidDevice.sendReport(targetDevice, 1, up), 50);
    }

    private void showDevicePicker() {
        Set<BluetoothDevice> paired = btAdapter.getBondedDevices();
        if (paired.isEmpty()) {
            Toast.makeText(this, "אין מכשירים מקושרים", Toast.LENGTH_LONG).show();
            return;
        }
        String[] names = paired.stream().map(BluetoothDevice::getName).toArray(String[]::new);
        BluetoothDevice[] devices = paired.toArray(new BluetoothDevice[0]);
        new android.app.AlertDialog.Builder(this)
            .setTitle("בחר סטרימר")
            .setItems(names, (d, i) -> {
                hidDevice.connect(devices[i]);
                statusText.setText("מתחבר ל-" + names[i] + "...");
            }).show();
    }

    private void buildUI() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(40, 60, 40, 60);
        root.setBackgroundColor(0xFF0d0d0f);

        statusText = new TextView(this);
        statusText.setText("מנותק");
        statusText.setTextColor(0xFF888888);
        statusText.setTextSize(14);
        statusText.setGravity(Gravity.CENTER);
        root.addView(statusText);

        root.addView(makeBtn("🔗 התחבר לסטרימר", 0xFFe8412a, v -> showDevicePicker()));
        root.addView(spacer(24));

        // D-Pad
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(3);
        grid.setRowCount(3);
        addGrid(grid, "", 0, 0, null);
        addGrid(grid, "▲", 0, 1, v -> sendKey(KEY_UP));
        addGrid(grid, "", 0, 2, null);
        addGrid(grid, "◀", 1, 0, v -> sendKey(KEY_LEFT));
        addGrid(grid, "OK", 1, 1, v -> sendKey(KEY_ENTER));
        addGrid(grid, "▶", 1, 2, v -> sendKey(KEY_RIGHT));
        addGrid(grid, "", 2, 0, null);
        addGrid(grid, "▼", 2, 1, v -> sendKey(KEY_DOWN));
        addGrid(grid, "", 2, 2, null);
        root.addView(grid);

        root.addView(spacer(24));
        root.addView(makeBtn("↩ חזור", 0xFF242430, v -> sendKey(KEY_ESC)));
        root.addView(spacer(8));
        root.addView(makeBtn("⌂ בית", 0xFF242430, v -> sendKey(KEY_HOME)));

        scroll.addView(root);
        setContentView(scroll);
    }

    private Button makeBtn(String text, int color, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(text);
        b.setBackgroundColor(color);
        b.setTextColor(0xFFFFFFFF);
        b.setTextSize(16);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 140);
        lp.setMargins(0, 8, 0, 8);
        b.setLayoutParams(lp);
        b.setOnClickListener(l);
        return b;
    }

    private void addGrid(GridLayout g, String text, int row, int col, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(20);
        b.setTextColor(0xFFFFFFFF);
        b.setBackgroundColor(text.equals("OK") ? 0xFFe8412a : 0xFF242430);
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams(
            GridLayout.spec(row), GridLayout.spec(col));
        lp.width = 200; lp.height = 200;
        lp.setMargins(8, 8, 8, 8);
        b.setLayoutParams(lp);
        if (l != null) b.setOnClickListener(l);
        else b.setEnabled(false);
        g.addView(b);
    }

    private View spacer(int dp) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp));
        return v;
    }
}
