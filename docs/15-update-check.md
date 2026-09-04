# 15 · 检查更新（GitHub Release 版本对比）

> 通过 GitHub Releases API 查询远端最新版本号，与宿主当前 `versionName` 对比。
> GitHub 网络不畅、检查失败时，引导用户前往百度网盘手动下载。
> 仓库地址由调用者传入，因此**任何宿主应用**都能复用同一套检查逻辑。

## 一、设计定位

作为通用 common 库，检查更新采用 **「无 UI 核心逻辑 + 可选复古弹窗」** 两层设计：

| 层 | 类 | 适用场景 |
|---|---|---|
| 纯逻辑（无 UI） | `NokiaUpdateChecker` | 宿主自建更新界面 / 只想拿结果数据 |
| 可选 UI | `NokiaUpdateDialog` | 宿主不想自建界面，一行调用完成全流程 |

数据模型与配置对象：

| 类 | 职责 |
|---|---|
| `NokiaUpdateConfig` | 检查配置（链式装配）：仓库地址必填，其余全部有默认值 |
| `NokiaUpdateResult` | 检查结果：`UPDATE_AVAILABLE` / `UP_TO_DATE` / `FAILED` 三态 |
| `NokiaUpdateInfo` | 远端最新版本信息（tag、APK 直链、更新说明等） |

所在包：`io.github.cctyl.nokia.common.update`（nokia-common 模块，零第三方依赖）。

## 二、检查流程

```text
check(context, config, callback)
    │ 后台单线程池执行
    ▼
GET https://api.github.com/repos/{owner}/{repo}/releases/latest
    │ org.json 解析（Android 内置，零依赖）
    ▼
NokiaUpdateInfo { version, downloadUrl(apk 资产), changelog, ... }
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
NokiaUpdateChecker.check(this,
        new NokiaUpdateConfig("https://github.com/cctyl/keydroidx-launcher"),
        result -> {
            switch (result.status) {
                case UPDATE_AVAILABLE:
                    // result.info.version / changelog / resolveDownloadUrl()
                    break;
                case UP_TO_DATE:
                    break;
                case FAILED:
                    // 引导用户前往备用下载地址
                    // config.getFallbackUrl() 或 NokiaUpdateConfig.DEFAULT_FALLBACK_URL
                    break;
            }
        });
```

回调保证在主线程派发；内部异常全部吞掉，不会影响宿主正常运行。

### ② 一站式弹窗（推荐，复古风格）

```java
// Activity 里一行调用：检查 → 按结果弹窗 → 跳转
NokiaUpdateDialog.checkAndShow(this,
        new NokiaUpdateConfig("https://github.com/cctyl/keydroidx-launcher"));
```

弹窗行为：

| 结果 | 标题 | 左软键 | 右软键 |
|---|---|---|---|
| 有新版本 | 发现新版本（展示当前/最新版本号 + 更新说明摘要） | 「更新」→ 浏览器打开 APK 直链（无 APK 资产时打开 Release 页） | 取消 |
| 已是最新 | 已是最新版本 | 确认 | 关闭 |
| 检查失败 | 检查更新失败 | 「网盘下载」→ 跳转备用地址 | 取消 |

### ③ 配置项一览

```java
NokiaUpdateConfig config = new NokiaUpdateConfig("https://github.com/cctyl/keydroidx-launcher")
        .setFallbackUrl(NokiaUpdateConfig.DEFAULT_FALLBACK_URL) // 失败备用下载地址
        .setCurrentVersion(null)         // 不传则自动读宿主 versionName
        .setIncludePreRelease(false)     // 是否把 pre-release 纳入检查
        .setApkAssetKeyword(null)        // APK 资产匹配关键字；null = 任意 *.apk
        .setTimeoutMs(10_000);           // 连接/读取超时
```

## 四、测试

- 单元测试：`nokia-common/src/test/.../update/NokiaUpdateCheckerTest.java`
  覆盖仓库地址解析、版本号比较（含预发布/空值）、Release JSON 解析。
- 示例 App：sample 主界面新增「检查更新（复古弹窗）」与「检查更新（纯回调 API）」
  两个按钮，使用 `https://github.com/cctyl/keydroidx-launcher` 实测
  （该仓库最新 Release 携带 `*.apk` 资产，可验证直链提取与版本对比）。
