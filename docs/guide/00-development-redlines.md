# 00 · 接入红线（开发前必读）

> **适用范围**：接入 `keydroidx-key-core`、采用完整复古骨架的生态独立应用，即 [01 §3](./01-getting-started.md#3-三种接入方式) 的方式 ①（继承 `KeydroidxBaseActivity`）与方式 ②（Fragment 承载）。
>
> **不适用**：只引 `keydroidx-common` 的桌面本体（继承 `common.ui.KeydroidxBaseActivity`）、以及走方式 ③「仅独立集成按键解析」的应用——它们拿不到/不需要 Provider 绑定，不受下面「继承类」条款约束。
>
> 本文只列**硬性红线与唯一事实源**，教程与 API 细节一律指向对应 guide 章节，不在此重复。

---

## 一、硬性红线

| # | 红线 | 违反后果 | 详见 |
|:--|:--|:--|:--|
| 1 | 业务 Activity 继承 `io.github.cctyl.nokia.keycore.ui.KeydroidxBaseActivity`，**严禁直接继承 Android 原生 `Activity`/`AppCompatActivity`** | 丢掉骨架、按键分发、主题与字体热同步 | [04](./04-base-activity.md) |
| 2 | 内容页 Fragment 继承 `KeydroidxListPageFragment` / `KeydroidxScrollPageFragment` / `KeydroidxPageFragment`（均在 `common.ui.page`），**严禁直接继承原生 `Fragment`** | 丢掉字体整树渲染、页面契约与焦点管理 | [05](./05-page-framework.md) |
| 3 | 子类**严禁再调 `setContentView()`**，只覆写 `getContentLayoutRes()` | 顶掉整个复古骨架 | [04](./04-base-activity.md) |
| 4 | **禁止写死 keyCode**（`keyCode == 23` 之类一律不许），只处理 `KeydroidxKeyAction` 语义动作 | 机型碎片化下按键全乱 | [03](./03-key-model.md) |
| 5 | 骨架之外自行消费按键处（自定义 Dialog 的 `setOnKeyListener` 等）必须**走 `KeydroidxUi.getKeyResolver(context).resolveAction(event)`**，且 **DOWN / UP 成对消费** | 只吞 DOWN 会触发系统合成 `performClick()`，一次按键触发两次 | [04](./04-base-activity.md)、[NOKIA_DEVELOPMENT_RULES](../NOKIA_DEVELOPMENT_RULES.md) |
| 6 | **首尾循环导航**：列表首项按 UP 到末尾、末项按 DOWN 回首项；`KeydroidxListPageFragment.onDirection` 为 `final`，禁止绕过 | 焦点卡在边界 | [05](./05-page-framework.md)、[06](./06-list-focus.md) |
| 7 | **防出界滚动强制**用 `KeydroidxListFocusHelper.smoothScrollToVisible(scroll, target)`，**严禁**用 `child.getTop()` 算滚动位置 | 嵌套布局下焦点滚出可视区 | [06](./06-list-focus.md) |
| 8 | **首键防吞**：列表条目根 View 声明 `focusable="true"` + `focusableInTouchMode="true"`；外层 `ScrollView` 声明 `focusable="false"` + `focusableInTouchMode="false"`；自定义 Dialog 在 `show()` 里补一次 `KeydroidxDialogFocus.forceNonTouchMode` | 进页面后第一次按方向键/确定键无效 | [04](./04-base-activity.md)、[06](./06-list-focus.md)、[07](./07-dialogs.md) |
| 9 | **对话框**一律用 SDK 组件：选项菜单 `KeydroidxOptionsDialog`、确认框 `KeydroidxConfirmDialog`、文本输入 `KeydroidxTextInputFragment`。**严禁原生 `AlertDialog` / `PopupWindow`** | 触屏风格残留、首键被吞、窗口 Token 泄漏 | [07](./07-dialogs.md)、[08](./08-text-input.md) |
| 10 | **软键与标题走声明式 getter**（`getPageTitle` / `getSoftLeftText` / `getSoftCenterText` / `getSoftRightText`），**严禁** `findViewById` 改顶栏与软键栏 | 页面切换后软键栏状态错乱 | [05](./05-page-framework.md) |
| 11 | **色值全部来自 `KeydroidxTheme`**（`ThemeDef` / `getCurrentTheme()`），**严禁硬编码颜色** | 桌面换肤后本 App 不跟随 | [09](./09-theme-font-icons.md) |
| 12 | **字号全部引用 `@dimen/nokia_font_*` 6 级 Token**，严禁裸写数字、严禁 ≤13sp 加粗 | 点阵字体糊掉 | [](../spec/typography-and-font-spec.md) |
| 13 | **图标统一 `KeydroidxIcons`**，`KeydroidxFontManager` 自动整树生效；**严禁新增 PNG / XML 图标** | 多分辨率下留白失真 | [09](./09-theme-font-icons.md) |
| 14 | **根布局响应式原生 DP**（`match_parent` + `weight`），**禁止**运行时 `setScaleX/Y`、**禁止**根宽写死 240dp | 大屏留白、小屏溢出 | [](../spec/responsive-layout-spec.md) |

---

## 二、软键栏规范（唯一事实源）

1. **禁止高亮**：底部左/中/右软键只是物理键的静态标签，**绝对禁止**加选中背景，**绝对禁止**用左右方向键在软键间切换高亮。
2. **空软键保留占位**：三栏等宽（`0dp + weight=1`），getter 返回 null/空串时基类只把文字置空、View 仍在（视觉等价 `INVISIBLE`，中栏不偏移）。**严禁对软键 View 用 `View.GONE` 或移除**，否则三栏塌陷、标题偏向一侧。
3. **长文字自适应**：中键文字过长时基类 `fitCenterTextToWidth` 按实际测量宽度逐步缩号（以 `@dimen/nokia_font_small_title` 为基准向下，最低约 6sp）；顶栏标题与左右软键单行、超长 `ellipsize="end"` 截断。

---

## 三、图标尺寸档位

| 场景 | 尺寸 |
|:--|:--|
| 桌面 / 小组件单行图标 | `20dp` |
| 列表行 / 菜单项图标 | `22dp` |
| 弹窗选项图标 | `18dp` |
| 快捷开关图标 | `18dp` |

> `KeydroidxIcons.get(context, code, color, sizeDp)` 第 3 参是**颜色**、第 4 参是 **dp**；`createDrawable(context, code, sizePx, color)` 第 3 参是 **px**，仅特殊场景使用。

---

## 四、关于 Toast

红线只针对**对话框**（上表 #9），**不禁止系统 Toast**：SDK 目前**没有**复古 Toast 替代组件，`KeydroidxFeedbackFragment` / `KeydroidxAboutFragment` / `KeydroidxMoreAppsFragment` / `KeydroidxTextInputFragment` 自带的瞬时提示本身就用系统 Toast。等 SDK 提供复古 Toast 后再收紧本条。

---

## 五、自查清单

- [ ] 所有 Activity 继承自 `KeydroidxBaseActivity`；所有内容 Fragment 继承自三大模板基类之一
- [ ] 软键与标题全部走声明式 getter，无 `findViewById` 操作软键栏的代码
- [ ] 软键栏无背景高亮、无左右方向键切换高亮的代码；空软键未被 `GONE`
- [ ] 无硬编码颜色，色值与高亮背景均来自 `KeydroidxTheme`
- [ ] 无裸写字号，全部引用 `@dimen/nokia_font_*`；≤13sp 无 `textStyle="bold"`
- [ ] 无新增 PNG / XML 图标，全部走 `KeydroidxIcons`
- [ ] 业务代码无 keyCode 字面量；骨架之外自行消费按键处走 `KeyResolver` 且 DOWN / UP 成对
- [ ] 列表首尾循环生效；滚动用 `smoothScrollToVisible`，无 `getTop()` 计算
- [ ] 条目声明 `focusableInTouchMode="true"`，外层 `ScrollView` 禁焦，进页面第 1 次按方向键立即响应
- [ ] 无原生 `AlertDialog` / `PopupWindow`；选项/确认/输入分别用 SDK 三个组件
- [ ] 根布局响应式原生 DP，无 `setScaleX/Y`、无写死 240dp

---

## 相关文档

- 接入方式总览（含不适用的方式 ③）→ [01 快速接入](./01-getting-started.md)
- 全生态硬性经验沉淀 → [NOKIA_DEVELOPMENT_RULES.md](../NOKIA_DEVELOPMENT_RULES.md)
