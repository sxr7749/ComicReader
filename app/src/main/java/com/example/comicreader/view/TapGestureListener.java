package com.example.comicreader.view;

import android.content.Context;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import androidx.recyclerview.widget.RecyclerView;

public class TapGestureListener implements RecyclerView.OnItemTouchListener {
    private GestureDetector gestureDetector;
    private OnTapListener onTapListener;

    public interface OnTapListener {
        void onTap(float x, float y);
    }

    public TapGestureListener(Context context, OnTapListener listener) {
        this.onTapListener = listener;
        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                if (onTapListener != null) {
                    onTapListener.onTap(e.getX(), e.getY());
                }
                return true;
            }
        });
    }

    @Override
    public boolean onInterceptTouchEvent(RecyclerView rv, MotionEvent e) {
        gestureDetector.onTouchEvent(e);
        return false;
    }

    @Override
    public void onTouchEvent(RecyclerView rv, MotionEvent e) {
    }

    @Override
    public void onRequestDisallowInterceptTouchEvent(boolean disallowIntercept) {
    }
}