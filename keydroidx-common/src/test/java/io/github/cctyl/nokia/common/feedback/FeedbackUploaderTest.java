package io.github.cctyl.nokia.common.feedback;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class FeedbackUploaderTest {

    @Test
    public void testHexConversion() {
        byte[] original = new byte[]{0x00, 0x01, 0x0f, 0x10, (byte) 0xaa, (byte) 0xff};
        String hex = FeedbackUploader.bytesToHex(original);
        assertEquals("00010f10aaff", hex);
        byte[] parsed = FeedbackUploader.hexToBytes(hex);
        assertArrayEquals(original, parsed);
    }

    @Test
    public void testComputeAccessKey() throws Exception {
        byte[] secretKey = "test-secret-key-12345".getBytes(StandardCharsets.UTF_8);
        long timestamp = 1712345678900L;
        String nonce = "a1b2c3d4e5f60718293a4b5c6d7e8f90";

        String accessKey = FeedbackUploader.computeAccessKey(secretKey, timestamp, nonce);
        assertNotNull(accessKey);
        assertEquals(64, accessKey.length()); // SHA256 hex is 64 chars

        // Verify with Java standard Mac directly
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secretKey, "HmacSHA256"));
        byte[] msg = (timestamp + ":" + nonce).getBytes(StandardCharsets.UTF_8);
        byte[] expectedBytes = mac.doFinal(msg);
        assertEquals(FeedbackUploader.bytesToHex(expectedBytes), accessKey);
    }

    @Test
    public void testBuildMetaJson() throws Exception {
        Map<String, Object> extras = new LinkedHashMap<>();
        extras.put("brand", "Nokia");
        extras.put("api_level", 28);
        extras.put("is_root", false);

        String json = FeedbackUploader.buildMetaJson(
                "demoApp",
                "1.0.0",
                "user@example.com",
                "App crashed when opening menu",
                extras
        );

        assertTrue(json.contains("\"app\":\"demoApp\""));
        assertTrue(json.contains("\"app_version\":\"1.0.0\""));
        assertTrue(json.contains("\"contact\":\"user@example.com\""));
        assertTrue(json.contains("\"comment\":\"App crashed when opening menu\""));
        assertTrue(json.contains("\"brand\":\"Nokia\""));
        assertTrue(json.contains("\"api_level\":28"));
        assertTrue(json.contains("\"is_root\":false"));
    }
}
