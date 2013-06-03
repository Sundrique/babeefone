package com.babeefone;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.*;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.FragmentActivity;
import android.view.Window;
import android.widget.TextView;
import android.widget.Toast;

public class BootstrapActivity extends FragmentActivity {
    public static final int MESSAGE_STATE_CHANGE = 0;
    public static final int MESSAGE_MODE_CHANGE = 1;

    private static final int REQUEST_ENABLE_BT = 0;

    private TextView title;

    private BluetoothAdapter bluetoothAdapter = null;
    private MainService mainService = null;
    private Intent serviceIntent;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        serviceIntent = new Intent(this, MainService.class);

        requestWindowFeature(Window.FEATURE_CUSTOM_TITLE);
        setContentView(R.layout.main);
        getWindow().setFeatureInt(Window.FEATURE_CUSTOM_TITLE, R.layout.custom_title);

        title = (TextView) findViewById(R.id.title_left_text);
        title.setText(R.string.app_name);
        title = (TextView) findViewById(R.id.title_right_text);

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
            initialize();
        }
    }

    @Override
    public void onStop() {
        super.onStop();

        unbindService(serviceConnection);

        unregisterReceiver(broadcastReceiver);
    }

    private void initialize() {
        if (!MainService.started) {
            startService(serviceIntent);
        }
    }

    public void exit() {
        stopService(serviceIntent);
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int type = intent.getIntExtra("type", 0);
            if (type == MESSAGE_STATE_CHANGE) {
                title.setText(mainService.getConnected() ? R.string.title_connected : R.string.title_connecting);
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

    public MainService getMainService() {
        return mainService;
    }
}