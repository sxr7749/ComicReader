package com.example.comicreader;

import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.os.LocaleList;
import android.os.Handler;
import android.os.Looper;
import com.example.comicreader.database.ComicDatabase;
import com.example.comicreader.importer.PrefetchedComicImporter;
import com.example.comicreader.model.AppSettings;
import java.util.Locale;

public class ComicApplication extends android.app.Application {
    private ComicDatabase database;

    @Override
    public void onCreate() {
        super.onCreate();
        database = new ComicDatabase(this);
        applySavedLanguage();
        
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                PrefetchedComicImporter importer = new PrefetchedComicImporter(ComicApplication.this);
                importer.importPrefetchedComics();
            }
        }, 1000);
    }

    private void applySavedLanguage() {
        AppSettings settings = database.getSettings();
        if (settings != null && settings.getLanguage() != null) {
            applyLanguage(settings.getLanguage());
        }
    }

    public void applyLanguage(String language) {
        Resources resources = getResources();
        Configuration configuration = resources.getConfiguration();
        Locale locale;

        switch (language) {
            case AppSettings.LANGUAGE_CN:
                locale = Locale.SIMPLIFIED_CHINESE;
                break;
            case AppSettings.LANGUAGE_EN:
                locale = Locale.ENGLISH;
                break;
            default:
                locale = Resources.getSystem().getConfiguration().getLocales().get(0);
                break;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            configuration.setLocales(new LocaleList(locale));
        } else {
            configuration.locale = locale;
        }

        resources.updateConfiguration(configuration, resources.getDisplayMetrics());
    }

    public ComicDatabase getDatabase() {
        return database;
    }
}