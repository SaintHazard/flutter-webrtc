package com.cloudwebrtc.webrtc;
import org.webrtc.VideoFrame;

public interface BitmapCallback {
    void onBitmapProcessed(VideoFrame videoFrame);
    void onError(Exception e);
}