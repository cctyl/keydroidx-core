package io.github.cctyl.nokia.keycore.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;

import io.github.cctyl.nokia.common.ui.about.KeydroidxAboutConfig;
import io.github.cctyl.nokia.common.ui.about.KeydroidxAboutFragment;

/**
 * KeydroidX 独立应用通用关于 Activity。
 * 作为单 Activity / 多 Activity 应用的标准宿主容器，承载 {@link KeydroidxAboutFragment}。
 */
public class KeydroidxAboutActivity extends KeydroidxBaseActivity {

    public static final String EXTRA_CONFIG = "extra_about_config";

    public static void start(Context context) {
        start(context, null);
    }

    public static void start(Context context, @Nullable KeydroidxAboutConfig config) {
        Intent intent = new Intent(context, KeydroidxAboutActivity.class);
        if (config != null) {
            intent.putExtra(EXTRA_CONFIG, config);
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    @Override
    protected void onInitViews() {
        if (getSupportFragmentManager().findFragmentById(io.github.cctyl.nokia.common.R.id.midPanel) == null) {
            KeydroidxAboutConfig config = null;
            if (getIntent() != null && getIntent().hasExtra(EXTRA_CONFIG)) {
                config = (KeydroidxAboutConfig) getIntent().getSerializableExtra(EXTRA_CONFIG);
            }
            getSupportFragmentManager().beginTransaction()
                    .replace(io.github.cctyl.nokia.common.R.id.midPanel, KeydroidxAboutFragment.newInstance(config))
                    .commitNow();
        }
        refreshPageBar();
    }
}
