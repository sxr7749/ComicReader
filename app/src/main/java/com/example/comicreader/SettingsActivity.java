package com.example.comicreader;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.os.LocaleList;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.lifecycle.ViewModelProvider;
import com.example.comicreader.model.AppSettings;
import com.example.comicreader.model.UpdateInfo;
import com.example.comicreader.utils.ReadingStatsManager;
import com.example.comicreader.utils.UpdateChecker;
import com.example.comicreader.viewmodel.ComicViewModel;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.switchmaterial.SwitchMaterial;
import java.util.Locale;

public class SettingsActivity extends AppCompatActivity {
    private ComicViewModel viewModel;
    private AppSettings settings;
    private ReadingStatsManager statsManager;

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
        setContentView(R.layout.activity_settings);

        viewModel = new ViewModelProvider(this).get(ComicViewModel.class);
        settings = viewModel.getSettings();
        statsManager = new ReadingStatsManager(this);

        initToolbar();
        setupThemeChips();
        setupLanguageChips();
        setupReadModeChips();
        setupPageModeChips();
        setupAutoRotateSwitch();
        setupCheckUpdateButton();
        setupAboutButton();
        setupReadingStats();
    }

    private void initToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.settings_toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupThemeChips() {
        ChipGroup chipGroup = findViewById(R.id.theme_chip_group);

        String[] themes = {AppSettings.THEME_SYSTEM, AppSettings.THEME_LIGHT, AppSettings.THEME_DARK, AppSettings.THEME_EYE};
        String[] labels = {"跟随系统", "浅色模式", "深色模式", "护眼模式"};

        for (int i = 0; i < themes.length; i++) {
            final String theme = themes[i];
            Chip chip = new Chip(this);
            chip.setText(labels[i]);
            chip.setCheckable(true);
            chip.setChecked(theme.equals(settings.getTheme()));
            chip.setOnClickListener(v -> {
                settings.setTheme(theme);
                viewModel.saveSettings(settings);
                applyTheme(theme);
            });
            chipGroup.addView(chip);
        }
    }

    private void applyTheme(String theme) {
        switch (theme) {
            case AppSettings.THEME_LIGHT:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
            case AppSettings.THEME_DARK:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
            case AppSettings.THEME_EYE:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
            default:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                break;
        }
    }

    private void setupLanguageChips() {
        ChipGroup chipGroup = findViewById(R.id.language_chip_group);

        String[] languages = {AppSettings.LANGUAGE_SYSTEM, AppSettings.LANGUAGE_CN, AppSettings.LANGUAGE_EN};
        String[] labels = {getString(R.string.language_system), getString(R.string.language_cn), getString(R.string.language_en)};

        for (int i = 0; i < languages.length; i++) {
            final String language = languages[i];
            Chip chip = new Chip(this);
            chip.setText(labels[i]);
            chip.setCheckable(true);
            chip.setChecked(language.equals(settings.getLanguage()));
            chip.setOnClickListener(v -> {
                settings.setLanguage(language);
                viewModel.saveSettings(settings);
                applyLanguage(language);
            });
            chipGroup.addView(chip);
        }
    }

    private void applyLanguage(String language) {
        Resources resources = getResources();
        Configuration configuration = resources.getConfiguration();
        Locale locale;

        switch (language) {
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            configuration.setLocales(new LocaleList(locale));
        } else {
            configuration.locale = locale;
        }

        resources.updateConfiguration(configuration, resources.getDisplayMetrics());

        // 重启Activity以应用语言更改
        Intent intent = getIntent();
        finish();
        startActivity(intent);
    }

    private void setupReadModeChips() {
        ChipGroup chipGroup = findViewById(R.id.direction_chip_group);

        String[] modes = {AppSettings.READ_MODE_VERTICAL, AppSettings.READ_MODE_LTR, AppSettings.READ_MODE_RTL};
        String[] labels = {"竖版条漫", "从左到右", "从右到左"};

        for (int i = 0; i < modes.length; i++) {
            final String mode = modes[i];
            Chip chip = new Chip(this);
            chip.setText(labels[i]);
            chip.setCheckable(true);
            chip.setChecked(mode.equals(settings.getReadMode()));
            chip.setOnClickListener(v -> {
                settings.setReadMode(mode);
                viewModel.saveSettings(settings);
            });
            chipGroup.addView(chip);
        }
    }

    private void setupPageModeChips() {
        ChipGroup chipGroup = findViewById(R.id.page_mode_chip_group);

        String[] modes = {AppSettings.MODE_SLIDE, AppSettings.MODE_COVER, AppSettings.MODE_NONE};
        String[] labels = {"滑动翻页", "封面翻页", "无动画"};

        for (int i = 0; i < modes.length; i++) {
            final String mode = modes[i];
            Chip chip = new Chip(this);
            chip.setText(labels[i]);
            chip.setCheckable(true);
            chip.setChecked(mode.equals(settings.getPageMode()));
            chip.setOnClickListener(v -> {
                settings.setPageMode(mode);
                viewModel.saveSettings(settings);
            });
            chipGroup.addView(chip);
        }
    }

    private void setupAutoRotateSwitch() {
        SwitchMaterial switchMaterial = findViewById(R.id.auto_rotate_switch);
        switchMaterial.setChecked(settings.isAutoRotate());
        switchMaterial.setOnCheckedChangeListener((buttonView, isChecked) -> {
            settings.setAutoRotate(isChecked);
            viewModel.saveSettings(settings);
        });
    }

    private void setupCheckUpdateButton() {
        TextView versionText = findViewById(R.id.tv_current_version);
        versionText.setText("v" + UpdateChecker.getCurrentVersionName(this));

        findViewById(R.id.btn_check_update).setOnClickListener(v -> {
            UpdateChecker.checkForUpdate(this, new UpdateChecker.OnUpdateCheckListener() {
                @Override
                public void onUpdateAvailable(final UpdateInfo updateInfo) {
                    runOnUiThread(() -> UpdateChecker.showUpdateDialog(SettingsActivity.this, updateInfo));
                }

                @Override
                public void onNoUpdate() {
                    runOnUiThread(() -> {
                        com.google.android.material.snackbar.Snackbar.make(
                                findViewById(android.R.id.content),
                                "已是最新版本",
                                com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show();
                    });
                }

                @Override
                public void onError(String error) {
                    runOnUiThread(() -> {
                        com.google.android.material.snackbar.Snackbar.make(
                                findViewById(android.R.id.content),
                                "检查更新失败: " + error,
                                com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show();
                    });
                }
            }, true);
        });
    }

    private void setupAboutButton() {
        findViewById(R.id.btn_about).setOnClickListener(v -> {
            Intent intent = new Intent(this, AboutActivity.class);
            startActivity(intent);
        });
    }

    private void setupReadingStats() {
        // 获取统计数据
        int[] todayStats = statsManager.getTodayStats();
        int[] totalStats = statsManager.getTotalStats();

        // 更新今日统计
        TextView tvTodayDuration = findViewById(R.id.tv_today_duration);
        TextView tvTodayPages = findViewById(R.id.tv_today_pages);
        tvTodayDuration.setText(ReadingStatsManager.formatDuration(todayStats[0]));
        tvTodayPages.setText(todayStats[1] + "页");

        // 更新总计统计
        TextView tvTotalDuration = findViewById(R.id.tv_total_duration);
        TextView tvTotalPages = findViewById(R.id.tv_total_pages);
        TextView tvReadCount = findViewById(R.id.tv_read_count);
        tvTotalDuration.setText(ReadingStatsManager.formatDuration(totalStats[0]));
        tvTotalPages.setText(totalStats[1] + "页");
        tvReadCount.setText(totalStats[2] + "次");
    }
}