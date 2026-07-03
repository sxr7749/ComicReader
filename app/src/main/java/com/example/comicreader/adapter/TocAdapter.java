package com.example.comicreader.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.comicreader.R;
import com.example.comicreader.utils.EpubParser;
import java.util.ArrayList;
import java.util.List;

public class TocAdapter extends RecyclerView.Adapter<TocAdapter.TocViewHolder> {

    private List<EpubParser.TocItem> flatTocItems;
    private OnTocItemClickListener listener;
    private int currentChapterIndex;
    private Context context;

    public interface OnTocItemClickListener {
        void onTocItemClick(int chapterIndex);
    }

    public TocAdapter(Context context, List<EpubParser.TocItem> tocItems, int currentChapterIndex, OnTocItemClickListener listener) {
        this.context = context;
        this.flatTocItems = flattenToc(tocItems);
        this.currentChapterIndex = currentChapterIndex;
        this.listener = listener;
    }

    private List<EpubParser.TocItem> flattenToc(List<EpubParser.TocItem> tocItems) {
        List<EpubParser.TocItem> flatList = new ArrayList<>();
        java.util.Set<Integer> addedChapterIndices = new java.util.HashSet<>();
        
        if (tocItems == null) return flatList;
        
        for (EpubParser.TocItem item : tocItems) {
            if (item.chapterIndex >= 0 && !addedChapterIndices.contains(item.chapterIndex)) {
                flatList.add(item);
                addedChapterIndices.add(item.chapterIndex);
            }
            if (item.children != null && !item.children.isEmpty()) {
                for (EpubParser.TocItem child : flattenToc(item.children)) {
                    if (child.chapterIndex >= 0 && !addedChapterIndices.contains(child.chapterIndex)) {
                        flatList.add(child);
                        addedChapterIndices.add(child.chapterIndex);
                    }
                }
            }
        }
        return flatList;
    }

    public void setCurrentChapterIndex(int index) {
        this.currentChapterIndex = index;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public TocViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_toc, parent, false);
        return new TocViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TocViewHolder holder, int position) {
        EpubParser.TocItem item = flatTocItems.get(position);
        holder.title.setText(item.title);
        
        int paddingLeft = (item.level - 1) * 32;
        holder.title.setPadding(paddingLeft + 16, 0, 16, 0);
        
        if (item.chapterIndex == currentChapterIndex) {
            holder.title.setTextColor(context.getResources().getColor(R.color.accent));
            holder.itemView.setBackgroundColor(context.getResources().getColor(R.color.surface_variant));
        } else {
            holder.title.setTextColor(context.getResources().getColor(R.color.text_primary));
            holder.itemView.setBackgroundColor(context.getResources().getColor(R.color.background));
        }
        
        holder.itemView.setOnClickListener(v -> {
            if (listener != null && item.chapterIndex >= 0) {
                listener.onTocItemClick(item.chapterIndex);
            }
        });
    }

    @Override
    public int getItemCount() {
        return flatTocItems.size();
    }

    static class TocViewHolder extends RecyclerView.ViewHolder {
        TextView title;

        TocViewHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.toc_title);
        }
    }
}