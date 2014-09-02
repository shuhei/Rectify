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
import android.widget.Button;
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

    private ImageView sourceImageView;
    private ImageView destinationImageView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(DEBUG_TAG, "onCreate");

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Find image views.
        sourceImageView = (ImageView) findViewById(R.id.source_image_view);
        destinationImageView = (ImageView) findViewById(R.id.destination_image_view);
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

            Bitmap bitmap = PhotoHolder.getInstance().get();
            PhotoHolder.getInstance().clean();

            bitmap = resizeImageToShow(bitmap);

            Log.d(DEBUG_TAG, "Showing the photo from camera.");
            sourceImageView.setImageBitmap(bitmap);

            // Clear destination image.
            destinationImageView.setImageResource(android.R.color.transparent);
        }
    }

    // ImageView cannot show too large image.
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

        Button rectifyButton = (Button) findViewById(R.id.rectify_button);
        rectifyButton.setEnabled(false);

        // Get the bitmap from the image view.
        Drawable drawable = sourceImageView.getDrawable();
        Bitmap bitmap = ((BitmapDrawable) drawable).getBitmap();

        // Create an OpenCV mat from the bitmap.
        Mat srcMat = ImageUtils.bitmapToMat(bitmap);

        // Find the largest rectangle.
        // Find image views.
        RectFinder rectFinder = new RectFinder(0.2, 0.98);
        MatOfPoint2f rectangle = rectFinder.findRectangle(srcMat);

        if (rectangle == null) {
            Toast.makeText(this, "No rectangles were found.", Toast.LENGTH_LONG).show();
            rectifyButton.setEnabled(true);
            return;
        }

        // Transform the rectangle.
        PerspectiveTransformation perspective = new PerspectiveTransformation();
        Mat dstMat = perspective.transform(srcMat, rectangle);

        // Create a bitmap from the result mat.
        Bitmap resultBitmap = ImageUtils.matToBitmap(dstMat);
        Log.d(DEBUG_TAG, String.format("Result bitmap: %d %d", resultBitmap.getWidth(), resultBitmap.getHeight()));

        // Show the result bitmap on the destination image view.
        destinationImageView.setImageBitmap(resultBitmap);

        rectifyButton.setEnabled(true);
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
