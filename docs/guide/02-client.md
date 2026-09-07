# 02 · 生态客户端（配置同步）

`io.github.cctyl.nokia.keycore.KeydroidxClient`

`KeydroidxClient` 是整个 SDK 的配置中枢：负责把「桌面端配好的按键 / 主题 / 字体」跨进程同步到本应用，并在拿不到时逐级降级。全局单例。

```
KeydroidxClient.get(context)
        │
        ├─ reload() 四级降级：
        │   ① content://io.github.cctyl.nokia.keyprovider/...     (Release 桌面)
        │   ② content://io.github.cctyl.nokia.debug.keyprovider/...(Debug 桌面)
        │   ③ 本地 SharedPreferences（本应用独立配键）
        │   ④ Android 标准键值兜底
        │
        └─ ContentObserver：桌面配置变更 → 自动 reload → 回调所有监听者
```

> **①② 的探测顺序不是固定的**：当 HOME 应用包名包含 `debug`（开发者设备装的是 Debug 版桌面）时，顺序反转为先探 Debug、再探 Release，保证调试时改动即时可见。

## KeydroidxClient

`io.github.cctyl.nokia.keycore.KeydroidxClient`

### 常量

| 常量 | 值 | 说明 |
|------|----|------|
| `RELEASE_AUTHORITY` | `io.github.cctyl.nokia.keyprovider` | Release 桌面 Provider authority |
| `DEBUG_AUTHORITY` | `io.github.cctyl.nokia.debug.keyprovider` | Debug 桌面 Provider authority |

### 获取单例

```java
public static synchronized KeydroidxClient get(@NonNull Context context)
```
双检锁单例；内部持有 applicationContext，首次创建时加载本地偏好并执行一次 `reload()`。**全 SDK 所有组件（基类、弹窗、主题）都通过它取配置，业务侧一般不需要自己 new。**

> `KeydroidxClient` 同时实现了 common 的 `ThemeProvider` 接口并在 `get()` 时自注入 `KeydroidxTheme.setThemeProvider(this)`——独立 App 无需手动注入主题提供者；桌面（launcher）则注入自己的本地实现。机制见 [architecture/module-layering](../architecture/module-layering.md) §五。

### 配置来源枚举 ConfigSource

| 枚举值 | 含义 |
|--------|------|
| `DESKTOP_RELEASE` | 来自 Release 桌面 Provider（Tier 1） |
| `DESKTOP_DEBUG` | 来自 Debug 桌面 Provider（Tier 2） |
| `LOCAL_CUSTOM` | 本应用本地独立配置（Tier 3） |
| `FALLBACK_DEFAULT` | 无任何配置，标准键值兜底（Tier 4） |

### 读取当前配置

```java
public KeydroidxKeyBinding getBinding()          // 返回内部按键映射表（活引用，勿改）
public KeydroidxKeyBinding getKeyBinding()       // getBinding() 的别名
public ConfigSource getConfigSource()        // 当前生效的配置来源
public boolean isFromDesktop()               // 来源是否为桌面（Release 或 Debug）
public String getCurrentThemeId()            // 当前主题 ID，如 "classic_blue"
public KeydroidxTheme.ThemeDef getCurrentTheme() // 当前主题定义对象
public String getCurrentFontId()             // 当前字体 ID
public float getCurrentFontScale()           // 当前字体缩放系数
```

### 主动重载与本地设置

```java
public synchronized void reload()
```
按四级降级顺序重新拉取全部配置（按键 + settings 表中的 theme_id/font_id/font_scale），成功后向所有监听者派发变更并写入本地偏好。**桌面端改了配置但 Observer 未注册成功时，可手动调它兜底。**

```java
public void setThemeId(String themeId)  // 写本地偏好 + 派发 onThemeChanged
public void setFontId(String fontId)    // 同步给 KeydroidxFontManager + 派发 onFontChanged
```
> 这两个方法只影响本应用本地偏好（Tier 3 的数据源），不会写回桌面。桌面优先级更高，下次 reload 会覆盖。

### 监听配置变化

```java
public interface OnConfigChangedListener {
    void onKeysChanged(@NonNull KeydroidxKeyBinding binding, @NonNull ConfigSource source);
    void onThemeChanged(@NonNull String themeId, @NonNull KeydroidxTheme.ThemeDef theme);
    void onFontChanged(@NonNull String fontId, float fontScale);
}

public void addListener(OnConfigChangedListener listener)
public void registerListener(OnConfigChangedListener listener)      // addListener 别名
public void removeListener(OnConfigChangedListener listener)
public void unregisterListener(OnConfigChangedListener listener)    // removeListener 别名
```

- 注册时**立即回调一次当前最新值**（三个回调都会触发），无需再手动初始化；
- 回调统一 post 到主线程；
- `KeydroidxBaseActivity` 已在 `onCreate/onDestroy` 自动注册/反注册并实现了三个回调（换肤、字体、按键热更新），继承它的页面无需重复处理。

### Provider 协议（桌面端实现方参考）

| URI | 列 | 说明 |
|-----|----|------|
| `content://{authority}/keys` | `action`(String)、`actionId`(int)、`keyCode`(int)、`keyName`(String) | 每行一条映射；action 取值为 `KeydroidxKeyAction.ACTION_KEYS` 中的字符串 |
| `content://{authority}/settings` | `key`(String)、`value`(String) | 支持的 key：`theme_id`、`font_id`、`font_scale` |

> 契约常量统一在 `common.contract.KeydroidxProviderContract`，桌面端实现必须与其字段命名严格一致。

> 旧版入口 `KeydroidxKeyClient`（代理到 `KeydroidxClient` 的兼容壳）已删除，调用方统一使用 `KeydroidxClient`。

## 相关文档

- 按键映射表本身的读写 → [03-key-model](./03-key-model.md)
- 基类如何消费这些回调做自动换肤 → [04-base-activity](./04-base-activity.md)
