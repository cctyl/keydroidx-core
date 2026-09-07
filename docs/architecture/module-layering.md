# 生态分层架构（现状 + 决策档案）

> **状态**：已实施落地（本文为现状说明，非设计方案）
> **最近核实**：2026-09-06，依据 `keydroidx-core` / `keydroidx-launcher` / `keydroidx-music` / `keydroidx-foucs` 四仓代码（import 数字为语句口径，见 §四脚注）

本文回答两个问题：**现在生态到底长什么样**（§一~§五），以及**为什么是这三层**（§六 决策档案）。

---

## 一、生态全景

```
                          keydroidx-core 仓库
        ┌──────────────────────────────────────────────────┐
        │  keydroidx-common          keydroidx-key-core    │
        │  （通用基础库）            （Provider 客户端）   │
        │                                                  │
        │  keydroidx-mini-shizuku    sample（测试台）      │
        │  （独立 IPC 模块）                               │
        └──────────────────────────────────────────────────┘
             │                          │
   只依赖 common                  依赖 key-core
             │                          │
             ▼                          ▼
   keydroidx-launcher      ┌─────────────┴─────────────┐
   （桌面 / Provider 宿主）  │                            │
                           ▼                            ▼
                  keydroidx-music              keydroidx-foucs
                  （独立 App）                  （独立 App）
                                                     │
                                                     │ 另外依赖
                                                     ▼
                                            keydroidx-mini-shizuku

   keydroidx-browser —— 仅 3 个源文件的空骨架，尚未接入 SDK
```

**关键分工**：桌面是 Provider 的**提供方**，音乐 / Focus 是**调用方**，两者依赖的模块因此不同——这不是遗漏，是架构要求。

---

## 二、四个模块

| 模块 | Maven 坐标 | 定位 |
|:---|:---|:---|
| `keydroidx-common` | `io.github.cctyl.nokia:keydroidx-common:1.0.0` | 全生态共享的通用基础库，零业务依赖 |
| `keydroidx-key-core` | `io.github.cctyl.nokia:keydroidx-key-core:1.0.0` | 面向独立 App 的 Provider 客户端，依赖 common |
| `keydroidx-mini-shizuku` | `io.github.cctyl.nokia:keydroidx-mini-shizuku:1.0.0` | 独立 IPC 客户端，**刻意保持零依赖**（仅 3 个类） |
| `sample` | — | 安装统计 / 更新检查 / 权限 / Shizuku 的**测试台**，不是接入示例 |

> `keydroidx-mini-shizuku` 是**独立模块，不在 key-core 内**。它刻意不使用 `KeydroidxLog`（会破坏零依赖定位），是 `AGENTS.md` 日志规范的唯一例外。
> 消费方：`keydroidx-foucs` 与 `keydroidx-launcher`（音乐未使用）。

> ⚠️ **发布范围**：该模块发布产物与引用坐标已一致（`io.github.cctyl.nokia:keydroidx-mini-shizuku:1.0.0`，`keydroidx-mini-shizuku/build.gradle` 发布配置如此，`keydroidx-foucs` / launcher 的 mini_shizuku 亦按此坐标引用）。它**只发布到 mavenLocal，未上 JitPack 等公共仓库**，故多仓联调必须走 `includeBuild` + 显式 `substitute`（见 §7.1），脱离 includeBuild 单靠坐标从远端取会失败——这是**发布范围**问题，不是坐标写法问题。

### 环境

`minSdk 19` / `targetSdk 34` / `compileSdk 34`，JDK 17（AGP 8.5.1）。

> launcher 为对齐 common 曾将 `minSdk` 从 14 提到 19。

---

## 三、keydroidx-key-core 的真实构成（重要）

`keydroidx-key-core` 现共 **7 个类**（2026-09-06 删除全部兼容空壳后）：

### A 组：key-core 独有实现（7 个，无 `@Deprecated`）

这就是「调用桌面 Provider」的那部分能力，**完好存在**：

| 类 | 行数 | 职责 |
|:---|:---|:---|
| `KeydroidxClient` | 338 | Provider 查询 + ContentObserver + 主题/字体/按键同步 |
| `model/KeydroidxKeyBinding` | 163 | 按键映射表与兜底解析 |
| `ui/KeydroidxKeyWizardActivity` | 149 | 改键向导 |
| `ui/KeydroidxTextInputActivity` | 85 | 文本输入页（薄壳，托管 `common.ui.KeydroidxTextInputFragment`） |
| `ui/KeydroidxBaseActivity` | 80 | 继承 common 的骨架，仅叠加 KeydroidxClient 绑定 |
| `ui/KeydroidxAboutActivity` | 46 | 关于页壳 |
| `ui/KeydroidxFeedbackActivity` | 38 | 反馈页壳 |

> `keycore.ui.KeydroidxBaseActivity` **不是独立实现**，它 `extends common.ui.KeydroidxBaseActivity implements KeydroidxClient.OnConfigChangedListener`。分工是：common 出纯骨架，core 出 Provider 绑定。

### B 组：兼容桥接（16 个，已于 2026-09-06 删除）

通用 UI/工具类抽到 common 后，core 里曾留下同名空壳：

```java
@Deprecated
public class KeydroidxTheme extends io.github.cctyl.nokia.common.ui.KeydroidxTheme {
}
```

清单：`KeydroidxTheme`、`KeydroidxFontManager`、`KeydroidxIcons`、`KeydroidxKeyAction`、`KeydroidxFeedback`、`KeydroidxFeedbackConfig`、`KeydroidxPage`、`KeydroidxPageHost`、`KeydroidxPageFragment`、`KeydroidxListPageFragment`、`KeydroidxScrollPageFragment`、`KeydroidxListFocusHelper`、`KeydroidxFocusHost`、`KeydroidxDialogFocus`、`KeydroidxOptionsDialog`、`KeydroidxConfirmDialog`。

**现已全部删除**：

- 生态内引用已全部改指 `io.github.cctyl.nokia.common.*`（music 34 处 import、key-core 内部 4 处）；launcher / foucs / browser / sample 原本 0 引用，无需改动。
- 行为等价：空壳只做继承转发；唯一带额外逻辑的是三个 `PageFragment` 空壳的 `onApplyFonts`（`super` 之后再调一次 `KeydroidxFontManager.applyToViewTree`），而 `common.ui.page.KeydroidxPageFragment.onApplyFonts` 本身已调用一次，删除只是少了一次幂等重复调用。
- **破坏性变更**：外部若仍 import `keycore.ui.*` / `keycore.ui.page.*` / `keycore.ui.dialog.*` / `keycore.ui.focus.*` / `keycore.feedback.*` / `keycore.model.KeydroidxKeyAction`，会直接编译失败，改成 `common.` 同路径即可（类名不变）。

> **新代码一律用 `io.github.cctyl.nokia.common.*`**；`keycore.*` 只剩上面 A 组 8 个 common 确实没有的类。
> 原 `keycore.log` 包中的日志桥接类已随 2026-09-06 生态类名重构（Nokia→Keydroidx）删除（生态内 0 引用）。
> 迁移记录见 [key-core-migration.md](./key-core-migration.md)。

---

## 四、三个接入方的正确姿势

| | keydroidx-launcher | keydroidx-music | keydroidx-foucs |
|:---|:---|:---|:---|
| 角色 | Provider **宿主**（Server） | Provider **调用方** | Provider **调用方** |
| 依赖 | 仅 `keydroidx-common` | 仅 `keydroidx-key-core` | `key-core` + `common` + `mini-shizuku` |
| import 统计 | ≈200 处 `common.*`，0 处 `keycore.*` | ≈49 处 `common.*` + 11 处 `keycore.*` | ≈17 处 `common.*` + 7 处 `keycore.*` + 1 处 shizuku |
| 成熟度 | 完整 | 完整（2026-09-06 已完成迁移） | 完整，且是**最贴近目标形态**的接入方 |

> **import 口径**：按 `import io.github.cctyl.nokia.{common,keycore}.` 语句数统计（`app/src/main` 内 `.java`/`.kt`，不含全限定引用与注释），基线 2026-09-06。此类数字随代码演进即过期，只作量级参考。

> **keydroidx-browser** 目前只有 3 个源文件、未声明任何 SDK 依赖，属于空骨架，不在上表内。

### Focus 是「目标形态」的样板

它同时依赖 key-core 与 common，但引用分布很说明问题：

- **只从 key-core 取的**（common 里确实没有）：`KeydroidxClient`、`keycore.ui.KeydroidxBaseActivity`、`KeydroidxFeedbackActivity`、`KeydroidxAboutActivity`
- **其余全部走 common**：`KeydroidxLog`、`KeydroidxIcons`、`KeydroidxDimens`、`KeydroidxFontManager`、`KeydroidxOptionsDialog`、`KeydroidxListPageFragment`、`KeydroidxScrollPageFragment`、`KeydroidxAppPickerFragment`、`KeydroidxAboutConfig`、`KeydroidxFeedback*`、`KeydroidxInstall`

这正是 [key-core-migration.md](./key-core-migration.md) 想让音乐达到的形态。

### 音乐的迁移中间态（已于 2026-09-06 结束）

音乐原本 7 处 `common.*` 全是 common 独有的新能力（`KeydroidxFeedback` / `KeydroidxFeedbackConfig` / `KeydroidxInstall` / `KeydroidxLog` / `KeydroidxTheme` / `KeydroidxAboutConfig`），而 `MainActivity.kt` 同时 import `common.ui.KeydroidxTheme` 与 `keycore.ui.KeydroidxFontManager`——同一能力的两个包名混用。

现在 34 处空壳引用已改为 `common.*`，`keycore.*` 只剩 11 处，且全部属于 A 组（common 没有的类）。

---

## 五、关键机制：ThemeProvider 依赖倒置

`common` 的 `KeydroidxTheme` 需要「当前主题」，但主题来源在两边不同。解法是在 common 定义抽象，由上层注入：

```java
// common 层
public interface ThemeProvider {
    KeydroidxTheme.ThemeDef getCurrentTheme(Context context);
}

KeydroidxTheme.setThemeProvider(ThemeProvider provider);   // 注入入口
KeydroidxTheme.getCurrentTheme(Context context);           // 读取入口
KeydroidxTheme.getThemes();                                // 返回 List<ThemeDef>
```

| 层 | 实现 | 注入位置 |
|:---|:---|:---|
| core | `KeydroidxClient implements ThemeProvider`，跨进程查桌面并缓存 | `KeydroidxClient.get()` 内自注入 |
| launcher | `LauncherThemeProvider`，直接读本地 SP，不跨进程查自己 | `EmulatorApplication.java:80` |

> 独立 App 只需 `KeydroidxClient.get(this)` 一行即完成 Provider 绑定 + 主题注入，无需手动 `setThemeProvider`。

---

## 六、决策档案：为什么拆出 common

> 本节为历史决策记录，说明「为什么是这三层」。原始设计文档见 `ecosystem-overview.md`。

### 6.1 拆分前的痛点

`keydroidx-key-core` 单模块内曾同时承载两类性质完全不同的能力：

| 性质 | 代表类 | 服务对象 |
|:---|:---|:---|
| 通用基础能力（纯算法/UI 规范/协议） | `KeydroidxTheme`、`KeydroidxLog`、`FeedbackUploader`、`DeviceInfoCollector` | 桌面 + 独立 App 都需要 |
| 客户端专属能力（跨进程查询/改键向导） | `KeydroidxClient`、`KeydroidxKeyWizardActivity` | 仅独立 App 需要 |

桌面自己也需要「意见反馈 + 日志上报 + 主题调色板 + 文件日志器」，但复用时陷入二选一困境：

- **困境 A — 桌面直接依赖 `keydroidx-key-core`**：违背单向依赖原则（桌面是 Server，不应反向依赖面向独立 App 的 Client SDK）；桌面被迫引入 `KeydroidxClient` 去跨进程查**自己的** Provider，逻辑倒置；SDK 迭代会牵动系统常驻 Launcher。
- **困境 B — 源码硬拷贝**：`KeydroidxTheme`（6 套调色板）、`KeydroidxLog`（7 天轮转 + 崩溃捕获）、`FeedbackUploader`（二进制协议打包）在两仓各存一份，改一处要人工双向同步，极易代码分叉。

### 6.2 唯一技术障碍：循环依赖

```
KeydroidxTheme（想进 common）
    │  第 137 行调用
    ▼
KeydroidxClient（留在 core）
    │  import
    ▼
KeydroidxTheme（已在 common）  ← 循环！
```

直接搬运会让 `common` 反向依赖 `core`，Gradle 直接报错。

### 6.3 解法

引入 `ThemeProvider` 抽象接口做依赖倒置（见 §五），`common` 零依赖 `core`，循环打破。同时建立 `keydroidx-common` 承载全部共享能力。

### 6.4 目标达成情况

| 目标 | 状态 |
|:---|:---|
| 桌面只依赖 common，彻底不碰 `KeydroidxClient` | ✅ 0 处 keycore import |
| 底层代码全生态只有一份源码 | ✅ 已迁移（2026-09-06 删除 16 个空壳，common 是唯一源码） |
| core 继续服务独立 App，API 向后兼容 | ⚠️ 已放弃兼容：空壳桥接已删除，`keycore.*` 只剩 7 个 common 没有的类 |
| 桌面反馈页用原生 Fragment 复用 common 协议 | ✅ `KeydroidxFeedbackFragment` |

---

## 七、接入配置要点

### 7.1 `includeBuild` 必须写显式 substitution

```gradle
// settings.gradle
includeBuild('../keydroidx-core') {
    dependencySubstitution {
        substitute module('io.github.cctyl.nokia:keydroidx-common') using project(':keydroidx-common')
        substitute module('io.github.cctyl.nokia:keydroidx-key-core') using project(':keydroidx-key-core')
    }
}
```

> ⚠️ **只写 `includeBuild` 不够**。因 core 仓库的模块未声明 `project group`，Gradle 的自动替换会**静默失败**并回退到已缓存的远端产物，导致本地改动不生效且无任何报错。必须显式写 `substitute`。

### 7.2 Android 11+ 包可见性

- 引入 **`keydroidx-key-core`** 时，`<queries>` 由 key-core 的 manifest **自动合并**，业务无需声明（其 manifest 已声明 2 个 provider authority + 4 个内置 Activity）。
- 仅引入 **`keydroidx-common`** 且需自行查询桌面 Provider 时，需在业务 manifest 中自行声明。

### 7.3 第三方依赖

`keydroidx-common` 通过 `api` 暴露 **XXPermissions 20.0**（`com.github.getActivity:XXPermissions:20.0`），因此「零第三方依赖」的说法**不成立**——接入方会传递性引入它。

---

## 八、相关文档

- 生态演进愿景与联动协议 → [ecosystem-overview.md](./ecosystem-overview.md)
- key-core → common 代码迁移方案 → [key-core-migration.md](./key-core-migration.md)
- 接入步骤 → [../guide/01-getting-started.md](../guide/01-getting-started.md)
- 响应式布局规范 → [../spec/responsive-layout-spec.md](../spec/responsive-layout-spec.md)
