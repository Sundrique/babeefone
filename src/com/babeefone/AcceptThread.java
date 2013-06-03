package com.babeefone;

import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.util.Log;

import java.io.IOException;

class AcceptThread extends BaseThread {
    private BluetoothServerSocket bluetoothServerSocket = null;

    public AcceptThread(MainService mainService) {
        super(mainService);

        try {
            bluetoothServerSocket = mainService
                    .getBluetoothAdapter()
                    .listenUsingInsecureRfcommWithServiceRecord(MainService.NAME, MainService.BABEEFONE_UUID);
        } catch (IOException e) {
            // todo log exception
        }
    }

    public void run() {
        BluetoothSocket socket;

        while (!mainService.getConnected() && !canceled) {
            try {
                socket = bluetoothServerSocket.accept();
            } catch (IOException e) {
                // todo log exception
                break;
            }

            if (socket != null) {
                synchronized (mainService) {
                    mainService.connected(socket, socket.getRemoteDevice());
                    mainService.setConnectedDeviceMode();
                }
            }
        }

    }

    public void cancel() {
        super.cancel();

        try {
            bluetoothServerSocket.close();
        } catch (IOException e) {
            // todo log exception
        }
    }
}
