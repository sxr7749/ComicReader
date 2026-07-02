package com.example.comicreader.model;

public class AppSettings {
    public static final String THEME_DARK = "dark";
    public static final String THEME_LIGHT = "light";
    public static final String THEME_SYSTEM = "system";
    public static final String THEME_EYE = "eye";

    public static final String READ_MODE_VERTICAL = "vertical";
    public static final String READ_MODE_LTR = "ltr";
    public static final String READ_MODE_RTL = "rtl";

    public static final String MODE_SLIDE = "slide";
    public static final String MODE_COVER = "cover";
    public static final String MODE_NONE = "none";

    public static final String LANGUAGE_CN = "zh";
    public static final String LANGUAGE_EN = "en";
    public static final String LANGUAGE_SYSTEM = "system";

    private String theme;
    private String readMode;
    private String pageMode;
    private String language;
    private boolean autoRotate;
    private float zoomLevel = 1.0f;

    public AppSettings() {
        this.theme = THEME_SYSTEM;
        this.readMode = READ_MODE_VERTICAL;
        this.pageMode = MODE_SLIDE;
        this.language = LANGUAGE_SYSTEM;
        this.autoRotate = false;
    }

    public String getTheme() {
        return theme;
    }

    public void setTheme(String theme) {
        this.theme = theme;
    }

    public String getReadMode() {
        return readMode;
    }

    public void setReadMode(String readMode) {
        this.readMode = readMode;
    }

    public String getPageMode() {
        return pageMode;
    }

    public void setPageMode(String pageMode) {
        this.pageMode = pageMode;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public boolean isAutoRotate() {
        return autoRotate;
    }

    public void setAutoRotate(boolean autoRotate) {
        this.autoRotate = autoRotate;
    }

    public float getZoomLevel() {
        return zoomLevel;
    }

    public void setZoomLevel(float zoomLevel) {
        this.zoomLevel = zoomLevel;
    }
}
