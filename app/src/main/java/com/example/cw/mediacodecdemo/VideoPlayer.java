package com.example.cw.mediacodecdemo;

import android.media.MediaCodec;
import android.media.MediaCodecList;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Surface;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;

public class VideoPlayer {

    private static final String TAG = "VideoPlayer";

    private File mFileSource;
    private Surface mOutputSurface;
    private FrameCallback mFrameCallback;
    private int mVideoWidth;
    private int mVideoHeight;
    private MediaExtractor mMediaExtractor;
    private MediaCodec mMediaCodec;
    private int trackIndex;
    private volatile boolean mIsStopRequested;
    private boolean mLoop;
    private MediaCodec.BufferInfo mBufferInfo;
    private int fps;

    public VideoPlayer(File mFileSource, Surface mOutputSurface, FrameCallback mFrameCallback) {
        this.mFileSource = mFileSource;
        this.mOutputSurface = mOutputSurface;
        this.mFrameCallback = mFrameCallback;
        mBufferInfo = new MediaCodec.BufferInfo();
        try {
            mMediaExtractor = new MediaExtractor();
            mMediaExtractor.setDataSource(mFileSource.toString());
            trackIndex = selectTrack(mMediaExtractor);
            if (trackIndex < 0){
                throw new RuntimeException("No Video track found in file: " + mFileSource);
            }
            mMediaExtractor.selectTrack(trackIndex);
            MediaFormat mediaFormat = mMediaExtractor.getTrackFormat(trackIndex);
            mVideoWidth = mediaFormat.getInteger(MediaFormat.KEY_WIDTH);
            mVideoHeight = mediaFormat.getInteger(MediaFormat.KEY_HEIGHT);
            Log.d(TAG, "VideoPlayer: width :" + mVideoWidth + ", height :"+ mVideoHeight);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private int selectTrack(MediaExtractor extractor){
        int numTracks = extractor.getTrackCount();
        for (int i=0; i<numTracks; i++){
            MediaFormat mediaFormat = extractor.getTrackFormat(i);
            String mime = mediaFormat.getString(MediaFormat.KEY_MIME);
            if (mime.startsWith("video/")){
                Log.d(TAG, "selectTrack: " + i);
                return i;
            }
        }
        return -1;
    }

    /**
     * Decodes the video stream, sending frames to the surface.
     * <p>
     * Does not return until video playback is complete, or we get a "stop" signal from
     * frameCallback.
     */
    private void play() throws IOException{
        if (!mFileSource.canRead()){
            throw new FileNotFoundException("can not read video file!");
        }
        MediaFormat mediaFormat = mMediaExtractor.getTrackFormat(trackIndex);
        String mime = mediaFormat.getString(MediaFormat.KEY_MIME);
        fps = mediaFormat.getInteger(MediaFormat.KEY_FRAME_RATE);
        String codecName = new MediaCodecList(MediaCodecList.ALL_CODECS).findDecoderForFormat(mediaFormat);
        if (codecName == null){
            Log.d(TAG, "play: can not find suitable codec!");
        }
        mMediaCodec = MediaCodec.createDecoderByType(mime);
        mMediaCodec.configure(mediaFormat, mOutputSurface, null, 0);
        mMediaCodec.start();
        doExtract(mMediaExtractor, mMediaCodec, trackIndex, mFrameCallback);
    }

    /**
     * Work loop.  We execute here until we run out of video or are told to stop.
     */
    private void doExtract(MediaExtractor extractor, MediaCodec decoder, int tractIndex, FrameCallback frameCallback){
        if (fps == 0){
            fps = 30;
        }
        final int TIMEOUT_USEC = 10000;

        boolean outputDone = false;
        boolean inputDone = false;

        long startMs = System.currentTimeMillis();
        while (!outputDone){
            if (mIsStopRequested){
                return;
            }

            //feed more data to the decoder
            if (!inputDone){
                int inputBufferIndex = decoder.dequeueInputBuffer(TIMEOUT_USEC);
                if (inputBufferIndex > 0){
                    ByteBuffer inputBuffer = decoder.getInputBuffer(inputBufferIndex);
                    // Read the sample data into the ByteBuffer.  This neither respects nor
                    // updates inputBuf's position, limit, etc.
                    int chunkSize = extractor.readSampleData(inputBuffer, 0);
                    if (chunkSize < 0){
                        //End of Stream
                        decoder.queueInputBuffer(inputBufferIndex, 0,0,0L,MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        inputDone = true;
                        Log.d(TAG, "doExtract: " + "inputDone");
                    }else {
                        if (extractor.getSampleTrackIndex() != tractIndex){
                            Log.d(TAG, "doExtract: " + "get wrong trackIndex!!!");
                        }
                        long presentationTimeUs = extractor.getSampleTime();
                        decoder.queueInputBuffer(inputBufferIndex, 0, chunkSize, presentationTimeUs, 0);
                        extractor.advance();
                    }
                }else {
                    Log.d(TAG, "doExtract: " + " buffer index not available");
                }
            }

            if (!outputDone) {
                int decoderStatus = decoder.dequeueOutputBuffer(mBufferInfo, TIMEOUT_USEC);
                if (decoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    // no output available yet
                    Log.d(TAG, "no output from decoder available");
                } else if (decoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat newFormat = decoder.getOutputFormat();
                    Log.d(TAG, "decoder output format changed: " + newFormat);
                } else if (decoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED){
                    Log.d(TAG, "doExtract: output buffers changed");
                } else if (decoderStatus < 0) {
                    throw new RuntimeException(
                            "unexpected result from decoder.dequeueOutputBuffer: " +
                                    decoderStatus);
                } else { // decoderStatus >= 0
                    boolean doLoop = false;
                    Log.d(TAG, "surface decoder given buffer " + decoderStatus +
                            " (size=" + mBufferInfo.size + ")");
                    if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        Log.d(TAG, "output EOS");
                        if (mLoop) {
                            doLoop = true;
                        } else {
                            outputDone = true;
                        }
                    }

                    boolean doRender = (mBufferInfo.size != 0);

                    // As soon as we call releaseOutputBuffer, the buffer will be forwarded
                    // to SurfaceTexture to convert to a texture.  We can't control when it
                    // appears on-screen, but we can manage the pace at which we release
                    // the buffers.
                    if (doRender && frameCallback != null) {
                        frameCallback.preRender(mBufferInfo.presentationTimeUs);
                    }
                    decodeDelay(mBufferInfo, startMs);
                    decoder.releaseOutputBuffer(decoderStatus, doRender);
                    if (doRender && frameCallback != null) {
                        frameCallback.postRender();
                    }

                    if (doLoop) {
                        Log.d(TAG, "Reached EOS, looping");
                        extractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
                        inputDone = false;
                        decoder.flush();    // reset decoder state
                        frameCallback.loopReset();
                    }
                }
            }
        }
        mMediaCodec.stop();
        mMediaCodec.release();
        mMediaExtractor.release();
    }

    private void decodeDelay(MediaCodec.BufferInfo bufferInfo, long startMs){
        long delayTime = bufferInfo.presentationTimeUs /1000 - (System.currentTimeMillis() -startMs);
        if (delayTime > 0){
            try {
                Thread.sleep(delayTime);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public int getVideoWidth(){
        return mVideoWidth;
    }

    public int getVideoHeight(){
        return mVideoHeight;
    }

    public void requestStop(){
        mIsStopRequested = true;
    }

    /**
     * Sets the loop mode.  If true, playback will loop forever.
     */
    public void setLoop(boolean loopMode){
        this.mLoop = loopMode;
    }

    public static class PlayTask implements Runnable{

        private static final int MSG_PLAY_STOPPED = 0;

        private VideoPlayer mPlayer;
        private PlayFeedback mFeedback;
        private boolean mDoLoop;
        private Thread mThread;
        private LocalHandler mLocalHandler;

        private final Object mStopLock = new Object();
        private boolean mStopped = false;

        public PlayTask(VideoPlayer mPlayer, PlayFeedback mFeedback) {
            this.mPlayer = mPlayer;
            this.mFeedback = mFeedback;
            mLocalHandler = new LocalHandler();
        }

        /**
         * Sets the loop mode.  If true, playback will loop forever.
         */
        public void setLoopMode(boolean loopMode) {
            mDoLoop = loopMode;
        }

        public void execute(){
            mPlayer.setLoop(mDoLoop);
            mThread = new Thread(this, "VideoPlayer");
            mThread.start();
        }

        /**
         * Requests that the player stop.
         * <p>
         * Called from arbitrary thread.
         */
        public void requestStop() {
            mPlayer.requestStop();
        }

        @Override
        public void run() {
            try {
                mPlayer.play();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                synchronized (mStopLock){
                    mStopped = true;
                    mStopLock.notifyAll();
                }
                mLocalHandler.sendMessage(mLocalHandler.obtainMessage(MSG_PLAY_STOPPED, mFeedback));
            }
        }
    }

    private static class LocalHandler extends Handler {

        @Override
        public void handleMessage(Message msg) {
            int what = msg.what;
//            switch (what){
//                case
//            }
        }
    }

}
