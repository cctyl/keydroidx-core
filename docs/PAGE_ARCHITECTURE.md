# KeydroidX 页面体系架构设计与使用说明

本文档详细说明 `keydroidx-core` 中新增的 **页面体系与列表焦点管理架构**（`io.github.cctyl.nokia.keycore.ui.page`）。

该架构源自 `KeydroidX Launcher` 的实战打磨与沉淀，旨在**彻底消除按键机列表页的样板代码、防止光标滚出屏幕、实现软键栏/标题栏的声明式联动，并无缝支持 Fragment 与 Activity 两种形态**。

---

## 一、架构全景与核心设计

```
                    ┌─────────────────────────┐
                    │      NokiaFocusHost     │ (按键事件分发契约: UP/DOWN/LEFT/RIGHT/SELECT/SOFT...)
                    └────────────┬────────────┘
                                 │ extends
                    ┌────────────┴────────────┐
                    │        NokiaPage        │ (声明式页面契约: getPageTitle / getSoftKeys...)
                    └────────────┬────────────┘
                                 │ implements
                    ┌────────────┴────────────┐
                    │     NokiaPageFragment   │ (Fragment 根基类: 字体/主题/可视区滚动/契约同步)
                    └──────┬────────────┬─────┘
                           │            │
             ┌─────────────┴──┐      ┌──┴────────────────────────┐
             │ NokiaListPage  │      │ NokiaScrollPageFragment   │
             │   Fragment     │      │ (长文/表单自动平滑步进滚动)│
             └────────────────┘      └───────────────────────────┘
                    │
                    ▼ uses
       ┌────────────────────────┐
       │   NokiaListFocusHelper │ ◄── [独立复用] Activity / ViewPager / 自定义 View 亦可直接使用
       │ (循环导航/高亮/自动可视滚动) │
       └────────────────────────┘
```

---

## 二、核心组件与职责

### 1. 契约层
- **`NokiaFocusHost`**：按键导航与软键事件的抽象契约。包含 `onDirection(int action)`、`onSelect()`、`onSoftLeft()`、`onSoftRight()`、`onBack()`。
- **`NokiaPage`**：声明式页面描述。由页面声明 `getPageTitle()`、`getSoftLeftText()`、`getSoftCenterText()`、`getSoftRightText()`，无需子页面手动获取和修改宿主 View。
- **`NokiaPageHost`**：由宿主 Activity 实现（`NokiaBaseActivity` 已默认实现）。提供 `refreshPageBar()`、`setPageTitle()`、`setSoftKeys()`、`exitCurrent()`。

### 2. 列表与焦点调度核心
- **`NokiaListFocusHelper`**：
  - **核心痛点解决**：在多层嵌套布局（如 `ScrollView -> LinearLayout -> Items`）中，直接使用 `child.getTop()` 只是相对于直接父容器的偏移。`smoothScrollToVisible` 算法通过递归累加祖先节点的绝对 `top`，精确计算在 `ScrollView` 中的绝对垂直位置并执行滚动，**彻底解决光标滚动到屏幕外的问题**。
  - **开箱即用**：自动管理焦点索引（`focusIndex`）、循环首尾导航、主题高亮 Drawable 创建与清除、条目聚焦联动。
  - **无侵入性**：既能在 `NokiaListPageFragment` 内部使用，也能在任何普通 Activity 中作为成员变量直接管理列表焦点。

### 3. 页面基类
- **`NokiaPageFragment`**：
  - 自动应用全局生态点阵字体（`NokiaFontManager`）；
  - 自动绑定当前主题并同步顶栏/软键栏；
  - 页面切入时自动触发宿主 `refreshPageBar()`；
  - 守卫生命周期（`isAdded()` / Context 守护）。
- **`NokiaListPageFragment`**：
  - 单列列表页面的黄金基类。
  - 子类只需要在 `onPageCreated` 中提供 `itemViews` 列表和 `listScroll`（ScrollView）。
  - **上下键循环导航、条目高亮、滚动跟随全部由父类自动搞定**，子类只需覆写 `onItemClicked(int index, View view)` 处理业务点击。
- **`NokiaScrollPageFragment`**：
  - 适合长文本、说明页、设置表单等页面。
  - 自动定位布局中的 `ScrollView`，上下键触发平滑步进滚动。

---

## 三、快速上手示例

### 示例 1：使用 `NokiaListPageFragment` 快速编写列表页面

```java
public class MyPlaylistFragment extends NokiaListPageFragment {

    @Override
    protected int getLayoutRes() {
        return R.layout.fragment_my_playlist;
    }

    @Override
    public String getPageTitle() {
        return "我的歌单";
    }

    @Override
    public String getSoftLeftText() {
        return "选项";
    }

    @Override
    public String getSoftCenterText() {
        return "播放";
    }

    @Override
    public String getSoftRightText() {
        return "返回";
    }

    @Override
    protected void onPageCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        // 1. 获取 ScrollView 与各条目 View
        this.listScroll = view.findViewById(R.id.scroll_list);
        this.itemViews = new View[] {
            view.findViewById(R.id.item_fav),
            view.findViewById(R.id.item_history),
            view.findViewById(R.id.item_local)
        };

        // 2. 设置初始焦点为第 1 项（会自动高亮并保证可视）
        setFocusIndex(0);
    }

    @Override
    protected void onItemClicked(int index, @NonNull View itemView) {
        // 处理确定键（SELECT）点击
        switch (index) {
            case 0: openFavorites(); break;
            case 1: openHistory(); break;
            case 2: openLocal(); break;
        }
    }
}
```

### 示例 2：在传统 Activity 中直接使用 `NokiaListFocusHelper`

如果现有页面是 Activity 架构，无需重构成 Fragment，直接使用 `NokiaListFocusHelper`：

```kotlin
class PlaylistDetailActivity : NokiaBaseActivity() {

    private lateinit var focusHelper: NokiaListFocusHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_playlist_detail)

        val scroll = findViewById<ScrollView>(R.id.scroll_view)
        focusHelper = NokiaListFocusHelper(this, scroll)
    }

    private fun renderSongList(songs: List<Song>) {
        val songViews = mutableListOf<View>()
        // ... inflate 并添加 views ...
        
        // 绑定给 focusHelper，立即拥有循环导航、高亮、自动滚动能力
        focusHelper.setItems(songViews)
        focusHelper.setFocusIndex(0, true)
    }

    override fun onAction(action: Int): Boolean {
        // 上下键直接托管给 focusHelper
        if (action == NokiaKeyAction.UP || action == NokiaKeyAction.DOWN) {
            return focusHelper.onDirection(action)
        }
        if (action == NokiaKeyAction.SELECT) {
            val currentIdx = focusHelper.focusIndex
            playSong(currentIdx)
            return true
        }
        return super.onAction(action)
    }
}
```

---

## 四、开发注意事项（避坑指南）

1. **XML 声明 `android:focusable="true"`**：
   - 所有参与焦点的条目根布局（或子项）在 XML 中务必声明 `android:focusable="true"`，保证 Android View 树的焦点系统正常工作。
2. **异步刷新后重新绑定**：
   - 当网络请求完成并动态向容器 `addView` 之后，记得重新调用 `focusHelper.setItems(...)` 或更新 `itemViews`，并重设 `focusIndex`。
3. **软键与标题栏热更新**：
   - 当 Fragment 内部状态改变（例如选中的条目改变、从播放态变为暂停态）需要修改软键文字时，直接调用 `host.refreshPageBar()`，宿主 Activity 会立即读取最新的 `getSoftCenterText()` 等并刷新底栏。
