package com.babeefone;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioFormat;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.UUID;

public class MainService extends Service {
    protected static final String NAME = "Babeefone";
    protected static final UUID MY_UUID = UUID.fromString("53735fb0-b328-11e2-9e96-0800200c9a66");

    private static final String PREFERENCES_NAME = "Babeefone";

    protected static final int SAMPLE_RATE = 44100;
    protected static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;

    public static final int STATE_NONE = 0;       // we're doing nothing
    public static final int STATE_LISTEN = 1;     // now listening for incoming connections
    public static final int STATE_CONNECTING = 2; // now initiating an outgoing connection
    public static final int STATE_CONNECTED = 3;  // now connected to a remote device

    public static final int MODE_PARENT = 0;
    public static final int MODE_BABY = 1;

    public static final String BROADCAST_ACTION = "com.babeefone.MainService";

    private int state = STATE_NONE;
    private int mode = MODE_PARENT;

    private AcceptThread acceptThread;
    private ConnectedThread connectedThread;
    private RecordThread recordThread;
    private ArrayList<ConnectThread> connectThreads = new ArrayList<ConnectThread>();

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothSocket bluetoothSocket;
    private BluetoothDevice connectedDevice;
    private ObjectOutputStream objectOutputStream;

    public static boolean isStarted = false;

    @Override
    public void onCreate() {
        super.onCreate();

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        isStarted = true;

        SharedPreferences settings = getSharedPreferences(PREFERENCES_NAME, 0);
        int settingsMode = settings.getInt("mode", MODE_PARENT);
        setMode(settingsMode);

        disconnected();

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

        isStarted = false;

        cancelRecordThread();
        cancelConnectThreads();
        cancelConnectedThread();
        cancelAcceptThread();

        try {
            if (objectOutputStream != null) {
                objectOutputStream.close();
            }
        } catch (IOException e) {
            // todo log exception
        }
    }


    private synchronized void setState(int state) {
        this.state = state;

        broadcast(BootstrapActivity.MESSAGE_STATE_CHANGE);
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

    private void cancelConnectedThread() {
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
                setState(STATE_LISTEN);
            }
        }
    }

    protected void connectionLost() {
        cancelConnectedThread();

        disconnected();
    }

    public synchronized void connected(BluetoothSocket socket, BluetoothDevice device) {
        if (state != STATE_CONNECTED) {
            cancelConnectThreads();

            cancelAcceptThread();

            this.bluetoothSocket = socket;

            try {
                objectOutputStream = new ObjectOutputStream(new BufferedOutputStream(bluetoothSocket.getOutputStream()));
            } catch (IOException e) {
                connectionLost();
            }

            connectedThread = new ConnectedThread(this);
            connectedThread.start();

            connectedDevice = device;

            setState(STATE_CONNECTED);
        }
    }

    private synchronized void disconnected() {
        setState(STATE_CONNECTING);

        ConnectThread connectThread;
        for (BluetoothDevice bluetoothDevice : bluetoothAdapter.getBondedDevices()) {
            connectThread = new ConnectThread(this, bluetoothDevice);
            connectThread.start();
            connectThreads.add(connectThread);
        }

        if (acceptThread == null) {
            acceptThread = new AcceptThread(this);
            acceptThread.start();
        }
    }
}
