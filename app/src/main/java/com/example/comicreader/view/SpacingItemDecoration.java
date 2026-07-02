package com.example.comicreader.view;

import android.graphics.Rect;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

public class SpacingItemDecoration extends RecyclerView.ItemDecoration {
    private int spacing;
    private boolean spacingEnabled = true;

    public SpacingItemDecoration(int spacing) {
        this.spacing = spacing;
    }

    public void setSpacingEnabled(boolean enabled) {
        this.spacingEnabled = enabled;
    }

    @Override
    public void getItemOffsets(@NonNull Rect outRect, @NonNull View view,
                               @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        if (spacingEnabled) {
            outRect.top = spacing;
            outRect.bottom = spacing;
        } else {
            outRect.top = 0;
            outRect.bottom = 0;
        }
    }
}