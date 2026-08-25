# KeydroidX Core (原键核心 SDK)

`keydroidx-core` 是 **KeydroidX（原键）按键机生态** 的通用核心 SDK。
为现代智能按键机（Feature Phone / Keyphone）独立应用提供**零配置按键同步、多级平滑降级、极简复古向导 UI** 等一站式解决方案。

---

## 🌟 核心特性

1. **跨应用按键自动共享**：
   - 优先通过 `ContentProvider` 静默读取已安装的 **KeydroidX（原键）桌面** 按键配置，用户在桌面配过一次键，所有生态独立 App 自动生效，无需重复配置。
2. **三级平滑降级机制**：
   - `Tier 1（生态共享）`：读取 KeydroidX 原键桌面 Provider；
   - `Tier 2（应用独立）`：未安装桌面时，读取本 App 独立保存的按键配置；
   - `Tier 3（标准兜底）`：首次打开未配置时，默认提供 Android 标准 DPAD 方向键与通用键映射。
3. **开箱即用复古向导 (`NokiaKeyWizardActivity`)**：
   - 全屏响应式自适应布局（自适应 240x320、320x480 及以上分辨率，无黑边）；
   - `ACTION_DOWN` 单次即时响应录入；
   - 大按钮触屏跳过项，不与物理软键冲突。
4. **极简轻量**：
   - 核心代码 < 500 行，零第三方依赖，全面兼容 Android 4.4 (API 19) ~ Android 14+ (API 34)。

---

## 🚀 快速接入

### 1. 声明 Android 11+ 包可见性

如果应用 `targetSdkVersion >= 30`，需在 `AndroidManifest.xml` 中声明查询权限：

```xml
<queries>
    <provider android:authorities="io.github.cctyl.nokia.keyprovider" />
    <provider android:authorities="io.github.cctyl.nokia.debug.keyprovider" />
</queries>
```

### 2. 方式一：继承 `NokiaBaseActivity`（最简模式）

继承 `NokiaBaseActivity` 即可自动获得生态统一的复古骨架——**顶栏（标题图标 + 标题 + 信号 / 电量状态栏）与底部三段式软键栏均由基类布局 `activity_nokia_base` 统一提供，各页面共用，子类无需自行绘制**，只负责装配文案与处理按键：

```kotlin
class MyActivity : NokiaBaseActivity() {

    // ① 返回内容区布局；基类会把它 inflate 进统一骨架的 contentContainer
    //    切勿在子类里再调 setContentView()，否则会顶掉顶栏/软键栏
    override fun getContentLayoutRes(): Int = R.layout.activity_my

    override fun onInitViews() {
        // ② 装配顶栏标题 / 图标与软键文案
        setPageTitle("我的应用")
        setTitleIcon(NokiaIcons.ICON_HOME)
        setStatusBarVisible(true)
        // ③ 状态栏电量：调 registerBatteryReceiver() 自动刷新图标与百分比，勿硬编码
        registerBatteryReceiver()
        setSoftKeys("选项", "确定", "返回")
    }

    // ④ 所有物理键统一走 onAction(action: Int)，按 NokiaKeyAction 常量分派
    override fun onAction(action: Int): Boolean {
        return when (action) {
            NokiaKeyAction.SOFT_LEFT -> { /* 左软键：选项 */ true }
            NokiaKeyAction.SELECT   -> { /* 确定 */ true }
            NokiaKeyAction.SOFT_RIGHT -> { finish(); true }
            else -> super.onAction(action)   // 默认 SOFT_RIGHT=finish，方向键交基类
        }
    }
}
```

> **要点**：顶栏 / 状态栏 / 软键栏是基类统一绘制并随主题热切换的，各页面**不要**自绘顶栏或硬编码电量百分比；需要实时电量就调 `registerBatteryReceiver()`（基类已实现，`onDestroy` 会自动反注册）。

### 3. 文档目录

详细文档按模块拆分在 [`docs/`](./docs/README.md)，API 细化到每个公开方法，从索引进入：

| 主题 | 文档 |
|------|------|
| 快速接入 | [01-getting-started](./docs/01-getting-started.md) |
| 配置同步与三级降级 | [02-client](./docs/02-client.md) |
| 按键模型 | [03-key-model](./docs/03-key-model.md) |
| 页面骨架 Activity | [04-base-activity](./docs/04-base-activity.md) |
| 页面框架 Fragment | [05-page-framework](./docs/05-page-framework.md) |
| 列表焦点控制 | [06-list-focus](./docs/06-list-focus.md) |
| 标准弹窗 | [07-dialogs](./docs/07-dialogs.md) |
| 主题·字体·图标 | [08-theme-font-icons](./docs/08-theme-font-icons.md) |
| 配键向导 | [09-key-wizard](./docs/09-key-wizard.md) |

---

## 📋 物理按键动作对照表

| 动作枚举 (`NokiaKeyAction`) | 动作说明 | 默认系统兜底键 (`KeyCode`) |
| :--- | :--- | :--- |
| `ACTION_UP` | 方向键-上 | `19 (DPAD_UP)` |
| `ACTION_DOWN` | 方向键-下 | `20 (DPAD_DOWN)` |
| `ACTION_LEFT` | 方向键-左 | `21 (DPAD_LEFT)` |
| `ACTION_RIGHT` | 方向键-右 | `22 (DPAD_RIGHT)` |
| `ACTION_SELECT` | 居中确定/确认 | `66 (ENTER / DPAD_CENTER)` |
| `ACTION_SOFT_LEFT` | 左功能/菜单软键 | `82 (MENU)` |
| `ACTION_SOFT_RIGHT` | 右功能/返回软键 | `4 (BACK)` |
| `ACTION_LOCK_SCREEN`| 锁屏按键 | `17 (STAR / *)` |
| `ACTION_CALL` | 拨号/呼叫按键 | `5 (CALL)` |

---

## 🌐 组织与开源生态

- **桌面项目**：`keydroidx-launcher`
- **核心 SDK**：`keydroidx-core`
- **开源协议**：Apache 2.0
