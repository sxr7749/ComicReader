package com.example.comicreader.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.example.comicreader.R;
import com.example.comicreader.model.ComicBook;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ComicAdapter extends RecyclerView.Adapter<ComicAdapter.ComicViewHolder> {
    private List<ComicBook> comics;
    private OnComicClickListener listener;
    private boolean isSelectionMode = false;
    private Set<String> selectedIds = new HashSet<>();

    public ComicAdapter(List<ComicBook> comics, OnComicClickListener listener) {
        this.comics = comics;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ComicViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_comic, parent, false);
        return new ComicViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ComicViewHolder holder, int position) {
        ComicBook comic = comics.get(position);
        holder.bind(comic, isSelectionMode, selectedIds.contains(comic.getId()));
    }

    @Override
    public int getItemCount() {
        return comics.size();
    }

    public void updateList(List<ComicBook> newComics) {
        comics = newComics;
        notifyDataSetChanged();
    }

    public interface OnComicClickListener {
        void onComicClick(ComicBook comic);
        void onComicLongClick(ComicBook comic);
        void onSelectionModeChanged(boolean isSelectionMode, int selectedCount);
        void onSelectionChanged(String comicId, boolean isSelected);
    }

    class ComicViewHolder extends RecyclerView.ViewHolder {
        ImageView coverImage;
        TextView titleText;
        TextView progressText;
        ProgressBar progressBar;
        CheckBox checkboxSelect;

        ComicViewHolder(@NonNull View itemView) {
            super(itemView);
            coverImage = itemView.findViewById(R.id.cover_image);
            titleText = itemView.findViewById(R.id.title_text);
            progressText = itemView.findViewById(R.id.progress_text);
            progressBar = itemView.findViewById(R.id.progress_bar);
            checkboxSelect = itemView.findViewById(R.id.checkbox_select);

            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    ComicBook comic = comics.get(position);
                    if (isSelectionMode) {
                        toggleSelection(comic.getId());
                    } else {
                        listener.onComicClick(comic);
                    }
                }
            });

            itemView.setOnLongClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    ComicBook comic = comics.get(position);
                    if (!isSelectionMode) {
                        if (listener != null) {
                            listener.onComicLongClick(comic);
                        }
                    } else {
                        toggleSelection(comic.getId());
                    }
                }
                return true;
            });
        }

        void bind(ComicBook comic, boolean selectionMode, boolean isSelected) {
            titleText.setText(comic.getTitle());

            if (comic.getCoverPath() != null && !comic.getCoverPath().isEmpty()) {
                Glide.with(itemView.getContext())
                        .load(comic.getCoverPath())
                        .placeholder(R.drawable.ic_launcher_background)
                        .error(R.drawable.ic_launcher_background)
                        .centerCrop()
                        .into(coverImage);
            } else {
                coverImage.setImageResource(R.drawable.ic_launcher_background);
            }

            float progress = comic.getProgress();
            progressBar.setProgress((int) progress);
            progressText.setText(String.format("%.0f%%", progress));

            checkboxSelect.setVisibility(selectionMode ? View.VISIBLE : View.GONE);
            checkboxSelect.setChecked(isSelected);
        }
    }

    public void toggleSelectionMode() {
        isSelectionMode = !isSelectionMode;
        if (!isSelectionMode) {
            selectedIds.clear();
        }
        notifyDataSetChanged();
        listener.onSelectionModeChanged(isSelectionMode, selectedIds.size());
    }

    public void toggleSelection(String comicId) {
        if (selectedIds.contains(comicId)) {
            selectedIds.remove(comicId);
        } else {
            selectedIds.add(comicId);
        }
        notifyDataSetChanged();
        listener.onSelectionModeChanged(isSelectionMode, selectedIds.size());
        listener.onSelectionChanged(comicId, selectedIds.contains(comicId));
    }

    public void selectAll() {
        for (ComicBook comic : comics) {
            selectedIds.add(comic.getId());
        }
        notifyDataSetChanged();
        listener.onSelectionModeChanged(isSelectionMode, selectedIds.size());
    }

    public void clearSelection() {
        selectedIds.clear();
        notifyDataSetChanged();
        listener.onSelectionModeChanged(isSelectionMode, selectedIds.size());
    }

    public List<String> getSelectedIds() {
        return new ArrayList<>(selectedIds);
    }

    public int getSelectedCount() {
        return selectedIds.size();
    }

    public boolean isSelectionMode() {
        return isSelectionMode;
    }

    public void setSelectionMode(boolean selectionMode) {
        isSelectionMode = selectionMode;
        if (!isSelectionMode) {
            selectedIds.clear();
        }
        notifyDataSetChanged();
        listener.onSelectionModeChanged(isSelectionMode, selectedIds.size());
    }
}