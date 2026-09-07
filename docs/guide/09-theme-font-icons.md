# 09 · 主题 · 字体 · 图标

包：`io.github.cctyl.nokia.common.ui`

## KeydroidxTheme — 主题系统

与桌面端完全一致的 6 套经典机身主题。所有业务色**必须从 ThemeDef 取，禁止硬编码颜色**（本项目血泪规范）。

### 主题 ID 常量

| 常量 | ID | 名称 |
|------|----|------|
| `THEME_CLASSIC_BLUE` | classic_blue | 经典深蓝（默认兜底） |
| `THEME_OBSIDIAN_BLACK` | obsidian_black | 曜石纯黑 |
| `THEME_CYAN_SEA` | cyan_sea | 青海浩渺 |
| `THEME_EMERALD_GREEN` | emerald_green | 翡翠幽绿 |
| `THEME_WINE_PURPLE` | wine_purple | 典雅酒红 |
| `THEME_AMBER_GOLD` | amber_gold | 琥珀暖金 |

### ThemeDef 字段

```java
public final String id;            // 主题 ID
public final String name;          // 中文名
@ColorInt public final int primaryColor;      // 主色（顶栏渐变起点）
@ColorInt public final int darkColor;         // 深色底（窗口/内容区背景、顶栏渐变终点）
@ColorInt public final int accentColor;       // 强调色
@ColorInt public final int focusColor;        // 焦点高亮色（选中行背景）
@ColorInt public final int textColor;         // 主文字色
@ColorInt public final int subTextColor;      // 副文字色
@ColorInt public final int cardBgColor;       // 卡片背景色（弹窗体）
@ColorInt public final int softKeyStartColor; // 软键栏渐变起点
@ColorInt public final int softKeyEndColor;   // 软键栏渐变终点
@ColorInt public final int bgStartColor;      // 内容背景渐变起点
@ColorInt public final int bgCenterColor;     // 内容背景渐变中点
@ColorInt public final int bgEndColor;        // 内容背景渐变终点
```

### ThemeDef 方法

```java
public Drawable createTitleDrawable()
// TOP_BOTTOM 渐变 primaryColor→darkColor，用于顶栏/弹窗标题栏

public Drawable createSoftKeyDrawable()
// TOP_BOTTOM 渐变 softKeyStartColor→softKeyEndColor（与顶栏不同的一组色值），用于软键栏

public Drawable createSelectedRowDrawable(float radiusPx)
// focusColor 圆角矩形（px 半径），列表选中行高亮的标准实现
```

### 静态方法

```java
public static ThemeDef getTheme(String themeId)
// 取主题；未知名安全回退 CLASSIC_BLUE —— 永不返回 null

public static List<ThemeDef> getThemes()   // 全部主题（6 套）

public static ThemeDef getCurrentTheme(Context context)
// 经 ThemeProvider 取当前主题（独立 App 由 KeydroidxClient 注入，桌面注入本地实现）

public static Drawable createSelectionDrawable(Context context, float radiusDp)
// 快捷取「当前生态主题」的选中行 drawable；内部取当前主题并做 dp→px。
// 列表焦点高亮统一走这里（KeydroidxListFocusHelper / KeydroidxListPageFragment 内部即用它）
```

> 业务页面跟随换肤：继承 `KeydroidxBaseActivity` 自动生效；自定义 View 请监听 `KeydroidxClient.OnConfigChangedListener.onThemeChanged`。

## KeydroidxFontManager — 点阵字体管理

> 📌 **全局排版与字号规范唯一事实源**：详见 **[typography-and-font-spec.md](../spec/typography-and-font-spec.md)**。所有文字尺寸必须引用 `@dimen/nokia_font_*` 6 级语义 Token，禁止硬编码裸数字号与滥用加粗。

全局静态管理器：加载 assets 内置字体、缓存 Typeface、整树递归应用 + 缩放。

### 字体 ID 常量

| 常量 | 值 | 文件 |
|------|----|------|
| `FONT_ID_ARK_12PX`（默认） | ark_pixel_12px | fonts/ArkPixel-12px.ttf |
| `FONT_ID_FUSION_12PX` | fusion_pixel_12px | fonts/FusionPixel-12px.ttf |
| `FONT_ID_SYSTEM_DEFAULT` | system_default | 系统默认 |

兼容旧 ID：`"ark_12px"` / `"fusion_12px"` 在 setCurrentFontId 时自动归一。

### 方法

```java
public static synchronized void setCurrentFontId(String fontId)  // 切当前字体（null 回退 ARK）
public static synchronized String getCurrentFontId()
public static synchronized void setFontScale(float scale)        // 合法域 (0.1, 5.0)，越界忽略
public static synchronized float getFontScale()

public static Typeface getTypeface(Context context)              // 当前字体的 Typeface
public static Typeface getTypeface(Context context, String fontId)
// 从 assets 加载并按 fontId 缓存；文件缺失或 SYSTEM_DEFAULT 返回 Typeface.DEFAULT（安全回退）
```

```java
public static void applyToViewTree(View root)
```
递归把当前字体+缩放应用到整棵 View 树：
- **跳过 MaterialIcons 图标 TextView**（typeface 与 KeydroidxIcons 字体相同者），防止图标变豆腐块；
- 缩放实现：首次遇到 TextView 时把原始 px 存入 tag（`0x7f099998`）再乘系数 setTextSize(PX)，重复调用不会二次放大；
- **自动挂监听**：默认 `autoAttachWatcher = true`，会向容器注册 `OnHierarchyChangeListener`，之后动态 `addView` 进来的新视图**自动应用字体，无需手动补调用**。仅当你以 `applyToViewTree(root, false)` 关闭 watcher 时，才需要对新加的行手动补一次。

```java
public static void invalidate()   // 清空 Typeface 缓存（assets 热替换后用）
```

## KeydroidxIcons — MaterialIcons 矢量图标

基于内置 `fonts/MaterialIcons-Regular.ttf` 的字符图标方案（2500+ 图标可用）。**项目禁止新增 PNG/XML 图标。**

### 常量速查（部分）

导航/通用：`ICON_SEARCH` `ICON_SETTINGS` `ICON_HOME` `ICON_MORE_VERT/HORIZ` `ICON_REFRESH` `ICON_CLOSE` `ICON_CHECK` `ICON_ARROW_BACK/FORWARD` `ICON_CHEVRON_RIGHT` `ICON_KEYBOARD`
媒体：`ICON_PLAY` `ICON_PAUSE` `ICON_SKIP_NEXT/PREVIOUS` `ICON_VOLUME_UP/DOWN/MUTE` `ICON_REPEAT` `ICON_REPEAT_ONE` `ICON_SHUFFLE` `ICON_MUSIC_NOTE` `ICON_QUEUE_MUSIC` `ICON_ALBUM` `ICON_LIBRARY_MUSIC` `ICON_RADIO` `ICON_LYRICS` `ICON_SUBTITLES` `ICON_PLAY_CIRCLE(_FILLED)`
内容：`ICON_FOLDER` `ICON_DELETE` `ICON_EDIT` `ICON_ADD` `ICON_INFO` `ICON_HELP` `ICON_WARNING` `ICON_ERROR` `ICON_LOCK` `ICON_STAR` `ICON_FAVORITE(_BORDER)` `ICON_PALETTE`
扩展：`ICON_PERSON` `ICON_EXPLORE` `ICON_LEADERBOARD` `ICON_HISTORY` `ICON_SD_CARD` `ICON_TODAY` `ICON_SIGNAL_CELLULAR_4_BAR`

> ⚠️ 常量以**实际代码为准**：完整清单见 [common 的 KeydroidxIcons.java](../../keydroidx-common/src/main/java/io/github/cctyl/nokia/common/ui/KeydroidxIcons.java)（200+ 常量，含 `ICON_` 前缀全名与短别名两套，common 是超集）。下述常见误用**并不存在**：`ICON_MENU` `ICON_STOP` `ICON_PHONE` `ICON_VOLUME_OFF`（用 `ICON_VOLUME_MUTE`）`ICON_FOLDER_OPEN`（用 `ICON_FOLDER`）`ICON_KEYBOARD_ARROW_*` `ICON_RADIO_BUTTON_*` `ICON_CHECK_CIRCLE` `ICON_TEXT_FIELDS`。缺的图标可按 codepoints 自行追加常量（如 `"\uE7FD"`）。

### 方法

```java
public static synchronized Typeface getTypeface(Context context)
// 加载 assets 字体并缓存；失败回退 Typeface.DEFAULT

public static void applyTo(TextView textView)              // 仅设置 typeface
public static void setIcon(TextView textView, String iconCode)
// 设置 typeface + 文本；null 安全。标准用法：KeydroidxIcons.setIcon(tv, KeydroidxIcons.ICON_PLAY)

public static IconDrawable createDrawable(Context context, String iconCode,
                                         int sizePx, @ColorInt int color)
// 生成可直接 setBackground/setImageDrawable 的图标 Drawable（菜单项、无 View 场景）
```

```java
public static class IconDrawable extends Drawable { ... }  // 自绘字符的尺寸自适应 Drawable
```

## KeydroidxBatteryDrawable — 电池矢量图标

```java
public KeydroidxBatteryDrawable(Context context)
public void setBatteryState(int pct, boolean charging)
// pct: 0-100 电量百分比；charging: 是否充电中（绘制闪电）。内部 invalidate 自刷新
```
纯 Canvas 绘制的电池外壳+电量格，供状态栏 ImageView 使用（`KeydroidxBaseActivity.registerBatteryReceiver()` 内部即用它）；其余为 Drawable 标准覆写（draw/setAlpha/setColorFilter/getOpacity/getIntrinsicWidth/Height）。

## KeydroidxDashedLineDrawable — 虚线分割线

```java
package io.github.cctyl.nokia.common.ui.drawable;

public KeydroidxDashedLineDrawable(int color, float strokeWidthPx,
                               float dashWidthPx, float dashGapPx)
```
横向虚线（自绘 onDraw，规避 4.4 上 DashPathEffect XML shape 的坑）。典型参数：`(colorDivider, 1f, 4f, 3f)`，View 高度设 6~8dp 居中显示。

## KeydroidxDimens — 尺寸工具

```java
package io.github.cctyl.nokia.common.util;

public static int dp(Resources res, float dpValue)    // dp→px **截断**（(int) 强转，非四舍五入），
                                                      // 保持像素网格对齐；用 res.getDisplayMetrics
                                                      // 规避 Resources.getSystem() 的 DPI 偏差 Bug
public static float dpF(Resources res, float dpValue) // 浮点版本
@Deprecated public static void textSize(TextView tv, float v) // 旧 API；字号一律用 @dimen Token
```

## 相关文档

- 主题热更新如何被基类消费 → [04-base-activity](./04-base-activity.md)
- 高亮在列表中的应用 → [06-list-focus](./06-list-focus.md)
