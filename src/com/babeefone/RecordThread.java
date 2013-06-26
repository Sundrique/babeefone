package com.babeefone;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;

class RecordThread extends BaseThread {

    private final int FRAME_SIZE = 440;

    private AudioRecord audioRecord;
    private int inBufferSize;

    public RecordThread(MainService mainService) {
        super(mainService);
        inBufferSize = AudioRecord.getMinBufferSize(MainService.SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, MainService.AUDIO_FORMAT);
        audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC,
                MainService.SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                MainService.AUDIO_FORMAT,
                inBufferSize);

        VoiceActivityDetector.init();
    }

    public void run() {
        short[] buffer = new short[FRAME_SIZE];
        int count;
        int silinteFramesCount = 0;
        audioRecord.startRecording();
        while (!canceled) {
            count = audioRecord.read(buffer, 0, buffer.length);

            if (count > 0) {
                if (!VoiceActivityDetector.process(buffer)) {
                    silinteFramesCount++;
                } else {
                    silinteFramesCount = 0;
                }
                if (silinteFramesCount < 6) {
                    short[] bf = new short[count];
                    System.arraycopy(buffer, 0, bf, 0, count);

                    DataPacket dataPacket = new DataPacket();
                    dataPacket.type = DataPacket.TYPE_AUDIO_DATA;
                    dataPacket.audioData = bf;

                    mainService.send(dataPacket);
                }
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
        VoiceActivityDetector.free();
    }
}
