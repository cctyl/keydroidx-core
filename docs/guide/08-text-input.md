# 08 · 文本输入

主包：`io.github.cctyl.nokia.common.ui`（Fragment 形态）／`io.github.cctyl.nokia.keycore.ui`（Activity 形态）

物理键盘设备上系统软键盘体验极差，SDK 提供全屏复古文本编辑页替代输入弹窗。两种形态按需选用：

| 形态 | 类 | 适用 |
|:---|:---|:---|
| Fragment | `KeydroidxTextInputFragment` | 已在 `KeydroidxBaseActivity` 体系内，页面内跳转 |
| Activity | `KeydroidxTextInputActivity` | 独立入口 / 需要 `startActivityForResult` |

## KeydroidxTextInputFragment

继承 `KeydroidxPageFragment`（见 [05-page-framework](./05-page-framework.md)），自动获得主题、字体与按键分发。

### 创建

```java
public static KeydroidxTextInputFragment newInstance(
        @NonNull String title,      // 顶栏标题
        @Nullable String text,      // 初始文本
        @Nullable String hint,      // 占位提示
        boolean multiline,          // 是否多行
        int maxChars)               // 最大字符数
```

### 配置

```java
public interface OnConfirmListener {
    void onConfirm(String text);    // 确认时回调（校验通过后）
}

public KeydroidxTextInputFragment setOnConfirmListener(@Nullable OnConfirmListener listener)
public KeydroidxTextInputFragment setRequired(boolean required)   // 空文本时阻止确认
```

### 按键行为

| 键 | 行为 |
|:---|:---|
| 左软键 | 确认（触发 `onConfirmListener`，`required` 且为空则拦截） |
| 右软键 / 返回键 | 返回上一页 |
| 方向键 | 多行时上下移动光标行；单行时交给宿主 |

### 使用示例

```java
openFragment(KeydroidxTextInputFragment.newInstance("问题描述", currentText,
        "描述问题与复现步骤", true, 500)
        .setRequired(true)
        .setOnConfirmListener(text -> {
            // 保存并返回
        }));
```

## KeydroidxTextInputActivity

key-core 独有（继承 `keycore.ui.KeydroidxBaseActivity`，自动绑定桌面配置同步），适合从普通 Activity 以标准 `startActivityForResult` 方式调起。

> **实现形态（2026-09-06 收口）**：本 Activity 是**薄壳**，UI 与业务逻辑全部由 common 的 `KeydroidxTextInputFragment` 提供，与桌面 Launcher 压栈用的是同一个 Fragment，两端零重复。此前 core 自带一份 `activity_nokia_text_input.xml` 与重复的着色/软键逻辑，已删除。

### 启动

```java
Intent intent = new Intent(context, KeydroidxTextInputActivity.class)
        .putExtra(KeydroidxTextInputActivity.EXTRA_TITLE, "问题描述")
        .putExtra(KeydroidxTextInputActivity.EXTRA_HINT, "描述问题与复现步骤")
        .putExtra(KeydroidxTextInputActivity.EXTRA_TEXT, currentText)
        .putExtra(KeydroidxTextInputActivity.EXTRA_MAX_CHARS, 500);
startActivityForResult(intent, REQ_EDIT);
```

### 常量

| 常量 | 说明 |
|:---|:---|
| `EXTRA_TITLE` / `EXTRA_HINT` / `EXTRA_TEXT` / `EXTRA_MAX_CHARS` | 入参 |
| `EXTRA_MULTILINE` | 是否多行输入，默认 `true`（历史版本固定多行，故默认保持 true） |
| `RESULT_TEXT` | 返回 Intent 中的结果键 |

### 按键与软键

与 `KeydroidxTextInputFragment` 完全一致（因为是同一个实现）：方向键透传给输入框、LSK=选项菜单（粘贴 / 复制全部 / 清空全部 / 保存并退出 / 退出不保存）、CSK=确定、RSK=有内容时退格清除 / 空内容时返回、BACK=放弃退出。必填为空时提示「内容不能为空」而不退出。


### 取结果

```java
@Override
protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
    if (requestCode == REQ_EDIT && resultCode == RESULT_OK && data != null) {
        String text = data.getStringExtra(KeydroidxTextInputActivity.RESULT_TEXT);
    }
}
```

## 相关文档

- 页面框架与跳转 → [05-page-framework](./05-page-framework.md)
- 实际用例：反馈页的描述输入 → [11-feedback](./11-feedback.md)
