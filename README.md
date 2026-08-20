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

继承 `NokiaBaseActivity` 可自动拥有复古标题栏、底栏装配及按键分发：

```java
public class MyActivity extends NokiaBaseActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my);

        // 设置页面标题与底部软键文案
        setPageTitle("我的应用");
        setBottomBar("选项", "确定", "返回");
    }

    @Override
    protected boolean onActionSoftLeft() {
        // 处理左软键
        return true;
    }

    @Override
    protected boolean onActionSelect() {
        // 处理确定键
        return true;
    }

    @Override
    protected boolean onActionSoftRight() {
        // 处理右软键
        finish();
        return true;
    }
}
```

### 3. 方式二：使用 `NokiaKeyClient` 独立集成

```java
// 1. 获取按键配置
NokiaKeyBinding binding = NokiaKeyClient.get(context).getBinding();

// 2. 解析 KeyEvent 为物理语义动作
@Override
public boolean dispatchKeyEvent(KeyEvent event) {
    if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
        int action = binding.resolveAction(event.getKeyCode());
        switch (action) {
            case NokiaKeyAction.ACTION_SELECT:
                // 处理确定
                return true;
            case NokiaKeyAction.ACTION_CALL:
                // 处理拨号
                return true;
        }
    }
    return super.dispatchKeyEvent(event);
}

// 3. 打开配键向导
NokiaKeyWizardActivity.start(this);
```

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
