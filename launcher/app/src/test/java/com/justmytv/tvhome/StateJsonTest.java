package com.justmytv.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;

public class StateJsonTest {
    @Test public void emptyOrCorruptGivesDefaults() {
        for (String s : new String[]{"", "   ", "{not json", "[]", null}) {
            StateJson j = StateJson.parse(s);
            assertEquals(5, j.columns);
            assertEquals(0, j.rowsBelow);
            assertTrue(j.h24);
            assertTrue(j.showDate);
            assertTrue(j.order.isEmpty());
            assertFalse(j.hasCity());
        }
    }

    @Test public void roundTrip() {
        StateJson a = new StateJson();
        a.order = Arrays.asList("a", "b");
        a.hidden.add("b");
        a.columns = 6;
        a.h24 = false;
        a.city = "Berlin";
        a.lat = 52.52;
        a.lon = 13.41;
        a.fetchedAt = 1763000000000L;
        StateJson b = StateJson.parse(a.toJson());
        assertEquals(a.order, b.order);
        assertEquals(a.hidden, b.hidden);
        assertEquals(6, b.columns);
        assertFalse(b.h24);
        assertEquals("Berlin", b.city);
        assertEquals(13.41, b.lon, 1e-9);
        assertEquals(1763000000000L, b.fetchedAt);
    }

    @Test public void outOfRangeIsClamped() {
        StateJson j = StateJson.parse("{\"ui\":{\"columns\":99,\"rowsBelow\":-4,\"corner\":5}}");
        assertEquals(7, j.columns);
        assertEquals(0, j.rowsBelow);
        assertEquals(16, j.corner);
    }

    @Test public void unknownKeysSurvive() {
        StateJson j = StateJson.parse("{\"wallpaper\":{\"type\":\"video\",\"poster\":\"p.webp\"},\"ui\":{\"future\":1}}");
        String out = j.toJson();
        assertTrue(out, out.contains("\"poster\":\"p.webp\""));
        assertTrue(out, out.contains("\"future\":1"));
    }

    @Test public void remote() {
        assertEquals("org.smarttube.stable", new StateJson().remote.get("245"));
        StateJson j = StateJson.parse("{\"remote\":{\"242\":\"com.netflix.ninja\"}}");
        assertEquals(1, j.remote.size());                          // saved map replaces the default, so it can be cleared
        assertEquals("com.netflix.ninja", StateJson.parse(j.toJson()).remote.get("242"));
        assertTrue(StateJson.parse("{\"remote\":{}}").remote.isEmpty());
    }

    @Test public void wallpaper() {
        StateJson j = StateJson.parse("{\"wallpaper\":{\"type\":\"video\",\"uri\":\"content://x/1\",\"dim\":30}}");
        assertEquals("video", j.wallpaper);
        assertEquals("content://x/1", j.videoUri);
        assertEquals(20, j.dim);                                   // not on the 0/20/40/60 scale
        assertEquals("gradient", StateJson.parse("{\"wallpaper\":{\"type\":\"live\"}}").wallpaper);
        assertEquals("gradient", new StateJson().wallpaper);
    }
}
