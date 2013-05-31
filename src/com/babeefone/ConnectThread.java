package com.babeefone;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;

import java.io.IOException;

class ConnectThread extends BaseThread {

    private BluetoothDevice device;

    public ConnectThread(MainService mainService, BluetoothDevice device) {
        super(mainService);
        this.device = device;
    }

    public void run() {
        BluetoothSocket bluetoothSocket = null;

        try {
            bluetoothSocket = device.createInsecureRfcommSocketToServiceRecord(MainService.BABEEFONE_UUID);
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
            mainService.connectionFailed(this);
        }
    }
}