package com.example.cw.mediacodecdemo;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaCodecList;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;

public class AudioPlayer implements MediaTimeProvider{

    private static final String TAG = "AudioPlayer";

    private File mFileSource;
    private MediaExtractor mMediaExtractor;
    private MediaCodec mAudioCodec;
    private MediaCodec.BufferInfo mBufferInfo;
    private int trackIndex;
    private AudioTrack mAudioTrack;
    private boolean mIsRequestPaused;

    public AudioPlayer(File mFileSource) {
        this.mFileSource = mFileSource;
        mBufferInfo = new MediaCodec.BufferInfo();
        try {
            mMediaExtractor = new MediaExtractor();
            mMediaExtractor.setDataSource(mFileSource.toString());
            trackIndex = selectTrack(mMediaExtractor);
            if (trackIndex < 0){
                throw new RuntimeException("could not find audio track index\n");
            }
            mMediaExtractor.selectTrack(trackIndex);
            MediaFormat mediaFormat = mMediaExtractor.getTrackFormat(trackIndex);
            int audioChannels = mediaFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
            int audioSampleRate = mediaFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
            int minBufferSize = AudioTrack.getMinBufferSize(audioSampleRate, audioChannels==1?AudioFormat.CHANNEL_OUT_MONO:AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT);
            mAudioTrack = new AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    audioSampleRate,
                    audioChannels==1?AudioFormat.CHANNEL_OUT_MONO:AudioFormat.CHANNEL_OUT_STEREO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minBufferSize,
                    AudioTrack.MODE_STREAM
            );
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private int selectTrack(MediaExtractor extractor){
        int numTracks = extractor.getTrackCount();
        for (int i=0; i<numTracks; i++){
            MediaFormat mediaFormat = extractor.getTrackFormat(i);
            String mime = mediaFormat.getString(MediaFormat.KEY_MIME);
            if (mime.startsWith("audio/")){
                Log.d(TAG, "selectTrack: " + i);
                return i;
            }
        }
        return -1;
    }

    private void play() throws IOException{
        if (!mFileSource.canRead()){
            throw new FileNotFoundException("can not read audio file!");
        }
        mAudioTrack.play();
        MediaFormat mediaFormat = mMediaExtractor.getTrackFormat(trackIndex);
        String mime = mediaFormat.getString(MediaFormat.KEY_MIME);
        String codecName = new MediaCodecList(MediaCodecList.ALL_CODECS).findDecoderForFormat(mediaFormat);
        if (codecName == null){
            Log.d(TAG, "play: " + " can bot find suitable audio codec!");
        }
        mAudioCodec = MediaCodec.createDecoderByType(mime);
        mAudioCodec.configure(mediaFormat, null, null, 0);
        mAudioCodec.start();
        doExtract(mMediaExtractor, mAudioCodec, trackIndex);
    }

    private void doExtract(MediaExtractor extractor, MediaCodec decoder, int trackIndex){
        final int TIMEOUT_USEC = 10000;
        boolean inputDone = false;
        boolean outputDone = false;
        long startMs = System.currentTimeMillis();
        while (!outputDone){
            if (mIsRequestPaused){
                return;
            }
            if (!inputDone){
                int inputBufferIndex = decoder.dequeueInputBuffer(TIMEOUT_USEC);
                if (inputBufferIndex > 0){
                    ByteBuffer byteBuffer = decoder.getInputBuffer(inputBufferIndex);
                    int chunkSize = extractor.readSampleData(byteBuffer, 0);
                    if (chunkSize <0){
                        //end of stream
                        decoder.queueInputBuffer(inputBufferIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        inputDone = true;
                        Log.d(TAG, "doExtract: input done");
                    }else {
                        long presentationTimeUs = extractor.getSampleTime();
                        decoder.queueInputBuffer(inputBufferIndex, 0 ,chunkSize, presentationTimeUs, 0);
                        extractor.advance();
                    }
                }else {
                    Log.d(TAG, "doExtract: input buffer index not available" );
                }
            }

            if (!outputDone){
                int decoderStatus = decoder.dequeueOutputBuffer(mBufferInfo, TIMEOUT_USEC);
                if (decoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER){

                }else if (decoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED){

                }else if (decoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){

                }else if (decoderStatus <0){

                }else {
                    if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) !=0){
                        Log.d(TAG, "output EOS");
                        outputDone = true;
                    }
                    ByteBuffer byteBuffer = decoder.getOutputBuffer(decoderStatus);
//                    decodeDelay(mBufferInfo, startMs);
                    // 如果解码成功，则将解码后的音频PCM数据用AudioTrack播放出来
                    byte[] mAudioOutTempBuf;
                    if (mBufferInfo.size > 0) {
                        mAudioOutTempBuf = new byte[mBufferInfo.size];
                        byteBuffer.position(0);
                        byteBuffer.get(mAudioOutTempBuf, 0, mBufferInfo.size);
                        byteBuffer.clear();
                        if (mAudioTrack != null)
                            mAudioTrack.write(mAudioOutTempBuf, 0, mBufferInfo.size);
                    }
                    // 释放资源
                    decoder.releaseOutputBuffer(decoderStatus, false);
                }
            }
        }
        mAudioCodec.stop();
        mAudioCodec.release();
        mMediaExtractor.release();
    }

    private void decodeDelay(MediaCodec.BufferInfo bufferInfo, long startMs){
        long delayTime = bufferInfo.presentationTimeUs /1000 - (System.currentTimeMillis() -startMs);
        if (delayTime > 0){
            Log.d(TAG, "decodeDelay: audio delay "+ delayTime);
            try {
                Thread.sleep(delayTime);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public void requestStop(){
        mIsRequestPaused= true;
        if (mAudioTrack != null){
            mAudioTrack.stop();
            mAudioTrack = null;
        }
    }

    public void requestPause(){
        mIsRequestPaused= true;
        if (mAudioTrack != null){
            mAudioTrack.pause();
        }
    }

    @Override
    public long getAudioTimeUs() {
        if (mAudioTrack != null){
            int numFramePlayed = mAudioTrack.getPlaybackHeadPosition();
            return (numFramePlayed *1000000L) / mAudioTrack.getSampleRate();
        }
        return -1L;
    }

    public static class PlayAudioTask implements Runnable{

        private AudioPlayer mAudioPlayer;
        private Thread mThread;

        public PlayAudioTask(AudioPlayer mAudioPlayer) {
            this.mAudioPlayer = mAudioPlayer;
        }

        public void execute(){
            mThread = new Thread(this, "Play Audio");
            mThread.start();
        }

        @Override
        public void run() {
            try {
                mAudioPlayer.play();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public boolean isStopped(){
            return mAudioPlayer.mIsRequestPaused;
        }

        public void requestPause() {
            mAudioPlayer.requestStop();
        }
    }
}
