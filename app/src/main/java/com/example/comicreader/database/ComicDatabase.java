package com.example.comicreader.database;

import android.content.Context;
import android.content.SharedPreferences;
import com.example.comicreader.model.AppSettings;
import com.example.comicreader.model.ComicBook;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class ComicDatabase {
    private static final String PREF_NAME = "comic_reader_prefs";
    private static final String KEY_COMICS = "comics";
    private static final String KEY_SETTINGS = "settings";

    private SharedPreferences sharedPreferences;
    private Gson gson;

    public ComicDatabase(Context context) {
        sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        gson = new Gson();
    }

    public void saveComics(List<ComicBook> comics) {
        String json = gson.toJson(comics);
        sharedPreferences.edit().putString(KEY_COMICS, json).apply();
    }

    public List<ComicBook> getComics() {
        String json = sharedPreferences.getString(KEY_COMICS, null);
        if (json != null) {
            Type type = new TypeToken<List<ComicBook>>() {}.getType();
            return gson.fromJson(json, type);
        }
        return new ArrayList<>();
    }

    public void addComic(ComicBook comic) {
        List<ComicBook> comics = getComics();
        comics.add(comic);
        saveComics(comics);
    }

    public void updateComic(ComicBook updatedComic) {
        List<ComicBook> comics = getComics();
        for (int i = 0; i < comics.size(); i++) {
            if (comics.get(i).getId().equals(updatedComic.getId())) {
                comics.set(i, updatedComic);
                break;
            }
        }
        saveComics(comics);
    }

    public void deleteComic(String comicId) {
        List<ComicBook> comics = getComics();
        for (int i = comics.size() - 1; i >= 0; i--) {
            if (comics.get(i).getId().equals(comicId)) {
                comics.remove(i);
                break;
            }
        }
        saveComics(comics);
    }

    public void deleteMultipleComics(List<String> comicIds) {
        if (comicIds == null || comicIds.isEmpty()) {
            return;
        }
        List<ComicBook> comics = getComics();
        for (int i = comics.size() - 1; i >= 0; i--) {
            if (comicIds.contains(comics.get(i).getId())) {
                comics.remove(i);
            }
        }
        saveComics(comics);
    }

    public ComicBook getComicById(String comicId) {
        List<ComicBook> comics = getComics();
        for (ComicBook comic : comics) {
            if (comic.getId().equals(comicId)) {
                return comic;
            }
        }
        return null;
    }

    public void saveSettings(AppSettings settings) {
        String json = gson.toJson(settings);
        sharedPreferences.edit().putString(KEY_SETTINGS, json).apply();
    }

    public AppSettings getSettings() {
        String json = sharedPreferences.getString(KEY_SETTINGS, null);
        if (json != null) {
            return gson.fromJson(json, AppSettings.class);
        }
        return new AppSettings();
    }
}