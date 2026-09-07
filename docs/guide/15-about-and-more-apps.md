# 15 · 关于页与更多应用

主包：`io.github.cctyl.nokia.common.ui.about`

SDK 内置诺基亚风格的「关于」页与「更多应用」推荐位，配置驱动、开箱即用。

## KeydroidxAboutConfig — 关于页配置

纯 POJO + 链式 setter，实现 `Serializable`（可通过 Intent 传递）。

### 快速创建

```java
// 从应用包名/版本自动填充 appName、versionName、appIconRes
KeydroidxAboutConfig config = KeydroidxAboutConfig.createDefault(context);
```

### 可配置项

| 方法 | 说明 |
|:---|:---|
| `setAppName(String)` / `setVersionName(String)` / `setAppIconRes(int)` | 应用名 / 版本 / 图标 |
| `setDescription(String)` | 一句话简介 |
| `setAuthor(String)` | 作者 |
| `setRepoUrl(String)` | 开源仓库地址（可跳转） |
| `setVideoUrl(String)` | 演示视频地址 |
| `setAcknowledgements(String)` | 致谢文本 |
| `setExtraStatement(String)` | 附加声明（如免责/隐私说明） |

### 内置功能开关

| 方法 | 说明 |
|:---|:---|
| `setShowDetailedLogToggle(boolean)` | 显示「详细日志」开关（写 `KeydroidxLog.setDetailedLogEnabled`） |
| `setShowUpdateCheck(boolean)` + `setUpdateCurrentVersion(String)` | 显示「检查更新」入口，联动 [14-update-check](./14-update-check.md) |
| `setShowMoreApps(boolean)` | 显示「更多应用」入口 |
| `setMoreApps(List<KeydroidXApps.App>)` | 推荐应用列表数据 |

### 附加链接

```java
config.addExtraLink("用户协议", "https://example.com/terms", "description");
// LinkItem(title, url, glyph)：glyph 为 MaterialIcons 字符，可空
```

## KeydroidxAboutFragment — 关于页

继承 `KeydroidxScrollPageFragment`，自动获得长文滚动与按键导航。

```java
// 打开（在 KeydroidxBaseActivity 体系内）
openFragment(KeydroidxAboutFragment.newInstance(config));
```

「更多应用」入口被点击时，Fragment 内部会通过 `KeydroidxPageHost.openFragment()` 自动推入 `KeydroidxMoreAppsFragment`，无需业务处理。

## KeydroidxMoreAppsFragment — 更多应用列表

继承 `KeydroidxListPageFragment`（循环焦点导航 + 高亮自动完成）。

```java
public static KeydroidxMoreAppsFragment newInstance(@Nullable List<KeydroidXApps.App> apps)
```

选中某项后跳转其下载地址。数据通常由服务端下发；列表为空时显示空态文案。

## 完整示例

```java
KeydroidxAboutConfig config = KeydroidxAboutConfig.createDefault(this)
        .setDescription("KeydroidX 生态的复古音乐播放器")
        .setRepoUrl("https://github.com/your/repo")
        .setShowDetailedLogToggle(true)
        .setShowUpdateCheck(true)
        .setUpdateCurrentVersion(BuildConfig.VERSION_NAME)
        .setShowMoreApps(true);

openFragment(KeydroidxAboutFragment.newInstance(config));
```

> 独立 App 也可直接用 key-core 的 `KeydroidxAboutActivity.start(context, config)`，见 [16-ready-made-activities](./16-ready-made-activities.md)。

## 相关文档

- 更新检查接入 → [14-update-check](./14-update-check.md)
- 详细日志开关 → [11-feedback](./11-feedback.md)
