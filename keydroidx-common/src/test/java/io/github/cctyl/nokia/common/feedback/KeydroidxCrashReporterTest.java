package io.github.cctyl.nokia.common.feedback;

import org.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.Map;

import io.github.cctyl.nokia.common.log.LogErrorMarker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * {@link KeydroidxCrashReporter} 标记文件的纯 JVM 单测（不依赖 Android Context）。
 */
public class KeydroidxCrashReporterTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void markPending_writesCrashDetails() throws Exception {
        File dir = folder.newFolder("log");
        RuntimeException e = new RuntimeException("boom");

        KeydroidxCrashReporter.markPending(dir, LogErrorMarker.CATEGORY_UNCAUGHT,
                "uncaught exception on thread [main]", e);

        File marker = new File(dir, KeydroidxCrashReporter.MARKER_NAME);
        assertTrue(marker.isFile());
        assertFalse("临时文件必须已被 rename 消费掉",
                new File(dir, KeydroidxCrashReporter.MARKER_NAME + ".tmp").exists());

        JSONObject json = KeydroidxCrashReporter.readJson(marker);
        assertNotNull(json);
        assertEquals(LogErrorMarker.CATEGORY_UNCAUGHT, json.getString("category"));
        assertEquals(RuntimeException.class.getName(), json.getString("type"));
        assertEquals("boom", json.getString("message"));
        assertEquals("uncaught exception on thread [main]", json.getString("detail"));
        assertTrue(json.getLong("ts") > 0);
        assertFalse(json.getString("time").isEmpty());
        assertFalse(json.getString("thread").isEmpty());
        assertTrue("堆栈必须落进标记文件（崩溃时当日日志可能来不及写）",
                json.getString("stack").contains("markPending_writesCrashDetails"));
    }

    @Test
    public void markPending_keepsFirstErrorButCrashOverwrites() throws Exception {
        File dir = folder.newFolder("log");

        KeydroidxCrashReporter.markPending(dir, LogErrorMarker.CATEGORY_ERROR, "Tag: first error", null);
        KeydroidxCrashReporter.markPending(dir, LogErrorMarker.CATEGORY_ERROR, "Tag: second error", null);

        File marker = new File(dir, KeydroidxCrashReporter.MARKER_NAME);
        JSONObject afterSecond = KeydroidxCrashReporter.readJson(marker);
        assertNotNull(afterSecond);
        assertEquals("同一会话内多次 ERROR 不应重复覆盖标记",
                "Tag: first error", afterSecond.getString("detail"));

        KeydroidxCrashReporter.markPending(dir, LogErrorMarker.CATEGORY_UNCAUGHT, "crash!",
                new IllegalStateException("x"));

        JSONObject afterCrash = KeydroidxCrashReporter.readJson(marker);
        assertNotNull(afterCrash);
        assertEquals("崩溃是更强的信号，必须覆盖升级已有标记",
                LogErrorMarker.CATEGORY_UNCAUGHT, afterCrash.getString("category"));
        assertEquals("crash!", afterCrash.getString("detail"));
    }

    @Test
    public void markPending_truncatesLongStack() throws Exception {
        File dir = folder.newFolder("log");
        StringBuilder longMsg = new StringBuilder(12000);
        for (int i = 0; i < 12000; i++) {
            longMsg.append('x');
        }

        KeydroidxCrashReporter.markPending(dir, LogErrorMarker.CATEGORY_UNCAUGHT, "long",
                new RuntimeException(longMsg.toString()));

        JSONObject json = KeydroidxCrashReporter.readJson(
                new File(dir, KeydroidxCrashReporter.MARKER_NAME));
        assertNotNull(json);
        assertEquals("堆栈必须截断，避免标记文件过大", 4000, json.getString("stack").length());
    }

    @Test
    public void markPending_ignoresNullDir() {
        // 不应抛异常（崩溃路径必须绝对安全）
        KeydroidxCrashReporter.markPending(null, LogErrorMarker.CATEGORY_ERROR, "x", null);
    }

    @Test
    public void buildComment_andExtras_describeCrash() throws Exception {
        JSONObject data = new JSONObject();
        data.put("v", 1);
        data.put("category", LogErrorMarker.CATEGORY_UNCAUGHT);
        data.put("type", "java.lang.NoSuchMethodError");
        data.put("message", "no virtual method");
        data.put("detail", "uncaught exception on thread [main]");
        data.put("time", "2026-09-12 20:31:05");
        data.put("thread", "main");
        data.put("process", "io.github.cctyl.nokia");

        String comment = KeydroidxCrashReporter.buildComment(data);
        assertTrue(comment.contains("崩溃"));
        assertTrue(comment.contains("java.lang.NoSuchMethodError"));
        assertTrue(comment.contains("main"));
        assertTrue("meta.comment 上限 500 字符", comment.length() <= 500);

        Map<String, Object> extras = KeydroidxCrashReporter.buildExtras(data);
        assertEquals(Boolean.TRUE, extras.get("auto_report"));
        assertEquals(LogErrorMarker.CATEGORY_UNCAUGHT, extras.get("crash_category"));
        assertEquals("java.lang.NoSuchMethodError", extras.get("crash_type"));
        assertEquals("io.github.cctyl.nokia", extras.get("crash_process"));
    }

    @Test
    public void buildComment_withoutMarkerData_stillValid() {
        String comment = KeydroidxCrashReporter.buildComment(null);
        assertNotNull(comment);
        assertTrue(comment.contains("自动上报"));
        assertTrue(comment.length() <= 500);

        Map<String, Object> extras = KeydroidxCrashReporter.buildExtras(null);
        assertEquals(Boolean.TRUE, extras.get("auto_report"));
    }

    /**
     * 服务端 comment 上限是 500 <b>字节</b>（Rust s.len()），超限会掐断 TCP；
     * 这里用真实崩溃里那种「长中文 + 长类名」的异常消息验证不会越界。
     */
    @Test
    public void buildComment_neverExceedsServerByteLimit() throws Exception {
        StringBuilder msg = new StringBuilder();
        for (int i = 0; i < 60; i++) {
            msg.append("手动触发的测试未捕获异常");
        }
        msg.append(" Unable to start activity ComponentInfo{io.github.cctyl.nokia.debug")
                .append("/ru.playsoftware.j2meloader.nokia.KeydroidxDesktopActivity}");

        JSONObject data = new JSONObject();
        data.put("category", LogErrorMarker.CATEGORY_UNCAUGHT);
        data.put("type", "java.lang.RuntimeException");
        data.put("message", msg.toString());
        data.put("detail", "uncaught exception on thread [main]");
        data.put("time", "2026-09-12 09:56:15");
        data.put("thread", "main");
        data.put("process", "io.github.cctyl.nokia.debug");

        String comment = KeydroidxCrashReporter.buildComment(data);
        int bytes = KeydroidxCrashReporter.utf8Length(comment);
        assertTrue("comment 必须 ≤500 字节（服务端上限），实际 " + bytes + " 字节", bytes <= 500);
        assertTrue(comment.contains("java.lang.RuntimeException"));
        assertTrue(comment.contains("pending_report.json"));
    }

    @Test
    public void truncateUtf8_doesNotSplitMultibyteChar() {
        String s = "ab中文😀cd";
        assertEquals("ab", KeydroidxCrashReporter.truncateUtf8(s, 2));
        assertEquals("ab中", KeydroidxCrashReporter.truncateUtf8(s, 5));
        assertEquals(s, KeydroidxCrashReporter.truncateUtf8(s, 100));
        assertEquals("", KeydroidxCrashReporter.truncateUtf8(null, 10));
        assertEquals(0, KeydroidxCrashReporter.utf8Length(null));
        // 中文 3 字节/字，与 String.length()（字符数）不同：这正是本次失败的原因
        assertEquals(12, KeydroidxCrashReporter.utf8Length("ab中文😀"));
    }
}
