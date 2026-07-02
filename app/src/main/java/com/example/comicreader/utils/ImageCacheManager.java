package com.example.comicreader.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;
import android.util.LruCache;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 图片缓存管理器
 * 实现内存缓存 + 磁盘缓存，减少重复加载
 */
public class ImageCacheManager {
    private static ImageCacheManager instance;
    private LruCache<String, Bitmap> memoryCache;
    private File diskCacheDir;
    private long diskCacheSize = 50 * 1024 * 1024; // 50MB磁盘缓存
    private Context context;

    private ImageCacheManager(Context context) {
        this.context = context.getApplicationContext();
        initMemoryCache();
        initDiskCache();
    }

    public static synchronized ImageCacheManager getInstance(Context context) {
        if (instance == null) {
            instance = new ImageCacheManager(context);
        }
        return instance;
    }

    private void initMemoryCache() {
        // 内存缓存大小为可用内存的1/8
        long maxMemory = Runtime.getRuntime().maxMemory();
        int cacheSize = (int) (maxMemory / 8);

        memoryCache = new LruCache<String, Bitmap>(cacheSize) {
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                return bitmap.getByteCount();
            }

            @Override
            protected void entryRemoved(boolean evicted, String key, Bitmap oldValue, Bitmap newValue) {
                if (evicted && oldValue != null) {
                    // 内存缓存被移除时，写入磁盘缓存
                    putToDiskCache(key, oldValue);
                }
            }
        };
    }

    private void initDiskCache() {
        // 磁盘缓存目录
        diskCacheDir = new File(context.getCacheDir(), "image_cache");
        if (!diskCacheDir.exists()) {
            diskCacheDir.mkdirs();
        }
    }

    /**
     * 获取缓存的图片
     * 先从内存缓存查找，如果没有再从磁盘缓存查找
     */
    public Bitmap getBitmap(String path) {
        String key = hashKey(path);

        // 先从内存缓存查找
        Bitmap bitmap = memoryCache.get(key);
        if (bitmap != null) {
            return bitmap;
        }

        // 从磁盘缓存查找
        bitmap = getFromDiskCache(key);
        if (bitmap != null) {
            // 放入内存缓存
            memoryCache.put(key, bitmap);
            return bitmap;
        }

        return null;
    }

    /**
     * 添加图片到缓存
     * 同时放入内存缓存和磁盘缓存
     */
    public void putBitmap(String path, Bitmap bitmap) {
        String key = hashKey(path);

        // 放入内存缓存
        if (bitmap != null && memoryCache.get(key) == null) {
            memoryCache.put(key, bitmap);
        }

        // 放入磁盘缓存
        putToDiskCache(key, bitmap);
    }

    /**
     * 从磁盘缓存获取图片
     */
    private Bitmap getFromDiskCache(String key) {
        File file = new File(diskCacheDir, key);
        if (!file.exists()) {
            return null;
        }

        try {
            FileInputStream fis = new FileInputStream(file);
            Bitmap bitmap = BitmapFactory.decodeStream(fis);
            fis.close();
            return bitmap;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 写入磁盘缓存
     */
    private void putToDiskCache(String key, Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()) {
            return;
        }

        // 检查磁盘缓存大小，如果超过限制则清理旧文件
        checkDiskCacheSize();

        File file = new File(diskCacheDir, key);
        try {
            FileOutputStream fos = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, fos);
            fos.flush();
            fos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 检查磁盘缓存大小，超过限制则清理旧文件
     */
    private void checkDiskCacheSize() {
        long currentSize = getDiskCacheSize();
        if (currentSize > diskCacheSize) {
            // 清理最旧的20%文件
            File[] files = diskCacheDir.listFiles();
            if (files != null) {
                int deleteCount = files.length / 5;
                for (int i = 0; i < deleteCount && i < files.length; i++) {
                    files[i].delete();
                }
            }
        }
    }

    /**
     * 获取磁盘缓存大小
     */
    private long getDiskCacheSize() {
        File[] files = diskCacheDir.listFiles();
        if (files == null) {
            return 0;
        }

        long size = 0;
        for (File file : files) {
            size += file.length();
        }
        return size;
    }

    /**
     * 清除所有缓存
     */
    public void clearCache() {
        // 清除内存缓存
        memoryCache.evictAll();

        // 清除磁盘缓存
        File[] files = diskCacheDir.listFiles();
        if (files != null) {
            for (File file : files) {
                file.delete();
            }
        }
    }

    /**
     * 生成缓存key（使用MD5哈希）
     */
    private String hashKey(String key) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] bytes = md.digest(key.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return key.replace("/", "_").replace(".", "_");
        }
    }

    /**
     * 预加载图片到缓存
     * 用于页面预加载
     */
    public void preloadBitmap(String path) {
        if (path == null || path.isEmpty()) {
            return;
        }

        if (getBitmap(path) != null) {
            return; // 已经在缓存中
        }

        // 异步加载图片
        new Thread(() -> {
            try {
                // 先检查文件是否存在
                File file = new File(path);
                if (!file.exists()) {
                    return;
                }

                // 加载压缩后的图片，减少内存占用
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(path, options);

                int imgWidth = options.outWidth;
                int imgHeight = options.outHeight;
                if (imgWidth <= 0 || imgHeight <= 0) {
                    return;
                }

                // 根据屏幕宽度计算采样率
                int reqWidth = 1080; // 默认预加载宽度
                int inSampleSize = 1;
                if (imgWidth > reqWidth) {
                    inSampleSize = imgWidth / reqWidth;
                }

                options.inJustDecodeBounds = false;
                options.inSampleSize = inSampleSize;
                options.inPreferredConfig = Bitmap.Config.ARGB_8888;

                Bitmap bitmap = BitmapFactory.decodeFile(path, options);
                if (bitmap != null) {
                    putBitmap(path, bitmap);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }
}