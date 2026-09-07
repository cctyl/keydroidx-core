# 11 · 反馈上报与日志

> 一站式用户反馈能力：内置诺基亚风格反馈页 + 日志打包 + 签名 HTTP 上传。
> 宿主 APP 五分钟接入，密钥不入库、不进 SDK。

## 核心类

| 类 | 职责 |
|---|---|
| `KeydroidxFeedbackConfig` | 全局配置（上传 URL / 通信密钥 / 应用名 / 版本 / 日志目录） |
| `KeydroidxFeedback` | 门面：`init()` 注册配置、`submit()` 上传 |
| `KeydroidxFeedbackActivity` | SDK 内置复古反馈页，开箱即用 |
| `KeydroidxTextInputFragment` | 全屏文本编辑页（反馈页的联系方式/问题描述编辑器，宿主也可复用） |
| `FeedbackUploader` | 协议实现：设备信息组装、日志 zip 打包、HTTP POST 上传（请求签名在 SDK 内部完成） |
| `DeviceInfoCollector` | 设备信息采集（extras 默认值） |
| `KeydroidxLog` | SDK 内置零依赖文件日志器（对齐桌面架构，支持按天轮转、级别控制与崩溃同步落盘） |

包路径：反馈与安装统计为 `io.github.cctyl.nokia.common.feedback`，日志为 `common.log.KeydroidxLog`（`keycore.feedback` 下同名桥接类已于 2026-09-06 删除；原 `keycore.log` 下的日志桥接类已随 2026-09-06 类名重构删除）；反馈页 `KeydroidxFeedbackActivity` 在 `keycore.ui`（key-core 独有）。

---

## 一、快速接入（三步）

### ① 密钥与服务地址放 `local.properties`（该文件不进 Git）

```properties
FEEDBACK_URL=https://your.server.com
FEEDBACK_SECRET_KEY=<feedback_secret.key 文件里的 hex 字符串>
```

> 只配一个服务端根地址 `FEEDBACK_URL`（不带任何接口路径）。SDK 内部会自动拼接
> 反馈上传路径 `/upload` 与安装统计路径 `/install`，无需也不允许单独配置。
>
> ⚠️ 通信密钥属于生态方分发物。任何情况下不要把它提交进 Git（含历史 commit）、
> 不要写死在源码或示例里。CI 打包时存为 secret 环境变量注入，方式相同。

### ② 宿主 `build.gradle` 注入 BuildConfig（纯 SDK 零三方依赖）

```groovy
def localProps = new Properties()
def f = rootProject.file("local.properties")
if (f.exists()) localProps.load(new FileInputStream(f))

android {
    defaultConfig {
        buildConfigField "String", "FEEDBACK_URL",
            "\"${localProps.getProperty('FEEDBACK_URL', 'http://127.0.0.1')}\""
        buildConfigField "String", "FEEDBACK_SECRET_KEY",
            "\"${localProps.getProperty('FEEDBACK_SECRET_KEY', '')}\""
    }
    buildFeatures { buildConfig true }
}

dependencies {
    // 仅需依赖 keydroidx-key-core，禁止引入 OkHttp、协程等外部重型库
    implementation 'io.github.cctyl.nokia:keydroidx-key-core:1.0.0'
}
```

> 💡 **无 Key 友好性**：若本地未配置密钥，`FEEDBACK_SECRET_KEY` 默认赋空字符串 `""`，**项目编译与核心功能 100% 正常**。仅在提交反馈时提示「反馈功能未配置」，不会崩溃。

### ③ 初始化 + 入口跳转

在 `Application.onCreate` 或入口 Activity 中初始化（一次即可）：

**Kotlin 示例：**
```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // 1. 初始化日志器（自动读取详细日志开关，安装崩溃捕获）
        KeydroidxLog.setTag("MyApp")
        KeydroidxLog.init(this)
        KeydroidxLog.installCrashHandler(this)

        // 2. 初始化反馈能力（传入 null 自动与 KeydroidxLog 目录对齐）
        KeydroidxFeedback.init(
            KeydroidxFeedbackConfig(
                BuildConfig.FEEDBACK_URL,
                BuildConfig.FEEDBACK_SECRET_KEY,
                "myapp", // 应用标识（需与服务端登记的名称一致）
                BuildConfig.VERSION_NAME,
                null
            )
        )
    }
}
```

**Java 示例：**
```java
public class MyApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

        // 1. 初始化日志器
        KeydroidxLog.setTag("MyApp");
        KeydroidxLog.init(this);
        KeydroidxLog.installCrashHandler(this);

        // 2. 初始化反馈能力
        KeydroidxFeedback.init(new KeydroidxFeedbackConfig(
                BuildConfig.FEEDBACK_URL,
                BuildConfig.FEEDBACK_SECRET_KEY,
                "myapp",
                BuildConfig.VERSION_NAME,
                null)); // null 自动对齐 Android/data/<包名>/files/log
    }
}
```

**拉起反馈页：**
```java
// 宿主在任意菜单项或设置项中直接拉起
startActivity(new Intent(this, KeydroidxFeedbackActivity.class));
```

Manifest 权限（必须）：

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

---

## 二、反馈页功能（`KeydroidxFeedbackActivity`）

继承 key-core 的 `KeydroidxBaseActivity`，自动获得统一骨架、点阵字体、主题跟随与桌面配置热同步：

- **问题类型**：值选择器行，LEFT/RIGHT 快速切换或 CENTER 弹出选项菜单（写入 extras 的 `feedback_type`）；
- **联系方式 / 问题描述**：必填。CENTER 进入全屏编辑页 `KeydroidxTextInputFragment`
  （输入区占满内容区、物理键盘直输、软键 确定/返回）；为空提交时自动定位到对应行并打开编辑页引导填写；
  编辑页压入返回栈，确定后回调写回并出栈恢复焦点；
- **主题跟随**：页面背景、行卡片、焦点高亮、文字颜色全部取自
  `KeydroidxClient.getCurrentTheme()`，与桌面当前主题实时一致（onResume 重取）；
- **附带运行日志**：强制开启不可关闭，行内实时显示「N 个文件 / X KB」，
  超限时提示「过大仅保留最新」；
- 提交中按钮置灰防连点；成功 Toast 后自动关闭；失败提示手动重试；
- 物理按键适配：方向键逐行移动焦点（不循环）、类型行 LEFT/RIGHT 切值、
  CENTER 激活当前行；左软键提交、右软键返回（按当前按键绑定动态解析）。

未调用 `init()` 或配置无效时，页面提交会给出明确错误提示。

---

## 三、生态标准日志器（`KeydroidxLog`）与目录约定

### 1. 统一日志目录

```
默认标准：Android/data/<包名>/files/log/yyyyMMdd.log
覆盖方式：KeydroidxFeedbackConfig 最后一个参数传入自定义 File
```

`KeydroidxFeedback` 与 `KeydroidxLog` 均以 `Android/data/<包名>/files/log` 为生态标准日志目录（与 `keydroidx-launcher` 桌面端完全对齐）。

### 2. `KeydroidxLog` 快速集成

宿主无需引入第三方日志框架，直接在 `Application.onCreate` 初始化：

```java
// 1. 设置主 TAG
KeydroidxLog.setTag("MyApp");

// 2. 初始化：自动读取详细日志开关，并设置文件落盘级别（开启=DEBUG，关闭=ERROR）
KeydroidxLog.init(this);

// 3. 安装崩溃落盘处理器：未捕获异常发生时，同步将堆栈刷入当日日志文件
KeydroidxLog.installCrashHandler(this);
```

### 3. 详细日志级别与开关控制

`KeydroidxLog` 提供了通用持久化开关，对齐桌面端设计：

- **详细日志关闭（默认 / Release 正式版）**：`fileMinLevel = Log.ERROR`。平时仅在发生错误（`KeydroidxLog.e`）与应用崩溃（`FATAL`）时落盘，零性能开销、文件体积极小。
- **详细日志开启（排查模式 / Debug 构建）**：`fileMinLevel = Log.DEBUG`。记录所有业务 `DEBUG`、`INFO`、`WARN` 日志。
- **通用 API**：
  ```java
  // 读取当前开关（未设置时，Debug 构建默认 true，Release 构建默认 false）
  boolean enabled = KeydroidxLog.isDetailedLogEnabled(context);

  // 设置开关（自动持久化并实时更新内存中的落盘级别）
  KeydroidxLog.setDetailedLogEnabled(context, true);
  ```

---

### 4. 接入实战：在主界面或设置页增加「详细日志」开关

推荐直接放在主界面或设置页的左软键选项菜单中（如 `KeydroidxOptionsDialog`），方便用户排查问题时一键切换：

```kotlin
// 构建选项菜单列表
val isDetailedLog = KeydroidxLog.isDetailedLogEnabled(this)
val logTitle = if (isDetailedLog) "详细日志：开" else "详细日志：关"

val items = listOf(
    KeydroidxOptionsDialog.OptionItem(1, "意见反馈", KeydroidxIcons.ICON_EDIT),
    KeydroidxOptionsDialog.OptionItem(2, logTitle, KeydroidxIcons.ICON_SETTINGS),
    KeydroidxOptionsDialog.OptionItem(3, "关于", KeydroidxIcons.ICON_INFO)
)

KeydroidxOptionsDialog.show(this, "选项", items) { item ->
    when (item.id) {
        1 -> startActivity(Intent(this, KeydroidxFeedbackActivity::class.java))
        2 -> {
            val next = !KeydroidxLog.isDetailedLogEnabled(this)
            KeydroidxLog.setDetailedLogEnabled(this, next)
            val tip = if (next) "已开启详细日志（记录调试信息）" else "已关闭详细日志（仅记录错误与崩溃）"
            Toast.makeText(this, tip, Toast.LENGTH_SHORT).show()
        }
        3 -> startActivity(Intent(this, AboutActivity::class.java))
    }
}
```

---

### 5. 零成本桥接已有工程中的 `android.util.Log`

如果宿主工程中已有大量历史 `android.util.Log.d/i/w/e` 调用，无需逐一重构代码。推荐在宿主中建立一个 `NLog.kt` 门面：

```kotlin
package com.example.myapp.util

import io.github.cctyl.nokia.common.log.KeydroidxLog

object NLog {
    @JvmStatic fun v(tag: String, msg: String) = KeydroidxLog.v(tag, msg)
    @JvmStatic fun d(tag: String, msg: String) = KeydroidxLog.d(tag, msg)
    @JvmStatic fun i(tag: String, msg: String) = KeydroidxLog.i(tag, msg)
    @JvmStatic fun w(tag: String, msg: String, tr: Throwable? = null) = KeydroidxLog.w(tag, msg, tr)
    @JvmStatic fun e(tag: String, msg: String, tr: Throwable? = null) = KeydroidxLog.e(tag, msg, tr)
}
```

然后在业务 Kotlin 文件头部将 `import android.util.Log` 替换为：
```kotlin
import com.example.myapp.util.NLog as Log
```
这样文件内部现有的 `Log.d("Tag", "msg")`、`Log.e("Tag", "msg", tr)` 会自动且无缝地重定向到 `KeydroidxLog`，全部享受等级过滤、按天轮转与文件落盘能力！

---

### 6. 业务代码规范打点示例

```java
KeydroidxLog.d("Player", "切换歌曲: id=1001");
KeydroidxLog.i("Network", "请求完成: code=200");
KeydroidxLog.w("Cache", "缓存未命中");
KeydroidxLog.e("Auth", "登录失败", exception);
```

### 5. 打包大小策略

| 规则 | 值 |
|---|---|
| 协议上限 | 10 MB |
| SDK 打包预算 | **9 MB**（给 meta / 协议头留余量） |
| 单文件上限 | 8 MB（超出部分截断） |
| 超限裁剪顺序 | 按 `lastModified` **越新越优先**，装不下的旧文件丢弃 |
| 全部装不下 | 发空 zip |

打包统计通过 `FeedbackUploader.ZipResult` 返回（`includedFiles` / `skippedFiles`
/ `originalTotalBytes`），供 UI 展示裁剪情况。

### 日志脱敏建议

日志可能包含用户敏感信息（手机号、token 等）。建议宿主写日志前先做正则替换脱敏；
SDK 不代做脱敏（无法理解业务语义）。

---

## 四、自动附带的设备信息（extras）

| 字段 | 来源 |
|---|---|
| device_brand / device_model / device_manufacturer | `Build.*` |
| android_version / android_api | `Build.VERSION.*` |
| supported_abis（或 cpu_abi） | `Build.SUPPORTED_ABIS` |
| cpu_cores | `Runtime.availableProcessors()` |
| cpu_model / cpu_hardware | `/proc/cpuinfo` |
| cpu_max_freq_mhz / cpu_min_freq_mhz | `/sys/.../cpufreq/` |
| gpu_renderer / gpu_vendor / gpu_version | EGL 临时上下文 GL strings |
| total_mem_mb / avail_mem_mb / memory_class_mb | `ActivityManager.MemoryInfo` |
| free_disk_mb / total_disk_mb | `StatFs` |
| screen_px / screen_density | `DisplayMetrics` |
| battery_pct / charging | `ACTION_BATTERY_CHANGED` 粘性广播 |
| locale / uptime_days | `Locale` / `SystemClock` |
| android_id | `Settings.Secure.ANDROID_ID` |
| app_package / app_version_name / app_version_code | `PackageManager` |

全部来自系统公开 API，不含 IMEI、MAC 地址、位置等隐私敏感数据。
`android_id` 为系统公开设备标识（Android 8+ 按签名密钥+设备唯一，无额外权限）。
宿主可通过 `submit()` 的 extraInfo 参数追加自己的字段（值支持 Number/Boolean/String，
String 自动截断 200 字符，序列化后 extras 总量 ≤4096 字节）。

---

## 五、排查问题（客户端视角）

- **请求方式**：`POST`，地址即 `KeydroidxFeedbackConfig.resolveUploadUrl()`（`baseUrl + /upload`）；
- **请求体**：zip 压缩后的字节流（无日志附件时长度为 0）；
- **请求签名**：由 `FeedbackUploader` 内部完成，宿主无需参与，**协议细节不对外公开**；
- **失败判定**：非 200 一律视为失败，静默处理；
- **禁止自动重试**：重试只会加剧失败，SDK 不做自动重试，UI 提示用户手动再试即可；
- 客户端诊断日志 tag 为 `FeedbackUploader` / `KeydroidxFeedback`。

> 自建服务端的同学请直接参考 `log_upload/docs/CLIENT_API.md` 与 `log_upload/docs/PROTOCOL.md`。

---


