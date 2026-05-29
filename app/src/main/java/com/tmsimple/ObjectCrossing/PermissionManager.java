package com.tmsimple.ObjectCrossing;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PermissionManager {

    private final Activity activity;
    private final LogManager logManager;

    public static final int REQUEST_CODE_ALL_PERMISSIONS = 999;

    private final List<String> ALL_PERMISSIONS = Arrays.asList(
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
    );

    public PermissionManager(Activity activity, LogManager logManager) {
        this.activity = activity;
        this.logManager = logManager;
    }

    /** Request one permission */
    public void checkPermission(String permission, int requestCode) {
        if (ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_DENIED) {
            ActivityCompat.requestPermissions(activity, new String[]{permission}, requestCode);
            logManager.log("Requesting permission: " + permission);
        } else {
            logManager.log("Permission already granted: " + permission);
        }
    }

    /** Request all permissions in one go */
    public void requestAllPermissions() {
        List<String> permissionsToRequest = new ArrayList<>();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_DENIED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT);
            }
            if (ContextCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_DENIED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_SCAN);
            }
        } else {
            if (ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_DENIED) {
                permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION);
            }
            if (ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_DENIED) {
                permissionsToRequest.add(Manifest.permission.ACCESS_COARSE_LOCATION);
            }
        }

        if (!permissionsToRequest.isEmpty()) {
            ActivityCompat.requestPermissions(activity, permissionsToRequest.toArray(new String[0]), REQUEST_CODE_ALL_PERMISSIONS);
            logManager.log("Requesting permissions: " + permissionsToRequest);
        } else {
            logManager.log("All permissions already granted ✅");
        }
    }

    /** Handle permission results */
    public void handlePermissionResult(int requestCode, int[] grantResults) {
        if (requestCode == REQUEST_CODE_ALL_PERMISSIONS) {
            if (grantResults.length > 0 && allGranted(grantResults)) {
                logManager.log("All permissions granted ✅");
            } else {
                logManager.log("Some permissions denied ❌");
            }
        } else {
            logManager.log("Permission result for requestCode " + requestCode);
        }
    }

    private boolean allGranted(int[] results) {
        for (int result : results) {
            if (result != PackageManager.PERMISSION_GRANTED) return false;
        }
        return true;
    }
}
