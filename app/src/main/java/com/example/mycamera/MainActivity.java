package com.example.mycamera;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSnapHelper;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager.widget.ViewPager;

import java.util.ArrayList;
import java.util.List;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class MainActivity extends AppCompatActivity implements View.OnClickListener, ViewPager.OnPageChangeListener {


    private List<String> funs;
    private RecyclerView recyclerView;
    private CenterLayoutManager myLayoutManager;
    private RecyclerViewAdapter myAdapter;
    private LinearSnapHelper linearSnapHelper;
    private MyItemDecoration myItemDecoration;
    private int prePosition = 0;
    private RecyclerViewAdapter.MyViewHolder preViewHolder;
    private RecyclerViewAdapter.MyViewHolder nowViewHolder;
    private Button btnChangeCameraType;
    private Button btnCameraVideo;
    public ImageView imgPreview;
    private ViewPager viewPager;
    private List<Fragment> layoutList;
    private MyPagerAdapter myPagerAdapter;
    private TakePhotos mTakePhotos;
    private RecoderVideo mRecoderVideo;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        // 1 申请权限
        PermissionCheck.checkPermission(this);
        // 2 找控件，初始化界面
        initView();
        ActionBar actionBar=getSupportActionBar();
        actionBar.hide();
    }

    // 权限请求回调，处理授权失败结果，拒绝授权则继续申请
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] mPermissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, mPermissions, grantResults);
        if (requestCode == PermissionCheck.REQUEST_CODE) {
            // flag = 1 则表示全部授权成功，否则表示未取得全部权限
            int flag = 1;
            for (int grantResult : grantResults) {
                if (grantResult != PackageManager.PERMISSION_GRANTED) {
                    flag = 0;
                    break;
                }
            }
            // 未取得全部权限
            if (flag == 0) {
                // 继续申请权限
                PermissionCheck.checkPermission(this);
            }
        }
    }

    /**
     * 初始化界面、寻找控件
     */
    private void initView() {

        // 找控件
        viewPager = (ViewPager) findViewById(R.id.view_pager);
        recyclerView = (RecyclerView) findViewById(R.id.rv);
        imgPreview = (ImageView) findViewById(R.id.preview);
        btnCameraVideo = (Button) findViewById(R.id.camera_video);
        btnChangeCameraType = (Button) findViewById(R.id.change);

        // viewPager配置照相和录像画面
        mTakePhotos = new TakePhotos(this);
        mRecoderVideo = new RecoderVideo(this);
        layoutList = new ArrayList<Fragment>();
        layoutList.add(mTakePhotos);
        layoutList.add(mRecoderVideo);
        // 设置适配器
        myPagerAdapter = new MyPagerAdapter(getSupportFragmentManager(), layoutList);
        viewPager.setAdapter(myPagerAdapter);

        // 功能列表
        funs = new ArrayList<String>();
        funs.add("照片");
        funs.add("视频");

        btnCameraVideo.setOnClickListener(this);
        btnChangeCameraType.setOnClickListener(this);
        imgPreview.setOnClickListener(this);
        viewPager.addOnPageChangeListener(this);

        // 初始化RecyclerView控件
        /* 1 创建
         * LayoutManager：RecyclerView布局管理
         * Adapter：RecyclerView内容适配器
         * LinearSnapHelper：滑动结束时使Item保持在中间的位置
         * MyItemDecoration：item修饰器
         * 对象
         */
        myLayoutManager = new CenterLayoutManager(this, LinearLayoutManager.HORIZONTAL, false);
        myAdapter = new RecyclerViewAdapter(this, funs);
        linearSnapHelper = new LinearSnapHelper();
        myItemDecoration = new MyItemDecoration();
        // 2 将上面的对象设置找到RecyclerView中
        linearSnapHelper.attachToRecyclerView(recyclerView);
        recyclerView.addItemDecoration(myItemDecoration);
        recyclerView.setLayoutManager(myLayoutManager);
        recyclerView.setAdapter(myAdapter);

        // 3 RecyclerView Item的点击回调监听
        myAdapter.setOnItemClickListener(new MyOnItemClickListener() {
            @Override
            public void onItemClick(View view, int position) {
                if (prePosition != position) {
                    changeTextColor(position);
                    changeButtonIcon(position);
                    // 更新prePosition
                    prePosition = position;
                    //使点击到的条目滚动到中间
                    recyclerView.smoothScrollToPosition(position);
                    // 切换页面
                    viewPager.setCurrentItem(position, true);
                }
            }
        });

        // 4 监听RecyclerView滚动
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    if (recyclerView.getChildCount() > 0) {
                        try {
                            // 获取居中的具体位置
                            int nowPosition = ((RecyclerView.LayoutParams) linearSnapHelper.findSnapView(recyclerView.getLayoutManager()).getLayoutParams()).getViewAdapterPosition();
                            if (prePosition != nowPosition) {
                                changeTextColor(nowPosition);
                                changeButtonIcon(nowPosition);
                                // 更新prePosition
                                prePosition = nowPosition;
                                // 切换页面
                                viewPager.setCurrentItem(nowPosition, true);
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        });

    }

    /**
     * 滑动功能条时，改变功能文本颜色
     */
    private void changeTextColor(int nowPosition) {
        // 上一个居中的item的holder
        preViewHolder = (RecyclerViewAdapter.MyViewHolder) recyclerView.getChildViewHolder(recyclerView.getChildAt(prePosition));
        // 将文本颜色设置为白色
        preViewHolder.textView.setTextColor(getResources().getColor(R.color.white));
        // 当前居中的item的holder
        nowViewHolder = (RecyclerViewAdapter.MyViewHolder) recyclerView.getChildViewHolder(recyclerView.getChildAt(nowPosition));
        // 将文本颜色设置为黑色
        nowViewHolder.textView.setTextColor(getResources().getColor(R.color.black));
    }

    /**
     * 滑动功能条时，改变按钮图标
     */
    private void changeButtonIcon(int position) {
        if (position == 0)
            btnCameraVideo.setBackgroundResource(R.drawable.camera);
        else {
            btnCameraVideo.setBackgroundResource(R.drawable.video);
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.camera_video:
                if (prePosition == 0) {
                    mTakePhotos.takePhoto();
                }
                else {
                    if (mRecoderVideo.isRecording) {
                        //再次按下将停止录制
                        mRecoderVideo.stopRecorder();
                        mRecoderVideo.isRecording = false;
                        btnCameraVideo.setBackgroundResource(R.drawable.video);
                    } else {
                        //第一次按下将isRecording置为ture
                        //配置并开始录制
                        mRecoderVideo.isRecording = true;
                        mRecoderVideo.startRecorder();
                        btnCameraVideo.setBackgroundResource(R.drawable.stop);
                    }
                }
                break;
            case R.id.change:
                mTakePhotos.changeCamera();
                break;
            case R.id.preview:
                Intent intent = new Intent();
                intent.setClass(this, ShowActivity.class);
                startActivity(intent);
                break;
        }
    }

    @Override
    public void onPageSelected(int position) {
        changeTextColor(position);
        // 更新prePosition
        prePosition = position;
        // 使点击到的条目滚动到中间
        recyclerView.smoothScrollToPosition(position);
        changeButtonIcon(position);
    }

    @Override
    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

    }

    @Override
    public void onPageScrollStateChanged(int state) {

    }
}
