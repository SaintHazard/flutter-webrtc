package com.cloudwebrtc.webrtc;
import android.content.Context;
import org.webrtc.CapturerObserver;
import org.webrtc.JavaI420Buffer;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoFrame;

import android.graphics.Bitmap;
import android.graphics.Matrix;
import java.nio.ByteBuffer;
import org.webrtc.VideoFrame.I420Buffer;

public class OpenCVVideoCapturer implements VideoCapturer {
    private final VideoCapturer videoCapturer;
    private SurfaceTextureHelper surfaceTextureHelper;
    private OpenCVHelper openCVHelper;
    private Context context;
    private static final String TAG = "OpenCVVideoCapturer";

    public OpenCVVideoCapturer(VideoCapturer videoCapturer, Context context) {
        this.videoCapturer = videoCapturer;
        this.openCVHelper = new OpenCVHelper(context);
        this.context = context;
    }

    @Override
    public void initialize(SurfaceTextureHelper surfaceTextureHelper, Context context, CapturerObserver capturerObserver) {
        this.surfaceTextureHelper = surfaceTextureHelper;
        videoCapturer.initialize(surfaceTextureHelper, context, new CapturerObserver() {
            @Override
            public void onCapturerStarted(boolean success) {
                capturerObserver.onCapturerStarted(success);
            }

            @Override
            public void onCapturerStopped() {
                capturerObserver.onCapturerStopped();
            }

            @Override
            public void onFrameCaptured(VideoFrame frame) {
                Bitmap bitmap = convertVideoFrameToBitmap(frame);
                Bitmap newBitmap = openCVHelper.removeBlemishes(bitmap);

                VideoFrame processedFrame = convertBitmapToVideoFrame(newBitmap);
                capturerObserver.onFrameCaptured(processedFrame);


//                openCVHelper.removeBlemishes(bitmap, new BitmapCallback() {
//                    @Override
//                    public void onBitmapProcessed(Bitmap bitmap) {
//                        VideoFrame processedFrame = convertBitmapToVideoFrame(bitmap);
//                        capturerObserver.onFrameCaptured(processedFrame);
//                    }
//
//                    @Override
//                    public void onError(Exception e) {
//                        capturerObserver.onFrameCaptured(frame);
//                    }
//                });
            }
        });
    }

    VideoFrame convertBitmapToVideoFrame(Bitmap bitmap) {
        // Create a new Bitmap with the same size as the original
        Bitmap scaledBitmap = Bitmap.createScaledBitmap(bitmap, bitmap.getWidth(), bitmap.getHeight(), false);

        // Convert Bitmap to YUV
        int width = scaledBitmap.getWidth();
        int height = scaledBitmap.getHeight();
        int[] argb = new int[width * height];
        scaledBitmap.getPixels(argb, 0, width, 0, 0, width, height);

        ByteBuffer yBuffer = ByteBuffer.allocateDirect(width * height);
        ByteBuffer uBuffer = ByteBuffer.allocateDirect(width * height / 4);
        ByteBuffer vBuffer = ByteBuffer.allocateDirect(width * height / 4);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argbValue = argb[y * width + x];
                int R = (argbValue >> 16) & 0xFF;
                int G = (argbValue >> 8) & 0xFF;
                int B = argbValue & 0xFF;

                // Convert RGB to YUV
                int Y = (int) (0.299 * R + 0.587 * G + 0.114 * B);
                int U = (int) (-0.169 * R - 0.331 * G + 0.5 * B + 128);
                int V = (int) (0.5 * R - 0.419 * G - 0.081 * B + 128);

                yBuffer.put(y * width + x, (byte) Y);
                if (y % 2 == 0 && x % 2 == 0) {
                    uBuffer.put((y / 2) * (width / 2) + (x / 2), (byte) U);
                    vBuffer.put((y / 2) * (width / 2) + (x / 2), (byte) V);
                }
            }
        }

        I420Buffer i420Buffer = JavaI420Buffer.wrap(width, height,
                yBuffer, width,
                uBuffer, width / 2,
                vBuffer, width / 2, null);

        // Create VideoFrame from I420Buffer
        VideoFrame videoFrame = new VideoFrame(i420Buffer, 0, System.nanoTime());

        return videoFrame;
    }


    Bitmap convertVideoFrameToBitmap(VideoFrame videoFrame) {
        I420Buffer i420Buffer = videoFrame.getBuffer().toI420();
        int width = i420Buffer.getWidth();
        int height = i420Buffer.getHeight();
        ByteBuffer yBuffer = i420Buffer.getDataY();
        ByteBuffer uBuffer = i420Buffer.getDataU();
        ByteBuffer vBuffer = i420Buffer.getDataV();

        int[] argb = new int[width * height];

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int Y = yBuffer.get(y * i420Buffer.getStrideY() + x) & 0xFF;
                int U = uBuffer.get((y / 2) * i420Buffer.getStrideU() + (x / 2)) & 0xFF;
                int V = vBuffer.get((y / 2) * i420Buffer.getStrideV() + (x / 2)) & 0xFF;

                // YUV to RGB conversion
                int R = (int) (Y + 1.370705 * (V - 128));
                int G = (int) (Y - 0.698001 * (V - 128) - 0.337633 * (U - 128));
                int B = (int) (Y + 1.732446 * (U - 128));

                // Clamping values to [0, 255]
                R = R < 0 ? 0 : R > 255 ? 255 : R;
                G = G < 0 ? 0 : G > 255 ? 255 : G;
                B = B < 0 ? 0 : B > 255 ? 255 : B;

                // ARGB value
                argb[y * width + x] = 0xFF000000 | (R << 16) | (G << 8) | B;
            }
        }

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bitmap.setPixels(argb, 0, width, 0, 0, width, height);

        // Apply rotation based on video frame rotation
        Matrix matrix = new Matrix();
        matrix.postRotate(videoFrame.getRotation());

        Bitmap rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true);

        i420Buffer.release();

        return rotatedBitmap;
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