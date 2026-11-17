package com.evg4b.justmytv;

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

    private static final java.util.Set<String> ALL = StateJson.allThemes();

    private static List<String> parse(String json, boolean skipApple) { return parse(json, skipApple, "", ALL); }

    private static List<String> parse(String json, boolean skipApple, String source, java.util.Set<String> themes) {
        return Aerials.filter(Aerials.clips(json), skipApple, source, themes);
    }

    @Test public void keepsAppleAndAmazonOverHttpsOnly() {
        assertEquals(Arrays.asList("https://sylvan.apple.com/x.mov", "https://cdn.example/y.mp4"), parse(JSON, false));
        assertEquals(Collections.singletonList("https://cdn.example/y.mp4"), parse(JSON, true));
        assertTrue(parse("not json", false).isEmpty());
    }

    @Test public void filtersByCollectionAndTheme() {
        String json = "[{\"u\":\"https://sylvan.apple.com/a.mov\",\"s\":\"A\",\"k\":\"city\"},"
                + "{\"u\":\"https://cdn.example/z.mp4\",\"s\":\"Z\",\"k\":\"water\"},"
                + "{\"u\":\"https://cdn.example/n.mp4\",\"s\":\"Z\"}]";   // no theme: landscapes
        java.util.Set<String> all = StateJson.allThemes();
        assertEquals(3, parse(json, false, "", all).size());
        assertEquals(Collections.singletonList("https://sylvan.apple.com/a.mov"), parse(json, false, "A", all));
        assertEquals(Collections.singletonList("https://cdn.example/n.mp4"),
                parse(json, false, "Z", Collections.singleton("nature")));
        assertTrue(parse(json, false, "A", Collections.singleton("water")).isEmpty());
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
            assertEquals(251, parse(json, false).size());   // 139 Apple + 112 Amazon
            assertEquals(112, parse(json, true).size());
            int sum = 0;   // every clip has exactly one theme
            for (String t : Aerials.THEMES) {
                int n = parse(json, false, "", Collections.singleton(t)).size();
                assertTrue(t, n > 0);
                sum += n;
            }
            assertEquals(251, sum);
        }
    }
}
