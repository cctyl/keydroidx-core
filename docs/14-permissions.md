# 14 - 权限管理与跨机型适配规范 (NokiaPermissionManager)

## 1. 概述与设计背景

按键机生态（Launcher、Music、Focus、Browser 等）在 Android 4.4 到 Android 14+ 的不同硬件平台上面临复杂的权限管理场景：
1. **多 Android 版本断层**：
   - Android 4.4 ~ 5.1（API 19 ~ 22）：无动态运行时权限，所有权限在安装时一次性静态授予。
   - Android 6.0+（API 23+）：引入运行时危险权限机制，必须动态申请并由用户授权。
   - Android 11+（API 30+）：引入软件包可见性机制（`QUERY_ALL_PACKAGES`）和精细化权限管控。
2. **多厂商 ROM 权限机制差异**：
   - 小米（MIUI / HyperOS）：使用私有 AppOps 管理 `OP_GET_INSTALLED_APPS`（读取应用列表）。
   - 展锐平台（Unisoc）：国内工信部进网规范（CTA）在系统安全模块中管控应用列表。
   - 华为 / 魅族 / 三星：各自存在不同的安全管理中心或权限兼容策略。
3. **诺基亚物理按键交互规范约束**：
   - 严禁弹出现代触屏 Material 样式的权限解释弹窗。
   - 所有权限解释提示框、拒绝引导去设置页的弹窗，必须统一符合 240dp 基准、双软键导航（左软键确认/授权，右软键取消/返回）、方向键焦点切换规范。

为避免各个子模块重复造轮子以及在权限申请逻辑上出现机型碎片化问题，`nokia-common` 统一集成了业界成熟的 `com.github.getActivity:XXPermissions`，并在此基础上封装了诺基亚专属门面 `NokiaPermissionManager`。

---

## 2. 架构设计与职责划分

```
+-------------------------------------------------------------------+
|               生态业务层 (Launcher, Music, Sample 等)              |
+-------------------------------------------------------------------+
                                  |
                                  v
+-------------------------------------------------------------------+
|     nokia-common: NokiaPermissionManager (生态统一人机交互与门面)     |
|  - 诺基亚复古风格确认弹窗 (NokiaConfirmDialog)                        |
|  - 永久拒绝后引导去系统设置页弹窗                                      |
|  - Android 4.4 兼容守卫 (低于 API 23 零开销直接通过)                   |
|  - 规范化标准权限派发 (禁止直接传入厂商私有字符串)                     |
+-------------------------------------------------------------------+
                                  |
                                  v
+-------------------------------------------------------------------+
|             底层引擎: XXPermissions (多 ROM 兼容与调度引擎)         |
|  - 自动识别厂商 ROM: 小米/华为/魅族/三星/原生等                        |
|  - 无界面 Headless Fragment 动态回调解耦                           |
|  - 各机型设置页深度跳转路由 (ApplicationDetails / Miui / Harmony 等) |
+-------------------------------------------------------------------+
```

---

## 3. 核心使用规范与红线约束

### 3.1 核心调用原则：直接使用标准常量，切勿手动搞特殊！

> **重要经验与红线**：
> `XXPermissions` 内部自带了非常完善的跨 ROM（如 MIUI、Flyme、ColorOS 等）自动抹平机制，其核心代码 `GetInstalledAppsPermissionCompat` 已经能够根据设备特征自动在标准权限和 OEM 私有 AppOps 之间转换。
> 
> **严禁做法**：
> 绝不能手动把某个特定芯片/厂商的私有权限字符串（例如展锐的 `com.unisoc.permission.CTA_QUERY_ALL_PACKAGES`）作为参数传给 `XXPermissions.permission(...)`！框架内部具有严格的安全参数校验（`PermissionChecker`），一旦发现入参不在其内置官方字典内，会直接抛出 `IllegalArgumentException` 导致应用闪退。
>
> **正确做法**：
> 统一直接传入 `Permission.GET_INSTALLED_APPS`、`Permission.READ_PHONE_STATE` 等标准常量，框架会自动抹平底层的差异，做到跨机型全自动适配。

### 3.2 清单声明要求（AndroidManifest.xml）

各子应用若需要访问软件包列表（如 Launcher），建议在清单中作如下静态声明以覆盖所有平台（静态声明对不适用的手机完全无害，系统会自动忽略）：

```xml
<!-- Android 11+ 原生系统级软件包可见性 -->
<uses-permission android:name="android.permission.QUERY_ALL_PACKAGES" tools:ignore="QueryAllPackagesPermission" />

<!-- 展锐芯片方案工信部进网规范（CTA）应用列表权限声明（避免平台级安全中心拦截） -->
<uses-permission android:name="com.unisoc.permission.CTA_QUERY_ALL_PACKAGES" tools:ignore="ProtectedPermissions" />

<!-- 通用第三方应用列表读取权限声明 -->
<uses-permission android:name="android.permission.GET_INSTALLED_APPS" tools:ignore="ProtectedPermissions" />
```

---

## 4. API 接入指南

### 4.1 常用权限判定

```java
import io.github.cctyl.nokia.common.permission.NokiaPermissionManager;
import com.hjq.permissions.Permission;

// 1. 检查是否具备单项或多项权限
boolean granted = NokiaPermissionManager.isGranted(context, Permission.GET_INSTALLED_APPS);

// 2. 检查应用列表访问权（自动向下兼容 Android 4.4）
boolean hasAppList = NokiaPermissionManager.hasAppListPermission(context);
```

### 4.2 发起诺基亚风格交互的权限申请

`NokiaPermissionManager.requestWithNokiaDialog` 会首先弹出诺基亚复古风格确认框，向用户解释申请缘由；用户点击左软键【授权】后才唤起系统原生申请；若被用户永久拒绝，会自动弹出复古引导框提示前往系统设置：

```java
NokiaPermissionManager.requestWithNokiaDialog(
        activity,
        "权限申请",
        "需要获取应用列表权限以展示和启动应用",
        new String[]{Permission.GET_INSTALLED_APPS},
        new NokiaPermissionManager.OnPermissionResultCallback() {
            @Override
            public void onGranted() {
                // 授权成功，执行业务逻辑
            }

            @Override
            public void onDenied(List<String> deniedList, boolean doNotAskAgain) {
                // 授权被拒
            }
        }
);
```

### 4.3 批量静默/直接申请

对于向导结算页等需要直接唤起系统授权流程的场景：

```java
NokiaPermissionManager.request(activity, new String[]{
        Permission.READ_PHONE_STATE,
        Permission.GET_INSTALLED_APPS
}, new NokiaPermissionManager.OnPermissionResultCallback() {
    @Override
    public void onGranted() {
        // 全量授权完成
    }

    @Override
    public void onDenied(List<String> deniedList, boolean doNotAskAgain) {
        if (doNotAskAgain) {
            // 弹出诺基亚风格提示前往系统设置页
            NokiaPermissionManager.showSettingDialog(
                    activity,
                    "权限受限",
                    "核心权限已被拒绝，请前往系统设置手动开启。",
                    null
            );
        }
    }
});
```

---

## 5. Android 4.4 (KitKat / API 19) 兼容性说明

1. **版本守卫（仅作用于 dangerous 权限）**：
   在 `Build.VERSION.SDK_INT < 23` 的设备上，系统无运行时权限机制，**dangerous 权限**（如 `READ_PHONE_STATE`、`GET_INSTALLED_APPS` 等）在安装时一次性静态授予，`NokiaPermissionManager` 会拦截其动态请求并直接回调 `onGranted()`。
2. **特殊权限例外（重要）**：
   **特殊权限**（special permission，如 `BIND_NOTIFICATION_LISTENER_SERVICE` 通知使用权、`SYSTEM_ALERT_WINDOW` 悬浮窗、`PACKAGE_USAGE_STATS` 使用情况访问等）**不随运行时权限机制**，从 Android 4.3/4.4 起就需要用户手动去系统设置开启，**在任何 Android 版本都不会「安装时自动授予」**。`NokiaPermissionManager` 对特殊权限不做低版本短路，统一穿透到 `XXPermissions` 走设置页跳转授权，保证 4.4 上通知使用权也能被真实检查与主动申请。
3. **零闪退保证**：
   `NokiaPermissionManager` 在低版本上对 dangerous 权限拦截动态请求并直接回调 `onGranted()`，对特殊权限则委托 `XXPermissions`（内部按 `enabled_notification_listeners` 等 Settings.Secure 项判断），杜绝低版本系统因缺少系统权限接口而崩溃的风险。
