package io.github.cctyl.nokia.keycore.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;

import io.github.cctyl.nokia.common.ui.about.NokiaAboutConfig;
import io.github.cctyl.nokia.common.ui.about.NokiaAboutFragment;

/**
 * KeydroidX 独立应用通用关于 Activity。
 * 作为单 Activity / 多 Activity 应用的标准宿主容器，承载 {@link NokiaAboutFragment}。
 */
public class NokiaAboutActivity extends NokiaBaseActivity {

    public static final String EXTRA_CONFIG = "extra_about_config";

    public static void start(Context context) {
        start(context, null);
    }

    public static void start(Context context, @Nullable NokiaAboutConfig config) {
        Intent intent = new Intent(context, NokiaAboutActivity.class);
        if (config != null) {
            intent.putExtra(EXTRA_CONFIG, config);
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    @Override
    protected void onInitViews() {
        if (getSupportFragmentManager().findFragmentById(io.github.cctyl.nokia.common.R.id.midPanel) == null) {
            NokiaAboutConfig config = null;
            if (getIntent() != null && getIntent().hasExtra(EXTRA_CONFIG)) {
                config = (NokiaAboutConfig) getIntent().getSerializableExtra(EXTRA_CONFIG);
            }
            getSupportFragmentManager().beginTransaction()
                    .replace(io.github.cctyl.nokia.common.R.id.midPanel, NokiaAboutFragment.newInstance(config))
                    .commitNow();
        }
        refreshPageBar();
    }
}
