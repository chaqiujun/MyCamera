package com.example.mycamera;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.MediaController;
import android.widget.VideoView;


public class ImageVideoItem extends FrameLayout {

    // 显示图片的ImageView
    private ImageView imageView;
    // 图片对应的Bitmap
    private Bitmap bitmap;
    // 显示视频的VedioView
    private VideoView videoView;
    // 每一个图片视频项对象
    private ImageVideoInfo imageVedioItem;
    private Context context;
    private MediaController mediaController;


    public ImageVideoItem(Context context) {
        super(context);
        this.context = context;
        setupViews();
    }

    public ImageVideoItem(Context context, AttributeSet attrs) {
        super(context, attrs);
        setupViews();
    }

    public void setupViews() {
        View view = LayoutInflater.from(getContext()).inflate(R.layout.pager_item, null);
        imageView = (ImageView) view.findViewById(R.id.img);
        videoView = (VideoView) view.findViewById(R.id.vedio);
        addView(view);
    }

    // 用ImageVideoItem的信息填充数据
    public void setData(ImageVideoInfo item) {
        this.imageVedioItem = item;
        if (item.getType().equals("jpg")) {
            imageView.setVisibility(VISIBLE);
            videoView.setVisibility(INVISIBLE);
            bitmap = (Bitmap) BitmapFactory.decodeFile(item.getPath());
            if (bitmap != null) {
                Log.i("ShowActivity", item.getPath());
            }
            imageView.setImageBitmap(bitmap);
        } else {
            videoView.setVisibility(VISIBLE);
            imageView.setVisibility(INVISIBLE);
            mediaController = new MediaController(context);
            videoView.setVideoPath(item.getPath());
            videoView.setMediaController(mediaController);
        }
    }

    // 重新载入数据
    public void reload() {
        if (imageVedioItem.getType().equals("jpg")) {
            Log.i("ShowActivity","图片..."+imageVedioItem.getPath());
            imageView.setVisibility(VISIBLE);
            videoView.setVisibility(INVISIBLE);
            bitmap = (Bitmap) BitmapFactory.decodeFile(imageVedioItem.getPath());
            imageView.setImageBitmap(bitmap);
        } else {
            videoView.setVisibility(VISIBLE);
            imageView.setVisibility(INVISIBLE);
            videoView.setVideoPath(imageVedioItem.getPath());
            videoView.setMediaController(mediaController);
        }
    }

    // 回收数据
    public void recycle() {
        imageView.setImageBitmap(null);
        if (this.bitmap == null || this.bitmap.isRecycled()) {
            return;
        }
        this.bitmap.recycle();
        this.bitmap = null;
    }

}