package com.example.comicreader.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

public class VerticalSeekBar extends View {
    private int progress = 0;
    private int max = 100;
    private OnProgressChangeListener listener;
    private Paint trackBgPaint;
    private Paint progressPaint;
    private Paint thumbPaint;
    private Paint thumbBorderPaint;
    private int thumbHeight = 48;
    private int thumbWidth = 14;
    private int trackWidth = 4;

    public interface OnProgressChangeListener {
        void onProgressChanged(int progress);
        void onStartTrackingTouch();
        void onStopTrackingTouch();
    }

    public VerticalSeekBar(Context context) {
        super(context);
        init();
    }

    public VerticalSeekBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public VerticalSeekBar(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    private void init() {
        trackBgPaint = new Paint();
        trackBgPaint.setColor(0x15FFFFFF);
        trackBgPaint.setStyle(Paint.Style.FILL);
        trackBgPaint.setAntiAlias(true);

        progressPaint = new Paint();
        progressPaint.setAntiAlias(true);

        thumbPaint = new Paint();
        thumbPaint.setAntiAlias(true);

        thumbBorderPaint = new Paint();
        thumbBorderPaint.setColor(0x80FFFFFF);
        thumbBorderPaint.setStyle(Paint.Style.STROKE);
        thumbBorderPaint.setStrokeWidth(1f);
        thumbBorderPaint.setAntiAlias(true);
    }

    public void setMax(int max) {
        this.max = max;
        invalidate();
    }

    public int getMax() {
        return max;
    }

    public void setProgress(int progress) {
        this.progress = Math.max(0, Math.min(max, progress));
        invalidate();
    }

    public int getProgress() {
        return progress;
    }

    public void setOnProgressChangeListener(OnProgressChangeListener listener) {
        this.listener = listener;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();
        int centerX = width / 2;

        float paddingTop = getPaddingTop() > 0 ? getPaddingTop() : 24;
        float paddingBottom = getPaddingBottom() > 0 ? getPaddingBottom() : 24;

        float trackTop = paddingTop;
        float trackBottom = height - paddingBottom;

        float halfTrack = trackWidth / 2f;
        float halfThumb = thumbWidth / 2f;

        RectF trackBgRect = new RectF();
        trackBgRect.left = centerX - halfTrack;
        trackBgRect.top = trackTop;
        trackBgRect.right = centerX + halfTrack;
        trackBgRect.bottom = trackBottom;
        float trackRadius = halfTrack;
        canvas.drawRoundRect(trackBgRect, trackRadius, trackRadius, trackBgPaint);

        float progressPercent = (float) progress / max;
        float trackHeight = trackBottom - trackTop;
        float availableHeight = trackHeight - thumbHeight;
        float thumbY = trackTop + thumbHeight / 2f + progressPercent * availableHeight;

        RectF thumbRect = new RectF();
        thumbRect.left = centerX - halfThumb;
        thumbRect.top = thumbY - thumbHeight / 2f;
        thumbRect.right = centerX + halfThumb;
        thumbRect.bottom = thumbY + thumbHeight / 2f;

        float progressBottom = thumbY + thumbHeight / 2f;
        if (progress > 0 && progressBottom > trackTop) {
            RectF progressRect = new RectF();
            progressRect.left = centerX - halfTrack;
            progressRect.top = trackTop;
            progressRect.right = centerX + halfTrack;
            progressRect.bottom = progressBottom;

            LinearGradient progressGradient = new LinearGradient(
                    0, trackTop,
                    0, trackBottom,
                    0xFFCE93D8,
                    0xFFAB47BC,
                    Shader.TileMode.CLAMP);
            progressPaint.setShader(progressGradient);

            canvas.drawRoundRect(progressRect, trackRadius, trackRadius, progressPaint);
        }

        LinearGradient thumbGradient = new LinearGradient(
                thumbRect.left, thumbRect.top,
                thumbRect.right, thumbRect.bottom,
                0xFFCE93D8,
                0xFFAB47BC,
                Shader.TileMode.CLAMP);
        thumbPaint.setShader(thumbGradient);

        float thumbRadius = halfThumb;
        canvas.drawRoundRect(thumbRect, thumbRadius, thumbRadius, thumbPaint);
        canvas.drawRoundRect(thumbRect, thumbRadius, thumbRadius, thumbBorderPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (listener != null) {
                    listener.onStartTrackingTouch();
                }
                updateProgress(event.getY());
                return true;

            case MotionEvent.ACTION_MOVE:
                updateProgress(event.getY());
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (listener != null) {
                    listener.onStopTrackingTouch();
                }
                return true;
        }
        return super.onTouchEvent(event);
    }

    private void updateProgress(float y) {
        int height = getHeight();
        float paddingTop = getPaddingTop() > 0 ? getPaddingTop() : 24;
        float paddingBottom = getPaddingBottom() > 0 ? getPaddingBottom() : 24;

        float trackTop = paddingTop;
        float trackBottom = height - paddingBottom;
        float trackHeight = trackBottom - trackTop;
        float availableHeight = trackHeight - thumbHeight;
        float relativeY = y - trackTop - thumbHeight / 2f;

        float progressPercent = Math.max(0f, Math.min(1f, relativeY / availableHeight));
        int newProgress = (int) (progressPercent * max);

        if (newProgress != progress) {
            progress = newProgress;
            invalidate();
            if (listener != null) {
                listener.onProgressChanged(progress);
            }
        }
    }
}
