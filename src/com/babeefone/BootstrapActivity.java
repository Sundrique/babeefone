package com.babeefone;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

/**
 * This is the main Activity that displays the current chat session.
 */
public class BootstrapActivity extends Activity {
    public static final int MESSAGE_STATE_CHANGE = 0;
    public static final int MESSAGE_MODE = 1;
    public static final int MESSAGE_DEVICE_NAME = 2;
    public static final int MESSAGE_TOAST = 3;

    public static final String DEVICE_NAME = "device_name";
    public static final String TOAST = "toast";
    public static final String MODE = "mode";

    private static final int REQUEST_ENABLE_BT = 0;

    private TextView mTitle;
    private Button modeButton;
    private Button exitButton;

    private String connectedDeviceName = null;
    private BluetoothAdapter bluetoothAdapter = null;
    private BabeefoneService babeefoneService = null;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_CUSTOM_TITLE);
        setContentView(R.layout.main);
        getWindow().setFeatureInt(Window.FEATURE_CUSTOM_TITLE, R.layout.custom_title);

        mTitle = (TextView) findViewById(R.id.title_left_text);
        mTitle.setText(R.string.app_name);
        mTitle = (TextView) findViewById(R.id.title_right_text);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        setVolumeControlStream(AudioManager.STREAM_MUSIC);
    }

    @Override
    public void onStart() {
        super.onStart();

        if (!bluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        } else {
            if (babeefoneService == null) {
                setupChat();
            }
        }
    }

    @Override
    public synchronized void onResume() {
        super.onResume();

        if (babeefoneService != null) {
            if (babeefoneService.getState() == BabeefoneService.STATE_NONE) {
                babeefoneService.start();
            }
        }
    }

    private void setupChat() {
        modeButton = (Button) findViewById(R.id.parent);
        modeButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                babeefoneService.setMode(babeefoneService.getMode() == BabeefoneService.MODE_PARENT ? BabeefoneService.MODE_BABY : BabeefoneService.MODE_PARENT);
                babeefoneService.setConnectedDeviceMode();
            }
        });

        exitButton = (Button) findViewById(R.id.exit);
        exitButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                babeefoneService.stop();
                Intent intent = new Intent(Intent.ACTION_MAIN);
                intent.addCategory(Intent.CATEGORY_HOME);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                finish();
            }
        });

        babeefoneService = new BabeefoneService(this, mHandler);
    }

    // The Handler that gets information back from the BabeefoneService
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_STATE_CHANGE:
                    switch (msg.arg1) {
                        case BabeefoneService.STATE_CONNECTED:
                            mTitle.setText(R.string.title_connected_to);
                            mTitle.append(connectedDeviceName);
                            break;
                        case BabeefoneService.STATE_CONNECTING:
                            mTitle.setText(R.string.title_connecting);
                            break;
                        case BabeefoneService.STATE_LISTEN:
                        case BabeefoneService.STATE_NONE:
                            mTitle.setText(R.string.title_not_connected);
                            break;
                    }
                    break;
                case MESSAGE_DEVICE_NAME:
                    connectedDeviceName = msg.getData().getString(DEVICE_NAME);
                    Toast.makeText(getApplicationContext(), "Connected to " + connectedDeviceName, Toast.LENGTH_SHORT).show();
                    break;
                case MESSAGE_TOAST:
                    Toast.makeText(getApplicationContext(), msg.getData().getString(TOAST), Toast.LENGTH_SHORT).show();
                    break;
                case MESSAGE_MODE:
                    int mode = msg.getData().getInt(MODE);
                    if (mode == BabeefoneService.MODE_BABY) {
                        modeButton.setText(R.string.parent);
                    } else {
                        modeButton.setText(R.string.baby);
                    }
            }
        }
    };

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_ENABLE_BT:
                if (resultCode == Activity.RESULT_OK) {
                    setupChat();
                } else {
                    Toast.makeText(this, R.string.bt_not_enabled_leaving, Toast.LENGTH_SHORT).show();
                    finish();
                }
        }
    }

	/*private void connectDevice(Intent data*//*, boolean secure*//*) {
        String address = data.getExtras().getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
		BluetoothDevice device = bluetoothAdapter.getRemoteDevice(address);
		babeefoneService.connect(device*//*, secure*//*);
	}*/
}