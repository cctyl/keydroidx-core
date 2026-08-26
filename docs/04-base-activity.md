# 04 · 页面骨架 Activity（NokiaBaseActivity）

`io.github.cctyl.nokia.keycore.ui.NokiaBaseActivity`

所有生态独立应用页面的统一宿主基类。继承它即可获得：

- **统一复古骨架**：顶栏（标题图标 + 标题 + 信号/电量状态栏）+ 内容区 + 三段式软键栏，布局来自 `activity_nokia_base.xml`，按屏幕高度权重分配（顶栏 7%、底栏约 8%），各页面共用、随主题热切换；
- **全屏沉浸**：onCreate 中自动隐藏系统状态栏（R+ 用 InsetsController，低版本用 FLAG_FULLSCREEN + legacy systemUiVisibility 双保险）；
- **按键分发**：`dispatchKeyEvent` 统一解析为语义动作并去抖后调 `onAction(int)`；
- **主题与字体自动应用**：注册 `NokiaClient` 监听，桌面换肤/换字体即时生效；
- **页面代理**：实现 `NokiaPageHost`，自动把标题/软键装配与按键转发给当前前台 `NokiaPage`。

## 子类必须实现的两个抽象方法

```java
@LayoutRes
protected abstract int getContentLayoutRes();
```
返回**内容区**布局资源。基类在 `setContentView(R.layout.activity_nokia_base)` 之后把它 inflate 进骨架的 `contentContainer`。
> ⚠️ 子类**严禁再调用 `setContentView()`**——会顶掉整个骨架。

```java
protected abstract void onInitViews();
```
视图初始化钩子，等价于其他基类的 `onCreate` 尾部；在此做 findViewById、装配标题软键、加载数据。

## 顶栏装配

```java
public void setPageTitle(CharSequence title)   // 设置顶栏标题（别名 setTitleText）
public void setTitleIcon(CharSequence iconCode)
// 设置标题左侧图标（NokiaIcons 常量）；null 或空串隐藏图标位
```

### 状态栏（顶栏右侧：信号 + 电量）

```java
public void setStatusBarVisible(boolean visible)   // 显示/隐藏整个状态区
public void setSignalIcon(CharSequence iconCode)   // 信号图标（NokiaIcons 常量）
public void setBatteryPercent(CharSequence text)   // 直接写电量百分比文本
```

```java
protected void registerBatteryReceiver()     // 注册 ACTION_BATTERY_CHANGED 广播
protected void unregisterBatteryReceiver()   // 反注册（onDestroy 已自动调用）
```
注册后电池图标（`NokiaBatteryDrawable` 矢量绘制）与百分比随系统电量实时刷新，并立即用 sticky broadcast 刷一次。
> ⚠️ **不要硬编码电量**（如 `"70%"`）。需要显示真实电量就调 `registerBatteryReceiver()`。

## 软键栏装配

```java
public void setSoftKeys(CharSequence left, CharSequence center, CharSequence right)
public void setSoftLeft(CharSequence text) / setSoftCenter(...) / setSoftRight(...)
```
三段式软键文字；传 null 按空串处理。触屏兜底：三个软键 TextView 自带点击监听，点击即回调对应 `onAction`。

## 按键分发

```java
@Override
public boolean dispatchKeyEvent(KeyEvent event)   // final 语义上勿重写
```
流程：60ms 内同 keyCode 去抖 → `NokiaClient.getKeyBinding().resolveAction(keyCode)` → 解析成功则调 `onAction(action)`，返回 true 则消费。ACTION_UP 阶段对已消费的键做吞除，防止重复触发。

```java
@CallSuper
protected boolean onAction(int action)
```
默认实现：先找当前前台 `NokiaPage`（Fragment），把动作翻译成 `onDirection/onSelect/onSoftLeft/onSoftRight` 分发；页面未消费且动作为 `SOFT_RIGHT` 时执行 `finish()`。
子类重写时**务必对未处理的分支调 `super.onAction(action)`**，否则右软键返回失效。

```java
@Nullable
protected NokiaPage getCurrentPage()
```
返回当前前台页面：自身实现了 `NokiaPage` 则返回自身；否则遍历 SupportFragmentManager 取第一个可见的 `NokiaPage` Fragment。多 Tab 保活架构下由宿主重写以精准指定当前页。

## NokiaPageHost 实现（供 Fragment 调用）

```java
public void refreshPageBar()   // 重新拉取当前页面的 getPageTitle/getSoft*Text 并装配
public void setPageTitle(CharSequence title)
public void setSoftKeys(CharSequence left, CharSequence center, CharSequence right)
public void exitCurrent()      // Fragment 回退栈非空则 popBackStack，否则 onBackPressed
```

## 返回键

```java
@Override
public void onBackPressed()
```
先问当前 `NokiaPage.onBack()`，返回 true 则消费；否则走系统默认。

## 滚动跟随工具

```java
public void smoothScrollToVisible(@Nullable ScrollView scroll, @Nullable View target)
```
祖先节点坐标累加算法计算 target 相对 ScrollView 的真实 Y，超出可视区时平滑滚动到刚好可见。多层嵌套布局中**不要直接用 `getTop()`**，一律走此方法（Fragment 侧的同名方法最终也委托到这里 / `NokiaListFocusHelper` 的静态版本）。

## 焦点生命周期与防吞键机制（自动，无需子类干预）

```java
@Override public void onWindowFocusChanged(boolean hasFocus)
protected void ensureActiveFocus()
```
- **机制**：Android 在触摸模式（Touch Mode）下，若窗口处于无焦点状态（`findFocus() == null`），首个方向键（UP/DOWN/LEFT/RIGHT/SELECT）会被系统用于退出触摸模式并寻焦而吞掉。
- **基类防护**：`NokiaBaseActivity` 在 `onWindowFocusChanged(true)` 和 `onResume()` 时自动执行 `ensureActiveFocus()`，确保 `midPanel`（内容容器）或可用视图始终在 Touch Mode 下持有焦点，彻底杜绝首键被吞。
- 详情与规范请参阅根目录 `NOKIA_DEVELOPMENT_RULES.md`。

## 主题与字体热更新（自动，无需子类干预）

```java
@Override public void onThemeChanged(String themeId, NokiaTheme.ThemeDef theme)
// 重绘顶栏/底栏渐变背景、窗口与内容区深色底、各级文字颜色
@Override public void onFontChanged(String fontId, float fontScale)
// NokiaFontManager.applyToViewTree(decorView)，整树应用点阵字体与缩放
```

## 完整示例

见 [01-getting-started §3.1](./01-getting-started.md)。带列表的复杂页面建议改用 Fragment 体系 → [05-page-framework](./05-page-framework.md)。
