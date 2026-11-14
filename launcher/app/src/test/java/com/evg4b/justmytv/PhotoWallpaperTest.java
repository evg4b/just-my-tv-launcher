package com.evg4b.justmytv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class PhotoWallpaperTest {
    @Test public void readsTheIdFromThePhotoAddress() {
        assertEquals("237", PhotoWallpaper.idFrom("/id/237/1920/1080.jpg"));
        assertNull(PhotoWallpaper.idFrom("/1920/1080.jpg"));
        assertNull(PhotoWallpaper.idFrom(null));
    }

    @Test public void readsTheAuthor() {
        assertEquals("André Spieker", PhotoWallpaper.authorFrom("{\"id\":\"237\",\"author\":\" André Spieker \"}"));
        assertNull(PhotoWallpaper.authorFrom("{\"id\":\"1\"}"));
        assertNull(PhotoWallpaper.authorFrom("not json"));
        assertNull(PhotoWallpaper.authorFrom(null));
    }
}
