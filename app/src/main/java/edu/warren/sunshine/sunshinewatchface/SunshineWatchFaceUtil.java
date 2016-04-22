package edu.warren.sunshine.sunshinewatchface;

import android.content.Context;
import android.graphics.Color;

/**
 * Created by Warren on 4/16/2016.
 */
public class SunshineWatchFaceUtil {

    /**
     * Name of the default interactive mode background color and the ambient mode background color.
     */
    public static final int[] COLOR_NAME_DEFAULT_AND_AMBIENT_BACKGROUND = {0x03, 0xA9, 0xF4};
    public static final int COLOR_VALUE_DEFAULT_AND_AMBIENT_BACKGROUND =
            parseColor(COLOR_NAME_DEFAULT_AND_AMBIENT_BACKGROUND);

    public static final int[] COLOR_NAME_DEFAULT_AND_AMBIENT_BACKGROUND_GROUND_TOP = {0x0, 0xFA, 0x00};
    public static final int COLOR_VALUE_DEFAULT_AND_AMBIENT_BACKGROUND_GROUND_TOP =
            parseColor(COLOR_NAME_DEFAULT_AND_AMBIENT_BACKGROUND_GROUND_TOP);

    public static final int[] COLOR_NAME_DEFAULT_AND_AMBIENT_BACKGROUND_GROUND_BOTTOM = {0x0, 0x80, 0x00};
    public static final int COLOR_VALUE_DEFAULT_AND_AMBIENT_BACKGROUND_GROUND_BOTTOM =
            parseColor(COLOR_NAME_DEFAULT_AND_AMBIENT_BACKGROUND_GROUND_BOTTOM);

    public static final String COLOR_NAME_TEMPERATURE = "white";
    public static final int DIGITAL_TEMPERATURE_COLOR = parseColor(COLOR_NAME_TEMPERATURE);

    public static final int[] DIGITAL_DATE_COLOR_RGB = {0xBB, 0xBB, 0xBB};
    public static final int DIGITAL_DATE_COLOR = parseColor(DIGITAL_DATE_COLOR_RGB);

    private static int parseColor(int[] rgbargs) {
        return  Color.rgb(rgbargs[0], rgbargs[1], rgbargs[2]);
    }

    private static int parseColor(String colorName) {
        return Color.parseColor(colorName.toLowerCase());
    }

    public static String formatTemperature(Context context, double temperature) {
        // For presentation, assume the user doesn't care about tenths of a degree.
        return String.format(context.getString(R.string.format_temperature), temperature);
    }
}
