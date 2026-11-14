package com.evg4b.justmytv;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class WeatherCodeTest {
    @Test public void wmoMapping() {
        assertEquals("wx_sun", WeatherCode.icon(0, true));
        assertEquals("wx_moon", WeatherCode.icon(0, false));
        assertEquals("wx_moon_cloud", WeatherCode.icon(2, false));
        assertEquals("wx_rain_heavy", WeatherCode.icon(82, true));   // violent rain showers
        assertEquals("wx_snow", WeatherCode.icon(86, true));         // heavy snow showers
        assertEquals("wx_storm", WeatherCode.icon(97, true));        // heavy thunderstorm
        assertEquals("wx_hail", WeatherCode.icon(99, true));
        assertEquals("wx_unknown", WeatherCode.icon(42, true));      // unknown code
    }
}
