# 13 · 安装统计上报（POST /install）

> 首次安装 + 版本升级时自动上报一次设备信息，服务端按 `(app, android_id)` 去重。
> 与 [10 · 反馈上报](10-feedback.md) 共用同一台服务器、同一套 HMAC-SHA256 鉴权、
> 同一个 `NokiaFeedbackConfig`，仅接口路径与请求体不同。

## 一、协议速览

| 项 | 反馈上报 `/upload` | 安装统计 `/install` |
|---|---|---|
| Body | zip 二进制（`application/octet-stream`） | JSON（`application/json`） |
| 设备信息位置 | `X-Meta` 头 JSON | Body JSON |
| 鉴权 | `X-Timestamp` / `X-Nonce` / `X-AccessKey`（HMAC-SHA256） | **完全相同** |
| 重试 | 禁止自动重试（UI 提示用户手动） | 最多 1 次（间隔约 1 秒），失败静默 |
| 去重 | 无 | 客户端按 `(android_id, version)` 跳过 + 服务端按 `(app, android_id)` 去重 |

服务端按 `(app, android_id)` 去重：**首次上报计为一次安装**；同一设备再次上报
（例如升级）不重复计安装数，但会把该设备的**版本等字段更新为最新**。
版本分布即按「设备最新一次上报的版本」统计。详细协议见 `log_upload/docs/CLIENT_API.md` 第 3 节。

## 二、核心类

| 类 | 职责 | 所在包 |
|---|---|---|
| `NokiaFeedbackConfig` | 全局配置（已扩展 `installUrl` 字段） | `io.github.cctyl.nokia.common.feedback` |
| `NokiaInstall` | 门面：`reportOnce(context)` 自动幂等上报 | 同上 |
| `InstallUploader` | 协议实现：JSON 组装、HMAC-SHA256 签名、HTTP POST（复用 `FeedbackUploader` 的签名工具） | 同上 |
| `DeviceInfoCollector` | 设备信息采集（与反馈上报共用） | 同上 |

`InstallUploader` 不重复实现签名算法，直接复用同包 `FeedbackUploader` 的
`computeAccessKey` / `hexToBytes` / `bytesToHex`（package-private static），
**确保两端算法永远一致**。

## 三、快速接入（两步）

### ① `local.properties` 配置安装上报地址（可选）

```properties
FEEDBACK_UPLOAD_URL=http://your.server.com:9421/upload
FEEDBACK_INSTALL_URL=http://your.server.com:9421/install
FEEDBACK_SECRET_KEY=<feedback_secret.key 文件里的 hex 字符串>
```

> 与反馈上报共用密钥；`FEEDBACK_INSTALL_URL` **可省略**——
> 未配置时 `NokiaFeedbackConfig.resolveInstallUrl()` 会自动把 `FEEDBACK_UPLOAD_URL`
> 末尾的 `/upload` 替换为 `/install`。显式配置更清晰，推荐。

### ② 宿主 `build.gradle` 注入 BuildConfig

```groovy
def localProps = new Properties()
def f = rootProject.file("local.properties")
if (f.exists()) localProps.load(new FileInputStream(f))

android {
    defaultConfig {
        buildConfigField "String", "FEEDBACK_UPLOAD_URL",
            "\"${localProps.getProperty('FEEDBACK_UPLOAD_URL', 'http://127.0.0.1:9421/upload')}\""
        buildConfigField "String", "FEEDBACK_INSTALL_URL",
            "\"${localProps.getProperty('FEEDBACK_INSTALL_URL', 'http://127.0.0.1:9421/install')}\""
        buildConfigField "String", "FEEDBACK_SECRET_KEY",
            "\"${localProps.getProperty('FEEDBACK_SECRET_KEY', '')}\""
    }
    buildFeatures { buildConfig true }
}
```

### ③ 初始化 + 触发上报

在 `Application.onCreate` 里，紧跟 `NokiaFeedback.init(...)` 之后调用一行：

```java
public class MyApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

        NokiaLog.setTag("MyApp");
        NokiaLog.init(this);
        NokiaLog.installCrashHandler(this);

        // 反馈 + 安装统计共用同一份配置
        NokiaFeedback.init(new NokiaFeedbackConfig(
                BuildConfig.FEEDBACK_UPLOAD_URL,
                BuildConfig.FEEDBACK_INSTALL_URL,   // 传 null 也可，会自动从 uploadUrl 推导
                BuildConfig.FEEDBACK_SECRET_KEY,
                "myapp",
                BuildConfig.VERSION_NAME,
                null));

        // 首次安装 / 版本升级时自动上报一次；同版本不重复打
        NokiaInstall.reportOnce(this);
    }
}
```

> 全程后台执行、不阻塞主线程、不抛异常、不等结果。
> 未初始化 `NokiaFeedback` 或配置无效时静默跳过，不影响反馈上报能力。

Manifest 权限（与反馈上报相同，已有则无需重复声明）：

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

## 四、上报时机与幂等

`NokiaInstall.reportOnce()` 内部用 `SharedPreferences`（`nokia_install_report`）
记录「已上报的 android_id + 版本」，决策流程：

```
读 ANDROID_ID
  ├─ null / 空白 / 全零  → 放弃本次上报（避免脏数据）
  └─ 与已记录的 (android_id, version) 相同  → 跳过
     └─ 不同（新装 / 升级 / 清数据重装）→ 上报
        ├─ 成功（HTTP 200）→ 记录新 (android_id, version)
        └─ 失败 → 不记录，下次启动还会再试
```

- **首次安装**：SharedPreferences 为空 → 上报；
- **版本升级**：`appVersion` 变化 → 上报（即使 android_id 不变）；
- **同版本重复启动**：直接跳过，零网络开销；
- **清除数据重装**：SharedPreferences 被清 → 重新上报（服务端按 `android_id` 去重，不重复计安装数，仅刷新字段）；
- **上报失败**：不记录「已上报」，下次启动自动重试（最多 1 次重试在单次调用内完成）。

## 五、Body 字段（严格按服务端白名单）

`InstallUploader.buildInstallJson` 只写入协议白名单字段，并对每个值做长度截断与类型归一，
**不携带任何白名单外字段**（白名单外字段会被服务端忽略；白名单内字段类型/长度不对会
**判定为协议违规并封禁 IP 1 小时**）。

### 必填 7 个

| 字段 | 来源 | 约束 |
|---|---|---|
| `app` | `NokiaFeedbackConfig.appName` | 1–32，`[A-Za-z0-9_-]` |
| `app_version` | `NokiaFeedbackConfig.appVersion` | 1–32 |
| `android_id` | `Settings.Secure.ANDROID_ID` | 8–64 位十六进制，非全零 |
| `device_brand` | `Build.BRAND` | 1–64 |
| `device_model` | `Build.MODEL` | 1–64 |
| `android_version` | `Build.VERSION.RELEASE` | 1–16 |
| `android_api` | `Build.VERSION.SDK_INT` | 1–100 |

### 选填 11 个（能采集到就带）

| 字段 | 来源 | 约束 |
|---|---|---|
| `device_manufacturer` | `Build.MANUFACTURER` | ≤64 |
| `locale` | `Locale.getDefault()` | ≤32 |
| `screen_px` | `DisplayMetrics`，如 `"1080x2400"` | ≤32 |
| `screen_density` | `DisplayMetrics.densityDpi`，如 `"440dpi"` | ≤16 |
| `total_mem_mb` | `ActivityManager.MemoryInfo.totalMem` | int 0–1000000 |
| `total_disk_mb` | `StatFs` | int 0–1000000000 |
| `cpu_hardware` | `/proc/cpuinfo` Hardware 或 `Build.HARDWARE` | ≤64 |
| `cpu_cores` | `Runtime.availableProcessors()` | int 1–256 |
| `gpu_renderer` | `GLES20.glGetString(GL_RENDERER)` | ≤64 |
| `gpu_vendor` | `GLES20.glGetString(GL_VENDOR)` | ≤32 |
| `supported_abis` | `Build.SUPPORTED_ABIS`，如 `"arm64-v8a,armeabi-v7a"` | ≤128 |

选填字段取不到时不写入 JSON（服务端允许省略或传 null）。
全部设备信息由 `DeviceInfoCollector.collect(context)` 一次性采集——
**与反馈上报的 extras 完全同源**，零额外采集开销。

## 六、行为细则

| 项目 | 实现 |
|---|---|
| 失败重试 | 单次调用内最多 1 次重试（间隔约 1 秒） |
| 失败影响 | 全程 `try/catch` 兜底，统计失败静默返回，绝不影响 APP |
| Body 大小 | ≤ 2048 字节（超限在发送前直接放弃） |
| `android_id` 无效 | 取不到 / 全零 → 放弃本次上报 |
| 线程模型 | 后台单线程池（`Executors.newSingleThreadExecutor`），不阻塞主线程 |
| 诊断日志 | tag `NokiaInstall` / `InstallUploader`，走 `NokiaLog` |

## 七、与反馈上报的关系

```
                 ┌─────────────────────────────────────────────┐
                 │           NokiaFeedbackConfig              │
                 │  uploadUrl / installUrl / secretKeyHex /    │
                 │  appName / appVersion / logDir              │
                 └───────────────┬─────────────────────────────┘
                                 │
              ┌──────────────────┴──────────────────┐
              ▼                                     ▼
   ┌────────────────────┐              ┌────────────────────┐
   │   NokiaFeedback     │              │   NokiaInstall     │
   │   submit()          │              │   reportOnce()    │
   │   /upload + zip     │              │   /install + JSON │
   └─────────┬──────────┘              └─────────┬──────────┘
             │                                    │
             ▼                                    ▼
   ┌────────────────────┐              ┌────────────────────┐
   │ FeedbackUploader    │              │  InstallUploader   │
   │ computeAccessKey()  │◄──复用──────►│ (调用同方法)        │
   │ hexToBytes()        │              │                    │
   └────────────────────┘              └────────────────────┘
```

- **同一份配置**：`NokiaFeedback.init(...)` 一次即可，安装统计无需单独 init；
- **同一套鉴权**：HMAC-SHA256 算法、密钥、时间戳/nonce 规则完全一致；
- **同一台服务器**：默认 `:9421` 端口，仅路径 `/upload` vs `/install` 不同；
- **同一份设备信息**：`DeviceInfoCollector` 采集一次，两个场景按各自字段约束裁剪后使用。

## 八、实测验证（两台设备 · 三时机）

以 sample（`io.github.cctyl.nokia.sample`）在两台物理设备上验证全部上报时机：

| 时机 | 设备 1（USB `4a24ecf`，`android_id=80a5...`） | 设备 2（网络 `192.168.1.8`，`android_id=c393...`） |
|---|---|---|
| 首装 v1.0（自动触发） | ✅ `install report sent: keydroidx-sample v1.0` | ✅ `v1.0` |
| v1.0 二次启动（幂等） | ✅ 零网络、零日志 | ✅ 零网络 |
| 升级到 v1.1（覆盖安装，保留数据） | ✅ 自动上报 `v1.1` | ✅ 自动上报 `v1.1` |
| v1.1 二次启动（幂等） | ✅ 零网络 | — |

验证要点：

1. **自动触发**：上报由 `Application.onCreate` 里的 `NokiaInstall.reportOnce(this)` 自动触发，无需用户点击；sample 里的两个按钮仅用于反复手动测试（「清除记录并重新上报」会清 `nokia_install_report` prefs 强制重报）。
2. **首装**：SharedPreferences 为空 → 上报；服务端按 `(app, android_id)` 计为一次安装。
3. **升级触发**：`versionName 1.0 → 1.1`，启动即自动重新上报；服务端不重复计安装数，仅把该设备的版本字段更新为 `v1.1`。
4. **幂等跳过**：同 `(android_id, version)` 命中 prefs → 直接返回，零网络开销。
5. **多设备去重**：两台 `android_id` 不同，服务端分别计为 2 次安装；升级不重复计安装数。
6. **installUrl 自动推导**：未配置 `FEEDBACK_INSTALL_URL` 时，`resolveInstallUrl()` 从 `http://192.168.1.5:9421/upload` 推导出 `http://192.168.1.5:9421/install`，上报成功（HTTP 200）。

查看上报日志：

```bash
adb logcat NokiaInstall:V InstallUploader:V SampleApp:V *:S
```

失败重试与封禁规避：首版配置错误（连 `127.0.0.1` 被拒）时验证了「最多重试 1 次、间隔 1 秒」机制，不会无限重试导致出口 IP 被服务端持续封禁。
