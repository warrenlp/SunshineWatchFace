package edu.warren.sunshine.sunshinewatchface;

import android.content.Context;
import android.graphics.Color;
import android.net.Uri;
import android.util.Log;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.Wearable;

/**
 * Created by Warren on 4/16/2016.
 */
public class SunshineWatchFaceUtil {
    private static final String TAG = "SunshineWatchFaceUtil";

    /**
     * The path for the {@link DataItem} containing {@link SunshineWatchFace} configuration.
     */
    public static final String SUNSHINE_PATH_FEATURE = "/sunshine_watch_face_config";

    /**
     * The {@link DataMap} key for {@link SunshineWatchFace} weather image.
     * The image name must be a {@link String} recognized by {@link WeatherBitmap}.
     */
    public static final String KEY_WEATHER_IMAGE = "WEATHER_IMAGE";

    /**
     * The {@link DataMap} key for {@link SunshineWatchFace} high temperature.
     */
    public static final String KEY_HIGH_TEMP = "HIGH_TEMP";

    /**
     * The {@link DataMap} key for {@link SunshineWatchFace} low temperature.
     */
    public static final String KEY_LOW_TEMP = "LOW_TEMP";

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

    // An impossibly low temperature so that the display shows a known default String version
    public static final int INT_VALUE_DEFAULT_TEMPERATURE = -1000;
    public static final String STRING_VALUE_DEFAULT_TEMPERATURE = "--";

    private static int parseColor(int[] rgbargs) {
        return  Color.rgb(rgbargs[0], rgbargs[1], rgbargs[2]);
    }

    /**
     * Callback interface to perform an action with the current config {@link DataMap} for
     * {@link SunshineWatchFace}.
     */
    public interface FetchConfigDataMapCallback {
        /**
         * Callback invoked with the current config {@link DataMap} for
         * {@link SunshineWatchFace}.
         */
        void onConfigDataMapFetched(DataMap config);
    }

    private static int parseColor(String colorName) {
        return Color.parseColor(colorName.toLowerCase());
    }

    public static String formatTemperature(Context context, double temperature) {
        // For presentation, assume the user doesn't care about tenths of a degree.
        return String.format(context.getString(R.string.format_temperature), temperature);
    }

    /**
     * Asynchronously fetches the current config {@link DataMap} for {@link SunshineWatchFace}
     * and passes it to the given callback.
     * <p>
     * If the current config {@link DataItem} doesn't exist, it isn't created and the callback
     * receives an empty DataMap.
     */
    public static void fetchConfigDataMap(final GoogleApiClient client,
                                          final FetchConfigDataMapCallback callback) {
        Wearable.NodeApi.getLocalNode(client).setResultCallback(
                new ResultCallback<NodeApi.GetLocalNodeResult>() {
                    @Override
                    public void onResult(NodeApi.GetLocalNodeResult getLocalNodeResult) {
                        String localNode = getLocalNodeResult.getNode().getId();
                        Uri uri = new Uri.Builder()
                                .scheme("wear")
                                .path(SunshineWatchFaceUtil.SUNSHINE_PATH_FEATURE)
                                .authority(localNode)
                                .build();
                        Wearable.DataApi.getDataItem(client, uri)
                                .setResultCallback(new DataItemResultCallback(callback));
                    }
                }
        );
    }

    /**
     * Overwrites the current config {@link DataItem}'s {@link DataMap} with {@code newConfig}.
     * If the config DataItem doesn't exist, it's created.
     */
    public static void putConfigDataItem(GoogleApiClient googleApiClient, DataMap newConfig) {
        PutDataMapRequest putDataMapRequest = PutDataMapRequest.create(SUNSHINE_PATH_FEATURE);
        putDataMapRequest.setUrgent();
        DataMap configToPut = putDataMapRequest.getDataMap();
        configToPut.putAll(newConfig);
        Wearable.DataApi.putDataItem(googleApiClient, putDataMapRequest.asPutDataRequest())
                .setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
                    @Override
                    public void onResult(DataApi.DataItemResult dataItemResult) {
                        if (Log.isLoggable(TAG, Log.DEBUG)) {
                            Log.d(TAG, "putDataItem result status: " + dataItemResult.getStatus());
                        }
                    }
                });
    }

    private static class DataItemResultCallback implements ResultCallback<DataApi.DataItemResult> {

        private final FetchConfigDataMapCallback mCallback;

        public DataItemResultCallback(FetchConfigDataMapCallback callback) {
            mCallback = callback;
        }

        @Override
        public void onResult(DataApi.DataItemResult dataItemResult) {
            if (dataItemResult.getStatus().isSuccess()) {
                if (dataItemResult.getDataItem() != null) {
                    DataItem configDataItem = dataItemResult.getDataItem();
                    DataMapItem dataMapItem = DataMapItem.fromDataItem(configDataItem);
                    DataMap config = dataMapItem.getDataMap();
                    mCallback.onConfigDataMapFetched(config);
                } else {
                    mCallback.onConfigDataMapFetched(new DataMap());
                }
            }
        }
    }

    public enum WeatherBitmap {

        CLEAR(0, R.drawable.art_clear),
        CLOUDS(1, R.drawable.art_clouds),
        FOG(2, R.drawable.art_fog),
        LIGHT_CLOUDS(3, R.drawable.art_light_clouds),
        LIGHT_RAIN(4, R.drawable.art_light_rain),
        RAIN(5, R.drawable.art_rain),
        SNOW(6, R.drawable.art_snow),
        STORM(7, R.drawable.art_storm);

        public final int value;
        public final int id;

        WeatherBitmap(int value, int id) {
            this.value = value;
            this.id = id;
        }

        public static int getDefaultValue() { return CLEAR.value; }
        public static int getDefaultID() { return CLEAR.id; }
    }

    /*
    * Ensure Singleton usage only with private constructor.
     */
    private SunshineWatchFaceUtil() { }
}
