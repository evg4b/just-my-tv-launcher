package com.evg4b.justmytv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class PhotoWallpaperTest {
    @Test public void readsTheRandomPhotoReply() {
        PhotoWallpaper.Photo p = PhotoWallpaper.parse("{\"id\":\"abc\",\"urls\":{\"raw\":\"https://images.unsplash.com/photo-1?ixid=x\"},"
                + "\"user\":{\"name\":\" André Spieker \"},"
                + "\"links\":{\"download_location\":\"https://api.unsplash.com/photos/abc/download?ixid=x\"}}");
        assertEquals("https://images.unsplash.com/photo-1?ixid=x&w=1920&h=1080&fit=crop&fm=jpg&q=80", p.image);
        assertEquals("André Spieker", p.author);
        assertEquals("https://api.unsplash.com/photos/abc/download?ixid=x", p.downloadLocation);
    }

    @Test public void rejectsWhatCannotBeShown() {
        assertNull(PhotoWallpaper.parse(null));
        assertNull(PhotoWallpaper.parse("not json"));
        assertNull(PhotoWallpaper.parse("{\"urls\":{\"raw\":\"http://plain/x\"}}"));
        PhotoWallpaper.Photo p = PhotoWallpaper.parse("{\"urls\":{\"raw\":\"https://images.unsplash.com/p\"},"
                + "\"links\":{\"download_location\":\"https://elsewhere.example/x\"}}");
        assertEquals("https://images.unsplash.com/p?w=1920&h=1080&fit=crop&fm=jpg&q=80", p.image);
        assertEquals("", p.author);
        assertEquals("", p.downloadLocation);   // the key is sent to api.unsplash.com only
    }
}
