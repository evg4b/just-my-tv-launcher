package com.evg4b.justmytv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class WeatherParserTest {
    private static String resource(String name) throws IOException {
        try (InputStream in = WeatherParserTest.class.getClassLoader().getResourceAsStream(name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test public void parsesCurrent() throws IOException {
        WeatherParser.Now now = WeatherParser.parseForecast(resource("forecast_berlin.json"));
        assertEquals(4, now.temp);          // 4.4 -> 4
        assertEquals("°C", now.unit);
        assertEquals(1, now.code);
        assertFalse(now.isDay);
        assertEquals("2026-11-14T00:15", now.time);
    }

    @Test public void errorBodyIsNull() {
        assertNull(WeatherParser.parseForecast("{\"error\":true,\"reason\":\"bad\"}"));
        assertNull(WeatherParser.parseForecast("not json"));
        assertNull(WeatherParser.parseForecast(null));
    }

    @Test public void noResultsKeyIsEmptyList() {
        assertTrue(WeatherParser.parseGeocode("{\"generationtime_ms\":0.16}").isEmpty());
    }

    @Test public void geocodeWithMissingFields() {
        List<WeatherParser.Place> p = WeatherParser.parseGeocode("{\"results\":[{\"id\":2950159,\"name\":\"Berlin\","
                + "\"latitude\":52.52437,\"longitude\":13.41053,\"country\":\"Germany\"}]}");
        assertEquals(1, p.size());
        assertEquals("Berlin", p.get(0).name);
        assertEquals("", p.get(0).admin1);
        assertEquals(13.41053, p.get(0).lon, 1e-9);
    }
}
