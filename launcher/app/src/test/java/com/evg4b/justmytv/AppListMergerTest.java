package com.evg4b.justmytv;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

public class AppListMergerTest {
    @Test public void newAppsGoLastAndUninstalledLeaveOrder() {
        AppListMerger.Result r = AppListMerger.merge(
                Arrays.asList("a", "b", "c"),
                new HashSet<>(Arrays.asList("a", "b", "c")),
                new HashSet<>(),
                Arrays.asList("a", "c", "d"));         // b removed, d new
        assertEquals(Arrays.asList("a", "c", "d"), r.order);
        assertEquals(Arrays.asList("a", "c", "d"), r.visible);
    }

    @Test public void hiddenStaysHidden() {
        AppListMerger.Result r = AppListMerger.merge(
                Arrays.asList("a", "b"),
                new HashSet<>(Arrays.asList("a", "b")),
                new HashSet<>(Collections.singletonList("a")),
                Arrays.asList("a", "b"));
        assertEquals(Arrays.asList("a", "b"), r.order);
        assertEquals(Collections.singletonList("b"), r.visible);
    }

    @Test public void reinstallGoesLast() {
        AppListMerger.Result r1 = AppListMerger.merge(
                Arrays.asList("b", "a", "c"),
                new HashSet<>(Arrays.asList("a", "b", "c")),
                new HashSet<>(),
                Arrays.asList("a", "c"));
        assertEquals(Arrays.asList("a", "c"), r1.order);
        AppListMerger.Result r2 = AppListMerger.merge(r1.order, r1.known, new HashSet<>(), Arrays.asList("a", "b", "c"));
        assertEquals(Arrays.asList("a", "c", "b"), r2.order);
    }

    @Test public void firstRunKeepsInstallOrder() {
        AppListMerger.Result r = AppListMerger.merge(Collections.emptyList(), new HashSet<>(), new HashSet<>(),
                Arrays.asList("x", "y"));
        assertEquals(Arrays.asList("x", "y"), r.order);
    }

    @Test public void reorderKeepsHiddenSlots() {
        // order a h b c, h hidden; the user moved c to the front of the visible list
        assertEquals(Arrays.asList("c", "h", "a", "b"),
                AppListMerger.reorder(Arrays.asList("a", "h", "b", "c"), Arrays.asList("c", "a", "b")));
    }
}
