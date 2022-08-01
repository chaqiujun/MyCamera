package com.example.mycamera;

import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;

import java.util.List;

public class MyPagerAdapter extends FragmentPagerAdapter {

    private List<Fragment> layoutList;

    public MyPagerAdapter(FragmentManager manager, List<Fragment> layoutList) {
        super(manager);
        this.layoutList = layoutList;
    }

    // 页面数
    @Override
    public int getCount() {
        return layoutList.size();
    }

    @NonNull
    @Override
    public Fragment getItem(int position) {
        return layoutList.get(position);
    }

    @Override
    public void setPrimaryItem(ViewGroup container, int position, Object object) {
        super.setPrimaryItem(container, position, object);
    }
}