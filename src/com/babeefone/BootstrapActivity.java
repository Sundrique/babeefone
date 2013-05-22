package com.babeefone;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.*;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class BootstrapActivity extends Activity {
    public static final int MESSAGE_STATE_CHANGE = 0;
    public static final int MESSAGE_MODE = 1;
    public static final int MESSAGE_DEVICE_NAME = 2;

    public static final String DEVICE_NAME = "device_name";
    public static final String MODE = "mode";
    public static final String STATE = "state";

    private static final int REQUEST_ENABLE_BT = 0;

    private TextView mTitle;
    private Button modeButton;
    private Button exitButton;

    private String connectedDeviceName = null;
    private BluetoothAdapter bluetoothAdapter = null;
    private MainService mainService = null;

    Intent serviceIntent;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        serviceIntent = new Intent(this, MainService.class);

        requestWindowFeature(Window.FEATURE_CUSTOM_TITLE);
        setContentView(R.layout.main);
        getWindow().setFeatureInt(Window.FEATURE_CUSTOM_TITLE, R.layout.custom_title);

        mTitle = (TextView) findViewById(R.id.title_left_text);
        mTitle.setText(R.string.app_name);
        mTitle = (TextView) findViewById(R.id.title_right_text);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (bluetoothAdapter == null) {
            Toast.makeText(this, R.string.bt_not_available, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        setVolumeControlStream(AudioManager.STREAM_MUSIC);
    }

    @Override
    public void onStart() {
        super.onStart();

        registerReceiver(broadcastReceiver, new IntentFilter(MainService.BROADCAST_ACTION));

        bindService(serviceIntent, serviceConnection, 0);

        if (!bluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        } else {
            if (!MainService.isStarted) {
                initialize();
            }
        }
    }

    @Override
    public void onStop() {
        super.onStop();

        unbindService(serviceConnection);

        unregisterReceiver(broadcastReceiver);
    }

    private void initialize() {
        modeButton = (Button) findViewById(R.id.parent);
        modeButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                mainService.setMode(mainService.getMode() == MainService.MODE_PARENT ? MainService.MODE_BABY : MainService.MODE_PARENT);
                mainService.setConnectedDeviceMode();
            }
        });

        exitButton = (Button) findViewById(R.id.exit);
        exitButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                stopService(serviceIntent);
                Intent intent = new Intent(Intent.ACTION_MAIN);
                intent.addCategory(Intent.CATEGORY_HOME);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                finish();
            }
        });

        startService(serviceIntent);
    }

    private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int type = intent.getIntExtra("type", 0);
            Bundle bundle = intent.getBundleExtra("data");
            switch (type) {
                case MESSAGE_STATE_CHANGE:
                    switch (bundle.getInt(STATE)) {
                        case MainService.STATE_CONNECTED:
                            mTitle.setText(R.string.title_connected_to);
                            mTitle.append(connectedDeviceName);
                            break;
                        case MainService.STATE_CONNECTING:
                            mTitle.setText(R.string.title_connecting);
                            break;
                        case MainService.STATE_LISTEN:
                        case MainService.STATE_NONE:
                            mTitle.setText(R.string.title_not_connected);
                            break;
                    }
                    break;
                case MESSAGE_DEVICE_NAME:
                    connectedDeviceName = bundle.getString(DEVICE_NAME);
                    Toast.makeText(getApplicationContext(), "Connected to " + connectedDeviceName, Toast.LENGTH_SHORT).show();
                    break;
                case MESSAGE_MODE:
                    int mode = bundle.getInt(MODE);
                    if (mode == MainService.MODE_BABY) {
                        modeButton.setText(R.string.parent);
                    } else {
                        modeButton.setText(R.string.baby);
                    }
                    break;
            }

        }
    };

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mainService = ((ServiceBinder) service).getService();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            // do nothing
        }
    };

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == Activity.RESULT_OK) {
                initialize();
            } else {
                Toast.makeText(this, R.string.bt_not_enabled_leaving, Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }
}