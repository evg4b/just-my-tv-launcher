package com.justmytv.tvhome;

import android.app.Activity;
import android.os.Bundle;

/** Home screen. M0: an empty window showing the default poster (theme background). */
public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        // Home has nowhere to go back to; finishing would only restart it with a flash.
    }
}
