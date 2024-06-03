package com.cloudwebrtc.webrtc;

import android.graphics.PointF;
import android.graphics.Rect;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlur;
import android.util.Log;
//import org.opencv.core.Rect;

import org.opencv.android.Utils;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;
import org.opencv.photo.Photo;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceContour;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;
import com.google.mlkit.vision.face.FaceLandmark;


public class OpenCVHelper {
    private HandlerThread handlerThread;
    private Handler handler;

    private ExecutorService executorService;


    public OpenCVHelper(Context context) {
//        handlerThread = new HandlerThread("BitmapProcessorThread");
//        start();
//        handler = new Handler(handlerThread.getLooper());

        executorService = Executors.newFixedThreadPool(4);
    }

    void processBitmapAsync(final Bitmap bitmap, final BitmapCallback callback) {
        executorService.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    Bitmap newBitmap = removeBlemishes(bitmap);
                    callback.onBitmapProcessed(newBitmap);
//                    removeBlemishes(bitmap, callback);
                } catch (Exception e) {
                    callback.onError(e);
                }
            }
        });
    }

//    void processBitmapAsync(final Bitmap bitmap, final BitmapCallback callback) {
//        handler.post(new Runnable() {
//            @Override
//            public void run() {
//                removeBlemishes(bitmap, callback);
////                try {
////                    // Your bitmap processing logic
////                    Bitmap processedBitmap = removeBlemishes(bitmap);
////                    callback.onBitmapProcessed(processedBitmap);
////                } catch (Exception e) {
////                    callback.onError(e);
////                }
//            }
//        });
//    }

    void start() {
//        if(handlerThread.isAlive() == false) {
//            handlerThread.start();
//        }
    }

    void stop() {
//        handlerThread.quitSafely();
    }

    Bitmap removeBlemishes(Bitmap bitmap) {
        Mat faceMat = new Mat(bitmap.getHeight(), bitmap.getWidth(), CvType.CV_8UC4);
        Utils.bitmapToMat(bitmap, faceMat);

        // Step 7: Apply blemish removal (Gaussian Blur in this example)
        if (faceMat.type() != CvType.CV_8UC3) {
            Imgproc.cvtColor(faceMat, faceMat, Imgproc.COLOR_RGBA2RGB);
        }

        // Step 8: Apply blemish removal using Bilateral Filter
        Mat filteredMat = new Mat();

        // Step 9: Apply blemish removal using Bilateral Filter
        Imgproc.bilateralFilter(faceMat, filteredMat, 9, 50, 50);

        // Step 8: Convert processed Mat back to Bitmap

        Bitmap outputBitmap = Bitmap.createBitmap(filteredMat.cols(), filteredMat.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(filteredMat, outputBitmap);

        faceMat.release();
        filteredMat.release();

        return outputBitmap;
    }



}