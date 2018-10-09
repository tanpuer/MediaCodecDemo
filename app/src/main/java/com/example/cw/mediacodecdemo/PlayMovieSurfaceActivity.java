package com.example.cw.mediacodecdemo;

import android.graphics.Point;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.RelativeLayout;

import java.io.File;

public class PlayMovieSurfaceActivity extends AppCompatActivity implements SurfaceHolder.Callback, FrameCallback, PlayFeedback{

    private static final String TAG = "SurfaceActivity";

    private SurfaceView mSurfaceView;
    private VideoPlayer mVideoPlayer;
    private AudioPlayer mAudioPlayer;
    private VideoPlayer.PlayTask mVideoPlayTask;
    private AudioPlayer.PlayAudioTask mAudioPlayTask;
    private int movieWidth;
    private int movieHeight;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_play_movie);
        mSurfaceView = findViewById(R.id.surface_view);
        mSurfaceView.getHolder().addCallback(this);
    }

    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {
        File file = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/test.mp4");
        Surface surface = surfaceHolder.getSurface();
        mVideoPlayer = new VideoPlayer(file, surface, this);
        mAudioPlayer = new AudioPlayer(file);

        movieWidth = mVideoPlayer.getVideoWidth();
        movieHeight = mVideoPlayer.getVideoHeight();
        resetSurfaceSize();
        Log.d(TAG, "surfaceCreated width width: "+ movieWidth + ", height: "+ movieHeight);
        mVideoPlayTask = new VideoPlayer.PlayTask(mVideoPlayer, this);
        mVideoPlayTask.execute();

        mAudioPlayTask = new AudioPlayer.PlayAudioTask(mAudioPlayer);
        mAudioPlayTask.execute();

    }

    @Override
    protected void onPause() {
        super.onPause();
        mVideoPlayTask.requestStop();
        mAudioPlayTask.requestPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mVideoPlayTask = null;
        mAudioPlayTask = null;
        mAudioPlayer = null;
        mVideoPlayer = null;
    }

    private void resetSurfaceSize(){
        if (movieHeight==0 || movieWidth ==0){
            return;
        }
        Point point = new Point();
        getWindow().getWindowManager().getDefaultDisplay().getSize(point);
        int width = point.x;
        int height = point.x * movieHeight/movieWidth;
        mSurfaceView.setLayoutParams(new RelativeLayout.LayoutParams(width, height));
    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {

    }

    @Override
    public void preRender(long presentationTimeUsec) {

    }

    @Override
    public void postRender() {

    }

    @Override
    public void loopReset() {

    }

    @Override
    public void playbackStopped() {

    }
}
