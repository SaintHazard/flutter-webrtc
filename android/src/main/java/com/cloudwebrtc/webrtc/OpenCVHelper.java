package com.cloudwebrtc.webrtc;

import android.graphics.PointF;
import android.graphics.Rect;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Handler;
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

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceContour;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;
import com.google.mlkit.vision.face.FaceLandmark;


public class OpenCVHelper {



    public OpenCVHelper(Context context) {

    }

    Bitmap removeBlemishes(Bitmap bitmap, BitmapCallback callback) {
        // FaceDetectorOptions 설정
        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
                .build();

        // FaceDetector 생성
        FaceDetector detector = FaceDetection.getClient(options);

        // InputImage 생성
        InputImage image = InputImage.fromBitmap(bitmap, 0);

        // 비동기 작업으로 얼굴 감지
        detector.process(image)
                .addOnSuccessListener(faces -> {
                    if(faces.isEmpty()) {
                        callback.onBitmapProcessed(bitmap);
                        return;
                    }

                    Bitmap resultBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true);
                    Canvas canvas = new Canvas(resultBitmap);
                    Paint paint = new Paint();

                    for (Face face : faces) {
                        // 얼굴 영역 가져오기
                        Rect bounds = face.getBoundingBox();

                        // 가우시안 블러 적용
                        Bitmap faceBitmap = Bitmap.createBitmap(resultBitmap, bounds.left, bounds.top, bounds.width(), bounds.height());
                        Bitmap blurredFaceBitmap = removeBlemishes(faceBitmap);
                        canvas.drawBitmap(blurredFaceBitmap, bounds.left, bounds.top, paint);
                    }

                    callback.onBitmapProcessed(resultBitmap);

                    // 결과 Bitmap 반환
                    // 여기서 필요한 경우 메인 스레드에 결과를 전달해야 함
                    // 예: imageView.setImageBitmap(resultBitmap);
                })
                .addOnFailureListener(e -> {
                    // 에러 처리
                    callback.onError(e);
                });

        return bitmap; // 비동기 작업이므로 결과를 즉시 반환할 수 없고, 콜백을 통해 처리해야 함
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