package com.shuheikagawa.rectify;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.Camera;
import android.hardware.SensorManager;
import android.media.Image;
import android.os.Bundle;
import android.util.Log;
import android.view.Display;
import android.view.Menu;
import android.view.MenuItem;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.Toast;


public class CameraActivity extends Activity {
    public static final String EXTRA_PHOTO = "com.shuheikagawa.rectify.PHOTO";

    private static final String DEBUG_TAG = "CameraActivity";
    private Camera camera;
    private CameraPreview preview;

    private Thread.UncaughtExceptionHandler originalExceptionHandler;
    private boolean hasExceptionHandler = false;

    private FrameLayout previewLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        // Set uncaught exception handler to make sure to release camera.
        if (!hasExceptionHandler) {
            hasExceptionHandler = true;
            originalExceptionHandler = Thread.getDefaultUncaughtExceptionHandler();
            Thread.setDefaultUncaughtExceptionHandler(exceptionHandler);
        }

        previewLayout = (FrameLayout) findViewById(R.id.camera_preview);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.camera, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();

        openCamera();

        if (camera == null) {
            Toast.makeText(this, "Failed to open camera.", Toast.LENGTH_LONG).show();
            return;
        }

        preview = new CameraPreview(this, camera);
        previewLayout.addView(preview);
    }

    @Override
    protected void onPause() {
        releaseCamera();

        super.onPause();
    }

    public void onCaptureButtonClick(View view) {
        Log.d(DEBUG_TAG, "Photo button is pressed.");

        if (camera == null) {
            Log.d(DEBUG_TAG, "Camera is not available.");
            Toast.makeText(this, "Camera is not available.", Toast.LENGTH_LONG).show();
            return;
        }

        Log.d(DEBUG_TAG, "Taking picture.");

        // Perform auto focus and then take a picture if available.
        if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_AUTOFOCUS) &&
                preview.isPreviewing()) {
            camera.cancelAutoFocus();
            camera.autoFocus(autoFocusCallback);
        } else {
            camera.takePicture(null, null, pictureCallback);
        }
    }

    public void onCancelButtonClick(View view) {
        Intent upIntent = getParentActivityIntent();
        navigateUpTo(upIntent);
    }

    private Camera.AutoFocusCallback autoFocusCallback = new Camera.AutoFocusCallback() {
        @Override
        public void onAutoFocus(boolean b, Camera camera) {
            camera.takePicture(null, null, pictureCallback);
        }
    };

    private Camera.PictureCallback pictureCallback = new Camera.PictureCallback() {
        @Override
        public void onPictureTaken(byte[] bytes, Camera camera) {
            Log.d(DEBUG_TAG, "On picture taken.");
            if (bytes == null) {
                Log.e(DEBUG_TAG, "Failed to take a photo");
                return;
            }
            Log.d(DEBUG_TAG, "Showing the taken photo.");

            Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);

            // Go back to the parent with the photo data.
            PhotoHolder.getInstance().set(bitmap);

            Intent upIntent = getParentActivityIntent();
            upIntent.putExtra(EXTRA_PHOTO, true);
            navigateUpTo(upIntent);
        }
    };

    private void openCamera() {
        Log.d(DEBUG_TAG, "Opening up camera.");

        if (!isCameraAvailable()) {
            Toast.makeText(this, "Camera is not available on this device.", Toast.LENGTH_LONG).show();
            return;
        }

        int numCameras = Camera.getNumberOfCameras();
        Log.d(DEBUG_TAG, String.format("%d cameras detected.", numCameras));
        for (int i = 0; i < numCameras; i++) {
            Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
            Camera.getCameraInfo(i, cameraInfo);
            Log.d(DEBUG_TAG, String.format("facing: %b, orientation: %d", cameraInfo.facing, cameraInfo.orientation));
        }

        try {
            camera = Camera.open(0);
        } catch (Exception e) {
            Log.e(DEBUG_TAG, "Got error opening camera.");
            e.printStackTrace();
            Toast.makeText(this, "Got error opening camera.", Toast.LENGTH_LONG).show();
            return;
        }

        if (camera == null) {
            Log.e(DEBUG_TAG, "Returned camera is null.");
            Toast.makeText(this, "Failed to open camera.", Toast.LENGTH_LONG).show();
            return;
        }

        Log.d(DEBUG_TAG, "Successfully opened a camera.");
    }

    private void releaseCamera() {
        if (camera != null ) {
            camera.release();
            camera = null;
        }
    }

    private boolean isCameraAvailable() {
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
            return false;
        }

        return true;
    }

    private Thread.UncaughtExceptionHandler exceptionHandler = new Thread.UncaughtExceptionHandler() {
        private volatile boolean crashing = false;

        @Override
        public void uncaughtException(Thread thread, Throwable throwable) {
            try {
                if (!crashing) {
                    crashing = true;
                    releaseCamera();
                }
            } finally {
                if (originalExceptionHandler != null) {
                    originalExceptionHandler.uncaughtException(thread, throwable);
                }
            }
        }
    };
}
