package com.babeefone;

import android.bluetooth.BluetoothDevice;

import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

class DisconnectedThread extends BaseThread {

    private ArrayList<BluetoothDevice> knownDevices = new ArrayList<BluetoothDevice>();
    private ArrayList<ConnectThread> connectThreads = new ArrayList<ConnectThread>();
    private static final Object DEVICES_LOCK = new Object();

    public DisconnectedThread(MainService mainService, ArrayList<BluetoothDevice> knownDevices) {
        super(mainService);

        this.knownDevices = knownDevices;
    }

    public void run() {
        connectKnown();
    }

    private void connectKnown() {
        synchronized (DEVICES_LOCK) {
            if (!canceled) {
                if (knownDevices.size() > 0) {
                    for (BluetoothDevice bluetoothDevice : knownDevices) {
                        connect(bluetoothDevice);
                    }
                } else {
                    new Timer().schedule(new TimerTask() {
                        @Override
                        public void run() {
                            connectKnown();
                        }
                    }, 500);
                }
            }
        }
    }

    private void connect(BluetoothDevice bluetoothDevice) {
        ConnectThread connectThread = new ConnectThread(this.mainService, bluetoothDevice, this);
        connectThread.start();
        connectThreads.add(connectThread);
    }

    protected void connectionFailed(ConnectThread thread) {
        connectThreads.remove(thread);
        if (connectThreads.size() == 0) {
            if (!mainService.getConnected()) {
                connectKnown();
            }
        }
    }

    public void addDevice(BluetoothDevice device) {
        synchronized (DEVICES_LOCK) {
            if (!knownDevices.contains(device)) {
                knownDevices.add(device);
            }
        }
    }

    public void cancel() {
        super.cancel();

        ConnectThread connectThread;
        while (connectThreads.size() > 0) {
            connectThread = connectThreads.get(0);
            connectThread.cancel();
            connectThreads.remove(connectThread);
        }
    }
}