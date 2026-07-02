package com.example.comicreader;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.LocaleList;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.comicreader.adapter.ComicAdapter;
import com.example.comicreader.model.AppSettings;
import com.example.comicreader.model.ComicBook;
import com.example.comicreader.model.UpdateInfo;
import com.example.comicreader.utils.UpdateChecker;
import com.example.comicreader.viewmodel.ComicViewModel;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.List;
import java.util.Set;

public class MainActivity extends AppCompatActivity implements ComicAdapter.OnComicClickListener {
    private static final int REQUEST_CODE_IMPORT = 1001;
    private static final int REQUEST_CODE_INSTALL = 10086;

    private ComicViewModel viewModel;
    private RecyclerView comicRecycler;
    private ComicAdapter adapter;
    private LinearLayout emptyState;
    private View loading;
    private ChipGroup categoryChips;
    private LinearLayout collapsibleArea;
    private MaterialButton btnCollapse;
    private SearchView searchView;

    private List<ComicBook> allComics = new ArrayList<>();
    private List<ComicBook> filteredComics = new ArrayList<>();
    private String currentCategory = "全部";
    private String currentSort = "name";
    private String searchQuery = "";
    private boolean isCollapsed = false;

    private LinearLayout selectionBottomBar;
    private TextView selectionCountText;
    private FloatingActionButton fabImport;

    private static final String[] CATEGORIES = {"全部", "国漫", "韩漫", "港漫", "日漫", "其他"};

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
        setContentView(R.layout.activity_main);

        viewModel = new ViewModelProvider(this).get(ComicViewModel.class);

        initViews();
        observeViewModel();
        setupAdapter();
        setupCategories();
        setupSortButton();
        setupCollapseButton();
        setupSearchView();
        setupSelectionButtons();

        fabImport.setOnClickListener(v -> importComic());

        findViewById(R.id.btn_empty_import).setOnClickListener(v -> importComic());

        // 添加返回键处理：在选择模式下按返回键退出选择模式
        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (adapter.isSelectionMode()) {
                    exitSelectionMode();
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });
    }

    private void setupSelectionButtons() {
        findViewById(R.id.btn_select_all).setOnClickListener(v -> {
            adapter.selectAll();
        });

        findViewById(R.id.btn_edit_category_selected).setOnClickListener(v -> {
            editSelectedCategory();
        });

        findViewById(R.id.btn_delete_selected).setOnClickListener(v -> {
            confirmBatchDelete();
        });

        findViewById(R.id.btn_cancel_selection).setOnClickListener(v -> {
            exitSelectionMode();
        });
    }

    private void initViews() {
        comicRecycler = findViewById(R.id.comic_recycler);
        emptyState = findViewById(R.id.empty_state);
        loading = findViewById(R.id.loading);
        categoryChips = findViewById(R.id.category_chips);
        collapsibleArea = findViewById(R.id.collapsible_area);
        btnCollapse = findViewById(R.id.btn_collapse);
        searchView = findViewById(R.id.search_view);
        selectionBottomBar = findViewById(R.id.selection_bottom_bar);
        selectionCountText = findViewById(R.id.selection_count_text);
        fabImport = findViewById(R.id.fab_import);

        GridLayoutManager layoutManager = new GridLayoutManager(this, 2);
        comicRecycler.setLayoutManager(layoutManager);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(R.string.title_main);
        toolbar.setNavigationIcon(R.drawable.ic_settings);
        toolbar.setNavigationOnClickListener(v -> openSettings());
    }

    private void setupAdapter() {
        adapter = new ComicAdapter(new ArrayList<>(), this);
        comicRecycler.setAdapter(adapter);
    }

    private void observeViewModel() {
        viewModel.getComics().observe(this, comics -> {
            allComics = comics;
            filterAndSortComics();
            updateEmptyState(filteredComics);
        });

        viewModel.getIsLoading().observe(this, isLoading -> {
            loading.setVisibility(isLoading ? View.VISIBLE : View.GONE);
            comicRecycler.setVisibility(isLoading ? View.GONE : View.VISIBLE);
        });

        viewModel.getMessage().observe(this, message -> {
            if (message != null && !message.isEmpty()) {
                Snackbar.make(comicRecycler, message, Snackbar.LENGTH_SHORT).show();
                viewModel.setMessage(null);
            }
        });
    }

    private void setupCategories() {
        categoryChips.removeAllViews();

        for (String category : CATEGORIES) {
            Chip chip = new Chip(this);
            chip.setText(category);
            chip.setCheckable(true);
            chip.setChecked(category.equals(currentCategory));
            chip.setOnClickListener(v -> {
                currentCategory = category;
                filterAndSortComics();
                for (int i = 0; i < categoryChips.getChildCount(); i++) {
                    View child = categoryChips.getChildAt(i);
                    if (child instanceof Chip) {
                        ((Chip) child).setChecked(child.equals(v));
                    }
                }
            });
            categoryChips.addView(chip);
        }

        Set<String> customTags = new HashSet<>();
        if (allComics != null) {
            for (ComicBook comic : allComics) {
                if (comic.getCustomTag() != null && !comic.getCustomTag().isEmpty()) {
                    customTags.add(comic.getCustomTag());
                }
            }
        }

        if (!customTags.isEmpty()) {
            View divider = new View(this);
            divider.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    (int) getResources().getDimension(R.dimen.divider_height)));
            divider.setBackgroundColor(getResources().getColor(R.color.divider));
            categoryChips.addView(divider);

            for (String tag : customTags) {
                Chip chip = new Chip(this);
                chip.setText(tag);
                chip.setCheckable(true);
                chip.setChecked(tag.equals(currentCategory));
                chip.setOnClickListener(v -> {
                    currentCategory = tag;
                    filterAndSortComics();
                    for (int i = 0; i < categoryChips.getChildCount(); i++) {
                        View child = categoryChips.getChildAt(i);
                        if (child instanceof Chip) {
                            ((Chip) child).setChecked(child.equals(v));
                        }
                    }
                });
                categoryChips.addView(chip);
            }
        }
    }

    private void setupSortButton() {
        TextView btnSort = findViewById(R.id.btn_sort);
        btnSort.setOnClickListener(v -> {
            String[] sortOptions = {"按文件名", "按大小", "按类型", "按添加时间"};
            new MaterialAlertDialogBuilder(this)
                    .setTitle("排序方式")
                    .setItems(sortOptions, (dialog, which) -> {
                        switch (which) {
                            case 0:
                                currentSort = "name";
                                btnSort.setText("按文件名");
                                break;
                            case 1:
                                currentSort = "size";
                                btnSort.setText("按大小");
                                break;
                            case 2:
                                currentSort = "type";
                                btnSort.setText("按类型");
                                break;
                            case 3:
                                currentSort = "date";
                                btnSort.setText("按添加时间");
                                break;
                        }
                        filterAndSortComics();
                    })
                    .show();
        });
    }

    private void setupCollapseButton() {
        btnCollapse.setOnClickListener(v -> {
            isCollapsed = !isCollapsed;
            collapsibleArea.setVisibility(isCollapsed ? View.GONE : View.VISIBLE);
            btnCollapse.setIconResource(isCollapsed ? R.drawable.ic_expand_more : R.drawable.ic_expand_less);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (viewModel != null) {
            viewModel.loadComics();
        }
        checkForUpdate();
    }

    private void checkForUpdate() {
        UpdateChecker.checkForUpdate(this, new UpdateChecker.OnUpdateCheckListener() {
            @Override
            public void onUpdateAvailable(final UpdateInfo updateInfo) {
                runOnUiThread(() -> UpdateChecker.showUpdateDialog(MainActivity.this, updateInfo));
            }

            @Override
            public void onNoUpdate() {
            }

            @Override
            public void onError(String error) {
            }
        });
    }

    private void setupSearchView() {
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                searchQuery = query.toLowerCase().trim();
                filterAndSortComics();
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                searchQuery = newText.toLowerCase().trim();
                filterAndSortComics();
                return true;
            }
        });
    }

    private void filterAndSortComics() {
        filteredComics = new ArrayList<>();
        for (ComicBook comic : allComics) {
            boolean matchCategory = "全部".equals(currentCategory)
                    || currentCategory.equals(comic.getCategory())
                    || currentCategory.equals(comic.getCustomTag());

            boolean matchSearch = searchQuery.isEmpty();
            if (!matchSearch) {
                String title = comic.getTitle() != null ? comic.getTitle().toLowerCase() : "";
                String category = comic.getCategory() != null ? comic.getCategory().toLowerCase() : "";
                String genre = comic.getGenre() != null ? comic.getGenre().toLowerCase() : "";
                matchSearch = title.contains(searchQuery)
                        || category.contains(searchQuery)
                        || genre.contains(searchQuery);
            }

            if (matchCategory && matchSearch) {
                filteredComics.add(comic);
            }
        }

        switch (currentSort) {
            case "name":
                Collections.sort(filteredComics, (a, b) -> {
                    String nameA = a.getTitle() != null ? a.getTitle() : "";
                    String nameB = b.getTitle() != null ? b.getTitle() : "";
                    return nameA.compareToIgnoreCase(nameB);
                });
                break;
            case "size":
                Collections.sort(filteredComics, (a, b) -> Long.compare(b.getFileSize(), a.getFileSize()));
                break;
            case "type":
                Collections.sort(filteredComics, (a, b) -> {
                    String typeA = a.getFileType() != null ? a.getFileType() : "";
                    String typeB = b.getFileType() != null ? b.getFileType() : "";
                    return typeA.compareToIgnoreCase(typeB);
                });
                break;
            case "date":
                Collections.sort(filteredComics, (a, b) -> {
                    if (a.getAddedTime() == null) return 1;
                    if (b.getAddedTime() == null) return -1;
                    return b.getAddedTime().compareTo(a.getAddedTime());
                });
                break;
        }

        adapter.updateList(filteredComics);
    }

    private void updateEmptyState(List<ComicBook> comics) {
        if (comics.isEmpty()) {
            emptyState.setVisibility(View.VISIBLE);
            comicRecycler.setVisibility(View.GONE);
        } else {
            emptyState.setVisibility(View.GONE);
            comicRecycler.setVisibility(View.VISIBLE);
        }
    }

    private void importComic() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        String[] mimeTypes = {"application/pdf", "application/zip",
                "application/epub+zip", "application/x-mobipocket-ebook",
                "text/plain",
                "image/jpeg", "image/png", "image/gif", "image/bmp", "image/webp"};
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        startActivityForResult(Intent.createChooser(intent, "选择漫画文件"), REQUEST_CODE_IMPORT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_IMPORT && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                viewModel.importComic(uri);
            }
        } else if (requestCode == REQUEST_CODE_INSTALL) {
            checkForUpdate();
        }
    }

    private void openSettings() {
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivity(intent);
    }

    @Override
    public void onComicClick(ComicBook comic) {
        Intent intent = new Intent(this, ReaderActivity.class);
        intent.putExtra("comicId", comic.getId());
        startActivity(intent);
    }

    @Override
    public void onComicLongClick(ComicBook comic) {
        showComicOptions(comic);
    }

    @Override
    public void onSelectionModeChanged(boolean isSelectionMode, int selectedCount) {
        if (isSelectionMode) {
            selectionBottomBar.setVisibility(View.VISIBLE);
            fabImport.hide();
            updateSelectionCount(selectedCount);
        } else {
            selectionBottomBar.setVisibility(View.GONE);
            fabImport.show();
        }
    }

    @Override
    public void onSelectionChanged(String comicId, boolean isSelected) {
        updateSelectionCount(adapter.getSelectedCount());
    }

    private void updateSelectionCount(int count) {
        selectionCountText.setText("已选择 " + count + " 项");
    }

    private void exitSelectionMode() {
        adapter.setSelectionMode(false);
    }

    private void confirmBatchDelete() {
        List<String> selectedIds = adapter.getSelectedIds();
        if (selectedIds.isEmpty()) {
            Snackbar.make(comicRecycler, "请选择要删除的项目", Snackbar.LENGTH_SHORT).show();
            return;
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle("批量删除")
                .setMessage("确定要删除选中的 " + selectedIds.size() + " 本漫画吗？")
                .setPositiveButton("删除", (dialog, which) -> {
                    viewModel.deleteMultipleComics(selectedIds);
                    exitSelectionMode();
                    Snackbar.make(comicRecycler, "已删除 " + selectedIds.size() + " 本漫画", Snackbar.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void editSelectedCategory() {
        List<String> selectedIds = adapter.getSelectedIds();
        if (selectedIds.isEmpty()) {
            Snackbar.make(comicRecycler, "请选择要编辑的项目", Snackbar.LENGTH_SHORT).show();
            return;
        }

        View view = getLayoutInflater().inflate(R.layout.dialog_edit_category, null);
        Spinner categorySpinner = view.findViewById(R.id.category_spinner);
        Spinner genreSpinner = view.findViewById(R.id.genre_spinner);
        EditText customTagEdit = view.findViewById(R.id.custom_tag_edit);

        String[] categories = {"国漫", "韩漫", "港漫", "日漫", "其他"};
        ArrayAdapter<String> categoryAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, categories);
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        categorySpinner.setAdapter(categoryAdapter);

        String[] genres = {"玄幻", "诡秘", "武侠", "修仙", "末世", "后宫", "系统",
                "穿越", "校园", "悬疑", "恋爱", "TL", "BL", "BG", "未分类"};
        ArrayAdapter<String> genreAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, genres);
        genreAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        genreSpinner.setAdapter(genreAdapter);

        new MaterialAlertDialogBuilder(this)
                .setTitle("批量编辑分类")
                .setView(view)
                .setPositiveButton("保存", (dialog, which) -> {
                    String newCategory = categorySpinner.getSelectedItem().toString();
                    String newGenre = genreSpinner.getSelectedItem().toString();
                    String newCustomTag = customTagEdit.getText().toString().trim();
                    int updatedCount = 0;
                    for (ComicBook comic : allComics) {
                        if (selectedIds.contains(comic.getId())) {
                            comic.setCategory(newCategory);
                            comic.setGenre(newGenre);
                            comic.setCustomTag(newCustomTag);
                            viewModel.updateComic(comic);
                            updatedCount++;
                        }
                    }
                    exitSelectionMode();
                    setupCategories();
                    Snackbar.make(comicRecycler, "已更新 " + updatedCount + " 本漫画的分类", Snackbar.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showComicOptions(ComicBook comic) {
        CharSequence[] options = {comic.isFavorite() ? "取消收藏" : "收藏",
                "文件重命名", "添加分类标签", "查看详情", "删除"};
        new MaterialAlertDialogBuilder(this)
                .setTitle(comic.getTitle())
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        comic.setFavorite(!comic.isFavorite());
                        viewModel.updateComic(comic);
                        Snackbar.make(comicRecycler,
                                comic.isFavorite() ? "已收藏" : "已取消收藏",
                                Snackbar.LENGTH_SHORT).show();
                    } else if (which == 1) {
                        editComicName(comic);
                    } else if (which == 2) {
                        editComicCategory(comic);
                    } else if (which == 3) {
                        showComicDetails(comic);
                    } else if (which == 4) {
                        confirmDelete(comic);
                    }
                })
                .show();
    }

    private void editComicName(ComicBook comic) {
        android.widget.EditText editText = new android.widget.EditText(this);
        editText.setText(comic.getTitle());
        editText.setHint("输入新名称");

        new MaterialAlertDialogBuilder(this)
                .setTitle("修改名称")
                .setView(editText)
                .setPositiveButton("保存", (dialog, which) -> {
                    String newName = editText.getText().toString().trim();
                    if (!newName.isEmpty()) {
                        comic.setTitle(newName);
                        viewModel.updateComic(comic);
                        Snackbar.make(comicRecycler, "名称已更新", Snackbar.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void editComicCategory(ComicBook comic) {
        View view = getLayoutInflater().inflate(R.layout.dialog_edit_category, null);
        Spinner categorySpinner = view.findViewById(R.id.category_spinner);
        Spinner genreSpinner = view.findViewById(R.id.genre_spinner);
        EditText customTagEdit = view.findViewById(R.id.custom_tag_edit);

        String[] categories = {"国漫", "韩漫", "港漫", "日漫", "其他"};
        ArrayAdapter<String> categoryAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, categories);
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        categorySpinner.setAdapter(categoryAdapter);

        String[] genres = {"玄幻", "诡秘", "武侠", "修仙", "末世", "后宫", "系统",
                "穿越", "校园", "悬疑", "恋爱", "TL", "BL", "BG", "未分类"};
        ArrayAdapter<String> genreAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, genres);
        genreAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        genreSpinner.setAdapter(genreAdapter);

        if (comic.getCategory() != null) {
            int pos = java.util.Arrays.asList(categories).indexOf(comic.getCategory());
            if (pos >= 0) categorySpinner.setSelection(pos);
        }
        if (comic.getGenre() != null) {
            int pos = java.util.Arrays.asList(genres).indexOf(comic.getGenre());
            if (pos >= 0) genreSpinner.setSelection(pos);
        }
        if (comic.getCustomTag() != null) {
            customTagEdit.setText(comic.getCustomTag());
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle("编辑分类")
                .setView(view)
                .setPositiveButton("保存", (dialog, which) -> {
                    comic.setCategory(categorySpinner.getSelectedItem().toString());
                    comic.setGenre(genreSpinner.getSelectedItem().toString());
                    comic.setCustomTag(customTagEdit.getText().toString().trim());
                    viewModel.updateComic(comic);
                    setupCategories();
                    Snackbar.make(comicRecycler, "分类已更新", Snackbar.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void confirmDelete(ComicBook comic) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("删除漫画")
                .setMessage("确定要删除这本漫画吗？")
                .setPositiveButton("删除", (dialog, which) -> {
                    viewModel.deleteComic(comic.getId());
                    Snackbar.make(comicRecycler, "已删除", Snackbar.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showComicDetails(ComicBook comic) {
        StringBuilder details = new StringBuilder();
        details.append("名称: ").append(comic.getTitle()).append("\n");
        details.append("类型: ").append(comic.getFileType()).append("\n");
        details.append("分类: ").append(comic.getCategory()).append(" / ").append(comic.getGenre()).append("\n");
        if (comic.getCustomTag() != null && !comic.getCustomTag().isEmpty()) {
            details.append("自定义标签: ").append(comic.getCustomTag()).append("\n");
        }
        details.append("页数: ").append(comic.getTotalPages()).append("\n");
        details.append("当前页: ").append(comic.getCurrentPage() + 1).append("\n");
        details.append("进度: ").append(String.format("%.1f%%", comic.getProgress())).append("\n");
        details.append("大小: ").append(formatFileSize(comic.getFileSize())).append("\n");
        if (comic.getAddedTime() != null) {
            details.append("添加时间: ").append(comic.getAddedTime().toString()).append("\n");
        }
        if (comic.getLastReadTime() != null) {
            details.append("最后阅读: ").append(comic.getLastReadTime().toString()).append("\n");
        }
        details.append("收藏: ").append(comic.isFavorite() ? "是" : "否");

        new MaterialAlertDialogBuilder(this)
                .setTitle("漫画详情")
                .setMessage(details.toString())
                .setPositiveButton("确定", null)
                .show();
    }

    private String formatFileSize(long size) {
        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return String.format("%.1f KB", size / 1024.0);
        } else if (size < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", size / (1024.0 * 1024));
        } else {
            return String.format("%.1f GB", size / (1024.0 * 1024 * 1024));
        }
    }
}
