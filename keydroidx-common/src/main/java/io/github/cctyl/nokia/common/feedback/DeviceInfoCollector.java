package io.github.cctyl.nokia.common.feedback;

import android.app.ActivityManager;
import android.content.Context;
import android.opengl.GLES20;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.provider.Settings;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;

/**
 * 设备信息采集器：为反馈上报的 extras 字段提供默认的设备信息。
 *
 * <p>仅读取系统公开 API，不涉及 IMEI/MAC 地址/位置等隐私敏感数据；
 * ANDROID_ID 为系统公开设备标识（按签名密钥+设备唯一，无额外权限）。</p>
 */
public final class DeviceInfoCollector {

    private DeviceInfoCollector() {
    }

    /**
     * 采集设备信息（品牌、型号、系统版本、内存、CPU 型号/核心/主频、GPU 渲染器、
     * Android 设备 ID 等）。
     *
     * @return 有序 map，直接作为反馈 meta 的 extras 使用
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

            // CPU 信息（核心数、型号、硬件平台、主频范围）
            collectCpuInfo(info);

            // GPU 信息（通过 EGL 查询 GL 渲染器/厂商/版本）
            collectGpuInfo(info);

            // Android 设备 ID（公开 API，按签名密钥+设备唯一，无隐私敏感）
            info.put("android_id", Settings.Secure.getString(
                    context.getContentResolver(), Settings.Secure.ANDROID_ID));
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

    /**
     * 采集 CPU 信息：核心数、型号、硬件平台、主频范围。
     * 读取 /proc/cpuinfo 与 /sys 文件系统，均为只读公开信息。
     */
    private static void collectCpuInfo(Map<String, Object> info) {
        try {
            info.put("cpu_cores", Runtime.getRuntime().availableProcessors());
        } catch (Throwable ignored) {
        }
        try {
            BufferedReader reader = new BufferedReader(new FileReader("/proc/cpuinfo"));
            try {
                String model = null;
                String hardware = null;
                String line;
                while ((line = reader.readLine()) != null) {
                    int idx = line.indexOf(':');
                    if (idx < 0) continue;
                    String key = line.substring(0, idx).trim();
                    String val = line.substring(idx + 1).trim();
                    if (val.isEmpty()) continue;
                    if (model == null && (key.equalsIgnoreCase("model name")
                            || key.equalsIgnoreCase("Processor"))) {
                        model = val;
                    }
                    if (hardware == null && key.equalsIgnoreCase("Hardware")) {
                        hardware = val;
                    }
                    if (model != null && hardware != null) break;
                }
                if (model != null) info.put("cpu_model", model);
                if (hardware != null) info.put("cpu_hardware", hardware);
            } finally {
                reader.close();
            }
        } catch (Throwable ignored) {
        }
        try {
            String maxFreq = readOneLine("/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq");
            if (maxFreq != null) {
                info.put("cpu_max_freq_mhz", Integer.parseInt(maxFreq.trim()) / 1000);
            }
            String minFreq = readOneLine("/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_min_freq");
            if (minFreq != null) {
                info.put("cpu_min_freq_mhz", Integer.parseInt(minFreq.trim()) / 1000);
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * 采集 GPU 信息：通过 EGL 创建临时上下文查询 GL_RENDERER / GL_VENDOR / GL_VERSION。
     * 若当前线程已有 EGL 上下文（如 GLSurfaceView）则跳过，避免破坏已有渲染状态。
     * 完成后立即销毁临时上下文，不占用 GPU 资源。
     */
    private static void collectGpuInfo(Map<String, Object> info) {
        EGL10 egl = null;
        EGLDisplay display = null;
        EGLContext context = null;
        EGLSurface surface = null;
        try {
            egl = (EGL10) EGLContext.getEGL();
            // 当前线程已有 EGL 上下文时跳过，避免覆盖已有渲染状态
            if (egl.eglGetCurrentContext() != EGL10.EGL_NO_CONTEXT) {
                return;
            }
            display = egl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);
            if (display == EGL10.EGL_NO_DISPLAY) return;
            int[] version = new int[2];
            if (!egl.eglInitialize(display, version)) return;

            int[] configSpec = {
                    EGL10.EGL_RED_SIZE, 5,
                    EGL10.EGL_GREEN_SIZE, 6,
                    EGL10.EGL_BLUE_SIZE, 5,
                    EGL10.EGL_DEPTH_SIZE, 0,
                    0x3040, 0x0004, // EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT
                    EGL10.EGL_NONE
            };
            EGLConfig[] configs = new EGLConfig[1];
            int[] numConfigs = new int[1];
            if (!egl.eglChooseConfig(display, configSpec, configs, 1, numConfigs)
                    || numConfigs[0] == 0) {
                return;
            }
            EGLConfig config = configs[0];

            int[] surfaceAttrs = {
                    EGL10.EGL_WIDTH, 1,
                    EGL10.EGL_HEIGHT, 1,
                    EGL10.EGL_NONE
            };
            surface = egl.eglCreatePbufferSurface(display, config, surfaceAttrs);
            if (surface == null || surface == EGL10.EGL_NO_SURFACE) return;

            int[] contextAttrs = {
                    0x3098, 2, // EGL_CONTEXT_CLIENT_VERSION, 2
                    EGL10.EGL_NONE
            };
            context = egl.eglCreateContext(display, config,
                    EGL10.EGL_NO_CONTEXT, contextAttrs);
            if (context == null || context == EGL10.EGL_NO_CONTEXT) return;

            egl.eglMakeCurrent(display, surface, surface, context);

            info.put("gpu_renderer", GLES20.glGetString(GLES20.GL_RENDERER));
            info.put("gpu_vendor", GLES20.glGetString(GLES20.GL_VENDOR));
            info.put("gpu_version", GLES20.glGetString(GLES20.GL_VERSION));
        } catch (Throwable ignored) {
        } finally {
            if (egl != null && display != null) {
                try {
                    egl.eglMakeCurrent(display, EGL10.EGL_NO_SURFACE,
                            EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_CONTEXT);
                } catch (Throwable ignored) {
                }
                try {
                    if (surface != null) egl.eglDestroySurface(display, surface);
                } catch (Throwable ignored) {
                }
                try {
                    if (context != null) egl.eglDestroyContext(display, context);
                } catch (Throwable ignored) {
                }
                try {
                    egl.eglTerminate(display);
                } catch (Throwable ignored) {
                }
            }
        }
    }

    /** 读取文件首行（用于 /sys 下的 CPU 频率等单值文件） */
    private static String readOneLine(String path) {
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(path));
            return reader.readLine();
        } catch (Throwable t) {
            return null;
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }
}
