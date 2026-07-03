package com.example.comicreader.utils;

import android.content.Context;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class EpubParser {
    private File epubFile;
    private Context context;
    private String extractionDir;
    private boolean parsed = false;
    private List<Chapter> chapters = new ArrayList<>();
    private List<TocItem> toc = new ArrayList<>();
    private String title = "";
    private String author = "";
    private String coverPath = null;

    public class Chapter {
        public String id;
        public String href;
        public String title;
        public String content;
        public int pageStart;
        public int pageEnd;

        public Chapter(String id, String href, String title) {
            this.id = id;
            this.href = href;
            this.title = title;
        }
    }

    public class TocItem {
        public String title;
        public int chapterIndex;
        public int level;
        public List<TocItem> children;

        public TocItem(String title, int chapterIndex, int level) {
            this.title = title;
            this.chapterIndex = chapterIndex;
            this.level = level;
            this.children = new ArrayList<>();
        }
    }

    public EpubParser(File epubFile, Context context) {
        this.epubFile = epubFile;
        this.context = context;
    }

    public EpubInfo parse() throws IOException {
        EpubInfo info = new EpubInfo();

        if (parsed) {
            info.title = this.title;
            info.author = this.author;
            info.coverPath = this.coverPath;
            info.totalPages = chapters.size();
            return info;
        }

        try {
            extractionDir = extractEpubToCache();

            String opfPath = findOpfPath();
            if (opfPath == null) {
                throw new IOException("无法找到OPF文件");
            }

            File opfFile = new File(extractionDir, opfPath);
            Map<String, String> manifest = new HashMap<>();
            Map<String, String> manifestMediaType = new HashMap<>();

            parseOpfFile(opfFile, info, manifest, manifestMediaType);

            parseSpine(opfFile, manifest, info);

            parseNav(opfFile, manifest);

            if (info.coverPath == null) {
                info.coverPath = extractCover(manifest, manifestMediaType, new File(opfFile.getParent()).getAbsolutePath());
            }

            loadChapterContents(manifest);

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

    private void parseOpfFile(File opfFile, EpubInfo info, Map<String, String> manifest, Map<String, String> manifestMediaType) throws IOException, XmlPullParserException {
        XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
        XmlPullParser parser = factory.newPullParser();
        parser.setInput(new FileReader(opfFile));

        int eventType = parser.getEventType();
        while (eventType != XmlPullParser.END_DOCUMENT) {
            switch (eventType) {
                case XmlPullParser.START_TAG:
                    String tag = parser.getName().toLowerCase();
                    if (tag.equals("metadata")) {
                        parseMetadata(parser, info);
                    } else if (tag.equals("manifest")) {
                        parseManifest(parser, manifest, manifestMediaType);
                    }
                    break;
            }
            eventType = parser.next();
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
                            this.title = info.title;
                        }
                    }
                } else if (tag.contains("creator") || tag.endsWith(":creator") || tag.contains("author")) {
                    parser.next();
                    if (parser.getEventType() == XmlPullParser.TEXT) {
                        String text = parser.getText();
                        if (text != null && !text.trim().isEmpty()) {
                            info.author = text.trim();
                            this.author = info.author;
                        }
                    }
                } else if (tag.contains("cover")) {
                    String coverId = parser.getAttributeValue(null, "content");
                    if (coverId != null && !coverId.isEmpty()) {
                        info.coverId = coverId;
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

    private void parseSpine(File opfFile, Map<String, String> manifest, EpubInfo info) throws IOException, XmlPullParserException {
        XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
        XmlPullParser parser = factory.newPullParser();
        parser.setInput(new FileReader(opfFile));

        String opfDir = opfFile.getParent();

        int eventType = parser.getEventType();
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG && "spine".equalsIgnoreCase(parser.getName())) {
                parseSpineItems(parser, manifest, opfDir);
                break;
            }
            eventType = parser.next();
        }

        info.totalPages = chapters.size();
    }

    private void parseSpineItems(XmlPullParser parser, Map<String, String> manifest, String opfDir) throws XmlPullParserException, IOException {
        int eventType = parser.next();
        while (!(eventType == XmlPullParser.END_TAG && "spine".equalsIgnoreCase(parser.getName()))) {
            if (eventType == XmlPullParser.START_TAG && "itemref".equalsIgnoreCase(parser.getName())) {
                String idref = parser.getAttributeValue(null, "idref");
                String linear = parser.getAttributeValue(null, "linear");

                if ("no".equalsIgnoreCase(linear)) {
                    eventType = parser.next();
                    continue;
                }

                if (idref != null) {
                    String href = manifest.get(idref);
                    if (href != null) {
                        String chapterTitle = extractChapterTitle(idref, href, opfDir);
                        chapters.add(new Chapter(idref, href, chapterTitle));
                    }
                }
            }
            eventType = parser.next();
        }
    }

    private String extractChapterTitle(String idref, String href, String opfDir) {
        try {
            File chapterFile = new File(opfDir, href);
            if (!chapterFile.exists()) {
                chapterFile = new File(extractionDir, href);
            }

            if (chapterFile.exists()) {
                String content = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(chapterFile.getAbsolutePath())), StandardCharsets.UTF_8);
                
                int titleStart = content.toLowerCase().indexOf("<title");
                if (titleStart >= 0) {
                    int titleEnd = content.indexOf(">", titleStart);
                    if (titleEnd >= 0) {
                        int titleClose = content.indexOf("</title>", titleEnd);
                        if (titleClose >= 0) {
                            String title = content.substring(titleEnd + 1, titleClose).trim();
                            if (!title.isEmpty()) return title;
                        }
                    }
                }

                int h1Start = content.toLowerCase().indexOf("<h1");
                if (h1Start >= 0) {
                    int h1End = content.indexOf(">", h1Start);
                    if (h1End >= 0) {
                        int h1Close = content.indexOf("</h1>", h1End);
                        if (h1Close >= 0) {
                            String title = content.substring(h1End + 1, h1Close).replaceAll("<[^>]*>", "").trim();
                            if (!title.isEmpty()) return title;
                        }
                    }
                }

                int h2Start = content.toLowerCase().indexOf("<h2");
                if (h2Start >= 0) {
                    int h2End = content.indexOf(">", h2Start);
                    if (h2End >= 0) {
                        int h2Close = content.indexOf("</h2>", h2End);
                        if (h2Close >= 0) {
                            String title = content.substring(h2End + 1, h2Close).replaceAll("<[^>]*>", "").trim();
                            if (!title.isEmpty()) return title;
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        File file = new File(href);
        String name = file.getName();
        return name.replaceAll("\\.[^.]+$", "");
    }

    private void parseNav(File opfFile, Map<String, String> manifest) throws IOException, XmlPullParserException {
        String opfDir = opfFile.getParent();

        String navId = null;
        for (Map.Entry<String, String> entry : manifest.entrySet()) {
            String href = entry.getValue();
            if (href.toLowerCase().endsWith("nav.xhtml") || href.toLowerCase().endsWith("toc.ncx")) {
                navId = entry.getKey();
                break;
            }
        }

        if (navId == null) {
            for (Map.Entry<String, String> entry : manifest.entrySet()) {
                String href = entry.getValue();
                if (href.toLowerCase().contains("toc") || href.toLowerCase().contains("nav")) {
                    navId = entry.getKey();
                    break;
                }
            }
        }

        if (navId != null) {
            String navHref = manifest.get(navId);
            File navFile = new File(opfDir, navHref);
            if (!navFile.exists()) {
                navFile = new File(extractionDir, navHref);
            }

            if (navFile.exists()) {
                if (navHref.toLowerCase().endsWith(".ncx")) {
                    parseNcx(navFile);
                } else {
                    parseNavXhtml(navFile);
                }
            }
        }

        if (toc.isEmpty()) {
            buildTocFromChapters();
        }
    }

    private void parseNavXhtml(File navFile) throws IOException, XmlPullParserException {
        XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
        XmlPullParser parser = factory.newPullParser();
        parser.setInput(new FileReader(navFile));

        int eventType = parser.getEventType();
        int level = 0;
        TocItem currentParent = null;

        while (eventType != XmlPullParser.END_DOCUMENT) {
            switch (eventType) {
                case XmlPullParser.START_TAG:
                    String tag = parser.getName().toLowerCase();
                    if (tag.equals("ol") || tag.equals("ul")) {
                        level++;
                    } else if (tag.equals("li")) {
                        parseNavListItem(parser, level, currentParent);
                    }
                    break;
                case XmlPullParser.END_TAG:
                    tag = parser.getName().toLowerCase();
                    if (tag.equals("ol") || tag.equals("ul")) {
                        level--;
                        if (currentParent != null && currentParent.level >= level) {
                            currentParent = findParentForLevel(level);
                        }
                    }
                    break;
            }
            eventType = parser.next();
        }
    }

    private void parseNavListItem(XmlPullParser parser, int level, TocItem parent) throws XmlPullParserException, IOException {
        int eventType = parser.next();
        String title = "";
        String href = "";

        while (!(eventType == XmlPullParser.END_TAG && "li".equalsIgnoreCase(parser.getName()))) {
            if (eventType == XmlPullParser.START_TAG) {
                String tag = parser.getName().toLowerCase();
                if (tag.equals("a")) {
                    href = parser.getAttributeValue(null, "href");
                    parser.next();
                    if (parser.getEventType() == XmlPullParser.TEXT) {
                        title = parser.getText();
                    }
                }
            }
            eventType = parser.next();
        }

        if (!title.isEmpty() && !href.isEmpty()) {
            int chapterIndex = findChapterIndexByHref(href);
            TocItem item = new TocItem(title, chapterIndex, level);

            if (parent != null) {
                parent.children.add(item);
            } else {
                toc.add(item);
            }
        }
    }

    private void parseNcx(File ncxFile) throws IOException, XmlPullParserException {
        XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
        XmlPullParser parser = factory.newPullParser();
        parser.setInput(new FileReader(ncxFile));

        int eventType = parser.getEventType();
        int level = 1;
        TocItem currentParent = null;

        while (eventType != XmlPullParser.END_DOCUMENT) {
            switch (eventType) {
                case XmlPullParser.START_TAG:
                    String tag = parser.getName().toLowerCase();
                    if (tag.equals("navpoint")) {
                        String playOrder = parser.getAttributeValue(null, "playOrder");
                        String id = parser.getAttributeValue(null, "id");
                        parseNcxNavPoint(parser, level, currentParent, playOrder);
                    }
                    break;
            }
            eventType = parser.next();
        }
    }

    private void parseNcxNavPoint(XmlPullParser parser, int level, TocItem parent, String playOrder) throws XmlPullParserException, IOException {
        int eventType = parser.next();
        String title = "";
        String href = "";

        while (!(eventType == XmlPullParser.END_TAG && "navpoint".equalsIgnoreCase(parser.getName()))) {
            if (eventType == XmlPullParser.START_TAG) {
                String tag = parser.getName().toLowerCase();
                if (tag.equals("navlabel")) {
                    eventType = parser.next();
                    while (!(eventType == XmlPullParser.END_TAG && "navlabel".equalsIgnoreCase(parser.getName()))) {
                        if (eventType == XmlPullParser.START_TAG && "text".equalsIgnoreCase(parser.getName())) {
                            parser.next();
                            if (parser.getEventType() == XmlPullParser.TEXT) {
                                title = parser.getText();
                            }
                        }
                        eventType = parser.next();
                    }
                } else if (tag.equals("content")) {
                    href = parser.getAttributeValue(null, "src");
                }
            }
            eventType = parser.next();
        }

        if (!title.isEmpty() && !href.isEmpty()) {
            int chapterIndex = findChapterIndexByHref(href);
            if (chapterIndex < 0 && playOrder != null) {
                try {
                    chapterIndex = Integer.parseInt(playOrder) - 1;
                } catch (Exception e) {
                }
            }
            TocItem item = new TocItem(title, chapterIndex, level);

            if (parent != null) {
                parent.children.add(item);
            } else {
                toc.add(item);
            }
        }
    }

    private int findChapterIndexByHref(String href) {
        String baseHref = href;
        int hashIndex = href.indexOf("#");
        if (hashIndex >= 0) {
            baseHref = href.substring(0, hashIndex);
        }

        for (int i = 0; i < chapters.size(); i++) {
            String chapterHref = chapters.get(i).href;
            if (chapterHref.equals(baseHref) || chapterHref.endsWith("/" + baseHref)) {
                return i;
            }
        }

        return -1;
    }

    private TocItem findParentForLevel(int level) {
        if (level <= 1) return null;

        for (int i = toc.size() - 1; i >= 0; i--) {
            TocItem item = toc.get(i);
            if (item.level == level - 1) {
                return item;
            }
        }

        return null;
    }

    private void buildTocFromChapters() {
        for (int i = 0; i < chapters.size(); i++) {
            Chapter chapter = chapters.get(i);
            if (chapter.title != null && !chapter.title.isEmpty()) {
                toc.add(new TocItem(chapter.title, i, 1));
            }
        }
    }

    private void loadChapterContents(Map<String, String> manifest) {
        String opfDir = extractionDir;
        for (Chapter chapter : chapters) {
            try {
                File chapterFile = new File(opfDir, chapter.href);
                if (!chapterFile.exists()) {
                    chapterFile = new File(extractionDir, chapter.href);
                }

                if (!chapterFile.exists()) {
                    int slashIndex = chapter.href.lastIndexOf("/");
                    if (slashIndex >= 0) {
                        String fileName = chapter.href.substring(slashIndex + 1);
                        chapterFile = findFileInExtractionDir(fileName);
                    }
                }

                if (chapterFile.exists()) {
                    String content = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(chapterFile.getAbsolutePath())), StandardCharsets.UTF_8);
                    String baseDir = chapterFile.getParent();
                    content = fixHtmlContent(content, baseDir);
                    chapter.content = content;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private File findFileInExtractionDir(String fileName) {
        File root = new File(extractionDir);
        return findFileRecursive(root, fileName);
    }

    private File findFileRecursive(File dir, String fileName) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) return null;

        File[] files = dir.listFiles();
        if (files == null) return null;

        for (File file : files) {
            if (file.isDirectory()) {
                File found = findFileRecursive(file, fileName);
                if (found != null) return found;
            } else if (file.getName().equalsIgnoreCase(fileName)) {
                return file;
            }
        }

        return null;
    }

    private String fixHtmlContent(String html, String baseDir) {
        if (html == null) return "";

        StringBuilder wrapped = new StringBuilder();
        wrapped.append("<!DOCTYPE html><html><head><meta charset=\"utf-8\">");
        wrapped.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">");
        wrapped.append("<base href=\"file://").append(baseDir).append("/\">");
        wrapped.append("<style>");
        wrapped.append("* { max-width: 100%; }");
        wrapped.append("body { background-color: #1A1A1A; color: #E0E0E0; padding: 16px; line-height: 1.8; font-size: 18px; margin: 0; word-wrap: break-word; }");
        wrapped.append("img { max-width: 100%; height: auto; display: block; margin: 16px auto; }");
        wrapped.append("p { margin: 0.8em 0; text-indent: 2em; }");
        wrapped.append("h1 { color: #FFFFFF; margin: 1.5em 0 0.8em 0; font-size: 24px; text-indent: 0; border-bottom: 1px solid #333; padding-bottom: 8px; }");
        wrapped.append("h2 { color: #FFFFFF; margin: 1.2em 0 0.6em 0; font-size: 20px; text-indent: 0; }");
        wrapped.append("h3 { color: #FFFFFF; margin: 1em 0 0.5em 0; font-size: 18px; text-indent: 0; }");
        wrapped.append("a { color: #4CAF50; text-decoration: none; }");
        wrapped.append("a:hover { text-decoration: underline; }");
        wrapped.append("table { width: 100%; border-collapse: collapse; margin: 16px 0; }");
        wrapped.append("th, td { border: 1px solid #333; padding: 8px; text-align: left; }");
        wrapped.append("th { background-color: #222; }");
        wrapped.append("blockquote { border-left: 4px solid #4CAF50; margin: 16px 0; padding: 8px 16px; color: #AAA; }");
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
        content = fixRelativePaths(content, baseDir);
        wrapped.append(content);

        wrapped.append("</body></html>");
        return wrapped.toString();
    }

    private String fixImagePaths(String html, String baseDir) {
        return fixRelativePaths(html, baseDir);
    }

    private String fixRelativePaths(String html, String baseDir) {
        if (html == null || html.isEmpty()) return html;

        String fileBase = "file://" + baseDir + "/";

        html = html.replaceAll("src=\"([^\"]+)\"", "src=\"" + fileBase + "$1\"");
        html = html.replaceAll("src='([^']+)'", "src='" + fileBase + "$1'");
        html = html.replaceAll("url\\(([^)]+)\\)", "url(" + fileBase + "$1)");

        html = html.replaceAll("href=\"([^\"#][^\"]*)\"", "href=\"" + fileBase + "$1\"");
        html = html.replaceAll("href='([^'#][^']*)'", "href='" + fileBase + "$1'");

        html = html.replaceAll("src=\"file://[^/]+//", "src=\"file://");
        html = html.replaceAll("href=\"file://[^/]+//", "href=\"file://");

        return html;
    }

    private String extractCover(Map<String, String> manifest, Map<String, String> manifestMediaType, String opfDir) {
        for (Map.Entry<String, String> entry : manifest.entrySet()) {
            String id = entry.getKey();
            String href = entry.getValue();
            String mediaType = manifestMediaType.get(id);

            if (mediaType != null && mediaType.startsWith("image/")) {
                if (id.toLowerCase().contains("cover") || href.toLowerCase().contains("cover")) {
                    File coverFile = new File(opfDir, href);
                    if (!coverFile.exists()) {
                        coverFile = new File(extractionDir, href);
                    }
                    if (coverFile.exists()) {
                        return coverFile.getAbsolutePath();
                    }
                }
            }
        }

        File imgDir = new File(extractionDir, "images");
        if (!imgDir.exists()) {
            imgDir = new File(extractionDir, "Images");
        }
        if (!imgDir.exists()) {
            imgDir = new File(extractionDir, "IMG");
        }

        if (imgDir.exists() && imgDir.isDirectory()) {
            File[] images = imgDir.listFiles();
            if (images != null && images.length > 0) {
                for (File img : images) {
                    if (img.getName().toLowerCase().contains("cover")) {
                        return img.getAbsolutePath();
                    }
                }
                return images[0].getAbsolutePath();
            }
        }

        return null;
    }

    public String getChapterContent(int chapterIndex) throws IOException {
        if (!parsed) {
            parse();
        }

        if (chapterIndex < 0 || chapterIndex >= chapters.size()) {
            return createEmptyPage("暂无内容");
        }

        Chapter chapter = chapters.get(chapterIndex);
        if (chapter.content != null && !chapter.content.isEmpty()) {
            return chapter.content;
        }

        return createEmptyPage("章节内容加载失败");
    }

    public String getFullContent() throws IOException {
        if (!parsed) {
            parse();
        }

        if (chapters.isEmpty()) {
            return createEmptyPage("暂无内容");
        }

        StringBuilder fullContent = new StringBuilder();
        fullContent.append("<!DOCTYPE html><html><head><meta charset=\"utf-8\">");
        fullContent.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">");
        fullContent.append("<style>");
        fullContent.append("* { max-width: 100%; }");
        fullContent.append("body { background-color: #1A1A1A; color: #E0E0E0; padding: 16px; line-height: 1.8; font-size: 18px; margin: 0; word-wrap: break-word; }");
        fullContent.append("img { max-width: 100%; height: auto; display: block; margin: 16px auto; }");
        fullContent.append("p { margin: 0.8em 0; text-indent: 2em; }");
        fullContent.append("h1 { color: #FFFFFF; margin: 1.5em 0 0.8em 0; font-size: 24px; text-indent: 0; border-bottom: 1px solid #333; padding-bottom: 8px; }");
        fullContent.append("h2 { color: #FFFFFF; margin: 1.2em 0 0.6em 0; font-size: 20px; text-indent: 0; }");
        fullContent.append("h3 { color: #FFFFFF; margin: 1em 0 0.5em 0; font-size: 18px; text-indent: 0; }");
        fullContent.append("a { color: #4CAF50; text-decoration: none; }");
        fullContent.append("a:hover { text-decoration: underline; }");
        fullContent.append("table { width: 100%; border-collapse: collapse; margin: 16px 0; }");
        fullContent.append("th, td { border: 1px solid #333; padding: 8px; text-align: left; }");
        fullContent.append("th { background-color: #222; }");
        fullContent.append("blockquote { border-left: 4px solid #4CAF50; margin: 16px 0; padding: 8px 16px; color: #AAA; }");
        fullContent.append(".chapter-break { page-break-before: always; margin-top: 40px; }");
        fullContent.append("</style></head><body>");

        String baseDir = "";
        if (!chapters.isEmpty()) {
            baseDir = chapters.get(0).href.contains("/") 
                ? extractionDir + "/" + chapters.get(0).href.substring(0, chapters.get(0).href.lastIndexOf("/"))
                : extractionDir;
        }

        for (int i = 0; i < chapters.size(); i++) {
            Chapter chapter = chapters.get(i);
            
            fullContent.append("<div id=\"chapter_").append(i).append("\">");
            
            if (i > 0) {
                fullContent.append("<div style=\"height:40px;\"></div>");
            }
            
            if (chapter.title != null && !chapter.title.isEmpty()) {
                fullContent.append("<h1>").append(chapter.title).append("</h1>");
            }
            
            if (chapter.content != null && !chapter.content.isEmpty()) {
                String chapterBody = extractBodyContent(chapter.content);
                String fixedContent = fixRelativePaths(chapterBody, baseDir);
                fullContent.append(fixedContent);
            }
            
            fullContent.append("</div>");
        }

        fullContent.append("</body></html>");
        return fullContent.toString();
    }

    private String extractBodyContent(String html) {
        if (html == null) return "";
        
        int bodyStart = html.toLowerCase().indexOf("<body");
        if (bodyStart >= 0) {
            int bodyEnd = html.indexOf(">", bodyStart);
            if (bodyEnd >= 0) {
                int bodyClose = html.toLowerCase().indexOf("</body>", bodyEnd);
                if (bodyClose >= 0) {
                    return html.substring(bodyEnd + 1, bodyClose);
                } else {
                    return html.substring(bodyEnd + 1);
                }
            }
        }
        
        return html;
    }

    private String createEmptyPage(String message) {
        return "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\"><style>body { background-color: #1A1A1A; color: #E0E0E0; padding: 16px; text-align: center; }</style></head><body>" + message + "</body></html>";
    }

    public List<TocItem> getToc() {
        if (!parsed) {
            try {
                parse();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return toc;
    }

    public List<Chapter> getChapters() {
        if (!parsed) {
            try {
                parse();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return chapters;
    }

    public static class EpubInfo {
        public String title = "";
        public String author = "";
        public String coverPath = null;
        public String coverId = null;
        public int totalPages = 0;
    }
}