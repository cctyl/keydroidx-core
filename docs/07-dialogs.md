# 07 · 标准弹窗

包：`io.github.cctyl.nokia.keycore.ui.dialog`

三个开箱即用的复古弹窗 + 一个焦点修复工具。共同特性：

- 底部弹出（`Gravity.BOTTOM`）、透明窗底、自带迷你顶栏与软键栏；
- `onCreate` 时自动应用当前生态主题（标题栏渐变、卡片底色、文字颜色）——**禁止在业务里再自绘弹窗配色**；
- 内部用 `NokiaKeyBinding.resolveAction` 解析物理键，软键语义与页面一致；
- `show()` 时自动执行 `NokiaDialogFocus.forceNonTouchMode(this)` 修复 Android 12+ 首个按键被吞问题，并整树应用点阵字体；
- 尺寸收敛于 `values/dimens.xml`（如 `nokia_dialog_title_bar_height`），勿硬编码。

## NokiaOptionsDialog — 选项菜单

底部弹出式纵向选项列表，上下循环选择、确定回调。

### 构造

```java
public NokiaOptionsDialog(@NonNull Context context)                    // 标题默认 "选项"
public NokiaOptionsDialog(@NonNull Context context, @NonNull String title)
```

### 数据项 OptionItem

```java
public static class OptionItem {
    public OptionItem(CharSequence title)                       // id=0，无图标
    public OptionItem(int id, CharSequence title)               // 自定义业务 id
    public OptionItem(int id, CharSequence title, Drawable icon)
    public int getId()
    public CharSequence getTitle()
    public Drawable getIcon()
}
```
`id` 用于不依赖下标的稳定判断；图标建议用 `NokiaIcons.createDrawable(...)` 生成。

### 链式装配

```java
public NokiaOptionsDialog addItem(CharSequence title)                      // 无图标
public NokiaOptionsDialog addItem(int id, CharSequence title)              // 带 id
public NokiaOptionsDialog addItem(int id, CharSequence title, Drawable icon) // 带 id+图标
public NokiaOptionsDialog setOnOptionSelectedListener(OnOptionSelectedListener listener)

public interface OnOptionSelectedListener {
    void onOptionSelected(int index, OptionItem item);  // 回调后弹窗自动 dismiss
}
```

### 用法示例

```kotlin
NokiaOptionsDialog(this, "歌曲操作")
    .addItem(1, "播放", NokiaIcons.createDrawable(this, NokiaIcons.ICON_PLAY, size, Color.WHITE))
    .addItem(2, "加入喜欢", iconFav)
    .addItem(3, "查看歌手", iconPerson)
    .setOnOptionSelectedListener { index, item ->
        when (item.id) { 1 -> play(); 2 -> fav(); 3 -> showArtist() }
    }
    .show()
```

### 按键行为

| 键 | 行为 |
|----|------|
| UP / DOWN | 移动高亮（到边界停住，不循环） |
| SELECT / 左软键 | 确认选中项 → 回调 → dismiss |
| 右软键 | dismiss |
| 触屏点击行 | 同确认 |

## NokiaConfirmDialog — 确认 / 提示

```java
public NokiaConfirmDialog(@NonNull Context context, @NonNull String title, @NonNull String message)

public NokiaConfirmDialog setPositiveButton(String text, OnConfirmListener listener)
public NokiaConfirmDialog setNegativeButton(String text, @Nullable Runnable listener)
// 默认按钮文案：确认 / 取消

public interface OnConfirmListener { void onConfirm(); }
```

- 左软键 = 肯定按钮，右软键 = 否定按钮；
- **先 dismiss 再回调**（避免回调里开新弹窗时窗口 token 冲突）；
- SELECT 与左软键等效确认。

```kotlin
NokiaConfirmDialog(this, "删除歌单", "确定删除「${name}」吗？")
    .setPositiveButton("删除") { doDelete() }
    .setNegativeButton("取消") { /* 可空 */ }
    .show()
```

## NokiaInputDialog — 文本输入

```java
public NokiaInputDialog(@NonNull Context context, @NonNull String title,
                        @Nullable String defaultText, @Nullable String hint)

public NokiaInputDialog setOnInputConfirmListener(OnInputConfirmListener listener)
public interface OnInputConfirmListener { void onConfirm(String text); }  // dismiss 后回调
```

- 弹出即回填 `defaultText` 并把光标置尾；
- 左软键确认提交文本；右软键取消关闭；
- 实体键盘字符直接进入 EditText；触屏点击输入框拉起 IME。
- 注意：SELECT 未被拦截，会正常落入输入框换行/确认逻辑。

```kotlin
NokiaInputDialog(this, "重命名歌单", oldName, "输入新名称")
    .setOnInputConfirmListener { name -> rename(name) }
    .show()
```

## NokiaDialogFocus — 弹窗焦点修复工具

```java
public final class NokiaDialogFocus {
    public static void forceNonTouchMode(Dialog dialog)
}
```

解决 Android 12+ 上 Dialog 窗口默认处于触摸模式导致**第一个物理按键被系统吞掉**的问题。实现：decor 设 `focusable + focusableInTouchMode`、容器 `FOCUS_BLOCK_DESCENDANTS` 阻断子 View 抢焦、post 一次 `requestFocus()`。三个标准弹窗的 `show()` 已自动调用；**自定义 Dialog 请在 `show()` 里手动补一次**。

## 相关文档

- 弹窗期间冻结列表导航：`NokiaListFocusHelper.setDirectionEnabled(false)` → [06-list-focus](./06-list-focus.md)；`NokiaListPageFragment.isDirectionEnabled()` → [05-page-framework](./05-page-framework.md)
