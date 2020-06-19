package com.example.audiorecorderdemo;

import android.media.AudioManager;
import android.media.AudioTrack;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.Arrays;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class AudioTrackWrapper {
    private static final String TAG = "AudioWrapperPlay";
    //根据采样率，采样精度，单双声道来得到frame的大小。
    private int bufferSizeInBytes_play = AudioTrack.getMinBufferSize(
            AudioConstant.SAMPLE_RATE_IN_HZ_PLAY,
            AudioConstant.CHANNEL_CONFIG_PLAY,
            AudioConstant.AUDIO_FORMAT_PLAY
    );

    private AudioTrack trackplayer;
    private Thread play_Th;
    private AtomicBoolean play_Th_Run = new AtomicBoolean(false);
    private boolean isNeedOffeData = false;
    private boolean hasOffeAllData = false;
    private LinkedBlockingQueue<byte[]> audioQueue = new LinkedBlockingQueue<>();
    private long mTotalAudioLen = -1;
    private long mPlayBeginPos = 0;
    private long mTotalPlayAudioLen = 0;
    private long mFileLen = 0;
    private boolean isPlayEnd = false;
    private boolean playingInRecording = false;

    interface OnAudioPlayerListener {
        void onPlayerStart(long fileLen);
        void onPlaying(byte[] buffer, long totalAudioLen, long fileLen, double recordDuration);
        void onPlayerPause(long playLen, long totalLen);
        void onPlayerStop(long totalLen);
    }

    private OnAudioPlayerListener mListener;

    public AudioTrackWrapper(OnAudioPlayerListener listener) {
        Log.i(TAG, "bufferSizeInBytes_play:" + bufferSizeInBytes_play);
        mListener = listener;
    }
    // 边录边播
    PipedOutputStream pipedOutputStream;
    PipedInputStream pipedInputStream;
    private PipedInputStream instream;

    public void setPlayingInRecordingEnable(boolean enable) {
        playingInRecording = enable;

    }
    public void setPipedOutputStream(PipedOutputStream pipedOutputStream) {
        this.pipedOutputStream = pipedOutputStream;
    }
    public void setPipedInputStream(PipedInputStream pipedInputStream) {
        this.pipedInputStream = pipedInputStream;
    }
    private boolean isPlayingInRecording() {
        return playingInRecording;
    }
    /**
     * 从阻塞队列中获取音频数据进行播放
     */
    public void play() {
        if (play_Th != null) {
            play_Th.interrupt();
            play_Th = null;
            play_Th_Run.set(false);
        }
        play_Th = new Thread(new Runnable() {
            @Override
            public void run() {
                play_Th_Run.set(true);
                Log.i(TAG, "play_Th_Run start.");
                trackplayer = new AudioTrack(
                        AudioManager.STREAM_MUSIC,
                        AudioConstant.SAMPLE_RATE_IN_HZ_PLAY,
                        AudioConstant.CHANNEL_CONFIG_PLAY,
                        AudioConstant.AUDIO_FORMAT_PLAY,
                        bufferSizeInBytes_play,
                        AudioTrack.MODE_STREAM
                );

                try {
                    Thread.sleep(100);
                    if (null != mListener) {
                        mListener.onPlayerStart(mFileLen);
                    }
                    mTotalPlayAudioLen = mPlayBeginPos;
                    if (isPlayingInRecording()) {
                        byte[] buffer = new byte[1024];
                        trackplayer.play();
                        try {
                            instream = new PipedInputStream(pipedOutputStream);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        while (isPlayingInRecording()) {
                            while (instream.available()>0){
                                int size = instream.read(buffer);
                                trackplayer.write(buffer, 0, size);
                            }
                        }
                    }
                    while(play_Th_Run.get() && !isPlayEnd) {
                        Log.i(TAG, "start take, audioQueue.size:" + audioQueue.size() + ", mTotalPlayAudioLen:" + mTotalPlayAudioLen + ", mTotalAudioLen:" + mTotalAudioLen);
                        if (hasOffeAllData && mTotalPlayAudioLen < mTotalAudioLen) {
                            byte[] tempData = audioQueue.take();
                            if (trackplayer.getPlayState() != AudioTrack.PLAYSTATE_PLAYING) {
                                Log.i(TAG, "audioTrack.play start.");
                                trackplayer.play();
                            }
                            mTotalPlayAudioLen += tempData.length;
                            trackplayer.write(tempData, 0, tempData.length);
                            if (null != mListener) {
                                mListener.onPlaying(tempData, mTotalPlayAudioLen, mFileLen, 0);
                            }
                        } else {
                            isPlayEnd = true;
                            mPlayBeginPos = 0;
                            mTotalPlayAudioLen = 0;
                            mTotalAudioLen = -1;
                            if (null != mListener) {
                                mListener.onPlayerStop(mTotalAudioLen);
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }

                if (null != trackplayer) {
                    trackplayer.stop();//停止播放
                    trackplayer.release();//释放底层资源。
                    trackplayer = null;
                }
                if (null != mListener && !isPlayEnd) {
                    mListener.onPlayerPause(mTotalPlayAudioLen, mTotalAudioLen);
                }
                Log.i(TAG, "play_Th_Run end.");

                isPlayEnd = false;
                play_Th_Run.set(false);
                play_Th = null;
            }
        });
        play_Th.start();
    }

    /**
     * 从filePath文件中读取数据，放入到阻塞队列中
     * @param filePath
     */
    public void offData(final String filePath) {
        if (!audioQueue.isEmpty()) {
            Log.w(TAG, "audioQueue is not empty");
            return ;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                Log.w(TAG, "offData start.");
                try {
                    Log.w(TAG, "filePath:" + filePath);
                    isNeedOffeData = true;
                    hasOffeAllData = false;
                    long totalAudioLen = 0;
                    File file = new File(filePath);
                    mFileLen = file.length();
                    Log.e(TAG, "mTotalAudioLen:" + mTotalAudioLen + ", bufferSizeInBytes_play:" + bufferSizeInBytes_play);
//                    BufferedInputStream bis = new BufferedInputStream(new FileInputStream(filePath));
                    BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file));
                    byte[] bytes = new byte[bufferSizeInBytes_play];
                    int len;
                    while (isNeedOffeData && (bufferSizeInBytes_play == (len = bis.read(bytes, 0, bytes.length)))) {
                        byte[] realBytes = new byte[bufferSizeInBytes_play];
                        System.arraycopy(bytes, 0, realBytes, 0, len);
                        totalAudioLen += len;
                        setAudioData(realBytes);
                        // Log.d(TAG, "---------------offData, len:" + len);
                    }
                    setFiniedAudioData();
                    mTotalAudioLen = totalAudioLen;
                    isNeedOffeData = false;
                    Log.w(TAG, "offData stop.");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    /**
     * set the data which need to be play.
     * @param data
     */
    public void setAudioData(byte[] data){
        try {
            audioQueue.put(data);
            Log.d(TAG, audioQueue.size()+"");
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * finish set the data to play. After set all audio data.
     */
    public void setFiniedAudioData() {
        hasOffeAllData = true;
    }

    public boolean isPlaying() {
        return play_Th_Run.get();
    }

    public void pause() {
        play_Th_Run.set(false);
        mPlayBeginPos = mTotalPlayAudioLen;
    }

    public void stop() {
        isNeedOffeData = false;
        audioQueue.clear();
        if (null != play_Th) {
            play_Th.interrupt();
        }
        play_Th_Run.set(false);
        mPlayBeginPos = 0;
        mTotalPlayAudioLen = 0;
        try {
            if (instream != null) {
                instream.close();
                instream = null;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
