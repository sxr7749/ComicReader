package com.example.comicreader.importer;

import android.content.Context;
import android.util.Log;
import com.example.comicreader.database.ComicDatabase;
import com.example.comicreader.model.ComicBook;
import com.example.comicreader.utils.EpubParser;
import com.example.comicreader.utils.FileUtils;
import com.example.comicreader.utils.MobiParser;
import com.example.comicreader.utils.PdfRendererManager;
import com.example.comicreader.utils.TxtParser;
import java.io.File;
import java.io.IOException;

public class PrefetchedComicImporter {
    private static final String PREF_KEY_IMPORTED = "prefetched_comics_imported";
    private Context context;
    private ComicDatabase database;

    public PrefetchedComicImporter(Context context) {
        this.context = context;
        this.database = new ComicDatabase(context);
    }

    public void importPrefetchedComics() {
        if (hasImported() && database.getComics().size() > 0) {
            Log.d("PrefetchedImporter", "已导入过预置漫画且数据库已有漫画，跳过");
            return;
        }

        if (hasImported()) {
            Log.d("PrefetchedImporter", "已标记导入但数据库为空，重新尝试导入");
        }
        
        Log.d("PrefetchedImporter", "开始导入预置漫画...");
        
        new Thread(new Runnable() {
            @Override
            public void run() {
                performImport();
            }
        }).start();
    }

    private void performImport() {
        try {
            String[] files = context.getAssets().list("");
            Log.d("PrefetchedImporter", "assets目录文件列表: " + (files != null ? java.util.Arrays.toString(files) : "null"));
            
            if (files != null && files.length > 0) {
                for (String fileName : files) {
                    Log.d("PrefetchedImporter", "检查文件: " + fileName);
                    if (isSupportedFile(fileName)) {
                        try {
                            importComicFromAssets(fileName);
                        } catch (Exception e) {
                            Log.e("PrefetchedImporter", "导入预置漫画失败: " + fileName, e);
                        }
                    } else {
                        Log.d("PrefetchedImporter", "跳过不支持的文件: " + fileName);
                    }
                }
            } else {
                Log.d("PrefetchedImporter", "assets目录为空或无法读取");
            }
        } catch (IOException e) {
            Log.e("PrefetchedImporter", "读取assets目录失败", e);
        }

        markAsImported();
        Log.d("PrefetchedImporter", "预置漫画导入完成");
    }

    private boolean hasImported() {
        return context.getSharedPreferences("comic_reader_prefs", Context.MODE_PRIVATE)
                .getBoolean(PREF_KEY_IMPORTED, false);
    }

    private void markAsImported() {
        context.getSharedPreferences("comic_reader_prefs", Context.MODE_PRIVATE)
                .edit()
                .putBoolean(PREF_KEY_IMPORTED, true)
                .apply();
    }

    private boolean isSupportedFile(String fileName) {
        String lower = fileName.toLowerCase();
        return lower.endsWith(".pdf") || lower.endsWith(".epub") ||
               lower.endsWith(".mobi") || lower.endsWith(".txt") ||
               lower.endsWith(".zip") || lower.endsWith(".jpg") ||
               lower.endsWith(".jpeg") || lower.endsWith(".png");
    }

    private void importComicFromAssets(String fileName) throws IOException {
        String extension = FileUtils.getFileExtension(fileName);
        String id = FileUtils.generateId();
        String destFileName = id + extension;
        File destFile = FileUtils.copyAssetToAppDir(context, fileName, destFileName);

        if (!destFile.exists() || destFile.length() == 0) {
            throw new IOException("文件复制失败");
        }

        ComicBook comic = null;
        String title = FileUtils.getFileNameWithoutExtension(fileName);

        switch (extension.toLowerCase()) {
            case ".pdf":
                comic = importPdf(destFile, id, title);
                break;
            case ".epub":
                comic = importEpub(destFile, id, title);
                break;
            case ".mobi":
                comic = importMobi(destFile, id, title);
                break;
            case ".txt":
                comic = importTxt(destFile, id, title);
                break;
            case ".jpg":
            case ".jpeg":
            case ".png":
                comic = importImage(destFile, id, title);
                break;
            case ".zip":
                comic = importZip(destFile, id, title);
                break;
        }

        if (comic != null) {
            database.addComic(comic);
            Log.d("PrefetchedImporter", "成功导入预置漫画: " + title);
        }
    }

    private ComicBook importPdf(File file, String id, String title) throws IOException {
        Log.d("PrefetchedImporter", "开始导入PDF: " + title);
        
        int totalPages = 0;
        String coverPath = null;
        
        try {
            PdfRendererManager manager = PdfRendererManager.getInstance(file, context);
            totalPages = manager.getPageCount();
            Log.d("PrefetchedImporter", "PDF页数: " + totalPages);
            
            if (totalPages <= 0) {
                throw new IOException("无法读取PDF页数");
            }

            coverPath = manager.extractCover(0, 600, context);
            Log.d("PrefetchedImporter", "封面路径: " + coverPath);
        } catch (Exception e) {
            Log.e("PrefetchedImporter", "解析PDF失败，使用默认封面", e);
            coverPath = file.getAbsolutePath();
            totalPages = 1;
        }

        ComicBook comic = new ComicBook(id, title, coverPath, file.getAbsolutePath(), "pdf", totalPages);
        comic.setFileSize(file.length());
        Log.d("PrefetchedImporter", "创建ComicBook成功: " + title);
        return comic;
    }

    private ComicBook importEpub(File file, String id, String title) throws IOException {
        EpubParser parser = new EpubParser(file, context);
        EpubParser.EpubInfo info = parser.parse();

        String coverPath = info.coverPath;
        if (coverPath == null) {
            coverPath = file.getAbsolutePath();
        }

        String comicTitle = info.title.isEmpty() ? title : info.title;
        ComicBook comic = new ComicBook(id, comicTitle, coverPath, file.getAbsolutePath(), "epub", info.totalPages);
        comic.setFileSize(file.length());
        return comic;
    }

    private ComicBook importMobi(File file, String id, String title) throws IOException {
        MobiParser parser = new MobiParser(file, context);
        MobiParser.MobiInfo info = parser.parse();

        String coverPath = info.coverPath;
        if (coverPath == null) {
            coverPath = file.getAbsolutePath();
        }

        String comicTitle = info.title.isEmpty() ? title : info.title;
        ComicBook comic = new ComicBook(id, comicTitle, coverPath, file.getAbsolutePath(), "mobi", info.totalPages);
        comic.setFileSize(file.length());
        return comic;
    }

    private ComicBook importTxt(File file, String id, String title) throws IOException {
        TxtParser parser = new TxtParser(file, context);
        try {
            parser.parse();
        } catch (Exception e) {
            throw new IOException("解析TXT文件失败", e);
        }
        int totalPages = parser.getTotalPages();

        if (totalPages <= 0) {
            throw new IOException("无法读取TXT内容");
        }

        ComicBook comic = new ComicBook(id, title, null, file.getAbsolutePath(), "txt", totalPages);
        comic.setFileSize(file.length());
        return comic;
    }

    private ComicBook importImage(File file, String id, String title) {
        ComicBook comic = new ComicBook(id, title, file.getAbsolutePath(), file.getAbsolutePath(), "image", 1);
        comic.setFileSize(file.length());
        return comic;
    }

    private ComicBook importZip(File file, String id, String title) throws IOException {
        File extractDir = new File(FileUtils.getComicDirectory(context), id);
        ZipExtractor.extract(file, extractDir);

        java.util.List<File> imageFiles = FileUtils.getImageFilesFromDirectory(extractDir);
        int totalPages = imageFiles.size();

        if (totalPages == 0) {
            throw new IOException("ZIP包中未找到图片文件");
        }

        String coverPath = imageFiles.get(0).getAbsolutePath();

        ComicBook comic = new ComicBook(id, title, coverPath, extractDir.getAbsolutePath(), "folder", totalPages);
        comic.setFileSize(file.length());
        return comic;
    }
}