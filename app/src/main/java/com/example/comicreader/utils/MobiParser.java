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

    public MobiParser(File mobiFile, Context context) {
        this.mobiFile = mobiFile;
        this.context = context;
    }

    public MobiInfo parse() throws IOException {
        if (parsed && cachedInfo != null) {
            return cachedInfo;
        }

        MobiInfo info = new MobiInfo();

        try (RandomAccessFile raf = new RandomAccessFile(mobiFile, "r")) {
            raf.seek(0);
            byte[] header = new byte[64];
            int bytesRead = raf.read(header);
            if (bytesRead < 16) {
                throw new IOException("文件太小，不是有效的MOBI文件");
            }

            String identifier = new String(header, 0, Math.min(bytesRead, 8), StandardCharsets.US_ASCII).trim();

            int palmHeaderSize = 76;
            int mobiHeaderOffset = 76;

            if (identifier.startsWith("BOOKMOBI")) {
                palmHeaderSize = 76;
            } else if (identifier.startsWith("MOBI") || identifier.startsWith("TEXt")) {
                palmHeaderSize = 76;
            } else {
                boolean isPalmDoc = checkPalmDocHeader(header);
                if (!isPalmDoc) {
                    throw new IOException("不是有效的MOBI文件");
                }
            }

            raf.seek(60);
            int titleOffset = readInt(raf);
            int titleLength = readInt(raf);

            raf.seek(84);
            int authorOffset = readInt(raf);
            int authorLength = readInt(raf);

            if (titleOffset > 0 && titleLength > 0 && titleOffset + titleLength <= mobiFile.length()) {
                raf.seek(titleOffset);
                byte[] titleBytes = new byte[titleLength];
                raf.readFully(titleBytes);
                info.title = new String(titleBytes, StandardCharsets.UTF_8).trim();
            }

            if (authorOffset > 0 && authorLength > 0 && authorOffset + authorLength <= mobiFile.length()) {
                raf.seek(authorOffset);
                byte[] authorBytes = new byte[authorLength];
                raf.readFully(authorBytes);
                info.author = new String(authorBytes, StandardCharsets.UTF_8).trim();
            }

            raf.seek(12);
            int numSections = readShort(raf);

            int textOffset = 0;
            int textLength = 0;

            for (int i = 0; i < numSections; i++) {
                int sectionOffsetPos = palmHeaderSize + i * 8;
                if (sectionOffsetPos + 8 > mobiFile.length()) break;
                raf.seek(sectionOffsetPos);
                int sectionOffset = readInt(raf);
                int sectionType = readInt(raf);

                if (i == 0) {
                    textOffset = sectionOffset;
                }
            }

            if (textOffset > 0 && textOffset < mobiFile.length()) {
                raf.seek(textOffset);
                int record0Size = 0;
                if (numSections > 1) {
                    int nextOffsetPos = palmHeaderSize + 8;
                    if (nextOffsetPos + 4 <= mobiFile.length()) {
                        raf.seek(nextOffsetPos);
                        int nextOffset = readInt(raf);
                        record0Size = nextOffset - textOffset;
                    }
                } else {
                    record0Size = (int) (mobiFile.length() - textOffset);
                }

                if (record0Size > 0 && record0Size < 10 * 1024 * 1024) {
                    if (textOffset + record0Size > mobiFile.length()) {
                        record0Size = (int) (mobiFile.length() - textOffset);
                    }
                    raf.seek(textOffset);
                    byte[] textBytes = new byte[record0Size];
                    raf.readFully(textBytes);

                    String content = decodeMobiText(textBytes);
                    info.chapters = extractChapters(content);
                }
            }

            if (info.chapters.isEmpty()) {
                extractTextFromRecords(raf, numSections, palmHeaderSize, info);
            }

            info.totalPages = info.chapters.size();
            if (info.totalPages == 0) {
                info.totalPages = 1;
                info.chapters.add("");
            }

            info.coverPath = extractCover();

        }

        cachedInfo = info;
        parsed = true;
        return info;
    }

    private boolean checkPalmDocHeader(byte[] header) {
        if (header.length < 32) return false;

        int version = (header[0] & 0xFF) << 8 | (header[1] & 0xFF);
        int creationDate = (header[2] & 0xFF) << 24 | (header[3] & 0xFF) << 16 |
                           (header[4] & 0xFF) << 8 | (header[5] & 0xFF);

        int dbType = (header[32] & 0xFF) << 24 | (header[33] & 0xFF) << 16 |
                     (header[34] & 0xFF) << 8 | (header[35] & 0xFF);

        int creator = (header[36] & 0xFF) << 24 | (header[37] & 0xFF) << 16 |
                      (header[38] & 0xFF) << 8 | (header[39] & 0xFF);

        return (dbType == 0x4D4F4249 || dbType == 0x54455874 ||
                creator == 0x4B494E44 || creator == 0x4D4F4249);
    }

    private String decodeMobiText(byte[] data) {
        try {
            return new String(data, StandardCharsets.UTF_8);
        } catch (Exception e) {
            try {
                return new String(data, "ISO-8859-1");
            } catch (Exception e2) {
                return new String(data);
            }
        }
    }

    private void extractTextFromRecords(RandomAccessFile raf, int numSections, int palmHeaderSize, MobiInfo info) {
        try {
            StringBuilder fullContent = new StringBuilder();

            for (int i = 0; i < numSections; i++) {
                int sectionOffsetPos = palmHeaderSize + i * 8;
                if (sectionOffsetPos + 8 > mobiFile.length()) break;
                raf.seek(sectionOffsetPos);
                int sectionOffset = readInt(raf);
                int sectionType = readInt(raf);

                if (sectionOffset <= 0) continue;

                int nextOffset;
                if (i + 1 < numSections) {
                    int nextOffsetPos = palmHeaderSize + (i + 1) * 8;
                    if (nextOffsetPos + 4 <= mobiFile.length()) {
                        raf.seek(nextOffsetPos);
                        nextOffset = readInt(raf);
                    } else {
                        nextOffset = (int) mobiFile.length();
                    }
                } else {
                    nextOffset = (int) mobiFile.length();
                }

                int recordSize = nextOffset - sectionOffset;
                if (recordSize <= 0 || recordSize > 1024 * 1024) continue;

                raf.seek(sectionOffset);
                byte[] recordData = new byte[recordSize];
                raf.readFully(recordData);

                String recordText = decodeMobiText(recordData);
                fullContent.append(recordText);
                fullContent.append("\n");
            }

            if (fullContent.length() > 0) {
                info.chapters = extractChapters(fullContent.toString());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private List<String> extractChapters(String content) {
        List<String> chapters = new ArrayList<>();

        content = stripHtmlTags(content);
        content = content.replaceAll("\\r\\n", "\n");
        content = content.replaceAll("\\r", "\n");

        String[] paragraphs = content.split("\n\n+");
        StringBuilder currentChapter = new StringBuilder();
        int charCount = 0;
        int chapterSize = 3000;

        for (String para : paragraphs) {
            para = para.trim();
            if (para.isEmpty()) continue;

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

    private String stripHtmlTags(String html) {
        if (html == null) return "";
        html = html.replaceAll("<br\\s*/?>", "\n");
        html = html.replaceAll("</p>", "\n\n");
        html = html.replaceAll("<[^>]*>", "");
        html = html.replaceAll("&nbsp;", " ");
        html = html.replaceAll("&amp;", "&");
        html = html.replaceAll("&lt;", "<");
        html = html.replaceAll("&gt;", ">");
        html = html.replaceAll("&quot;", "\"");
        return html.trim();
    }

    private String escapeHtml(String text) {
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
        for (int i = 0; i <= data.length - pattern.length; i++) {
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

    private int readShort(RandomAccessFile raf) throws IOException {
        byte[] bytes = new byte[2];
        raf.readFully(bytes);
        return (bytes[0] & 0xFF) << 8 | (bytes[1] & 0xFF);
    }

    private int readInt(RandomAccessFile raf) throws IOException {
        byte[] bytes = new byte[4];
        raf.readFully(bytes);
        return (bytes[0] & 0xFF) << 24 | (bytes[1] & 0xFF) << 16 | (bytes[2] & 0xFF) << 8 | (bytes[3] & 0xFF);
    }

    public String getChapterContent(int chapterIndex) throws IOException {
        if (!parsed) {
            parse();
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

    public static class MobiInfo {
        public String title = "";
        public String author = "";
        public String coverPath = null;
        public List<String> chapters = new ArrayList<>();
        public int totalPages = 0;
    }
}
