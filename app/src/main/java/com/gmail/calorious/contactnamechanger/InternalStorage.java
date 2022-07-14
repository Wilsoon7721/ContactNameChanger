package com.gmail.calorious.contactnamechanger;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Environment;
import android.os.storage.StorageManager;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

public class InternalStorage {
    // Amount of megabytes needed for this application
    private static final long MB_ALLOCATION = 10L;
    public static boolean initialized = false;
    // Internal calculation value
    private static final long BYTES_ALLOCATION;
    static {
        BYTES_ALLOCATION = MB_ALLOCATION * 1000000;
    }

    // Calls StorageManager to verify that enough space is able to be allocated for this app
    // filesDir - retrieve from getFilesDir()
    // applicationContext - retrieve from getApplicationContext()
    public static void initialize(Context applicationContext, File filesDir) {
        StorageManager storageManager = applicationContext.getSystemService(StorageManager.class);
        UUID appSpecificInternalDirectoryUUID;
        long availableBytes;
        try {
            appSpecificInternalDirectoryUUID = storageManager.getUuidForPath(filesDir);
            availableBytes = storageManager.getAllocatableBytes(appSpecificInternalDirectoryUUID);
        } catch(IOException ex) {
            Log.e("InternalStorage", "Initialization could not be completed - An IOException has occurred while obtaining UUID for file directory path.");
            ex.printStackTrace();
            return;
        }
        if(availableBytes >= BYTES_ALLOCATION) {
            if(availableBytes == BYTES_ALLOCATION) {
                Log.d("InternalStorage", "ALLOCATING ALL REMAINING SPACE ON EXTERNAL STORAGE TO APPLICATION...");
                try {
                    storageManager.allocateBytes(appSpecificInternalDirectoryUUID, BYTES_ALLOCATION);
                } catch(IOException ex) {
                    Log.e("InternalStorage", "Initialization could not be completed - An IOException has occurred while allocating space to application.");
                    ex.printStackTrace();
                    return;
                }
                Toast.makeText(applicationContext, "You are running low on device space in external storage location.", Toast.LENGTH_SHORT).show();
                return;
            }
            try {
                storageManager.allocateBytes(appSpecificInternalDirectoryUUID, BYTES_ALLOCATION);
            } catch(IOException ex) {
                Log.e("InternalStorage", "Initialization could not be completed - An IOException has occurred while allocating space to application.");
                ex.printStackTrace();
            }
        } else if(availableBytes <= 50000000) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                callAllCacheClear(applicationContext);
                return;
            }
            Intent storageIntent = new Intent();
            storageIntent.setAction(StorageManager.ACTION_MANAGE_STORAGE);
            applicationContext.startActivity(storageIntent);
        } else {
            Intent storageIntent = new Intent();
            storageIntent.setAction(StorageManager.ACTION_MANAGE_STORAGE);
            applicationContext.startActivity(storageIntent);
        }
        initialized = true;
    }
    /*
     * Returns:
     *  -1 - Other state
     *  0 - External Storage not mounted
     *  1 - External Storage mounted and writeable
     *  2 - External storage mounted but read-only
     */
    public static int queryExternalStorageState() {
        switch (Environment.getExternalStorageState()) {
            case Environment.MEDIA_MOUNTED:
                return 1;
            case Environment.MEDIA_MOUNTED_READ_ONLY:
                return 2;
            case Environment.MEDIA_UNMOUNTED:
                return 0;
            default:
                return -1;
        }
    }

    public static File getPrimaryExternalStorage(Context applicationContext) {
        int state = queryExternalStorageState();
        if(state == 1 || state == 2) {
            File[] allExternalStorageVolumes = ContextCompat.getExternalFilesDirs(applicationContext, null);
            return allExternalStorageVolumes[0];
        }
        return null;
    }

    @RequiresApi(api = Build.VERSION_CODES.R)
    public static void callAllCacheClear(Context context) {
        Toast.makeText(context, "The application suggests that you clear all your app cache to free space.", Toast.LENGTH_SHORT).show();
        Intent intent = new Intent();
        intent.setAction(StorageManager.ACTION_CLEAR_APP_CACHE);
        context.startActivity(intent);
    }
}
