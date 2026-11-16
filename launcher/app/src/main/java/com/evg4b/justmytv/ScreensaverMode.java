package com.evg4b.justmytv;

/** The screensaver's wallpaper from Home's (D112, SCREENSAVER 2). Pure, JVM-tested. */
final class ScreensaverMode {
    private ScreensaverMode() { }

    /** "video", "aerials", "photos" as at Home; everything else, the user's own picture included, is "gradient". */
    static String of(StateJson st) {
        switch (st.wallpaper) {
            case "video": return st.videoUri.isEmpty() ? "gradient" : "video";
            case "aerials":
            case "photos": return st.wallpaper;
            default: return "gradient";
        }
    }
}
