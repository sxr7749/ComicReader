package com.example.comicreader.utils;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import androidx.annotation.NonNull;
import com.example.comicreader.model.UpdateInfo;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.gson.Gson;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class UpdateChecker {
    private static final String VERSION_URL = "https://raw.githubusercontent.com/sxr7749/ComicReader/master/version.json";

    public interface OnUpdateCheckListener {
        void onUpdateAvailable(UpdateInfo updateInfo);
        void onNoUpdate();
        void onError(String error);
    }

    public static void checkForUpdate(final Context context, final OnUpdateCheckListener listener) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    URL url = new URL(VERSION_URL);
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    connection.setConnectTimeout(10000);
                    connection.setReadTimeout(10000);
                    connection.setRequestMethod("GET");

                    int responseCode = connection.getResponseCode();
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        InputStream inputStream = connection.getInputStream();
                        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            sb.append(line);
                        }
                        reader.close();
                        inputStream.close();

                        Gson gson = new Gson();
                        UpdateInfo updateInfo = gson.fromJson(sb.toString(), UpdateInfo.class);

                        int currentVersionCode = getCurrentVersionCode(context);
                        if (updateInfo.getVersionCode() > currentVersionCode) {
                            listener.onUpdateAvailable(updateInfo);
                        } else {
                            listener.onNoUpdate();
                        }
                    } else {
                        listener.onError("服务器返回错误: " + responseCode);
                    }
                    connection.disconnect();
                } catch (Exception e) {
                    listener.onError("检查更新失败: " + e.getMessage());
                }
            }
        }).start();
    }

    public static int getCurrentVersionCode(Context context) {
        try {
            return context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            return 0;
        }
    }

    public static String getCurrentVersionName(Context context) {
        try {
            return context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "1.0.0";
        }
    }

    public static void showUpdateDialog(final Activity activity, final UpdateInfo updateInfo) {
        String message = "发现新版本 " + updateInfo.getVersionName() + "\n\n更新内容:\n" + updateInfo.getUpdateLog();
        
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity)
                .setTitle("版本更新")
                .setMessage(message);

        if (!updateInfo.isForceUpdate()) {
            builder.setNegativeButton("稍后更新", null);
        }

        builder.setPositiveButton("立即更新", (dialog, which) -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!activity.getPackageManager().canRequestPackageInstalls()) {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:" + activity.getPackageName()));
                    activity.startActivityForResult(intent, 10086);
                    return;
                }
            }
            downloadApk(activity, updateInfo.getDownloadUrl());
        });

        builder.setCancelable(!updateInfo.isForceUpdate());
        builder.show();
    }

    public static void downloadApk(Context context, String downloadUrl) {
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(downloadUrl));
        request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI | DownloadManager.Request.NETWORK_MOBILE);
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        request.setTitle("集漫 更新");
        request.setDescription("正在下载新版本...");
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "ComicReader.apk");

        DownloadManager downloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        if (downloadManager != null) {
            downloadManager.enqueue(request);
            Snackbar.make(((Activity) context).findViewById(android.R.id.content),
                    "开始下载，请在通知栏查看进度", Snackbar.LENGTH_LONG).show();
        }
    }

    public static void setVersionUrl(@NonNull String url) {
        // For testing or custom deployment
    }
}
