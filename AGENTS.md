
## 项目定位与生态概览

`keydroidx-core` 是 **KeydroidX（原键）按键机生态** 的通用核心接入 SDK **仓库**。本仓库并非单一模块，而是由 4 个 Gradle 模块组成（见下文「本仓库模块一览」），面向独立应用发布的主模块是 `keydroidx-key-core`。

### 生态背景与痛点
Android 智能按键机（Feature Phone / Keyphone）由于硬件厂商众多，物理按键的键码（KeyCode）极度碎片化（如左软键、右软键、挂机键、通话键等在不同机型上各不相同）。若每个独立应用都各自为政让用户重新配置按键，体验割裂且维护成本极高。

### 核心定位
本 SDK 的核心目标是为 Android 按键机独立应用提供**「零配置按键同步、多级平滑降级、复古 UI 规范与向导」**的一站式能力。独立开发者或生态内自研应用（如独立音乐播放器、浏览器、小说阅读器、工具箱等）只需集成此 SDK，即可免去繁琐的按键适配工作。

---

## 本仓库模块一览（Gradle）

`settings.gradle:23` 定义了 4 个模块，生态内各仓库通过 `includeBuild` 或本地 Maven 坐标按需引入（勿整仓引入）：

| 模块 | Maven 坐标（v1.0.0） | 定位 | 依赖 |
|:---|:---|:---|:---|
| `keydroidx-common` | `io.github.cctyl.nokia:keydroidx-common` | **全生态共享通用基础库**：主题/字体/图标/尺寸、`KeydroidxLog` 文件日志器、反馈与安装统计上报、`KeydroidxPermissionManager` 权限门面、页面骨架/弹窗等 UI 组件 | `androidx.appcompat`；`api` 暴露 `XXPermissions 20.0` |
| `keydroidx-key-core` | `io.github.cctyl.nokia:keydroidx-key-core` | **独立 App 接入主 SDK**：桌面 Provider 客户端与四级降级、`KeydroidxClient`、配键向导/关于/反馈等开箱 Activity | `api project(':keydroidx-common')`（透传 common） |
| `keydroidx-mini-shizuku` | `io.github.cctyl.nokia:keydroidx-mini-shizuku` | **mini_shizuku 纯 IPC 客户端**（仅 3 个类：`MiniShizuku` / `MiniShizukuClient` / `MiniShizukuConst`），经本地 IPC 以 shell 身份执行系统命令（调用方由服务端按同签名校验，细节见 `docs/guide/17-mini-shizuku.md`） | **零依赖**，刻意不用 `KeydroidxLog` |
| `sample` | —（不发布） | 生态**测试台**（安装统计 / 更新检查 / 权限 / mini_shizuku 调用演示），非规范接入示例 | `keydroidx-key-core` + `keydroidx-mini-shizuku` |

要点：
- `keydroidx-key-core` 通过 `api` 依赖 common，**接入方只需引 `keydroidx-key-core` 一个坐标**即可获得 common 全部能力；`keydroidx-mini-shizuku` 完全独立、不依赖 common，按需单独引入。
- 通用能力一律放 `io.github.cctyl.nokia.common.*`；`io.github.cctyl.nokia.keycore.*` 下只保留 common 没有的 7 个类（`KeydroidxClient` / `model.KeydroidxKeyBinding` / `ui.KeydroidxBaseActivity` / `ui.KeydroidxKeyWizardActivity` / `ui.KeydroidxTextInputActivity` / `ui.KeydroidxAboutActivity` / `ui.KeydroidxFeedbackActivity`）。
- 模块间职责拆分、决策档案与各接入方正确引入姿势的完整说明见 `docs/architecture/module-layering.md`；mini_shizuku 的调用方式见 `docs/guide/17-mini-shizuku.md`。

---

## 与 KeydroidX 桌面（`keydroidx-launcher`）的架构关系

KeydroidX 生态采用 **「原键桌面中枢 + 独立轻量 APK 矩阵」** 的分层协同架构。本仓库与同级的 `../keydroidx-launcher` 项目紧密关联但职责分明：

| 维度 | 原键桌面 (`../keydroidx-launcher` / KeydroidX Launcher) | 本 SDK (`keydroidx-core` / KeydroidX Core SDK) |
| :--- | :--- | :--- |
| **角色定位** | 生态控制中心、桌面 Launcher、按键数据源（Provider 宿主） | 独立 APK 的接入端 SDK（Client 客户端） |
| **按键配置权** | **全局主导**：在系统/桌面层提供统一按键配置向导与持久化 | **消费与独立备用**：默认读取桌面配置；若无桌面则支持独立配置 |
| **通信机制** | 通过 `KeydroidxKeyProvider` 暴露 `content://*.keyprovider/keys`，改键时触发 `notifyChange` | 通过 `KeydroidxClient` 查询 ContentProvider 并注册 `ContentObserver` 实时热重载 |
| **独立运行能力** | 完整的 Android 桌面与 J2ME 容器环境 | 纯轻量 SDK（< 500 行核心代码，仅依赖 `androidx.appcompat`），零宿主桌面依赖 |

### 协作工作流：
```text
┌─────────────────────────────────────────────────────────────┐
│              原键桌面 (keydroidx-launcher / Launcher)       │
│  - 用户在桌面完成一次性按键向导/改键                          │
│  - KeydroidxKeyProvider 共享映射数据 (/keys)                     │
└──────────────────────────┬──────────────────────────────────┘
                           │ ContentProvider / ContentObserver
                           ▼
┌─────────────────────────────────────────────────────────────┐
│          生态独立应用 (使用本 SDK keydroidx-core)            │
│  - 音乐播放器 / 浏览器 / 阅读器 / 各种第三方 App               │
│  - KeydroidxClient 自动同步按键 -> 全局生效，免用户重复配置        │
│  - 继承 KeydroidxBaseActivity: 自动拥有原生 DP 视口与软键分发    │
└─────────────────────────────────────────────────────────────┘
```

---

## 核心机制：四级平滑降级（Graceful Degradation）

当第三方 App 集成 SDK 后，`KeydroidxClient.reload()` 会自动执行四级平滑降级链路，确保在任何设备与环境下均可稳定可用：

1. **Tier 1/2 (生态共享优先 - 桌面中枢)**：
   - 依次检测查询正式版 `io.github.cctyl.nokia.keyprovider` 与 Debug 开发版 `io.github.cctyl.nokia.debug.keyprovider`（HOME 包名含 `debug` 时探测顺序反转）。
   - 命中后标记 `isFromDesktop=true`，建立 `ContentObserver`，桌面改键即时热同步全 App。
2. **Tier 3 (独立运行能力 - 本地配置)**：
   - 用户若未安装原键桌面，SDK 检查本地 SharedPreferences（`nokia_key_bindings`）。
   - 用户可通过 SDK 内置的 `KeydroidxKeyWizardActivity` 独立配置本 App 的按键。
3. **Tier 4 (标准 Android 兜底 - 默认映射)**：
   - 既无原键桌面、也未配置本地按键时，SDK 自动启用标准 Android 键码兜底（DPAD 上下左右、DPAD_CENTER 确定、MENU 左软键、BACK 右软键/返回）。

---

## SDK 内部代码分层（common / key-core 包内视角）

> 本节讲的是**代码职责分层**（不是上面的 Gradle 模块划分）。

SDK 内部代码简洁严谨，自底向上分为四层：

### 1. 数据契约层 (`io.github.cctyl.nokia.keycore.model`)
- **`KeydroidxKeyAction`**：定义 9 种按键语义动作（`UP=0, DOWN=1, LEFT=2, RIGHT=3, SELECT=4, SOFT_LEFT=5, SOFT_RIGHT=6, LOCK_SCREEN=7, CALL=8`）与 `ACTION_KEYS` 字符串字典，是桌面 Provider 与 Client 间的协议基础。
- **`KeydroidxKeyBinding`**：底层双向映射表（`action <-> keyCode`），采用两个 `SparseIntArray` 实现 O(1) 查表。包含 `initDefaults`、`resolveAction(KeyEvent)` 兜底容错逻辑、本地 `save/loadLocal`、以及用于编辑隔离的 `clone()`。

### 2. 同步与客户端层 (`io.github.cctyl.nokia.keycore`)
- **`KeydroidxClient`**：单例，持有 `ApplicationContext`，全局配置中枢与生命周期管理者。
- 负责执行上述四级降级查询，管理 Provider 数据观察者，实现 common 的 `ThemeProvider` 并自注入，并通过 `OnConfigChangedListener` 在主线程派发热重载通知。

### 3. UI 集成与向导层 (`io.github.cctyl.nokia.keycore.ui`)
- **`KeydroidxBaseActivity`**：复古骨架 BaseActivity。根布局采用响应式原生 DP（`match_parent` 铺满 + `weight` 弹性均分，240×320 仅作设计基准、**不做运行时缩放**，见 `docs/spec/responsive-layout-spec.md`），内置复古标题栏与底部三软键面板（`midPanel` / `bottomPanel`）。在 `dispatchKeyEvent` 中精确配对消费 `ACTION_DOWN` 与 `ACTION_UP`，彻底杜绝按键粘连、事件穿透与双击误触。
- **`KeydroidxKeyWizardActivity`**：全屏复古按键向导。采用 250ms 防抖步进、`ACTION_DOWN && repeatCount==0` 瞬间捕获机制，支持触屏跳过/取消，完成后自动保存并驱动 `KeydroidxClient.reload()`。

### 4. 反馈上报层 (`io.github.cctyl.nokia.common.feedback` + `keycore.ui.KeydroidxFeedbackActivity`)
- **`KeydroidxFeedbackActivity`**：内置诺基亚风格通用反馈页（问题类型/联系方式必填/描述/日志开关），宿主 `startActivity` 即用，入口自定。
- **`KeydroidxFeedback` / `KeydroidxFeedbackConfig`**：门面与配置。宿主启动时 `init()` 注册服务 URL 与通信密钥（值来自宿主 BuildConfig，**密钥绝不入库、不进 SDK**）。配置只持有一个根地址 `baseUrl`，SDK 内部通过 `resolveUploadUrl()`（`baseUrl+/upload`）与 `resolveInstallUrl()`（`baseUrl+/install`）自动拼出两个接口路径，无需也不允许单独配置 `installUrl`。
- **`FeedbackUploader`**：HTTP POST 上传实现（日志 zip 打包 ≤9MB 超限裁剪、设备信息组装与发送）；失败静默且禁止自动重试。请求签名由 SDK 内部完成。
- **`KeydroidxInstall` / `InstallUploader`**：安装统计上报（`POST /install`），与反馈上报共用同一份 `KeydroidxFeedbackConfig`，仅路径与请求体不同（Body 为 JSON，非 zip）。`KeydroidxInstall.reportOnce(context)` 用 `SharedPreferences` 记录 `(android_id, version)` 实现客户端幂等：**首装/升级各报一次，同版本跳过**；失败最多重试 1 次。服务端按 `(app, android_id)` 去重，重复上报不重复计安装数但更新版本字段。协议见 `log_upload/docs/CLIENT_API.md` 第 3 节，接入文档见 `docs/guide/12-install-stats.md`。
- **`DeviceInfoCollector`**：设备信息采集（仅公开 API），反馈与安装上报共用。
- 详细接入文档见 `docs/guide/11-feedback.md`、`docs/guide/12-install-stats.md`；默认生态日志目录约定为 `Android/data/<包名>/files/log`，可在配置中覆盖。
- **`KeydroidxLog`**：生态标准零依赖文件日志器（对齐桌面架构），支持按天轮转（保留 7 天）、详细日志开关持久化（`isDetailedLogEnabled` / `setDetailedLogEnabled`）、未捕获崩溃同步瞬时落盘。

---

## 常用命令与构建

- **构建所有模块**：`./gradlew build`（Windows: `gradlew.bat build`）。
- **编译发布 AAR**：`./gradlew :keydroidx-key-core:assembleRelease`（输出产物在 `keydroidx-key-core/build/outputs/aar/`）。
- **发布到本地 Maven**：`./gradlew :keydroidx-key-core:publishReleasePublicationToMavenLocal`（坐标 `io.github.cctyl.nokia:keydroidx-key-core:1.0.0`）。
- **安装并运行示例 App**：`./gradlew :sample:installDebug`（演示如何接入 SDK）。
- **清理构建产物**：`./gradlew clean`。
- **环境要求**：JDK 17（AGP 8.5.1 要求）。镜像源已在 `settings.gradle` 中统一配置。

---

## 关键注意事项与避坑指南

1. **Android 11+ (API 30+) 包可见性声明**：
   - 引入 `keydroidx-key-core` 时其 manifest 已自动声明桌面 Provider Authorities 的 `<queries>` 并合并进接入方，**无需手动添加**。
   - 仅引入 `keydroidx-common` 且需自行查询桌面 Provider 时，才在 `AndroidManifest.xml` 的 `<queries>` 标签中声明，否则系统会静默拦截 Tier 1 ContentProvider 查询：
     ```xml
     <queries>
         <package android:name="io.github.cctyl.nokia" />
         <package android:name="io.github.cctyl.nokia.debug" />
         <provider android:authorities="io.github.cctyl.nokia.keyprovider" />
         <provider android:authorities="io.github.cctyl.nokia.debug.keyprovider" />
     </queries>
     ```
2. **两端数据协议一致性**：
   - 桌面端的 `ru.playsoftware.j2meloader.nokia.KeydroidxKeyProvider` 与 SDK 端的 `KeydroidxKeyAction` / `KeydroidxClient` 字段命名（`action`, `actionId`, `keyCode`, `keyName`）必须保持严格对应。
3. **零业务依赖原则**：
   - `keydroidx-key-core` 模块作为通用基础 SDK，必须保持绝对轻量，禁止引入任何第三方网络、UI 重型库（仅允许基础 `androidx.appcompat`）。
4. **统一日志器（强制）**：
   - 所有模块的日志一律走 `io.github.cctyl.nokia.common.log.KeydroidxLog`，**禁止直接使用 `android.util.Log`**。
   - `KeydroidxLog` 是生态标准日志器（按天轮转、落盘到 `Android/data/<包名>/files/log/yyyyMMdd.log`、未捕获崩溃同步落盘、详细日志开关持久化），直接用 `android.util.Log` 会绕过落盘机制，导致反馈上报「附带运行日志」抓不到现场、崩溃堆栈丢失。
   - 含堆栈的日志用带 `Throwable` 的重载：`KeydroidxLog.w(tag, msg, throwable)` / `KeydroidxLog.e(tag, msg, throwable)`，**不要**回退到 `android.util.Log.w(tag, msg, t)`。
   - 标签命名：按模块/组件取简短稳定 tag（如 `KeydroidxInstall`、`EmulatorApp`），不要用类名全限定。
   - **例外**：`keydroidx-mini-shizuku` 模块刻意保持「纯 IPC 客户端，零依赖」，仅为它引入 `keydroidx-common` 用 `KeydroidxLog` 会破坏其零依赖定位，因此该模块内允许使用 `android.util.Log`。其他模块一律走 `KeydroidxLog`。
5. **统一字号与排版规范（6 级标准 Token）**：
   - 详见 **`docs/spec/typography-and-font-spec.md`**。所有界面文本必须引用 `@dimen/keydroidx_font_*` 6 级语义 Token（`display:16sp`, `title:13sp`, `body:12sp`, `small_title:11sp`, `caption:9sp`, `micro:7sp`）。
   - 严禁在 XML 中裸写字号数字（如 `android:textSize="14sp"`），严禁在 ≤13sp 点阵字体上使用 `textStyle="bold"`。单列列表项主标题与表单主项强制对齐 `keydroidx_font_body` (12sp)。

