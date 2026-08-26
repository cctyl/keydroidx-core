# 10 · 反馈上报（KDFB）

> 一站式用户反馈能力：内置诺基亚风格反馈页 + 日志打包 + Ed25519 签名上传。
> 宿主 APP 五分钟接入，密钥不入库、不进 SDK。

## 核心类

| 类 | 职责 |
|---|---|
| `NokiaFeedbackConfig` | 全局配置（服务地址 / 端口 / 私钥 / 应用名 / 版本 / 日志目录） |
| `NokiaFeedback` | 门面：`init()` 注册配置、`submit()` 上传 |
| `NokiaFeedbackActivity` | SDK 内置复古反馈页，开箱即用 |
| `KdfbUploader` | 协议实现：meta 组装、日志 zip 打包、报文签名与 TCP 收发 |
| `DeviceInfoCollector` | 设备信息采集（extras 默认值） |
| `NokiaEd25519` | 纯 Java Ed25519 签名（零第三方依赖，已过 RFC 8032 测试向量） |

包路径统一为 `io.github.cctyl.nokia.keycore.feedback` 与 `.ui.NokiaFeedbackActivity`。

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

### ② 宿主 `build.gradle` 注入 BuildConfig

```groovy
def localProps = new Properties()
def f = rootProject.file("local.properties")
if (f.exists()) localProps.load(new FileInputStream(f))

android {
    defaultConfig {
        buildConfigField "String", "KDFB_SERVER_HOST",
            '"${localProps.getProperty('KDFB_SERVER_HOST') ?: ''}"'
        buildConfigField "int", "KDFB_SERVER_PORT",
            localProps.getProperty('KDFB_SERVER_PORT') ?: '9421'
        buildConfigField "String", "KDFB_PRIVATE_KEY",
            '"${localProps.getProperty('KDFB_PRIVATE_KEY') ?: ''}"'
    }
    buildFeatures { buildConfig true }
}
```

### ③ 初始化 + 入口跳转

```java
// Application 或首个 Activity 的 onCreate，注册一次即可
NokiaFeedback.init(new NokiaFeedbackConfig(
        BuildConfig.KDFB_SERVER_HOST,
        BuildConfig.KDFB_SERVER_PORT,
        BuildConfig.KDFB_PRIVATE_KEY,
        "myapp",                    // 应用标识，需与服务端登记一致
        BuildConfig.VERSION_NAME,
        null));                     // 日志目录，null = 默认 files/logs

// 入口由宿主自行决定（如设置页某一项）
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
- **联系方式 / 问题描述**：必填。CENTER 弹出复古输入对话框编辑，为空提交时自动定位并引导填写；
- **附带运行日志**：强制开启不可关闭，行内实时显示「N 个文件 / X KB」，
  超限时提示「过大仅保留最新」；
- 提交中按钮置灰防连点；成功 Toast 后自动关闭；失败提示手动重试；
- 物理软键适配：左软键提交、右软键返回（按当前按键绑定动态解析）。

未调用 `init()` 或配置无效时，页面提交会给出明确错误提示。

---

## 三、日志目录约定

```
默认：Android/data/<包名>/files/logs   （即 getExternalFilesDir("logs")）
覆盖：NokiaFeedbackConfig 最后一个参数传入自定义 File
```

宿主只要把日志写进上述目录，反馈页即自动发现、打包、上传。
目录不存在或无文件时自动发空 zip，不影响提交。

### 打包大小策略

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
