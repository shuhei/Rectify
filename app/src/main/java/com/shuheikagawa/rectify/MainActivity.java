package com.shuheikagawa.rectify;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
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

import it.sephiroth.android.library.imagezoom.ImageViewTouch;
import it.sephiroth.android.library.imagezoom.ImageViewTouchBase;
import it.sephiroth.android.library.imagezoom.graphics.FastBitmapDrawable;


public class MainActivity extends Activity {
    private final static String DEBUG_TAG = "MainActivity";
    private boolean openCVLoaded = false;

    private ImageViewTouch sourceImageView;
    private ImageViewTouch destinationImageView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(DEBUG_TAG, "onCreate");

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Set up touch-enabled image views. They cannot be fully configured with XML.
        sourceImageView = (ImageViewTouch) findViewById(R.id.source_image_view);
        sourceImageView.setDisplayType(ImageViewTouchBase.DisplayType.FIT_IF_BIGGER);
        // Do not use ImageViewTouch#setImageResource. Otherwise its getDrawable returns
        // BitmapDrawable that is incompatible with FastBitmapDrawable.
        Bitmap sampleBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.unbeaten_tracks);
        sourceImageView.setImageBitmap(sampleBitmap);

        destinationImageView = (ImageViewTouch) findViewById(R.id.destination_image_view);
        destinationImageView.setDisplayType(ImageViewTouchBase.DisplayType.FIT_IF_BIGGER);
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

        Log.d(DEBUG_TAG, String.format("ResizTouching image to %d %d.", resizedWidth, resizedHeight));

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
        // Notice that ImageViewTouch uses FastBitmapDrawable that does not inherit BitmapDrawable.
        FastBitmapDrawable drawable = (FastBitmapDrawable) sourceImageView.getDrawable();
        Bitmap bitmap = drawable.getBitmap();

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

    public void onMaskButtonClick(View view) {
        Log.d(DEBUG_TAG, "Masking image.");

        FastBitmapDrawable drawable = (FastBitmapDrawable) destinationImageView.getDrawable();
        Bitmap bitmap = drawable.getBitmap();

        maskBitmap(bitmap, 0.03f, 0.02f, 0.45f, 0.32f);

        destinationImageView.setImageBitmap(bitmap);
    }

    private void maskBitmap(Bitmap bitmap, float xRatio, float yRatio, float widthRatio, float heightRatio) {
        Canvas canvas = new Canvas(bitmap);

        Paint blackFill = new Paint();
        blackFill.setColor(Color.BLACK);
        blackFill.setStyle(Paint.Style.FILL);

        float left = bitmap.getWidth() * xRatio;
        float top = bitmap.getHeight() * yRatio;
        float right = bitmap.getWidth() * (xRatio + widthRatio);
        float bottom = bitmap.getHeight() * (yRatio + heightRatio);

        canvas.drawRect(left, top, right, bottom, blackFill);
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
