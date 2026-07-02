package com.example.comicreader.utils;

import android.content.Context;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;
import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class EpubParser {
    private File epubFile;
    private Context context;
    private List<String> chapterPaths = new ArrayList<>();
    private List<String> imagePaths = new ArrayList<>();
    private String extractionDir;
    private boolean parsed = false;

    public EpubParser(File epubFile, Context context) {
        this.epubFile = epubFile;
        this.context = context;
    }

    public EpubInfo parse() throws IOException {
        EpubInfo info = new EpubInfo();

        if (parsed) {
            if (!imagePaths.isEmpty()) {
                info.totalPages = imagePaths.size();
            } else if (!chapterPaths.isEmpty()) {
                info.totalPages = chapterPaths.size();
            } else {
                info.totalPages = 1;
            }
            return info;
        }

        try {
            extractionDir = extractEpubToCache();

            String opfPath = findOpfPath();
            if (opfPath != null) {
                parseOpfFile(new File(extractionDir, opfPath), info);
            }

            if (chapterPaths.isEmpty()) {
                findHtmlFiles(new File(extractionDir));
            }

            extractImagesFromChapters();

            if (!imagePaths.isEmpty()) {
                info.totalPages = imagePaths.size();
            } else if (!chapterPaths.isEmpty()) {
                info.totalPages = chapterPaths.size();
            } else {
                info.totalPages = 1;
            }

            if (info.coverPath == null && !imagePaths.isEmpty()) {
                info.coverPath = imagePaths.get(0);
            }

            parsed = true;

        } catch (XmlPullParserException e) {
            throw new IOException("EPUB解析错误", e);
        }

        return info;
    }

    private String extractEpubToCache() throws IOException {
        String dirName = "epub_" + System.currentTimeMillis();
        File extractDir = new File(FileUtils.getCacheDirectory(context), dirName);
        if (!extractDir.exists()) {
            extractDir.mkdirs();
        }

        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(epubFile))) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zis.getNextEntry()) != null) {
                File entryFile = new File(extractDir, entry.getName());
                if (entry.isDirectory()) {
                    entryFile.mkdirs();
                } else {
                    entryFile.getParentFile().mkdirs();
                    try (FileOutputStream fos = new FileOutputStream(entryFile)) {
                        int bytesRead;
                        while ((bytesRead = zis.read(buffer)) != -1) {
                            fos.write(buffer, 0, bytesRead);
                        }
                    }
                }
                zis.closeEntry();
            }
        }

        return extractDir.getAbsolutePath();
    }

    private String findOpfPath() throws IOException {
        File metaDir = new File(extractionDir, "META-INF");
        if (metaDir.exists() && metaDir.isDirectory()) {
            File containerFile = new File(metaDir, "container.xml");
            if (containerFile.exists()) {
                try {
                    return parseContainerXml(containerFile);
                } catch (XmlPullParserException e) {
                    e.printStackTrace();
                }
            }
        }

        File rootDir = new File(extractionDir);
        File[] files = rootDir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.getName().toLowerCase().endsWith(".opf")) {
                    return file.getName();
                }
                if (file.isDirectory()) {
                    File[] subFiles = file.listFiles();
                    if (subFiles != null) {
                        for (File subFile : subFiles) {
                            if (subFile.getName().toLowerCase().endsWith(".opf")) {
                                return file.getName() + "/" + subFile.getName();
                            }
                        }
                    }
                }
            }
        }

        return null;
    }

    private String parseContainerXml(File containerFile) throws XmlPullParserException, IOException {
        XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
        XmlPullParser parser = factory.newPullParser();
        parser.setInput(new FileReader(containerFile));

        int eventType = parser.getEventType();
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG) {
                String name = parser.getName();
                if ("rootfile".equals(name)) {
                    String path = parser.getAttributeValue(null, "full-path");
                    if (path != null && !path.isEmpty()) {
                        return path;
                    }
                }
            }
            eventType = parser.next();
        }
        return null;
    }

    private void parseOpfFile(File opfFile, EpubInfo info) throws IOException, XmlPullParserException {
        XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
        XmlPullParser parser = factory.newPullParser();
        parser.setInput(new FileReader(opfFile));

        String opfDir = opfFile.getParent();
        if (opfDir == null) opfDir = extractionDir;

        Map<String, String> manifest = new HashMap<>();
        Map<String, String> manifestMediaType = new HashMap<>();

        int eventType = parser.getEventType();
        while (eventType != XmlPullParser.END_DOCUMENT) {
            switch (eventType) {
                case XmlPullParser.START_TAG:
                    String tag = parser.getName().toLowerCase();
                    if (tag.equals("metadata")) {
                        parseMetadata(parser, info);
                    } else if (tag.equals("manifest")) {
                        parseManifest(parser, manifest, manifestMediaType);
                    } else if (tag.equals("spine")) {
                        parseSpine(parser, manifest, opfDir);
                    }
                    break;
            }
            eventType = parser.next();
        }

        extractImagesFromManifest(manifest, manifestMediaType, opfDir);
    }

    private void extractImagesFromManifest(Map<String, String> manifest, Map<String, String> manifestMediaType, String opfDir) {
        for (Map.Entry<String, String> entry : manifest.entrySet()) {
            String id = entry.getKey();
            String href = entry.getValue();
            String mediaType = manifestMediaType.get(id);

            File imgFile = new File(opfDir, href);
            if (!imgFile.exists()) {
                imgFile = new File(extractionDir, href);
            }
            if (!imgFile.exists()) {
                if (href.startsWith("/")) {
                    imgFile = new File(extractionDir, href.substring(1));
                }
            }

            if (imgFile.exists()) {
                boolean isImage = false;
                if (mediaType != null && mediaType.startsWith("image/")) {
                    isImage = true;
                } else {
                    isImage = isImageFile(imgFile.getName()) || isImageFileByMagic(imgFile);
                }

                if (isImage) {
                    String imgPath = imgFile.getAbsolutePath();
                    if (!imagePaths.contains(imgPath)) {
                        imagePaths.add(imgPath);
                    }
                }
            }
        }
    }

    private void parseMetadata(XmlPullParser parser, EpubInfo info) throws XmlPullParserException, IOException {
        int eventType = parser.next();
        while (!(eventType == XmlPullParser.END_TAG && "metadata".equalsIgnoreCase(parser.getName()))) {
            if (eventType == XmlPullParser.START_TAG) {
                String tag = parser.getName().toLowerCase();
                if (tag.contains("title") || tag.endsWith(":title")) {
                    parser.next();
                    if (parser.getEventType() == XmlPullParser.TEXT) {
                        String text = parser.getText();
                        if (text != null && !text.trim().isEmpty()) {
                            info.title = text.trim();
                        }
                    }
                } else if (tag.contains("creator") || tag.endsWith(":creator") || tag.contains("author")) {
                    parser.next();
                    if (parser.getEventType() == XmlPullParser.TEXT) {
                        String text = parser.getText();
                        if (text != null && !text.trim().isEmpty()) {
                            info.author = text.trim();
                        }
                    }
                }
            }
            eventType = parser.next();
        }
    }

    private void parseManifest(XmlPullParser parser, Map<String, String> manifest, Map<String, String> manifestMediaType) throws XmlPullParserException, IOException {
        int eventType = parser.next();
        while (!(eventType == XmlPullParser.END_TAG && "manifest".equalsIgnoreCase(parser.getName()))) {
            if (eventType == XmlPullParser.START_TAG && "item".equalsIgnoreCase(parser.getName())) {
                String id = parser.getAttributeValue(null, "id");
                String href = parser.getAttributeValue(null, "href");
                String mediaType = parser.getAttributeValue(null, "media-type");
                if (id != null && href != null) {
                    manifest.put(id, href);
                    if (mediaType != null) {
                        manifestMediaType.put(id, mediaType);
                    }
                }
            }
            eventType = parser.next();
        }
    }

    private void parseSpine(XmlPullParser parser, Map<String, String> manifest, String opfDir) throws XmlPullParserException, IOException {
        int eventType = parser.next();
        while (!(eventType == XmlPullParser.END_TAG && "spine".equalsIgnoreCase(parser.getName()))) {
            if (eventType == XmlPullParser.START_TAG && "itemref".equalsIgnoreCase(parser.getName())) {
                String idref = parser.getAttributeValue(null, "idref");
                if (idref != null) {
                    String href = manifest.get(idref);
                    if (href != null) {
                        File chapterFile = new File(opfDir, href);
                        if (chapterFile.exists()) {
                            chapterPaths.add(chapterFile.getAbsolutePath());
                        }
                    }
                }
            }
            eventType = parser.next();
        }
    }

    private void findHtmlFiles(File dir) {
        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                findHtmlFiles(file);
            } else {
                String name = file.getName().toLowerCase();
                if ((name.endsWith(".html") || name.endsWith(".xhtml") || name.endsWith(".htm"))) {
                    if (!chapterPaths.contains(file.getAbsolutePath())) {
                        chapterPaths.add(file.getAbsolutePath());
                    }
                }
            }
        }
    }

    private void extractImagesFromChapters() {
        List<String> manifestImages = new ArrayList<>(imagePaths);

        List<String> allImages = new ArrayList<>();
        findAllImages(new File(extractionDir), allImages);

        List<String> htmlImages = new ArrayList<>();
        for (String chapterPath : chapterPaths) {
            try {
                String content = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(chapterPath)), "UTF-8");
                List<String> chapterImages = new ArrayList<>();
                extractImagesFromHtmlToList(content, new File(chapterPath).getParent(), chapterImages);
                for (String imgPath : chapterImages) {
                    if (!htmlImages.contains(imgPath)) {
                        htmlImages.add(imgPath);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        imagePaths.clear();

        java.util.Set<String> addedSet = new java.util.HashSet<>();

        if (allImages.size() > chapterPaths.size() * 2) {
            for (String imgPath : allImages) {
                if (!addedSet.contains(imgPath)) {
                    imagePaths.add(imgPath);
                    addedSet.add(imgPath);
                }
            }
        } else if (!htmlImages.isEmpty()) {
            for (String imgPath : htmlImages) {
                if (!addedSet.contains(imgPath)) {
                    imagePaths.add(imgPath);
                    addedSet.add(imgPath);
                }
            }
            for (String imgPath : allImages) {
                if (!addedSet.contains(imgPath)) {
                    imagePaths.add(imgPath);
                    addedSet.add(imgPath);
                }
            }
        } else {
            for (String imgPath : allImages) {
                if (!addedSet.contains(imgPath)) {
                    imagePaths.add(imgPath);
                    addedSet.add(imgPath);
                }
            }
        }

        for (String imgPath : manifestImages) {
            if (!addedSet.contains(imgPath)) {
                imagePaths.add(imgPath);
            }
        }

        java.util.Collections.sort(imagePaths, (a, b) -> {
            File fileA = new File(a);
            File fileB = new File(b);
            String parentA = fileA.getParent() != null ? fileA.getParent().toLowerCase() : "";
            String parentB = fileB.getParent() != null ? fileB.getParent().toLowerCase() : "";
            int parentCompare = naturalCompare(parentA, parentB);
            if (parentCompare != 0) {
                return parentCompare;
            }
            return compareFileNames(fileA.getName(), fileB.getName());
        });
    }

    private void extractImagesFromHtmlToList(String html, String baseDir, List<String> result) {
        int imgStart = html.toLowerCase().indexOf("<img");
        while (imgStart >= 0) {
            int srcStart = html.indexOf("src=", imgStart);
            if (srcStart >= 0) {
                srcStart += 4;
                while (srcStart < html.length() && (html.charAt(srcStart) == '\"' || html.charAt(srcStart) == '\'' || html.charAt(srcStart) == ' ')) {
                    srcStart++;
                }
                int srcEnd = srcStart;
                while (srcEnd < html.length() && html.charAt(srcEnd) != '\"' && html.charAt(srcEnd) != '\'' && html.charAt(srcEnd) != ' ' && html.charAt(srcEnd) != '>') {
                    srcEnd++;
                }
                if (srcEnd > srcStart) {
                    String imgSrc = html.substring(srcStart, srcEnd);
                    if (!imgSrc.startsWith("http") && !imgSrc.isEmpty()) {
                        String normalizedSrc = imgSrc.trim();
                        
                        try {
                            normalizedSrc = java.net.URLDecoder.decode(normalizedSrc, "UTF-8");
                        } catch (Exception e) {
                        }

                        File imgFile = resolveImagePath(normalizedSrc, baseDir);

                        if (imgFile != null && imgFile.exists()) {
                            String imgPath = imgFile.getAbsolutePath();
                            if (!result.contains(imgPath)) {
                                result.add(imgPath);
                            }
                        }
                    }
                }
            }
            imgStart = html.toLowerCase().indexOf("<img", imgStart + 4);
        }
    }

    private File resolveImagePath(String src, String baseDir) {
        File imgFile = null;

        if (src.startsWith("/")) {
            imgFile = new File(extractionDir, src.substring(1));
        } else if (src.startsWith("../")) {
            File base = new File(baseDir);
            String relativePath = src;
            while (relativePath.startsWith("../")) {
                base = base.getParentFile();
                relativePath = relativePath.substring(3);
            }
            imgFile = new File(base, relativePath);
        } else {
            imgFile = new File(baseDir, src);
        }

        if (!imgFile.exists()) {
            imgFile = new File(extractionDir, src);
        }

        if (!imgFile.exists() && !src.startsWith("/")) {
            String[] parts = src.split("/");
            if (parts.length > 1) {
                String fileName = parts[parts.length - 1];
                imgFile = findImageFile(new File(extractionDir), fileName);
            }
        }

        return imgFile;
    }

    private void findAllImages(File dir, List<String> result) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) return;

        File[] files = dir.listFiles();
        if (files == null) return;

        java.util.Arrays.sort(files, (a, b) -> {
            if (a.isDirectory() && b.isDirectory()) {
                return naturalCompare(a.getName().toLowerCase(), b.getName().toLowerCase());
            }
            if (a.isDirectory()) return -1;
            if (b.isDirectory()) return 1;
            return compareFileNames(a.getName(), b.getName());
        });

        for (File file : files) {
            if (file.isDirectory()) {
                findAllImages(file, result);
            } else {
                String name = file.getName().toLowerCase();
                if (isImageFile(name) || isImageFileByMagic(file)) {
                    result.add(file.getAbsolutePath());
                }
            }
        }
    }

    private int compareFileNames(String nameA, String nameB) {
        String baseA = nameA.toLowerCase();
        String baseB = nameB.toLowerCase();
        
        boolean hasCoverA = baseA.contains("cover") || baseA.contains("front") || baseA.contains("title");
        boolean hasCoverB = baseB.contains("cover") || baseB.contains("front") || baseB.contains("title");
        
        if (hasCoverA && !hasCoverB) return -1;
        if (!hasCoverA && hasCoverB) return 1;
        
        return naturalCompare(baseA, baseB);
    }

    private int naturalCompare(String a, String b) {
        int lenA = a.length();
        int lenB = b.length();
        int i = 0, j = 0;
        
        while (i < lenA && j < lenB) {
            char ca = a.charAt(i);
            char cb = b.charAt(j);
            
            if (Character.isDigit(ca) && Character.isDigit(cb)) {
                int numA = 0, numB = 0;
                
                while (i < lenA && Character.isDigit(a.charAt(i))) {
                    numA = numA * 10 + (a.charAt(i) - '0');
                    i++;
                }
                
                while (j < lenB && Character.isDigit(b.charAt(j))) {
                    numB = numB * 10 + (b.charAt(j) - '0');
                    j++;
                }
                
                if (numA != numB) {
                    return Integer.compare(numA, numB);
                }
            } else {
                if (ca != cb) {
                    return Character.compare(ca, cb);
                }
                i++;
                j++;
            }
        }
        
        return lenA - lenB;
    }

    private String findCoverImage() {
        for (String path : imagePaths) {
            File file = new File(path);
            String name = file.getName().toLowerCase();
            if (name.contains("cover") || name.contains("front") || name.contains("title")) {
                return path;
            }
        }
        return null;
    }

    private File findImageFile(File dir, String fileName) {
        File[] files = dir.listFiles();
        if (files == null) return null;
        for (File file : files) {
            if (file.isDirectory()) {
                File found = findImageFile(file, fileName);
                if (found != null) return found;
            } else {
                if (file.getName().equalsIgnoreCase(fileName)) {
                    return file;
                }
            }
        }
        return null;
    }

    private boolean isImageFile(String fileName) {
        if (fileName == null) return false;
        fileName = fileName.toLowerCase();
        return fileName.endsWith(".jpg") || fileName.endsWith(".jpeg") || 
               fileName.endsWith(".png") || fileName.endsWith(".gif") || 
               fileName.endsWith(".webp") || fileName.endsWith(".bmp") ||
               fileName.endsWith(".tif") || fileName.endsWith(".tiff") ||
               fileName.endsWith(".svg") || fileName.endsWith(".ico") ||
               fileName.endsWith(".avif") || fileName.endsWith(".heic") ||
               fileName.endsWith(".heif") || fileName.endsWith(".jp2") ||
               fileName.endsWith(".j2k") || fileName.endsWith(".jpf") ||
               fileName.endsWith(".jpx") || fileName.endsWith(".jpm") ||
               fileName.endsWith(".mj2") || fileName.endsWith(".wbmp") ||
               fileName.endsWith(".jpe") || fileName.endsWith(".jfif") ||
               fileName.endsWith(".pjpeg") || fileName.endsWith(".pjp") ||
               fileName.endsWith(".apng") || fileName.endsWith(".mng") ||
               fileName.endsWith(".xbm") || fileName.endsWith(".xpm");
    }

    private boolean isImageFileByMagic(File file) {
        if (file == null || !file.exists() || !file.isFile()) return false;
        
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] header = new byte[12];
            int bytesRead = fis.read(header);
            if (bytesRead < 4) return false;

            if (header[0] == (byte)0xFF && header[1] == (byte)0xD8 && header[2] == (byte)0xFF) {
                return true;
            }

            if (header[0] == (byte)0x89 && header[1] == (byte)0x50 && 
                header[2] == (byte)0x4E && header[3] == (byte)0x47) {
                return true;
            }

            if (header[0] == (byte)0x47 && header[1] == (byte)0x49 && 
                header[2] == (byte)0x46 && header[3] == (byte)0x38) {
                return true;
            }

            if (header[0] == (byte)0x42 && header[1] == (byte)0x4D) {
                return true;
            }

            if (bytesRead >= 12 && 
                header[0] == 'W' && header[1] == 'E' && header[2] == 'B' && header[3] == 'P') {
                return true;
            }

            if (bytesRead >= 4 && 
                header[0] == 'I' && header[1] == 'I' && 
                (header[2] == (byte)0x2A || header[2] == (byte)0xBC)) {
                return true;
            }

            if (header[0] == '<' && (header[1] == 's' || header[1] == 'S')) {
                return true;
            }

            if (bytesRead >= 4 && 
                (header[0] == (byte)0x00 && header[1] == (byte)0x00 && header[2] == (byte)0x00 && header[3] == (byte)0x0C ||
                 header[0] == (byte)0x00 && header[1] == (byte)0x00 && header[2] == (byte)0x00 && header[3] == (byte)0x0D)) {
                return true;
            }

            if (bytesRead >= 4 && 
                header[0] == (byte)0x00 && header[1] == (byte)0x00 && header[2] == (byte)0x00 && header[3] == (byte)0x20) {
                return true;
            }

            if (bytesRead >= 12 && 
                header[4] == 'a' && header[5] == 'v' && header[6] == 'i' && header[7] == 'f') {
                return true;
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
        
        return false;
    }

    public String getChapterContent(int chapterIndex) throws IOException {
        if (!parsed) {
            parse();
        }

        if (!imagePaths.isEmpty()) {
            if (chapterIndex >= 0 && chapterIndex < imagePaths.size()) {
                String imagePath = imagePaths.get(chapterIndex);
                return createImagePage(imagePath);
            }
        }

        if (!chapterPaths.isEmpty()) {
            int htmlIndex = chapterIndex;
            if (!imagePaths.isEmpty()) {
                htmlIndex = chapterIndex - imagePaths.size();
            }
            if (htmlIndex >= 0 && htmlIndex < chapterPaths.size()) {
                File chapterFile = new File(chapterPaths.get(htmlIndex));
                if (chapterFile.exists()) {
                    String content = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(chapterFile.getAbsolutePath())), "UTF-8");
                    return wrapHtmlContent(content, chapterFile.getParent());
                }
            }
        }

        return "<html><body style='background:#1A1A1A;color:#E0E0E0;padding:16px;'>暂无内容</body></html>";
    }

    private String wrapHtmlContent(String html, String baseDir) {
        StringBuilder wrapped = new StringBuilder();
        wrapped.append("<!DOCTYPE html><html><head><meta charset=\"utf-8\">");
        wrapped.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no\">");
        wrapped.append("<style>");
        wrapped.append("* { max-width: 100%; }");
        wrapped.append("body { background-color: #1A1A1A; color: #E0E0E0; padding: 0; margin: 0; line-height: 1.8; font-size: 18px; }");
        wrapped.append("img { max-width: 100%; height: auto; display: block; margin: 0 auto; }");
        wrapped.append("p { margin: 0.8em 16px; text-indent: 2em; }");
        wrapped.append("h1, h2, h3, h4, h5, h6 { color: #FFFFFF; margin: 1.2em 16px 0.6em 16px; }");
        wrapped.append("</style></head><body>");

        String content = html;
        int bodyStart = html.toLowerCase().indexOf("<body");
        if (bodyStart >= 0) {
            int bodyEnd = html.indexOf(">", bodyStart);
            if (bodyEnd >= 0) {
                int bodyClose = html.toLowerCase().indexOf("</body>", bodyEnd);
                if (bodyClose >= 0) {
                    content = html.substring(bodyEnd + 1, bodyClose);
                } else {
                    content = html.substring(bodyEnd + 1);
                }
            }
        }

        content = fixImagePaths(content, baseDir);
        wrapped.append(content);

        wrapped.append("</body></html>");
        return wrapped.toString();
    }

    private String fixImagePaths(String html, String baseDir) {
        if (html == null || html.isEmpty()) return html;

        StringBuilder result = new StringBuilder();
        int imgStart = html.toLowerCase().indexOf("<img");
        int lastIndex = 0;

        while (imgStart >= 0) {
            result.append(html.substring(lastIndex, imgStart));

            int srcStart = html.indexOf("src=", imgStart);
            int srcEnd = imgStart;
            if (srcStart >= 0) {
                srcStart += 4;
                while (srcStart < html.length() && (html.charAt(srcStart) == '\"' || html.charAt(srcStart) == '\'' || html.charAt(srcStart) == ' ')) {
                    srcStart++;
                }
                srcEnd = srcStart;
                while (srcEnd < html.length() && html.charAt(srcEnd) != '\"' && html.charAt(srcEnd) != '\'' && html.charAt(srcEnd) != ' ' && html.charAt(srcEnd) != '>') {
                    srcEnd++;
                }

                if (srcEnd > srcStart) {
                    String imgSrc = html.substring(srcStart, srcEnd);
                    if (!imgSrc.startsWith("http") && !imgSrc.startsWith("file://") && !imgSrc.isEmpty()) {
                        String normalizedSrc = imgSrc.trim();
                        File imgFile = null;

                        if (normalizedSrc.startsWith("/")) {
                            normalizedSrc = normalizedSrc.substring(1);
                            imgFile = new File(extractionDir, normalizedSrc);
                        } else {
                            imgFile = new File(baseDir, normalizedSrc);
                        }

                        if (!imgFile.exists()) {
                            imgFile = new File(extractionDir, normalizedSrc);
                        }

                        if (!imgFile.exists()) {
                            String[] parts = normalizedSrc.split("/");
                            if (parts.length > 1) {
                                String fileName = parts[parts.length - 1];
                                imgFile = findImageFile(new File(extractionDir), fileName);
                            }
                        }

                        if (imgFile != null && imgFile.exists()) {
                            String newSrc = "file://" + imgFile.getAbsolutePath();
                            result.append(html.substring(imgStart, srcStart)).append(newSrc);
                        } else {
                            result.append(html.substring(imgStart, srcEnd));
                        }
                    } else {
                        result.append(html.substring(imgStart, srcEnd));
                    }
                } else {
                    result.append(html.substring(imgStart));
                    srcEnd = html.length();
                }
            } else {
                result.append(html.substring(imgStart));
                srcEnd = html.length();
            }

            lastIndex = srcEnd;
            imgStart = html.toLowerCase().indexOf("<img", lastIndex);
        }

        result.append(html.substring(lastIndex));
        return result.toString();
    }

    private String createImagePage(String imagePath) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><head><meta charset=\"utf-8\">");
        html.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no\">");
        html.append("<style>");
        html.append("body { background-color: #1A1A1A; margin: 0; padding: 0; display: flex; align-items: center; justify-content: center; min-height: 100vh; }");
        html.append("img { max-width: 100%; max-height: 100vh; display: block; }");
        html.append("</style></head><body>");
        html.append("<img src=\"file://").append(imagePath).append("\">");
        html.append("</body></html>");
        return html.toString();
    }

    public String getChapterUrl(int chapterIndex) {
        if (!parsed) {
            try {
                parse();
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        }

        if (!chapterPaths.isEmpty()) {
            if (chapterIndex >= 0 && chapterIndex < chapterPaths.size()) {
                return "file://" + chapterPaths.get(chapterIndex);
            }
        }

        return null;
    }

    public List<String> getImagePaths() {
        if (!parsed) {
            try {
                parse();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return imagePaths;
    }

    public static class EpubInfo {
        public String title = "";
        public String author = "";
        public String coverPath = null;
        public int totalPages = 0;
    }
}