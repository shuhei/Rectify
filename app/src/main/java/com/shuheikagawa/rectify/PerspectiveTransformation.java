package com.shuheikagawa.rectify;

import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.List;

public class PerspectiveTransformation {
    private static final String DEBUG_TAG = "PerspectiveTransformation";

    public PerspectiveTransformation() {
    }

    public Mat transform(Mat src, MatOfPoint2f corners) {
        Mat result = Mat.zeros(src.size(), src.type());

        MatOfPoint2f sortedCorners = sortCorners(corners);
        MatOfPoint2f imageOutline = getOutline(result);

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
        Point center = getMassCenter(corners);
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

    private Point getMassCenter(MatOfPoint2f points) {
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

}
