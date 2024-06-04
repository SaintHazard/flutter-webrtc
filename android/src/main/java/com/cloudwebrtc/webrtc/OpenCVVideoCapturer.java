package com.cloudwebrtc.webrtc;
import android.content.Context;

import org.opencv.android.OpenCVLoader;
import org.webrtc.CapturerObserver;
import org.webrtc.JavaI420Buffer;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoFrame;

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.util.Log;

import java.nio.ByteBuffer;
import org.webrtc.VideoFrame.I420Buffer;

public class OpenCVVideoCapturer implements VideoCapturer {
    private final VideoCapturer videoCapturer;
    private OpenCVHelper openCVHelper;


    private static final String TAG = "OpenCVVideoCapturer";

    static {
        if (!OpenCVLoader.initLocal()) {
            Log.d("OpenCV", "OpenCV initialization failed");
        } else {
            Log.d("OpenCV", "OpenCV initialization succeeded");
        }
    }

    public OpenCVVideoCapturer(VideoCapturer videoCapturer, Context context) {
        this.videoCapturer = videoCapturer;
        this.openCVHelper = new OpenCVHelper(context);
    }

    @Override
    public void initialize(SurfaceTextureHelper surfaceTextureHelper, Context context, CapturerObserver capturerObserver) {
        videoCapturer.initialize(surfaceTextureHelper, context, new CapturerObserver() {
            @Override
            public void onCapturerStarted(boolean success) {
                capturerObserver.onCapturerStarted(success);
            }

            @Override
            public void onCapturerStopped() {
                capturerObserver.onCapturerStopped();
            }

//            @Override
//            public void onFrameCaptured(VideoFrame frame) {
//                frameCount++;
//                if (frameCount % FRAME_DROP_INTERVAL != 0) {
//                    // 드롭된 프레임은 원본 프레임을 그대로 전달
//                    capturerObserver.onFrameCaptured(frame);
//                    return;
//                }
//
//                Bitmap bitmap = convertVideoFrameToBitmap(frame);
//                VideoFrame processedFrame = convertBitmapToVideoFrame(bitmap);
//                capturerObserver.onFrameCaptured(frame);
//                processedFrame.release();
//            }


//            @Override
            public void onFrameCaptured(VideoFrame frame) {
                openCVHelper.processBitmapAsync(frame, new BitmapCallback() {
                    @Override
                    public void onBitmapProcessed(VideoFrame processedFrame) {
                        capturerObserver.onFrameCaptured(processedFrame);
                    }

                    @Override
                    public void onError(Exception e) {
                        e.printStackTrace();
                    }
                });
            }
        });
    }



    @Override
    public void startCapture(int width, int height, int framerate) {
        videoCapturer.startCapture(width, height, framerate);
    }

    @Override
    public void stopCapture() throws InterruptedException {
        videoCapturer.stopCapture();
    }

    @Override
    public void changeCaptureFormat(int width, int height, int framerate) {
        videoCapturer.changeCaptureFormat(width, height, framerate);
    }

    @Override
    public void dispose() {
        videoCapturer.dispose();
    }

    @Override
    public boolean isScreencast() {
        return videoCapturer.isScreencast();
    }
}