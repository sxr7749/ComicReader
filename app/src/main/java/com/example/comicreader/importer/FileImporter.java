package com.example.comicreader.importer;

import android.content.Context;
import android.net.Uri;
import android.webkit.MimeTypeMap;
import com.example.comicreader.model.ComicBook;
import com.example.comicreader.utils.EpubParser;
import com.example.comicreader.utils.FileUtils;
import com.example.comicreader.utils.MobiParser;
import com.example.comicreader.utils.PdfRendererManager;
import com.example.comicreader.utils.TxtParser;
import java.io.File;
import java.io.IOException;
import java.util.List;

public class FileImporter {
    private Context context;

    public FileImporter(Context context) {
        this.context = context;
    }

    public ComicBook importFile(Uri uri) throws IOException {
        String fileName = getFileNameFromUri(uri);
        String extension = FileUtils.getFileExtension(fileName);

        if (extension.isEmpty()) {
            extension = getExtensionFromMimeType(uri);
        }

        if (extension.isEmpty()) {
            String path = uri.getPath();
            if (path != null && path.contains(".")) {
                extension = path.substring(path.lastIndexOf('.')).toLowerCase();
            }
        }

        if (extension.isEmpty()) {
            throw new IOException("无法识别文件格式");
        }

        switch (extension) {
            case ".pdf":
            case ".PDF":
                return importPdf(uri, fileName);
            case ".zip":
            case ".ZIP":
                return importZip(uri, fileName);
            case ".epub":
            case ".EPUB":
                return importEpub(uri, fileName);
            case ".mobi":
            case ".MOBI":
                return importMobi(uri, fileName);
            case ".txt":
            case ".TXT":
                return importTxt(uri, fileName);
            case ".jpg":
            case ".jpeg":
            case ".png":
            case ".gif":
            case ".bmp":
            case ".webp":
            case ".JPG":
            case ".JPEG":
            case ".PNG":
            case ".GIF":
            case ".BMP":
            case ".WEBP":
                return importImage(uri, fileName);
            default:
                throw new IOException("暂不支持的文件格式: " + extension);
        }
    }

    private String getExtensionFromMimeType(Uri uri) {
        String mimeType = context.getContentResolver().getType(uri);
        if (mimeType != null) {
            String ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);
            if (ext != null) {
                return "." + ext.toLowerCase();
            }
        }
        return "";
    }

    private ComicBook importPdf(Uri uri, String fileName) throws IOException {
        String id = FileUtils.generateId();
        File destFile = FileUtils.copyFileToAppDir(context, uri, id + ".pdf");

        if (!destFile.exists() || destFile.length() == 0) {
            throw new IOException("文件复制失败");
        }

        PdfRendererManager manager = PdfRendererManager.getInstance(destFile, context);
        int totalPages = manager.getPageCount();
        
        if (totalPages <= 0) {
            throw new IOException("无法读取PDF页数");
        }

        String coverPath = manager.extractCover(0, 600, context);

        ComicBook comic = new ComicBook(id, FileUtils.getFileNameWithoutExtension(fileName),
                coverPath, destFile.getAbsolutePath(), "pdf", totalPages);
        comic.setFileSize(destFile.length());
        return comic;
    }

    private ComicBook importZip(Uri uri, String fileName) throws IOException {
        String id = FileUtils.generateId();
        File destFile = FileUtils.copyFileToAppDir(context, uri, id + ".zip");

        File extractDir = new File(FileUtils.getComicDirectory(context), id);
        ZipExtractor.extract(destFile, extractDir);

        List<File> imageFiles = FileUtils.getImageFilesFromDirectory(extractDir);
        int totalPages = imageFiles.size();

        if (totalPages == 0) {
            throw new IOException("ZIP包中未找到图片文件");
        }

        String coverPath = imageFiles.get(0).getAbsolutePath();

        ComicBook comic = new ComicBook(id, FileUtils.getFileNameWithoutExtension(fileName),
                coverPath, extractDir.getAbsolutePath(), "folder", totalPages);
        comic.setFileSize(destFile.length());
        return comic;
    }

    private ComicBook importImage(Uri uri, String fileName) throws IOException {
        String id = FileUtils.generateId();
        String ext = FileUtils.getFileExtension(fileName);
        if (ext.isEmpty()) {
            ext = ".jpg";
        }
        File destFile = FileUtils.copyFileToAppDir(context, uri, id + ext);

        ComicBook comic = new ComicBook(id, FileUtils.getFileNameWithoutExtension(fileName),
                destFile.getAbsolutePath(), destFile.getAbsolutePath(), "image", 1);
        comic.setFileSize(destFile.length());
        return comic;
    }

    private ComicBook importEpub(Uri uri, String fileName) throws IOException {
        String id = FileUtils.generateId();
        File destFile = FileUtils.copyFileToAppDir(context, uri, id + ".epub");

        if (!destFile.exists() || destFile.length() == 0) {
            throw new IOException("文件复制失败");
        }

        EpubParser parser = new EpubParser(destFile, context);
        EpubParser.EpubInfo info = parser.parse();

        String coverPath = info.coverPath;
        if (coverPath == null) {
            coverPath = destFile.getAbsolutePath();
        }

        ComicBook comic = new ComicBook(id, 
                info.title.isEmpty() ? FileUtils.getFileNameWithoutExtension(fileName) : info.title,
                coverPath, destFile.getAbsolutePath(), "epub", info.totalPages);
        comic.setFileSize(destFile.length());
        return comic;
    }

    private ComicBook importMobi(Uri uri, String fileName) throws IOException {
        String id = FileUtils.generateId();
        File destFile = FileUtils.copyFileToAppDir(context, uri, id + ".mobi");

        if (!destFile.exists() || destFile.length() == 0) {
            throw new IOException("文件复制失败");
        }

        MobiParser parser = new MobiParser(destFile, context);
        MobiParser.MobiInfo info = parser.parse();

        String coverPath = info.coverPath;
        if (coverPath == null) {
            coverPath = destFile.getAbsolutePath();
        }

        ComicBook comic = new ComicBook(id,
                info.title.isEmpty() ? FileUtils.getFileNameWithoutExtension(fileName) : info.title,
                coverPath, destFile.getAbsolutePath(), "mobi", info.totalPages);
        comic.setFileSize(destFile.length());
        return comic;
    }

    private ComicBook importTxt(Uri uri, String fileName) throws IOException {
        String id = FileUtils.generateId();
        File destFile = FileUtils.copyFileToAppDir(context, uri, id + ".txt");

        if (!destFile.exists() || destFile.length() == 0) {
            throw new IOException("文件复制失败");
        }

        TxtParser parser = new TxtParser(destFile, context);
        try {
            parser.parse();
        } catch (Exception e) {
            throw new IOException("解析TXT文件失败", e);
        }
        int totalPages = parser.getTotalPages();

        if (totalPages <= 0) {
            throw new IOException("无法读取TXT内容");
        }

        ComicBook comic = new ComicBook(id, 
                FileUtils.getFileNameWithoutExtension(fileName),
                null, destFile.getAbsolutePath(), "txt", totalPages);
        comic.setFileSize(destFile.length());
        return comic;
    }

    private String getFileNameFromUri(Uri uri) {
        String fileName = uri.getLastPathSegment();
        
        if (fileName != null && fileName.contains("/")) {
            fileName = fileName.substring(fileName.lastIndexOf('/') + 1);
        }

        if (fileName == null || fileName.isEmpty() || !fileName.contains(".")) {
            try {
                android.database.Cursor cursor = context.getContentResolver().query(uri, null, null, null, null);
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                    if (nameIndex != -1) {
                        fileName = cursor.getString(nameIndex);
                    }
                    cursor.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        if (fileName == null || fileName.isEmpty()) {
            fileName = "unknown";
        }
        return fileName;
    }
}
