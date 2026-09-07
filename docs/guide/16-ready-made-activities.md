# 16 · 开箱即用的 Activity

主包：`io.github.cctyl.nokia.keycore.ui`

key-core 内置四个现成 Activity，均已继承 `keycore.ui.KeydroidxBaseActivity`（自动绑定桌面配置同步、主题与字体热更新），宿主 `startActivity` 即用，无需自己写页面。

| Activity | 用途 | 启动方式 |
|:---|:---|:---|
| `KeydroidxKeyWizardActivity` | 独立配键向导 | `start(context)` |
| `KeydroidxTextInputActivity` | 全屏文本编辑 | Intent + extras，返回结果 |
| `KeydroidxAboutActivity` | 关于页 | `start(context[, config])` |
| `KeydroidxFeedbackActivity` | 反馈上报 | 直接 `startActivity` |

> 这些类是 **key-core 独有**（common 里没有），独立 App 只能从 key-core 获得。清单见 [architecture/module-layering](../architecture/module-layering.md) §三。

## KeydroidxKeyWizardActivity — 配键向导

设备未装原键桌面（Tier 2）时，让用户在本 App 内完成按键配置。

```java
KeydroidxKeyWizardActivity.start(context);
```

完成后自动保存到本地 SharedPreferences 并触发 `KeydroidxClient.reload()`，全局立即生效。支持触屏跳过 / 取消。

详见 [10-key-wizard](./10-key-wizard.md)。

## KeydroidxTextInputActivity — 全屏文本编辑

薄壳：实际实现是 common 的 `KeydroidxTextInputFragment`（与桌面 Launcher 同一份源码）。

```java
Intent intent = new Intent(context, KeydroidxTextInputActivity.class)
        .putExtra(KeydroidxTextInputActivity.EXTRA_TITLE, "问题描述")
        .putExtra(KeydroidxTextInputActivity.EXTRA_HINT, "描述问题与复现步骤")
        .putExtra(KeydroidxTextInputActivity.EXTRA_TEXT, currentText)
        .putExtra(KeydroidxTextInputActivity.EXTRA_MAX_CHARS, 500)
        .putExtra(KeydroidxTextInputActivity.EXTRA_MULTILINE, true);   // 默认 true
startActivityForResult(intent, REQ_EDIT);

// 结果
String text = data.getStringExtra(KeydroidxTextInputActivity.RESULT_TEXT);
```

详见 [08-text-input](./08-text-input.md)。

## KeydroidxAboutActivity — 关于页

```java
// 1) 默认配置（自动读取包名/版本/图标）
KeydroidxAboutActivity.start(context);

// 2) 自定义配置
KeydroidxAboutConfig config = KeydroidxAboutConfig.createDefault(context)
        .setDescription("...")
        .setShowUpdateCheck(true)
        .setUpdateCurrentVersion(BuildConfig.VERSION_NAME);
KeydroidxAboutActivity.start(context, config);
```

配置项见 [15-about-and-more-apps](./15-about-and-more-apps.md)。

## KeydroidxFeedbackActivity — 反馈上报

```java
startActivity(new Intent(context, KeydroidxFeedbackActivity.class));
```

内置问题类型 / 联系方式（必填校验）/ 描述 / 日志开关。**启动前必须先初始化**：

```java
// Application.onCreate 中
KeydroidxFeedback.init(new KeydroidxFeedbackConfig(
        BuildConfig.FEEDBACK_URL,
        BuildConfig.FEEDBACK_SECRET_KEY,
        "myapp",
        BuildConfig.VERSION_NAME,
        null));
```

详见 [11-feedback](./11-feedback.md)。

## 相关文档

- 宿主骨架能力 → [04-base-activity](./04-base-activity.md)
- 配置同步机制 → [02-client](./02-client.md)
