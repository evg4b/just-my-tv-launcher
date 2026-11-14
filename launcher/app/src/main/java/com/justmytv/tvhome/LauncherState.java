package com.justmytv.tvhome;

import android.content.Context;
import android.util.AtomicFile;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** launcher_state.json through AtomicFile: a TV switched off mid-write keeps the old file (PLAN 9.2). No logic. */
final class LauncherState {
    private final AtomicFile file;

    LauncherState(Context ctx) { file = new AtomicFile(new File(ctx.getFilesDir(), "launcher_state.json")); }

    /** A missing file is an empty string; StateJson.parse turns that into the defaults. */
    String read() {
        try {
            return new String(file.readFully(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    void write(String json) {
        FileOutputStream out = null;
        try {
            out = file.startWrite();
            out.write(json.getBytes(StandardCharsets.UTF_8));
            file.finishWrite(out);
        } catch (IOException e) {
            if (out != null) file.failWrite(out);
            Log.w("LauncherState", "write failed", e);
        }
    }
}
