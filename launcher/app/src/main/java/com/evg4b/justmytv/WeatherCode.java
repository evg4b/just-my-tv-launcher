package com.evg4b.justmytv;

/** WMO weather code to our icon (PLAN 4.4). Pure, JVM-tested. */
final class WeatherCode {
    private WeatherCode() { }

    /** Icon name (res/drawable/wx_*.xml). Night has its own branch. */
    static String icon(int code, boolean isDay) {
        switch (code) {
            case 0: return isDay ? "wx_sun" : "wx_moon";
            case 1: case 2: return isDay ? "wx_sun_cloud" : "wx_moon_cloud";
            case 3: return "wx_cloud";
            case 45: case 48: return "wx_fog";
            case 51: case 53: case 55: return "wx_drizzle";
            case 56: case 57: case 66: case 67: return "wx_sleet";
            case 61: case 63: case 80: case 81: return "wx_rain";
            case 65: case 82: return "wx_rain_heavy";
            case 71: case 73: case 75: case 77: case 85: case 86: return "wx_snow";
            case 95: case 97: return "wx_storm";
            case 96: case 99: return "wx_hail";
            default: return "wx_unknown";
        }
    }

    /** The drawable for an icon name; a switch, not getIdentifier, so the resource shrinker sees every icon. */
    static int res(String icon) {
        switch (icon) {
            case "wx_sun": return R.drawable.wx_sun;
            case "wx_moon": return R.drawable.wx_moon;
            case "wx_sun_cloud": return R.drawable.wx_sun_cloud;
            case "wx_moon_cloud": return R.drawable.wx_moon_cloud;
            case "wx_cloud": return R.drawable.wx_cloud;
            case "wx_fog": return R.drawable.wx_fog;
            case "wx_drizzle": return R.drawable.wx_drizzle;
            case "wx_sleet": return R.drawable.wx_sleet;
            case "wx_rain": return R.drawable.wx_rain;
            case "wx_rain_heavy": return R.drawable.wx_rain_heavy;
            case "wx_snow": return R.drawable.wx_snow;
            case "wx_storm": return R.drawable.wx_storm;
            case "wx_hail": return R.drawable.wx_hail;
            default: return R.drawable.wx_unknown;
        }
    }
}
