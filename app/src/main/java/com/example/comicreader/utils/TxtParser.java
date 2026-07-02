package com.example.comicreader.utils;

import android.content.Context;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

public class TxtParser {
    private File file;
    private Context context;
    private List<String> chapters;
    private int totalPages;

    public TxtParser(File file, Context context) {
        this.file = file;
        this.context = context;
        this.chapters = new ArrayList<>();
    }

    public void parse() throws Exception {
        chapters.clear();
        
        int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
        int charsPerLine = screenWidth / 16;
        int linesPerPage = 40;
        int charsPerPage = charsPerLine * linesPerPage;

        StringBuilder content = new StringBuilder();
        BufferedReader reader = null;
        
        try {
            reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), Charset.forName("GBK")));
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
        } catch (Exception e) {
            try {
                if (reader != null) reader.close();
                reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), Charset.forName("UTF-8")));
                String line;
                content = new StringBuilder();
                while ((line = reader.readLine()) != null) {
                    content.append(line).append("\n");
                }
            } catch (Exception e2) {
                if (reader != null) reader.close();
                reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), Charset.forName("GB2312")));
                content = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    content.append(line).append("\n");
                }
            }
        } finally {
            if (reader != null) reader.close();
        }

        String text = content.toString();
        int totalChars = text.length();
        
        for (int i = 0; i < totalChars; i += charsPerPage) {
            int end = Math.min(i + charsPerPage, totalChars);
            String pageContent = text.substring(i, end);
            chapters.add(pageContent);
        }
        
        totalPages = chapters.size();
    }

    public String getChapterContent(int chapterIndex) {
        if (chapterIndex >= 0 && chapterIndex < chapters.size()) {
            return formatContent(chapters.get(chapterIndex));
        }
        return "";
    }

    private String formatContent(String content) {
        return "<!DOCTYPE html><html><head><meta charset='UTF-8'><style>" +
               "body { background-color: transparent; color: inherit; padding: 24px; " +
               "font-family: 'SimHei', 'Microsoft YaHei', sans-serif; font-size: 18px; line-height: 1.8; }" +
               "</style></head><body>" + content.replace("\n", "<br/>") + "</body></html>";
    }

    public String getChapterContent(int chapterIndex, String theme) {
        if (chapterIndex >= 0 && chapterIndex < chapters.size()) {
            return formatContent(chapters.get(chapterIndex), theme);
        }
        return "";
    }

    private String formatContent(String content, String theme) {
        String bgColor, textColor;
        
        switch (theme) {
            case "eye":
                bgColor = "#f0e6d2";
                textColor = "#5c4033";
                break;
            case "light":
                bgColor = "#ffffff";
                textColor = "#333333";
                break;
            default:
                bgColor = "#1a1a2e";
                textColor = "#e0e0e0";
                break;
        }
        
        return "<!DOCTYPE html><html><head><meta charset='UTF-8'><style>" +
               "body { background-color: " + bgColor + "; color: " + textColor + "; padding: 24px; " +
               "font-family: 'SimHei', 'Microsoft YaHei', sans-serif; font-size: 18px; line-height: 1.8; }" +
               "</style></head><body>" + content.replace("\n", "<br/>") + "</body></html>";
    }

    public int getTotalPages() {
        return totalPages;
    }

    public List<String> getChapters() {
        return chapters;
    }
}
