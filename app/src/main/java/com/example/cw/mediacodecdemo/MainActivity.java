package com.example.cw.mediacodecdemo;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {

    private static final int WRITTEN_REQUEST_CODE = 1001;
    private Button mPlayVideo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mPlayVideo = findViewById(R.id.play_video_btn);
        requestWrittenRequest();

        mPlayVideo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                playVideo();
            }
        });
    }

    private void requestWrittenRequest(){
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.M){
            int writtenPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
            if (writtenPermission != PackageManager.PERMISSION_GRANTED){
                requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, WRITTEN_REQUEST_CODE);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode){
            case WRITTEN_REQUEST_CODE:{
                if (grantResults.length >0 && grantResults[0] == PackageManager.PERMISSION_GRANTED){
                    break;
                }else {
                    Toast.makeText(this, "written permission must be granted!", Toast.LENGTH_LONG).show();
                }
            }
            default:
                break;
        }
    }

    private void playVideo(){
        Intent intent = new Intent(this, PlayMovieSurfaceActivity.class);
        startActivity(intent);
    }
}
