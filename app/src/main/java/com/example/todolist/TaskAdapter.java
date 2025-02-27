package com.example.todolist;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class TaskAdapter extends RecyclerView.Adapter<TaskAdapter.TaskViewHolder> {
    private List<String> tasks;
    private Context context;

    public TaskAdapter(List<String> tasks, Context context) {
        this.tasks = tasks;
        this.context = context;
    }

    @Override
    public TaskViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(android.R.layout.simple_list_item_1, parent, false);
        return new TaskViewHolder(view);
    }

    @Override
    public void onBindViewHolder(TaskViewHolder holder, int position) {
        holder.taskText.setText(tasks.get(position));

        holder.itemView.setOnClickListener(v -> {
            String deletedTask = tasks.get(position);
            removeItem(position);

            // Check permission before showing notification
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                NotificationHelper.showNotification(context, "Task Completed", "You deleted: " + deletedTask);
            }
        });
    }

    @Override
    public int getItemCount() {
        return tasks.size();
    }

    public void removeItem(int position) {
        tasks.remove(position);
        notifyItemRemoved(position);
    }

    public static class TaskViewHolder extends RecyclerView.ViewHolder {
        TextView taskText;

        public TaskViewHolder(View itemView) {
            super(itemView);
            taskText = itemView.findViewById(android.R.id.text1);
        }
    }
}
