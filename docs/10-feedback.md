# 10 · 反馈上报（KDFB）

> 一站式用户反馈能力：内置诺基亚风格反馈页 + 日志打包 + Ed25519 签名上传。
> 宿主 APP 五分钟接入，密钥不入库、不进 SDK。

## 核心类

| 类 | 职责 |
|---|---|
| `NokiaFeedbackConfig` | 全局配置（服务地址 / 端口 / 私钥 / 应用名 / 版本 / 日志目录） |
| `NokiaFeedback` | 门面：`init()` 注册配置、`submit()` 上传 |
| `NokiaFeedbackActivity` | SDK 内置复古反馈页，开箱即用 |
| `NokiaTextInputActivity` | 全屏文本输入页（反馈页的联系方式/问题描述编辑器，宿主也可复用） |
| `KdfbUploader` | 协议实现：meta 组装、日志 zip 打包、报文签名与 TCP 收发 |
| `DeviceInfoCollector` | 设备信息采集（extras 默认值） |
| `NokiaEd25519` | 纯 Java Ed25519 签名（零第三方依赖，已过 RFC 8032 测试向量） |
| `NokiaLog` | SDK 内置零依赖文件日志器（对齐桌面架构，支持按天轮转、级别控制与崩溃同步落盘） |

包路径统一为 `io.github.cctyl.nokia.keycore.feedback`、`.log.NokiaLog` 与 `.ui.NokiaFeedbackActivity`。

---

## 一、快速接入（三步）

### ① 密钥与服务地址放 `local.properties`（该文件不进 Git）

```properties
KDFB_SERVER_HOST=your.server.com
KDFB_SERVER_PORT=9421
KDFB_PRIVATE_KEY=<feedback_priv.key 文件里的一行 hex>
```

> ⚠️ 私钥属于生态方分发物。任何情况下不要把它提交进 Git（含历史 commit）、
> 不要写死在源码或示例里。CI 打包时存为 secret 环境变量注入，方式相同。

### ② 宿主 `build.gradle` 注入 BuildConfig（纯 SDK 零三方依赖）

```groovy
def localProps = new Properties()
def f = rootProject.file("local.properties")
if (f.exists()) localProps.load(new FileInputStream(f))

android {
    defaultConfig {
        buildConfigField "String", "KDFB_SERVER_HOST",
            "\"${localProps.getProperty('KDFB_SERVER_HOST', '127.0.0.1')}\""
        buildConfigField "int", "KDFB_SERVER_PORT",
            "${localProps.getProperty('KDFB_SERVER_PORT', '9421')}"
        buildConfigField "String", "KDFB_PRIVATE_KEY",
            "\"${localProps.getProperty('KDFB_PRIVATE_KEY', '')}\""
    }
    buildFeatures { buildConfig true }
}

dependencies {
    // 仅需依赖 nokia-key-core，禁止引入 BouncyCastle、OkHttp、协程等外部库
    implementation 'io.github.cctyl.nokia:nokia-key-core:1.0.0'
}
```

> 💡 **无 Key 友好性**：若本地未配置私钥，`KDFB_PRIVATE_KEY` 默认赋空字符串 `""`，**项目编译与核心功能 100% 正常**。仅在提交反馈时提示「反馈功能未配置」，不会崩溃。

### ③ 初始化 + 入口跳转

在 `Application.onCreate` 或入口 Activity 中初始化（一次即可）：

**Kotlin 示例：**
```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // 1. 初始化日志器（自动读取详细日志开关，安装崩溃捕获）
        NokiaLog.setTag("MyApp")
        NokiaLog.init(this)
        NokiaLog.installCrashHandler(this)

        // 2. 初始化反馈能力（传入 null 自动与 NokiaLog 目录对齐）
        NokiaFeedback.init(
            NokiaFeedbackConfig(
                BuildConfig.KDFB_SERVER_HOST,
                BuildConfig.KDFB_SERVER_PORT,
                BuildConfig.KDFB_PRIVATE_KEY,
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
        NokiaLog.setTag("MyApp");
        NokiaLog.init(this);
        NokiaLog.installCrashHandler(this);

        // 2. 初始化反馈能力
        NokiaFeedback.init(new NokiaFeedbackConfig(
                BuildConfig.KDFB_SERVER_HOST,
                BuildConfig.KDFB_SERVER_PORT,
                BuildConfig.KDFB_PRIVATE_KEY,
                "myapp",
                BuildConfig.VERSION_NAME,
                null)); // null 自动对齐 Android/data/<包名>/log
    }
}
```

**拉起反馈页：**
```java
// 宿主在任意菜单项或设置项中直接拉起
startActivity(new Intent(this, NokiaFeedbackActivity.class));
```

Manifest 权限（必须）：

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

---

## 二、反馈页功能（`NokiaFeedbackActivity`）

继承 `NokiaBaseActivity`，自动获得 240dp 视口、点阵字体、主题跟随：

- **问题类型**：值选择器行，LEFT/RIGHT 快速切换或 CENTER 弹出选项菜单（写入 extras 的 `feedback_type`）；
- **联系方式 / 问题描述**：必填。CENTER 进入全屏输入页 `NokiaTextInputActivity`
  （大输入区、物理键盘直输、软键 确定/返回）；为空提交时自动定位到对应行并打开输入页引导填写；
- **主题跟随**：页面背景、行卡片、焦点高亮、文字颜色全部取自
  `NokiaClient.getCurrentTheme()`，与桌面当前主题实时一致（onResume 重取）；
- **附带运行日志**：强制开启不可关闭，行内实时显示「N 个文件 / X KB」，
  超限时提示「过大仅保留最新」；
- 提交中按钮置灰防连点；成功 Toast 后自动关闭；失败提示手动重试；
- 物理按键适配：方向键逐行移动焦点（不循环）、类型行 LEFT/RIGHT 切值、
  CENTER 激活当前行；左软键提交、右软键返回（按当前按键绑定动态解析）。

未调用 `init()` 或配置无效时，页面提交会给出明确错误提示。

---

## 三、生态标准日志器（`NokiaLog`）与目录约定

### 1. 统一日志目录

```
默认标准：Android/data/<包名>/log/yyyyMMdd.log
覆盖方式：NokiaFeedbackConfig 最后一个参数传入自定义 File
```

`NokiaFeedback` 与 `NokiaLog` 均以 `Android/data/<包名>/log` 为生态标准日志目录（与 `keydroidx-launcher` 桌面端完全对齐）。

### 2. `NokiaLog` 快速集成

宿主无需引入第三方日志框架，直接在 `Application.onCreate` 初始化：

```java
// 1. 设置主 TAG
NokiaLog.setTag("MyApp");

// 2. 初始化：自动读取详细日志开关，并设置文件落盘级别（开启=DEBUG，关闭=ERROR）
NokiaLog.init(this);

// 3. 安装崩溃落盘处理器：未捕获异常发生时，同步将堆栈刷入当日日志文件
NokiaLog.installCrashHandler(this);
```

### 3. 详细日志级别与开关控制

`NokiaLog` 提供了通用持久化开关，对齐桌面端设计：

- **详细日志关闭（默认 / Release 正式版）**：`fileMinLevel = Log.ERROR`。平时仅在发生错误（`NokiaLog.e`）与应用崩溃（`FATAL`）时落盘，零性能开销、文件体积极小。
- **详细日志开启（排查模式 / Debug 构建）**：`fileMinLevel = Log.DEBUG`。记录所有业务 `DEBUG`、`INFO`、`WARN` 日志。
- **通用 API**：
  ```java
  // 读取当前开关（未设置时，Debug 构建默认 true，Release 构建默认 false）
  boolean enabled = NokiaLog.isDetailedLogEnabled(context);

  // 设置开关（自动持久化并实时更新内存中的落盘级别）
  NokiaLog.setDetailedLogEnabled(context, true);
  ```

---

### 4. 接入实战：在主界面或设置页增加「详细日志」开关

推荐直接放在主界面或设置页的左软键选项菜单中（如 `NokiaOptionsDialog`），方便用户排查问题时一键切换：

```kotlin
// 构建选项菜单列表
val isDetailedLog = NokiaLog.isDetailedLogEnabled(this)
val logTitle = if (isDetailedLog) "详细日志：开" else "详细日志：关"

val items = listOf(
    NokiaOptionsDialog.OptionItem(1, "意见反馈", NokiaIcons.ICON_EDIT),
    NokiaOptionsDialog.OptionItem(2, logTitle, NokiaIcons.ICON_SETTINGS),
    NokiaOptionsDialog.OptionItem(3, "关于", NokiaIcons.ICON_INFO)
)

NokiaOptionsDialog.show(this, "选项", items) { item ->
    when (item.id) {
        1 -> startActivity(Intent(this, NokiaFeedbackActivity::class.java))
        2 -> {
            val next = !NokiaLog.isDetailedLogEnabled(this)
            NokiaLog.setDetailedLogEnabled(this, next)
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

import io.github.cctyl.nokia.keycore.log.NokiaLog

object NLog {
    @JvmStatic fun v(tag: String, msg: String) = NokiaLog.v(tag, msg)
    @JvmStatic fun d(tag: String, msg: String) = NokiaLog.d(tag, msg)
    @JvmStatic fun i(tag: String, msg: String) = NokiaLog.i(tag, msg)
    @JvmStatic fun w(tag: String, msg: String, tr: Throwable? = null) = NokiaLog.w(tag, msg, tr)
    @JvmStatic fun e(tag: String, msg: String, tr: Throwable? = null) = NokiaLog.e(tag, msg, tr)
}
```

然后在业务 Kotlin 文件头部将 `import android.util.Log` 替换为：
```kotlin
import com.example.myapp.util.NLog as Log
```
这样文件内部现有的 `Log.d("Tag", "msg")`、`Log.e("Tag", "msg", tr)` 会自动且无缝地重定向到 `NokiaLog`，全部享受等级过滤、按天轮转与文件落盘能力！

---

### 6. 业务代码规范打点示例

```java
NokiaLog.d("Player", "切换歌曲: id=1001");
NokiaLog.i("Network", "请求完成: code=200");
NokiaLog.w("Cache", "缓存未命中");
NokiaLog.e("Auth", "登录失败", exception);
```

### 5. 打包大小策略

| 规则 | 值 |
|---|---|
| 服务端硬上限 | 10 MB |
| SDK 打包预算 | **9 MB**（给 meta / 协议头留余量） |
| 单文件上限 | 8 MB（超出部分截断） |
| 超限裁剪顺序 | 按 `lastModified` **越新越优先**，装不下的旧文件丢弃 |
| 全部装不下 | 发空 zip |

打包统计通过 `KdfbUploader.ZipResult` 返回（`includedFiles` / `skippedFiles`
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
| total_mem_mb / avail_mem_mb / memory_class_mb | `ActivityManager.MemoryInfo` |
| free_disk_mb / total_disk_mb | `StatFs` |
| screen_px / screen_density | `DisplayMetrics` |
| battery_pct / charging | `ACTION_BATTERY_CHANGED` 粘性广播 |
| locale / uptime_days | `Locale` / `SystemClock` |
| app_package / app_version_name / app_version_code | `PackageManager` |

全部来自系统公开 API，不含 IMEI、位置等隐私敏感数据。
宿主可通过 `submit()` 的 extraInfo 参数追加自己的字段（值支持 Number/Boolean/String，
String 自动截断 200 字符，序列化后 extras 总量 ≤4096 字节）。

---

## 五、协议要点（排查问题用）

报文为大端序二进制 TCP 协议（KDFB v1），细节见生态仓库
`log_upload/android/README.md`。客户端侧需要知道的：

- **签名范围**：version 起到 zip 末尾的连续字节；Ed25519，64 字节签名插在 meta 与 zip_len 之间；
- **时间戳窗口**：±5 分钟，设备时钟不准会被拒（reason=TIMESTAMP）；
- **失败静默**：服务端任何校验失败都不回包直接断连，客户端统一返回 false，
  **无法也不需要区分原因**——联调时看服务端控制台 `REJECT reason=<码>`：
  | reason | 含义 |
  |---|---|
  | MAGIC | 魔数错（拼包 bug） |
  | META | meta 非法/超长/meta_len 错位 |
  | SIGNATURE | 签名不符（检查签名范围与字节序） |
  | TIMESTAMP | 设备时间偏差 >5 分钟 |
  | RATE | 限流（3 次/分钟/IP、20 次/天/IP），等待后重试 |
  | ZIPSIZE | zip 超 10MB |
- **禁止自动重试**：限流下重试只会加剧失败，SDK 不做重试，UI 提示用户手动再试即可；
- 客户端诊断日志 tag 为 `KdfbUploader` / `NokiaFeedback`（WARN 级，仅记录异常类别）。

---

## 六、安全模型说明

项目开源、协议公开的前提下，本方案的安全边界如下：

- 私钥**只存在于各宿主编译出的 APK 内**，不在任何 Git 仓库中出现；
- APK 可被逆向提取私钥——这是接受的威胁模型：提取者最多以合法姿态刷接口，
  受服务端限流约束（3 次/分钟、20 次/天/IP）+ nonce 防重放兜底；
- 时间戳 + nonce 双重防重放；传输为明文（反馈内容本身非机密）。
