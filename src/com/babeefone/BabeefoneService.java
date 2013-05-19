package com.babeefone;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioFormat;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.*;

/**
 * This class does all the work for setting up and managing Bluetooth
 * connections with other devices. It has a thread that listens for
 * incoming connections, a thread for connecting with a device, and a
 * thread for performing data transmissions when connected.
 */
public class BabeefoneService {
    protected static final String NAME = "Babeefone";

    protected static final UUID MY_UUID = UUID.fromString("53735fb0-b328-11e2-9e96-0800200c9a66");

    protected static final int SAMPLE_RATE = 44100;
    protected static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;

    private final BluetoothAdapter bluetoothAdapter;
    private BluetoothSocket bluetoothSocket;
    private final Handler handler;

    private ObjectOutputStream objectOutputStream;

    private AcceptThread acceptThread;
    //private ConnectThread connectThread;
    private ArrayList<ConnectThread> connectThreads = new ArrayList<ConnectThread>();
    private ConnectedThread connectedThread;
    private RecordThread recordThread;

    public static final int STATE_NONE = 0;       // we're doing nothing
    public static final int STATE_LISTEN = 1;     // now listening for incoming connections
    public static final int STATE_CONNECTING = 2; // now initiating an outgoing connection
    public static final int STATE_CONNECTED = 3;  // now connected to a remote device

    protected int state;

    public static final int MODE_PARENT = 0;
    public static final int MODE_BABY = 1;

    private int mode = MODE_PARENT;

    private Context context;

    private static final String PREFS_NAME = "Babeefone";

    /**
     * Constructor. Prepares a new BootstrapActivity session.
     *
     * @param context The UI Activity Context
     * @param handler A Handler to send messages back to the UI Activity
     */
    public BabeefoneService(Context context, Handler handler) {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        state = STATE_NONE;
        this.handler = handler;
        this.context = context;
    }

    /**
     * Set the current state of the chat connection
     *
     * @param state An integer defining the current connection state
     */
    private synchronized void setState(int state) {
        this.state = state;

        handler.obtainMessage(BootstrapActivity.MESSAGE_STATE_CHANGE, state, -1).sendToTarget();
    }

    /**
     * Return the current connection state.
     */
    public synchronized int getState() {
        return state;
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

        Message msg = handler.obtainMessage(BootstrapActivity.MESSAGE_MODE);
        Bundle bundle = new Bundle();
        bundle.putInt(BootstrapActivity.MODE, mode);
        msg.setData(bundle);
        handler.sendMessage(msg);

        this.mode = mode;

        SharedPreferences.Editor editor = context.getSharedPreferences(PREFS_NAME, 0).edit();
        editor.putInt("mode", mode);
        editor.commit();
    }

    public void setConnectedDeviceMode() {
        DataPacket dataPacket = new DataPacket();
        dataPacket.type = DataPacket.TYPE_MODE;
        dataPacket.mode = (mode == MODE_PARENT ? MODE_BABY : MODE_PARENT);

        send(dataPacket);
    }

    protected synchronized int getMode() {
        return mode;
    }

    protected BluetoothAdapter getBluetoothAdapter() {
        return bluetoothAdapter;
    }

    /**
     * Start the chat service. Specifically start AcceptThread to begin a
     * session in listening (server) mode. Called by the Activity onResume()
     */
    public synchronized void start() {
        //cancelConnectThreads();

        //cancelConnectedThread();

        SharedPreferences settings = context.getSharedPreferences(PREFS_NAME, 0);
        int settingsMode = settings.getInt("mode", MODE_PARENT);
        setMode(settingsMode);

        disconnected();
    }

    /**
     * Start the ConnectThread to initiate a connection to a remote device.
     *
     * @param device The BluetoothDevice to connect
     */
    public synchronized void connect(BluetoothDevice device) {
        /*if (state == STATE_CONNECTING) {
            cancelConnectThread();
        }*/

        /*cancelConnectedThread();

        connectThread = new ConnectThread(this, device);
        connectThread.start();

        setState(STATE_CONNECTING);*/
    }

    /**
     * Start the ConnectedThread to begin managing a Bluetooth connection
     *
     * @param socket The BluetoothSocket on which the connection was made
     * @param device The BluetoothDevice that has been connected
     */
    public synchronized void connected(BluetoothSocket socket, BluetoothDevice device) {
        if (state != STATE_CONNECTED) {
            cancelConnectThreads();

            //cancelConnectedThread();

            cancelAcceptThread();

            this.bluetoothSocket = socket;

            try {
                objectOutputStream = new ObjectOutputStream(new BufferedOutputStream(bluetoothSocket.getOutputStream()));
            } catch (IOException e) {

            }

            connectedThread = new ConnectedThread(this);
            connectedThread.start();

            Message msg = handler.obtainMessage(BootstrapActivity.MESSAGE_DEVICE_NAME);
            Bundle bundle = new Bundle();
            bundle.putString(BootstrapActivity.DEVICE_NAME, device.getName());
            msg.setData(bundle);
            handler.sendMessage(msg);

            setState(STATE_CONNECTED);
        }
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
                // todo log exception
            }
        }
    }

    /**
     * Stop all threads
     */
    public synchronized void stop() {
        cancelConnectThreads();

        cancelConnectedThread();

        cancelAcceptThread();

        cancelRecordThread();

        try {
            if (objectOutputStream != null) {
                objectOutputStream.close();
            }
        } catch (IOException e) {

        }
    }

    private void cancelAcceptThread() {
        if (acceptThread != null) {
            acceptThread.cancel();
            acceptThread = null;
        }
    }

    /*private void cancelConnectThread() {
        if (connectThread != null) {
            connectThread.cancel();
            connectThread = null;
        }
    }*/

    private void cancelConnectedThread() {
        if (connectedThread != null) {
            connectedThread.cancel();
            connectedThread = null;
        }
    }

    private void cancelConnectThreads() {
        ConnectThread connectThread;
        while(connectThreads.size() > 0) {
            connectThread = connectThreads.get(0);
            connectThread.cancel();
            connectThreads.remove(connectThread);
        }
    }

    private void cancelRecordThread() {
        if (recordThread != null) {
            recordThread.cancel();
            recordThread = null;
        }
    }

    /*private void cancelThread(BaseThread thread) {
        if (thread != null) {
            thread.cancel();
            thread = null;
        }
    }*/

    /**
     * Indicate that the connection attempt failed and notify the UI Activity.
     */
    protected void connectionFailed(ConnectThread thread) {
        Message msg = handler.obtainMessage(BootstrapActivity.MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(BootstrapActivity.TOAST, "Unable to connect device");
        msg.setData(bundle);
        handler.sendMessage(msg);

        connectThreads.remove(thread);
        if (connectThreads.size() == 0) {
            if (state != STATE_CONNECTED) {
                setState(STATE_LISTEN);
            }
        }
    }


    /**
     * Indicate that the connection was lost and notify the UI Activity.
     */
    protected void connectionLost() {
        Message msg = handler.obtainMessage(BootstrapActivity.MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(BootstrapActivity.TOAST, "Device connection was lost");
        msg.setData(bundle);
        handler.sendMessage(msg);

        cancelConnectedThread();

        //cancelAcceptThread();

        disconnected();
    }

    private void disconnected() {
        setState(STATE_CONNECTING);

        ConnectThread connectThread;
        for (BluetoothDevice bluetoothDevice : bluetoothAdapter.getBondedDevices()) {
            connectThread = new ConnectThread(this, bluetoothDevice);
            connectThread.start();
            connectThreads.add(connectThread);
        }
        /*if (connectThread == null) {
            connectThread = new ConnectThread(this, de);
            connectThread.start();
        }*/

        if (acceptThread == null) {
            acceptThread = new AcceptThread(this);
            acceptThread.start();
        }
    }

    protected BluetoothSocket getBluetoothSocket() {
        return bluetoothSocket;
    }
}
