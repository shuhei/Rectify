package com.shuheikagawa.rectify;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.util.Log;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfInt;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Created by shuhei on 8/30/14.
 */
public class RectFinder {
    private static final String DEBUG_TAG = "RectFinder";
    private static final int N = 11;
    private static final int CANNY_THRESHOLD = 50;
    private static final int AREA_THRESHOLD = 10000;

    public RectFinder() {
    }

    public Mat drawRectangles(Mat src) {
        List<MatOfPoint2f> rectangles = findRectangles(src);
        Log.d(DEBUG_TAG, rectangles.size() + " rectangles found.");

        if (rectangles.size() == 0) {
            Log.d(DEBUG_TAG, "No rectangles found.");
            return src;
        }

        Collections.sort(rectangles, AreaComparator);
        Log.d(DEBUG_TAG, "Sorted rectangles.");

        MatOfPoint2f largestRectangle = rectangles.get(0);

        return drawPerspectiveTransformation(src, largestRectangle);
    }

    private Mat drawPerspectiveTransformation(Mat src, MatOfPoint2f corners) {
        Mat result = Mat.zeros(src.size(), src.type());

        MatOfPoint2f sortedCorners = sortCorners(corners);
        MatOfPoint2f imageOutline = getOutline(result);

        Log.d(DEBUG_TAG, String.format("%d %d - %d %d", sortedCorners.cols(), sortedCorners.rows(), imageOutline.cols(), imageOutline.rows()));
        Log.d(DEBUG_TAG, String.format("%d - %d", sortedCorners.checkVector(2, CvType.CV_32F), imageOutline.checkVector(2, CvType.CV_32F)));

        Mat transformation = Imgproc.getPerspectiveTransform(sortedCorners, imageOutline);
        Imgproc.warpPerspective(src, result, transformation, result.size());

        return result;
    }

    private MatOfPoint2f getOutline(Mat image) {
        Point topLeft = new Point(0, 0);
        Point topRight = new Point(image.cols(), 0);
        Point bottomRight = new Point(image.cols(), image.rows());
        Point bottomLeft = new Point(0, image.rows());
        Point[] points = {topLeft, topRight, bottomRight, bottomLeft};

        MatOfPoint2f result = new MatOfPoint2f();
        result.fromArray(points);

        return result;
    }

    private MatOfPoint2f sortCorners(MatOfPoint2f corners) {
        Point center = getMathCenter(corners);
        List<Point> points = corners.toList();
        List<Point> topPoints = new ArrayList<Point>();
        List<Point> bottomPoints = new ArrayList<Point>();

        for (Point point : points) {
            if (point.y < center.y) {
                topPoints.add(point);
            } else {
                bottomPoints.add(point);
            }
        }

        Point topLeft = topPoints.get(0).x > topPoints.get(1).x ? topPoints.get(1) : topPoints.get(0);
        Point topRight = topPoints.get(0).x > topPoints.get(1).x ? topPoints.get(0) : topPoints.get(1);
        Point bottomLeft = bottomPoints.get(0).x > bottomPoints.get(1).x ? bottomPoints.get(1) : bottomPoints.get(0);
        Point bottomRight = bottomPoints.get(0).x > bottomPoints.get(1).x ? bottomPoints.get(0) : bottomPoints.get(1);

        MatOfPoint2f result = new MatOfPoint2f();
        Point[] sortedPoints = {topLeft, topRight, bottomRight, bottomLeft};
        result.fromArray(sortedPoints);

        return result;
    }

    private Point getMathCenter(MatOfPoint2f points) {
        double xSum = 0;
        double ySum = 0;
        List<Point> pointList = points.toList();
        int len = pointList.size();
        for (Point point : pointList) {
            xSum += point.x;
            ySum += point.y;
        }
        return new Point(xSum / len, ySum / len);
    }

    // Compare contours by their areas in descending order.
    private static Comparator<MatOfPoint2f> AreaComparator = new Comparator<MatOfPoint2f>() {
        public int compare(MatOfPoint2f m1, MatOfPoint2f m2) {
            double area1 = Imgproc.contourArea(m1);
            double area2 = Imgproc.contourArea(m2);
            return (int) Math.ceil(area2 - area1);
        }
    };

    private Mat draw(Mat src, List<MatOfPoint> rectangles) {
        Mat result = new Mat(src.size(), src.type());
        src.copyTo(result);

        Scalar color = new Scalar(255);
        int contourIndex = -1; // To draw all contours.
        Imgproc.drawContours(result, rectangles, contourIndex, color, 3);

        return result;
    }

    public List<MatOfPoint2f> findRectangles(Mat src) {
        // Blur the image to filter out the noise.
        Mat blurred = new Mat();
        Imgproc.medianBlur(src, blurred, 9);

        Mat gray0 = new Mat(blurred.size(), CvType.CV_8U);
        Mat gray = new Mat();

        List<MatOfPoint> contours = new ArrayList<MatOfPoint>();
        List<MatOfPoint2f> rectangles = new ArrayList<MatOfPoint2f>();

        // Find squares in every color plane of the image.
        for (int c = 0; c < 3; c++) {
            List<Mat> sources = new ArrayList<Mat>();
            sources.add(blurred);

            List<Mat> destinations = new ArrayList<Mat>();
            destinations.add(gray0);

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
                    int thresh = (l + 1) * 255 / N;
                    Imgproc.threshold(gray0, gray, thresh, 255, Imgproc.THRESH_BINARY);
                }

                // Find contours and store them all as a list.
                Imgproc.findContours(gray, contours, new Mat(), Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE);

                for (MatOfPoint contour : contours) {
                    MatOfPoint2f contourFloat = toMatOfPointFloat(contour);
                    double arcLen = Imgproc.arcLength(contourFloat, true) * 0.02;

                    MatOfPoint2f approx = new MatOfPoint2f();
                    Imgproc.approxPolyDP(contourFloat, approx, arcLen, true);

                    if (isRect(approx)) {
                        rectangles.add(approx);
                    }
                }
            }
        }

        return rectangles;
    }

    private MatOfPoint toMatOfPointInt(MatOfPoint2f mat) {
        MatOfPoint matInt = new MatOfPoint();
        mat.convertTo(matInt, CvType.CV_32S);
        return matInt;
    }

    private MatOfPoint2f toMatOfPointFloat(MatOfPoint mat) {
        MatOfPoint2f matFloat = new MatOfPoint2f();
        mat.convertTo(matFloat, CvType.CV_32FC2);
        return matFloat;
    }

    private boolean isRect(MatOfPoint2f polygon) {
        MatOfPoint polygonInt = toMatOfPointInt(polygon);

        // Check if the contour is a rectangle, has certain number of area and is convex.
        if (polygon.rows() == 4 && Math.abs(Imgproc.contourArea(polygon)) > AREA_THRESHOLD && Imgproc.isContourConvex(polygonInt)) {
            // Check if the all angles are more than 72.54 degrees (cos 0.3).
            double maxCosine = 0;
            Point[] approxPoints = polygon.toArray();

            for (int i = 2; i < 5; i++) {
                double cosine = Math.abs(angle(approxPoints[i % 4], approxPoints[i - 2], approxPoints[i - 1]));
                maxCosine = Math.max(cosine, maxCosine);
            }

            if (maxCosine < 0.3) {
                return true;
            }
        }

        return false;
    }

    private double angle(Point p1, Point p2, Point p0) {
        double dx1 = p1.x - p0.x;
        double dy1 = p1.y - p0.y;
        double dx2 = p2.x - p0.x;
        double dy2 = p2.y - p0.y;
        return (dx1 * dx2 + dy1 * dy2) / Math.sqrt((dx1 * dx1 + dy1 * dy1) * (dx2 * dx2 + dy2 * dy2) + 1e-10);
    }
}
