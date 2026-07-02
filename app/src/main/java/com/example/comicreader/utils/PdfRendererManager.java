package com.example.comicreader.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class PdfRendererManager {
    private static final Map<String, PdfRendererManager> instances = new HashMap<>();

    private File pdfFile;
    private ParcelFileDescriptor fileDescriptor;
    private PdfRenderer renderer;
    private File cacheDir;
    private int pageCount = -1;
    private boolean isOpen = false;

    private PdfRendererManager(File pdfFile, Context context) throws IOException {
        this.pdfFile = pdfFile;
        this.cacheDir = new File(context.getExternalCacheDir() != null ? context.getExternalCacheDir() : context.getCacheDir(), "pdf_pages");
        if (!cacheDir.exists()) {
            cacheDir.mkdirs();
        }
        openRenderer();
    }

    public static synchronized PdfRendererManager getInstance(File pdfFile, Context context) throws IOException {
        String key = pdfFile.getAbsolutePath();
        PdfRendererManager manager = instances.get(key);
        if (manager == null || !manager.isOpen) {
            if (manager != null) {
                manager.closeRenderer();
            }
            manager = new PdfRendererManager(pdfFile, context);
            instances.put(key, manager);
        }
        return manager;
    }

    public static synchronized void releaseAll() {
        for (PdfRendererManager manager : instances.values()) {
            manager.closeRenderer();
        }
        instances.clear();
    }

    private void openRenderer() throws IOException {
        if (isOpen) return;
        fileDescriptor = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY);
        renderer = new PdfRenderer(fileDescriptor);
        pageCount = renderer.getPageCount();
        isOpen = true;
    }

    private void closeRenderer() {
        isOpen = false;
        try {
            if (renderer != null) {
                renderer.close();
                renderer = null;
            }
            if (fileDescriptor != null) {
                fileDescriptor.close();
                fileDescriptor = null;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public int getPageCount() {
        return pageCount;
    }

    public File getCachedPageFile(int pageIndex) {
        return new File(cacheDir, pdfFile.getName().hashCode() + "_p" + pageIndex + ".jpg");
    }

    public boolean isPageCached(int pageIndex) {
        return getCachedPageFile(pageIndex).exists();
    }

    public synchronized Bitmap renderPage(int pageIndex, int targetWidth) throws IOException {
        if (!isOpen || renderer == null) {
            openRenderer();
        }
        if (pageIndex < 0 || pageIndex >= pageCount) {
            return null;
        }

        PdfRenderer.Page page = null;
        try {
            page = renderer.openPage(pageIndex);
            
            float originalWidth = page.getWidth();
            float originalHeight = page.getHeight();
            float scale = (float) targetWidth / originalWidth;
            
            int width = targetWidth;
            int height = (int) (originalHeight * scale);
            
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            
            bitmap = enhanceContrast(bitmap);
            
            return bitmap;
        } finally {
            if (page != null) {
                page.close();
            }
        }
    }

    private Bitmap enhanceContrast(Bitmap bitmap) {
        Bitmap enhanced = bitmap.copy(Bitmap.Config.ARGB_8888, true);
        
        ColorMatrix colorMatrix = new ColorMatrix();
        float contrast = 1.2f;
        float brightness = 0.1f;
        
        colorMatrix.set(new float[] {
            contrast, 0, 0, 0, brightness * 255,
            0, contrast, 0, 0, brightness * 255,
            0, 0, contrast, 0, brightness * 255,
            0, 0, 0, 1, 0
        });
        
        Paint paint = new Paint();
        paint.setColorFilter(new ColorMatrixColorFilter(colorMatrix));
        
        Canvas canvas = new Canvas(enhanced);
        canvas.drawBitmap(enhanced, 0, 0, paint);
        
        return enhanced;
    }

    public synchronized File renderAndCachePage(int pageIndex, int targetWidth) throws IOException {
        File cacheFile = getCachedPageFile(pageIndex);
        if (cacheFile.exists()) {
            return cacheFile;
        }

        Bitmap bitmap = renderPage(pageIndex, targetWidth);
        if (bitmap == null) {
            return null;
        }

        try (FileOutputStream fos = new FileOutputStream(cacheFile)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, fos);
        } finally {
            bitmap.recycle();
        }

        return cacheFile;
    }

    public String extractCover(int pageIndex, int width, Context context) {
        try {
            File coverFile = new File(FileUtils.getCacheDirectory(context),
                    pdfFile.getName().hashCode() + "_cover.jpg");
            if (coverFile.exists()) {
                return coverFile.getAbsolutePath();
            }

            Bitmap bitmap = renderPage(pageIndex, width);
            if (bitmap != null) {
                try (FileOutputStream fos = new FileOutputStream(coverFile)) {
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 80, fos);
                }
                bitmap.recycle();
                return coverFile.getAbsolutePath();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "";
    }
}
