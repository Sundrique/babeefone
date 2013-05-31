package com.babeefone;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

class RecordThread extends BaseThread {

    private AudioRecord audioRecord;
    private int inBufferSize;
    /*private boolean isTalking = false;

    private Runnable triggerStopRecordingRunnable = new Runnable() {
        @Override
        public void run() {
            try {
                //isTalking = true;

                Thread.sleep(MainService.AUDIO_AUTOTALK_TIMEOUT);

                cancelTriggerThread();

                isTalking = false;
            } catch (InterruptedException ex) {
            }
        }
    };
    private Thread triggerStopRecording = null;*/

    public RecordThread(MainService mainService) {
        super(mainService);
        inBufferSize = AudioRecord.getMinBufferSize(MainService.SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, MainService.AUDIO_FORMAT);
        audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC,
                MainService.SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                MainService.AUDIO_FORMAT,
                inBufferSize);
    }

    public void run() {
        short[] buffer = new short[inBufferSize];
        int count;
        audioRecord.startRecording();
        while (!canceled) {
            count = audioRecord.read(buffer, 0, buffer.length);

            if (count > 0) {
                /*int i = 0;
                while (!isTalking && i < count) {
                    isTalking = buffer[i] > MainService.AUDIO_TRESHOLD;
                    Log.v("MY_DEBUG", Integer.toString(buffer[i]));
                    i++;
                }*/

                //if (isTalking) {
                    short[] bf = new short[count];
                    System.arraycopy(buffer, 0, bf, 0, count);

                    DataPacket dataPacket = new DataPacket();
                    dataPacket.type = DataPacket.TYPE_AUDIO_DATA;
                    dataPacket.audioData = bf;

                    mainService.send(dataPacket);

                    /*cancelTriggerThread();

                    triggerStopRecording = new Thread(triggerStopRecordingRunnable);
                    triggerStopRecording.start();
                }*/
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

    /*private void cancelTriggerThread() {
        if (triggerStopRecording != null) {
            if (triggerStopRecording.isAlive()) {
                triggerStopRecording.interrupt();
            }
            triggerStopRecording = null;
        }
    }*/
}
