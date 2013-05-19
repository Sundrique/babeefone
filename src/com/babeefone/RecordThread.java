package com.babeefone;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;

class RecordThread extends BaseThread {

    private AudioRecord audioRecord;
    private int inBufferSize;

    public RecordThread(BabeefoneService babeefoneService) {
        super(babeefoneService);
        inBufferSize = AudioRecord.getMinBufferSize(BabeefoneService.SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, BabeefoneService.AUDIO_FORMAT);
        audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC,
                BabeefoneService.SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                BabeefoneService.AUDIO_FORMAT,
                inBufferSize);
    }

    public void run() {
        short[] buffer = new short[inBufferSize];
        int count;
        audioRecord.startRecording();
        while (!canceled) {
            count = audioRecord.read(buffer, 0, buffer.length);

            if (count > 0) {
                short[] bf = new short[count];
                System.arraycopy(buffer, 0, bf, 0, count);

                DataPacket dataPacket = new DataPacket();
                dataPacket.type = DataPacket.TYPE_AUDIO_DATA;
                dataPacket.audioData = bf;

                babeefoneService.send(dataPacket);
            }
        }
    }

    public void cancel() {
        super.cancel();
        if (audioRecord != null) {
            audioRecord.stop();
            audioRecord.release();
            audioRecord = null;
        }
    }
}
