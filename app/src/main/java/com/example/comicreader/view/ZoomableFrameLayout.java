package com.example.comicreader.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.FrameLayout;

public class ZoomableFrameLayout extends FrameLayout {
    private ScaleGestureDetector scaleGestureDetector;
    private GestureDetector gestureDetector;
    private Matrix matrix = new Matrix();
    private float[] matrixValues = new float[9];
    private float scale = 1f;
    private float minScale = 0.5f;
    private float maxScale = 3f;
    private boolean isZooming = false;
    private boolean isDragging = false;
    private float startX = 0f;
    private float startY = 0f;
    private float viewWidth = 0f;
    private float viewHeight = 0f;
    private OnTapListener onTapListener;

    public interface OnTapListener {
        void onTap(float x, float y);
    }

    public void setOnTapListener(OnTapListener listener) {
        this.onTapListener = listener;
    }

    public ZoomableFrameLayout(Context context) {
        super(context);
        init(context);
    }

    public ZoomableFrameLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public ZoomableFrameLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context);
    }

    private void init(Context context) {
        scaleGestureDetector = new ScaleGestureDetector(context, new ScaleListener());
        gestureDetector = new GestureDetector(context, new GestureListener());
        matrix.setTranslate(0, 0);
        matrix.setScale(1, 1);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        viewWidth = w;
        viewHeight = h;
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        canvas.save();
        canvas.concat(matrix);
        super.dispatchDraw(canvas);
        canvas.restore();
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        scaleGestureDetector.onTouchEvent(ev);
        gestureDetector.onTouchEvent(ev);

        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                startX = ev.getX();
                startY = ev.getY();
                isDragging = false;
                break;

            case MotionEvent.ACTION_POINTER_DOWN:
                if (ev.getPointerCount() == 2) {
                    isZooming = true;
                    isDragging = false;
                    return true;
                }
                break;

            case MotionEvent.ACTION_MOVE:
                if (isZooming) {
                    return true;
                }

                if (scale != 1f && ev.getPointerCount() == 1) {
                    float dx = ev.getX() - startX;
                    float dy = ev.getY() - startY;

                    if (Math.abs(dx) > 8 || Math.abs(dy) > 8) {
                        if (canDrag(dx, dy)) {
                            isDragging = true;
                            return true;
                        }
                    }
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                isZooming = false;
                isDragging = false;
                break;
        }

        return super.onInterceptTouchEvent(ev);
    }

    private boolean canDrag(float dx, float dy) {
        if (scale == 1f) {
            return false;
        }

        matrix.getValues(matrixValues);
        float transX = matrixValues[Matrix.MTRANS_X];
        float transY = matrixValues[Matrix.MTRANS_Y];

        float scaledWidth = viewWidth * scale;
        float scaledHeight = viewHeight * scale;

        boolean canDragX = false;
        boolean canDragY = false;

        if (scale > 1f) {
            float maxOffsetX = (scaledWidth - viewWidth) / 2f;
            float maxOffsetY = (scaledHeight - viewHeight) / 2f;

            if (scaledWidth > viewWidth) {
                if (dx > 0 && transX < maxOffsetX) {
                    canDragX = true;
                } else if (dx < 0 && transX > -maxOffsetX) {
                    canDragX = true;
                }
            }

            if (scaledHeight > viewHeight) {
                if (dy > 0 && transY < maxOffsetY) {
                    canDragY = true;
                } else if (dy < 0 && transY > -maxOffsetY) {
                    canDragY = true;
                }
            }
        } else {
            float maxTransX = viewWidth - scaledWidth;
            float maxTransY = viewHeight - scaledHeight;

            if (dx > 0 && transX < maxTransX) {
                canDragX = true;
            } else if (dx < 0 && transX > 0) {
                canDragX = true;
            }

            if (dy > 0 && transY < maxTransY) {
                canDragY = true;
            } else if (dy < 0 && transY > 0) {
                canDragY = true;
            }
        }

        return canDragX || canDragY;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        scaleGestureDetector.onTouchEvent(ev);
        gestureDetector.onTouchEvent(ev);

        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                startX = ev.getX();
                startY = ev.getY();
                break;

            case MotionEvent.ACTION_MOVE:
                if (isDragging && scale != 1f && !isZooming) {
                    float dx = ev.getX() - startX;
                    float dy = ev.getY() - startY;

                    matrix.getValues(matrixValues);
                    float currentTransX = matrixValues[Matrix.MTRANS_X];
                    float currentTransY = matrixValues[Matrix.MTRANS_Y];

                    float newTransX = currentTransX + dx;
                    float newTransY = currentTransY + dy;

                    constrainPosition(newTransX, newTransY);

                    startX = ev.getX();
                    startY = ev.getY();
                    invalidate();
                    return true;
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                isDragging = false;
                isZooming = false;
                break;
        }
        return super.onTouchEvent(ev);
    }

    private void constrainPosition(float transX, float transY) {
        float scaledWidth = viewWidth * scale;
        float scaledHeight = viewHeight * scale;

        float minX, maxX, minY, maxY;

        if (scale > 1f) {
            float maxOffsetX = (scaledWidth - viewWidth) / 2f;
            float maxOffsetY = (scaledHeight - viewHeight) / 2f;
            minX = -maxOffsetX;
            maxX = maxOffsetX;
            minY = -maxOffsetY;
            maxY = maxOffsetY;
        } else {
            minX = 0;
            maxX = viewWidth - scaledWidth;
            minY = 0;
            maxY = viewHeight - scaledHeight;
        }

        float constrainedX = Math.max(minX, Math.min(maxX, transX));
        float constrainedY = Math.max(minY, Math.min(maxY, transY));

        matrix.getValues(matrixValues);
        matrixValues[Matrix.MTRANS_X] = constrainedX;
        matrixValues[Matrix.MTRANS_Y] = constrainedY;
        matrix.setValues(matrixValues);
    }

    public float getScale() {
        return scale;
    }

    public void setScale(float newScale) {
        scale = Math.max(minScale, Math.min(maxScale, newScale));

        matrix.reset();
        matrix.postScale(scale, scale);

        float scaledWidth = viewWidth * scale;
        float scaledHeight = viewHeight * scale;

        float offsetX, offsetY;

        if (scale >= 1f) {
            offsetX = (viewWidth - scaledWidth) / 2f;
            offsetY = (viewHeight - scaledHeight) / 2f;
        } else {
            offsetX = 0;
            offsetY = 0;
        }

        matrix.postTranslate(offsetX, offsetY);
        invalidate();
    }

    public void zoomIn() {
        float newScale = scale + 0.2f;
        setScale(newScale);
    }

    public void zoomOut() {
        float newScale = scale - 0.2f;
        setScale(newScale);
    }

    public void resetZoom() {
        scale = 1f;
        matrix.reset();
        invalidate();
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        private float lastScale = 1f;

        @Override
        public boolean onScaleBegin(ScaleGestureDetector detector) {
            isZooming = true;
            lastScale = scale;
            return true;
        }

        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            float scaleFactor = detector.getScaleFactor();
            float newScale = lastScale * scaleFactor;
            newScale = Math.max(minScale, Math.min(maxScale, newScale));

            float focusX = detector.getFocusX();
            float focusY = detector.getFocusY();

            matrix.getValues(matrixValues);
            float oldScale = matrixValues[Matrix.MSCALE_X];
            float oldTransX = matrixValues[Matrix.MTRANS_X];
            float oldTransY = matrixValues[Matrix.MTRANS_Y];

            float scaleDiff = newScale / oldScale;

            float newTransX = focusX - (focusX - oldTransX) * scaleDiff;
            float newTransY = focusY - (focusY - oldTransY) * scaleDiff;

            matrix.reset();
            matrix.postScale(newScale, newScale);
            matrix.postTranslate(newTransX, newTransY);

            scale = newScale;
            constrainPosition(newTransX, newTransY);

            lastScale = newScale;

            invalidate();
            return true;
        }

        @Override
        public void onScaleEnd(ScaleGestureDetector detector) {
            isZooming = false;
        }
    }

    private class GestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {
            if (onTapListener != null) {
                onTapListener.onTap(e.getX(), e.getY());
            }
            return false;
        }

        @Override
        public boolean onDoubleTap(MotionEvent e) {
            if (scale > 1f) {
                resetZoom();
            } else {
                setScale(2.0f);
            }
            return true;
        }
    }
}