package com.example.comicreader.utils;

import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class FileUtils {
    public static final String[] IMAGE_EXTENSIONS = {".jpg", ".jpeg", ".png", ".gif", ".bmp", ".webp"};
    public static final String[] SUPPORTED_EXTENSIONS = {".pdf", ".zip", ".epub", ".mobi", ".jpg", ".jpeg", ".png", ".gif", ".bmp", ".webp"};

    public static boolean isImageFile(String fileName) {
        if (fileName == null) return false;
        String lower = fileName.toLowerCase();
        for (String ext : IMAGE_EXTENSIONS) {
            if (lower.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isSupportedFile(String fileName) {
        if (fileName == null) return false;
        String lower = fileName.toLowerCase();
        for (String ext : SUPPORTED_EXTENSIONS) {
            if (lower.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }

    public static String getFileExtension(String fileName) {
        if (fileName == null) return "";
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot == -1) return "";
        return fileName.substring(lastDot).toLowerCase();
    }

    public static String getFileNameWithoutExtension(String fileName) {
        if (fileName == null) return "";
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot == -1) return fileName;
        return fileName.substring(0, lastDot);
    }

    public static File getComicDirectory(Context context) {
        File dir = new File(context.getFilesDir(), "comics");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    public static File getCacheDirectory(Context context) {
        File dir;
        if (context.getExternalCacheDir() != null) {
            dir = new File(context.getExternalCacheDir(), "comic_cache");
        } else {
            dir = new File(context.getCacheDir(), "comic_cache");
        }
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    public static String generateId() {
        return UUID.randomUUID().toString();
    }

    public static List<File> getImageFilesFromDirectory(File directory) {
        List<File> imageFiles = new ArrayList<>();
        if (!directory.exists() || !directory.isDirectory()) {
            return imageFiles;
        }

        File[] files = directory.listFiles();
        if (files == null) {
            return imageFiles;
        }

        Arrays.sort(files);
        for (File file : files) {
            if (file.isFile() && isImageFile(file.getName())) {
                imageFiles.add(file);
            } else if (file.isDirectory()) {
                imageFiles.addAll(getImageFilesFromDirectory(file));
            }
        }
        return imageFiles;
    }

    public static boolean copyFile(InputStream inputStream, OutputStream outputStream) throws IOException {
        byte[] buffer = new byte[8192];
        int bytesRead;
        boolean hasData = false;
        while ((bytesRead = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, bytesRead);
            hasData = true;
        }
        outputStream.flush();
        return hasData;
    }

    public static File copyFileToAppDir(Context context, Uri uri, String fileName) throws IOException {
        File appDir = getComicDirectory(context);
        File destFile = new File(appDir, fileName);

        try (InputStream inputStream = context.getContentResolver().openInputStream(uri);
             OutputStream outputStream = new FileOutputStream(destFile)) {
            if (inputStream != null) {
                copyFile(inputStream, outputStream);
            }
        }

        return destFile;
    }

    public static long getFileSize(File file) {
        if (!file.exists()) return 0;
        return file.length();
    }

    public static String formatFileSize(long size) {
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return String.format("%.2f KB", size / 1024.0);
        if (size < 1024 * 1024 * 1024) return String.format("%.2f MB", size / (1024.0 * 1024));
        return String.format("%.2f GB", size / (1024.0 * 1024 * 1024));
    }

    public static File copyAssetToAppDir(Context context, String assetPath, String destFileName) throws IOException {
        File appDir = getComicDirectory(context);
        File destFile = new File(appDir, destFileName);
        
        try (InputStream inputStream = context.getAssets().open(assetPath);
             OutputStream outputStream = new FileOutputStream(destFile)) {
            if (inputStream != null) {
                copyFile(inputStream, outputStream);
            }
        }
        
        return destFile;
    }

    public static String[] listAssets(Context context, String path) throws IOException {
        return context.getAssets().list(path);
    }
}