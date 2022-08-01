package com.example.mycamera;

import android.graphics.Rect;
import android.view.View;
import android.widget.Toast;

import androidx.recyclerview.widget.RecyclerView;

/**
 * RecyclerView的item修饰器，使第一个item和最后一个item也能居中
 */
public class MyItemDecoration extends RecyclerView.ItemDecoration {

    // 第一张图片的左边距
    private int leftPageVisibleWidth;

    @Override
    public void getItemOffsets(Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {

        // 一张居中图片距离屏幕左边的距离：(屏幕的宽度-item的宽度)/2。其中item的宽度 = 实际TextView的宽度 + margin
        // TextView的宽度为90
        if (leftPageVisibleWidth == 0) {
            // 获取屏幕的宽度（像素）
            int screenWidth = DensityUtil.getScreenWidth(view.getContext());
            // 获取TextView的宽度（像素）
            int textViewWidth = DensityUtil.dip2px(view.getContext(), 90);
            //计算一次
            leftPageVisibleWidth = DensityUtil.px2dip(view.getContext(), screenWidth - textViewWidth) / 2;
        }

        // 获取当前Item的position
        int position = parent.getChildAdapterPosition(view);

        // 获得Item的数量
        int itemCount = parent.getAdapter().getItemCount();

        // 左、右外边距
        int leftMagin = 5, rightMagin = 5;
        // 如果是第一个item，左边距设置为leftPageVisibleWidth，否则设置为0
        if (position == 0) {
            leftMagin += DensityUtil.dip2px(view.getContext(), leftPageVisibleWidth);
        }
        // 如果是最后一个item，右边距设置为leftPageVisibleWidth（左右一样），否则设置为0
        if (position == itemCount - 1) {
            rightMagin += DensityUtil.dip2px(view.getContext(), leftPageVisibleWidth);
        }

        // 设置view布局
        RecyclerView.LayoutParams layoutParams = (RecyclerView.LayoutParams) view.getLayoutParams();
        //10，10分别是item到上下的margin
        layoutParams.setMargins(leftMagin, 0, rightMagin, 0);
        view.setLayoutParams(layoutParams);

        super.getItemOffsets(outRect, view, parent, state);
    }

}