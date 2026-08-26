package io.github.cctyl.nokia.keycore.feedback;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 设备信息采集器：为反馈上报的 extras 字段提供默认的设备信息。
 *
 * <p>仅读取系统公开 API，不涉及任何隐私敏感数据（无 IMEI/位置等）。</p>
 */
public final class DeviceInfoCollector {

    private DeviceInfoCollector() {
    }

    /**
     * 采集设备信息（品牌、型号、系统版本、内存、CPU 架构等）。
     *
     * @return 有序 map，直接作为 KDFB meta 的 extras 使用
     */
    public static Map<String, Object> collect(Context context) {
        Map<String, Object> info = new LinkedHashMap<>();
        try {
            info.put("device_brand", Build.BRAND);
            info.put("device_model", Build.MODEL);
            info.put("device_manufacturer", Build.MANUFACTURER);
            info.put("android_version", Build.VERSION.RELEASE);
            info.put("android_api", Build.VERSION.SDK_INT);
            if (Build.VERSION.SDK_INT >= 21) {
                info.put("supported_abis", join(Build.SUPPORTED_ABIS));
            } else {
                info.put("cpu_abi", Build.CPU_ABI);
            }
            info.put("screen_px", screenPx(context));

            ActivityManager am = (ActivityManager) context.getApplicationContext()
                    .getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) {
                ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
                am.getMemoryInfo(mi);
                info.put("total_mem_mb", mi.totalMem >> 20);
                info.put("avail_mem_mb", mi.availMem >> 20);
                info.put("memory_class_mb", am.getMemoryClass());
            }

            File dataDir = Environment.getDataDirectory();
            if (dataDir != null) {
                StatFs stat = new StatFs(dataDir.getPath());
                long totalBytes = stat.getBlockCount() * (long) stat.getBlockSize();
                info.put("free_disk_mb", stat.getAvailableBlocks() * (long) stat.getBlockSize() >> 20);
                info.put("total_disk_mb", totalBytes >> 20);
            }

            // 电池电量（取粘性广播，不发请求）
            android.content.Intent bat = context.registerReceiver(null,
                    new android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED));
            if (bat != null) {
                int level = bat.getIntExtra("level", -1);
                int scale = bat.getIntExtra("scale", 100);
                if (level >= 0 && scale > 0) {
                    info.put("battery_pct", level * 100 / scale);
                }
                int status = bat.getIntExtra("status", -1);
                info.put("charging", status == android.os.BatteryManager.BATTERY_STATUS_CHARGING
                        || status == android.os.BatteryManager.BATTERY_STATUS_FULL);
            }

            // 屏幕密度与语言、开机时长
            android.util.DisplayMetrics dm = context.getResources().getDisplayMetrics();
            info.put("screen_density", dm.densityDpi + "dpi");
            info.put("locale", java.util.Locale.getDefault().toString());
            info.put("uptime_days", (long) (android.os.SystemClock.elapsedRealtime() / 86400000L));
        } catch (Throwable ignored) {
            // 设备信息采集失败不应阻断反馈提交
        }
        return info;
    }

    /** 附加上下文信息（App 版本名/版本号），与设备信息合并 */
    public static Map<String, Object> collectWithApp(Context context) {
        Map<String, Object> info = collect(context);
        try {
            android.content.pm.PackageInfo pi = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0);
            info.put("app_package", context.getPackageName());
            info.put("app_version_name", pi.versionName);
            info.put("app_version_code", pi.versionCode);
        } catch (Throwable ignored) {
        }
        return info;
    }

    private static String join(String[] arr) {
        if (arr == null || arr.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < arr.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(arr[i]);
        }
        return sb.toString();
    }

    private static String screenPx(Context context) {
        try {
            android.util.DisplayMetrics dm = context.getResources().getDisplayMetrics();
            return dm.widthPixels + "x" + dm.heightPixels;
        } catch (Throwable t) {
            return "";
        }
    }
}
