package com.babeefone;

import java.io.Serializable;

public class DataPacket implements Serializable {
	private static final long serialVersionUID = 1L;

	public static final int TYPE_AUDIO_DATA = 0;
    public static final int TYPE_MODE = 1;
	public int type;

	public short[] audioData;

    public int mode;
}