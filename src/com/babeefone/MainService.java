package com.babeefone;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.*;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.media.AudioFormat;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

public class MainService extends Service {
    protected static final String NAME = "Babeefone";
    protected static final UUID BABEEFONE_UUID = UUID.fromString("53735fb0-b328-11e2-9e96-0800200c9a66");

    private static final String PREFERENCES_NAME = "Babeefone";

    protected static final int SAMPLE_RATE = 44100;
    protected static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;

    public static final int MODE_PARENT = 0;
    public static final int MODE_BABY = 1;

    public static final String BROADCAST_ACTION = "com.babeefone.MainService";

    private static final long DISCOVERY_DURATION = 7000;
    private static final long DISCOVERY_PAUSE = 3000;

    private int mode = MODE_PARENT;

    private AcceptThread acceptThread;
    private ConnectedThread connectedThread;
    private RecordThread recordThread;
    private DisconnectedThread disconnectedThread;
    private ArrayList<BluetoothDevice> knownDevices = new ArrayList<BluetoothDevice>();

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothSocket bluetoothSocket;
    private ObjectOutputStream objectOutputStream;

    private boolean connected = false;

    public static boolean started = false;

    @Override
    public void onCreate() {
        super.onCreate();

        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        registerReceiver(broadcastReceiver, filter);

        filter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        registerReceiver(broadcastReceiver, filter);

        filter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        registerReceiver(broadcastReceiver, filter);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        for (BluetoothDevice bluetoothDevice : bluetoothAdapter.getBondedDevices()) {
            knownDevices.add(bluetoothDevice);
        }

        SQLiteDatabase db = new DbOpenHelper(this).getWritableDatabase();
        Cursor cursor = db.query(
                true,
                "devices",
                new String[]{"address"},
                null, null, null, null, null, null
        );
        if (cursor.getCount() > 0) {
            cursor.moveToFirst();
            while (cursor.isAfterLast() == false) {
                String address = cursor.getString(cursor.getColumnIndex("address"));
                knownDevices.add(bluetoothAdapter.getRemoteDevice(address));
                cursor.moveToNext();
            }
        }
        db.close();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        started = true;

        initialize();

        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, new Intent(this, BootstrapActivity.class), 0);
        Notification notification = new NotificationCompat.Builder(this)
                .setContentTitle("Title")
                .setContentText("Text")
                .setSmallIcon(R.drawable.app_icon)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setTicker("Running in the Foreground")
                .build();

        startForeground(1, notification);

        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return new ServiceBinder(this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        stopForeground(true);

        started = false;

        cancelRecordThread();
        cancelDisconnectedThread();
        disconnect();
        cancelAcceptThread();

        unregisterReceiver(broadcastReceiver);

        bluetoothAdapter.cancelDiscovery();

        try {
            if (objectOutputStream != null) {
                objectOutputStream.close();
            }
        } catch (IOException e) {
            // todo log exception
        }
    }


    public void initialize() {
        SharedPreferences settings = getSharedPreferences(PREFERENCES_NAME, 0);
        int settingsMode = settings.getInt("mode", MODE_PARENT);
        setMode(settingsMode);

        disconnected();
    }

    protected BluetoothAdapter getBluetoothAdapter() {
        return bluetoothAdapter;
    }

    protected BluetoothSocket getBluetoothSocket() {
        return bluetoothSocket;
    }

    protected synchronized void setConnected(boolean connected) {
        this.connected = connected;

        broadcast(BootstrapActivity.MESSAGE_STATE_CHANGE);
    }

    protected boolean getConnected() {
        return connected;
    }

    public synchronized void setMode(int mode) {
        switch (mode) {
            case MODE_PARENT:
                cancelRecordThread();
                break;
            case MODE_BABY:
                if (recordThread == null) {
                    recordThread = new RecordThread(this);
                    recordThread.start();
                }
                break;
            default:
                throw (new IllegalArgumentException("Unknown mode"));
        }

        broadcast(BootstrapActivity.MESSAGE_MODE_CHANGE);

        this.mode = mode;

        SharedPreferences.Editor editor = getSharedPreferences(PREFERENCES_NAME, 0).edit();
        editor.putInt("mode", mode);
        editor.commit();
    }

    protected synchronized int getMode() {
        return mode;
    }

    private void broadcast(int type) {
        Intent intent = new Intent(BROADCAST_ACTION);
        intent.putExtra("type", type);
        intent.setPackage(getPackageName());
        sendBroadcast(intent);
    }

    public void setConnectedDeviceMode() {
        DataPacket dataPacket = new DataPacket();
        dataPacket.type = DataPacket.TYPE_MODE;
        dataPacket.mode = (mode == MODE_PARENT ? MODE_BABY : MODE_PARENT);

        send(dataPacket);
    }

    public synchronized void send(Object object) {
        if (connected) {
            try {
                if (objectOutputStream == null) {
                    objectOutputStream = new ObjectOutputStream(new BufferedOutputStream(bluetoothSocket.getOutputStream()));
                }
                objectOutputStream.writeObject(object);
                objectOutputStream.flush();
            } catch (IOException e) {
                connectionLost();
            }
        }
    }

    private void cancelAcceptThread() {
        if (acceptThread != null) {
            acceptThread.cancel();
            acceptThread = null;
        }
    }

    protected void disconnect() {
        setConnected(false);

        if (connectedThread != null) {
            connectedThread.cancel();
            connectedThread = null;
        }
    }

    private void cancelRecordThread() {
        if (recordThread != null) {
            recordThread.cancel();
            recordThread = null;
        }
    }

    private void cancelDisconnectedThread() {
        if (disconnectedThread != null) {
            disconnectedThread.cancel();
            disconnectedThread = null;
        }
    }

    protected void connectionLost() {
        disconnect();

        disconnected();
    }

    public synchronized void connected(BluetoothSocket socket, BluetoothDevice device) {
        if (!connected) {
            bluetoothSocket = socket;

            setConnected(true);

            cancelDisconnectedThread();

            cancelAcceptThread();

            bluetoothAdapter.cancelDiscovery();

            storeDevice(device);

            try {
                objectOutputStream = new ObjectOutputStream(new BufferedOutputStream(bluetoothSocket.getOutputStream()));
            } catch (IOException e) {
                connectionLost();
            }

            connectedThread = new ConnectedThread(this);
            connectedThread.start();
        }
    }

    private synchronized void disconnected() {
        if (disconnectedThread == null) {
            disconnectedThread = new DisconnectedThread(this, knownDevices);
            disconnectedThread.start();
        }

        startDiscoveryDelayed();

        if (acceptThread == null) {
            acceptThread = new AcceptThread(this);
            acceptThread.start();
        }
    }

    private void storeDevice(BluetoothDevice device) {
        SQLiteDatabase db = new DbOpenHelper(this).getWritableDatabase();
        Cursor cursor = db.query(
                true,
                "devices",
                new String[]{"address"},
                "address = \"" + device.getAddress() + "\"",
                null, null, null, null, null
        );
        if (cursor.getCount() == 0) {
            ContentValues contentValues = new ContentValues();
            contentValues.put("address", device.getAddress());
            db.insert("devices", null, contentValues);
        }
        db.close();
    }

    private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device.getBondState() != BluetoothDevice.BOND_BONDED) {
                    if (disconnectedThread != null) {
                        knownDevices.add(device);
                        disconnectedThread.addDevice(device);
                    }
                }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                startDiscoveryDelayed();
            }
        }
    };

    private void startDiscoveryDelayed() {
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                if (!bluetoothAdapter.isDiscovering()) {
                    bluetoothAdapter.startDiscovery();

                    new Timer().schedule(new TimerTask() {
                        @Override
                        public void run() {
                            if (bluetoothAdapter.isDiscovering()) {
                                bluetoothAdapter.cancelDiscovery();
                            }
                        }
                    }, DISCOVERY_DURATION);
                }
            }
        }, DISCOVERY_PAUSE);
    }
}
