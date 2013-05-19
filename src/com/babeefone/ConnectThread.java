package com.babeefone;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;

import java.io.IOException;

/**
 * This thread runs while attempting to make an outgoing connection
 * with a device. It runs straight through; the connection either
 * succeeds or fails.
 */
class ConnectThread extends BaseThread {

    private BluetoothDevice device;

    public ConnectThread(BabeefoneService babeefoneService, BluetoothDevice device) {
        super(babeefoneService);
        this.device = device;
    }

    public void run() {
        babeefoneService.getBluetoothAdapter().cancelDiscovery();
        BluetoothSocket bluetoothSocket = null;

        try {
            bluetoothSocket = device.createRfcommSocketToServiceRecord(BabeefoneService.MY_UUID);
            bluetoothSocket.connect();
            if (!canceled) {
                babeefoneService.connected(bluetoothSocket, device);
            }
            return;
        } catch (IOException e) {
            try {
                bluetoothSocket.close();
            } catch (IOException e2) {
                // todo log exception
            }
        }

        if (!canceled) {
            babeefoneService.connectionFailed(this);
        }
    }

}
