package com.example.comicreader.adapter;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.example.comicreader.R;
import com.example.comicreader.model.ComicBook;
import com.example.comicreader.utils.EpubParser;
import com.example.comicreader.utils.FileUtils;
import com.example.comicreader.utils.ImageCacheManager;
import com.example.comicreader.utils.MobiParser;
import com.example.comicreader.utils.PdfRendererManager;
import com.example.comicreader.utils.TxtParser;
import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ComicReaderAdapter extends RecyclerView.Adapter<ComicReaderAdapter.PageViewHolder> {
    private Context context;
    private ComicBook comic;
    private List<File> imageFiles;
    private int screenWidth;
    private int spacingMode = 0;
    private int defaultMargin;
    private EpubParser epubParser;
    private MobiParser mobiParser;
    private TxtParser txtParser;
    private ExecutorService preloadExecutor;
    private static final int PRELOAD_RANGE = 3; // 预加载前后3页

    public ComicReaderAdapter(Context context, ComicBook comic) {
        this.context = context;
        this.comic = comic;
        this.screenWidth = context.getResources().getDisplayMetrics().widthPixels;
        this.defaultMargin = context.getResources().getDimensionPixelSize(R.dimen.page_spacing);
        this.preloadExecutor = Executors.newSingleThreadExecutor();
        initParsersAsync();
        loadPagesAsync();
    }

    private void initParsers() {
        String type = comic.getFileType();
        File file = new File(comic.getFilePath());
        if ("epub".equals(type)) {
            epubParser = new EpubParser(file, context);
            try {
                EpubParser.EpubInfo info = epubParser.parse();
                if (info != null && info.totalPages > 0) {
                    comic.setTotalPages(info.totalPages);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else if ("mobi".equals(type)) {
            mobiParser = new MobiParser(file, context);
            try {
                MobiParser.MobiInfo info = mobiParser.parse();
                if (info != null && info.totalPages > 0) {
                    comic.setTotalPages(info.totalPages);
                }
                java.util.List<String> imgPaths = mobiParser.getImagePaths();
                if (imgPaths != null && !imgPaths.isEmpty()) {
                    imageFiles = new java.util.ArrayList<>();
                    for (String path : imgPaths) {
                        imageFiles.add(new File(path));
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else if ("txt".equals(type)) {
            txtParser = new TxtParser(file, context);
            try {
                txtParser.parse();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void initParsersAsync() {
        new Thread(() -> {
            initParsers();
            if (context instanceof android.app.Activity) {
                ((android.app.Activity) context).runOnUiThread(() -> {
                    notifyDataSetChanged();
                });
            }
        }).start();
    }

    private void loadPages() {
        String filePath = comic.getFilePath();
        File file = new File(filePath);
        String type = comic.getFileType();

        if ("folder".equals(type)) {
            imageFiles = FileUtils.getImageFilesFromDirectory(file);
        } else if ("image".equals(type)) {
            imageFiles = new java.util.ArrayList<>();
            imageFiles.add(file);
        } else if ("pdf".equals(type)) {
            imageFiles = null;
        }
    }

    private void loadPagesAsync() {
        new Thread(() -> {
            loadPages();
            if (context instanceof android.app.Activity) {
                ((android.app.Activity) context).runOnUiThread(() -> {
                    notifyDataSetChanged();
                });
            }
        }).start();
    }

    public int getTotalPages() {
        if ("pdf".equals(comic.getFileType())) {
            return comic.getTotalPages();
        }
        if ("epub".equals(comic.getFileType())) {
            return 1;
        }
        if ("mobi".equals(comic.getFileType())) {
            if (imageFiles != null && !imageFiles.isEmpty()) {
                return imageFiles.size();
            }
            return comic.getTotalPages();
        }
        if ("txt".equals(comic.getFileType())) {
            if (txtParser != null) {
                int pages = txtParser.getTotalPages();
                if (pages != comic.getTotalPages()) {
                    comic.setTotalPages(pages);
                }
                return pages;
            }
            return comic.getTotalPages();
        }
        return imageFiles != null ? imageFiles.size() : 0;
    }

    public void setSpacingMode(int mode) {
        this.spacingMode = mode;
    }

    public int getSpacingMode() {
        return spacingMode;
    }

    public int getDefaultMargin() {
        return defaultMargin;
    }

    public void onConfigurationChanged(int newWidth) {
        this.screenWidth = newWidth;
        notifyDataSetChanged();
    }

    public EpubParser getEpubParser() {
        return epubParser;
    }

    public void scrollToChapter(int chapterIndex) {
        if ("epub".equals(comic.getFileType())) {
            for (PageViewHolder holder : activeViewHolders) {
                if (holder.viewType == 1) {
                    holder.scrollToChapter(chapterIndex);
                    return;
                }
            }
        }
    }

    private List<PageViewHolder> activeViewHolders = new java.util.ArrayList<>();
    private OnScrollProgressListener scrollProgressListener;
    
    public interface OnScrollProgressListener {
        void onScrollProgress(int progress);
    }
    
    public void setOnScrollProgressListener(OnScrollProgressListener listener) {
        this.scrollProgressListener = listener;
    }

    @Override
    public int getItemViewType(int position) {
        String type = comic.getFileType();
        if ("epub".equals(type)) {
            return 1;
        }
        if ("mobi".equals(type)) {
            if (imageFiles != null && !imageFiles.isEmpty()) {
                return 0;
            }
            return 2;
        }
        if ("txt".equals(type)) {
            return 3;
        }
        return 0;
    }

    @NonNull
    @Override
    public PageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view;
        if (viewType == 1 || viewType == 2 || viewType == 3) {
            view = LayoutInflater.from(context).inflate(R.layout.item_page_webview, parent, false);
        } else {
            view = LayoutInflater.from(context).inflate(R.layout.item_page, parent, false);
        }
        return new PageViewHolder(view, viewType);
    }

    @Override
    public void onBindViewHolder(@NonNull PageViewHolder holder, int position) {
        activeViewHolders.add(holder);
        holder.bind(position);
        preloadPages(position);
    }

    @Override
    public void onViewRecycled(@NonNull PageViewHolder holder) {
        activeViewHolders.remove(holder);
        super.onViewRecycled(holder);
        if (holder.viewType == 1 || holder.viewType == 2 || holder.viewType == 3) {
            if (holder.webView != null) {
                holder.webView.stopLoading();
                holder.webView.loadUrl("about:blank");
            }
        } else {
            holder.imageView.setImageDrawable(null);
            holder.imageView.setImageBitmap(null);
        }
        if (holder.loadThread != null && holder.loadThread.isAlive()) {
            holder.loadThread.interrupt();
        }
    }

    @Override
    public int getItemCount() {
        return getTotalPages();
    }

    /**
     * 预加载前后3页的图片到缓存
     * 只对图片类型（folder/image/pdf）进行预加载
     */
    private void preloadPages(int currentPosition) {
        String type = comic.getFileType();
        // 只预加载图片类型的页面，不预加载 epub/mobi/txt
        if ("epub".equals(type) || "mobi".equals(type) || "txt".equals(type)) {
            return;
        }

        int totalPages = getTotalPages();
        ImageCacheManager cacheManager = ImageCacheManager.getInstance(context);

        // 计算预加载范围
        int start = Math.max(0, currentPosition - PRELOAD_RANGE);
        int end = Math.min(totalPages - 1, currentPosition + PRELOAD_RANGE);

        // 在后台线程中预加载
        preloadExecutor.execute(() -> {
            for (int i = start; i <= end; i++) {
                // 跳过当前页面（已经在加载中）
                if (i == currentPosition) {
                    continue;
                }

                try {
                    File imageFile = null;

                    if ("pdf".equals(type)) {
                        // PDF 文件预加载
                        File pdfFile = new File(comic.getFilePath());
                        PdfRendererManager manager = PdfRendererManager.getInstance(pdfFile, context);
                        File cached = manager.getCachedPageFile(i);
                        if (cached.exists()) {
                            imageFile = cached;
                        } else {
                            // 渲染并缓存PDF页面
                            imageFile = manager.renderAndCachePage(i, screenWidth);
                        }
                    } else if (imageFiles != null && i < imageFiles.size()) {
                        // 图片文件/文件夹
                        imageFile = imageFiles.get(i);
                    }

                    // 预加载图片到缓存
                    if (imageFile != null && imageFile.exists()) {
                        cacheManager.preloadBitmap(imageFile.getAbsolutePath());
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    /**
     * 清理资源，关闭预加载线程池
     */
    public void cleanup() {
        if (preloadExecutor != null && !preloadExecutor.isShutdown()) {
            preloadExecutor.shutdown();
        }
    }

    class PageViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;
        WebView webView;
        Thread loadThread;
        int viewType;

        PageViewHolder(@NonNull View itemView, int viewType) {
            super(itemView);
            this.viewType = viewType;
            if (viewType == 1 || viewType == 2 || viewType == 3) {
                webView = itemView.findViewById(R.id.page_webview);
                webView.getSettings().setJavaScriptEnabled(true);
                webView.getSettings().setUseWideViewPort(true);
                webView.getSettings().setLoadWithOverviewMode(true);
                webView.getSettings().setAllowFileAccess(true);
                webView.setBackgroundColor(ContextCompat.getColor(context, R.color.background));
                
                if (viewType == 1) {
                    webView.setWebViewClient(new WebViewClient() {
                        @Override
                        public void onPageFinished(WebView view, String url) {
                            super.onPageFinished(view, url);
                            setupScrollListener();
                        }
                    });
                }
            } else {
                imageView = (ImageView) itemView;
            }
        }
        
        private void setupScrollListener() {
            webView.setOnScrollChangeListener(new View.OnScrollChangeListener() {
                @Override
                public void onScrollChange(View v, int scrollX, int scrollY, int oldScrollX, int oldScrollY) {
                    if (scrollProgressListener != null) {
                        float contentHeight = webView.getContentHeight() * webView.getScale();
                        int viewHeight = webView.getHeight();
                        if (contentHeight > viewHeight) {
                            float scrollRange = contentHeight - viewHeight;
                            int progress = scrollRange > 0 ? (int) ((scrollY * 100.0) / scrollRange) : 0;
                            progress = Math.max(0, Math.min(100, progress));
                            scrollProgressListener.onScrollProgress(progress);
                        }
                    }
                }
            });
        }

        void bind(int position) {
            if (viewType == 1) {
                bindEpub(position);
                return;
            }
            if (viewType == 2) {
                bindMobi(position);
                return;
            }
            if (viewType == 3) {
                bindTxt(position);
                return;
            }

            imageView.setImageBitmap(null);

            if (loadThread != null && loadThread.isAlive()) {
                loadThread.interrupt();
            }

            final int pageIndex = position;

            loadThread = new Thread(() -> {
                try {
                    if (Thread.interrupted()) return;

                    File imageFile = null;
                    String type = comic.getFileType();

                    if ("pdf".equals(type)) {
                        File pdfFile = new File(comic.getFilePath());
                        PdfRendererManager manager = PdfRendererManager.getInstance(pdfFile, context);
                        File cached = manager.getCachedPageFile(pageIndex);
                        if (cached.exists()) {
                            imageFile = cached;
                        } else {
                            imageFile = manager.renderAndCachePage(pageIndex, screenWidth);
                        }
                    } else if (imageFiles != null && pageIndex < imageFiles.size()) {
                        imageFile = imageFiles.get(pageIndex);
                    }

                    if (Thread.interrupted()) return;

                    if (imageFile != null && imageFile.exists()) {
                        final Bitmap bitmap = decodeSampledBitmap(imageFile, screenWidth);
                        if (bitmap != null && !Thread.interrupted()) {
                            final Bitmap finalBitmap = bitmap;
                            imageView.post(() -> {
                                RecyclerView.LayoutParams params =
                                        (RecyclerView.LayoutParams) imageView.getLayoutParams();
                                params.width = screenWidth;
                                params.height = finalBitmap.getHeight();
                                imageView.setLayoutParams(params);
                                imageView.setImageBitmap(finalBitmap);
                            });
                            return;
                        }
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            loadThread.start();
        }

        void bindEpub(int position) {
            if (webView == null) return;
            webView.stopLoading();
            webView.loadUrl("about:blank");

            loadThread = new Thread(() -> {
                try {
                    Thread.sleep(100);
                    if (Thread.interrupted()) return;

                    String content = "";
                    if (epubParser != null) {
                        content = epubParser.getFullContent();
                    }

                    if (Thread.interrupted()) return;

                    final String finalContent = content;
                    webView.post(() -> {
                        if (webView != null && finalContent != null && !finalContent.isEmpty()) {
                            webView.loadDataWithBaseURL("file:///", finalContent, "text/html", "UTF-8", null);
                        } else {
                            webView.loadData("<html><body style='background:#1A1A1A;color:#E0E0E0;padding:16px;'>加载失败</body></html>", "text/html", "UTF-8");
                        }
                    });

                } catch (Exception e) {
                    e.printStackTrace();
                    webView.post(() -> {
                        webView.loadData("<html><body style='background:#1A1A1A;color:#E0E0E0;padding:16px;'>加载失败</body></html>", "text/html", "UTF-8");
                    });
                }
            });
            loadThread.start();
        }

        void scrollToChapter(int chapterIndex) {
            if (webView != null && epubParser != null) {
                String anchorId = "chapter_" + chapterIndex;
                webView.loadUrl("javascript:var el = document.getElementById('" + anchorId + "'); if(el) { el.scrollIntoView(true); } else { setTimeout(function() { var el2 = document.getElementById('" + anchorId + "'); if(el2) el2.scrollIntoView(true); }, 500); }");
            }
        }

        void bindMobi(int position) {
            if (webView == null) return;
            webView.stopLoading();
            webView.loadUrl("about:blank");

            final int pageIndex = position;

            loadThread = new Thread(() -> {
                try {
                    Thread.sleep(100);
                    if (Thread.interrupted()) return;

                    String content = "";
                    if (mobiParser != null) {
                        content = mobiParser.getChapterContent(pageIndex);
                    }

                    if (Thread.interrupted()) return;

                    final String finalContent = content;
                    webView.post(() -> {
                        if (webView != null && finalContent != null && !finalContent.isEmpty()) {
                            webView.loadDataWithBaseURL(null, finalContent, "text/html", "UTF-8", null);
                        }
                    });

                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            loadThread.start();
        }

        void bindTxt(int position) {
            if (webView == null) return;
            webView.stopLoading();
            webView.loadUrl("about:blank");

            final int pageIndex = position;

            loadThread = new Thread(() -> {
                try {
                    Thread.sleep(100);
                    if (Thread.interrupted()) return;

                    String content = "";
                    if (txtParser != null) {
                        content = txtParser.getChapterContent(pageIndex);
                    }

                    if (Thread.interrupted()) return;

                    final String finalContent = content;
                    webView.post(() -> {
                        if (webView != null && finalContent != null && !finalContent.isEmpty()) {
                            webView.loadDataWithBaseURL(null, finalContent, "text/html", "UTF-8", null);
                        }
                    });

                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            loadThread.start();
        }

        private Bitmap decodeSampledBitmap(File file, int reqWidth) {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), options);

            int imgWidth = options.outWidth;
            int imgHeight = options.outHeight;
            if (imgWidth <= 0 || imgHeight <= 0) {
                return null;
            }

            int inSampleSize = 1;
            if (imgWidth > reqWidth) {
                inSampleSize = imgWidth / reqWidth;
            }

            options.inJustDecodeBounds = false;
            options.inSampleSize = inSampleSize;
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;

            Bitmap decoded = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
            if (decoded == null) return null;

            if (decoded.getWidth() != reqWidth) {
                float ratio = (float) reqWidth / decoded.getWidth();
                int targetHeight = (int) (decoded.getHeight() * ratio);
                Bitmap scaled = Bitmap.createScaledBitmap(decoded, reqWidth, targetHeight, true);
                if (scaled != decoded) {
                    decoded.recycle();
                }
                return scaled;
            }
            return decoded;
        }
    }
}