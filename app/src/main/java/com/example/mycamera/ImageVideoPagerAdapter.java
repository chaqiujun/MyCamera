package com.example.mycamera;

import android.content.Context;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

import java.util.HashMap;
import java.util.List;

public class ImageVideoPagerAdapter extends PagerAdapter {

    private Context context;
    private List<ImageVideoInfo> imageVideoInfos;
    // 保存相片的id以及对应的ImageVideoItem
    private HashMap<Integer, ImageVideoItem> hashMap;

    public ImageVideoPagerAdapter(Context context, List<ImageVideoInfo> imageVideoInfos) {
        this.context = context;
        this.imageVideoInfos = imageVideoInfos;
        hashMap = new HashMap<Integer, ImageVideoItem>();
    }

    @Override
    public int getCount() {
        return imageVideoInfos.size();
    }

    @Override
    public boolean isViewFromObject(@NonNull View view, @NonNull Object object) {
        return view == object;
    }

    // 初始化一个ShowImageVideo对象，如果已经存在就重新载入，没有的话new一个
    @NonNull
    @Override
    public Object instantiateItem(@NonNull ViewGroup container, int position) {
        ImageVideoItem itemView;
        if (hashMap.containsKey(position)) {
            Log.i("ShowActivity","重新载入..."+position);
            itemView = hashMap.get(position);
            itemView.reload();
        } else {
            Log.i("ShowActivity","新建对象..."+position);
            itemView = new ImageVideoItem(context);
            ImageVideoInfo imageVideoInfo = imageVideoInfos.get(position);
            itemView.setData(imageVideoInfo);
            hashMap.put(position, itemView);
            ((ViewPager) container).addView(itemView);
        }
        return itemView;
    }

    @Override// 当我们左右滑动图片的时候会将图片回收掉
    public void destroyItem(@NonNull View container, int position, @NonNull Object object) {
        ImageVideoItem item = (ImageVideoItem) object;
        item.recycle();
    }

}
