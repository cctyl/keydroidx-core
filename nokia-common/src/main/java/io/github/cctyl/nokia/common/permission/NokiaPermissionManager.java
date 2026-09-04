package io.github.cctyl.nokia.common.permission;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hjq.permissions.OnPermissionCallback;
import com.hjq.permissions.Permission;
import com.hjq.permissions.XXPermissions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.github.cctyl.nokia.common.log.NokiaLog;
import io.github.cctyl.nokia.common.ui.dialog.NokiaConfirmDialog;

/**
 * KeydroidX 生态统一权限管理门面（基于 XXPermissions 封装）。
 *
 * <p>特性：
 * <ul>
 *   <li>向下兼容至 Android 4.4 (KitKat, API 19)，在低版本上安全降级；</li>
 *   <li>广谱适配原生权限，并动态检测和追加特殊芯片/厂商（如展锐 Unisoc CTA）的应用列表权限；</li>
 *   <li>弹窗完全遵循 Nokia 复古按键交互规范（复用 {@link NokiaConfirmDialog}），绝不弹出 Material/原生触屏对话框；</li>
 *   <li>支持权限说明解释、批量申请、被永久拒绝后一键跳转系统设置页。</li>
 * </ul>
 * </p>
 */
public final class NokiaPermissionManager {

    private static final String TAG = "NokiaPermissionManager";

    /** 展锐工信部 CTA 应用列表读取权限 */
    public static final String PERMISSION_CTA_QUERY_ALL_PACKAGES = "com.unisoc.permission.CTA_QUERY_ALL_PACKAGES";

    /** 小米 MIUI / 部分 ROM 应用列表读取权限 */
    public static final String PERMISSION_GET_INSTALLED_APPS = "android.permission.GET_INSTALLED_APPS";

    private NokiaPermissionManager() {}

    /**
     * 判断指定权限是否已被授予。
     */
    public static boolean isGranted(@NonNull Context context, @NonNull String permission) {
        // 特殊权限（如通知使用权、悬浮窗）不随运行时权限机制，低版本也要真实检查
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M && !isSpecialPermission(permission)) {
            return true;
        }
        return XXPermissions.isGranted(context, permission);
    }

    /**
     * 判断一组权限是否全部被授予。
     */
    public static boolean isGranted(@NonNull Context context, @NonNull String... permissions) {
        // 低版本：dangerous 权限安装时已授予，但特殊权限仍需真实检查
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            for (String p : permissions) {
                if (isSpecialPermission(p) && !XXPermissions.isGranted(context, p)) {
                    return false;
                }
            }
            return true;
        }
        return XXPermissions.isGranted(context, permissions);
    }

    /**
     * 判断一组权限是否全部被授予。
     */
    public static boolean isGranted(@NonNull Context context, @NonNull List<String> permissions) {
        // 低版本：dangerous 权限安装时已授予，但特殊权限仍需真实检查
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            for (String p : permissions) {
                if (isSpecialPermission(p) && !XXPermissions.isGranted(context, p)) {
                    return false;
                }
            }
            return true;
        }
        return XXPermissions.isGranted(context, permissions);
    }

    /**
     * 检查是否具备查看/读取已安装应用列表的权限。
     *
     * <p>兼容 Android 4.4+、Android 11+ 原生 QUERY_ALL_PACKAGES、展锐 CTA 以及小米等定制系统。</p>
     */
    public static boolean hasAppListPermission(@NonNull Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true;
        }

        // 1. 检查展锐 CTA 特有权限（若系统声明了此权限定义）
        if (isSystemPermissionDefined(context, PERMISSION_CTA_QUERY_ALL_PACKAGES)) {
            if (!XXPermissions.isGranted(context, PERMISSION_CTA_QUERY_ALL_PACKAGES)) {
                return false;
            }
        }

        // 2. 检查 GET_INSTALLED_APPS
        if (isSystemPermissionDefined(context, PERMISSION_GET_INSTALLED_APPS)) {
            if (!XXPermissions.isGranted(context, PERMISSION_GET_INSTALLED_APPS)) {
                return false;
            }
        }

        // 3. 通用 XXPermissions 提供的应用列表权限判定
        try {
            return XXPermissions.isGranted(context, Permission.GET_INSTALLED_APPS);
        } catch (Throwable t) {
            // 部分未支持的平台上 fallback 为验证是否能实际读出数量
            try {
                return context.getPackageManager().getInstalledApplications(0).size() > 0;
            } catch (Throwable ignored) {
                return true;
            }
        }
    }

    /**
     * 获取当前系统下需要申请的应用列表权限列表。
     * 针对通用 ROM（Permission.GET_INSTALLED_APPS）与特殊 ROM（如展锐 CTA）动态追加。
     */
    @NonNull
    public static List<String> getRequiredAppListPermissions(@NonNull Context context) {
        List<String> perms = new ArrayList<>();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return perms;
        }

        // 通用应用列表权限
        perms.add(Permission.GET_INSTALLED_APPS);

        // 特殊厂商：展锐 CTA
        if (isSystemPermissionDefined(context, PERMISSION_CTA_QUERY_ALL_PACKAGES)) {
            perms.add(PERMISSION_CTA_QUERY_ALL_PACKAGES);
        }

        return perms;
    }

    /**
     * 检查系统中是否存在某权限定义（避免在没有该定义的系统上盲目发起请求抛出异常）
     */
    public static boolean isSystemPermissionDefined(@NonNull Context context, @NonNull String permissionName) {
        try {
            PackageManager pm = context.getPackageManager();
            return pm.getPermissionInfo(permissionName, 0) != null;
        } catch (Throwable e) {
            return false;
        }
    }

    /**
     * 判断是否为「特殊权限」（special permission，如通知使用权、悬浮窗、使用情况访问等）。
     * <p>特殊权限不随 Android 6.0 运行时权限机制，需用户手动去系统设置开启，
     * 即使在 Android 4.4 ~ 5.1 上也必须真实检查 / 主动申请，不能用版本守卫短路。
     * <p>委托给 {@link XXPermissions#isSpecial(String)}（XXPermissions 20.0 内置白名单）。
     */
    public static boolean isSpecialPermission(@Nullable String perm) {
        if (perm == null) return false;
        try {
            return XXPermissions.isSpecial(perm);
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * 分离标准危险权限与厂商专有危险权限。
     * XXPermissions 仅接受其白名单内的权限；若含有厂商专有危险权限（如部分机型的 CTA 权限），
     * 需分流至系统原生 requestPermissions 申请，避免触发外部库的参数校验异常。
     */
    public static void request(@NonNull Activity activity,
                               @NonNull List<String> permissions,
                               @NonNull OnPermissionCallback callback) {
        if (activity == null || activity.isFinishing()) {
            return;
        }
        if (permissions.isEmpty()) {
            callback.onGranted(permissions, true);
            return;
        }
        // 低版本（< API 23）：无运行时权限机制，dangerous 权限安装时已授予；
        // 但特殊权限（如通知使用权）仍需用户手动开启，必须穿透到 XXPermissions 跳系统设置页
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            final List<String> specialPerms = new ArrayList<>();
            for (String p : permissions) {
                if (isSpecialPermission(p)) {
                    specialPerms.add(p);
                }
            }
            if (specialPerms.isEmpty()) {
                callback.onGranted(permissions, true);
                return;
            }
            final List<String> finalAll = permissions;
            XXPermissions.with(activity)
                    .unchecked()
                    .permission(specialPerms)
                    .request(new OnPermissionCallback() {
                        @Override
                        public void onGranted(@NonNull List<String> granted, boolean allGranted) {
                            callback.onGranted(finalAll, allGranted);
                        }

                        @Override
                        public void onDenied(@NonNull List<String> denied, boolean quick) {
                            callback.onDenied(denied, quick);
                        }
                    });
            return;
        }

        List<String> standardPerms = new ArrayList<>();
        List<String> customPerms = new ArrayList<>();

        for (String perm : permissions) {
            if (isStandardPermission(perm)) {
                standardPerms.add(perm);
            } else {
                customPerms.add(perm);
            }
        }

        // 如果只有厂商专有权限，走原生系统申请
        if (standardPerms.isEmpty()) {
            requestCustomPermissions(activity, customPerms, callback);
            return;
        }

        // 先通过 XXPermissions 申请标准权限，成功后再顺带请求非标准权限
        XXPermissions.with(activity)
                .unchecked()
                .permission(standardPerms)
                .request(new OnPermissionCallback() {
                    @Override
                    public void onGranted(@NonNull List<String> granted, boolean allGranted) {
                        if (!customPerms.isEmpty()) {
                            requestCustomPermissions(activity, customPerms, new OnPermissionCallback() {
                                @Override
                                public void onGranted(@NonNull List<String> customGranted, boolean customAllGranted) {
                                    List<String> totalGranted = new ArrayList<>(granted);
                                    totalGranted.addAll(customGranted);
                                    callback.onGranted(totalGranted, allGranted && customAllGranted);
                                }

                                @Override
                                public void onDenied(@NonNull List<String> customDenied, boolean quick) {
                                    callback.onDenied(customDenied, quick);
                                }
                            });
                        } else {
                            callback.onGranted(granted, allGranted);
                        }
                    }

                    @Override
                    public void onDenied(@NonNull List<String> denied, boolean quick) {
                        NokiaLog.w(TAG, "Permissions denied: " + denied + ", quick=" + quick);
                        callback.onDenied(denied, quick);
                    }
                });
    }

    private static boolean isStandardPermission(String perm) {
        return perm != null && (perm.startsWith("android.permission.") || perm.startsWith("com.android."));
    }

    private static void requestCustomPermissions(@NonNull Activity activity,
                                                 @NonNull List<String> permissions,
                                                 @NonNull OnPermissionCallback callback) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            activity.requestPermissions(permissions.toArray(new String[0]), 999);
        }
        // 原生回调难以无侵入挂载，根据当前授权状态回调
        activity.getWindow().getDecorView().postDelayed(() -> {
            boolean allGranted = true;
            for (String p : permissions) {
                if (activity.checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                callback.onGranted(permissions, true);
            } else {
                callback.onDenied(permissions, false);
            }
        }, 300);
    }

    /**
     * 发起权限请求（变长参数）。
     */
    public static void request(@NonNull Activity activity,
                               @NonNull OnPermissionCallback callback,
                               @NonNull String... permissions) {
        request(activity, Arrays.asList(permissions), callback);
    }

    /**
     * 弹出复古诺基亚样式的权限引导说明弹窗，并在用户点击左软键时发起申请。
     *
     * @param activity 宿主
     * @param title 弹窗标题（如“权限申请”）
     * @param message 弹窗说明（如“需要获取应用列表权限以解冻并启动应用”）
     * @param permissions 待申请权限
     * @param callback 回调
     */
    public static void requestWithNokiaDialog(@NonNull Activity activity,
                                             @NonNull String title,
                                             @NonNull String message,
                                             @NonNull List<String> permissions,
                                             @NonNull OnPermissionCallback callback) {
        if (activity == null || activity.isFinishing()) {
            return;
        }
        // isGranted 已在内部对低版本的 dangerous/special 权限做了正确分流，无需再单独守卫版本
        if (isGranted(activity, permissions)) {
            callback.onGranted(permissions, true);
            return;
        }

        NokiaConfirmDialog dialog = new NokiaConfirmDialog(activity, title, message);
        dialog.setPositiveButton("授权", () -> request(activity, permissions, callback));
        dialog.setNegativeButton("取消", () -> callback.onDenied(permissions, false));
        dialog.show();
    }

    /**
     * 便捷方法：请求应用列表权限
     */
    public static void requestAppListPermission(@NonNull Activity activity,
                                                @NonNull String message,
                                                @NonNull OnPermissionCallback callback) {
        List<String> perms = getRequiredAppListPermissions(activity);
        requestWithNokiaDialog(activity, "权限申请", message, perms, callback);
    }

    /**
     * 获取桌面正常运行所需的「核心权限全集」。
     *
     * <p>全集覆盖：
     * <ul>
     *   <li>应用列表（GET_INSTALLED_APPS + 展锐 CTA 等厂商专有）—— 功能表/解冻/启动；</li>
     *   <li>READ_PHONE_STATE —— 顶栏信号/双卡；</li>
     *   <li>POST_NOTIFICATIONS（API 33+）—— 保活通知可见；</li>
     *   <li>BIND_NOTIFICATION_LISTENER_SERVICE —— 通知中心/音乐组件
     *       （XXPermissions 20.0 内置 special permission，自动跳系统设置页授权）。</li>
     * </ul>
     * 存储/位置/相机/麦克风等非主流程必需权限不纳入启动自检，由对应功能首次使用时再申请。
     *
     * @return 当前系统下需申请的核心权限全集（含未授权项）；低版本仅返回特殊权限（如通知使用权）
     */
    public static List<String> getRequiredCorePermissions(@NonNull Context context) {
        List<String> perms = new ArrayList<>();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            // 低版本：dangerous 权限安装时已授予，无需申请；
            // 但通知使用权是 special permission（Android 4.3+ 引入），仍需用户手动开启
            perms.add(Permission.BIND_NOTIFICATION_LISTENER_SERVICE);
            return perms;
        }
        // 应用列表（含展锐 CTA 等厂商专有）
        perms.addAll(getRequiredAppListPermissions(context));
        // 电话状态：顶栏信号/双卡
        perms.add(Permission.READ_PHONE_STATE);
        // 通知权限（API 33+）：保活通知可见
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Permission.POST_NOTIFICATIONS);
        }
        // 通知使用权：通知中心/音乐组件（special permission，XXPermissions 跳设置页授权）
        perms.add(Permission.BIND_NOTIFICATION_LISTENER_SERVICE);
        return perms;
    }

    /**
     * 判断核心权限全集是否全部就绪（供清单页/启动自检快速判定）。
     */
    public static boolean isCorePermissionsGranted(@NonNull Context context) {
        // 低版本：dangerous 权限安装时已授予，仅检查特殊权限（如通知使用权）
        for (String p : getRequiredCorePermissions(context)) {
            if (!XXPermissions.isGranted(context, p)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 便捷方法：向导/设置页/启动自检批量请求核心权限全集。
     *
     * <p>内部先过滤已授权项，仅对缺失项弹出复古诺基亚说明框后统一申请；
     * XXPermissions 自动分流：dangerous 权限弹系统框，special 权限（如通知使用权）跳设置页，
     * 全部走同一 {@link OnPermissionCallback} 回调。
     */
    public static void requestCorePermissions(@NonNull Activity activity,
                                              @NonNull String message,
                                              @NonNull OnPermissionCallback callback) {
        List<String> all = getRequiredCorePermissions(activity);
        // 过滤出未授权项，避免对已授权权限重复打扰
        List<String> needed = new ArrayList<>();
        for (String p : all) {
            if (!XXPermissions.isGranted(activity, p)) {
                needed.add(p);
            }
        }
        if (needed.isEmpty()) {
            NokiaLog.i(TAG, "核心权限全集已就绪，无需申请");
            callback.onGranted(all, true);
            return;
        }
        NokiaLog.i(TAG, "启动核心权限批量申请，缺失: " + needed);
        requestWithNokiaDialog(activity, "系统权限申请", message, needed, callback);
    }

    /**
     * 权限被永久拒绝时，弹出诺基亚样式的“去设置”引导弹窗。
     * 用户按左软键即调用系统设置跳转。
     */
    public static void showSettingDialog(@NonNull Activity activity,
                                         @NonNull String title,
                                         @NonNull String message,
                                         @Nullable Runnable onCancel) {
        if (activity == null || activity.isFinishing()) {
            return;
        }

        NokiaConfirmDialog dialog = new NokiaConfirmDialog(activity, title, message);
        dialog.setPositiveButton("去设置", () -> XXPermissions.startPermissionActivity(activity));
        dialog.setNegativeButton("取消", () -> {
            if (onCancel != null) {
                onCancel.run();
            }
        });
        dialog.show();
    }
}
