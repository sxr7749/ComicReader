package com.example.comicreader;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.LocaleList;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.example.comicreader.view.VerticalSeekBar;
import com.example.comicreader.view.SpacingItemDecoration;
import com.example.comicreader.view.ZoomableFrameLayout;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;
import com.example.comicreader.adapter.ComicReaderAdapter;
import com.example.comicreader.adapter.TocAdapter;
import com.example.comicreader.model.AppSettings;
import com.example.comicreader.model.ComicBook;
import com.example.comicreader.utils.EpubParser;
import com.example.comicreader.utils.PdfRendererManager;
import com.example.comicreader.utils.ReadingStatsManager;
import com.example.comicreader.viewmodel.ComicViewModel;
import com.google.android.material.appbar.MaterialToolbar;
import java.util.List;
import java.util.Locale;

public class ReaderActivity extends AppCompatActivity {
    private static final String EXTRA_COMIC_ID = "comicId";

    private ComicViewModel viewModel;
    private AppSettings settings;
    private RecyclerView recyclerView;
    private ViewPager2 viewPager;
    private ComicReaderAdapter adapter;
    private ComicBook comic;
    private LinearLayoutManager layoutManager;
    private List<ComicBook> allComics;
    private int currentComicIndex = -1;

    private LinearLayout topBar;
    private LinearLayout bottomBar;
    private LinearLayout brightnessPanel;
    private LinearLayout scrollPanel;
    private LinearLayout scrollPanelBg;
    private SeekBar brightnessSeekBar;
    private VerticalSeekBar scrollSeekBar;
    private View progressIndicator;
    private ZoomableFrameLayout zoomContainer;
    private boolean barsVisible = true;
    private boolean isGrayscale = false;
    private int spacingMode = 0;

    private LinearLayout tocPanel;
    private LinearLayout tocPanelBg;
    private RecyclerView tocRecycler;
    private TocAdapter tocAdapter;

    private boolean isVerticalMode = true;
    private ViewPager2.OnPageChangeCallback pageChangeCallback;

    // 音量键翻页和自动翻页
    private boolean volumeKeyPageTurn = true; // 默认启用音量键翻页
    private boolean autoPageTurnEnabled = false; // 自动翻页开关
    private int autoPageTurnInterval = 5000; // 自动翻页间隔（毫秒）
    private Handler autoPageHandler = new Handler();
    private Runnable autoPageRunnable;
    private ImageButton autoPageButton; // 自动翻页按钮

    // 阅读统计
    private ReadingStatsManager statsManager;
    private int lastPagePosition = -1; // 记录上次页码，用于判断是否翻页

    @Override
    protected void attachBaseContext(Context newBase) {
        ComicApplication app = (ComicApplication) newBase.getApplicationContext();
        AppSettings settings = app.getDatabase().getSettings();
        Context context = newBase;
        if (settings != null && settings.getLanguage() != null) {
            Locale locale;
            switch (settings.getLanguage()) {
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
            Resources res = newBase.getResources();
            Configuration config = res.getConfiguration();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                config.setLocales(new LocaleList(locale));
                context = newBase.createConfigurationContext(config);
            } else {
                config.locale = locale;
                res.updateConfiguration(config, res.getDisplayMetrics());
            }
        }
        super.attachBaseContext(context);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setContentView(R.layout.activity_reader);

            viewModel = new ViewModelProvider(this).get(ComicViewModel.class);
            settings = viewModel.getSettings();

            String comicId = getIntent().getStringExtra(EXTRA_COMIC_ID);
            if (comicId != null) {
                comic = viewModel.getComicById(comicId);
            }

            if (comic == null) {
                Toast.makeText(this, "漫画不存在", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }

            viewModel.getComics().observe(this, comics -> {
                allComics = comics;
                if (allComics != null) {
                    findCurrentIndex();
                    setupFileNavigation();
                }
            });

            initViews();
            setupReader();
            setupTitle();
            setupBrightnessControl();
            setupGrayscaleControl();
            setupSpacingControl();
            setupZoomControl();
            setupProgressControl();
            setupScrollBar();
            setupAutoPageTurn();

            statsManager = new ReadingStatsManager(this);
            statsManager.startReading();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "打开漫画失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void findCurrentIndex() {
        if (allComics != null) {
            for (int i = 0; i < allComics.size(); i++) {
                if (allComics.get(i).getId().equals(comic.getId())) {
                    currentComicIndex = i;
                    break;
                }
            }
        }
    }

    private void initViews() {
        recyclerView = findViewById(R.id.reader_recycler);
        viewPager = findViewById(R.id.reader_pager);
        topBar = findViewById(R.id.top_bar);
        bottomBar = findViewById(R.id.bottom_bar);
        brightnessPanel = findViewById(R.id.brightness_panel);
        scrollPanel = findViewById(R.id.scroll_panel);
        scrollPanelBg = findViewById(R.id.scroll_panel_bg);
        brightnessSeekBar = findViewById(R.id.brightness_seekbar);
        scrollSeekBar = findViewById(R.id.scroll_seekbar);
        progressIndicator = findViewById(R.id.progress_indicator);
        zoomContainer = findViewById(R.id.zoom_container);

        MaterialToolbar toolbar = findViewById(R.id.reader_toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        findViewById(R.id.btn_settings).setOnClickListener(v -> openSettings());

        autoPageButton = findViewById(R.id.btn_auto_page);
        autoPageButton.setOnClickListener(v -> toggleAutoPageTurn());

        tocPanel = findViewById(R.id.toc_panel);
        tocPanelBg = findViewById(R.id.toc_panel_bg);
        tocRecycler = findViewById(R.id.toc_recycler);

        findViewById(R.id.btn_toc).setOnClickListener(v -> toggleToc());
        findViewById(R.id.btn_close_toc).setOnClickListener(v -> closeToc());
        tocPanelBg.setOnClickListener(v -> closeToc());
    }

    private void setupTitle() {
        TextView titleView = findViewById(R.id.reader_title);
        titleView.setText(comic.getTitle());
        updatePageInfo(comic.getCurrentPage());
    }

    private void setupFileNavigation() {
        View btnTopPrev = findViewById(R.id.btn_top_prev_file);
        View btnTopNext = findViewById(R.id.btn_top_next_file);

        if (allComics != null) {
            if (currentComicIndex > 0) {
                btnTopPrev.setVisibility(View.VISIBLE);
                btnTopPrev.setOnClickListener(v -> switchToFile(currentComicIndex - 1));
            }

            if (currentComicIndex < allComics.size() - 1) {
                btnTopNext.setVisibility(View.VISIBLE);
                btnTopNext.setOnClickListener(v -> switchToFile(currentComicIndex + 1));
            }
        }
    }

    private void switchToFile(int index) {
        if (index < 0 || index >= allComics.size()) return;

        ComicBook nextComic = allComics.get(index);
        Intent intent = new Intent(this, ReaderActivity.class);
        intent.putExtra(EXTRA_COMIC_ID, nextComic.getId());
        startActivity(intent);
        finish();
    }

    private void setupBrightnessControl() {
        float currentBrightness = getWindow().getAttributes().screenBrightness;
        if (currentBrightness <= 0) currentBrightness = 1.0f;
        brightnessSeekBar.setProgress((int) (currentBrightness * 255));

        brightnessSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float brightness = progress / 255.0f;
                WindowManager.LayoutParams layoutParams = getWindow().getAttributes();
                layoutParams.screenBrightness = brightness;
                getWindow().setAttributes(layoutParams);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        findViewById(R.id.btn_brightness).setOnClickListener(v -> {
            if (brightnessPanel.getVisibility() == View.VISIBLE) {
                brightnessPanel.setVisibility(View.GONE);
            } else {
                brightnessPanel.setVisibility(View.VISIBLE);
            }
        });

        findViewById(R.id.reader_recycler).setOnClickListener(v -> {
            if (brightnessPanel.getVisibility() == View.VISIBLE) {
                brightnessPanel.setVisibility(View.GONE);
            }
            if (scrollPanel.getVisibility() == View.VISIBLE) {
                scrollPanel.setVisibility(View.GONE);
                scrollPanelBg.setVisibility(View.GONE);
            }
        });

        viewPager.setOnClickListener(v -> {
            if (brightnessPanel.getVisibility() == View.VISIBLE) {
                brightnessPanel.setVisibility(View.GONE);
            }
            if (scrollPanel.getVisibility() == View.VISIBLE) {
                scrollPanel.setVisibility(View.GONE);
                scrollPanelBg.setVisibility(View.GONE);
            }
        });
    }

    private void setupGrayscaleControl() {
        findViewById(R.id.btn_grayscale).setOnClickListener(v -> {
            isGrayscale = !isGrayscale;
            applyGrayscaleFilter(isGrayscale);
            Toast.makeText(this, isGrayscale ? "黑白模式" : "彩色模式", Toast.LENGTH_SHORT).show();
        });
    }

    private void applyGrayscaleFilter(boolean grayscale) {
        ColorMatrix colorMatrix = new ColorMatrix();
        if (grayscale) {
            colorMatrix.setSaturation(0);
        } else {
            colorMatrix.setSaturation(1);
        }

        android.graphics.Paint paint = new android.graphics.Paint();
        paint.setColorFilter(new ColorMatrixColorFilter(colorMatrix));
        getWindow().getDecorView().setLayerType(View.LAYER_TYPE_HARDWARE, paint);
    }

    private void setupSpacingControl() {
        findViewById(R.id.btn_spacing).setOnClickListener(v -> {
            if (!isVerticalMode) {
                Toast.makeText(this, "横版模式不支持无间距", Toast.LENGTH_SHORT).show();
                return;
            }
            spacingMode = (spacingMode + 1) % 2;
            if (spacingDecoration != null) {
                spacingDecoration.setSpacingEnabled(spacingMode == 0);
                recyclerView.invalidateItemDecorations();
            }
            String mode = spacingMode == 0 ? "默认间距" : "无间距";
            Toast.makeText(this, mode, Toast.LENGTH_SHORT).show();
        });
    }

    private void setupZoomControl() {
        findViewById(R.id.btn_zoom_in).setOnClickListener(v -> {
            float currentZoom = settings.getZoomLevel();
            float newZoom = Math.min(currentZoom + 0.2f, 3.0f);
            settings.setZoomLevel(newZoom);
            applyZoom(newZoom);
            Toast.makeText(this, String.format("缩放: %.0f%%", newZoom * 100), Toast.LENGTH_SHORT).show();
        });

        findViewById(R.id.btn_zoom_out).setOnClickListener(v -> {
            float currentZoom = settings.getZoomLevel();
            float newZoom = Math.max(currentZoom - 0.2f, 0.5f);
            settings.setZoomLevel(newZoom);
            applyZoom(newZoom);
            Toast.makeText(this, String.format("缩放: %.0f%%", newZoom * 100), Toast.LENGTH_SHORT).show();
        });
    }

    private void applyZoom(float zoom) {
        if (zoomContainer != null) {
            zoomContainer.setScale(zoom);
        }
    }

    private void setupProgressControl() {
        progressIndicator.setOnClickListener(v -> {
            if (scrollPanel.getVisibility() == View.VISIBLE) {
                scrollPanel.setVisibility(View.GONE);
                scrollPanelBg.setVisibility(View.GONE);
                progressIndicator.setVisibility(View.VISIBLE);
                progressIndicator.setBackgroundResource(R.drawable.ic_progress_button);
                progressIndicator.setTranslationX(18f);
            } else {
                scrollPanel.setVisibility(View.VISIBLE);
                scrollPanelBg.setVisibility(View.VISIBLE);
                progressIndicator.setVisibility(View.GONE);
            }
        });
    }

    private void setupScrollBar() {
        scrollSeekBar.setOnProgressChangeListener(new VerticalSeekBar.OnProgressChangeListener() {
            @Override
            public void onProgressChanged(int progress) {
                int totalPages = adapter != null ? adapter.getTotalPages() : 0;
                if (totalPages > 0) {
                    int targetPage = (int) ((progress / 100.0) * (totalPages - 1));
                    scrollToPage(targetPage);
                }
            }

            @Override
            public void onStartTrackingTouch() {}

            @Override
            public void onStopTrackingTouch() {}
        });
    }

    private void scrollToPage(int page) {
        boolean isVertical = AppSettings.READ_MODE_VERTICAL.equals(settings.getReadMode());
        if (isVertical && layoutManager != null) {
            layoutManager.scrollToPosition(page);
        } else if (viewPager != null) {
            viewPager.setCurrentItem(page, false);
        }
    }

    private void setupReader() {
        int currentPage = comic.getCurrentPage();
        if (layoutManager != null) {
            int pos = layoutManager.findFirstVisibleItemPosition();
            if (pos >= 0) currentPage = pos;
        } else if (viewPager != null && viewPager.getVisibility() == View.VISIBLE) {
            currentPage = viewPager.getCurrentItem();
        }

        if (pageChangeCallback != null) {
            viewPager.unregisterOnPageChangeCallback(pageChangeCallback);
            pageChangeCallback = null;
        }

        boolean isVertical = AppSettings.READ_MODE_VERTICAL.equals(settings.getReadMode());
        isVerticalMode = isVertical;

        if (isVertical) {
            setupVerticalReader(currentPage);
        } else {
            setupHorizontalReader(currentPage);
        }
    }

    private SpacingItemDecoration spacingDecoration;

    private void setupVerticalReader(int currentPage) {
        recyclerView.setVisibility(View.VISIBLE);
        viewPager.setVisibility(View.GONE);

        layoutManager = new LinearLayoutManager(this);
        layoutManager.setOrientation(LinearLayoutManager.VERTICAL);
        recyclerView.setLayoutManager(layoutManager);

        adapter = new ComicReaderAdapter(this, comic);
        adapter.setSpacingMode(spacingMode);
        setupEpubScrollListener(adapter);
        recyclerView.setAdapter(adapter);

        int defaultMargin = getResources().getDimensionPixelSize(R.dimen.page_spacing);
        spacingDecoration = new SpacingItemDecoration(defaultMargin);
        spacingDecoration.setSpacingEnabled(spacingMode == 0);
        recyclerView.addItemDecoration(spacingDecoration);

        findViewById(R.id.btn_spacing).setVisibility(View.VISIBLE);

        recyclerView.post(() -> {
            layoutManager.scrollToPosition(currentPage);
            updateScrollBar(currentPage);
        });

        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView rv, int dx, int dy) {
                int position = layoutManager.findFirstVisibleItemPosition();
                if (position >= 0) {
                    updatePageInfo(position);
                    updateScrollBar(position);
                }
            }

            @Override
            public void onScrollStateChanged(RecyclerView rv, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    int position = layoutManager.findFirstVisibleItemPosition();
                    if (position >= 0) {
                        saveProgress(position);
                    }
                }
            }
        });

        recyclerView.addOnItemTouchListener(new com.example.comicreader.view.TapGestureListener(this,
                this::handleTap));
    }

    private void setupHorizontalReader(int currentPage) {
        recyclerView.setVisibility(View.GONE);
        viewPager.setVisibility(View.VISIBLE);

        viewPager.setOrientation(ViewPager2.ORIENTATION_HORIZONTAL);
        viewPager.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);

        adapter = new ComicReaderAdapter(this, comic);
        // 横版模式强制使用默认间距，避免无间距模式导致崩溃
        adapter.setSpacingMode(0);
        spacingMode = 0;
        viewPager.setAdapter(adapter);
        viewPager.setCurrentItem(currentPage, false);
        updateScrollBar(currentPage);
        updatePageInfo(currentPage);

        // 隐藏无间距按钮（横版模式不需要）
        findViewById(R.id.btn_spacing).setVisibility(View.GONE);

        pageChangeCallback = new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                updatePageInfo(position);
                updateScrollBar(position);
                saveProgress(position);
            }
        };
        viewPager.registerOnPageChangeCallback(pageChangeCallback);
    }

    private void updatePageInfo(int page) {
        int total = adapter != null ? adapter.getTotalPages() : 0;
        if (total <= 0) return;

        TextView pageInfo = findViewById(R.id.page_info);
        pageInfo.setText(String.format("第 %d 页 / 共 %d 页", page + 1, total));

        TextView pagePercent = findViewById(R.id.page_percent);
        int percent = (int) ((page + 1) * 100.0 / total);
        pagePercent.setText(percent + "%");

        ProgressBar progressBar = findViewById(R.id.reader_progress);
        progressBar.setProgress(percent);

        // 检测翻页并记录统计
        if (lastPagePosition != -1 && lastPagePosition != page) {
            statsManager.onPageTurn();
        }
        lastPagePosition = page;
    }

    private void updateScrollBar(int page) {
        int total = adapter != null ? adapter.getTotalPages() : 0;
        if (total <= 0) return;

        int percent = (int) ((page + 1) * 100.0 / total);
        scrollSeekBar.setProgress(percent);

        progressIndicator.post(() -> {
            int topBarHeight = topBar.getVisibility() == View.VISIBLE ? topBar.getHeight() : 0;
            int bottomBarHeight = bottomBar.getVisibility() == View.VISIBLE ? bottomBar.getHeight() : 0;
            int indicatorHeight = progressIndicator.getHeight();
            int availableHeight = getResources().getDisplayMetrics().heightPixels - topBarHeight - bottomBarHeight;
            int maxPosition = availableHeight - indicatorHeight;
            float y = topBarHeight + (percent / 100.0f) * maxPosition;
            progressIndicator.setY(y);
        });
    }

    private void saveProgress(int page) {
        if (comic != null && page != comic.getCurrentPage()) {
            comic.setCurrentPage(page);
            comic.setLastReadTime(new java.util.Date());
            viewModel.updateComic(comic);
        }
    }

    private void handleTap(float x, float y) {
        if (brightnessPanel.getVisibility() == View.VISIBLE) {
            brightnessPanel.setVisibility(View.GONE);
            return;
        }
        if (scrollPanel.getVisibility() == View.VISIBLE) {
            scrollPanel.setVisibility(View.GONE);
            scrollPanelBg.setVisibility(View.GONE);
            progressIndicator.setVisibility(View.VISIBLE);
            progressIndicator.setBackgroundResource(R.drawable.ic_progress_button);
            progressIndicator.setTranslationX(18f);
            return;
        }

        float screenWidth = getResources().getDisplayMetrics().widthPixels;
        float indicatorWidth = getResources().getDimension(R.dimen.progress_indicator_width);

        if (x > screenWidth - indicatorWidth) {
            return;
        }

        boolean isVertical = AppSettings.READ_MODE_VERTICAL.equals(settings.getReadMode());
        if (isVertical) {
            float screenHeight = getResources().getDisplayMetrics().heightPixels;
            float topZone = screenHeight * 0.4f;
            float bottomZone = screenHeight * 0.6f;
            if (y < topZone) {
                scrollUp();
            } else if (y > bottomZone) {
                scrollDown();
            } else {
                toggleBars();
            }
        } else {
            float screenWidth2 = getResources().getDisplayMetrics().widthPixels;
            boolean isRtl = AppSettings.READ_MODE_RTL.equals(settings.getReadMode());
            if (x < screenWidth2 / 3) {
                if (isRtl) {
                    scrollNextPage();
                } else {
                    scrollPrevPage();
                }
            } else if (x > screenWidth2 * 2 / 3) {
                if (isRtl) {
                    scrollPrevPage();
                } else {
                    scrollNextPage();
                }
            } else {
                toggleBars();
            }
        }
    }

    private void scrollPrevPage() {
        if (recyclerView != null && recyclerView.getVisibility() == View.VISIBLE) {
            int screenHeight = getResources().getDisplayMetrics().heightPixels;
            recyclerView.smoothScrollBy(0, -screenHeight);
        } else if (viewPager != null && viewPager.getVisibility() == View.VISIBLE) {
            int currentPosition = viewPager.getCurrentItem();
            if (currentPosition > 0) {
                viewPager.setCurrentItem(currentPosition - 1, true);
            }
        }
    }

    private void scrollNextPage() {
        if (recyclerView != null && recyclerView.getVisibility() == View.VISIBLE) {
            int screenHeight = getResources().getDisplayMetrics().heightPixels;
            recyclerView.smoothScrollBy(0, screenHeight);
        } else if (viewPager != null && viewPager.getVisibility() == View.VISIBLE) {
            int totalPages = adapter != null ? adapter.getTotalPages() : 0;
            int currentPosition = viewPager.getCurrentItem();
            if (currentPosition < totalPages - 1) {
                viewPager.setCurrentItem(currentPosition + 1, true);
            }
        }
    }

    private void scrollUp() {
        if (recyclerView != null && recyclerView.getVisibility() == View.VISIBLE) {
            int screenHeight = getResources().getDisplayMetrics().heightPixels;
            recyclerView.smoothScrollBy(0, -screenHeight);
        } else if (viewPager != null && viewPager.getVisibility() == View.VISIBLE) {
            int currentPosition = viewPager.getCurrentItem();
            if (currentPosition > 0) {
                viewPager.setCurrentItem(currentPosition - 1, false);
            }
        }
    }

    private void scrollDown() {
        if (recyclerView != null && recyclerView.getVisibility() == View.VISIBLE) {
            int screenHeight = getResources().getDisplayMetrics().heightPixels;
            recyclerView.smoothScrollBy(0, screenHeight);
        } else if (viewPager != null && viewPager.getVisibility() == View.VISIBLE) {
            int totalPages = adapter != null ? adapter.getTotalPages() : 0;
            int currentPosition = viewPager.getCurrentItem();
            if (currentPosition < totalPages - 1) {
                viewPager.setCurrentItem(currentPosition + 1, false);
            }
        }
    }

    private void toggleBars() {
        barsVisible = !barsVisible;
        topBar.setVisibility(barsVisible ? View.VISIBLE : View.GONE);
        bottomBar.setVisibility(barsVisible ? View.VISIBLE : View.GONE);
        brightnessPanel.setVisibility(View.GONE);

        if (barsVisible) {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        }

        int currentPage = 0;
        if (layoutManager != null) {
            currentPage = layoutManager.findFirstVisibleItemPosition();
        } else if (viewPager != null && viewPager.getVisibility() == View.VISIBLE) {
            currentPage = viewPager.getCurrentItem();
        }
        final int page = currentPage;
        progressIndicator.post(() -> updateScrollBar(page));
    }

    private void openSettings() {
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivity(intent);
    }

    private void toggleToc() {
        if (tocPanel.getVisibility() == View.VISIBLE) {
            closeToc();
        } else {
            openToc();
        }
    }

    private void openToc() {
        if (!"epub".equals(comic.getFileType()) && !"mobi".equals(comic.getFileType())) {
            Toast.makeText(this, R.string.no_toc, Toast.LENGTH_SHORT).show();
            return;
        }

        EpubParser epubParser = null;
        if (adapter != null && adapter.getEpubParser() != null) {
            epubParser = adapter.getEpubParser();
        }

        if (epubParser == null) {
            Toast.makeText(this, R.string.no_toc, Toast.LENGTH_SHORT).show();
            return;
        }

        List<EpubParser.TocItem> toc = epubParser.getToc();
        if (toc == null || toc.isEmpty()) {
            Toast.makeText(this, R.string.no_toc, Toast.LENGTH_SHORT).show();
            return;
        }

        int currentPage = 0;
        if (layoutManager != null) {
            currentPage = layoutManager.findFirstVisibleItemPosition();
        } else if (viewPager != null && viewPager.getVisibility() == View.VISIBLE) {
            currentPage = viewPager.getCurrentItem();
        }

        tocAdapter = new TocAdapter(this, toc, currentPage, chapterIndex -> {
            jumpToChapter(chapterIndex);
            closeToc();
        });

        tocRecycler.setLayoutManager(new LinearLayoutManager(this));
        tocRecycler.setAdapter(tocAdapter);

        tocPanelBg.setVisibility(View.VISIBLE);
        tocPanel.setVisibility(View.VISIBLE);
    }

    private void closeToc() {
        tocPanel.setVisibility(View.GONE);
        tocPanelBg.setVisibility(View.GONE);
    }

    private void jumpToChapter(int chapterIndex) {
        if (adapter != null) {
            adapter.scrollToChapter(chapterIndex);
        }
    }

    private void setupEpubScrollListener(ComicReaderAdapter adapter) {
        if (!"epub".equals(comic.getFileType())) return;
        
        adapter.setOnScrollProgressListener(progress -> {
            updateEpubProgress(progress);
        });
    }

    private void updateEpubProgress(int progress) {
        TextView pageInfo = findViewById(R.id.page_info);
        int chapterCount = 0;
        if (adapter != null && adapter.getEpubParser() != null) {
            chapterCount = adapter.getEpubParser().getChapters().size();
        }
        
        pageInfo.setText(String.format("进度 %d%%", progress));

        TextView pagePercent = findViewById(R.id.page_percent);
        pagePercent.setText(progress + "%");

        ProgressBar progressBar = findViewById(R.id.reader_progress);
        progressBar.setProgress(progress);

        scrollSeekBar.setProgress(progress);

        progressIndicator.post(() -> {
            int topBarHeight = topBar.getVisibility() == View.VISIBLE ? topBar.getHeight() : 0;
            int bottomBarHeight = bottomBar.getVisibility() == View.VISIBLE ? bottomBar.getHeight() : 0;
            int indicatorHeight = progressIndicator.getHeight();
            int availableHeight = getResources().getDisplayMetrics().heightPixels - topBarHeight - bottomBarHeight;
            int maxPosition = availableHeight - indicatorHeight;
            float y = topBarHeight + (progress / 100.0f) * maxPosition;
            progressIndicator.setY(y);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        AppSettings newSettings = viewModel.getSettings();
        if (!newSettings.getReadMode().equals(settings.getReadMode())) {
            settings = newSettings;
            setupReader();
        }
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        getWindow().getDecorView().setLayerType(View.LAYER_TYPE_NONE, null);
        int newWidth = getResources().getDisplayMetrics().widthPixels;
        int currentPos = -1;
        if (layoutManager != null) {
            currentPos = layoutManager.findFirstVisibleItemPosition();
        }
        if (AppSettings.READ_MODE_VERTICAL.equals(settings.getReadMode())) {
            layoutManager = new LinearLayoutManager(this);
            layoutManager.setOrientation(LinearLayoutManager.VERTICAL);
            recyclerView.setLayoutManager(layoutManager);
            adapter = new ComicReaderAdapter(this, comic);
            adapter.setSpacingMode(spacingMode);
            adapter.onConfigurationChanged(newWidth);
            recyclerView.setAdapter(adapter);
            if (currentPos >= 0) {
                final int finalPos = currentPos;
                final LinearLayoutManager finalLM = layoutManager;
                recyclerView.postDelayed(() -> finalLM.scrollToPosition(finalPos), 100);
            }
        } else {
            adapter = new ComicReaderAdapter(this, comic);
            adapter.setSpacingMode(spacingMode);
            adapter.onConfigurationChanged(newWidth);
            viewPager.setAdapter(adapter);
            if (currentPos >= 0) {
                viewPager.setCurrentItem(currentPos, false);
            }
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // 音量键翻页功能
        if (volumeKeyPageTurn) {
            if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                // 音量加键 = 下一页
                scrollNextPage();
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                // 音量减键 = 上一页
                scrollPrevPage();
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    // 自动翻页功能
    private void setupAutoPageTurn() {
        autoPageRunnable = new Runnable() {
            @Override
            public void run() {
                if (autoPageTurnEnabled) {
                    scrollNextPage();
                    autoPageHandler.postDelayed(this, autoPageTurnInterval);
                }
            }
        };
    }

    private void toggleAutoPageTurn() {
        autoPageTurnEnabled = !autoPageTurnEnabled;
        if (autoPageTurnEnabled) {
            startAutoPageTurn();
            Toast.makeText(this, "自动翻页已开启 (" + (autoPageTurnInterval / 1000) + "秒)", Toast.LENGTH_SHORT).show();
            if (autoPageButton != null) {
                autoPageButton.setImageResource(R.drawable.ic_auto_page_on);
            }
        } else {
            stopAutoPageTurn();
            Toast.makeText(this, "自动翻页已关闭", Toast.LENGTH_SHORT).show();
            if (autoPageButton != null) {
                autoPageButton.setImageResource(R.drawable.ic_auto_page_off);
            }
        }
    }

    private void startAutoPageTurn() {
        autoPageHandler.removeCallbacks(autoPageRunnable);
        autoPageHandler.postDelayed(autoPageRunnable, autoPageTurnInterval);
    }

    private void stopAutoPageTurn() {
        autoPageHandler.removeCallbacks(autoPageRunnable);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopAutoPageTurn();
        PdfRendererManager.releaseAll();

        // 清理 Adapter 资源
        if (adapter != null) {
            adapter.cleanup();
        }

        // 停止阅读统计
        if (statsManager != null) {
            statsManager.stopReading();
        }
    }

    private class SpacingItemDecoration extends RecyclerView.ItemDecoration {
        private int spacing;
        private boolean isSpacingEnabled = true;

        SpacingItemDecoration(int spacing) {
            this.spacing = spacing;
        }

        void setSpacingEnabled(boolean enabled) {
            this.isSpacingEnabled = enabled;
        }

        @Override
        public void getItemOffsets(@NonNull android.graphics.Rect outRect, @NonNull View view,
                                   @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
            int position = parent.getChildAdapterPosition(view);
            if (position == RecyclerView.NO_POSITION) {
                return;
            }

            if (isSpacingEnabled) {
                outRect.top = spacing;
                outRect.bottom = spacing;
            } else {
                outRect.top = 0;
                outRect.bottom = 0;
            }
        }
    }
}
