package com.evg4b.justmytv;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ScreensaverModeTest {
    private static String of(String type, String videoUri) {
        StateJson st = new StateJson();
        st.wallpaper = type;
        st.videoUri = videoUri;
        return ScreensaverMode.of(st);
    }

    @Test public void mirrorsHome() {
        assertEquals("video", of("video", "content://x/clip.mp4"));
        assertEquals("aerials", of("aerials", ""));
        assertEquals("photos", of("photos", ""));
        assertEquals("gradient", of("gradient", ""));
    }

    @Test public void ownPictureAndMissingVideoBecomeTheGradient() {
        assertEquals("gradient", of("picture", ""));
        assertEquals("gradient", of("video", ""));
        assertEquals("gradient", of("something-newer", ""));
    }
}
