package com.example.comicreader.viewmodel;

import android.app.Application;
import android.net.Uri;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.example.comicreader.database.ComicDatabase;
import com.example.comicreader.importer.FileImporter;
import com.example.comicreader.model.AppSettings;
import com.example.comicreader.model.ComicBook;
import java.io.IOException;
import java.util.List;

public class ComicViewModel extends AndroidViewModel {
    private ComicDatabase database;
    private MutableLiveData<List<ComicBook>> comics;
    private MutableLiveData<Boolean> isLoading;
    private MutableLiveData<String> message;

    public ComicViewModel(Application application) {
        super(application);
        database = new ComicDatabase(application);
        comics = new MutableLiveData<>();
        isLoading = new MutableLiveData<>(false);
        message = new MutableLiveData<>();
        loadComics();
    }

    public LiveData<List<ComicBook>> getComics() {
        return comics;
    }

    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }

    public LiveData<String> getMessage() {
        return message;
    }

    public void loadComics() {
        List<ComicBook> comicList = database.getComics();
        comics.postValue(comicList);
    }

    public void importComic(Uri uri) {
        isLoading.postValue(true);

        new Thread(() -> {
            try {
                FileImporter importer = new FileImporter(getApplication());
                ComicBook comic = importer.importFile(uri);
                database.addComic(comic);
                loadComics();
                message.postValue("导入成功");
            } catch (IOException e) {
                message.postValue("导入失败: " + e.getMessage());
            } finally {
                isLoading.postValue(false);
            }
        }).start();
    }

    public void deleteComic(String comicId) {
        database.deleteComic(comicId);
        loadComics();
    }

    public void deleteMultipleComics(List<String> comicIds) {
        database.deleteMultipleComics(comicIds);
        loadComics();
    }

    public void updateComic(ComicBook comic) {
        database.updateComic(comic);
        loadComics();
    }

    public ComicBook getComicById(String comicId) {
        return database.getComicById(comicId);
    }

    public AppSettings getSettings() {
        return database.getSettings();
    }

    public void saveSettings(AppSettings settings) {
        database.saveSettings(settings);
    }

    public void setMessage(String msg) {
        message.postValue(msg);
    }
}