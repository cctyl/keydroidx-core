# 01 · 快速接入

## 1. 引入依赖

按角色选择模块：

- **独立 App（调用桌面 Provider）** → 引 `keydroidx-key-core`（传递依赖 `keydroidx-common`）
- **桌面本体或只要通用能力** → 只引 `keydroidx-common`

`settings.gradle` 中通过 `includeBuild` + **显式** `dependencySubstitution` 将 maven 坐标替换为本地源码（生态内项目通用做法）：

```groovy
includeBuild('../keydroidx-core') {
    dependencySubstitution {
        substitute(module('io.github.cctyl.nokia:keydroidx-key-core'))
            .using(project(':keydroidx-key-core'))
        substitute(module('io.github.cctyl.nokia:keydroidx-common'))
            .using(project(':keydroidx-common'))
    }
}
```

> ⚠️ **只写 `includeBuild` 不写 `substitute` 会静默失败**：Gradle 自动替换依赖模块声明 group，而 core 仓库未声明 project group，替换不生效也不报错，构建会悄悄回退到已缓存的远端产物。详见 [architecture/module-layering](../architecture/module-layering.md) §七。

业务模块 `build.gradle`：

```groovy
dependencies {
    implementation 'io.github.cctyl.nokia:keydroidx-key-core:1.0.0'
    // 或只要通用能力：implementation 'io.github.cctyl.nokia:keydroidx-common:1.0.0'
}
```

## 2. Android 11+ 包可见性

`targetSdkVersion >= 30` 时需要声明 `<queries>`，否则跨进程查询桌面 Provider 会静默失败（`SecurityException` 被捕获后直接降级到本地配置）。

**引 `keydroidx-key-core` 时无需手动声明**——其 manifest 已内置以下内容并自动合并进你的应用：

```xml
<queries>
    <provider android:authorities="io.github.cctyl.nokia.keyprovider" />
    <provider android:authorities="io.github.cctyl.nokia.debug.keyprovider" />
</queries>
```

**只引 `keydroidx-common` 且需要自行查询桌面 Provider 时**，才需要把上面的片段写进你的 manifest。

> 注意：`<queries>` 若已有其他内容，用 `tools:node="merge"` 合并，不要写两份 `<queries>` 标签。

## 3. 三种接入方式

| 方式 | 适用场景 | 文档 |
|------|----------|------|
| 继承 `KeydroidxBaseActivity` | 独立页面 Activity，想要统一顶栏/软键栏 | [04-base-activity](./04-base-activity.md) |
| 页面用 Fragment 承载 | 宿主 Activity + 多 Tab 保活切换 | [05-page-framework](./05-page-framework.md) |
| 仅独立集成按键解析 | 不需要复古骨架，只要按键语义映射 | [02-client](./02-client.md)、[03-key-model](./03-key-model.md) |

> 方式 ①② 受 [00 接入红线](./00-development-redlines.md) 全部条款约束；方式 ③ 不受「继承类」条款约束，但也拿不到统一骨架与主题/字体热同步，需自行处理。

### 3.1 最小示例：继承 KeydroidxBaseActivity

```kotlin
class MyActivity : KeydroidxBaseActivity() {

    // 内容区布局；基类会 inflate 进统一骨架的 contentContainer。
    // 切勿在子类再调 setContentView()！
    override fun getContentLayoutRes(): Int = R.layout.activity_my

    override fun onInitViews() {
        setPageTitle("我的应用")
        setTitleIcon(KeydroidxIcons.ICON_HOME)
        setStatusBarVisible(true)
        registerBatteryReceiver()          // 电量实时刷新
        setSoftKeys("选项", "确定", "返回")
    }

    override fun onAction(action: Int): Boolean {
        return when (action) {
            KeydroidxKeyAction.SOFT_LEFT -> { showMenu(); true }
            KeydroidxKeyAction.SELECT     -> { doSelect(); true }
            else -> super.onAction(action)  // 默认右软键 onBack()：弹返回栈或 finish
        }
    }
}
```

> **import 注意**：`KeydroidxKeyAction` / `KeydroidxIcons` 请 import `io.github.cctyl.nokia.common.model.KeydroidxKeyAction` 与 `io.github.cctyl.nokia.common.ui.KeydroidxIcons`。`keycore.model.KeydroidxKeyAction` 等同名桥接类已于 2026-09-06 删除，不要再 import（详见 [architecture/module-layering](../architecture/module-layering.md) §三）。

### 3.2 最小示例：Fragment 页面

```kotlin
class SettingPage : KeydroidxListPageFragment() {
    override fun getLayoutRes() = R.layout.page_setting

    override fun getPageTitle() = "设置"
    override fun getSoftLeftText() = "选项"
    override fun getSoftRightText() = "返回"

    override fun onPageCreated(view: View, savedInstanceState: Bundle?) {
        itemViews = arrayOf(item1, item2, item3)   // 填充列表项
        listScroll = view.findViewById(R.id.scroll)
        setFocusIndex(0)
    }

    override fun onSelect(): Boolean { /* 处理确定 */ return true }
}
```

宿主 Activity 必须继承 `KeydroidxBaseActivity`（它实现了 `KeydroidxPageHost` 并自动把按键分发给当前可见的 `KeydroidxPage`）。

## 4. 构建注意事项

- **JDK**：模块要求 JDK 17 编译环境；
- **minSdk / 兼容**：全面兼容 Android 4.4 (API 19) ~ Android 14+ (API 34)。注意 `keydroidx-common` 以 `api` 暴露 [XXPermissions](./13-permissions.md)，接入方会传递性引入该依赖；
- **assets 字体**：SDK 自带 `fonts/MaterialIcons-Regular.ttf`；中文字体 `ArkPixel-12px.ttf` / `FusionPixel-12px.ttf` 缺失时自动回退系统字体（见 [09-theme-font-icons](./09-theme-font-icons.md)）；
- **vectorDrawables**：Android 4.5 以下设备膨胀矢量资源易抛 `InflateException`，业务侧建议开启 `vectorDrawables.useSupportLibrary`。

## 5. 下一步

- 了解配置如何从桌面同步过来 → [02-client](./02-client.md)
- 了解按键如何变成语义动作 → [03-key-model](./03-key-model.md)
