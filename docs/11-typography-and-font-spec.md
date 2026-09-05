# 11 · 字体与字号排版设计规范

> **本文件是 KeydroidX 按键机生态字号体系的唯一事实源 (Single Source of Truth)。**
> 生态内所有 App（桌面 Launcher、Music 及后续衍生应用）的字体大小、语义档位、缩放机制，**一律以本文件为准**，与 `nokia-common/res/values/dimens.xml` 的 Token 严格一一对应。

---

## 0. 一句话总则

> **在 240×320 基准分辨率下，以 `font_scale = 1.0` 为"标准舒适默认"，定义 6 档语义字号；用户在桌面"设置 → 字体大小"调节 `font_scale`（1.0 / 1.25 / 1.5…），所有字号统一乘以该倍率。**

实际渲染字号 = 基准字号（Token）× `font_scale`

- 基准字号是设计师在 1.0x 下敲定的"标准舒适大小"，**不是"过小"**。
- `font_scale = 1.0` 就是标准默认；1.25 / 1.5 是给视力需求或特殊机型留的可达性放大，**不作为默认值**。
- 所有 App 通过 `KeyProvider` 读取同一个 `font_scale`，经 `NokiaFontManager` 统一下发，保证三边渲染一致。

---

## 1. 核心设计原则

1. **基准视口原则（240×320）**
   - 生态所有界面的设计基准分辨率为 **240dp × 320dp**。
   - `NokiaBaseActivity` 在初始化时按屏幕宽度比例将 DPI 规范化缩放至 240 视口，开发者在布局文件中编写的 `sp` 均直接映射到该基准。
2. **1.0x 即舒适默认**
   - `font_scale = 1.0` 是设计师认可的"标准舒适大小"，**不再是"过小需放大"**。
   - 严禁把 1.5x 当默认——那等于承认基准本身设计错了。基准错了应改基准，而不是用倍率去补。
3. **点阵像素字体的细腻感**
   - 生态使用 ArkPixel-12px / FusionPixel-12px 等点阵像素字体，像素对齐天然锐利，故基准字号可小于通用功能机（KaiOS body ~14–16px），以换取更高信息密度。
   - **禁止滥用 `android:textStyle="bold"`**：点阵字体加粗只是算法横向加重，会使像素糊成块。仅 `nokia_font_display` 大标题可在必要时加粗。
4. **单一缩放源**
   - 缩放倍率只存在一个存储点：`io.github.cctyl.nokia.common.ui.NokiaFontManager.sFontScale`。
   - `Configuration.fontScale` 必须固定为 `1.0`（中和系统字体设置，避免 sp 字号被系统字体大小二次干扰），**用户倍率只走 `sFontScale`**，绝不把 `userFontScale` 写进 `Configuration.fontScale`（双重缩放陷阱，见 §6.1）。
   - 生态只有一份 `NokiaFontManager` 实现（common 那一套）。衍生 App / Launcher 禁止另起本地 `NokiaFontManager` 静态字段，必须共用 common 那一套；启动早期把桌面读到的 `font_scale` 与 `font_id` 经 `NokiaFontManager.setFontScale()` / `setCurrentFontId()` 写入 common。
5. **Token 即法律**
   - 禁止在 XML 或代码里裸写字号数字（`android:textSize="9sp"`、`setTextSize(tv, 9f)`）。
   - 一律引用 `@dimen/nokia_font_*` Token 或 `NokiaFontManager.setTextSizeResource(tv, R.dimen.nokia_font_*)`。
   - 改一个 Token，全生态对应语义同步生效。

---

## 2. 官方字号阶梯 (Typography Tokens)

**基准倍率 `font_scale = 1.0` 下的 6 档语义字号**。已沉淀在 `nokia-common/res/values/dimens.xml`：

| Token | 基准 (1.0x) | 语义层级 | 适用场景 | 真实代码范例 |
| :--- | :--- | :--- | :--- | :--- |
| **`@dimen/nokia_font_display`** | **16sp** | 大主视觉 | 大数字时钟、About 应用名、首屏核心主视觉大标题 | 桌面时钟大数字、关于页 App 名 |
| **`@dimen/nokia_font_title`** | **13sp** | 页面/弹窗标题 | 页面大标题、弹窗标题栏、软键中键主操作、播放器歌名 | `NokiaBaseActivity` 顶栏/中键、`NokiaOptionsDialog` 标题 |
| **`@dimen/nokia_font_body`** | **12sp** | **核心正文/列表项** | 桌面所有单列设置菜单（5.png）、意见反馈主 Label、歌曲名/歌单名、弹窗确认正文 | 桌面设置列表项、反馈表单项、`item_song` 主标题 |
| **`@dimen/nokia_font_small_title`** | **11sp** | 小标题/操作按钮 | 左右软键文本、表单提交按钮、分组小标题栏（Section Header）、弹窗列表选项 | `tv_soft_left` / `tv_soft_right`、提交按钮、`NokiaOptionsDialog` 行 |
| **`@dimen/nokia_font_caption`** | **9sp** | **辅助副文本** | 歌手名、专辑名、输入框占位符、免责说明、九宫格应用名、卡片副标题 | `item_song` 副文本、输入框 hint、关于页副标题、九宫格应用名 |
| **`@dimen/nokia_font_micro`** | **7sp** | 极小角标 | 角标 Badge、下载进度百分比、微型徽标、极致紧凑状态 | 下载角标、未读数 |

### 2.1 档位选用规则

- **正文一律 `nokia_font_body` (12sp)**：单列列表主标题、歌名、设置项名、反馈表单主 Label、输入框主文本。**单列全宽列表必须使用 12sp**，与桌面设置菜单保持 100% 视觉对齐。
- **副文本一律 `nokia_font_caption` (9sp)**：歌手、专辑、序号、时间、URL、卡片副标题、输入框占位符、免责提示说明、九宫格应用名。
- **操作/小标题栏用 `nokia_font_small_title` (11sp)**：左右软键文本、表单提交按钮、分组小标题栏（Section Header）、选项弹窗内容行。Section Header 不加粗、半透明背景。
- **弹窗/页面主标题用 `nokia_font_title` (13sp)**：顶栏页面大标题、弹窗标题栏、播放器歌曲大名、软键中键主操作。
- **大数字 / About 应用名用 `nokia_font_display` (16sp)**：仅在强调场景出现，整屏不超过一处。
- **`nokia_font_micro` (7sp) 慎用**：仅限角标/徽标，不可用于承载阅读内容的正文。

### 2.2 各倍率下的实际渲染

| Token | 1.0x | 1.25x | 1.5x |
| :--- | :--- | :--- | :--- |
| `nokia_font_display` | 16sp | 20sp | 24sp |
| `nokia_font_title` | 13sp | 16.25sp | 19.5sp |
| `nokia_font_body` | 12sp | 15sp | 18sp |
| `nokia_font_small_title` | 11sp | 13.75sp | 16.5sp |
| `nokia_font_caption` | 9sp | 11.25sp | 13.5sp |
| `nokia_font_micro` | 7sp | 8.75sp | 10.5sp |

> 上表用于设计评审与真机对照。1.0x 是默认，1.25/1.5 是可选放大。

---

## 3. 常见控件尺寸与间距推荐

为保证独立 App 与桌面 Launcher 交互体验一致，控件尺寸与字号必须配套：

| 控件类型 | 推荐尺寸/高度 | 主文本 Token | 副文本 Token | 备注 |
| :--- | :--- | :--- | :--- | :--- |
| **标准列表行 (List Row)** | `minHeight="36dp ~ 38dp"` | `nokia_font_body` (12sp) | `nokia_font_caption` (9sp) | 左侧图标 20dp，饱满清晰 |
| **两行列表行 (Two-line Row)** | `minHeight="44dp ~ 48dp"` | `nokia_font_body` (12sp) | `nokia_font_caption` (9sp) | 主文本视觉占优 |
| **快捷宫格卡片 (Grid Card)** | `height="42dp ~ 46dp"` | `nokia_font_body` (12sp) | `nokia_font_caption` (9sp) | 卡片图标 20dp ~ 22dp |
| **分组小标题栏 (Section Header)** | `height="20dp ~ 22dp"` | `nokia_font_small_title` (11sp) | — | 不加粗，半透明背景 |
| **弹窗标题栏 (Dialog Title)** | `height="28dp"` | `nokia_font_title` (13sp) | — | 背景为主题色渐变 |
| **弹窗内容行 (Dialog Row)** | `minHeight="32dp ~ 36dp"` | `nokia_font_small_title` (11sp) | — | 选中时圆角高亮 |
| **软键栏 (Softkey Bar)** | `height="22dp"` | `nokia_font_small_title` (11sp) | — | 中间标题 ≤4字 12sp / 5~6字 11sp / ≥7字 10sp（动态自适应，仍以 11sp 为基准） |
| **大数字时钟 (Clock)** | — | `nokia_font_display` (16sp) | — | 整屏仅一处 |

---

## 4. 全局自动字体与字号缩放机制

### 4.1 缩放链路

```
桌面【设置 → 字体大小】
        │ 写入 SharedPreferences
        ▼
KeyProvider (ContentProvider) 暴露 font_scale
        │ 各 App 通过 <queries> 跨进程读取
        ▼
NokiaClient / NokiaBaseActivity 启动早期读取 font_scale + font_id
        │ 调用 NokiaFontManager.setFontScale() / setCurrentFontId()
        ▼
NokiaFontManager.sFontScale / sCurrentFontId  ← 全局唯一缩放源
        │ applyToViewTree() 遍历时乘以 sFontScale
        ▼
TextView 实际渲染：基准设计 px × sFontScale
```

**关键约束**：
- `sFontScale` 是全进程静态字段，由 `NokiaClient`（衍生 App）或 `NokiaBaseActivity.attachBaseContext`（Launcher 自身）在启动早期**唯一**写入。
- 衍生 App 经 `NokiaClient` 读 KeyProvider；Launcher 自身直接读自身 SharedPreferences（`NokiaSettingsStorage`），两者最终都调 common 的 `NokiaFontManager.setFontScale()`，殊途同归。
- `Configuration.fontScale` 固定为 `1.0`（中和系统字体设置），用户倍率只走 `NokiaFontManager.sFontScale`，杜绝双重缩放。

### 4.2 零手动负担：View 树自动拦截

SDK 在 `NokiaBaseActivity` / `NokiaFontManager` 层内置 `ViewGroup.OnHierarchyChangeListener` 自动劫持：
- **静态 XML 布局**：`NokiaBaseActivity` 初始化时自动遍历整棵 View 树，记录每个 `TextView` 的"设计基准 px"并乘以 `sFontScale`。
- **动态创建 View**：无论是 `container.removeAllViews()` 后重新 `inflate`，还是代码 `container.addView(tv)`，HierarchyWatcher 都会在子节点挂载瞬间自动应用点阵 Typeface 并乘以 `sFontScale`。
- **业务开发者无需手动编写任何 `NokiaFontManager.applyToViewTree` 代码**（动态添加的根容器除外，需调一次以挂载 watcher）。

### 4.3 动态设置字号的安全 API

代码中动态设置字号时，**严禁直接调用 `tv.setTextSize(sp)`**（会破坏原始设计基准记录，导致重复缩放或漏缩放）。必须使用 SDK 助手方法：

```java
// 方式一（首选）：引用 dimens Token，语义清晰
NokiaFontManager.setTextSizeResource(myTextView, R.dimen.nokia_font_body);

// 方式二：直接指定设计 SP 字号（自动记录基准并即时乘以 fontScale）
NokiaFontManager.setTextSize(myTextView, 9f);   // 仅当无法引用资源时使用
```

> 即便使用方式二，传入的数字也必须与本文件 §2 的某档基准一致（9 / 8 / 11 / 13 / 16 / 7），**不得出现 8.5 / 10 / 12 / 15 等非档位值**。

---

## 5. 使用规则（强制）

1. **必须用 Token，禁止裸写数字**
   - XML：`android:textSize="@dimen/nokia_font_body"` ✅ / `android:textSize="9sp"` ❌
   - 代码：`NokiaFontManager.setTextSizeResource(tv, R.dimen.nokia_font_body)` ✅ / `tv.setTextSize(9f)` ❌

2. **统一单位 sp**
   - 所有文字字号用 `sp`。**禁止用 `dp` 写文字字号**（common `activity_nokia_base.xml` 历史用 `11dp/14dp/16dp`，需收口为 `sp`）。
   - `dp` 仅用于控件尺寸、间距、图标大小。

3. **禁止小数 sp**
   - 禁止 `8.5sp` 这类小数——点阵字体在非整数像素上会插值糊化。所有字号取整数。

4. **禁止 `textStyle="bold"` 用于 ≤13sp 文本**
   - 点阵字体加粗是算法加重，小字号下会糊。仅 `nokia_font_display` (16sp) 大标题可在必要时加粗。

5. **单行截断规范**
   - 屏宽 240px，主标题务必 `android:singleLine="true"` + `android:ellipsize="end"`；焦点项可配合 Marquee 滚动。

6. **软键栏三栏等宽**
   - 软键栏为三栏等宽（`0dp + weight=1`），空软键用 `View.INVISIBLE` 占位，**严禁 `View.GONE`**（会塌陷布局）。
   - 软键文字基准 `nokia_font_small_title` (11sp)，中间标题按字数动态自适应：≤4字 12sp / 5~6字 11sp / ≥7字 10sp，`ellipsize="middle"`。

---

## 6. 排版避坑指南

### 6.1 双重缩放陷阱
- **现象**：文字被放大到 2.25 倍（1.5 × 1.5）。
- **根因**：宿主在 `attachBaseContext` 把 `Configuration.fontScale` 设为 `userFontScale`（如 1.5），**同时** `NokiaFontManager.sFontScale` 也被设为 1.5；XML inflate 时系统先按 `Configuration.fontScale` 放大一次，`NokiaFontManager` 又乘一次。
- **对策**：`Configuration.fontScale` 固定为 `1.0`（仅中和系统字体设置），用户倍率只走 `NokiaFontManager.sFontScale` 一条路。

### 6.2 漏缩放陷阱（本规范重点修复项）
- **现象**：某 App 的"关于"页字体明显比其他 App 小。
- **根因**：该 App 未把桌面 `font_scale` 同步进 common 的 `NokiaFontManager.sFontScale`，`sFontScale` 停在默认 1.0，所有走 common 的 Fragment（About、弹窗）拿到的是 1.0 倍率。
- **对策**：所有 App（含 Launcher）必须经 `NokiaClient` 把 `font_scale` 写进 common `NokiaFontManager`，三边共用同一 `sFontScale`。

### 6.3 重复放大陷阱
- **现象**：动态创建的 TextView 字越来越大，有的字大有的字小。
- **根因**：直接 `tv.setTextSize(px)`，每次 `applyToViewTree` 都把"当前已放大值"当"设计值"再次放大。
- **对策**：动态设字号走 `NokiaFontManager.setTextSizeResource / setTextSize`，由 SDK 记录"设计基准 px"，重复应用不漂移。

### 6.4 字体覆盖陷阱
- **现象**：MaterialIcons 矢量图标变成方块字。
- **根因**：`applyToViewTree` 把点阵字体覆盖到了图标字体上。
- **对策**：SDK 已内置保护——检测到当前 Typeface 是 `NokiaIcons` 字体时自动跳过。业务层无需处理，但**禁止**对图标 `TextView` 手动 `setTypeface(...)`。

---

## 7. 自查清单

实现任何页面 / 修改任何字号前，对照：

- [ ] 所有文字字号引用 `@dimen/nokia_font_*` Token，无裸写数字。
- [ ] 文字单位统一 `sp`，无 `dp` 写字号。
- [ ] 无小数 sp（如 8.5sp）。
- [ ] ≤13sp 文本无 `bold`。
- [ ] 正文用 `nokia_font_body` (12sp)，副文本用 `nokia_font_caption` (9sp)，未用 14/15sp 当正文。
- [ ] 弹窗标题 `nokia_font_title` (13sp)，弹窗内容 `nokia_font_small_title` (11sp)。
- [ ] 分组小标题用 `nokia_font_small_title` (11sp)。
- [ ] 动态建字走 `NokiaFontManager.setTextSizeResource`，未直接 `tv.setTextSize`。
- [ ] 未在 `attachBaseContext` 把 `userFontScale` 写进 `Configuration.fontScale`（应固定为 `1.0` 中和系统设置，倍率走 `NokiaFontManager.sFontScale`）。
- [ ] App 已接入 `NokiaClient`，`font_scale` 同步进 common `NokiaFontManager`。

---

## 8. 迁移说明（向本规范对齐）

本规范发布时，存量代码存在以下偏差，需在后续提交中逐项收口（不在本次规范提交内动代码）：

| 偏差 | 现状 | 目标 |
| :--- | :--- | :--- |
| `dimens.xml` Token 统一 | 12sp / 11sp / 9sp / 7sp | 统一收口到 `@dimen/nokia_font_*` 6 级层级 |
| 列表主标题字号不一 | 9 / 12 / 14 / 15 sp 混用 | 统一 `nokia_font_body` (12sp) |
| common 文字用 `dp` | `activity_nokia_base.xml` 用 `11dp/14dp/16dp` | 改为 `sp` 并引用 Token |
| 小数 sp | 关于页 `8.5sp` | 改为 `8sp/9sp` 标准整数字号 |
| 关于页/弹窗裸写数字 | `setTextSize(tv, 9)` 等 | 改为 `setTextSizeResource(..., R.dimen.nokia_font_body)` |
| `font_scale` 默认 1.5 | 把"标准"当"过小" | 默认改回 `1.0`，倍率平滑缩放 |
| Music 死 Token `music_font_*` | 与 `nokia_font_*` 重复且无人引用 | 删除，Music XML 迁移到 `nokia_font_*` |

> 已完成：~~Launcher 本地 `NokiaFontManager` 与 common 字段不通~~ → 已统一进 common（本地类删除，全部 27+2 处引用改用 `io.github.cctyl.nokia.common.ui.NokiaFontManager`，`sFontScale` 为唯一缩放源，`attachBaseContext` 固定 `Configuration.fontScale=1.0`）。

---

## 9. 版本

- **v2.0** — 重定为 6 档语义阶梯（display/title/small_title/body/caption/micro = 16/13/11/9/8/7 sp），明确 `font_scale=1.0` 为标准舒适默认，确立 `NokiaFontManager.sFontScale` 为唯一缩放源，禁止裸写数字与 `Configuration.fontScale` 改写。
- v1.0 — 初版（Token 与实际用法不一致，已废弃）。
