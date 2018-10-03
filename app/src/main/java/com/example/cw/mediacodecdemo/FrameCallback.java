package com.example.cw.mediacodecdemo;

public interface FrameCallback {

    void preRender(long presentationTimeUsec);

    void postRender();

    void loopReset();

}
