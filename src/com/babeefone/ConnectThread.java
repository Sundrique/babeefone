package com.babeefone;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;

import java.io.IOException;

class ConnectThread extends BaseThread {

    private BluetoothDevice device;
    private DisconnectedThread disconnectedThread;

    public ConnectThread(MainService mainService, BluetoothDevice device, DisconnectedThread disconnectedThread) {
        super(mainService);
        this.device = device;
        this.disconnectedThread = disconnectedThread;
    }

    public void run() {
        BluetoothSocket bluetoothSocket = null;

        try {
            bluetoothSocket = device.createInsecureRfcommSocketToServiceRecord(MainService.BABEEFONE_UUID);
            BluetoothAdapter.getDefaultAdapter().cancelDiscovery();
            bluetoothSocket.connect();
            if (!canceled) {
                mainService.connected(bluetoothSocket, device);
            }
            return;
        } catch (IOException e) {
            try {
                if (bluetoothSocket != null) {
                    bluetoothSocket.close();
                }
            } catch (IOException e2) {
                // todo log exception
            }
        }

        if (!canceled) {
            disconnectedThread.connectionFailed(this);
        }
    }
}