package com.example.comicreader.utils;

import android.content.Context;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class MobiParser {
    private File mobiFile;
    private Context context;
    private MobiInfo cachedInfo;
    private boolean parsed = false;
    private List<String> imagePaths = new ArrayList<>();
    private boolean isComicMode = false;
    private List<String> chapterContents = new ArrayList<>();

    public MobiParser(File mobiFile, Context context) {
        this.mobiFile = mobiFile;
        this.context = context;
    }

    public MobiInfo parse() throws IOException {
        if (parsed && cachedInfo != null) {
            return cachedInfo;
        }

        if (mobiFile.length() < 50) {
            throw new IOException("文件太小，不是有效的MOBI文件");
        }

        MobiInfo info = new MobiInfo();

        try (RandomAccessFile raf = new RandomAccessFile(mobiFile, "r")) {
            raf.seek(0);
            byte[] header = new byte[512];
            int bytesRead = raf.read(header);

            int headerType = detectHeaderType(header, bytesRead);

            if (headerType == 1) {
                parseBookMobiFormat(raf, info);
            } else if (headerType == 2) {
                parsePalmDocFormat(raf, info);
            } else if (headerType == 3) {
                parseKf8Format(raf, info);
            } else {
                parseGenericFormat(raf, info);
            }

            extractAllImages(raf);

            if (isComicMode) {
                info.totalPages = imagePaths.size();
            } else {
                if (info.chapters.isEmpty()) {
                    extractTextFromAllRecords(raf, info);
                }
                info.totalPages = info.chapters.size();
            }

            if (info.totalPages == 0) {
                info.totalPages = 1;
                if (isComicMode && !imagePaths.isEmpty()) {
                    info.totalPages = imagePaths.size();
                } else {
                    info.chapters.add("");
                }
            }

            if (info.coverPath == null) {
                info.coverPath = extractCover();
            }

        }

        cachedInfo = info;
        parsed = true;
        return info;
    }

    private int detectHeaderType(byte[] header, int bytesRead) {
        if (bytesRead >= 8) {
            String identifier = new String(header, 0, Math.min(bytesRead, 8), StandardCharsets.US_ASCII).trim();
            if (identifier.startsWith("BOOKMOBI")) return 1;
            if (identifier.startsWith("MOBI")) return 1;
        }

        if (bytesRead >= 40) {
            int creator = (header[36] & 0xFF) << 24 | (header[37] & 0xFF) << 16 |
                          (header[38] & 0xFF) << 8 | (header[39] & 0xFF);
            if (creator == 0x4B465820) return 3;
            if (creator == 0x4B494E44) return 3;
            if (creator == 0x4D4F4249) return 1;
            if (creator == 0x414D415A) return 2;
        }

        if (bytesRead >= 16) {
            int magic = (header[0] & 0xFF) << 24 | (header[1] & 0xFF) << 16 |
                        (header[2] & 0xFF) << 8 | (header[3] & 0xFF);
            if (magic == 0x504B0304) return 3;
            if (magic == 0x4D4F4249) return 1;
        }

        if (bytesRead >= 8) {
            String id8 = new String(header, 0, Math.min(bytesRead, 8), StandardCharsets.US_ASCII);
            if (id8.startsWith("TEXt") || id8.startsWith("BOOK")) return 2;
        }

        return 0;
    }

    private void parseBookMobiFormat(RandomAccessFile raf, MobiInfo info) throws IOException {
        try {
            raf.seek(60);
            int titleOffset = readIntBE(raf);
            int titleLength = readIntBE(raf);

            raf.seek(84);
            int authorOffset = readIntBE(raf);
            int authorLength = readIntBE(raf);

            if (titleOffset > 0 && titleLength > 0 && titleOffset + titleLength <= mobiFile.length()) {
                raf.seek(titleOffset);
                byte[] titleBytes = new byte[titleLength];
                raf.readFully(titleBytes);
                info.title = decodeString(titleBytes).trim();
            }

            if (authorOffset > 0 && authorLength > 0 && authorOffset + authorLength <= mobiFile.length()) {
                raf.seek(authorOffset);
                byte[] authorBytes = new byte[authorLength];
                raf.readFully(authorBytes);
                info.author = decodeString(authorBytes).trim();
            }

            raf.seek(12);
            int numSections = readShortBE(raf);

            int textOffset = 0;
            for (int i = 0; i < numSections; i++) {
                int pos = 76 + i * 8;
                if (pos + 8 > mobiFile.length()) break;
                raf.seek(pos);
                int offset = readIntBE(raf);
                if (i == 0) textOffset = offset;
            }

            if (textOffset > 0 && textOffset < mobiFile.length()) {
                extractTextFromOffset(raf, textOffset, info);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void parsePalmDocFormat(RandomAccessFile raf, MobiInfo info) throws IOException {
        try {
            int nameOffset = (raf.readByte() & 0xFF) << 8 | (raf.readByte() & 0xFF);

            raf.seek(32);
            int dbType = readIntBE(raf);
            int creator = readIntBE(raf);

            if (nameOffset > 0 && nameOffset < mobiFile.length()) {
                raf.seek(nameOffset);
                byte[] nameBytes = new byte[32];
                raf.readFully(nameBytes);
                String dbName = new String(nameBytes, StandardCharsets.US_ASCII).trim();
                if (info.title.isEmpty()) {
                    info.title = dbName;
                }
            }

            raf.seek(76);
            int textOffset = readIntBE(raf);
            int numRecords = readIntBE(raf);

            if (textOffset > 0 && textOffset < mobiFile.length()) {
                extractTextFromOffset(raf, textOffset, info);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void parseKf8Format(RandomAccessFile raf, MobiInfo info) throws IOException {
        try {
            raf.seek(0);
            byte[] localHeader = new byte[30];
            raf.readFully(localHeader);

            int fileNameLength = (localHeader[26] & 0xFF) | ((localHeader[27] & 0xFF) << 8);
            int extraFieldLength = (localHeader[28] & 0xFF) | ((localHeader[29] & 0xFF) << 8);

            int fileDataOffset = 30 + fileNameLength + extraFieldLength;
            if (fileDataOffset > mobiFile.length()) {
                parseGenericFormat(raf, info);
                return;
            }

            raf.seek(fileDataOffset);
            byte[] contentStart = new byte[8];
            raf.readFully(contentStart);

            String contentId = new String(contentStart, StandardCharsets.US_ASCII).trim();
            if (contentId.startsWith("MOBI") || contentId.startsWith("BOOK")) {
                parseBookMobiFormat(raf, info);
            } else {
                extractTextFromOffset(raf, fileDataOffset, info);
            }
        } catch (Exception e) {
            e.printStackTrace();
            parseGenericFormat(raf, info);
        }
    }

    private void parseGenericFormat(RandomAccessFile raf, MobiInfo info) throws IOException {
        try {
            raf.seek(0);
            byte[] buffer = new byte[(int) Math.min(mobiFile.length(), 64 * 1024)];
            raf.readFully(buffer);

            String content = decodeString(buffer);
            content = cleanContent(content);

            if (content.length() > 100) {
                info.chapters = extractChapters(content);
            }

            int jpgCount = countPattern(buffer, new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});
            int pngCount = countPattern(buffer, new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47});

            if (jpgCount + pngCount > 10) {
                isComicMode = true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private int countPattern(byte[] data, byte[] pattern) {
        int count = 0;
        for (int i = 0; i <= data.length - pattern.length; i++) {
            boolean match = true;
            for (int j = 0; j < pattern.length; j++) {
                if (data[i + j] != pattern[j]) {
                    match = false;
                    break;
                }
            }
            if (match) {
                count++;
            }
        }
        return count;
    }

    private void extractAllImages(RandomAccessFile raf) {
        try {
            raf.seek(0);
            int fileSize = (int) Math.min(mobiFile.length(), 50 * 1024 * 1024);
            byte[] buffer = new byte[fileSize];
            raf.readFully(buffer);

            List<byte[]> extractedImages = new ArrayList<>();

            int pos = 0;
            while (pos < buffer.length - 3) {
                if (buffer[pos] == (byte) 0xFF && buffer[pos + 1] == (byte) 0xD8 && buffer[pos + 2] == (byte) 0xFF) {
                    int end = findPattern(buffer, new byte[]{(byte) 0xFF, (byte) 0xD9}, pos + 3);
                    if (end >= pos) {
                        int len = end - pos + 2;
                        if (len > 1000) {
                            byte[] img = new byte[len];
                            System.arraycopy(buffer, pos, img, 0, len);
                            extractedImages.add(img);
                        }
                        pos = end + 2;
                        continue;
                    }
                } else if (buffer[pos] == (byte) 0x89 && buffer[pos + 1] == 0x50 &&
                           buffer[pos + 2] == 0x4E && buffer[pos + 3] == 0x47) {
                    int end = findPattern(buffer, new byte[]{0x49, 0x45, 0x4E, 0x44, (byte) 0xAE, (byte) 0x42, (byte) 0x60, (byte) 0x82}, pos + 4);
                    if (end >= pos) {
                        int len = end - pos + 8;
                        if (len > 1000) {
                            byte[] img = new byte[len];
                            System.arraycopy(buffer, pos, img, 0, len);
                            extractedImages.add(img);
                        }
                        pos = end + 8;
                        continue;
                    }
                }
                pos++;
            }

            if (extractedImages.size() > 5) {
                isComicMode = true;
            }

            for (int i = 0; i < extractedImages.size(); i++) {
                byte[] img = extractedImages.get(i);
                String ext = (img[0] == (byte) 0xFF && img[1] == (byte) 0xD8) ? ".jpg" : ".png";
                String fileName = "mobi_img_" + i + ext;
                File imgFile = new File(FileUtils.getCacheDirectory(context), fileName);
                try (OutputStream os = new FileOutputStream(imgFile)) {
                    os.write(img);
                }
                imagePaths.add(imgFile.getAbsolutePath());
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void extractTextFromOffset(RandomAccessFile raf, int offset, MobiInfo info) {
        try {
            if (offset <= 0 || offset >= mobiFile.length()) return;

            raf.seek(offset);
            int remaining = (int) (mobiFile.length() - offset);
            if (remaining > 10 * 1024 * 1024) remaining = 10 * 1024 * 1024;

            byte[] data = new byte[remaining];
            raf.readFully(data);

            String content = decodeString(data);
            content = cleanContent(content);

            if (content.length() > 100) {
                info.chapters = extractChapters(content);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void extractTextFromAllRecords(RandomAccessFile raf, MobiInfo info) {
        try {
            raf.seek(0);
            byte[] header = new byte[128];
            raf.readFully(header);

            int numSections = readShortBE(new ByteArrayInputStream(header), 12);
            int firstSectionOffset = readIntBE(new ByteArrayInputStream(header), 76);

            if (firstSectionOffset <= 0 || firstSectionOffset >= mobiFile.length()) {
                extractRawText(raf, info);
                return;
            }

            StringBuilder fullContent = new StringBuilder();

            for (int i = 0; i < Math.min(numSections, 200); i++) {
                int offsetPos = 76 + i * 8;
                if (offsetPos + 8 > mobiFile.length()) break;

                raf.seek(offsetPos);
                int offset = readIntBE(raf);
                int type = readIntBE(raf);

                if (offset <= 0) continue;

                int nextOffset;
                if (i + 1 < numSections) {
                    int nextPos = 76 + (i + 1) * 8;
                    if (nextPos + 4 <= mobiFile.length()) {
                        raf.seek(nextPos);
                        nextOffset = readIntBE(raf);
                    } else {
                        nextOffset = (int) mobiFile.length();
                    }
                } else {
                    nextOffset = (int) mobiFile.length();
                }

                int size = nextOffset - offset;
                if (size <= 0 || size > 512 * 1024) continue;

                if (offset + size > mobiFile.length()) {
                    size = (int) (mobiFile.length() - offset);
                }

                raf.seek(offset);
                byte[] recordData = new byte[size];
                raf.readFully(recordData);

                String text = decodeString(recordData);
                text = cleanContent(text);

                if (text.length() > 50) {
                    fullContent.append(text);
                    fullContent.append("\n\n");
                }
            }

            if (fullContent.length() > 100) {
                info.chapters = extractChapters(fullContent.toString());
            } else {
                extractRawText(raf, info);
            }
        } catch (Exception e) {
            e.printStackTrace();
            extractRawText(raf, info);
        }
    }

    private void extractRawText(RandomAccessFile raf, MobiInfo info) {
        try {
            raf.seek(0);
            byte[] buffer = new byte[(int) Math.min(mobiFile.length(), 2 * 1024 * 1024)];
            int bytesRead = raf.read(buffer);

            String content = decodeString(buffer);
            content = cleanContent(content);

            if (content.length() > 100) {
                info.chapters = extractChapters(content);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String decodeString(byte[] data) {
        try {
            return new String(data, StandardCharsets.UTF_8);
        } catch (Exception e) {
            try {
                return new String(data, "GBK");
            } catch (Exception e2) {
                try {
                    return new String(data, "GB2312");
                } catch (Exception e3) {
                    try {
                        return new String(data, "ISO-8859-1");
                    } catch (Exception e4) {
                        return new String(data);
                    }
                }
            }
        }
    }

    private String cleanContent(String content) {
        if (content == null) return "";

        content = content.replaceAll("\\u0000", "");
        content = content.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", "");
        content = content.replaceAll("\\r\\n", "\n");
        content = content.replaceAll("\\r", "\n");
        content = content.replaceAll("(?s)\\<script[^>]*>.*?</script\\>", "");
        content = content.replaceAll("(?s)\\<style[^>]*>.*?</style\\>", "");
        content = content.replaceAll("(?s)\\<![^>]*\\>", "");
        content = content.replaceAll("<br\\s*/?>", "\n");
        content = content.replaceAll("</p>", "\n\n");
        content = content.replaceAll("<[^>]*>", "");
        content = content.replaceAll("&nbsp;", " ");
        content = content.replaceAll("&amp;", "&");
        content = content.replaceAll("&lt;", "<");
        content = content.replaceAll("&gt;", ">");
        content = content.replaceAll("&quot;", "\"");
        content = content.replaceAll("&#\\d+;", " ");
        content = content.replaceAll("&[a-zA-Z]+;", " ");

        StringBuilder cleaned = new StringBuilder();
        boolean inWhitespace = false;
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (Character.isWhitespace(c)) {
                if (!inWhitespace) {
                    cleaned.append(' ');
                    inWhitespace = true;
                }
            } else {
                cleaned.append(c);
                inWhitespace = false;
            }
        }

        return cleaned.toString().trim();
    }

    private List<String> extractChapters(String content) {
        List<String> chapters = new ArrayList<>();

        if (content == null || content.isEmpty()) {
            chapters.add("<p>暂无内容</p>");
            return chapters;
        }

        String[] paragraphs = content.split("\n\n+");
        StringBuilder currentChapter = new StringBuilder();
        int charCount = 0;
        int chapterSize = 3000;

        for (String para : paragraphs) {
            para = para.trim();
            if (para.isEmpty()) continue;
            if (para.length() < 3) continue;

            if (charCount + para.length() > chapterSize && charCount > 0) {
                chapters.add(currentChapter.toString());
                currentChapter = new StringBuilder();
                charCount = 0;
            }

            currentChapter.append("<p>").append(escapeHtml(para)).append("</p>\n");
            charCount += para.length();
        }

        if (charCount > 0) {
            chapters.add(currentChapter.toString());
        }

        if (chapters.isEmpty()) {
            chapters.add("<p>暂无内容</p>");
        }

        return chapters;
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        text = text.replace("&", "&amp;");
        text = text.replace("<", "&lt;");
        text = text.replace(">", "&gt;");
        text = text.replace("\"", "&quot;");
        return text;
    }

    private String extractCover() {
        try {
            int fileSize = (int) Math.min(mobiFile.length(), 2 * 1024 * 1024);
            byte[] buffer = new byte[fileSize];

            try (RandomAccessFile raf = new RandomAccessFile(mobiFile, "r")) {
                raf.seek(0);
                raf.readFully(buffer);
            }

            int jpgStart = findPattern(buffer, new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});
            if (jpgStart >= 0) {
                int jpgEnd = findPattern(buffer, new byte[]{(byte) 0xFF, (byte) 0xD9});
                if (jpgEnd >= jpgStart) {
                    int coverLen = jpgEnd - jpgStart + 2;
                    if (coverLen > 1000) {
                        byte[] coverBytes = new byte[coverLen];
                        System.arraycopy(buffer, jpgStart, coverBytes, 0, coverLen);

                        String fileName = "cover_" + System.currentTimeMillis() + ".jpg";
                        File coverFile = new File(FileUtils.getCacheDirectory(context), fileName);

                        try (OutputStream os = new FileOutputStream(coverFile)) {
                            os.write(coverBytes);
                        }

                        return coverFile.getAbsolutePath();
                    }
                }
            }

            int pngStart = findPattern(buffer, new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47});
            if (pngStart >= 0) {
                int pngEnd = findPattern(buffer, new byte[]{0x49, 0x45, 0x4E, 0x44, (byte) 0xAE, (byte) 0x42, (byte) 0x60, (byte) 0x82});
                if (pngEnd >= pngStart) {
                    int coverLen = pngEnd - pngStart + 8;
                    if (coverLen > 1000) {
                        byte[] coverBytes = new byte[coverLen];
                        System.arraycopy(buffer, pngStart, coverBytes, 0, coverLen);

                        String fileName = "cover_" + System.currentTimeMillis() + ".png";
                        File coverFile = new File(FileUtils.getCacheDirectory(context), fileName);

                        try (OutputStream os = new FileOutputStream(coverFile)) {
                            os.write(coverBytes);
                        }

                        return coverFile.getAbsolutePath();
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    private int findPattern(byte[] data, byte[] pattern) {
        return findPattern(data, pattern, 0);
    }

    private int findPattern(byte[] data, byte[] pattern, int startPos) {
        for (int i = startPos; i <= data.length - pattern.length; i++) {
            boolean match = true;
            for (int j = 0; j < pattern.length; j++) {
                if (data[i + j] != pattern[j]) {
                    match = false;
                    break;
                }
            }
            if (match) {
                return i;
            }
        }
        return -1;
    }

    private int readShortBE(RandomAccessFile raf) throws IOException {
        byte[] bytes = new byte[2];
        raf.readFully(bytes);
        return (bytes[0] & 0xFF) << 8 | (bytes[1] & 0xFF);
    }

    private int readIntBE(RandomAccessFile raf) throws IOException {
        byte[] bytes = new byte[4];
        raf.readFully(bytes);
        return (bytes[0] & 0xFF) << 24 | (bytes[1] & 0xFF) << 16 |
               (bytes[2] & 0xFF) << 8 | (bytes[3] & 0xFF);
    }

    private int readShortBE(InputStream is, int offset) throws IOException {
        is.mark(offset + 2);
        is.skip(offset);
        int b1 = is.read() & 0xFF;
        int b2 = is.read() & 0xFF;
        is.reset();
        return b1 << 8 | b2;
    }

    private int readIntBE(InputStream is, int offset) throws IOException {
        is.mark(offset + 4);
        is.skip(offset);
        int b1 = is.read() & 0xFF;
        int b2 = is.read() & 0xFF;
        int b3 = is.read() & 0xFF;
        int b4 = is.read() & 0xFF;
        is.reset();
        return b1 << 24 | b2 << 16 | b3 << 8 | b4;
    }

    public String getChapterContent(int chapterIndex) throws IOException {
        if (!parsed) {
            parse();
        }

        if (isComicMode) {
            if (chapterIndex >= 0 && chapterIndex < imagePaths.size()) {
                return createImagePage(imagePaths.get(chapterIndex));
            }
            return "<html><body style='background:#1A1A1A;color:#E0E0E0;padding:16px;'>暂无内容</body></html>";
        }

        if (cachedInfo == null || cachedInfo.chapters.isEmpty()) {
            return "<html><body style='background:#1A1A1A;color:#E0E0E0;padding:16px;'>暂无内容</body></html>";
        }

        if (chapterIndex < 0 || chapterIndex >= cachedInfo.chapters.size()) {
            return "";
        }

        String content = cachedInfo.chapters.get(chapterIndex);
        return wrapHtmlContent(content);
    }

    private String wrapHtmlContent(String bodyContent) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><head><meta charset=\"utf-8\">");
        html.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">");
        html.append("<style>");
        html.append("* { max-width: 100%; }");
        html.append("body { background-color: #1A1A1A; color: #E0E0E0; padding: 16px; line-height: 1.8; font-size: 18px; margin: 0; word-wrap: break-word; }");
        html.append("p { margin: 0.8em 0; text-indent: 2em; }");
        html.append("h1, h2, h3, h4, h5, h6 { color: #FFFFFF; margin: 1.2em 0 0.6em 0; text-indent: 0; }");
        html.append("</style></head><body>");
        html.append(bodyContent);
        html.append("</body></html>");
        return html.toString();
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

    public List<String> getImagePaths() {
        return imagePaths;
    }

    public static class MobiInfo {
        public String title = "";
        public String author = "";
        public String coverPath = null;
        public List<String> chapters = new ArrayList<>();
        public int totalPages = 0;
    }
}