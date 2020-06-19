package com.example.audiorecorderdemo;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.File;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "AudioRecorderWrapper";
    AudioRecorderWrapper recorderWrapper;
    AudioTrackWrapper trackWrapper;
    private TextView recordInfo;
    private TextView recordTime;
    private TextView tvPlayProgress;
    private ProgressBar progressBarAudioPlay;
    private Button btnRecordStart;
    private Button btnRecordPause;
    private Button btnRecordStop;
    private Button btnPlayStart;
    private Button btnPlayPause;
    private Button btnPlayStop;
    private Button btnRecordAndPlay;
    private String mCurrentRecordFilenamePath;
    private long mTotalAudioLen;
    private int fileformat = AudioConstant.SAVE_FILE_PCM;       // 录音文件的保存可以，可选pcm，wav，或两种格式都保存。
    private final int MSG_START_RECORD = 1;
    private final int MSG_START_STOP = 2;
    private String[] permissions = {
            Manifest.permission.READ_EXTERNAL_STORAGE
            , Manifest.permission.WRITE_EXTERNAL_STORAGE
            , Manifest.permission.RECORD_AUDIO
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initPermission(this, permissions);

        initView();

        recorderWrapper = new AudioRecorderWrapper(this, mAudioRecorderListener);
        trackWrapper = new AudioTrackWrapper(mAudioPlayerListener);
    }

    private void initView() {
        recordInfo = (TextView) findViewById(R.id.recordInfo);
        recordTime = (TextView) findViewById(R.id.recordTime);
        tvPlayProgress = (TextView) findViewById(R.id.tvPlayProgress);

        progressBarAudioPlay = (ProgressBar) findViewById(R.id.progressBarAudioPlay);

        btnRecordStart = (Button) findViewById(R.id.btnRecordStart);
        btnRecordPause = (Button) findViewById(R.id.btnRecordPause);
        btnRecordStop = (Button) findViewById(R.id.btnRecordStop);
        btnPlayStart = (Button) findViewById(R.id.btnPlayStart);
        btnPlayPause = (Button) findViewById(R.id.btnPlayPause);
        btnPlayStop = (Button) findViewById(R.id.btnPlayStop);
        btnRecordAndPlay = (Button) findViewById(R.id.btnRecordAndPlay);

        btnRecordStart.setOnClickListener(mBtnListener);
        btnRecordPause.setOnClickListener(mBtnListener);
        btnRecordStop.setOnClickListener(mBtnListener);
        btnPlayStart.setOnClickListener(mBtnListener);
        btnPlayPause.setOnClickListener(mBtnListener);
        btnPlayStop.setOnClickListener(mBtnListener);
        btnRecordAndPlay.setOnClickListener(mBtnListener);

        btnRecordStart.setEnabled(true);
        btnRecordStop.setEnabled(false);
        btnPlayStart.setEnabled(true);
        btnPlayPause.setEnabled(false);
        btnPlayStop.setEnabled(false);

        progressBarAudioPlay.setProgress(0);
    }

    private String getTimeInfo() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss");
        Date data = new Date();
        String timeString = sdf.format(data);
        return timeString;
    }

    private AudioRecorderWrapper.OnAudioRecorderListener mAudioRecorderListener = new AudioRecorderWrapper.OnAudioRecorderListener() {
        @Override
        public void onError(int state) {

        }

        @Override
        public void onRecorderStart() {
        }
        public int errorRecordDataCount = 0;

        @Override
        public void onRecording(byte[] buffer, long totalAudioLen, final double recordDuration) {

            mTotalAudioLen = totalAudioLen;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    DecimalFormat df = new DecimalFormat("#0.00");
                    recordTime.setText(df.format(recordDuration));
                }
            });

            for (int i = 0; i < buffer.length; i += 6) {
                if (buffer[i] == 0) {
                    errorRecordDataCount++;
                } else {
                    errorRecordDataCount = 0;
                }
            }
            if (errorRecordDataCount > 200) {
                Log.d(TAG, "11111111111111111111 Record fail");
                errorRecordDataCount = 0;
            }
        }

        @Override
        public void onRecorderPause() {

        }

        @Override
        public void onRecorderStop() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    String pre = recordInfo.getText().toString();
                    recordInfo.setText(pre + "\n文件大小: " + mTotalAudioLen + "byte");
                }
            });
        }
    };

    private AudioTrackWrapper.OnAudioPlayerListener mAudioPlayerListener = new AudioTrackWrapper.OnAudioPlayerListener() {
        @Override
        public void onPlayerStart(final long fileLen) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    progressBarAudioPlay.setMax((int) fileLen);
                }
            });
        }

        @Override
        public void onPlaying(byte[] buffer, final long totalAudioLen, final long fileLen, double recordDuration) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    progressBarAudioPlay.setProgress((int) totalAudioLen);
                    DecimalFormat df = new DecimalFormat("#0.00");
                    tvPlayProgress.setText(df.format((totalAudioLen * 1.0 / fileLen) * 100) + "%");
                }
            });
        }

        @Override
        public void onPlayerPause(long playLen, long totalLen) {
            Log.e(TAG, "playLen:" + playLen + ", totalLen:" + totalLen);
        }

        @Override
        public void onPlayerStop(long totalAudioLen) {
            Log.e(TAG, "------------onPlayerStop");
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    tvPlayProgress.setText("100%");
                    btnPlayStart.setEnabled(true);
                    btnPlayPause.setEnabled(false);
                    btnPlayStop.setEnabled(false);
                }
            });
        }
    };
    @SuppressLint("HandlerLeak")
    private Handler handler = new Handler(){
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_START_RECORD:
                    btnRecordStart.callOnClick();
                    break;
                case MSG_START_STOP:
                    btnRecordStop.callOnClick();
                    //handler.sendEmptyMessageDelayed(MSG_START_RECORD, 1000);
                    break;
                default:break;
            }
        }
    };
    private View.OnClickListener mBtnListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.btnRecordStart:
                    btnRecordAndPlay.setEnabled(false);
                    btnRecordStart.setEnabled(false);
                    btnRecordPause.setEnabled(true);
                    btnRecordStop.setEnabled(true);
                    mCurrentRecordFilenamePath = AudioConstant.FILE_DIR_PATH + File.separator + getTimeInfo();  // 无后缀
                    String suffix;

                    recorderWrapper.startRecord(mCurrentRecordFilenamePath, fileformat);

                    if (AudioConstant.SAVE_FILE_PCM == fileformat) {
                        suffix = ".pcm";
                    } else if (AudioConstant.SAVE_FILE_WAV == fileformat) {
                        suffix = ".wav";
                    } else {
                        suffix = ".wav";
                    }
                    recordInfo.setText("录音保存地址:\n" + mCurrentRecordFilenamePath + suffix);
                    //handler.sendEmptyMessageDelayed(MSG_START_STOP, 5400000);
                    //handler.sendEmptyMessageDelayed(MSG_START_STOP, 10000);
                    break;
                case R.id.btnRecordPause:
                    btnRecordStart.setEnabled(true);
                    btnRecordPause.setEnabled(false);
                    btnRecordStop.setEnabled(true);
                    recorderWrapper.pauseRecord();
                    break;
                case R.id.btnRecordStop:
                    btnRecordStart.setEnabled(true);
                    btnRecordPause.setEnabled(false);
                    btnRecordStop.setEnabled(false);
                    recorderWrapper.stopRecord();
                    btnPlayStart.setEnabled(true);
                    btnPlayPause.setEnabled(false);
                    btnPlayStop.setEnabled(false);
                    btnRecordAndPlay.setEnabled(true);
                    trackWrapper.stop();
                    progressBarAudioPlay.setProgress(0);
                    break;
                case R.id.btnRecordAndPlay:
                    btnRecordAndPlay.setEnabled(false);
                    btnRecordStart.setEnabled(false);
                    btnRecordPause.setEnabled(false);
                    btnRecordStop.setEnabled(true);
                    btnPlayStart.setEnabled(false);
                    btnPlayPause.setEnabled(false);
                    btnPlayStop.setEnabled(false);

                    pipedInputStream = new PipedInputStream();
                    recorderWrapper.setPlayingInRecordingEnable(true);
                    recorderWrapper.setPipedInputStream(pipedInputStream);
                    recorderWrapper.startRecord(mCurrentRecordFilenamePath, fileformat);

                    pipedOutputStream = new PipedOutputStream();
                    trackWrapper.setPlayingInRecordingEnable(true);
                    trackWrapper.setPipedOutputStream(pipedOutputStream);
                    trackWrapper.setPipedInputStream(pipedInputStream);
                    trackWrapper.play();

                    new Thread(){
                        @Override
                        public void run() {
                            byte[] buffer = new byte[1024];
                            int size = 0 ;
                            while (true){
                                try {
                                    while (pipedInputStream.available()>0){
                                        size = pipedInputStream.read(buffer);
                                        pipedOutputStream.write(buffer, 0, size);
                                    }
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                    }.start();
                    break;
                case R.id.btnPlayStart:
                    Log.e(TAG, "mCurrentRecordFilenamePath:" + mCurrentRecordFilenamePath);
                    if (null != mCurrentRecordFilenamePath) {
                        btnPlayStart.setEnabled(false);
                        btnPlayPause.setEnabled(true);
                        btnPlayStop.setEnabled(true);
                        trackWrapper.offData(mCurrentRecordFilenamePath + ".pcm");
                        trackWrapper.play();
                    }
//                    trackWrapper.offData("/sdcard/AudioRecorderWrapper/20200116_172242.pcm");   // 测试这个文件
//                    trackWrapper.play();
                    break;
                case R.id.btnPlayPause:
                    btnPlayStart.setEnabled(true);
                    btnPlayPause.setEnabled(false);
                    btnPlayStop.setEnabled(true);
                    trackWrapper.pause();
                    break;
                case R.id.btnPlayStop:
                    tvPlayProgress.setText("0%");
                    btnPlayStart.setEnabled(true);
                    btnPlayPause.setEnabled(false);
                    btnPlayStop.setEnabled(false);
                    trackWrapper.stop();
                    progressBarAudioPlay.setProgress(0);
                    break;
                    default:break;
            }
        }
    };
    PipedInputStream pipedInputStream;
    PipedOutputStream pipedOutputStream;
    public static void initPermission(Activity activity, String[] permissions) {
        List<String> toApplyList = new ArrayList<String>();
        // 将没有获取的权限加入ArrayList中,动态请求.
        for (String perm : permissions) {
            if (PackageManager.PERMISSION_GRANTED != ContextCompat.checkSelfPermission(activity, perm)) {
                toApplyList.add(perm);
                //进入到这里代表没有权限.
                Log.e("ManifestUtil", "perm: " + perm);
            }
        }
        String tmpList[] = new String[toApplyList.size()];
        if (!toApplyList.isEmpty()) {
            ActivityCompat.requestPermissions(activity, toApplyList.toArray(tmpList), 123);
        }
    }

}
