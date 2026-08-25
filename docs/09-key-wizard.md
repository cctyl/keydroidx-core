# 09 · 配键向导（NokiaKeyWizardActivity）

`io.github.cctyl.nokia.keycore.ui.NokiaKeyWizardActivity`

独立运行的物理按键录入向导：9 步引导用户依次按下 上/下/左/右/确定/左软键/右软键/锁屏/拨号 键，完成后写本地配置并立即全局生效。

适用场景：**设备未安装 KeydroidX 桌面（三级降级到 Tier 3 本地配置）**时，让用户在本应用内完成配键。装了桌面的设备应由桌面统一配，不需要进向导。

## 启动

```java
public static void start(Context context)
```
内部 start；context 非 Activity 时自动加 `FLAG_ACTIVITY_NEW_TASK`。

```kotlin
if (!NokiaClient.get(this).isFromDesktop) {
    NokiaKeyWizardActivity.start(this)
}
```

## 向导流程

| 步骤 | 行为 |
|------|------|
| 进入 | `draftBinding = NokiaClient.get().getBinding().clone()` 在副本上改，中途退出不污染现有配置 |
| 每步 | 显示「步骤 x / 9」与「请按下【xx】键」，等待任意 ACTION_DOWN |
| 捕获 | 当前步骤 action ↔ 用户按下的 keyCode 写入 draftBinding，界面显示 KeyCode 详情，600ms 后自动进入下一步 |
| 跳过 | 「跳过」按钮保留该步原有映射直接下一步 |
| 退出 | 第 0 步可按系统返回键取消；其余步骤返回键被捕获当作该步按键 |
| 完成 | `draftBinding.save(this)` 写 SharedPreferences → `NokiaClient.get().reload()` 触发三级降级重读与全局回调 → Toast 提示 → finish |

## 注意事项

- 向导保存的是**本应用本地配置**（Tier 3）。之后若安装/打开桌面并同步成功，桌面配置优先级更高，本地配置会被覆盖——这是预期行为；
- `onDestroy` 会清理 Handler 全部回调，无泄漏；
- 布局为自适应实现（`activity_nokia_key_wizard.xml`），240×320 ~ 长屏均可用。

## 相关文档

- 三级降级机制 → [02-client](./02-client.md)
- 底层映射读写 → [03-key-model](./03-key-model.md)
