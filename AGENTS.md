
## 项目定位与生态概览

`keydroidx-core`（模块名 `nokia-key-core`）是 **KeydroidX（原键）按键机生态** 面向独立应用开发的通用核心接入 SDK。

### 生态背景与痛点
Android 智能按键机（Feature Phone / Keyphone）由于硬件厂商众多，物理按键的键码（KeyCode）极度碎片化（如左软键、右软键、挂机键、通话键等在不同机型上各不相同）。若每个独立应用都各自为政让用户重新配置按键，体验割裂且维护成本极高。

### 核心定位
本 SDK 的核心目标是为 Android 按键机独立应用提供**「零配置按键同步、多级平滑降级、复古 UI 规范与向导」**的一站式能力。独立开发者或生态内自研应用（如独立音乐播放器、浏览器、小说阅读器、工具箱等）只需集成此 SDK，即可免去繁琐的按键适配工作。

---

## 与 KeydroidX 桌面（`nokia_desktop`）的架构关系

KeydroidX 生态采用 **「原键桌面中枢 + 独立轻量 APK 矩阵」** 的分层协同架构。本仓库与同级的 `../nokia_desktop` 项目紧密关联但职责分明：

| 维度 | 原键桌面 (`../nokia_desktop` / KeydroidX Launcher) | 本 SDK (`keydroidx-core` / KeydroidX Core SDK) |
| :--- | :--- | :--- |
| **角色定位** | 生态控制中心、桌面 Launcher、按键数据源（Provider 宿主） | 独立 APK 的接入端 SDK（Client 客户端） |
| **按键配置权** | **全局主导**：在系统/桌面层提供统一按键配置向导与持久化 | **消费与独立备用**：默认读取桌面配置；若无桌面则支持独立配置 |
| **通信机制** | 通过 `NokiaKeyProvider` 暴露 `content://*.keyprovider/keys`，改键时触发 `notifyChange` | 通过 `NokiaKeyClient` 查询 ContentProvider 并注册 `ContentObserver` 实时热重载 |
| **独立运行能力** | 完整的 Android 桌面与 J2ME 容器环境 | 纯轻量 SDK（< 500 行核心代码，仅依赖 `androidx.appcompat`），零宿主桌面依赖 |

### 协作工作流：
```text
┌─────────────────────────────────────────────────────────────┐
│              原键桌面 (nokia_desktop / Launcher)            │
│  - 用户在桌面完成一次性按键向导/改键                          │
│  - NokiaKeyProvider 共享映射数据 (/keys)                     │
└──────────────────────────┬──────────────────────────────────┘
                           │ ContentProvider / ContentObserver
                           ▼
┌─────────────────────────────────────────────────────────────┐
│          生态独立应用 (使用本 SDK keydroidx-core)            │
│  - 音乐播放器 / 浏览器 / 阅读器 / 各种第三方 App               │
│  - NokiaKeyClient 自动同步按键 -> 全局生效，免用户重复配置    │
│  - 继承 NokiaBaseActivity: 自动拥有 240dp 视口与软键分发     │
└─────────────────────────────────────────────────────────────┘
```

---

## 核心机制：三级平滑降级（Graceful Degradation）

当第三方 App 集成 SDK 后，`NokiaKeyClient.reload()` 会自动执行三级平滑降级链路，确保在任何设备与环境下均可稳定可用：

1. **Tier 1 (生态共享优先 - 桌面中枢)**：
   - 依次检测查询正式版 `io.github.cctyl.nokia.keyprovider` 与 Debug 开发版 `io.github.cctyl.nokia.debug.keyprovider`。
   - 命中后标记 `isFromDesktop=true`，建立 `ContentObserver`，桌面改键即时热同步全 App。
2. **Tier 2 (独立运行能力 - 本地配置)**：
   - 用户若未安装原键桌面，SDK 检查本地 SharedPreferences（`nokia_key_bindings`）。
   - 用户可通过 SDK 内置的 `NokiaKeyWizardActivity` 独立配置本 App 的按键。
3. **Tier 3 (标准 Android 兜底 - 默认映射)**：
   - 既无原键桌面、也未配置本地按键时，SDK 自动启用标准 Android 键码兜底（DPAD 上下左右、DPAD_CENTER 确定、MENU 左软键、BACK 右软键/返回）。

---

## SDK 内部模块与层级

SDK 内部代码简洁严谨，自底向上分为三层：

### 1. 数据契约层 (`io.github.cctyl.nokia.keycore.model`)
- **`NokiaKeyAction`**：定义 9 种按键语义动作（`UP=0, DOWN=1, LEFT=2, RIGHT=3, SELECT=4, SOFT_LEFT=5, SOFT_RIGHT=6, LOCK_SCREEN=7, CALL=8`）与 `ACTION_KEYS` 字符串字典，是桌面 Provider 与 Client 间的协议基础。
- **`NokiaKeyBinding`**：底层双向映射表（`action <-> keyCode`），采用两个 `SparseIntArray` 实现 O(1) 查表。包含 `initDefaults`、`resolveAction(KeyEvent)` 兜底容错逻辑、本地 `save/loadLocal`、以及用于编辑隔离的 `clone()`。

### 2. 同步与客户端层 (`io.github.cctyl.nokia.keycore`)
- **`NokiaKeyClient`**：单例，持有 `ApplicationContext`，全局按键分发核心与生命周期管理者。
- 负责执行上述三级降级查询，管理 Provider 数据观察者，并通过 `OnKeyBindingChangedListener` 在主线程派发热重载通知。

### 3. UI 集成与向导层 (`io.github.cctyl.nokia.keycore.ui`)
- **`NokiaBaseActivity`**：复古骨架 BaseActivity。内置 240dp 基准分辨率居中自适应容器、复古标题栏与底部三软键面板（`midPanel` / `bottomPanel`）。在 `dispatchKeyEvent` 中精确配对消费 `ACTION_DOWN` 与 `ACTION_UP`，彻底杜绝按键粘连、事件穿透与双击误触。
- **`NokiaKeyWizardActivity`**：全屏复古按键向导。采用 250ms 防抖步进、`ACTION_DOWN && repeatCount==0` 瞬间捕获机制，支持触屏跳过/取消，完成后自动保存并驱动 `NokiaKeyClient.reload()`。

---

## 常用命令与构建

- **构建所有模块**：`./gradlew build`（Windows: `gradlew.bat build`）。
- **编译发布 AAR**：`./gradlew :nokia-key-core:assembleRelease`（输出产物在 `nokia-key-core/build/outputs/aar/`）。
- **发布到本地 Maven**：`./gradlew :nokia-key-core:publishReleasePublicationToMavenLocal`（坐标 `io.github.cctyl.nokia:nokia-key-core:1.0.0`）。
- **安装并运行示例 App**：`./gradlew :sample:installDebug`（演示如何接入 SDK）。
- **清理构建产物**：`./gradlew clean`。
- **环境要求**：JDK 17（AGP 8.5.1 要求）。镜像源已在 `settings.gradle` 中统一配置。

---

## 关键注意事项与避坑指南

1. **Android 11+ (API 30+) 包可见性声明**：
   - 接入方 App 的 `AndroidManifest.xml` 中必须在 `<queries>` 标签中声明桌面 Provider Authorities，否则系统会静默拦截 Tier 1 ContentProvider 查询：
     ```xml
     <queries>
         <package android:name="io.github.cctyl.nokia" />
         <package android:name="io.github.cctyl.nokia.debug" />
         <provider android:authorities="io.github.cctyl.nokia.keyprovider" />
         <provider android:authorities="io.github.cctyl.nokia.debug.keyprovider" />
     </queries>
     ```
2. **两端数据协议一致性**：
   - 桌面端的 `ru.playsoftware.j2meloader.nokia.NokiaKeyProvider` 与 SDK 端的 `NokiaKeyAction` / `NokiaKeyClient` 字段命名（`action`, `actionId`, `keyCode`, `keyName`）必须保持严格对应。
3. **零业务依赖原则**：
   - `nokia-key-core` 模块作为通用基础 SDK，必须保持绝对轻量，禁止引入任何第三方网络、UI 重型库（仅允许基础 `androidx.appcompat`）。

