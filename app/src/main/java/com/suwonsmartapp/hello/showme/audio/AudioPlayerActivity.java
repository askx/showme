package com.suwonsmartapp.hello.showme.audio;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.suwonsmartapp.hello.R;
import com.suwonsmartapp.hello.showme.file.FileInfo;
import com.suwonsmartapp.hello.showme.file.FileThumbnail;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

public class AudioPlayerActivity extends AppCompatActivity implements View.OnClickListener {

    private static final String TAG = AudioPlayerActivity.class.getSimpleName();
    private void showLog(String msg) { Log.d(TAG, msg); }
    private void showToast(String toast_msg) { Toast.makeText(this, toast_msg, Toast.LENGTH_LONG).show(); }
    private static final String HOME = "com.suwonsmartapp.hello.showme.";

    private static final int MSG_GET_MP = 1;
    private static final int MSG_NEXT_MP = 2;

    private TextView mTvAudioPlayerTitle;
    private ImageView mIvAudioPlayerPicture;
    private SeekBar mSbAudioPlayerSeekbar;
    private TextView mTvAudioPlayerTimeRight;
    private TextView mTvAudioPlayerTimeLeft;

    private ImageButton mIbAudioPlayerPlay;
    private ImageButton mIbAudioPlayerForward;
    private ImageButton mIbAudioPlayerBackward;
    private ImageButton mIbAudioPlayerRevert;

    private static MediaPlayer mMediaPlayer;

    private double TimeLeft = 0;
    private double TimeRight = 0;

    private int mCurrentPosition = 0;
    private ArrayList<FileInfo> musicList;
    private FileInfo playSong;

    private boolean mIsReceiverRegistered;

    public static final int RESULT_OK = 0x0fff;
    public static final int REQUEST_CODE_AUDIO = 0x0001;
    public static final int REQUEST_CODE_AUDIO_PLAYER = 0x0002;
    public static final int REQUEST_CODE_VIDEO = 0x0010;
    public static final int REQUEST_CODE_VIDEO_PLAYER = 0x0020;
    public static final int REQUEST_CODE_IMAGE = 0x0100;
    public static final int REQUEST_CODE_IMAGE_PLAYER = 0x0200;
    Bundle extraAudioPlayerService;
    Intent intentAudioPlayerService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.audio_main_player);

        // 화면을 세로모드로 고정함.
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        setupViews();

        // 인텐트를 통해 경로명과 파일명을 읽음.
        Intent intent = getIntent();
        if (intent != null) {
            mCurrentPosition = intent.getIntExtra("currentPosition", -1);
            musicList = intent.getParcelableArrayListExtra("songInfoList");
        } else {
            showToast(getString(R.string.msg_wrong_file));
            finish();
        }

        // 이벤트 핸들러를 씨크바에 연결함.
        mSbAudioPlayerSeekbar = (SeekBar) findViewById(R.id.audio_player_seekbar);
        mSbAudioPlayerSeekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) { mMediaPlayer.seekTo(progress); }
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) { }
        });

        registerCallReceiver();
    }

    private void setupViews() {
        mIvAudioPlayerPicture = (ImageView) findViewById(R.id.audio_player_picture);
        mTvAudioPlayerTitle = (TextView) findViewById(R.id.audio_player_title);
        mTvAudioPlayerTimeLeft = (TextView) findViewById(R.id.auido_player_time_left);
        mTvAudioPlayerTimeRight = (TextView) findViewById(R.id.audio_player_time_right);

        mIbAudioPlayerBackward = (ImageButton) findViewById(R.id.audio_player_backward);
        mIbAudioPlayerBackward.setOnClickListener(this);

        mIbAudioPlayerPlay = (ImageButton) findViewById(R.id.audio_player_play);
        mIbAudioPlayerPlay.setOnClickListener(this);

        mIbAudioPlayerForward = (ImageButton) findViewById(R.id.audio_player_forward);
        mIbAudioPlayerForward.setOnClickListener(this);

        mIbAudioPlayerRevert = (ImageButton) findViewById(R.id.audio_player_showlist);
        mIbAudioPlayerRevert.setOnClickListener(this);

        mMediaPlayer = new MediaPlayer();
    }

    @Override
    protected void onStart() {
        super.onStart();

        registerCallReceiver();         // 리시버가 이미 등록되어 있으면 재등록은 하지 않음

        // 처음 스타트되면 서비스에 알려 곡을 재생토록 해야 함.
        Intent serviceIPC = new Intent(getApplicationContext(), AudioMessengerService.class);
        serviceIPC.putExtra("currentPosition", mCurrentPosition);
        serviceIPC.putParcelableArrayListExtra("songInfoList", musicList);
        startService(serviceIPC);

        bindService(serviceIPC, mConnectionMessenger, Context.BIND_ADJUST_WITH_ACTIVITY);

        if (AudioMessengerService.isPaused) {
            mIbAudioPlayerPlay.setImageResource(android.R.drawable.ic_media_play);
        }
    }

    @Override
    protected void onResume() {

        registerCallReceiver();         // 리시버가 이미 등록되어 있으면 재등록은 하지 않음

        // 리줌 후에 폴더를 바꾸어 곡을 변경할 경우 서비스에도 알려야 함.
        // 스타트인 경우(onStart)와 musicList가 변경되었을 수도 있음.
        Intent serviceIPC = new Intent(getApplicationContext(), AudioMessengerService.class);
        serviceIPC.putExtra("currentPosition", mCurrentPosition);
        serviceIPC.putParcelableArrayListExtra("songInfoList", musicList);
        startService(serviceIPC);

        bindService(serviceIPC, mConnectionMessenger, Context.BIND_ADJUST_WITH_ACTIVITY);

        if (AudioMessengerService.isPaused) {
            mIbAudioPlayerPlay.setImageResource(android.R.drawable.ic_media_play);
        }

        super.onResume();

//      볼륨 조절을 위해서...
//        AudioManager audioManager = (AudioManager) this.getSystemService(Context.AUDIO_SERVICE);
//        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC,
//                      audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC), 0);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (mBoundMessenger) {
            unbindService(mConnectionMessenger);
            mBoundMessenger = false;
        }

        if (mMusicThread != null && mMusicThread.isAlive()) {
            mMusicThread.interrupt();
        }

        unregisterCallReceiver();
    }

    // 서비스와 통신을 하기 위한 메신저
    private static Messenger mServiceMessenger;

    // 서비스에 연결(bind)을 요청받았는지 알려주는 플래그
    private boolean mBoundMessenger;

    private ServiceConnection mConnectionMessenger = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mServiceMessenger = new Messenger(service);
            mBoundMessenger = true;
            setMusicUI();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mServiceMessenger = null;
            mBoundMessenger = false;
        }
    };

    private void setMusicUI() {
        FileInfo playSong = musicList.get(mCurrentPosition);
        String song = playSong.getTitle();
        mTvAudioPlayerTitle.setText(song.substring(song.lastIndexOf("/") + 1, song.length()));
        Bitmap bm = FileThumbnail.getAudioThumbnail(getApplicationContext(), playSong.getTitle());

        if (bm != null) {
            if (playSong.getTitle().toLowerCase().lastIndexOf(".mp3") == -1) {
                mIvAudioPlayerPicture.setImageResource(R.drawable.audio_music_large);
            } else {
                mIvAudioPlayerPicture.setImageBitmap(bm);
            }
        } else {
            mIvAudioPlayerPicture.setImageResource(R.drawable.audio_music_large);
        }

        if (mMediaPlayer != null) {
            mMusicThread = getmMusicThread();
            mMusicHandler.postDelayed(mMusicThread, 100);
        }
    }

    private final MusicHandler mMusicHandler = new MusicHandler();

    public static class MusicHandler extends Handler {

        public MusicHandler() { }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_GET_MP:
                    // 서비스로부터 음악 플레이어의 정보를 가져옴.
                    mMediaPlayer = (MediaPlayer) msg.obj;
                    break;

                case MSG_NEXT_MP:
                    // 현재 곡 재생이 끝나면 자동으로 다음 곡을 재생함.
                    break;
                default:
                    super.handleMessage(msg);
            }
        }
    }

    private Thread mMusicThread;

    private Thread getmMusicThread() {
        return new Thread(new Runnable() {
            @Override
            public void run() {
            synchronized (mMediaPlayer) {
                if (mMediaPlayer != null) {
                    try {
                        if (AudioMessengerService.isPaused) {
                            mIbAudioPlayerPlay.setImageResource(android.R.drawable.ic_media_play);
                        } else {
                            mIbAudioPlayerPlay.setImageResource(android.R.drawable.ic_media_pause);
                        }

                        TimeLeft = mMediaPlayer.getCurrentPosition();
                        mTvAudioPlayerTimeLeft.setText(String.format("%02d : %02d",
                                        TimeUnit.MILLISECONDS.toMinutes((long) TimeLeft),
                                        TimeUnit.MILLISECONDS.toSeconds((long) TimeLeft) -
                                                TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS
                                                        .toMinutes((long) TimeLeft)))
                        );

                        TimeRight = mMediaPlayer.getDuration();
                        mSbAudioPlayerSeekbar.setMax((int) TimeRight);
                        mTvAudioPlayerTimeRight.setText(String.format("%02d : %02d",
                                        TimeUnit.MILLISECONDS.toMinutes((long) TimeRight),
                                        TimeUnit.MILLISECONDS.toSeconds((long) TimeRight) -
                                                TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS
                                                        .toMinutes((long) TimeRight)))
                        );

                        mSbAudioPlayerSeekbar.setProgress((int) TimeLeft);
                        mMusicHandler.postDelayed(mMusicThread, 100);

                    } catch (IllegalStateException e) {
                    }
                }
            }
            }
        });
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.audio_player_play:
                restartOrPause();
                break;

            case R.id.audio_player_forward:
                next();
                break;

            case R.id.audio_player_backward:
                previous();
                break;

            case R.id.audio_player_showlist:
                backToList();
                break;
        }
    }

    private void restartOrPause() {
        Intent songListActivity = new Intent(HOME + "AudioMessengerService.Play");
        sendBroadcast(songListActivity);
    }

    private void previous() {
        Intent songListActivity = new Intent(HOME + "AudioMessengerService.Previous");
        sendBroadcast(songListActivity);
    }

    private void next() {
        Intent songListActivity = new Intent(HOME + "AudioMessengerService.Next");
        sendBroadcast(songListActivity);
    }

    private void backToList() {
        Intent intent = new Intent();
        setResult(RESULT_OK, intent);
        finish();
    }

    private BroadcastReceiver mBRPlayer = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if ((HOME + "AudioPlayerActivity.STOP").equals(action)) {
                if (mBoundMessenger) {
                    unbindService(mConnectionMessenger);
                    mBoundMessenger = false;
                }
                finish();

            } else if ((HOME + "AudioPlayerActivity.songChanged").equals(action)) {
                mCurrentPosition = intent.getIntExtra("currentPosition", -1);
                playSong = musicList.get(mCurrentPosition);
                setMusicUI();
            }
        }
    };

    private void registerCallReceiver() {
        if(!mIsReceiverRegistered){
            IntentFilter filter = new IntentFilter();
            filter.addAction(HOME + "AudioPlayerActivity.STOP");
            filter.addAction(HOME + "AudioPlayerActivity.songChanged");
            registerReceiver(mBRPlayer, filter);

            mIsReceiverRegistered = true;
        }
    }

    private void unregisterCallReceiver() {
        if(mIsReceiverRegistered){
            unregisterReceiver(mBRPlayer);
            mIsReceiverRegistered = false;
        }
    }
}
