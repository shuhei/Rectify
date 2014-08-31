package com.shuheikagawa.rectify;

import org.opencv.photo.Photo;

// Use singleton to share data between activities.
// http://stackoverflow.com/questions/4878159/android-whats-the-best-way-to-share-data-between-activities
public class PhotoHolder {
    private static final PhotoHolder theInstance = new PhotoHolder();

    private byte[] bytes;

    public static PhotoHolder getInstance() {
        return theInstance;
    }

    private PhotoHolder() {
    }

    public byte[] getBytes() {
        return bytes;
    }

    public boolean hasBytes() {
        return bytes != null;
    }

    public void setBytes(byte[] bytes) {
        this.bytes = bytes;
    }

    public void clean() {
        this.bytes = null;
    }
}
