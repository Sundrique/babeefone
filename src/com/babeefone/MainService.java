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
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.UUID;

public class MainService extends Service {
    protected static final String NAME = "Babeefone";
    protected static final UUID BABEEFONE_UUID = UUID.fromString("53735fb0-b328-11e2-9e96-0800200c9a66");

    private static final String PREFERENCES_NAME = "Babeefone";

    protected static final int SAMPLE_RATE = 44100;
    protected static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;

    public static final int STATE_NONE = 0;       // we're doing nothing
    public static final int STATE_LISTEN = 1;     // now listening for incoming connections
    public static final int STATE_CONNECTING = 2; // now initiating an outgoing connection
    public static final int STATE_CONNECTED = 3;  // now connected to a remote device

    private static final int CONNECTING_PAIRED = 0;
    private static final int CONNECTING_STORED = 1;
    private static final int CONNECTING_AVAILABLE = 2;

    public static final int MODE_PARENT = 0;
    public static final int MODE_BABY = 1;

    public static final String BROADCAST_ACTION = "com.babeefone.MainService";

    private static final long DISCOVERY_DURATION = 7000;

    private int state = STATE_NONE;
    private int mode = MODE_PARENT;
    private int connecting;

    private final Handler handler = new Handler();

    private AcceptThread acceptThread;
    private ConnectedThread connectedThread;
    private RecordThread recordThread;
    private ArrayList<ConnectThread> connectThreads = new ArrayList<ConnectThread>();
    private ArrayList<BluetoothDevice> availableDevices = new ArrayList<BluetoothDevice>();

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothSocket bluetoothSocket;
    private BluetoothDevice connectedDevice;
    private ObjectOutputStream objectOutputStream;

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
        cancelConnectThreads();
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

    private synchronized void setState(int state) {
        if (state != this.state) {
            this.state = state;

            broadcast(BootstrapActivity.MESSAGE_STATE_CHANGE);
        }
    }

    public synchronized int getState() {
        return state;
    }

    protected BluetoothAdapter getBluetoothAdapter() {
        return bluetoothAdapter;
    }

    protected BluetoothSocket getBluetoothSocket() {
        return bluetoothSocket;
    }

    protected BluetoothDevice getConnectedDevice() {
        return connectedDevice;
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
        if (state == STATE_CONNECTED) {
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
        setState(STATE_NONE);

        connectedDevice = null;

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

    private void cancelConnectThreads() {
        ConnectThread connectThread;
        while (connectThreads.size() > 0) {
            connectThread = connectThreads.get(0);
            connectThread.cancel();
            connectThreads.remove(connectThread);
        }
    }

    protected void connectionFailed(ConnectThread thread) {
        connectThreads.remove(thread);
        if (connectThreads.size() == 0) {
            if (state != STATE_CONNECTED) {
                if (connecting == CONNECTING_PAIRED) {
                    connectStored();
                } else if (connecting == CONNECTING_STORED) {
                    connectAvailable();
                } else {
                    connectPaired();
                }
            }
        }
    }

    protected void connectionLost() {
        disconnect();

        disconnected();
    }

    public synchronized void connected(BluetoothSocket socket, BluetoothDevice device) {
        if (state != STATE_CONNECTED) {
            bluetoothSocket = socket;

            connectedDevice = device;

            setState(STATE_CONNECTED);

            cancelConnectThreads();

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

    public synchronized void connect(BluetoothDevice bluetoothDevice) {
        setState(STATE_CONNECTING);

        ConnectThread connectThread = new ConnectThread(this, bluetoothDevice);
        connectThread.start();
        connectThreads.add(connectThread);
    }

    private synchronized void disconnected() {
        connectPaired();

        if (acceptThread == null) {
            acceptThread = new AcceptThread(this);
            acceptThread.start();
        }
    }

    private synchronized void connectPaired() {
        if (state != STATE_CONNECTED) {
            connecting = CONNECTING_PAIRED;
            if (bluetoothAdapter.getBondedDevices().size() > 0) {
                for (BluetoothDevice bluetoothDevice : bluetoothAdapter.getBondedDevices()) {
                    connect(bluetoothDevice);
                }
            } else {
                connectStored();
            }
        }
    }

    private synchronized void connectStored() {
        if (state != STATE_CONNECTED) {
            connecting = CONNECTING_STORED;
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
                    connect(bluetoothAdapter.getRemoteDevice(address));
                    cursor.moveToNext();
                }
            } else {
                connectAvailable();
            }
        }
    }

    private synchronized void connectAvailable() {
        if (state != STATE_CONNECTED) {
            connecting = CONNECTING_AVAILABLE;
            availableDevices.clear();
            bluetoothAdapter.startDiscovery();
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (bluetoothAdapter.isDiscovering()) {
                        bluetoothAdapter.cancelDiscovery();
                    }
                }
            }, DISCOVERY_DURATION);
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
    }

    private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device.getBondState() != BluetoothDevice.BOND_BONDED) {
                    availableDevices.add(device);
                }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                if (state != STATE_CONNECTED) {
                    if (availableDevices.size() > 0) {
                        for (BluetoothDevice bluetoothDevice : availableDevices) {
                            connect(bluetoothDevice);
                        }
                    } else {
                        connectPaired();
                    }
                }
            }
        }
    };
}
