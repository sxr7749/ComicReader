package com.example.comicreader.utils;

import android.content.Context;
import android.content.SharedPreferences;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 阅读统计管理器
 * 负责记录和统计用户的阅读数据
 */
public class ReadingStatsManager {
    private static final String PREFS_NAME = "reading_stats";
    private static final String KEY_TODAY_DURATION = "today_duration";
    private static final String KEY_TOTAL_DURATION = "total_duration";
    private static final String KEY_TODAY_PAGES = "today_pages";
    private static final String KEY_TOTAL_PAGES = "total_pages";
    private static final String KEY_READ_COUNT = "read_count";
    private static final String KEY_LAST_DATE = "last_date";
    private static final String KEY_START_TIME = "start_time";

    private final SharedPreferences prefs;
    private long startTime = 0;
    private boolean isReading = false;

    public ReadingStatsManager(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        checkAndResetDailyStats();
    }

    /**
     * 开始阅读
     */
    public void startReading() {
        if (!isReading) {
            startTime = System.currentTimeMillis();
            isReading = true;

            // 增加阅读次数
            int count = prefs.getInt(KEY_READ_COUNT, 0);
            prefs.edit().putInt(KEY_READ_COUNT, count + 1).apply();
        }
    }

    /**
     * 停止阅读
     */
    public void stopReading() {
        if (isReading) {
            long duration = System.currentTimeMillis() - startTime;
            addDuration(duration);
            isReading = false;
            startTime = 0;
        }
    }

    /**
     * 翻页时调用
     */
    public void onPageTurn() {
        // 增加今日和总页数
        int todayPages = prefs.getInt(KEY_TODAY_PAGES, 0);
        int totalPages = prefs.getInt(KEY_TOTAL_PAGES, 0);

        prefs.edit()
            .putInt(KEY_TODAY_PAGES, todayPages + 1)
            .putInt(KEY_TOTAL_PAGES, totalPages + 1)
            .apply();
    }

    /**
     * 获取今日统计数据
     * @return 包含今日阅读时长(分钟)、阅读页数、阅读次数的数组
     */
    public int[] getTodayStats() {
        checkAndResetDailyStats();
        int duration = prefs.getInt(KEY_TODAY_DURATION, 0) / 60000; // 转换为分钟
        int pages = prefs.getInt(KEY_TODAY_PAGES, 0);
        return new int[]{duration, pages};
    }

    /**
     * 获取总计统计数据
     * @return 包含总阅读时长(分钟)、总阅读页数、总阅读次数的数组
     */
    public int[] getTotalStats() {
        int duration = prefs.getInt(KEY_TOTAL_DURATION, 0) / 60000; // 转换为分钟
        int pages = prefs.getInt(KEY_TOTAL_PAGES, 0);
        int count = prefs.getInt(KEY_READ_COUNT, 0);
        return new int[]{duration, pages, count};
    }

    /**
     * 检查并重置今日统计数据（如果是新的一天）
     */
    private void checkAndResetDailyStats() {
        String today = getDateString();
        String lastDate = prefs.getString(KEY_LAST_DATE, "");

        if (!today.equals(lastDate)) {
            // 新的一天，重置今日统计
            prefs.edit()
                .putInt(KEY_TODAY_DURATION, 0)
                .putInt(KEY_TODAY_PAGES, 0)
                .putString(KEY_LAST_DATE, today)
                .apply();
        }
    }

    /**
     * 增加阅读时长
     */
    private void addDuration(long duration) {
        int todayDuration = prefs.getInt(KEY_TODAY_DURATION, 0);
        int totalDuration = prefs.getInt(KEY_TOTAL_DURATION, 0);

        prefs.edit()
            .putInt(KEY_TODAY_DURATION, todayDuration + (int)duration)
            .putInt(KEY_TOTAL_DURATION, totalDuration + (int)duration)
            .apply();
    }

    /**
     * 获取当前日期字符串
     */
    private String getDateString() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        return sdf.format(new Date());
    }

    /**
     * 格式化时长显示
     * @param minutes 分钟数
     * @return 格式化的字符串
     */
    public static String formatDuration(int minutes) {
        if (minutes < 60) {
            return minutes + "分钟";
        } else {
            int hours = minutes / 60;
            int mins = minutes % 60;
            if (mins == 0) {
                return hours + "小时";
            } else {
                return hours + "小时" + mins + "分钟";
            }
        }
    }
}