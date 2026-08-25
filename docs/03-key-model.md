# 03 · 按键模型

物理按键与语义动作的双向映射。核心思想：**业务代码只认 `NokiaKeyAction` 语义常量，永远不写死 keyCode**；同一套代码在任意按键机上，只要用户（或桌面）配过键即可工作。

## NokiaKeyAction

`io.github.cctyl.nokia.keycore.model.NokiaKeyAction`

不可实例化的常量类。

### 语义动作常量

| 常量 | 值 | 含义 | 标准兜底 KeyCode |
|------|----|------|------------------|
| `UP` / `ACTION_UP` | 0 | 方向-上 | `KEYCODE_DPAD_UP (19)` |
| `DOWN` / `ACTION_DOWN` | 1 | 方向-下 | `KEYCODE_DPAD_DOWN (20)` |
| `LEFT` / `ACTION_LEFT` | 2 | 方向-左 | `KEYCODE_DPAD_LEFT (21)` |
| `RIGHT` / `ACTION_RIGHT` | 3 | 方向-右 | `KEYCODE_DPAD_RIGHT (22)` |
| `SELECT` / `ACTION_SELECT` | 4 | 居中确定 | `KEYCODE_DPAD_CENTER (23)` |
| `SOFT_LEFT` / `ACTION_SOFT_LEFT` | 5 | 左软键 | `KEYCODE_MENU (82)` |
| `SOFT_RIGHT` / `ACTION_SOFT_RIGHT` | 6 | 右软键 | `KEYCODE_BACK (4)` |
| `LOCK_SCREEN` / `ACTION_LOCK_SCREEN` | 7 | 锁屏/挂机键 | `KEYCODE_ENDCALL` |
| `CALL` / `ACTION_CALL` | 8 | 拨号/通话键 | `KEYCODE_CALL (5)` |
| `UNKNOWN` / `ACTION_UNKNOWN` | -1 | 未识别 | — |

带 `ACTION_` 前缀的是兼容别名，**新代码推荐用简写形式**。

### 静态方法

```java
public static String getActionName(int action)
```
返回动作中文名（"上"/"确定"/"左软键"...），越界返回 `"未知"`。用于向导、日志、调试面板。

```java
public static int parseActionKey(@Nullable String key)
```
把 `"UP"`/`"SOFT_LEFT"` 等协议字符串解析为动作常量；忽略大小写，未匹配返回 `UNKNOWN(-1)`。Provider 协议解析专用。

### 常量数组

```java
public static final String[] ACTION_KEYS   // {"UP","DOWN",...,"CALL"}，索引即动作值
public static final String[] ACTION_NAMES  // {"上","下","左","右","确定","左软键","右软键","锁屏","拨号"}
```

## NokiaKeyBinding

`io.github.cctyl.nokia.keycore.model.NokiaKeyBinding`

action ↔ keyCode 的双向映射表（内部两个 `SparseIntArray`），支持 SharedPreferences 持久化。所有读写方法均为 `synchronized`。

### 构造

```java
public NokiaKeyBinding()                       // 初始化为标准兜底映射
public NokiaKeyBinding(@NonNull Context context) // 初始化后立即 loadLocal(context)
```

### 映射读写

```java
public synchronized void bind(int action, int keyCode)
```
写入一条双向映射；`keyCode <= 0` 时静默忽略（防止脏数据）。

```java
public synchronized void setKeyCode(int action, int keyCode)  // bind 的别名
public synchronized int getKeyCode(int action)                // action → keyCode，无映射返回 -1
public synchronized int resolveAction(int keyCode)            // keyCode → action，未识别返回 -1
```

```java
public synchronized int resolveAction(@Nullable KeyEvent event)
```
从 KeyEvent 解析语义动作。额外规则：若 SELECT 尚未被绑定，`KEYCODE_ENTER` 兜底视为 SELECT。

```java
public synchronized void clear()     // 清空全部映射
public void initDefaults()           // 重置为标准兜底映射表
```

### 本地持久化

存储位置：SharedPreferences 文件 `nokia_key_bindings`，key 为 `ACTION_KEYS[i]` 字符串。

```java
public synchronized void save(@NonNull Context context)          // 全量写出
public synchronized void loadLocal(@NonNull Context context)     // 读入并 bind（缺项跳过）
public synchronized boolean loadFromLocal(@NonNull Context context)
// 有本地配置才读入并返回 true；从未配过返回 false —— NokiaClient 三级降级的判断依据
public static boolean hasConfiguredLocally(@NonNull Context context)
```

### 工具方法

```java
public static String getWizardPromptName(int step)  // 第 step 步的录入提示名，供配键向导用
public synchronized NokiaKeyBinding clone()         // 深拷贝（监听回调派发副本，防止外部篡改内部表）
public static final int ACTION_COUNT = 9
public static final String[] ACTION_PROMPTS         // 向导步骤提示文案（同 ACTION_NAMES）
```

## 典型用法

### 独立集成（不继承基类时）

```kotlin
override fun dispatchKeyEvent(event: KeyEvent): Boolean {
    if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
        val action = NokiaClient.get(this).keyBinding.resolveAction(event)
        if (action >= 0) return handleAction(action)
    }
    return super.dispatchKeyEvent(event)
}
```

> 注意长按连发：需要重复响应的按键（如删除键连续删字符）不要加 `repeatCount == 0` 过滤，靠系统 REPEAT 事件驱动。

### 配键向导里临时改映射

```kotlin
val draft = NokiaClient.get(this).keyBinding.clone()
draft.bind(NokiaKeyAction.UP, event.keyCode)   // 用户按下某物理键
draft.save(this)                                // 写本地
NokiaClient.get(this).reload()                  // 立即生效并通知全局
```

## 相关文档

- 谁来调用 resolveAction 并分发 → [04-base-activity](./04-base-activity.md)
- 录入向导 → [09-key-wizard](./09-key-wizard.md)
