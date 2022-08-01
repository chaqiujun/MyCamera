package com.example.mycamera;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

class RecyclerViewAdapter extends RecyclerView.Adapter<RecyclerViewAdapter.MyViewHolder> {

    private final Context context;
    private final List<String> funs;
    private MyOnItemClickListener clickListener;

    public RecyclerViewAdapter(Context context, List<String> funs) {
        this.context = context;
        this.funs = funs;
    }

    // 将条目的xml转换成view
    @NonNull
    @Override
    public MyViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new MyViewHolder(LayoutInflater.from(context).inflate(R.layout.item, parent, false));
    }

    // 设置控件的内容
    @Override
    public void onBindViewHolder(MyViewHolder holder, int position) {
        holder.textView.setText(funs.get(position).toString());
        if (position == 0 ) {
            holder.textView.setTextColor(context.getResources().getColor(R.color.black));
        }
    }

    // 获取item数
    @Override
    public int getItemCount() {
        return funs.size();
    }

    public void setOnItemClickListener(MyOnItemClickListener listener) {
        this.clickListener = listener;
    }

    // 获取控件的holder
    class MyViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {

        public TextView textView;

        public MyViewHolder(View itemView) {
            super(itemView);
            textView = itemView.findViewById(R.id.tv);
            itemView.setOnClickListener(this);
        }

        @Override
        public void onClick(View v) {
            if (clickListener != null) {
                clickListener.onItemClick(v, getAdapterPosition());
            }
        }

    }
}