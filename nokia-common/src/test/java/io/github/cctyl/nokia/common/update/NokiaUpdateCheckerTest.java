package io.github.cctyl.nokia.common.update;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

/**
 * 检查更新纯逻辑单元测试：仓库地址解析、版本号比较、Release JSON 解析。
 */
public class NokiaUpdateCheckerTest {

    // ---------- resolveRepoPath ----------

    @Test
    public void repoPath_standardUrl() {
        assertEquals("cctyl/keydroidx-launcher",
                new NokiaUpdateConfig("https://github.com/cctyl/keydroidx-launcher").resolveRepoPath());
    }

    @Test
    public void repoPath_variants() {
        assertEquals("a/b", new NokiaUpdateConfig("github.com/a/b/").resolveRepoPath());
        assertEquals("a/b", new NokiaUpdateConfig("a/b").resolveRepoPath());
        assertEquals("a/b", new NokiaUpdateConfig("https://github.com/a/b.git").resolveRepoPath());
        assertEquals("a/b", new NokiaUpdateConfig("git@github.com:a/b.git").resolveRepoPath());
        assertEquals("a/b", new NokiaUpdateConfig("https://github.com/a/b/issues").resolveRepoPath());
    }

    @Test
    public void repoPath_invalid() {
        assertNull(new NokiaUpdateConfig("https://github.com/onlyone").resolveRepoPath());
        assertNull(new NokiaUpdateConfig("").resolveRepoPath());
        assertNull(new NokiaUpdateConfig(null).resolveRepoPath());
    }

    // ---------- compareVersion ----------

    @Test
    public void compare_basic() {
        assertEquals(1, NokiaUpdateChecker.compareVersion("1.2", "1.1"));
        assertEquals(-1, NokiaUpdateChecker.compareVersion("1.9", "1.10"));
        assertEquals(0, NokiaUpdateChecker.compareVersion("1.2", "1.2"));
        assertEquals(0, NokiaUpdateChecker.compareVersion("1.2", "1.2.0"));
    }

    @Test
    public void compare_vPrefixAndMixed() {
        assertEquals(0, NokiaUpdateChecker.compareVersion("v1.2.3", "1.2.3"));
        assertEquals(1, NokiaUpdateChecker.compareVersion("2.0", "1.9.9"));
        assertEquals(1, NokiaUpdateChecker.compareVersion("1.2.10", "1.2.9"));
    }

    @Test
    public void compare_prerelease() {
        // 正式版 > 预发布
        assertEquals(1, NokiaUpdateChecker.compareVersion("1.2.3", "1.2.3-beta"));
        assertEquals(-1, NokiaUpdateChecker.compareVersion("1.2.3-beta", "1.2.3"));
        assertEquals(1, NokiaUpdateChecker.compareVersion("1.2.4-beta", "1.2.3"));
        assertEquals(-1, NokiaUpdateChecker.compareVersion("1.2.3-rc1", "1.2.3-rc2"));
    }

    @Test
    public void compare_nullAndGarbage() {
        assertEquals(1, NokiaUpdateChecker.compareVersion("1.0", null));
        assertEquals(-1, NokiaUpdateChecker.compareVersion(null, "1.0"));
        assertEquals(0, NokiaUpdateChecker.compareVersion(null, null));
    }

    // ---------- parseRelease ----------

    @Test
    public void parseRelease_withApkAsset() throws Exception {
        JSONObject rel = new JSONObject("{"
                + "\"tag_name\":\"v1.4.0\","
                + "\"name\":\"Release 1.4\","
                + "\"html_url\":\"https://github.com/a/b/releases/tag/v1.4.0\","
                + "\"body\":\"  修复若干bug  \","
                + "\"published_at\":\"2026-09-01T00:00:00Z\","
                + "\"assets\":[{"
                + "  \"name\":\"app-release.apk\","
                + "  \"size\":123456,"
                + "  \"browser_download_url\":\"https://github.com/a/b/releases/download/v1.4.0/app-release.apk\""
                + "}]}");

        NokiaUpdateInfo info = NokiaUpdateChecker.parseRelease(rel, new NokiaUpdateConfig("a/b"));
        assertEquals("1.4.0", info.version);
        assertEquals("v1.4.0", info.tagName);
        assertEquals("https://github.com/a/b/releases/download/v1.4.0/app-release.apk", info.downloadUrl);
        assertEquals("app-release.apk", info.assetName);
        assertEquals(123456, info.assetSize);
        assertEquals("修复若干bug", info.changelog);
        assertTrue(info.isNewerThan("1.3.0"));
        assertFalse(info.isNewerThan("1.4.0"));
        assertEquals(info.downloadUrl, info.resolveDownloadUrl());
    }

    @Test
    public void parseRelease_noAsset_fallsBackToHtmlUrl() throws Exception {
        JSONObject rel = new JSONObject("{"
                + "\"tag_name\":\"v0.9.0\","
                + "\"html_url\":\"https://github.com/a/b/releases/tag/v0.9.0\""
                + "}");
        NokiaUpdateInfo info = NokiaUpdateChecker.parseRelease(rel, new NokiaUpdateConfig("a/b"));
        assertEquals("0.9.0", info.version);
        assertNull(info.downloadUrl);
        assertEquals("https://github.com/a/b/releases/tag/v0.9.0", info.resolveDownloadUrl());
    }

    @Test
    public void parseRelease_invalid() throws Exception {
        assertNull(NokiaUpdateChecker.parseRelease(new JSONObject("{}"), new NokiaUpdateConfig("a/b")));
    }
}
