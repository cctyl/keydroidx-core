# 14 · 检查更新（GitHub Release 版本对比）

> 通过 GitHub Releases API 查询远端最新版本号，与宿主当前 `versionName` 对比。
> GitHub 网络不畅、检查失败时，引导用户前往百度网盘手动下载。
> 仓库地址由调用者传入，因此**任何宿主应用**都能复用同一套检查逻辑。

## 一、设计定位

作为通用 common 库，检查更新采用 **「无 UI 核心逻辑 + 可选复古弹窗」** 两层设计：

| 层 | 类 | 适用场景 |
|---|---|---|
| 纯逻辑（无 UI） | `KeydroidxUpdateChecker` | 宿主自建更新界面 / 只想拿结果数据 |
| 可选 UI | `KeydroidxUpdateDialog` | 宿主不想自建界面，一行调用完成全流程 |

数据模型与配置对象：

| 类 | 职责 |
|---|---|
| `KeydroidxUpdateConfig` | 检查配置（链式装配）：仓库地址必填，其余全部有默认值 |
| `KeydroidxUpdateResult` | 检查结果：`UPDATE_AVAILABLE` / `UP_TO_DATE` / `FAILED` 三态 |
| `KeydroidxUpdateInfo` | 远端最新版本信息（tag、APK 直链、更新说明等） |

所在包：`io.github.cctyl.nokia.common.update`（keydroidx-common 模块，零第三方依赖）。

## 二、检查流程

```text
check(context, config, callback)
    │ 后台单线程池执行
    ▼
GET https://api.github.com/repos/{owner}/{repo}/releases/latest
    │ org.json 解析（Android 内置，零依赖）
    ▼
KeydroidxUpdateInfo { version, downloadUrl(apk 资产), changelog, ... }
    │ compareVersion(currentVersion, 远端 version)
    ▼
UPDATE_AVAILABLE / UP_TO_DATE ──主线程回调──▶ 宿主
    │
    └─ 任何异常（无网/超时/403/404/解析失败）→ FAILED
         宿主引导用户前往 fallbackUrl（默认百度网盘）
```

要点：

- **仓库地址解析**：兼容 `https://github.com/a/b`、`github.com/a/b/`、`a/b`、`git@github.com:a/b.git`、带 `/issues` 等子路径，统一解析出 `owner/repo`。
- **当前版本号**：`config.currentVersion` 显式配置优先；未配置时自动读宿主 `PackageInfo.versionName`。
- **APK 直链**：从 Release `assets` 里挑首个匹配资产——默认匹配 `*.apk`（忽略大小写），`setApkAssetKeyword("xxx")` 可改为 contains 匹配指定命名；无 APK 资产时 `resolveDownloadUrl()` 退回 Release 页面地址。
- **预发布**：`setIncludePreRelease(true)` 时改查 `/releases` 列表取最新非 draft（含 pre-release）。
- **版本号比较**：`compareVersion(a, b)` 支持语义化版本（`1.10 > 1.9`）、`v` 前缀、数字/字母边界切分（`1.2.3-beta2`），修饰段小于正式段（`1.2.3-beta < 1.2.3`）。
- **失败归一**：所有异常统一收敛为 `FAILED`，绝不抛出、绝不崩溃；HTTP 请求带 `User-Agent`（GitHub API 缺失 UA 会 403）。
- **权限**：宿主需声明 `android.permission.INTERNET`。

## 三、快速接入

### ① 纯回调（宿主自建 UI）

```java
KeydroidxUpdateChecker.check(this,
        new KeydroidxUpdateConfig("https://github.com/cctyl/keydroidx-launcher"),
        result -> {
            switch (result.status) {
                case UPDATE_AVAILABLE:
                    // result.info.version / changelog / resolveDownloadUrl()
                    break;
                case UP_TO_DATE:
                    break;
                case FAILED:
                    // 引导用户前往备用下载地址
                    // config.getFallbackUrl() 或 KeydroidxUpdateConfig.DEFAULT_FALLBACK_URL
                    break;
            }
        });
```

回调保证在主线程派发；内部异常全部吞掉，不会影响宿主正常运行。

### ② 一站式弹窗（推荐，复古风格）

```java
// Activity 里一行调用：检查 → 按结果弹窗 → 跳转
KeydroidxUpdateDialog.checkAndShow(this,
        new KeydroidxUpdateConfig("https://github.com/cctyl/keydroidx-launcher"));
```

弹窗行为：

| 结果 | 标题 | 左软键 | 右软键 |
|---|---|---|---|
| 有新版本 | 发现新版本（展示当前/最新版本号 + 更新说明摘要） | 「更新」→ 浏览器打开 APK 直链（无 APK 资产时打开 Release 页） | 取消 |
| 已是最新 | 已是最新版本 | 确认 | 关闭 |
| 检查失败 | 检查更新失败 | 「网盘下载」→ 跳转备用地址 | 取消 |

### ③ 配置项一览

```java
KeydroidxUpdateConfig config = new KeydroidxUpdateConfig("https://github.com/cctyl/keydroidx-launcher")
        .setFallbackUrl(KeydroidxUpdateConfig.DEFAULT_FALLBACK_URL) // 失败备用下载地址
        .setCurrentVersion(null)         // 不传则自动读宿主 versionName
        .setIncludePreRelease(false)     // 是否把 pre-release 纳入检查
        .setApkAssetKeyword(null)        // APK 资产匹配关键字；null = 任意 *.apk
        .setTimeoutMs(10_000);           // 连接/读取超时
```

### ④ 检查结果对象

```java
public class KeydroidxUpdateResult {
    public enum Status { UPDATE_AVAILABLE, UP_TO_DATE, ERROR }
    public final Status status;
    @Nullable public final KeydroidxUpdateInfo info;   // 仅 UPDATE_AVAILABLE 时非空
    public final String currentVersion;            // 参与对比的当前版本
    @Nullable public final String error;           // 失败原因描述
}

public class KeydroidxUpdateInfo {
    public final String version;      // 解析出的语义版本（去 v 前缀）
    public final String tagName;      // Release tag 原文
    public final String releaseName;  // Release 标题
    public final String htmlUrl;      // Release 页面地址
    public final String downloadUrl;  // APK 资产直链（可能为空）
    public final String assetName;    // APK 资产文件名
    public final long assetSize;      // APK 字节数
    public final String changelog;    // Release body 原文
    public final String publishTime;  // 发布时间（ISO 字符串）

    public boolean isNewerThan(String currentVersion)
    public String resolveDownloadUrl()   // downloadUrl 为空时回退 htmlUrl
}
```

### ⑤ 自检后直接弹窗

```java
KeydroidxUpdateChecker.check(context, config, result -> {
    // 业务可先自行处理 result，再决定是否弹窗
    KeydroidxUpdateDialog.showResult(context, result, config.getFallbackUrl());
});
```

### ⑥ 实现细节

- 请求地址：`{API_BASE}/{repoPath}/releases?per_page=5`，只看最近 5 个 Release；
- 响应体上限 **256KB**，超限视为异常响应；
- 版本比较：`KeydroidxUpdateChecker.compareVersion(a, b)`（支持预发布后缀），也可独立使用。

## 四、自动检查更新（每日一次 + 进入应用弹窗提醒）

> 2026-09 新增：在手动检查之上提供「每日一次后台检查 + 进入应用后弹窗提醒」的完整方案。
> 不发系统通知（功能机场景下弹窗体验更好），弹窗复用符合 FEATURE_PHONE_UI_SPEC 的紧凑组件。

### 1. 涉及类

| 类 | 职责 |
|---|---|
| `KeydroidxAutoUpdateChecker` | 门面：每日节流检查 + 落「待提醒」记录 + 弹窗展示 |
| `KeydroidxUpdatePrefs` | 持久化（SP 文件 `nokia_update_prefs`）：开关、忽略版本、按天节流、待提醒记录 |

### 2. 行为规则（全部自动生效，宿主无需关心）

| 层级 | 规则 |
|---|---|
| 检查开关 | `auto_check_enabled` **默认开启**，用户可关（关于页开关卡片 / 宿主自建设置项） |
| 检查节流 | 一天最多成功检查一次（本地时区按天记）；失败不记当天，下次进程启动重试 |
| 提醒记录 | 发现新版本且未被忽略 → 存 `pending_update`（含 version / changelog / APK 直链，changelog 截断 400 字符） |
| 弹窗节流 | **一天最多弹一次**——弹出即记当天，无论用户选更新/忽略还是 BACK 关闭 |
| 忽略版本 | 「忽略此版本」= 该版本永不再提醒，直到出现更新的 Release（`compareVersion` 判定） |
| 过期清理 | 待提醒版本 ≤ 宿主当前版本时自动清掉（宿主升级后不留垃圾数据） |

### 3. 接入（宿主两行代码）

```java
// ① Application.attachBaseContext 主进程块（建议延迟数百毫秒以上避开冷启动）：
KeydroidxAutoUpdateChecker.checkOncePerDay(this,
        new KeydroidxUpdateConfig("https://github.com/<owner>/<repo>"), 8000);

// ② 主界面 Activity.onCreate（首帧就绪后延迟约 1.5s，避免与启动期权限弹窗抢焦点）：
KeydroidxAutoUpdateChecker.showPendingUpdateDialog(this, updateConfig);
```

**关于 ② 的两次尝试**：① 的检查在后台延迟 8s + 网络耗时，普遍晚于首帧。
推荐像 launcher 一样 postDelayed 两次（1.5s 处理「上次检查遗留的待提醒」、
15s 处理「本次启动刚完成的检查」），并用「本次会话已弹过」标志防止重弹——
不过弹窗本身已有 `last_remind_day` 按天节流兜底，即使每次 onCreate 都调用也不会骚扰用户。

### 4. 弹窗交互（`KeydroidxUpdateReminderDialog`）

与 `KeydroidxOptionsDialog` 同级高度（标题栏 26dp + 固定 96dp 可滚动内容区 + 软键栏 26dp），
更新说明长时在内容区内滚动，不会把软键栏挤出屏幕：

| 按键 | 行为 |
|---|---|
| LSK / CENTER | 「更新」→ 浏览器打开 APK 直链（无直链回退 Release 页 / 百度网盘） |
| RSK | 「忽略此版本」→ 该版本永不再提醒 |
| UP / DOWN | 滚动更新说明（触屏滑动同样可滚） |
| BACK | 仅关闭（当天不再弹，次日再提醒） |

changelog 展示前会做清洗：去 markdown 标题井号/加粗星号、去空行、超长截断加省略号。

### 5. 宿主设置项（可选）

- **关于页（零成本）**：宿主使用标准 `KeydroidxAboutFragment` 且 `setShowUpdateCheck(true)` 时，
  「自动检查更新」开关卡片自动出现在「检查更新」下方，读写的就是 `KeydroidxUpdatePrefs`；
- **自建设置项**：直接读写 `KeydroidxUpdatePrefs.isAutoCheckEnabled/setAutoCheckEnabled`。

### 6. 宿主注意事项（红线）

1. **`attachBaseContext` 阶段 `getApplicationContext()` 返回 null**（Application 尚未回填），
   common 已在内部兼容（回退用传入 context）；宿主不要在该阶段自行调用 `getApplicationContext()`；
2. **flavor 渠道后缀必须剥离**：`1.3.2-open` 之类的 versionName 会被 semver 当成 pre-release 修饰段，
   与 GitHub 裸 tag（`1.3.2`）比较时判为「更小」→ 每天误报更新。接入方式：
   ```java
   config.setCurrentVersion(versionName.replaceFirst("-" + Pattern.quote(BuildConfig.FLAVOR) + "(-\\d+)?$", ""));
   ```
   无 flavor 的应用可跳过（不调 `setCurrentVersion`，默认读 PackageInfo）；
3. 宿主需声明 `android.permission.INTERNET`；弹窗不依赖任何通知权限。

### 7. 测试技巧（不污染用户数据）

debug 包可用 `run-as` 直接改 SP，无需 `pm clear`、无需压低版本号联网等待：

```bash
# 构造待提醒记录（pending_update 为 JSON：version/changelog/download_url）
adb shell run-as <pkg> cp /data/local/tmp/prefs.xml shared_prefs/nokia_update_prefs.xml
```

触发链路日志 tag：`KeydroidxAutoUpdate`（`adb logcat -d -s KeydroidxAutoUpdate:*`）。

## 五、测试

- 单元测试：`keydroidx-common/src/test/.../update/KeydroidxUpdateCheckerTest.java`
  覆盖仓库地址解析、版本号比较（含预发布/空值）、Release JSON 解析。
- 示例 App：sample 主界面新增「检查更新（复古弹窗）」与「检查更新（纯回调 API）」
  两个按钮，使用 `https://github.com/cctyl/keydroidx-launcher` 实测
  （该仓库最新 Release 携带 `*.apk` 资产，可验证直链提取与版本对比）。
