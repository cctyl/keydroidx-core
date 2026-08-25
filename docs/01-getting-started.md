# 01 · 快速接入

## 1. 引入依赖

`settings.gradle` 中通过 `includeBuild` + `dependencySubstitution` 将 maven 坐标替换为本地源码（生态内项目通用做法）：

```groovy
includeBuild('../keydroidx-core') {
    dependencySubstitution {
        substitute(module('io.github.cctyl.nokia:nokia-key-core'))
            .using(project(':nokia-key-core'))
    }
}
```

业务模块 `build.gradle`：

```groovy
dependencies {
    implementation 'io.github.cctyl.nokia:nokia-key-core'
}
```

## 2. Android 11+ 包可见性

`targetSdkVersion >= 30` 时必须在 `AndroidManifest.xml` 声明，否则跨进程查询桌面 Provider 会静默失败（`SecurityException` 被捕获后直接降级到本地配置）：

```xml
<queries>
    <provider android:authorities="io.github.cctyl.nokia.keyprovider" />
    <provider android:authorities="io.github.cctyl.nokia.debug.keyprovider" />
</queries>
```

> 注意：`<queries>` 若已有其他内容，用 `tools:node="merge"` 合并，不要写两份 `<queries>` 标签。

## 3. 三种接入方式

| 方式 | 适用场景 | 文档 |
|------|----------|------|
| 继承 `NokiaBaseActivity` | 独立页面 Activity，想要统一顶栏/软键栏 | [04-base-activity](./04-base-activity.md) |
| 页面用 Fragment 承载 | 宿主 Activity + 多 Tab 保活切换 | [05-page-framework](./05-page-framework.md) |
| 仅独立集成按键解析 | 不需要复古骨架，只要按键语义映射 | [02-client](./02-client.md)、[03-key-model](./03-key-model.md) |

### 3.1 最小示例：继承 NokiaBaseActivity

```kotlin
class MyActivity : NokiaBaseActivity() {

    // 内容区布局；基类会 inflate 进统一骨架的 contentContainer。
    // 切勿在子类再调 setContentView()！
    override fun getContentLayoutRes(): Int = R.layout.activity_my

    override fun onInitViews() {
        setPageTitle("我的应用")
        setTitleIcon(NokiaIcons.ICON_HOME)
        setStatusBarVisible(true)
        registerBatteryReceiver()          // 电量实时刷新
        setSoftKeys("选项", "确定", "返回")
    }

    override fun onAction(action: Int): Boolean {
        return when (action) {
            NokiaKeyAction.SOFT_LEFT -> { showMenu(); true }
            NokiaKeyAction.SELECT     -> { doSelect(); true }
            else -> super.onAction(action)  // 默认 SOFT_RIGHT=finish()
        }
    }
}
```

### 3.2 最小示例：Fragment 页面

```kotlin
class SettingPage : NokiaListPageFragment() {
    override fun getLayoutRes() = R.layout.page_setting

    override fun getPageTitle() = "设置"
    override fun getSoftLeftText() = "选项"
    override fun getSoftRightText() = "返回"

    override fun onPageCreated(view: View, savedInstanceState: Bundle?) {
        itemViews = arrayOf(item1, item2, item3)   // 填充列表项
        listScroll = view.findViewById(R.id.scroll)
        setFocusIndex(0)
    }

    override fun onSelect() { /* 处理确定 */ true }
}
```

宿主 Activity 必须继承 `NokiaBaseActivity`（它实现了 `NokiaPageHost` 并自动把按键分发给当前可见的 `NokiaPage`）。

## 4. 构建注意事项

- **JDK**：模块要求 JDK 17 编译环境；
- **minSdk / 兼容**：全面兼容 Android 4.4 (API 19) ~ Android 14+ (API 34)，零第三方依赖；
- **assets 字体**：SDK 自带 `fonts/MaterialIcons-Regular.ttf`；中文字体 `ArkPixel-12px.ttf` / `FusionPixel-12px.ttf` 缺失时自动回退系统字体（见 [08-theme-font-icons](./08-theme-font-icons.md)）；
- **vectorDrawables**：Android 4.5 以下设备膨胀矢量资源易抛 `InflateException`，业务侧建议开启 `vectorDrawables.useSupportLibrary`。

## 5. 下一步

- 了解配置如何从桌面同步过来 → [02-client](./02-client.md)
- 了解按键如何变成语义动作 → [03-key-model](./03-key-model.md)
