package com.shuheikagawa.rectify;

import android.graphics.Bitmap;

// Use singleton to share data between activities.
// http://stackoverflow.com/questions/4878159/android-whats-the-best-way-to-share-data-between-activities
public class PhotoHolder {
    private static final PhotoHolder theInstance = new PhotoHolder();

    private Bitmap bitmap;

    public static PhotoHolder getInstance() {
        return theInstance;
    }

    private PhotoHolder() {
    }

    public Bitmap get() {
        return bitmap;
    }

    public boolean contains() {
        return bitmap != null;
    }

    public void set(Bitmap bitmap) {
        this.bitmap = bitmap;
    }

    public void clean() {
        bitmap = null;
    }
}
