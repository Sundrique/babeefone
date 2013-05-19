package com.babeefone;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;

/**
 * This thread runs during a connection with a remote device.
 * It handles all incoming and outgoing transmissions.
 */
class ConnectedThread extends BaseThread {
    private static final Object PLAYER_LOCK = new Object();
    private ObjectInputStream objectInputStream;
    private BufferedInputStream bufferedInputStream;

    private AudioTrack audioTrack;
    private int outBufferSize;

    public ConnectedThread(BabeefoneService babeefoneService) {
        super(babeefoneService);

        try {
            bufferedInputStream = new BufferedInputStream(babeefoneService.getBluetoothSocket().getInputStream());
        } catch (IOException e) {
            // todo log exception
        }

        initAudioTrack();
    }

    private void initAudioTrack() {
        synchronized (PLAYER_LOCK) {
            if (null != audioTrack) {
                audioTrack.release();
            }
            outBufferSize = AudioTrack.getMinBufferSize(BabeefoneService.SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, BabeefoneService.AUDIO_FORMAT);
            audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC,
                    BabeefoneService.SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    BabeefoneService.AUDIO_FORMAT,
                    outBufferSize,
                    AudioTrack.MODE_STREAM);
        }
    }

    public void run() {
        try {
            objectInputStream = new ObjectInputStream(bufferedInputStream);

            while (!canceled) {
                DataPacket dataPacket = null;
                try {
                    dataPacket = (DataPacket) objectInputStream.readObject();
                } catch (ClassNotFoundException e) {
                }

                if (dataPacket == null) {
                    continue;
                }

                switch (dataPacket.type) {
                    case DataPacket.TYPE_AUDIO_DATA:
                        writeAudio(dataPacket.audioData);
                        break;
                    case DataPacket.TYPE_MODE:
                        babeefoneService.setMode(dataPacket.mode);
                        break;
                    default:
                        throw new IllegalArgumentException("Unknown packet type");
                }
            }
        } catch (IOException e) {
            if (!canceled) {
                babeefoneService.connectionLost();
            }
        }

    }

    private void writeAudio(short[] data) {
        synchronized (PLAYER_LOCK) {
            if (!canceled) {
                if (data != null && data.length > 0) {
                    audioTrack.write(data, 0, data.length);
                }
                audioTrack.flush();
                audioTrack.play();
            }
        }
    }

    public void cancel() {
        super.cancel();

        try {
            babeefoneService.getBluetoothSocket().close();
            synchronized (PLAYER_LOCK) {
                audioTrack.stop();
                audioTrack.release();
                audioTrack = null;
            }
        } catch (IOException e) {
            // todo log exception
        }
    }
}
