package com.example.mycamera;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

public class PermissionCheck {
    public static final int REQUEST_CODE = 5;
    // 定义要申请的权限
    private static final String[] permissions = new String[]{
            Manifest.permission.CAMERA, Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.RECORD_AUDIO
    };

    private static List<String> permissionList = new ArrayList<String>();


    // 每个权限是否已授
    public static boolean isPermissionGranted(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

            //权限是否已经授权 GRANTED-授权  DINIED-拒绝
            for (String permission : permissions) {
                // 检查权限是否全部授予
                if (ContextCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED) {
                    // 如果没有就添加到权限集合
                    permissionList.add(permission);
                }
            }
            // 是空返回ture，否则返回false
            return permissionList.isEmpty();
        } else {
            return true;
        }
    }

    public static void checkPermission(Activity activity) {
        if (!isPermissionGranted(activity)) {
            //如果没有设置过权限许可，则弹出系统的授权窗口
            ActivityCompat.requestPermissions(activity, permissionList.toArray(new String[permissionList.size()]), REQUEST_CODE);
        }
        else {
            Toast.makeText(activity.getApplicationContext(), "已取得全部授权...", Toast.LENGTH_SHORT).show();
        }
    }
}
