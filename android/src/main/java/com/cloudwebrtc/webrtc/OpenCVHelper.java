package com.cloudwebrtc.webrtc;

import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
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

        executorService = Executors.newFixedThreadPool(8);
    }

    void processBitmapAsync(final Bitmap bitmap, final BitmapCallback callback) {
        executorService.submit(new Runnable() {
            @Override
            public void run() {
                try {
//                    Bitmap newBitmap = removeBlemishes(bitmap);
//                    callback.onBitmapProcessed(newBitmap);
                    removeBlemishes(bitmap, callback);
                } catch (Exception e) {
                    callback.onError(e);
                }
            }
        });
    }

    void removeBlemishes(Bitmap bitmap, BitmapCallback callback) {
        InputImage image = InputImage.fromBitmap(bitmap, 0);

        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
                .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
                .build();

        FaceDetector detector = FaceDetection.getClient(options);

        detector.process(image)
                .addOnSuccessListener(faces -> {
                    Bitmap resultBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true);

                    if (!faces.isEmpty()) {

                        for (Face face : faces) {
                            List<FaceContour> contours = face.getAllContours();

                            if (contours != null && !contours.isEmpty()) {
                                Path facePath = new Path();

                                FaceContour contour = face.getContour(FaceContour.FACE);
                                List<PointF> points = contour.getPoints();

                                if(points.size() > 0) {
                                    facePath.moveTo(points.get(0).x, points.get(0).y);
                                    for (int i = 1; i < points.size(); i++) {
                                        facePath.lineTo(points.get(i).x, points.get(i).y);
                                    }
                                    facePath.close();

                                    Rect faceBounds = face.getBoundingBox();

                                    if(faceBounds.left < 0) {
                                        faceBounds.left = 0;
                                    }

                                    if(faceBounds.top < 0) {
                                        faceBounds.top = 0;
                                    }

                                    if(faceBounds.left + faceBounds.width() <= bitmap.getWidth() && faceBounds.top + faceBounds.height() <= bitmap.getHeight()) {
                                        Bitmap faceBitmap = Bitmap.createBitmap(bitmap, faceBounds.left, faceBounds.top, faceBounds.width(), faceBounds.height());
                                        Bitmap blurredFaceBitmap = removeBlemishes(faceBitmap);

                                        Canvas faceCanvas = new Canvas(resultBitmap);
                                        Paint maskPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

                                        maskPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));

                                        faceCanvas.drawBitmap(blurredFaceBitmap, faceBounds.left, faceBounds.top, null);
                                        faceCanvas.drawPath(facePath, maskPaint);

                                    }
                                }
                            }
                        }
                    }
                    callback.onBitmapProcessed(resultBitmap);
                })
                .addOnFailureListener(e -> {
                    // Handle error
                    callback.onError(e);
                });
    }

//    void removeBlemishes(Bitmap bitmap, BitmapCallback callback) {
//        InputImage image = InputImage.fromBitmap(bitmap, 0);
//
//        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
//                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
//                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
//                .enableTracking()
//                .build();
//
//        FaceDetector detector = FaceDetection.getClient(options);
//
//        detector.process(image)
//                .addOnSuccessListener(faces -> {
//                    Bitmap mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true);
//
//                    if (!faces.isEmpty()) {
//                        Canvas canvas = new Canvas(mutableBitmap);
//
//                        for (Face face : faces) {
//                            Rect bounds = face.getBoundingBox();
//
//                            Log.d("Tag", "left: " + bounds.left + ", width: " + bounds.width());
//
//                            if(bounds.left < 0) {
//                                bounds.left = 0;
//                            }
//
//                            if(bounds.top < 0) {
//                                bounds.top = 0;
//                            }
//
//                            if(bounds.left + bounds.width() <= bitmap.getWidth() && bounds.top + bounds.height() <= bitmap.getHeight()) {
//                                Bitmap faceBitmap = Bitmap.createBitmap(bitmap, bounds.left, bounds.top, bounds.width(), bounds.height());
//                                Bitmap resultBitmap = removeBlemishes(faceBitmap);
//                                // Copy result back to original bitmap
//                                canvas.drawBitmap(resultBitmap, bounds.left, bounds.top, null);
//                            }
//                        }
//                    }
//                    callback.onBitmapProcessed(mutableBitmap);
//                })
//                .addOnFailureListener(e -> {
//                    // Handle error
//                    callback.onError(e);
//                });
//    }

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
        Imgproc.bilateralFilter(faceMat, filteredMat, 15, 75, 50);

        // Step 8: Convert processed Mat back to Bitmap

        Bitmap outputBitmap = Bitmap.createBitmap(filteredMat.cols(), filteredMat.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(filteredMat, outputBitmap);

        faceMat.release();
        filteredMat.release();

        return outputBitmap;
    }



}