package com.example.audiorecorderdemo;

import android.content.Context;
import android.content.Intent;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.RandomAccessFile;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class AudioRecorderWrapper {
    private static final String TAG = "AudioWrapperRecord";
    private Context mContext;
    private AudioRecord mAudioRecord;
    private OnAudioRecorderListener mListener;
    private AtomicBoolean startRecording_Th_Run = new AtomicBoolean(false);

    // 音频输入-麦克风
    private static final int AUDIO_INPUT = MediaRecorder.AudioSource.MIC;
    private int bufferSizeInBytes_record = AudioRecord.getMinBufferSize(
            AudioConstant.SAMPLE_RATE_IN_HZ_RECORD,
            AudioConstant.CHANNEL_CONFIG_RECORD,
            AudioConstant.AUDIO_FORMAT_RECORD
    );

    private String pcmFilePath;
    private String wavFilePath;
    private Thread startRecording_Th = null;
    private RecordState mRecordState = RecordState.STOP;
    private final Object pauseLock = new Object();
    private boolean playingInRecording = false;
    private AudioTrackWrapper audioTrackWrapper;

    interface OnAudioRecorderListener {
        void onError(int state);
        void onRecorderStart();
        void onRecording(byte[] buffer, long totalAudioLen, double recordDuration);
        void onRecorderPause();
        void onRecorderStop();
    }

    // 录音状态
    private enum RecordState {
        STOP,
        RECORDING,
        PAUSE,
    }
    // 边录边播
    private PipedInputStream pipedInputStream;
    public void setPlayingInRecordingEnable(boolean enable) {
        playingInRecording = enable;
    }
    public void setPipedInputStream(PipedInputStream pipedInputStream) {
        this.pipedInputStream = pipedInputStream;

    }
    private boolean isPlayingInRecording() {
        return playingInRecording;
    }
    public AudioRecorderWrapper(Context context, OnAudioRecorderListener listener) {
        mContext = context;
        mListener = listener;
        Log.i(TAG, "bufferSizeInBytes_record:" + bufferSizeInBytes_record);
    }

    public static boolean isSDCardExist() {
        return Environment.getExternalStorageState().equals(android.os.Environment.MEDIA_MOUNTED);
    }

    private void initParameter() {
        mRecordState = RecordState.STOP;
    }

    public void startRecord(final String filenamePath, final int format) {
        if (isSDCardExist()) {
            // 检测当前录音状态，如果已暂停，那么继续录音
            if (startRecording_Th != null && RecordState.PAUSE == mRecordState) {
                synchronized (pauseLock) {
                    mRecordState = RecordState.RECORDING;
                    pauseLock.notify();
                }
                return ;
            }

            if (0 != preDealwithFile(filenamePath)) {
                Log.e(TAG, "preDealwithFile is error.");
                return ;
            }

            mAudioRecord = new AudioRecord(AUDIO_INPUT,
                    AudioConstant.SAMPLE_RATE_IN_HZ_RECORD,
                    AudioConstant.CHANNEL_CONFIG_RECORD,
                    AudioConstant.AUDIO_FORMAT_RECORD,
                    bufferSizeInBytes_record
            );

            startRecording_Th = new Thread(new Runnable() {
                @Override
                public void run() {
                    startRecording_Th_Run.set(true);

                    mRecordState = RecordState.RECORDING;
                    mAudioRecord.startRecording();
                    if (mListener != null) {
                        mListener.onRecorderStart();
                    }
                    if (isPlayingInRecording()) {
                        pipedOutputStream = new PipedOutputStream();
                        try {
                            byte[] buffer = new byte[bufferSizeInBytes_record];
                            pipedOutputStream.connect(pipedInputStream);
                            int bufferReadSize = 1024;
                            while (isPlayingInRecording()) {
                                mAudioRecord.read(buffer, 0, bufferReadSize);
                                pipedOutputStream.write(buffer, 0, bufferReadSize);
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    saveAudioData2File(format);

                    startRecording_Th_Run.set(false);
                    startRecording_Th = null;

                    initParameter();
                }
            });
            startRecording_Th.start();
        }

    }
    PipedOutputStream  pipedOutputStream;
    public void pauseRecord() {
        mRecordState = RecordState.PAUSE;
        if (mListener != null) {
            mListener.onRecorderPause();
        }
    }

    public void stopRecord() {
        mRecordState = RecordState.STOP;
        if (mAudioRecord != null) {
            mAudioRecord.stop();
            mAudioRecord.release();
            mAudioRecord = null;
        }
        playingInRecording = false;
        synchronized (pauseLock) {
            pauseLock.notify();
        }
        if (mListener != null) {
            mListener.onRecorderStop();
        }
        try {
            if (pipedOutputStream != null) {
                pipedOutputStream.close();
                pipedOutputStream = null;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 将录制的音频以指定格式进行保存
     * @param format
     */
    private void saveAudioData2File(int format) {
        try {
            int len;
            final byte[] buffer = new byte[bufferSizeInBytes_record];
            long totalAudioLen = 0;
            byte[] wavHead = new byte[44];
            long totalDataLen = totalAudioLen + 36;
            long longSampleRate = AudioConstant.SAMPLE_RATE_IN_HZ_RECORD;
            int channels;
            if (AudioFormat.CHANNEL_IN_STEREO == AudioConstant.CHANNEL_CONFIG_RECORD) {
                channels = 2;
            } else {
                channels = 1;
            }
            int pcmBit;
            if (AudioFormat.ENCODING_PCM_16BIT == AudioConstant.AUDIO_FORMAT_RECORD) {
                pcmBit = 2;
            } else {
                pcmBit = 1;
            }

            long byteRate = 16 * AudioConstant.SAMPLE_RATE_IN_HZ_RECORD * channels / 8;
            long recordTimes = 0;   // 本次录音总共从麦克风读取音频数据次数
            double recordRead = AudioConstant.SAMPLE_RATE_IN_HZ_RECORD * channels * pcmBit * 1.0 / bufferSizeInBytes_record; // 每秒从麦克风读取音频次数
            double recordDuration;

            boolean isSavePcm = (AudioConstant.SAVE_FILE_PCM == (format & AudioConstant.SAVE_FILE_PCM));
            boolean isSaveWav = (AudioConstant.SAVE_FILE_WAV == (format & AudioConstant.SAVE_FILE_WAV));

            BufferedOutputStream pcmFos = null;
            RandomAccessFile randomAccessFile = null;

            if (isSavePcm) {
                pcmFos = new BufferedOutputStream(new FileOutputStream(new File(pcmFilePath)));
            }
            if (isSaveWav) {
                randomAccessFile = new RandomAccessFile(wavFilePath, "rw");
                randomAccessFile.write(wavHead);
            }

            while (RecordState.STOP != mRecordState) {
                if (RecordState.PAUSE == mRecordState) {
                    synchronized (pauseLock) {
                        pauseLock.wait();
                    }
                } else if (RecordState.STOP == mRecordState) {
                    continue;
                }
                if (mAudioRecord != null) {
                    len = mAudioRecord.read(buffer, 0, bufferSizeInBytes_record);
                    if (len > 0) {

                        if (isSavePcm) {
                            pcmFos.write(buffer, 0, len);
                        }
                        if (isSaveWav) {
                            randomAccessFile.write(buffer, 0, len);
                        }
                        totalAudioLen += bufferSizeInBytes_record;
                        if (mListener != null) {
                            recordTimes = totalAudioLen / bufferSizeInBytes_record;
                            recordDuration = recordTimes / recordRead;
                            mListener.onRecording(buffer, totalAudioLen, recordDuration);
                        }
                        Log.d(TAG, "recording... len:" + len);
                    }
                }
            }

            wavHead = getWavHeader(totalAudioLen, totalDataLen, longSampleRate, channels, byteRate);
            if (isSaveWav) {
                randomAccessFile.seek(0);
                randomAccessFile.write(wavHead);
                randomAccessFile.close();
            }

            if (isSavePcm) {
                pcmFos.flush();
                pcmFos.close();
            }
            Log.i(TAG, "saveAudioData2File finish.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 生成Wav文件头部44个字节数据
     * @param totalAudioLen     音频数据量
     * @param totalDataLen      totalDataLen = totalAudioLen + 36
     * @param longSampleRate    采样率
     * @param channels          通道数
     * @param byteRate          16 * AudioConstant.SAMPLE_RATE_IN_HZ_RECORD * channels / 8;
     * @return
     */
    private byte[] getWavHeader(long totalAudioLen, long totalDataLen, long longSampleRate, int channels, long byteRate) {
        byte[] header = new byte[44];
        header[0] = 'R'; // RIFF/WAVE header
        header[1] = 'I';
        header[2] = 'F';
        header[3] = 'F';
        header[4] = (byte) (totalDataLen & 0xff);
        header[5] = (byte) ((totalDataLen >> 8) & 0xff);
        header[6] = (byte) ((totalDataLen >> 16) & 0xff);
        header[7] = (byte) ((totalDataLen >> 24) & 0xff);
        header[8] = 'W';
        header[9] = 'A';
        header[10] = 'V';
        header[11] = 'E';
        header[12] = 'f'; // 'fmt ' chunk
        header[13] = 'm';
        header[14] = 't';
        header[15] = ' ';
        header[16] = 16; // 4 bytes: size of 'fmt ' chunk
        header[17] = 0;
        header[18] = 0;
        header[19] = 0;
        //编码方式 10H为PCM编码格式
        header[20] = 1; // format = 1
        header[21] = 0;
        header[22] = (byte) channels;
        header[23] = 0;
        //采样率，每个通道的播放速度
        header[24] = (byte) (longSampleRate & 0xff);
        header[25] = (byte) ((longSampleRate >> 8) & 0xff);
        header[26] = (byte) ((longSampleRate >> 16) & 0xff);
        header[27] = (byte) ((longSampleRate >> 24) & 0xff);
        //音频数据传送速率,采样率*通道数*采样深度/8
        header[28] = (byte) (byteRate & 0xff);
        header[29] = (byte) ((byteRate >> 8) & 0xff);
        header[30] = (byte) ((byteRate >> 16) & 0xff);
        header[31] = (byte) ((byteRate >> 24) & 0xff);
        //确定系统一次要处理多少个这样字节的数据，确定缓冲区，通道数*采样位数
        header[32] = (byte) (channels * 16 / 8); // block align
        header[33] = 0;
        //每个样本的数据位数
        header[34] = 16; // bits per sample
        header[35] = 0;
        header[36] = 'd';
        header[37] = 'a';
        header[38] = 't';
        header[39] = 'a';
        header[40] = (byte) (totalAudioLen & 0xff);
        header[41] = (byte) ((totalAudioLen >> 8) & 0xff);
        header[42] = (byte) ((totalAudioLen >> 16) & 0xff);
        header[43] = (byte) ((totalAudioLen >> 24) & 0xff);

        return header;
    }

    private int preDealwithFile(String filePath) {
        if (isSDCardExist()) {
            if (filePath == null || "".equals(filePath)) {
                Date date = new Date();
                DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd_hh-mm-mm");
                filePath = AudioConstant.FILE_DIR_PATH + File.separator + dateFormat.format(date);
                Log.e(TAG, "preDealwithFile, time name file:" + filePath);
            }
            
            wavFilePath = filePath + ".wav";
            pcmFilePath = filePath + ".pcm";
            return safeMakeFileDir(filePath);
        }
        return -1;
    }

    public int safeMakeFile(String filePath) {
        if (0 == safeMakeFileDir(filePath)) {
            File file = new File(filePath);
            if (!file.exists()) {
                try {
                    if (file.createNewFile()) {
                        notifySystemToScanFile(filePath);
                        Log.i(TAG, filePath + " createNewFile() success.");
                        return 0;
                    } else {
                        Log.e(TAG, filePath + " createNewFile() failed.");
                        return -1;
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else {
                notifySystemToScanFile(filePath);
                return 0;
            }
        }
        return -1;
    }

    public int safeMakeFileDir(String filePath) {
        File file = new File(filePath);
        File fileDirFile = file.getParentFile();
        String fileDirPath = fileDirFile.getPath();
        return safeMakeDir(fileDirPath);
    }

    public int safeMakeDir(String dirPath) {
        File dir = new File(dirPath);
        if (!dir.exists()) {
            if (dir.mkdirs()) {
                Log.i(TAG, "fileParent.mkdirs() success.");
                notifySystemToScanDir(dirPath);
                return 0;
            } else {
                Log.e(TAG, "fileParent.mkdirs() failed.");
                return -1;
            }
        } else {
            notifySystemToScanDir(dirPath);
            return 0;
        }
    }

    /**
     * Android7.0 will crash.
     * @param dirPath
     */
    public void notifySystemToScanDir(String dirPath) {
//        Intent intent = new Intent("android.intent.action.MEDIA_SCANNER_SCAN_DIR");
//        File dir = new File(dirPath);
//
//        Uri uri = Uri.fromFile(dir);
//        intent.setData(uri);
//        mContext.getApplicationContext().sendBroadcast(intent);
    }

    public void notifySystemToScanFile(String filePath) {
        Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        File file = new File(filePath);
        Log.e(TAG, "notifySystemToScanFile:" + filePath);

        Uri uri = Uri.fromFile(file);
        intent.setData(uri);
        mContext.getApplicationContext().sendBroadcast(intent);
    }

}
