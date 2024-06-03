package com.cloudwebrtc.webrtc;


import android.graphics.Bitmap;

public interface BitmapCallback {
    void onBitmapProcessed(Bitmap bitmap);
    void onError(Exception e);
}