package com.cloudwebrtc.webrtc;

import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.YuvImage;

import org.opencv.android.Utils;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import org.webrtc.JavaI420Buffer;
import org.webrtc.VideoFrame;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceContour;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

interface OnProcessingCompleteListener {
    void onProcessingComplete(Bitmap result);
}

public class OpenCVHelper {
    private ExecutorService executorService;
    public OpenCVHelper(Context context) {
        executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    }

    void processBitmapAsync(final VideoFrame videoFrame, final boolean useBitmap, final BitmapCallback callback) {
        if(useBitmap == false) {
            callback.onBitmapProcessed(videoFrame);
            return;
        }
//        Mat mat = convertVideoFrameToMat(videoFrame);
//        VideoFrame result = convertMatToVideoFrame(mat);
//
//        callback.onBitmapProcessed(result);

        Bitmap bitmap = convertVideoFrameToBitmap(videoFrame);

        executorService.submit(() -> {
            try {
                boolean faceDetector = true;
                boolean filter = true;
                removeBlemishes(bitmap, callback, faceDetector, filter);
            } catch (Exception e) {
                callback.onError(e);
            }
        });
    }

    void removeBlemishes(Bitmap bitmap, BitmapCallback callback, boolean withFaceDetector, boolean withFilter) {
        InputImage image = InputImage.fromBitmap(bitmap, 0);

        if(withFaceDetector == false && withFilter == false) {
            VideoFrame processedFrame = convertBitmapToVideoFrame(bitmap);
            callback.onBitmapProcessed(processedFrame);
            processedFrame.release();
            return;
        }
        else if(withFaceDetector == false && withFilter) {
            Bitmap blurredFaceBitmap = processImage(bitmap);
            Bitmap resultBitmap = blurredFaceBitmap.copy(Bitmap.Config.ARGB_8888, true);
            VideoFrame processedFrame = convertBitmapToVideoFrame(resultBitmap);
            callback.onBitmapProcessed(processedFrame);
            processedFrame.release();
            return;
        }

        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
                .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
                .build();

        FaceDetector detector = FaceDetection.getClient(options);

        detector.process(image)
                .addOnSuccessListener(faces -> {
                    if(withFilter == false || faces.isEmpty() == true) {
                        VideoFrame processedFrame = convertBitmapToVideoFrame(bitmap);
                        callback.onBitmapProcessed(processedFrame);
                        processedFrame.release();
                        return;
                    }

                    Bitmap resultBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true);

                    // 병렬 처리를 위한 태스크 리스트 생성
                    List<Callable<Void>> tasks = new ArrayList<>();

                    for (Face face : faces) {
                        List<FaceContour> contours = face.getAllContours();

                        if (contours != null && !contours.isEmpty()) {
                            Path facePath = new Path();

                            FaceContour contour = face.getContour(FaceContour.FACE);
                            List<PointF> points = contour.getPoints();

                            if (points.size() > 0) {
                                facePath.moveTo(points.get(0).x, points.get(0).y);
                                for (int i = 1; i < points.size(); i++) {
                                    facePath.lineTo(points.get(i).x, points.get(i).y);
                                }
                                facePath.close();

                                // 눈과 입술 윤곽선 탐지
                                Path eyesAndMouthPath = new Path();
                                for (int contourType : new int[]{FaceContour.LEFT_EYE, FaceContour.RIGHT_EYE, FaceContour.UPPER_LIP_TOP, FaceContour.UPPER_LIP_BOTTOM, FaceContour.LOWER_LIP_TOP, FaceContour.LOWER_LIP_BOTTOM}) {
                                    FaceContour eyesAndMouthContour = face.getContour(contourType);
                                    if (eyesAndMouthContour != null) {
                                        List<PointF> eyesAndMouthPoints = eyesAndMouthContour.getPoints();
                                        if (!eyesAndMouthPoints.isEmpty()) {
                                            eyesAndMouthPath.moveTo(eyesAndMouthPoints.get(0).x, eyesAndMouthPoints.get(0).y);
                                            for (int i = 1; i < eyesAndMouthPoints.size(); i++) {
                                                eyesAndMouthPath.lineTo(eyesAndMouthPoints.get(i).x, eyesAndMouthPoints.get(i).y);
                                            }
                                            eyesAndMouthPath.close();
                                        }
                                    }
                                }

                                Rect faceBounds = face.getBoundingBox();

                                if (faceBounds.left < 0) {
                                    faceBounds.left = 0;
                                }

                                if (faceBounds.top < 0) {
                                    faceBounds.top = 0;
                                }

                                if (faceBounds.left + faceBounds.width() <= resultBitmap.getWidth() && faceBounds.top + faceBounds.height() <= resultBitmap.getHeight()) {

                                    Bitmap faceBitmap = Bitmap.createBitmap(resultBitmap, faceBounds.left, faceBounds.top, faceBounds.width(), faceBounds.height());

                                    // 각 얼굴 처리 작업을 Callable로 추가
                                    tasks.add(new Callable<Void>() {
                                        @Override
                                        public Void call() {
                                            Bitmap blurredFaceBitmap = processImage(faceBitmap);

                                            Bitmap maskBitmap = Bitmap.createBitmap(resultBitmap.getWidth(), resultBitmap.getHeight(), Bitmap.Config.ARGB_8888);
                                            Canvas maskCanvas = new Canvas(maskBitmap);
                                            Paint maskPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                                            maskPaint.setColor(Color.BLACK);
                                            maskCanvas.drawPath(facePath, maskPaint);
                                            maskPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
                                            maskCanvas.drawPath(eyesAndMouthPath, maskPaint);

                                            Bitmap maskedBlurredFaceBitmap = Bitmap.createBitmap(resultBitmap.getWidth(), resultBitmap.getHeight(), Bitmap.Config.ARGB_8888);
                                            Canvas maskedCanvas = new Canvas(maskedBlurredFaceBitmap);
                                            maskedCanvas.drawBitmap(blurredFaceBitmap, faceBounds.left, faceBounds.top, null);
                                            maskPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
                                            maskedCanvas.drawBitmap(maskBitmap, 0, 0, maskPaint);

                                            synchronized (resultBitmap) {
                                                Canvas resultCanvas = new Canvas(resultBitmap);
                                                resultCanvas.drawBitmap(maskedBlurredFaceBitmap, 0, 0, null);
                                            }

                                            return null;
                                        }
                                    });
                                }
                            }
                        }
                    }

                    // 모든 얼굴 처리 작업을 병렬로 실행
                    try {
                        executorService.invokeAll(tasks);
                    } catch (InterruptedException e) {
                        callback.onError(e);
                    }

                    VideoFrame processedFrame = convertBitmapToVideoFrame(resultBitmap);
                    callback.onBitmapProcessed(processedFrame);
                    processedFrame.release();
                })
                .addOnFailureListener(e -> {
                    // Handle error
                    callback.onError(e);
                });
    }

    public static Mat bitmapToMat(Bitmap bitmap) {
        Mat mat = new Mat(bitmap.getHeight(), bitmap.getWidth(), CvType.CV_8UC4);
        Utils.bitmapToMat(bitmap, mat);

        if (mat.type() != CvType.CV_8UC3) {
            Imgproc.cvtColor(mat, mat, Imgproc.COLOR_RGBA2RGB);
        }

        return mat;
    }

    // Mat 객체를 Bitmap으로 변환
    public static Bitmap matToBitmap(Mat mat) {
        Bitmap bitmap = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(mat, bitmap);
        return bitmap;
    }

    // 이미지를 부드럽게 하고 화사하게 만들기
    public static Bitmap processImage(Bitmap bitmap) {
        Mat mat = null;
        Mat filteredMat = null;
        Bitmap resultBitmap = null;

        try {
            mat = bitmapToMat(bitmap);

            // Bilateral Filter 적용
            filteredMat = applyBilateralFilter(mat);
            resultBitmap = matToBitmap(filteredMat);
        } finally {
            // 사용한 Mat 객체 해제
            if (mat != null) mat.release();
            if (filteredMat != null) filteredMat.release();
        }

        return resultBitmap;
    }

    private static Mat applyBilateralFilter(Mat mat) {
        Mat filteredMat = new Mat();
        Imgproc.bilateralFilter(mat, filteredMat, 9, 50, 50);
        return filteredMat;
    }

    private static Bitmap adjustBrightnessAndContrast(Bitmap bitmap) {
        Bitmap resultBitmap = null;
        Mat brightenedMat = null;

        try {
            Mat mat = bitmapToMat(bitmap);

            brightenedMat = new Mat(mat.size(), mat.type());
            mat.convertTo(brightenedMat, -1, 1.1, 15); // 대비를 1.1배, 밝기를 15 증가
            resultBitmap = matToBitmap(brightenedMat);
        } finally {
            if (brightenedMat != null) brightenedMat.release();
        }


        return resultBitmap;
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

        VideoFrame.I420Buffer i420Buffer = JavaI420Buffer.wrap(width, height,
                yBuffer, width,
                uBuffer, width / 2,
                vBuffer, width / 2, null);

        // Create VideoFrame from I420Buffer
        VideoFrame videoFrame = new VideoFrame(i420Buffer, 0, System.nanoTime());

        return videoFrame;
    }


    Bitmap convertVideoFrameToBitmap(VideoFrame videoFrame) {
        VideoFrame.I420Buffer i420Buffer = videoFrame.getBuffer().toI420();
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

    private void logByteBuffer(String name, ByteBuffer buffer) {
        byte[] array = new byte[buffer.remaining()];
        buffer.get(array);
        buffer.rewind();
        System.out.println(name + ": " + Arrays.toString(array));
    }

    private Mat convertVideoFrameToMat(VideoFrame videoFrame) {
        VideoFrame.I420Buffer i420Buffer = videoFrame.getBuffer().toI420();
        int width = i420Buffer.getWidth();
        int height = i420Buffer.getHeight();

        ByteBuffer yBuffer = i420Buffer.getDataY();
        ByteBuffer uBuffer = i420Buffer.getDataU();
        ByteBuffer vBuffer = i420Buffer.getDataV();

        Mat yMat = new Mat(height, width, CvType.CV_8UC1, yBuffer);
        Mat uMat = new Mat(height / 2, width / 2, CvType.CV_8UC1, uBuffer);
        Mat vMat = new Mat(height / 2, width / 2, CvType.CV_8UC1, vBuffer);

        // Resize U and V channels to match Y channel size
        Mat uMatResized = new Mat();
        Mat vMatResized = new Mat();
        Imgproc.resize(uMat, uMatResized, new Size(width, height), 0, 0, Imgproc.INTER_LINEAR);
        Imgproc.resize(vMat, vMatResized, new Size(width, height), 0, 0, Imgproc.INTER_LINEAR);

        // Create a single Mat with 3 channels from Y, U, V channels
        Mat yuvMat = new Mat(height, width, CvType.CV_8UC3);
        List<Mat> channels = new ArrayList<>();
        channels.add(yMat);
        channels.add(uMatResized);
        channels.add(vMatResized);
        Core.merge(channels, yuvMat);

        // Convert YUV Mat to BGR Mat
        Mat bgrMat = new Mat();
        Imgproc.cvtColor(yuvMat, bgrMat, Imgproc.COLOR_YUV2BGR);

        i420Buffer.release();

        return bgrMat;
    }


    VideoFrame convertMatToVideoFrame(Mat mat) {
        int width = mat.cols();
        int height = mat.rows();
        int totalBytes = width * height;

        ByteBuffer yBuffer = ByteBuffer.allocateDirect(totalBytes);
        ByteBuffer uBuffer = ByteBuffer.allocateDirect(totalBytes / 4);
        ByteBuffer vBuffer = ByteBuffer.allocateDirect(totalBytes / 4);

        Mat yuvMat = new Mat();
        Imgproc.cvtColor(mat, yuvMat, Imgproc.COLOR_BGR2YUV_I420);

        Mat yMat = new Mat(height, width, CvType.CV_8UC1, yBuffer);
        Mat uMat = new Mat(height / 2, width / 2, CvType.CV_8UC1, uBuffer);
        Mat vMat = new Mat(height / 2, width / 2, CvType.CV_8UC1, vBuffer);

        List<Mat> yuvList = new ArrayList<>(3);
        yuvList.add(yMat);
        yuvList.add(uMat);
        yuvList.add(vMat);
        Core.split(yuvMat, yuvList);


        VideoFrame.I420Buffer i420Buffer = JavaI420Buffer.wrap(
                width, height,
                yBuffer, width,
                uBuffer, width / 2,
                vBuffer, width / 2,
                null
        );

        VideoFrame videoFrame = new VideoFrame(i420Buffer, 0, System.nanoTime());

        yuvMat.release();

        return videoFrame;
    }

}