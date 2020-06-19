package com.example.audiorecorderdemo;

import android.media.AudioFormat;
import android.os.Environment;

import java.io.File;

public class AudioConstant {
    // 采样频率：44100是目前的标准，但是某些设备仍然支持22050， 16000， 11025
   /* public static final int SAMPLE_RATE_IN_HZ_RECORD = 32000;
    public static final int CHANNEL_CONFIG_RECORD = AudioFormat.CHANNEL_IN_STEREO;
    public static final int AUDIO_FORMAT_RECORD = AudioFormat.ENCODING_PCM_16BIT;

    public static final int SAMPLE_RATE_IN_HZ_PLAY = 32000;
    public static final int CHANNEL_CONFIG_PLAY = AudioFormat.CHANNEL_OUT_STEREO;
    public static final int AUDIO_FORMAT_PLAY = AudioFormat.ENCODING_PCM_16BIT;*/

    // 单通道
    // 16000， xxx_mono，这样就采集到单通道的数据，也就是只用到一个麦克。
    //如果要用到两个麦克，并且两个麦克的的数据都以单通道的形式拿出来，
    // 就配置成16000，xxx_stereo，然后对采集到的数据进行拆分。
    public static final int SAMPLE_RATE_IN_HZ_RECORD = 16000;
    public static final int CHANNEL_CONFIG_RECORD = AudioFormat.CHANNEL_IN_MONO;
    public static final int AUDIO_FORMAT_RECORD = AudioFormat.ENCODING_PCM_16BIT;

    public static final int SAMPLE_RATE_IN_HZ_PLAY = 16000;
    public static final int CHANNEL_CONFIG_PLAY = AudioFormat.CHANNEL_OUT_MONO;
    public static final int AUDIO_FORMAT_PLAY = AudioFormat.ENCODING_PCM_16BIT;
    // end
    public static final String FILE_DIR_NAME = "mtklog";
    public static final String FILE_DIR_PATH = Environment.getExternalStorageDirectory() + File.separator + FILE_DIR_NAME;

    public static final int SAVE_FILE_PCM = 0x01;                                   // 保存为pcm文件
    public static final int SAVE_FILE_WAV = 0x02;                                   // 保存为wav文件
    public static final int SAVE_FILE_ALL = (SAVE_FILE_PCM | SAVE_FILE_WAV);        // pcm和wav文件都保存
}
