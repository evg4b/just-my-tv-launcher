package com.justmytv.tvhome;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Launchable apps, leanback first, one per package, in install order (PLAN 9.3). Slow: never on the main thread. */
final class AppScanner {
    private AppScanner() { }

    static List<App> scan(Context ctx) {
        PackageManager pm = ctx.getPackageManager();
        Map<String, App> out = new LinkedHashMap<>();
        for (String cat : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            boolean leanback = cat.equals(Intent.CATEGORY_LEANBACK_LAUNCHER);
            for (ResolveInfo ri : pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(cat), 0)) {
                ActivityInfo ai = ri.activityInfo;
                if (ai == null || !ai.exported) continue;
                String pkg = ai.packageName;
                if (pkg.equals(ctx.getPackageName()) || out.containsKey(pkg)) continue;
                long first = 0, upd = 0;
                try {
                    PackageInfo pi = pm.getPackageInfo(pkg, 0);
                    first = pi.firstInstallTime;
                    upd = pi.lastUpdateTime;
                } catch (PackageManager.NameNotFoundException ignored) { }
                out.put(pkg, new App(ai, String.valueOf(ri.loadLabel(pm)), leanback, first, upd));
            }
        }
        List<App> list = new ArrayList<>(out.values());
        list.sort((x, y) -> Long.compare(x.firstInstall, y.firstInstall));   // new apps go last
        return list;
    }
}
