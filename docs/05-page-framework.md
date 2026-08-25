# 05 · 页面框架（Fragment 体系）

包：`io.github.cctyl.nokia.keycore.ui.page`

三个接口 + 三个抽象基类，构成「宿主 Activity ↔ 页面」的契约式框架。核心收益：**页面用声明式 getter 描述标题与软键，按键事件被宿主精准路由到当前前台页面，页面自身不碰骨架控件。**

## 接口层

### NokiaFocusHost — 按键事件接收

所有可接收物理键的页面的最小契约：

```java
boolean onDirection(int direction)  // 方向键；direction ∈ {UP, DOWN, LEFT, RIGHT}
boolean onSelect()                  // 确定键
boolean onSoftLeft()                // 左软键
boolean onSoftRight()               // 右软键
boolean onBack()                    // 返回键
```
返回 `true` 表示消费该事件。由宿主（`NokiaBaseActivity.onAction`）调用。

### NokiaPage — 标题与软键声明（extends NokiaFocusHost）

```java
CharSequence getPageTitle()       // 顶栏标题；null/空串保持默认
CharSequence getSoftLeftText()    // 左软键文字
CharSequence getSoftCenterText()  // 中软键文字
CharSequence getSoftRightText()   // 右软键文字
```
getter 允许动态返回（随焦点/播放态变化），内部状态变化后调 `notifyHostRefresh()` 让宿主重新拉取。

### NokiaPageHost — 宿主侧契约

由 `NokiaBaseActivity` 实现，方法说明见 [04-base-activity](./04-base-activity.md)。

## 基类层

### NokiaPageFragment — 普通功能页基类

模板方法模式。生命周期固化：

1. `onCreateView` → 自动 inflate `getLayoutRes()`；
2. `onViewCreated` → 清除根布局背景 → `notifyHostRefresh()` → 回调 `onPageCreated` → 整树应用点阵字体。

```java
@LayoutRes
protected abstract int getLayoutRes();          // 子类声明页面布局

protected boolean isTopAlign()                  // 贴顶(默认 true)/居中，预留钩子

protected void onPageCreated(@NonNull View view, @Nullable Bundle savedInstanceState)
// 子类初始化钩子：findViewById、监听、数据加载都写这里

public void notifyHostRefresh()
// 通知宿主重新拉取本页声明的标题与软键栏（状态变化后必须手动调）

public void smoothScrollToVisible(@Nullable ScrollView scroll, @Nullable View target)
// 生命周期安全的滚动跟随（isAdded 守护），委托 NokiaListFocusHelper 静态实现
```

默认契约实现（子类按需重写）：

| 方法 | 默认行为 |
|------|----------|
| `getPageTitle/getSoftLeftText/getSoftCenterText` | 返回 null |
| `getSoftRightText()` | `"返回"` |
| `onDirection/onSelect/onSoftLeft` | 不消费（false） |
| `onSoftRight()` | 委托 `onBack()` |
| `onBack()` | 有 NokiaPageHost 则 `exitCurrent()`，否则 `activity.onBackPressed()` |

### NokiaListPageFragment — 单列列表页基类

收编列表页三件套样板代码：焦点高亮、强制循环导航、自动滚动。

#### 子类需填充的字段

```java
protected View[] itemViews;     // 列表项数组（在 onPageCreated 填充）
protected ScrollView listScroll; // 列表滚动容器（同上）
protected int focusIndex;        // 当前焦点索引，初始 -1
```
动态列表可用 `setItemList(List<View>)` 快捷赋值；`getItemCount()` 默认取 `itemViews.length`，数据驱动时可重写。

#### 导航（final，禁止绕过循环导航规范）

```java
@Override public final boolean onDirection(int direction)
```
- `focusIndex < 0` 时首个方向键自动落焦第 0 项；
- UP/DOWN **首尾循环**（首项按上跳末尾，末项按下回开头）；
- LEFT/RIGHT 转发 `onLeftRight(int)`；
- 全部返回 true（消费，不穿透）。

```java
protected boolean isDirectionEnabled()   // 返回 false 时方向键仍被消费但焦点不动（弹窗态用）
protected boolean onLeftRight(int direction) // 默认消费无效果；子类覆写实现左右切 Tab
```

#### 焦点管理

```java
protected void setFocusIndex(int index)
// 清旧高亮 → 更新 focusIndex → 应用主题色圆角高亮 + requestFocus → 自动平滑滚到可视区

protected void clearFocusBackground()   // 清除高亮
protected void applyFocusBackground()   // 绘制 NokiaTheme.createSelectionDrawable 高亮并持焦
protected void scrollToVisible(int index) // 确保索引行可见
```

#### 布局辅助

```java
protected void constrainScrollHeight(@NonNull View root, @NonNull ScrollView scroll)
// post 后按父面板实际高度 ÷ scaleX 反推可视高度，把 ScrollView 高度钉到底边，
// 解决「wrap_content 估算偏高导致最后一行被底栏裁切」问题
```

`onDestroyView` 自动置空全部字段引用，防泄漏。

### NokiaScrollPageFragment — 长内容滚动页基类

适用文本说明页、长表单等含 ScrollView 的非列表页。

```java
protected ScrollView pageScrollView;
// onPageCreated 时自动 findScrollView(root) 递归找到第一个 ScrollView 并赋值；
// 子类初始化写在 onScrollPageCreated(view, savedInstanceState)，不要重写 onPageCreated

protected int getScrollStepPx()  // 单次滚动步长，默认可视高度 45%（兜底 100dp/160px）
public boolean scrollUp() / scrollDown()        // 平滑滚动一步，无容器时返回 false
public boolean canScrollUp() / canScrollDown()  // 边界探测

@Override public boolean onDirection(int action)
// UP→scrollUp，DOWN→scrollDown；左右不处理
```

## 选型速查

| 页面形态 | 基类 |
|----------|------|
| 单列菜单 / 列表 | `NokiaListPageFragment` |
| 文章 / 说明 / 长表单 | `NokiaScrollPageFragment` |
| 自由布局（播放器、宫格） | `NokiaPageFragment` 自己管焦点 |
| 不用 Fragment 的 Activity 页 | 直接继承 `NokiaBaseActivity` + 组合 `NokiaListFocusHelper` |

## 相关文档

- Activity 内直接管理列表焦点 → [06-list-focus](./06-list-focus.md)
- 弹窗期间禁导航的做法见各基类 `isDirectionEnabled` 与 [07-dialogs](./07-dialogs.md)
