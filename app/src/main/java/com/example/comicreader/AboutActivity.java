package com.example.comicreader;

import android.content.Context;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.os.LocaleList;
import android.view.View;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.comicreader.model.AppSettings;
import com.google.android.material.appbar.MaterialToolbar;
import java.util.Locale;

public class AboutActivity extends AppCompatActivity {

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
        setContentView(R.layout.activity_about);

        MaterialToolbar toolbar = findViewById(R.id.about_toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        findViewById(R.id.btn_copy_wechat).setOnClickListener(v -> copyToClipboard(getString(R.string.contact_wechat)));
        findViewById(R.id.btn_copy_email).setOnClickListener(v -> copyToClipboard(getString(R.string.contact_email)));
        findViewById(R.id.btn_copy_alipay).setOnClickListener(v -> copyToClipboard(getString(R.string.contact_alipay)));
    }

    private void copyToClipboard(String text) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        android.content.ClipData clip = android.content.ClipData.newPlainText("集漫联系方式", text);
        clipboard.setPrimaryClip(clip);
        Toast.makeText(this, "已复制", Toast.LENGTH_SHORT).show();
    }
}