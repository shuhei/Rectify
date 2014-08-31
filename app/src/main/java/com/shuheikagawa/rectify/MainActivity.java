package com.shuheikagawa.rectify;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Scalar;


public class MainActivity extends Activity {
    private final static String DEBUG_TAG = "MainActivity";
    private boolean openCVLoaded = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(DEBUG_TAG, "onCreate");

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Initialize OpenCV.
        OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_9, this, openCVLoaderCallback);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        Log.d(DEBUG_TAG, "New intent has come.");

        super.onNewIntent(intent);

        if (intent.getBooleanExtra(CameraActivity.EXTRA_PHOTO, false)) {
            Log.d(DEBUG_TAG, "Received a photo from camera.");

            byte[] bytes = PhotoHolder.getInstance().getBytes();
            Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            PhotoHolder.getInstance().clean();

            bitmap = resizeImageToShow(bitmap);

            Log.d(DEBUG_TAG, "Showing the photo from camera.");
            ImageView sourceImageView = (ImageView) findViewById(R.id.source_image_view);
            sourceImageView.setImageBitmap(bitmap);
        }
    }

    private Bitmap resizeImageToShow(Bitmap bitmap) {
        final float LIMIT = 2048f;

        if (bitmap.getWidth() <= LIMIT && bitmap.getHeight() <= LIMIT) {
            return bitmap;
        }

        double widthRatio = bitmap.getWidth() / LIMIT;
        double heightRatio = bitmap.getHeight() / LIMIT;

        double ratio = Math.max(widthRatio, heightRatio);

        int resizedWidth = (int)(bitmap.getWidth() / ratio);
        int resizedHeight = (int)(bitmap.getHeight() / ratio);

        Log.d(DEBUG_TAG, String.format("Resizing image to %d %d.", resizedWidth, resizedHeight));

        return Bitmap.createScaledBitmap(bitmap, resizedWidth, resizedHeight, false);
    }

    public void onPhotoButtonClick(View view) {
        Intent intent = new Intent(this, CameraActivity.class);
        startActivity(intent);
    }

    public void onRectifyButtonClick(View view) {
        if (!openCVLoaded) {
            Toast.makeText(this, "OpenCV is not yet loaded.", Toast.LENGTH_LONG).show();
            return;
        }

        // Find image views.
        ImageView sourceImageView = (ImageView) findViewById(R.id.source_image_view);
        ImageView destinationImageView = (ImageView) findViewById(R.id.destination_image_view);

        // Get the bitmap from the image view.
        Drawable drawable = sourceImageView.getDrawable();
        Bitmap bitmap = ((BitmapDrawable) drawable).getBitmap();

        // Create an OpenCV mat from the bitmap.
        Mat srcMat = bitmapToMat(bitmap);

        // Find the largest rectangle.
        RectFinder rectFinder = new RectFinder(0.2, 0.98);
        MatOfPoint2f rectangle = rectFinder.findRectangle(srcMat);

        if (rectangle == null) {
            Toast.makeText(this, "No rectangles were found.", Toast.LENGTH_LONG).show();
            return;
        }

        // Transform the rectangle.
        PerspectiveTransformation perspective = new PerspectiveTransformation();
        Mat dstMat = perspective.transform(srcMat, rectangle);

        // Create a bitmap from the result mat.
        Bitmap resultBitmap = matToBitmap(dstMat);
        Utils.matToBitmap(dstMat, resultBitmap);

        // Show the result bitmap on the destination image view.
        destinationImageView.setImageBitmap(resultBitmap);
    }

    private Mat bitmapToMat(Bitmap bitmap) {
        Mat mat = new Mat(bitmap.getHeight(), bitmap.getWidth(), CvType.CV_8U, new Scalar(4));
        Bitmap bitmap32 = bitmap.copy(Bitmap.Config.ARGB_8888, true);
        Utils.bitmapToMat(bitmap32, mat);
        return mat;
    }

    private Bitmap matToBitmap(Mat mat) {
        Bitmap bitmap = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(mat, bitmap);
        return bitmap;
    }

    private BaseLoaderCallback openCVLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            if (status != LoaderCallbackInterface.SUCCESS) {
                Log.e(DEBUG_TAG, "Failed to load OpenCV.");
                super.onManagerConnected(status);
                return;
            }

            openCVLoaded = true;
        }
    };

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        Log.d(DEBUG_TAG, "onCreateOptionsMenu");

        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Log.d(DEBUG_TAG, "onOptionsItemSelected");

        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
