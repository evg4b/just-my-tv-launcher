package com.justmytv.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class AerialsTest {
    private static final String JSON = "[{\"t\":\"a\",\"u\":\"https://sylvan.apple.com/x.mov\",\"s\":\"A\"},"
            + "{\"t\":\"z\",\"u\":\"https://cdn.example/y.mp4\",\"s\":\"Z\"},"
            + "{\"t\":\"c\",\"u\":\"https://github.com/z.mp4\",\"s\":\"C1\"},"
            + "{\"t\":\"h\",\"u\":\"http://plain/x.mp4\",\"s\":\"Z\"}]";

    @Test public void keepsAppleAndAmazonOverHttpsOnly() {
        assertEquals(Arrays.asList("https://sylvan.apple.com/x.mov", "https://cdn.example/y.mp4"), Aerials.parse(JSON, false));
        assertEquals(Collections.singletonList("https://cdn.example/y.mp4"), Aerials.parse(JSON, true));
        assertTrue(Aerials.parse("not json", false).isEmpty());
    }

    @Test public void neverTheSameTwiceInARow() {
        List<String> urls = Arrays.asList("a", "b", "c");
        Random r = new Random(1);
        String prev = "a";
        for (int i = 0; i < 200; i++) {
            String n = Aerials.next(urls, prev, r);
            assertNotEquals(prev, n);
            prev = n;
        }
        assertEquals("a", Aerials.next(Collections.singletonList("a"), "a", r));
        assertNull(Aerials.next(Collections.emptyList(), null, r));
    }

    @Test public void bundledListParses() throws Exception {
        try (java.io.InputStream in = new java.io.FileInputStream("src/main/res/raw/aerials.json")) {
            String json = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            assertEquals(251, Aerials.parse(json, false).size());   // 139 Apple + 112 Amazon
            assertEquals(112, Aerials.parse(json, true).size());
        }
    }
}
