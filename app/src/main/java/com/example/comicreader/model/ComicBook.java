package com.example.comicreader.model;

import java.io.Serializable;
import java.util.Date;

public class ComicBook implements Serializable {
    private String id;
    private String title;
    private String coverPath;
    private String filePath;
    private String fileType;
    private int totalPages;
    private int currentPage;
    private boolean isFavorite;
    private Date lastReadTime;
    private Date addedTime;
    private String category;
    private String genre;
    private String customTag;
    private long fileSize;

    public ComicBook() {
    }

    public ComicBook(String id, String title, String coverPath, String filePath, String fileType, int totalPages) {
        this.id = id;
        this.title = title;
        this.coverPath = coverPath;
        this.filePath = filePath;
        this.fileType = fileType;
        this.totalPages = totalPages;
        this.currentPage = 0;
        this.isFavorite = false;
        this.lastReadTime = new Date();
        this.addedTime = new Date();
        this.category = "其他";
        this.genre = "未分类";
        this.fileSize = new java.io.File(filePath).length();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getCoverPath() {
        return coverPath;
    }

    public void setCoverPath(String coverPath) {
        this.coverPath = coverPath;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getFileType() {
        return fileType;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public void setTotalPages(int totalPages) {
        this.totalPages = totalPages;
    }

    public int getCurrentPage() {
        return currentPage;
    }

    public void setCurrentPage(int currentPage) {
        this.currentPage = currentPage;
    }

    public boolean isFavorite() {
        return isFavorite;
    }

    public void setFavorite(boolean favorite) {
        isFavorite = favorite;
    }

    public Date getLastReadTime() {
        return lastReadTime;
    }

    public void setLastReadTime(Date lastReadTime) {
        this.lastReadTime = lastReadTime;
    }

    public Date getAddedTime() {
        return addedTime;
    }

    public void setAddedTime(Date addedTime) {
        this.addedTime = addedTime;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getGenre() {
        return genre;
    }

    public void setGenre(String genre) {
        this.genre = genre;
    }

    public String getCustomTag() {
        return customTag;
    }

    public void setCustomTag(String customTag) {
        this.customTag = customTag;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public float getProgress() {
        if (totalPages <= 0) return 0;
        float progress = (float) currentPage / totalPages * 100;
        return Math.min(progress, 100f);
    }
}
