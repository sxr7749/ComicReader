package com.example.comicreader.model;

public class PageImage {
    private int pageIndex;
    private String imagePath;
    private long fileSize;

    public PageImage() {
    }

    public PageImage(int pageIndex, String imagePath, long fileSize) {
        this.pageIndex = pageIndex;
        this.imagePath = imagePath;
        this.fileSize = fileSize;
    }

    public int getPageIndex() {
        return pageIndex;
    }

    public void setPageIndex(int pageIndex) {
        this.pageIndex = pageIndex;
    }

    public String getImagePath() {
        return imagePath;
    }

    public void setImagePath(String imagePath) {
        this.imagePath = imagePath;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }
}