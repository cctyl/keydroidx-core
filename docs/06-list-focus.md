# 06 · 列表焦点控制（NokiaListFocusHelper）

`io.github.cctyl.nokia.keycore.ui.page.NokiaListFocusHelper`

**组合式**列表焦点控制器：不依赖任何继承体系，Activity / Fragment / 自定义 View 都能直接持有一个实例，获得标准按键机列表体验：

- 焦点上下循环导航（首项按上到末尾，末项按下到开头）；
- 高亮背景自动设置/清除（生态主题色圆角矩形 + `requestFocus()` 持焦，规避触摸模式吞键）；
- 防出界自动平滑滚动；
- 支持静态数组与动态增删的条目集合。

> 继承 `NokiaListPageFragment` 时无需此类（基类内置了同款逻辑）；两者不要叠加使用。

## 构造

```java
public NokiaListFocusHelper()                                    // 之后 setScrollView()
public NokiaListFocusHelper(@Nullable ScrollView scrollView)     // 构造时绑定滚动容器
public NokiaListFocusHelper(@Nullable Context context, @Nullable ScrollView scrollView)
// context 参数保留兼容，当前未使用
```

## 链式配置

```java
public NokiaListFocusHelper setScrollView(@Nullable ScrollView scrollView)  // 绑定滚动容器
public NokiaListFocusHelper setCyclic(boolean cyclic)   // 是否首尾循环，默认 true
public void setDirectionEnabled(boolean enabled)        // 弹窗等场景临时禁用导航
public boolean isDirectionEnabled()
public void setOnFocusChangedListener(@Nullable OnFocusChangedListener listener)

public interface OnFocusChangedListener {
    void onFocusChanged(int oldIndex, int newIndex, @Nullable View view);
    // 清焦时 newIndex = -1、view = null
}
```

## 条目管理

```java
public void setItems(@Nullable List<View> newItems)   // 整体替换（先清焦点）
public void setItems(@Nullable View[] newItems)       // 数组重载
public void addItem(@NonNull View view)               // 追加单条
public void clearItems()                              // 清空（先清焦点）
public int getItemCount()
public List<View> getItems()                          // 内部 list 的活引用
@Nullable public View getItem(int index)              // 越界返回 null
```

> 条目根 View 必须在 XML 声明 `android:focusable="true"`（触摸模式兜底还需要代码侧 `focusableInTouchMode`，由 `setFocusIndex` 内部 requestFocus 配合完成）。

## 焦点操作

```java
public int getFocusIndex()                 // 初始 -1
@Nullable public View getFocusedView()     // 当前高亮 View，无焦点返回 null

public boolean setFocusIndex(int index)                  // 等价 setFocusIndex(index, true)
public boolean setFocusIndex(int index, boolean autoScroll)
// 越界或空列表返回 false；成功则：清旧高亮 → 应用主题高亮 → requestFocus →
// (autoScroll 时) smoothScrollToVisible → 回调 listener

public void clearFocus()
// 清高亮、focusIndex 置 -1 并回调 onFocusChanged(old, -1, null)
```

## 导航

```java
public boolean onDirection(int direction)
```
- `directionEnabled == false` 时直接消费（返回 true 不动焦点）；
- 空列表返回 false；
- `focusIndex < 0` 时首个方向键落焦第 0 项；
- UP/DOWN 按 `cyclic` 规则移动并返回 true；LEFT/RIGHT 返回 false（不归它管）。

## 防出界滚动（静态工具）

```java
public static void smoothScrollToVisible(@Nullable ScrollView scroll, @Nullable View target)
```
**祖先节点坐标累加算法**：沿 parent 链累加 `top` 直到 ScrollView，得出 target 的真实纵向坐标；顶部不可见滚到 `itemTop`，底部不可见滚到 `itemBottom - svHeight`。多层嵌套安全。
`NokiaPageFragment.smoothScrollToVisible` 与 `NokiaBaseActivity` 同名方法均为其变体——**全生态统一走这条路径，禁止手写 `getTop()` 定位滚动**。

## 典型用法（Activity 页面）

```kotlin
private lateinit var focus: NokiaListFocusHelper

override fun onInitViews() {
    val scroll = findViewById<ScrollView>(R.id.scroll)
    focus = NokiaListFocusHelper(scroll)
    focus.setItems(listOf(row1, row2, row3))
    focus.setFocusIndex(0)
}

override fun onAction(action: Int): Boolean = when (action) {
    NokiaKeyAction.UP, NokiaKeyAction.DOWN -> focus.onDirection(action)
    NokiaKeyAction.SELECT -> onSelect(focus.focusIndex); true
    else -> super.onAction(action)
}
```

动态刷新数据后（如异步加载完歌单）：重新 `setItems(...)` → `setFocusIndex(原索引.coerceAtMost(newCount - 1))`。

## 相关文档

- Fragment 版内置实现 → [05-page-framework](./05-page-framework.md)
- 高亮色来源 → [08-theme-font-icons](./08-theme-font-icons.md)（NokiaTheme.createSelectionDrawable）
