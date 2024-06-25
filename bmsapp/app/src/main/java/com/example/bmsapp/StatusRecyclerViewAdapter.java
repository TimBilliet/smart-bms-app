package com.example.bmsapp;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class StatusRecyclerViewAdapter extends RecyclerView.Adapter<StatusRecyclerViewAdapter.ViewHolder> {

    private List<String> dataList;

    public StatusRecyclerViewAdapter(List<String> dataList) {
        this.dataList = dataList;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public TextView statusItem;

        public ViewHolder(View view) {
            super(view);
            statusItem = view.findViewById(R.id.status_item);
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.recyclerview_row, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        String item = dataList.get(position);
        holder.statusItem.setText(item);
    }

    @Override
    public int getItemCount() {
        return dataList.size();
    }
}
