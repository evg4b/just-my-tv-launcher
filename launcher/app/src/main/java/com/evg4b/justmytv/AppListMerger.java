package com.evg4b.justmytv;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** The saved order and hiding applied to a scan (PLAN 9.3). Pure, JVM-tested. */
final class AppListMerger {
    private AppListMerger() { }

    static final class Result {
        final List<String> order;     // full order, hidden apps included
        final Set<String> known;      // = currently installed
        final List<String> visible;   // order minus hidden

        Result(List<String> o, Set<String> k, List<String> v) { order = o; known = k; visible = v; }
    }

    /**
     * order: the saved order; known: installed at the last scan; hidden: hidden apps;
     * installed: installed now, in install order (new ones at the end).
     */
    static Result merge(List<String> order, Set<String> known, Set<String> hidden, List<String> installed) {
        Set<String> inst = new LinkedHashSet<>(installed);
        LinkedHashSet<String> next = new LinkedHashSet<>();
        for (String p : order) if (inst.contains(p)) next.add(p);       // keep the order, drop removed apps
        for (String p : installed) if (!known.contains(p)) next.add(p);  // new (and reinstalled) ones go to the end
        for (String p : installed) next.add(p);                          // installed but somehow missing: end too
        List<String> visible = new ArrayList<>();
        for (String p : next) if (!hidden.contains(p)) visible.add(p);
        return new Result(new ArrayList<>(next), new HashSet<>(inst), visible);
    }

    /** After a Move: the visible apps take the visible slots in their new sequence; hidden apps keep their slots. */
    static List<String> reorder(List<String> order, List<String> visibleNew) {
        Set<String> moved = new HashSet<>(visibleNew);
        Iterator<String> it = visibleNew.iterator();
        List<String> out = new ArrayList<>(order.size());
        for (String p : order) out.add(moved.contains(p) && it.hasNext() ? it.next() : p);
        while (it.hasNext()) out.add(it.next());
        return out;
    }
}
