# 17 · mini_shizuku 调用（keydroidx-mini-shizuku）

> 让生态独立应用以 **shell（UID 2000）身份** 执行系统命令的能力。
> 服务端由 KeydroidX 桌面（`keydroidx-launcher`）内置并以 `app_process` 运行，本模块只提供**客户端**；
> 客户端与 launcher 需**同签名**才可拿到密钥 `K`。

## 一、这是什么 / 什么时候用

mini_shizuku 是 KeydroidX 桌面内置的轻量 Shell 通道：

- **服务端**（launcher 侧，`ru.playsoftware.mini_shizuku.server.*`）：桌面激活后在本地回环地址以 shell 身份监听 TCP（端口为内部约定，见服务端 `SocketService`）；
- **本模块**（客户端，`io.github.cctyl.nokia.shizuku.*`）：提供 `MiniShizuku` 门面，自动完成「拉取密钥 K → 鉴权 → 执行命令」。

典型用途（生态实际用法）：

- API < 24 设备上无 `dispatchGesture`，用 `MiniShizuku.exec("input swipe …")` 模拟点击/滑动（如 `keydroidx-foucs` 的 `ShellGesturePerformer`，仅在 `Build.VERSION.SDK_INT < 24` 走此路径）；
- 桌面自身的电源键拦截、挂机键拦截、进程判活等系统级命令（launcher 的 `Shizuku` 门面同样基于本客户端）。

> 无 root / 未激活服务时，所有调用**静默失败返回 false/null**，绝不崩溃。

## 二、依赖引入

模块坐标（本地 Maven，v1.0.0）：

```gradle
implementation 'io.github.cctyl.nokia:keydroidx-mini-shizuku:1.0.0'
```

多仓库开发联调时用 `includeBuild` 指向本仓库（见 `docs/architecture/module-layering.md` §7.1，`substitute` 必须显式写）：

```gradle
// settings.gradle
includeBuild('../keydroidx-core') {
    dependencySubstitution {
        substitute module('io.github.cctyl.nokia:keydroidx-common')      using project(':keydroidx-common')
        substitute module('io.github.cctyl.nokia:keydroidx-key-core')    using project(':keydroidx-key-core')
        substitute module('io.github.cctyl.nokia:keydroidx-mini-shizuku') using project(':keydroidx-mini-shizuku')
    }
}
```

> **零依赖**：本模块不依赖 `keydroidx-common`，与按键同步 SDK（`keydroidx-key-core`）也是**互相独立**的——只需要 shell 命令能力就单独引它，不需要引 key-core。

## 三、API 速览

对外唯一入口是门面类 `io.github.cctyl.nokia.shizuku.MiniShizuku`（全静态方法，内部委托 `MiniShizukuClient`）：

| 方法 | 含义 | 是否需同签名（K） | 返回值 |
|:---|:---|:---|:---|
| `init(Context)` | **必须先调用**：注入应用级 Context（在 `Application.onCreate` 调一次） | — | void |
| `isRunning()` | 服务是否在线（TCP 端口可连） | **否**（任意应用可探测） | boolean |
| `exec(String cmd)` | 静默执行一条命令（不回读输出） | 是 | boolean（仅表示写入成功，非执行结果） |
| `execAcked(String cmd)` | 执行并等待服务端一行 ack（`OK:` / `ERR:`）；拦截器等需确认生效的命令用 | 是 | boolean（读到 `ERR:`/IO 失败为 false；**读超时按旧服务端不回 ack 视为成功返回 true**） |
| `execWithOutput(String cmd)` | 执行并回读合并的 stdout/stderr（读到 `EXIT:<code>` 结束） | 是 | String（鉴权失败/IO 异常返回 **null**） |

```java
import io.github.cctyl.nokia.shizuku.MiniShizuku;

// Application.onCreate 里调一次
MiniShizuku.init(this);

// 先用 isRunning 探测（开线程里做，见 §五）
boolean online = MiniShizuku.isRunning();
if (!online) {
    // 服务未激活：提示用户去桌面激活 mini_shizuku
    return;
}

// 静默执行（不关心输出）
boolean ok = MiniShizuku.exec("input swipe 320 500 320 300");

// 执行并取回输出；鉴权失败/离线返回 null
String out = MiniShizuku.execWithOutput("id; whoami");
if (out != null) {
    // uid=2000(shell)... → 说明 shell 身份执行成功
}
```

完整可跑示例见 `sample`（`sample/src/main/java/io/github/cctyl/nokia/sample/MainActivity.java`）：点击按钮 → `isRunning()` 探测 → `execWithOutput("id; whoami")` 显示结果。

## 四、核心前置条件（必须全部满足）

1. **设备已安装 KeydroidX 桌面**（正式 `io.github.cctyl.nokia` 或调试 `io.github.cctyl.nokia.debug`，两者皆可，客户端自动探测）；
2. **桌面已激活 mini_shizuku 服务**且在线（桌面设置 → mini_shizuku → adb/root 激活）；
3. **宿主 App 与 launcher 同签名**：`K` 只能通过 launcher 的 `KeydroidxShizukuProvider.getKey` 获得，而该 Provider 按 `Binder.getCallingUid()` 反查签名、仅放行同签名调用方——异签名应用拿不到 K，`exec/execAcked/execWithOutput` 全部返回失败。

> `isRunning()` 只是 TCP 端口探测，不需要 K，对任意应用开放、无副作用。

### 调用链路一句话版

```
MiniShizuku.exec(cmd)
  → 进程内取/缓存 K（launcher 的 KeydroidxShizukuProvider，按调用方签名校验后放行）
  → 本地 TCP 发送「密钥 + 命令」（报文格式由 MiniShizukuConst 定义）
  → 服务端比对 K：一致则以 shell 身份执行；不一致直接拒绝
```

> 报文格式、端口等属于服务端内部约定，不在公开文档中展开。

完整鉴权与权限矩阵见 launcher 侧设计稿 `keydroidx-launcher/docs/mini_shizuku暴露给外部应用方案设计.md`。

## 五、注意与坑

1. **必须先 `init()`**：`init` 之前调用 exec 系方法，内部拿不到 Context、无法向 Provider 取 K，直接失败（logcat tag `MiniShizuku` 会打 `getKey: 未 init(context)…`）。
2. **在后台线程调用**：`isRunning` / `exec` 系列都是阻塞式 TCP 操作（连接超时 500ms、读超时 3s，见 `MiniShizukuConst`）。**禁止在主线程直接调用**，否则卡 UI；参考 sample 的用法：`new Thread(...)` 里执行、结果 `runOnUiThread` 回填。
3. **失败语义是静默的**：服务离线、鉴权被拒、命令不存在，均返回 false/null，**不会抛异常**——调用方务必判返回值，不能假定成功。
4. **`exec` 与 `execWithOutput` 的取舍**：`exec` 只发命令不等结果，适合「点了就行」的 input 模拟；要确认命令真的生效用 `execAcked`；要拿输出用 `execWithOutput`（读输出会读到命令结束符 `EXIT:<code>` 为止，需等完整执行完）。
5. **Android 11+ 包可见性（targetSdk ≥ 30 时）**：`keydroidx-mini-shizuku` 的 manifest 是**空的、不携带任何 `<queries>`**（与 `keydroidx-key-core` 不同——后者已自动声明 keyprovider 的 authority）。若宿主 targetSdk ≥ 30，客户端向 launcher 查询签名（`PackageManager.getPackageInfo`）与调用其 Provider 会被系统**静默拦截**，`resolveLauncherPackage` 找不到同签名 launcher、`getKey` 拿不到 K。此时须在宿主 manifest 自行声明：

```xml
<queries>
    <package android:name="io.github.cctyl.nokia" />
    <package android:name="io.github.cctyl.nokia.debug" />
    <provider android:authorities="io.github.cctyl.nokia.shizuku" />
    <provider android:authorities="io.github.cctyl.nokia.debug.shizuku" />
</queries>
```

> `foucs` 只在 API < 24 设备上走 mini_shizuku（低版本无包可见性过滤），故其 manifest 未声明上述 `<queries>` 也能工作；**若你的应用在 API 30+ 上也会调 MiniShizuku，必须声明**。

6. **协议常量以代码为准**：`MiniShizukuConst`（host/port/前缀/authority 后缀/launcher 候选包名）是 client 侧唯一事实源，与 launcher 服务端 `MsgProcess` 靠注释约定保持一致、无共享类——两端改协议需同步。
7. **launcher 重启后 K 变化由服务端自愈**：K 是 launcher 进程内存中随机生成的，launcher 被杀重启会换新 K。客户端进程级缓存旧 K 后再次调用，服务端比对不匹配会自动重新向 Provider 拉取一次，随后按新 K 执行——业务层无需处理。仅当「新 K 也拉不到」（如 launcher 已卸载 / 被换签名）才返回失败。

## 六、相关文档

- 模块定位与依赖关系：`docs/architecture/module-layering.md` §二
- 服务端激活/排障（launcher 侧）：`keydroidx-launcher/docs/mini_shizuku设计文档.md`、`README.md`「mini_shizuku 权限服务」
- 对外暴露方案与鉴权矩阵（launcher 侧）：`keydroidx-launcher/docs/mini_shizuku暴露给外部应用方案设计.md`
- 接入示例代码：`sample/`（`io.github.cctyl.nokia.sample`）
- 低版本手势适配实际消费方：`keydroidx-foucs` 的 `ShellGesturePerformer`（API < 24 才走此路径）
