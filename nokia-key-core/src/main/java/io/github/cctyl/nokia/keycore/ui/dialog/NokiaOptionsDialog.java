package io.github.cctyl.nokia.keycore.ui.dialog;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import io.github.cctyl.nokia.common.ui.NokiaTheme;
import io.github.cctyl.nokia.common.util.NokiaDimens;
import io.github.cctyl.nokia.keycore.NokiaClient;
import io.github.cctyl.nokia.keycore.NokiaKeyClient;
import io.github.cctyl.nokia.keycore.R;
import io.github.cctyl.nokia.keycore.model.NokiaKeyAction;
import io.github.cctyl.nokia.keycore.ui.NokiaFontManager;

/**
 * 标准复古诺基亚风格「选项」底部菜单弹窗（开箱即用 UI 组件）。
 */
public class NokiaOptionsDialog extends Dialog {

    public interface OnOptionSelectedListener {
        void onOptionSelected(int index, OptionItem item);
    }

    public static class OptionItem {
        private final int id;
        private final CharSequence title;
        private final Drawable icon;

        public OptionItem(CharSequence title) {
            this(0, title, null);
        }

        public OptionItem(int id, CharSequence title) {
            this(id, title, null);
        }

        public OptionItem(int id, CharSequence title, Drawable icon) {
            this.id = id;
            this.title = title;
            this.icon = icon;
        }

        public int getId() {
            return id;
        }

        public CharSequence getTitle() {
            return title;
        }

        public Drawable getIcon() {
            return icon;
        }
    }

    private final String title;
    private final List<OptionItem> items = new ArrayList<>();
    private OnOptionSelectedListener listener;
    private int selectedIndex = 0;

    private LinearLayout optionsContainer;
    private final List<View> itemViews = new ArrayList<>();

    public NokiaOptionsDialog(@NonNull Context context) {
        this(context, "选项");
    }

    public NokiaOptionsDialog(@NonNull Context context, @NonNull String title) {
        super(context, R.style.Theme_NokiaKeyCore_Dialog);
        this.title = title;
    }

    public NokiaOptionsDialog addItem(int id, CharSequence title) {
        this.items.add(new OptionItem(id, title));
        return this;
    }

    public NokiaOptionsDialog addItem(int id, CharSequence title, Drawable icon) {
        this.items.add(new OptionItem(id, title, icon));
        return this;
    }

    public NokiaOptionsDialog addItem(CharSequence title) {
        this.items.add(new OptionItem(title));
        return this;
    }

    public NokiaOptionsDialog setOnOptionSelectedListener(OnOptionSelectedListener listener) {
        this.listener = listener;
        return this;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_nokia_options);

        Window window = getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
            window.setGravity(Gravity.BOTTOM);
        }

        initViews();
    }

    private void initViews() {
        TextView tvTitle = findViewById(R.id.dialogTitle);
        if (tvTitle != null) {
            tvTitle.setText(title);
        }

        // 应用当前主题到弹窗标题与底部
        NokiaTheme.ThemeDef currentTheme = NokiaTheme.getTheme(NokiaClient.get(getContext()).getCurrentThemeId());
        View titleBar = findViewById(R.id.dialogTitleBar);
        if (titleBar != null && currentTheme != null) {
            titleBar.setBackground(currentTheme.createTitleDrawable());
        }
        View dialogBody = findViewById(R.id.dialogBody);
        if (dialogBody != null && currentTheme != null) {
            dialogBody.setBackgroundColor(currentTheme.cardBgColor);
        }
        View bottomBar = findViewById(R.id.dialogBottomBar);
        if (bottomBar != null && currentTheme != null) {
            bottomBar.setBackground(currentTheme.createSoftKeyDrawable());
        }
        if (tvTitle != null && currentTheme != null) {
            tvTitle.setTextColor(currentTheme.textColor);
        }
        TextView btnLeft = findViewById(R.id.softLeft);
        if (btnLeft != null && currentTheme != null) {
            btnLeft.setTextColor(currentTheme.textColor);
        }
        TextView btnRight = findViewById(R.id.softRight);
        if (btnRight != null && currentTheme != null) {
            btnRight.setTextColor(currentTheme.textColor);
        }

        optionsContainer = findViewById(R.id.dialogOptionsList);
        optionsContainer.removeAllViews();
        itemViews.clear();

        Context context = getContext();
        LayoutInflater inflater = LayoutInflater.from(context);

        int rowHeight = NokiaDimens.dp(context.getResources(), 36);
        int iconSize = NokiaDimens.dp(context.getResources(), 20);
        int iconMargin = NokiaDimens.dp(context.getResources(), 8);
        int paddingH = NokiaDimens.dp(context.getResources(), 12);

        for (int i = 0; i < items.size(); i++) {
            final int index = i;
            final OptionItem item = items.get(i);

            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, rowHeight));
            row.setPadding(paddingH, 0, paddingH, 0);
            row.setClickable(true);
            row.setFocusable(true);

            if (item.getIcon() != null) {
                ImageView iv = new ImageView(context);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(iconSize, iconSize);
                lp.setMarginEnd(iconMargin);
                iv.setLayoutParams(lp);
                iv.setImageDrawable(item.getIcon());
                row.addView(iv);
            }

            TextView tv = new TextView(context);
            tv.setText(item.getTitle());
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            tv.setTextColor(Color.WHITE);
            tv.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
            row.addView(tv);

            row.setOnClickListener(v -> {
                selectedIndex = index;
                confirmSelection();
            });

            optionsContainer.addView(row);
            itemViews.add(row);
        }

        updateSelection();

        View btnSelect = findViewById(R.id.dialogSoftLeft);
        if (btnSelect != null) {
            btnSelect.setOnClickListener(v -> confirmSelection());
        }

        View btnBack = findViewById(R.id.dialogSoftRight);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> dismiss());
        }
    }

    private void updateSelection() {
        NokiaTheme.ThemeDef currentTheme = NokiaTheme.getTheme(NokiaClient.get(getContext()).getCurrentThemeId());
        int focusColor = (currentTheme != null) ? currentTheme.focusColor : 0xFF0055AA;

        for (int i = 0; i < itemViews.size(); i++) {
            View view = itemViews.get(i);
            boolean isSelected = (i == selectedIndex);
            if (isSelected) {
                GradientDrawable gd = new GradientDrawable();
                gd.setColor(focusColor);
                gd.setCornerRadius(NokiaDimens.dp(getContext().getResources(), 4));
                view.setBackground(gd);
            } else {
                view.setBackgroundColor(Color.TRANSPARENT);
            }
        }
    }

    private void confirmSelection() {
        if (selectedIndex >= 0 && selectedIndex < items.size()) {
            OptionItem item = items.get(selectedIndex);
            if (listener != null) {
                listener.onOptionSelected(selectedIndex, item);
            }
        }
        dismiss();
    }

    @Override
    public boolean dispatchKeyEvent(@NonNull KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            int action = NokiaKeyClient.get(getContext()).getBinding().resolveAction(event);
            if (action == NokiaKeyAction.ACTION_UP) {
                if (selectedIndex > 0) {
                    selectedIndex--;
                    updateSelection();
                }
                return true;
            } else if (action == NokiaKeyAction.ACTION_DOWN) {
                if (selectedIndex < items.size() - 1) {
                    selectedIndex++;
                    updateSelection();
                }
                return true;
            } else if (action == NokiaKeyAction.ACTION_SELECT || action == NokiaKeyAction.ACTION_SOFT_LEFT) {
                confirmSelection();
                return true;
            } else if (action == NokiaKeyAction.ACTION_SOFT_RIGHT) {
                dismiss();
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void show() {
        super.show();
        NokiaDialogFocus.forceNonTouchMode(this);
        if (getWindow() != null && getWindow().getDecorView() != null) {
            NokiaFontManager.applyToViewTree(getWindow().getDecorView());
        }
    }
}
