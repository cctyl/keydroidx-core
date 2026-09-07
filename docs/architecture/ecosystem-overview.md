# 生态演进愿景与跨应用协议

> **性质**：愿景与协议文档。§一 是立项时的痛点分析（原文保留，仍有价值）；§三 起为**当前实际协议**，已按代码校准。
> 分层与模块现状见 [module-layering.md](./module-layering.md)。
> 最近核实：2026-09-05

---

## 一、背景与设计目标

### 1.1 现状与痛点（立项原文保留）

在 Android 按键机（物理全键盘/T9 键盘设备）生态中，开发适配按键操作的应用面临以下核心挑战：

1. **物理键值高度碎片化**：不同品牌、不同型号的按键机，其确认键、左右软键、返回键甚至方向键的物理 KeyCode 差异巨大（例如部分机型确认键为 `KEYCODE_DPAD_CENTER(23)`，部分为 `KEYCODE_ENTER(66)` 或 `KEYCODE_MENU(82)`）。若每个应用都需要用户独立配置一次按键，交互极其繁琐。
2. **桌面集成（Monolith）的局限性**：
   - **工程臃肿与编译成本**：当前原键桌面（Launcher）集成了 J2ME-Loader 模拟器、C++ NDK 3D 渲染引擎、dxlib 工具链等复杂底层代码。若将音乐播放器、浏览器、文件管理器等全部塞入桌面工程，会导致主工程极其庞大，日常维护与构建调试成本成倍增加。
   - **系统常驻与内存稳定性**：桌面（Launcher）是系统常驻的 HOME 宿主。如果内置多媒体解码或复杂 WebView 渲染，一旦发生内存泄漏（OOM）或原生层崩溃，会导致整个桌面崩溃重启，严重破坏用户体验。
   - **阻碍社区协同开发**：外部开发者如果想贡献一个简单的按键机音乐播放器，需要克隆几十 MB 包含 NDK/C++ 的主仓库并配置繁琐的 NDK 工具链，门槛极高。

### 1.2 架构目标

- **Launcher 作为按键与服务中枢**：原键桌面负责统一管理物理按键映射、状态栏信息与桌面组件容器，并通过标准 IPC（ContentProvider）将按键配置安全共享给外部应用。
- **独立轻量 APK 矩阵**：音乐播放器、专用浏览器、记事本等作为独立 APK 存在，独立编译、独立进程、崩溃隔离。
- **即插即用，无感同步**：独立应用启动时自动从桌面读取按键配置，用户只需在桌面配一次按键，所有生态应用全自动适配。
- **极致轻量的开源接入方案**：提供极简的按键机应用开发模板（SDK/基类），外部开发者只需关注纯 Java/Kotlin 业务逻辑，5 分钟即可开发出完全适配按键机操作的复古应用。

---

## 二、整体架构拓扑（当前）

```
┌──────────────────────────────────────────────────────────────┐
│                     原键桌面 (keydroidx-launcher)             │
│                                                              │
│  ┌──────────────────────┐      ┌──────────────────────────┐  │
│  │  按键映射向导与存储    │      │   桌面组件与状态容器      │  │
│  │   (KeydroidxKeyBinding)  │      │  (「正在播放」等组件)     │  │
│  └──────────┬───────────┘      └───────────▲──────────────┘  │
│             │                              │                 │
│  ┌──────────▼───────────────────┐          │                 │
│  │      KeydroidxKeyProvider        │          │ MediaSession    │
│  │  content://.../keys          │          │ （主通道，       │
│  │  content://.../settings      │          │  不拉起进程）    │
│  └──────────┬───────────────────┘          │                 │
└─────────────┼──────────────────────────────┼─────────────────┘
              │ 按键 + 主题 + 字体             │ 播放状态
              │ （ContentProvider）            │ 歌词（Provider 补）
              ▼                               │
┌─────────────────────────────────┐           │
│  独立 App（music / foucs / …）   │───────────┘
│  - KeydroidxClient 自动同步          │
│  - KeydroidxBaseActivity 复古骨架    │
│  - 后台播放 Service（音乐）      │
└─────────────────────────────────┘
```

> 图上「按键共享」是 ContentProvider 单向查询（App → 桌面）；「正在播放」是 MediaSession + Provider 双通道（详见 §3.2）。
> **早期设计中的「App 向桌面发广播通知播放状态」方案从未实现**，见 §3.3。

---

## 三、通信协议与数据契约

### 3.1 按键与配置共享契约（`KeydroidxKeyProvider`）

桌面端对外暴露只读 `ContentProvider`，独立应用通过标准 `ContentResolver` 查询。契约定义在 `io.github.cctyl.nokia.common.contract.KeydroidxProviderContract`。

| 项 | 值 |
|:---|:---|
| Authority（正式版） | `io.github.cctyl.nokia.keyprovider` |
| Authority（Debug 版） | `io.github.cctyl.nokia.debug.keyprovider` |
| 按键路径 | `keys` |
| 配置路径 | `settings` |

**`/keys` 表结构**

| 列名 | 类型 | 说明 |
|:---|:---|:---|
| `action` | TEXT | 语义动作名：`UP` `DOWN` `LEFT` `RIGHT` `SELECT` `SOFT_LEFT` `SOFT_RIGHT` `LOCK_SCREEN` `CALL` |
| `actionId` | INTEGER | 语义动作枚举值（`KeydroidxKeyAction`：0~8） |
| `keyCode` | INTEGER | 绑定的物理 KeyEvent KeyCode |
| `keyName` | TEXT | 键名（便于调试与人读） |

**`/settings` 表结构**

| 列名 | 说明 |
|:---|:---|
| `key` / `value` | 通用键值对 |
| `theme_id` | 当前主题 ID |
| `font_id` | 当前字体 ID |
| `font_scale` | 当前字号倍率 |

> ⚠️ 早期设计文档中的 authority `ru.playsoftware.j2meloader.nokia.keyprovider` 已废弃，以 `KeydroidxProviderContract` 常量为准。
> 列名为 `actionId`/`keyCode`/`keyName`（不是 `action_code`/`key_code`），两端必须严格一致。

**客户端降级链路**：由 `KeydroidxClient.reload()` 执行，共四分支——正式 Provider → Debug Provider → 本地 SharedPreferences → 标准 Android 键码兜底。当 HOME 包名含 `debug` 时前两者的**探测顺序反转**。详见 [../guide/02-client.md](../guide/02-client.md)。

### 3.2 音乐 ↔ 桌面「正在播放」联动（当前真实实现）

桌面上的「正在播放」组件读取音乐 App 的播放状态与歌词，采用**双通道**。

#### 通道 1：MediaSession（主通道，读播放状态）

音乐 App 通过 media3 的 `MediaSessionService` 注册标准 MediaSession；桌面用 `MediaSessionManager.getActiveSessions()` 读取。

| 侧 | 实现 |
|:---|:---|
| 音乐 | `PlaybackService : MediaSessionService()`（`PlaybackService.kt:40`） |
| 桌面 | `KeydroidxMusicSessionReader`（`MediaSessionManager.getActiveSessions`） |
| 渲染 | `KeydroidxDesktopFragment` 的「正在播放」行（图标 + 歌名/歌手 + 歌词 + 进度条） |

**为什么不用 ContentProvider 读状态**（`KeydroidxMusicSessionReader` 类注释原文）：

> `ContentResolver.query()` 到某个 Provider 时，若提供它的进程尚未运行，AMS 会直接把该进程冷启动起来，调用方主线程同步等待。实测桌面在「清理后台 → 返回桌面」时，音乐 Provider 冷启动耗时约 1.2s，直接导致 Skipped 75 frames / 单帧 1303ms 的卡顿。
> MediaSession 通道没有这个问题：session 由 system_server 的 MediaSessionService 维护，`getActiveSessions` 只返回**当前已注册**的 session，拿不到就是没在播放，绝不会去拉起对方进程——这也正是官方「正在播放」控件的做法。

**前提**：需用户授予「通知使用权」。未授予或 API < 21 时回退到 Provider 查询。

#### 通道 2：ContentProvider（补歌词）

MediaSession 的 metadata 里没有歌词（音乐 App 只 set 了 title/artist/album），歌词由 Provider 补充。只在「正在播放」时查——此时对方进程必然存活，查询是毫秒级，不会触发冷启动。

| 项 | 值 |
|:---|:---|
| Authority | `io.github.cctyl.keydroidx.music.playback` |
| URI | `content://io.github.cctyl.keydroidx.music.playback/state` |
| Content-Type | `vnd.android.cursor.item/vnd.keydroidx.music.playback` |

**字段**（9 个）

| 列名 | 类型 | 说明 |
|:---|:---|:---|
| `song_id` | Long | 歌曲 ID |
| `title` | String | 歌曲标题 |
| `artist` | String | 歌手名 |
| `album_art_uri` | String | 专辑封面 URL |
| `is_playing` | Int | 0/1 |
| `position_ms` | Long | 当前播放位置 |
| `duration_ms` | Long | 总时长 |
| `lyric_text` | String | **当前歌词行文本**（无歌词时为空） |
| `updated_at` | Long | 数据更新时间戳 |

### 3.3 【从未实现】早期设计的广播协议

早期设计曾规划一套广播协议用于音乐向桌面推送状态：

- `ru.playsoftware.j2meloader.nokia.action.MUSIC_STATUS_CHANGED`（`extra_is_playing` / `extra_title` / `extra_artist` / `extra_duration` / `extra_position`）
- `ru.playsoftware.j2meloader.nokia.action.MUSIC_CONTROL`（`extra_command`：`TOGGLE_PLAY` / `PREV` / `NEXT` / `STOP`）

> ❌ **全生态 0 处引用，从未实现。** 实际落地的是 §3.2 的 MediaSession + Provider 双通道。
> 上述 Action 与 extra 名**不要在任何新代码中使用**。

---

## 四、早期 SDK 规划的落地情况

早期规划过一个独立 SDK `nokia-keyphone-sdk`，实际并未以该名称建成，其设计意图分别落在了 `keydroidx-common` / `keydroidx-key-core` 中：

| 早期规划 | 实际落地 | 状态 |
|:---|:---|:---|
| `KeydroidxKeyResolver`（自动解析按键 + DOWN/UP 配对） | `KeydroidxBaseActivity.dispatchKeyEvent` + `KeyResolver` / `DefaultKeyResolver` | ✅ 已实现（名不同） |
| `KeydroidxScaledActivity`（240dp 基准缩放 + 标题栏 + 三软键栏） | `common.ui.KeydroidxBaseActivity` | ✅ 已实现，但**缩放机制已改为响应式原生 DP**，见 [../spec/responsive-layout-spec.md](../spec/responsive-layout-spec.md) |
| `KeydroidxListPageActivity`（循环焦点导航） | `KeydroidxListPageFragment` / `KeydroidxListFocusHelper` | ✅ 已实现（Fragment 形态） |
| `KeydroidxScrollPageActivity`（平滑翻页，45% 视口步长） | `KeydroidxScrollPageFragment.getScrollStepPx()` | ✅ 已实现，默认**可视高度 45%**（兜底 100dp / 160px） |
| `retro-keyphone-template` 脚手架仓库 | — | ❌ 未建 |

---

## 五、生态路线完成情况

### 第一阶段：Provider 基础设施

- [x] 桌面实现 `KeydroidxKeyProvider`，暴露按键映射（`/keys`）
- [x] 额外暴露主题 / 字体配置（`/settings`）
- [x] 外部应用可安全查询（`exported="true"`）

### 第二阶段：官方参考实现 — 音乐

- [x] 独立仓库 `keydroidx-music`
- [x] 后台播放 Service（media3 `MediaSessionService`）
- [x] 诺基亚复古 S40 播放界面
- [x] 接入 `KeydroidxClient`，纯方向键选歌与控制
- [x] 与桌面「正在播放」组件联动（MediaSession + Provider 双通道）

### 第三阶段：官方参考实现 — 轻量浏览器

- [x] 独立仓库 `keydroidx-browser`（仓库已建）
- [ ] WebView 封装 / 虚拟光标导航模式 —— **仓库已建但功能未实现**：仅 3 个源文件的空骨架，未声明任何 SDK 依赖

### 第四阶段：SDK 发布与社区文档

- [x] SDK 发布（本地 Maven：`io.github.cctyl.nokia:{keydroidx-common,keydroidx-key-core,keydroidx-mini-shizuku}:1.0.0`）
- [ ] 发布到 JitPack 等公共仓库 —— 未做
- [x] 《接入指南》—— 本仓库 `docs/guide/` 01~16
- [x] 开发规范 —— `docs/NOKIA_DEVELOPMENT_RULES.md`
- [ ] 脚手架模板仓库 —— 未建

### 计划外已落地

- [x] `keydroidx-foucs`（专注类 App）：目前**最贴近目标接入形态**的样板，见 [module-layering.md](./module-layering.md) §四
- [x] `keydroidx-mini-shizuku` 独立 IPC 模块

---

## 六、总结

「**Launcher 作为按键与服务中枢 + 独立轻量生态 APK 矩阵**」的架构已经落地并验证：桌面常驻稳定、独立 App 独立进程崩溃隔离、按键与主题字体零配置同步。

跨应用通信沉淀出两条经验：

1. **按键 / 配置同步**用 ContentProvider 单向查询（App → 桌面），配四级降级保证桌面未安装时仍可用。
2. **实时状态（如播放中）不要用 ContentProvider 轮询**——会冷启动对方进程导致调用方卡顿。应优先用 MediaSession 这类由 system_server 维护、只在对方存活时返回数据的通道，Provider 只用于补充对方已存活时才有意义的增量数据（如歌词）。
