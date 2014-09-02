package com.shuheikagawa.rectify;

import android.util.Log;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfInt;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class RectFinder {
    private static final String DEBUG_TAG = "RectFinder";
    private static final int N = 5; // 11 in the original sample.
    private static final int CANNY_THRESHOLD = 50;
    private static final double DOWNSCALE_IMAGE_SIZE = 600f;

    private double areaLowerThresholdRatio;
    private double areaUpperThresholdRatio;

    public RectFinder(double areaLowerThresholdRatio, double areaUpperThresholdRatio) {
        this.areaLowerThresholdRatio = areaLowerThresholdRatio;
        this.areaUpperThresholdRatio = areaUpperThresholdRatio;
    }

    public MatOfPoint2f findRectangle(Mat src) {
        // Downscale image for better performance.
        double ratio = DOWNSCALE_IMAGE_SIZE / Math.max(src.width(), src.height());
        Size downscaledSize = new Size(src.width() * ratio, src.height() * ratio);
        Log.d(DEBUG_TAG, "Before downscaling: " + src.size());
        Mat downscaled = new Mat(downscaledSize, src.type());
        Log.d(DEBUG_TAG, "After downscaling: " + downscaled.size());
        Imgproc.resize(src, downscaled, downscaledSize);

        // Find rectangles.
        List<MatOfPoint2f> rectangles = findRectangles(downscaled);
        Log.d(DEBUG_TAG, rectangles.size() + " rectangles found.");

        if (rectangles.size() == 0) {
            Log.d(DEBUG_TAG, "No rectangles found.");
            return null;
        }

        // Pick up the largest rectangle.
        Collections.sort(rectangles, AreaDescendingComparator);
        Log.d(DEBUG_TAG, "Sorted rectangles.");

        MatOfPoint2f largestRectangle = rectangles.get(0);
        Log.d(DEBUG_TAG, "Before scaling up: " + GeomUtils.pointsToString(largestRectangle));

        // Take back the scale.
        MatOfPoint2f result = GeomUtils.scaleRectangle(largestRectangle, 1f / ratio);
        Log.d(DEBUG_TAG, "After scaling up: " + GeomUtils.pointsToString(result));

        return result;
    }

    // Compare contours by their areas in descending order.
    private static Comparator<MatOfPoint2f> AreaDescendingComparator = new Comparator<MatOfPoint2f>() {
        public int compare(MatOfPoint2f m1, MatOfPoint2f m2) {
            double area1 = Imgproc.contourArea(m1);
            double area2 = Imgproc.contourArea(m2);
            return (int) Math.ceil(area2 - area1);
        }
    };

    public List<MatOfPoint2f> findRectangles(Mat src) {
        // Blur the image to filter out the noise.
        Mat blurred = new Mat();
        Imgproc.medianBlur(src, blurred, 9);

        // Set up images to use.
        Mat gray0 = new Mat(blurred.size(), CvType.CV_8U);
        Mat gray = new Mat();

        // For Core.mixChannels.
        List<MatOfPoint> contours = new ArrayList<MatOfPoint>();
        List<MatOfPoint2f> rectangles = new ArrayList<MatOfPoint2f>();

        List<Mat> sources = new ArrayList<Mat>();
        sources.add(blurred);
        List<Mat> destinations = new ArrayList<Mat>();
        destinations.add(gray0);

        // To filter rectangles by their areas.
        int srcArea = src.rows() * src.cols();

        // Find squares in every color plane of the image.
        for (int c = 0; c < 3; c++) {
            int[] ch = {c, 0};
            MatOfInt fromTo = new MatOfInt(ch);

            Core.mixChannels(sources, destinations, fromTo);

            // Try several threshold levels.
            for (int l = 0; l < N; l++) {
                if (l == 0) {
                    // HACK: Use Canny instead of zero threshold level.
                    // Canny helps to catch squares with gradient shading.
                    // NOTE: No kernel size parameters on Java API.
                    Imgproc.Canny(gray0, gray, 0, CANNY_THRESHOLD);

                    // Dilate Canny output to remove potential holes between edge segments.
                    Imgproc.dilate(gray, gray, Mat.ones(new Size(3, 3), 0));
                } else {
                    int threshold = (l + 1) * 255 / N;
                    Imgproc.threshold(gray0, gray, threshold, 255, Imgproc.THRESH_BINARY);
                }

                // Find contours and store them all as a list.
                Imgproc.findContours(gray, contours, new Mat(), Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE);

                for (MatOfPoint contour : contours) {
                    MatOfPoint2f contourFloat = GeomUtils.toMatOfPointFloat(contour);
                    double arcLen = Imgproc.arcLength(contourFloat, true) * 0.02;

                    // Approximate polygonal curves.
                    MatOfPoint2f approx = new MatOfPoint2f();
                    Imgproc.approxPolyDP(contourFloat, approx, arcLen, true);

                    if (isRectangle(approx, srcArea)) {
                        rectangles.add(approx);
                    }
                }
            }
        }

        return rectangles;
    }

    private boolean isRectangle(MatOfPoint2f polygon, int srcArea) {
        MatOfPoint polygonInt = GeomUtils.toMatOfPointInt(polygon);

        if (polygon.rows() != 4) {
            return false;
        }

        double area = Math.abs(Imgproc.contourArea(polygon));
        if (area < srcArea * areaLowerThresholdRatio || area > srcArea * areaUpperThresholdRatio) {
            return false;
        }

        if (!Imgproc.isContourConvex(polygonInt)) {
            return false;
        }

        // Check if the all angles are more than 72.54 degrees (cos 0.3).
        double maxCosine = 0;
        Point[] approxPoints = polygon.toArray();

        for (int i = 2; i < 5; i++) {
            double cosine = Math.abs(GeomUtils.angle(approxPoints[i % 4], approxPoints[i - 2], approxPoints[i - 1]));
            maxCosine = Math.max(cosine, maxCosine);
        }

        if (maxCosine >= 0.3) {
            return false;
        }

        return true;
    }
}
