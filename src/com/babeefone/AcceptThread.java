package com.babeefone;

import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;

import java.io.IOException;

/**
 * This thread runs while listening for incoming connections. It behaves
 * like a server-side client. It runs until a connection is accepted
 * (or until cancelled).
 */
class AcceptThread extends BaseThread {
    private BluetoothServerSocket bluetoothServerSocket = null;

    public AcceptThread(BabeefoneService babeefoneService) {
        super(babeefoneService);

        try {
            bluetoothServerSocket = babeefoneService.getBluetoothAdapter().listenUsingRfcommWithServiceRecord(BabeefoneService.NAME, BabeefoneService.MY_UUID);
        } catch (IOException e) {
            // todo log exception
        }
    }

    public void run() {
        BluetoothSocket socket = null;

        while (babeefoneService.state != BabeefoneService.STATE_CONNECTED && !canceled) {
            try {
                socket = bluetoothServerSocket.accept();
            } catch (IOException e) {
                // todo log exception
                break;
            }

            if (socket != null) {
                synchronized (babeefoneService) {
                    switch (babeefoneService.getState()) {
                        case BabeefoneService.STATE_LISTEN:
                        case BabeefoneService.STATE_CONNECTING:
                            babeefoneService.connected(socket, socket.getRemoteDevice());
                            babeefoneService.setConnectedDeviceMode();
                            break;
                        case BabeefoneService.STATE_NONE:
                        case BabeefoneService.STATE_CONNECTED:
                            try {
                                socket.close();
                            } catch (IOException e) {
                                // todo log exception
                            }
                            break;
                    }
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
