package com.justmytv.tvhome;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.Uri;

import java.util.ArrayList;
import java.util.List;

/** HDMI and other passthrough inputs, and switching to one (PLAN 7.7). */
final class Inputs {
    static final String VIEW_INPUTS = "com.android.tv.action.VIEW_INPUTS";

    private Inputs() { }

    static final class Row {
        final String id, label;
        final boolean hdmi;
        final int state;   // TvInputManager.INPUT_STATE_*

        Row(String id, String label, boolean hdmi, int state) {
            this.id = id;
            this.label = label;
            this.hdmi = hdmi;
            this.state = state;
        }
    }

    static List<Row> list(Context ctx, boolean connectedOnly) {
        List<Row> out = new ArrayList<>();
        TvInputManager tim = (TvInputManager) ctx.getSystemService(Context.TV_INPUT_SERVICE);
        if (tim == null) return out;
        try {
            for (TvInputInfo info : tim.getTvInputList()) {
                if (!info.isPassthroughInput() || info.isHidden(ctx)) continue;   // hidden in Settings -> Inputs
                boolean hdmi = info.getType() == TvInputInfo.TYPE_HDMI;
                int state = tim.getInputState(info.getId());
                // TCL reports an empty HDMI port as connected-standby, so "connected only" means CONNECTED
                if (connectedOnly && hdmi && state != TvInputManager.INPUT_STATE_CONNECTED) continue;
                out.add(new Row(info.getId(), String.valueOf(info.loadLabel(ctx)), hdmi, state));
            }
        } catch (RuntimeException e) {
            // a TV input service that fails to answer: no inputs rather than a crash
        }
        return out;
    }

    /** First the TV app (on this TV com.tcl.tv); otherwise whatever handles the channel URI. False if nothing did. */
    static boolean switchTo(Context ctx, String inputId) {
        Uri channel = TvContract.buildChannelUriForPassthroughInput(inputId);
        Intent view = new Intent(Intent.ACTION_VIEW)
                .setDataAndType(channel, "vnd.android.cursor.item/channel")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            ctx.startActivity(new Intent(view).setPackage("com.tcl.tv"));
            return true;
        } catch (ActivityNotFoundException | SecurityException ignored) { }
        try {
            ctx.startActivity(view);   // may show the app chooser
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            return false;
        }
    }
}
