package com.babeefone;

public class VoiceActivityDetector {
	static {
		System.loadLibrary("vad");
	}

    public static native void init();

    public static native boolean process(short[] samples);

    public static native void free();
}
