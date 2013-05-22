package com.babeefone;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;

class ConnectedThread extends BaseThread {
    private static final Object PLAYER_LOCK = new Object();
    private ObjectInputStream objectInputStream;
    private BufferedInputStream bufferedInputStream;

    private AudioTrack audioTrack;
    private int outBufferSize;

    public ConnectedThread(MainService mainService) {
        super(mainService);

        try {
            bufferedInputStream = new BufferedInputStream(mainService.getBluetoothSocket().getInputStream());
        } catch (IOException e) {
            mainService.connectionLost();
        }

        initAudioTrack();
    }

    private void initAudioTrack() {
        synchronized (PLAYER_LOCK) {
            if (null != audioTrack) {
                audioTrack.release();
            }
            outBufferSize = AudioTrack.getMinBufferSize(MainService.SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, MainService.AUDIO_FORMAT);
            audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC,
                    MainService.SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    MainService.AUDIO_FORMAT,
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
                    // todo log exception
                }

                if (dataPacket == null) {
                    continue;
                }

                switch (dataPacket.type) {
                    case DataPacket.TYPE_AUDIO_DATA:
                        writeAudio(dataPacket.audioData);
                        break;
                    case DataPacket.TYPE_MODE:
                        mainService.setMode(dataPacket.mode);
                        break;
                    default:
                        throw new IllegalArgumentException("Unknown packet type");
                }
            }
        } catch (IOException e) {
            if (!canceled) {
                mainService.connectionLost();
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
            mainService.getBluetoothSocket().close();
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
