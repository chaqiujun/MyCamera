package com.example.mycamera;

import android.os.Bundle;
import android.os.Environment;
import android.util.Log;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager.widget.ViewPager;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ShowActivity extends AppCompatActivity {

    private ViewPager viewPager;
    private ImageVideoPagerAdapter myAdapter;
    private List<ImageVideoInfo> imageVideoInfos;
    private Map<String, String> fileMap;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.show);
        getAllImageVidoPath();
        setupView();
        ActionBar actionBar=getSupportActionBar();
        actionBar.hide();
    }

    private void getAllImageVidoPath() {
        // 文件夹路径
        String path = Environment.getExternalStorageDirectory() + "/DCIM/MyCameraPhotos/";
        File fileDir = new File(path);
        // 获取所有文件对象
        File[] tempList = fileDir.listFiles();
        // 创建一个item数组保存所有图片视频文件的信息
        imageVideoInfos = new ArrayList<ImageVideoInfo>();

        for (int i = 0; i < tempList.length; i++) {
            // 文件名
            String fileName = tempList[i].getName();
            // 文件路径
            String filePath = tempList[i].getAbsolutePath();
            // 文件类型
            String type = fileName.split("\\.")[1];
            Log.i("ShowActivity", "" + i + "..." + filePath);
            imageVideoInfos.add(new ImageVideoInfo(fileName, filePath, type));
        }
        if (imageVideoInfos.size() > 0) {
            Log.i("ShowActivity", "有图片..." + imageVideoInfos.size());
        }
    }

    private void setupView() {
        viewPager = (ViewPager) findViewById(R.id.show_view_pager);
        myAdapter = new ImageVideoPagerAdapter(this, imageVideoInfos);
        viewPager.setAdapter(myAdapter);
    }

}