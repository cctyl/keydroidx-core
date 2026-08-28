# 12 · 生态分层架构演进：拆出 keydroidx-common

> **状态**：设计方案（待评审）
> **影响范围**：`keydroidx-core`、`keydroidx-launcher`、全部生态独立 App
> **前置文档**：[按键机生态与独立应用扩展架构设计](./按键机生态与独立应用扩展架构设计.md)、[10 · 反馈上报与日志](./10-feedback.md)

---

## 一、背景与动机

### 1.1 生态演进现状

KeydroidX 生态经过多轮迭代，已形成「桌面 + SDK + 独立应用矩阵」的三层协同架构：

```
keydroidx-launcher（桌面 / Provider 宿主）
        ▲ ContentProvider
        │
keydroidx-core（SDK / Client）── 独立 App（Music、Browser…）
```

`keydroidx-core`（模块名 `nokia-key-core`）目前在单模块内承载了**两类性质完全不同的能力**：

| 性质 | 代表类 | 服务对象 |
| :--- | :--- | :--- |
| **通用基础能力**（纯算法 / 纯 UI 规范 / 协议） | `NokiaTheme`、`NokiaLog`、`NokiaEd25519`、`KdfbUploader`、`DeviceInfoCollector` | 桌面 + 独立 App 都需要 |
| **客户端专属能力**（跨进程查询 / 改键向导） | `NokiaClient`、`NokiaKeyClient`、`NokiaKeyWizardActivity` | 仅独立 App 需要 |

### 1.2 痛点：桌面想接入反馈，却陷入架构悖论

桌面（Launcher）自身也需要「意见反馈 + 日志上报 + 主题调色板 + 文件日志器」这些**通用基础能力**。但当桌面想复用时，面临一个二选一困境：

**困境 A — 让桌面直接依赖 `nokia-key-core`**：
- 违背《按键机生态与独立应用扩展架构设计》确立的**单向依赖原则**：桌面是 Provider 宿主（Server），不应反向依赖面向独立 App 的 Client SDK；
- 桌面被迫引入 `NokiaKeyClient`（跨进程查自己的 Provider，逻辑倒置）；
- SDK 面向独立 App 的任何迭代都会牵动作为系统常驻 Launcher 的桌面，破坏稳定性。

**困境 B — 源码级硬拷贝（Copy-Paste）**：
- `NokiaEd25519`（~500 行大数运算）、`KdfbUploader`（二进制协议打包）、`NokiaTheme`（6 套调色板）、`NokiaLog`（7 天轮转 + 崩溃捕获）在桌面与 SDK 各存一份；
- 未来主题新增一套配色、修复一个签名 bug、调整一条日志格式，必须人工双向同步，极易产生**代码分叉（Drift）**。

### 1.3 目标

引入一个**纯基础库 `keydroidx-common`**，承载全生态共享的底层能力，使：

1. 桌面只依赖 `common`，彻底不碰 `NokiaKeyClient`，符合架构解耦原则；
2. 80% 底层代码全生态只有一份源码，改一处全生态受益；
3. `core` 继续服务独立 App，叠加 Client 能力，API 不破坏向后兼容；
4. 桌面反馈页可用原生 Fragment 实现，底层协议复用 `common`。

---

## 二、核心矛盾：NokiaTheme ↔ NokiaClient 循环依赖

在动手拆分前，必须先认清当前代码里一个**致命的循环依赖**，它是分层能否成功的唯一技术障碍。

### 2.1 现状依赖分析

对 `nokia-key-core` 全部源文件做了静态依赖扫描，结论如下：

| 文件 | 是否依赖 `NokiaClient` | 能否干净进 `common` |
| :--- | :--- | :--- |
| `log/NokiaLog.java` | ❌ 不依赖 | ✅ 直接进 |
| `feedback/NokiaEd25519.java` | ❌ 不依赖 | ✅ 直接进 |
| `feedback/KdfbUploader.java` | ❌ 不依赖 | ✅ 直接进 |
| `feedback/DeviceInfoCollector.java` | ❌ 不依赖 | ✅ 直接进 |
| `feedback/NokiaFeedback.java` | ❌ 不依赖 | ✅ 直接进 |
| `feedback/NokiaFeedbackConfig.java` | ❌ 不依赖 | ✅ 直接进 |
| `feedback/FeedbackRequest.java` | ❌ 不依赖 | ✅ 直接进 |
| `ui/NokiaIcons.java` | ❌ 不依赖 | ✅ 直接进 |
| `ui/NokiaBatteryDrawable.java` | ❌ 不依赖 | ✅ 直接进 |
| `ui/drawable/NokiaDashedLineDrawable.java` | ❌ 不依赖 | ✅ 直接进 |
| `util/NokiaDimens.java` | ❌ 不依赖 | ✅ 直接进 |
| **`ui/NokiaTheme.java`** | ⚠️ **第 137 行** `NokiaClient.get(context).getCurrentTheme()` | ❌ **直接进会循环依赖** |
| `ui/NokiaBaseActivity.java` | ✅ 依赖 | 留在 `core` |
| `ui/NokiaFeedbackActivity.java` | ✅ 依赖 | 留在 `core` |
| `ui/NokiaTextInputActivity.java` | ✅ 依赖 | 留在 `core` |
| `ui/dialog/NokiaOptionsDialog.java` | ✅ 依赖 | 留在 `core` |
| `ui/dialog/NokiaConfirmDialog.java` | ✅ 依赖 | 留在 `core` |
| `ui/NokiaTextInputFragment.java` | ❌ 不依赖 | ✅ 已下沉 `common`（反馈页需跨端复用） |
| `ui/page/*`（Fragment 体系） | ✅ 依赖 | 留在 `core` |
| `ui/NokiaKeyWizardActivity.java` | ✅ 依赖 | 留在 `core` |
| `NokiaClient.java` | — | 留在 `core` |
| `NokiaKeyClient.java` | — | 留在 `core` |
| `model/NokiaKeyAction.java` | — | 留在 `core` |
| `model/NokiaKeyBinding.java` | — | 留在 `core` |

### 2.2 循环依赖的形成

```
NokiaTheme（想进 common）
    │  第 137 行调用
    ▼
NokiaClient（留在 core）
    │  import
    ▼
NokiaTheme（已经在 common）  ← 循环！
```

如果直接把 `NokiaTheme` 搬进 `common`，`common` 就反向依赖 `core`，Gradle 会直接报循环依赖错误。

### 2.3 解决：依赖倒置（Dependency Inversion）

在 `common` 中定义一个**抽象接口**，`NokiaTheme` 只依赖接口，不依赖具体实现；由上层（`core` 或 `launcher`）在运行时注入实现：

```
common 层：
  NokiaTheme（纯调色板 + 绘制方法，不再直接调 NokiaClient）
  interface ThemeProvider { ThemeDef getCurrentTheme(); }   ← 抽象接口
  NokiaTheme.setProvider(ThemeProvider)                    ← 注入入口

core 层：
  NokiaClient implements ThemeProvider   ← 实现 common 接口

launcher 层：
  LauncherThemeProvider implements ThemeProvider   ← 直接读本地，不跨进程
```

这样 `common` 零依赖 `core`，循环打破。

---

## 三、分层设计总览

### 3.1 三层架构图

```
┌─────────────────────────────────────────────────────────────┐
│           keydroidx-common（纯基础库）                       │
│  零三方依赖 · 纯 Java 8 · 纯算法与 UI 规范                    │
│                                                             │
│  ┌──────────────┐  ┌───────────┐  ┌────────────────────┐  │
│  │ NokiaTheme   │  │ NokiaLog  │  │  feedback/*        │  │
│  │ ThemeProvider │  │(7天轮转)  │  │  FeedbackUploader  │  │
│  │  (抽象接口)   │  │           │  │  DeviceInfoCollector│  │
│  │ NokiaIcons   │  │           │  │  NokiaFeedback     │  │
│  │ NokiaDimens  │  │           │  │  NokiaFeedbackConfig│  │
│  │ Drawables    │  │           │  │                    │  │
│  └──────────────┘  └───────────┘  └────────────────────┘  │
└───────────────────────────┬─────────────────────────────────┘
                            │
              ┌─────────────┴──────────────┐
              │                            │
              ▼                            ▼
┌──────────────────────────┐  ┌──────────────────────────────┐
│   keydroidx-launcher     │  │      keydroidx-core          │
│   (依赖 common，不依赖core)│  │      (依赖 common)           │
│                          │  │                              │
│  LauncherThemeProvider   │  │  NokiaClient implements      │
│   implements ThemeProvider│  │     ThemeProvider            │
│  (直接读本地 SP，          │  │  NokiaKeyClient (跨进程查桌面)│
│   不跨进程查自己)          │  │  NokiaKeyAction / Binding    │
│                          │  │  NokiaBaseActivity (骨架)     │
│  NokiaFeedbackFragment   │  │  NokiaFeedbackActivity       │
│  (原生 Fragment，         │  │  NokiaTextInputActivity      │
│   复用 common 协议层)     │  │  NokiaKeyWizardActivity     │
│                          │  │  Fragment 体系 / Dialog 体系 │
│  桌面自有 Fragment 栈     │  │                              │
│  J2ME 容器 / NDK          │  └──────────────┬───────────────┘
└──────────────────────────┘                 │
                                             ▼
                               ┌──────────────────────────┐
                               │  独立 App（Music/Browser）│
                               │  依赖 core（自动传递 common）│
                               └──────────────────────────┘
```

### 3.2 各层职责

| 层 | 模块名 | 职责 | 依赖 |
| :--- | :--- | :--- | :--- |
| **common** | `nokia-common` | 全生态共享的纯基础能力：主题调色板、文件日志、Ed25519 签名、KDFB 上传协议、设备信息采集、通用图标/Drawable | 仅 `androidx.appcompat` |
| **core** | `nokia-key-core` | 面向独立 App 的 Client SDK：跨进程按键查询、改键向导、复古 Activity/Fragment/Dialog 全套 UI 骨架 | `common` + `androidx.appcompat` |
| **launcher** | `app` | 桌面 Launcher：Provider 宿主、J2ME 容器、桌面 Fragment 栈、原生反馈 Fragment | `common`（不依赖 core） |

---

## 四、模块边界与文件归属清单

### 4.1 `keydroidx-common`（迁入清单）

> 包名根：`io.github.cctyl.nokia.common.*`

| 当前路径 | 迁入路径 | 说明 |
| :--- | :--- | :--- |
| `log/NokiaLog.java` | `log.NokiaLog` | 文件日志器（7 天轮转、分级控制、崩溃捕获） |
| `ui/NokiaTheme.java` | `ui.NokiaTheme` | **需重构**：移除 `NokiaClient` 调用，改依赖 `ThemeProvider` 接口 |
| — | `ui.ThemeProvider` | **新增**：抽象主题提供者接口 |
| `ui/NokiaIcons.java` | `ui.NokiaIcons` | MaterialIcons 矢量图标常量 |
| `ui/NokiaBatteryDrawable.java` | `ui.NokiaBatteryDrawable` | 电池图标 Drawable |
| `ui/drawable/NokiaDashedLineDrawable.java` | `ui.drawable.NokiaDashedLineDrawable` | 虚线 Drawable |
| `util/NokiaDimens.java` | `util.NokiaDimens` | 尺寸工具 |
| `feedback/NokiaEd25519.java` | `feedback.NokiaEd25519` | 纯 Java Ed25519 签名 |
| `feedback/KdfbUploader.java` | `feedback.KdfbUploader` | KDFB v1 二进制协议 |
| `feedback/DeviceInfoCollector.java` | `feedback.DeviceInfoCollector` | 设备信息采集 |
| `feedback/NokiaFeedback.java` | `feedback.NokiaFeedback` | 反馈门面（需更新 import 指向 common） |
| `feedback/NokiaFeedbackConfig.java` | `feedback.NokiaFeedbackConfig` | 反馈配置 |
| `feedback/FeedbackRequest.java` | `feedback.FeedbackRequest` | 反馈请求封装 |

---

### 4.1.1 实测对比：core 与 launcher 同名类分叉情况（2025-08-27 实测）

对 `keydroidx-core/nokia-key-core` 与 `keydroidx-launcher/app/src/main/java/ru/playsoftware/j2meloader/nokia` 两个源码树做全量遍历，**共有 16 个同名类**。经逐个行数对比与关键类 `diff`，分为三类：

#### A 类：仅 core 有，launcher 无（可直接搬运，零分叉）

| 组件 | core 行数 | 说明 |
| :--- | :--- | :--- |
| `NokiaEd25519` | ~500 | 纯 Java Ed25519 签名（RFC 8032 测试向量通过） |
| `KdfbUploader` | ~250 | KDFB v1 二进制协议打包/签名/发送 |
| `DeviceInfoCollector` | ~80 | 设备信息采集（仅公开 API） |
| `NokiaFeedback` | ~100 | 反馈门面 |
| `NokiaFeedbackConfig` | ~50 | 反馈配置数据类 |
| `FeedbackRequest` | ~30 | 上传请求封装 |

> 这些类在 launcher 中**完全不存在**，是 core 后期新增的通用协议层。迁入 common 零冲突。

#### B 类：同名同构，但已分叉（需合并设计）

| 组件 | core 行数 | launcher 行数 | 分叉程度 | 关键差异 |
| :--- | :--- | :--- | :--- | :--- |
| `NokiaTheme` | 144 | 163 | 🔴 **严重** | `ThemeDef` 字段定义完全不同；core 依赖 `NokiaClient` 取主题，launcher 直接读 `NokiaSettingsStorage`；颜色值写法不同 |
| `NokiaLog` | 435 | 271 | 🔴 **严重** | core 是后来重构版，新增分级控制 (`isDetailedLogEnabled`)、崩溃捕获 (`installCrashHandler`)、7天轮转清理；launcher 版为早期简易版 |
| `NokiaIcons` | 180 | 211 | 🟡 中等 | launcher 图标常量更多（含桌面专用 widget/快捷方式图标） |
| `NokiaDimens` | 31 | 61 | 🟡 中等 | launcher 方法更多（含桌面专用尺寸） |
| `NokiaBatteryDrawable` | 187 | 191 | 🟢 轻微 | 仅细微实现差异 |
| `NokiaDashedLineDrawable` | 49 | 73 | 🟡 中等 | launcher 版支持更多配置项 |

#### C 类：同名但职责不同（不进 common，留在各自层）

| 组件 | core 版职责 | launcher 版职责 |
| :--- | :--- | :--- |
| `NokiaBaseActivity` | 依赖 Client 的复古骨架 Activity | 桌面自有骨架 Activity（依赖桌面内部体系） |
| `NokiaKeyBinding` | 客户端按键映射表（含三级降级） | Provider 端按键映射表（含持久化/向导） |
| `NokiaListPageFragment` | 依赖 Client/Theme 的列表页基类 | 桌面原生列表页基类（依赖桌面 Theme） |
| `NokiaPageFragment` / `NokiaScrollPageFragment` | 同上 | 同上 |
| `NokiaPage` / `NokiaPageHost` / `NokiaFocusHost` | 页面契约接口（core 版） | 页面契约接口（launcher 版） |
| `NokiaOptionsDialog` / `NokiaDialogFocus` | 依赖 Client 主题的弹窗 | 桌面自有弹窗（依赖桌面 Theme） |
| `NokiaFontManager` | 依赖 Client 取字体配置 | 桌面自有字体管理 |

> **结论**：只有 A 类 + B 类进 common；C 类保留在各自层，互不干扰。

---

### 4.1.2 关键分叉点详细记录（供合并设计参考）

#### NokiaTheme `ThemeDef` 字段对比

| 字段 | launcher 版 | core 版 | 合并建议 |
| :--- | :--- | :--- | :--- |
| `accentColor` | ✅ | ✅ | 保留 |
| `softKeyStartColor` | ✅ | ❌ | 保留（桌面软键渐变需要） |
| `softKeyEndColor` | ✅ | ❌ | 保留 |
| `bgStartColor` | ✅ | ❌ | 保留（桌面壁纸渐变需要） |
| `bgCenterColor` | ✅ | ❌ | 保留 |
| `bgEndColor` | ✅ | ❌ | 保留 |
| `focusColor` | ✅ | ✅ | 保留 |
| `primaryColor` | ❌ | ✅ | 保留（core 标题栏渐变起色） |
| `darkColor` | ❌ | ✅ | 保留（core 标题栏渐变止色/窗口背景） |
| `textColor` | ❌ | ✅ | 保留 |
| `subTextColor` | ❌ | ✅ | 保留 |
| `cardBgColor` | ❌ | ✅ | 保留 |

> **合并策略**：`ThemeDef` 取**并集**（13 个字段）。桌面端继续用 `softKey*`/`bg*` 渲染软键与壁纸；core 端用 `primaryColor`/`darkColor`/`textColor`/`cardBgColor` 渲染标题栏/卡片/文本。两套渲染路径互不干扰。

#### 取当前主题方式对比

| 维度 | launcher 版 | core 版 | common 统一后 |
| :--- | :--- | :--- | :--- |
| 实现方式 | `new NokiaSettingsStorage(ctx).getTheme()` 直读 SP | `NokiaClient.get(ctx).getCurrentTheme()` 跨进程查 Provider | **ThemeProvider 接口**，launcher 实现直读 SP，core 实现查 Provider |

#### 颜色值写法

| 维度 | launcher 版 | core 版 | 合并建议 |
| :--- | :--- | :--- | :--- |
| 写法 | `0xFF64B5F6` 字面量 | `Color.parseColor("#1a3a6b")` | 统一用 `Color.parseColor`（可读性好，编译期常量折叠无性能损） |

#### Drawable 方法名对比

| launcher 方法 | core 对应方法 | 合并策略 |
| :--- | :--- | :--- |
| `createBackgroundDrawable(theme)` | ❌ 无 | 保留（桌面壁纸需要） |
| `createDialogBodyDrawable(theme)` | ❌ 无 | 保留（桌面对话框需要） |
| `createSoftKeyDrawable(theme)` | `createSoftKeyDrawable()` / `createTitleDrawable()` | **重载共存**：无参版供 core 用（读 `primaryColor`/`darkColor`），有参版供桌面用 |
| `createFocusDrawable(theme, radius)` | `createSelectedRowDrawable(radius)` | 统一为 `createSelectedRowDrawable(radius)`，内部读 `focusColor` |
| `createSelectionDrawable(ctx, radiusDp)` | 同名但实现不同 | 保留 core 版（走 ThemeProvider），桌面实现可删除 |

#### NokiaLog 能力对比

| 能力 | launcher 版 | core 版 | 合并策略 |
| :--- | :--- | :--- | :--- |
| 基础落盘 (v/d/i/w/e) | ✅ | ✅ | 保留 core 版 |
| 7 天轮转清理 | ❌ | ✅ | 保留 core 版 |
| 分级控制 (`isDetailedLogEnabled`/`setDetailedLogEnabled`) | ❌ | ✅ | 保留 core 版 |
| 崩溃捕获 (`installCrashHandler`) | ❌ | ✅ | 保留 core 版 |
| 日志目录 | `files/logs` | `Android/data/<pkg>/log` | **统一为 `Android/data/<pkg>/log`**（与桌面原有 `NokiaSettingsStorage.KEY_LOG_FILE` 约定对齐） |

---

**common 不包含**：任何 `NokiaClient` / `NokiaKeyClient` / `NokiaKeyAction` / `NokiaKeyBinding` / `NokiaKeyWizardActivity` / 依赖 Client 的 UI 类。

### 4.2 `keydroidx-core`（保留清单）

> 包名根：`io.github.cctyl.nokia.keycore.*`（不变）

| 文件 | 说明 |
| :--- | :--- |
| `NokiaClient.java` | 三级降级 Client 单例，`implements ThemeProvider` |
| `NokiaKeyClient.java` | 按键客户端 |
| `model/NokiaKeyAction.java` | 9 种语义动作常量 |
| `model/NokiaKeyBinding.java` | 双向映射表 |
| `ui/NokiaBaseActivity.java` | 复古骨架 Activity（依赖 Client 获取主题/按键） |
| `ui/NokiaFeedbackActivity.java` | 内置反馈页（依赖 Client） |
| `ui/NokiaTextInputActivity.java` | 全屏输入页（依赖 Client） |
| `ui/NokiaKeyWizardActivity.java` | 独立改键向导 |
| `ui/NokiaFontManager.java` | 点阵字体管理 |
| `ui/dialog/*` | 三种弹窗（依赖 Client） |
| `ui/page/*` | Fragment 页面框架（依赖 Client） |

### 4.3 `keydroidx-launcher`（新增）

| 新增文件 | 说明 |
| :--- | :--- |
| `nokia/LauncherThemeProvider.java` | `implements ThemeProvider`，直接读桌面本地 SP，注入给 `NokiaTheme` |
| `nokia/feedback/NokiaFeedbackFragment.java` | 原生 Fragment 反馈页，复用 common 的 `NokiaFeedback` / `KdfbUploader` |
| `nokia/feedback/NokiaFeedbackTextFragment.java` | 原生文本输入 Fragment（或复用系统输入法） |

---

## 五、关键设计：ThemeProvider 接口与依赖注入

### 5.1 接口定义（common 层）

```java
package io.github.cctyl.nokia.common.ui;

/**
 * 主题提供者抽象。打破 NokiaTheme 对 NokiaClient 的硬依赖。
 * 由上层（core 的 NokiaClient 或 launcher 的本地实现）注入。
 */
public interface ThemeProvider {
    /** 返回当前生效的主题定义，永不返回 null（无配置时返回默认主题）。 */
    @NonNull NokiaTheme.ThemeDef getCurrentTheme();
}
```

### 5.2 NokiaTheme 改造（common 层）

```java
public class NokiaTheme {
    private static ThemeProvider provider;

    /** 由上层在 Application.onCreate 注入一次。 */
    public static void setProvider(@NonNull ThemeProvider p) {
        provider = p;
    }

    /** 获取当前主题；未注入 provider 时回退默认主题。 */
    public static ThemeDef getCurrentTheme(Context context) {
        if (provider != null) return provider.getCurrentTheme();
        return getTheme(THEME_CLASSIC_BLUE); // 安全兜底
    }
    // 其余纯调色板 / 绘制方法不变……
}
```

### 5.3 core 层注入

```java
// NokiaClient（core 层）implements ThemeProvider
public class NokiaClient implements ThemeProvider {
    @Override
    public ThemeDef getCurrentTheme() {
        return NokiaTheme.getTheme(currentThemeId); // 原有逻辑
    }
}

// 独立 App 在 Application.onCreate：
NokiaTheme.setProvider(NokiaClient.get(this));   // 注入
NokiaFeedback.init(config);                        // 反馈
```

### 5.4 launcher 层注入（不依赖 core）

```java
// 桌面自有实现，直接读本地 SharedPreferences，不跨进程查自己的 Provider
public class LauncherThemeProvider implements ThemeProvider {
    @Override
    public ThemeDef getCurrentTheme() {
        String themeId = NokiaSettingsStorage.getThemeId();
        return NokiaTheme.getTheme(themeId);
    }
}

// 桌面 Application.onCreate：
NokiaTheme.setProvider(new LauncherThemeProvider(this));  // 注入
NokiaFeedback.init(config);                                // 反馈（复用 common）
```

> 桌面**不引入 `NokiaClient`**，零跨进程开销，直接读本地 SP，符合 Provider 宿主身份。

---

## 六、目录结构与 Gradle 配置

### 6.1 `keydroidx-core` 仓库结构（拆分后）

```
keydroidx-core/
├── settings.gradle
├── nokia-common/                 ← 新增：纯基础库
│   ├── build.gradle
│   └── src/main/java/io/github/cctyl/nokia/common/
│       ├── log/NokiaLog.java
│       ├── ui/NokiaTheme.java
│       ├── ui/ThemeProvider.java
│       ├── ui/NokiaIcons.java
│       ├── ui/NokiaBatteryDrawable.java
│       ├── ui/drawable/NokiaDashedLineDrawable.java
│       ├── util/NokiaDimens.java
│       └── feedback/...
│
├── nokia-key-core/               ← 保留：Client SDK
│   ├── build.gradle              (implementation project(':nokia-common'))
│   └── src/main/java/io/github/cctyl/nokia/keycore/
│       ├── NokiaClient.java      (implements ThemeProvider)
│       ├── NokiaKeyClient.java
│       ├── model/...
│       └── ui/...                (依赖 Client 的 UI 全部保留)
│
└── sample/                       ← 示例 App
    └── build.gradle             (implementation project(':nokia-key-core'))
```

### 6.2 `nokia-common/build.gradle`

```groovy
plugins { id 'com.android.library' }

android {
    namespace 'io.github.cctyl.nokia.common'
    compileSdk rootProject.ext.COMPILE_SDK
    defaultConfig { minSdk rootProject.ext.MIN_SDK }
    buildFeatures { buildConfig true }
}

dependencies {
    api 'androidx.appcompat:appcompat:1.6.1'   // api 让 core 传递依赖
}
```

> 使用 `api` 而非 `implementation`，使 `core` 与独立 App 能直接使用 `common` 暴露的 `appcompat` 类。

### 6.3 `nokia-key-core/build.gradle`

```groovy
dependencies {
    api project(':nokia-common')   // 传递依赖 common
    // core 自身不再重复声明 appcompat（由 common 传递）
}
```

### 6.4 `settings.gradle`

```groovy
rootProject.name = 'nokia-key-core'
include ':nokia-common', ':nokia-key-core', ':sample'
```

### 6.5 Maven 坐标

| 模块 | 坐标 | 说明 |
| :--- | :--- | :--- |
| `nokia-common` | `io.github.cctyl.nokia:nokia-common:1.0.0` | 纯基础库 |
| `nokia-key-core` | `io.github.cctyl.nokia:nokia-key-core:1.1.0` | **版本号升 minor**，因新增 common 依赖 |

### 6.6 launcher 接入（`keydroidx-launcher/app/build.gradle`）

```groovy
dependencies {
    implementation 'io.github.cctyl.nokia:nokia-common:1.0.0'
    // 不依赖 nokia-key-core
}
```

---

## 七、对各角色的影响

### 7.1 桌面（keydroidx-launcher）

| 维度 | 变化 |
| :--- | :--- |
| 依赖 | 新增 `nokia-common`，**不引入** `nokia-key-core` |
| 反馈页 | 用原生 `NokiaFeedbackFragment`（继承 `NokiaScrollPageFragment`），复用 common 的协议层 |
| 主题 | `LauncherThemeProvider implements ThemeProvider`，直接读本地 SP |
| 日志 | 直接用 common 的 `NokiaLog`，与桌面现有 `NokiaLog` 合并统一 |
| 密钥 | `local.properties` 注入 `KDFB_PRIVATE_KEY`，方式与独立 App 一致 |
| 架构合规 | ✅ 彻底解耦，不碰 Client |

### 7.2 独立 App（keydroidx-music 等）

| 维度 | 变化 |
| :--- | :--- |
| 依赖 | `nokia-key-core:1.1.0`（自动传递 `nokia-common`），**无需改动** |
| 接入代码 | Application 中新增一行 `NokiaTheme.setProvider(NokiaClient.get(this))` |
| 向后兼容 | ✅ 100% 兼容，API 表面不变 |

### 7.3 SDK 维护者

| 维度 | 变化 |
| :--- | :--- |
| 仓库结构 | 多一个 `nokia-common` 模块 |
| 发布 | 需同时发布 `nokia-common` 与 `nokia-key-core` 两个 artifact |
| 文档 | 新增本文档；更新 `README.md` 索引 |

---

## 八、迁移步骤（实施时参考）

> 以下为实施阶段逐步操作清单，**当前仅设计评审，暂不执行**。

### 阶段 1：拆出 common 模块（仅 keydroidx-core 仓库内）

1. 在 `keydroidx-core/` 下新建 `nokia-common/` 模块目录与 `build.gradle`；
2. `settings.gradle` 加入 `include ':nokia-common'`；
3. 将 §4.1 清单中的文件从 `nokia-key-core` 移动到 `nokia-common`，更新包名为 `io.github.cctyl.nokia.common.*`；
4. 重构 `NokiaTheme`：新增 `ThemeProvider` 接口 + `setProvider`，移除 `NokiaClient` 硬依赖；
5. `nokia-key-core` 声明 `api project(':nokia-common')`，更新所有 import 从 `keycore.ui.NokiaTheme` → `common.ui.NokiaTheme`；
6. `NokiaClient implements ThemeProvider`，在独立 App 入口注入；
7. 编译验证 `:nokia-key-core:assembleRelease` + `:sample:assembleDebug`。

### 阶段 2：发布与验证

1. `:nokia-common:publishReleasePublicationToMavenLocal`；
2. `:nokia-key-core:publishReleasePublicationToMavenLocal`；
3. `keydroidx-music` 升级依赖到 `1.1.0`，加一行 `setProvider`，编译装机验证反馈页与日志正常。

### 阶段 3：launcher 接入

1. `keydroidx-launcher/app/build.gradle` 新增 `implementation 'io.github.cctyl.nokia:nokia-common:1.0.0'`；
2. 桌面 `Application.onCreate` 注入 `LauncherThemeProvider` + 初始化 `NokiaFeedback`；
3. 实现 `NokiaFeedbackFragment`（原生 Fragment，复用 common 协议）；
4. 在 `NokiaSettingsGroupFragment` 或 `NokiaAboutFragment` 加入「意见反馈」入口；
5. 装机验证：主题跟随、日志落盘、反馈提交全链路。

---

## 九、兼容性与风险

### 9.1 向后兼容

- `nokia-key-core` 的公开 API 表面**不变**（包名 `io.github.cctyl.nokia.keycore.*` 保持）；
- 独立 App 升级 `1.1.0` 后只需补一行 `NokiaTheme.setProvider(...)`，其余零改动；
- 若不补 `setProvider`，`NokiaTheme` 会安全回退默认主题（功能不崩，仅主题不跟随桌面）。

### 9.2 风险与缓解

| 风险 | 缓解 |
| :--- | :--- |
| 包名迁移导致反射/ProGuard 规则失效 | 统一使用新包名 `io.github.cctyl.nokia.common.*`，同步更新 proguard 规则 |
| 两个 artifact 版本不一致（common 1.0 / core 1.1） | 在 `nokia-key-core` 的 `build.gradle` 中用 `api` 硬绑定 `nokia-common:1.0.0`，强制对齐 |
| 桌面 `NokiaLog` 与 common `NokiaLog` 重名冲突 | 桌面删除自有 `NokiaLog`，统一用 common 版本 |
| 桌面 `NokiaTheme` 与 common `NokiaTheme` 重名冲突 | 桌面删除自有 `NokiaTheme`，统一用 common 版本 |

---

## 十、与原始架构设计文档的关系

本方案是对《按键机生态与独立应用扩展架构设计》第四章「独立应用开发 SDK 与脚手架规划」的**落地细化与修正**：

| 原始设计（§4.1） | 本方案修正 |
| :--- | :--- |
| 单一 `nokia-keyphone-sdk` 模块 | 拆为 `nokia-common`（基础） + `nokia-key-core`（Client）两层 |
| 未预见桌面复用需求 | 新增 `ThemeProvider` 接口，使桌面能共享基础能力而不依赖 Client |
| `NokiaKeyResolver` / `NokiaScaledActivity` | 对应当前 `NokiaKeyBinding.resolveAction()` 与 `NokiaBaseActivity`，命名已落地 |

原始设计文档确立的**单向依赖原则**（独立 App → core → 桌面 Provider）在本方案中得到完整保持，并进一步延伸为：

> **桌面 → common**（共享基础，不碰 Client）
> **独立 App → core → common**（共享基础 + 叠加 Client）

---

## 十一、总结

本分层方案用最小代价解决了「桌面复用基础能力」与「桌面不依赖 Client」之间的矛盾：

1. **common** 承载全生态 80% 同构的纯基础代码（主题 / 日志 / 签名 / 协议），改一处全生态同步；
2. **core** 继续专注 Client 能力，独立 App 零迁移成本；
3. **launcher** 只依赖 common，用原生 Fragment 实现反馈页，架构彻底解耦；
4. 唯一的技术障碍（`NokiaTheme ↔ NokiaClient` 循环）通过 `ThemeProvider` 接口依赖倒置彻底打破。

> **下一步**：待用户评审通过后，按 §八 的三阶段实施。
