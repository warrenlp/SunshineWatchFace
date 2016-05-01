/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package edu.warren.sunshine.sunshinewatchface;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.DateFormat;
import android.text.format.Time;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Wearable;

import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class SunshineWatchFace extends CanvasWatchFaceService {
    private static final String TAG = "SunshineWatchFace";

    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

    /**
     * Update rate in milliseconds for interactive mode. We update once a second since seconds are
     * displayed in interactive mode.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    // It would be useful to not have hard-coded values here but instead to use an api that can
    // pull the actual sunrise and sunset times for a given time-zone and latitude.
    // https://github.com/mikereedell/sunrisesunsetlib-java
    private static final float END_TIME = 21.5f;
    private static final float START_TIME = 7f;
    private static final float HIGH_ANGLE = 200f;
    private static final float LOW_ANGLE = -20f;
    private static final float DRAWN_RADIUS = 110f;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<SunshineWatchFace.Engine> mWeakReference;

        public EngineHandler(SunshineWatchFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            SunshineWatchFace.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    private class Engine extends CanvasWatchFaceService.Engine implements DataApi.DataListener,
            GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {
        final Handler mUpdateTimeHandler = new EngineHandler(this);
        boolean mRegisteredTimeZoneReceiver = false;
        Paint mBackgroundPaint;
        Paint mBackgroundGroundPaint;
        private Bitmap mWeatherBitmap;
        private String mHighTemp = SunshineWatchFaceUtil.STRING_VALUE_DEFAULT_TEMPERATURE;
        private String mLowTemp = SunshineWatchFaceUtil.STRING_VALUE_DEFAULT_TEMPERATURE;
        Paint mTextPaint;
        Paint mDatePaint;
        Paint mTemperaturePaint;
        boolean mAmbient;
        Time mTime;
        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mTime.clear(intent.getStringExtra("time-zone"));
                mTime.setToNow();
            }
        };

        GoogleApiClient mGoogleApiClient = new GoogleApiClient.Builder(SunshineWatchFace.this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(Wearable.API)
                .build();

        int mTapCount;

        Calendar mCalendar;
        Date mDate;
        SimpleDateFormat mDayOfWeekFormat;
        java.text.DateFormat mDateFormat;

        float mXOffset;
        float mYOffset;
        float mLineHeight;
        float mHorizonOffset;

        int mInteractiveBackgroundColor =
                SunshineWatchFaceUtil.COLOR_VALUE_DEFAULT_AND_AMBIENT_BACKGROUND;
        int mInteractiveBackgroundGroundTopColor =
                SunshineWatchFaceUtil.COLOR_VALUE_DEFAULT_AND_AMBIENT_BACKGROUND_GROUND_TOP;
        int mInteractiveBackgroundGroundBottomColor =
                SunshineWatchFaceUtil.COLOR_VALUE_DEFAULT_AND_AMBIENT_BACKGROUND_GROUND_BOTTOM;

        int mTemperatureColor = SunshineWatchFaceUtil.DIGITAL_TEMPERATURE_COLOR;
        int mDigitalDateColor = SunshineWatchFaceUtil.DIGITAL_DATE_COLOR;

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(SunshineWatchFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .setAcceptsTapEvents(true)
                    .build());
            Resources resources = SunshineWatchFace.this.getResources();
            mYOffset = resources.getDimension(R.dimen.digital_y_offset);
            mLineHeight = resources.getDimension(R.dimen.digital_line_height);
            mHorizonOffset = resources.getDimension(R.dimen.horizon_offset);
            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(mInteractiveBackgroundColor);
            mBackgroundGroundPaint = new Paint();
            mTextPaint = new Paint();
            mTextPaint = createTextPaint(resources.getColor(R.color.digital_text));
            mDatePaint = createTextPaint(mDigitalDateColor);
            mTemperaturePaint = createTextPaint(mTemperatureColor);

            mWeatherBitmap = BitmapFactory.decodeResource(getResources(),
                    SunshineWatchFaceUtil.WeatherBitmap.getDefaultID());
            mWeatherBitmap = Bitmap.createScaledBitmap(mWeatherBitmap,
                    (int) (mWeatherBitmap.getWidth() * 0.5),
                    (int) (mWeatherBitmap.getHeight() * 0.5), true);

            mTime = new Time();

            mCalendar = Calendar.getInstance();
            mDate = new Date();
            initFormats();
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        private Paint createTextPaint(int textColor) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setTypeface(NORMAL_TYPEFACE);
            paint.setAntiAlias(true);
            return paint;
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();

                // Update time zone in case it changed while we weren't visible.
                mTime.clear(TimeZone.getDefault().getID());
                mTime.setToNow();
            } else {
                unregisterReceiver();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void initFormats() {
            mDayOfWeekFormat = new SimpleDateFormat("EEE", Locale.getDefault());
            mDayOfWeekFormat.setCalendar(mCalendar);
            mDateFormat = DateFormat.getMediumDateFormat(SunshineWatchFace.this);
            mDateFormat.setCalendar(mCalendar);
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            SunshineWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            SunshineWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = SunshineWatchFace.this.getResources();
            boolean isRound = insets.isRound();
            mXOffset = resources.getDimension(isRound
                    ? R.dimen.digital_x_offset_round : R.dimen.digital_x_offset);
            float temperatureSize = resources.getDimension(isRound
                    ? R.dimen.digital_temperature_size_round : R.dimen.digital_temperature_size);
            float textSize = resources.getDimension(isRound
                    ? R.dimen.digital_text_size_round : R.dimen.digital_text_size);

            mTemperaturePaint.setTextSize(temperatureSize);
            mTextPaint.setTextSize(textSize);
            mDatePaint.setTextSize(resources.getDimension(R.dimen.digital_date_text_size));
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                if (mLowBitAmbient) {
                    mTextPaint.setAntiAlias(!inAmbientMode);
                }
                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        /**
         * Captures tap event (and tap type) and toggles the background color if the user finishes
         * a tap.
         */
        @Override
        public void onTapCommand(int tapType, int x, int y, long eventTime) {
            Resources resources = SunshineWatchFace.this.getResources();
            switch (tapType) {
                case TAP_TYPE_TOUCH:
                    // The user has started touching the screen.
                    break;
                case TAP_TYPE_TOUCH_CANCEL:
                    // The user has started a different gesture or otherwise cancelled the tap.
                    break;
                case TAP_TYPE_TAP:
                    // The user has completed the tap gesture.
                    setInteractiveWeatherImage(mTapCount);
                    mTapCount++;
                    if (mTapCount == SunshineWatchFaceUtil.WeatherBitmap.values().length) {
                        mTapCount = 0;
                    }
                    break;
            }
            invalidate();
        }

        private void setInteractiveWeatherImage(int imageId) {
            if (!isInAmbientMode()) {
                SunshineWatchFaceUtil.WeatherBitmap weatherBitmap = null;

                for (SunshineWatchFaceUtil.WeatherBitmap wbmap : SunshineWatchFaceUtil.WeatherBitmap.values()) {
                    if (imageId == wbmap.value) {
                        weatherBitmap = wbmap;
                        break;
                    }
                }

                if (weatherBitmap != null) {
                    mWeatherBitmap = BitmapFactory.decodeResource(getResources(), weatherBitmap.id);
                    mWeatherBitmap = Bitmap.createScaledBitmap(mWeatherBitmap,
                            (int) (mWeatherBitmap.getWidth() * 0.5),
                            (int) (mWeatherBitmap.getHeight() * 0.5), true);
                }
            }
        }

        private void setInteractiveHighTemperature(int temp) {
            if (!isInAmbientMode()) {
                if (temp <= SunshineWatchFaceUtil.INT_VALUE_DEFAULT_TEMPERATURE) {
                    mHighTemp = SunshineWatchFaceUtil.STRING_VALUE_DEFAULT_TEMPERATURE;
                } else {
                    // Temps are stored as a whole integer and need conversion to shifted double
                    mHighTemp = SunshineWatchFaceUtil.formatTemperature(SunshineWatchFace.this, (double)temp * 0.1);
                }
            }
        }

        private void setInteractiveLowTemperature(int temp) {
            if (!isInAmbientMode()) {
                if (temp <= SunshineWatchFaceUtil.INT_VALUE_DEFAULT_TEMPERATURE) {
                    mLowTemp = SunshineWatchFaceUtil.STRING_VALUE_DEFAULT_TEMPERATURE;
                } else {
                    // Temps are stored as a whole integer and need conversion to shifted double
                    mLowTemp = SunshineWatchFaceUtil.formatTemperature(SunshineWatchFace.this, (double)temp * 0.1);
                }
            }
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis(now);

            int xCenter = bounds.centerX();
            int yCenter = bounds.centerX();
            int width = bounds.width();
            int height = bounds.height();

            // Draw the background.
            if (isInAmbientMode()) {
                canvas.drawColor(Color.BLACK);
            } else {
                canvas.drawRect(0, 0, width, height, mBackgroundPaint);
                mBackgroundGroundPaint.setShader(new LinearGradient(xCenter, yCenter, xCenter, height,
                        mInteractiveBackgroundGroundTopColor, mInteractiveBackgroundGroundBottomColor,
                        Shader.TileMode.MIRROR));
                drawWeatherImage(canvas, bounds);
                canvas.drawRect(0, bounds.width() * 0.5f, bounds.width(), bounds.height(),
                        mBackgroundGroundPaint);
            }

            // Draw the Temperature
            String temperature = mHighTemp + " " + mLowTemp;
            float halfTempX = mTemperaturePaint.measureText(temperature) * 0.5f;
            canvas.drawText(temperature, xCenter-halfTempX, yCenter - mHorizonOffset, mTemperaturePaint);

            // Draw H:MM in ambient mode or H:MM:SS in interactive mode.
            mTime.setToNow();
            String text = mAmbient
                    ? String.format("%d:%02d", mTime.hour, mTime.minute)
                    : String.format("%d:%02d:%02d", mTime.hour, mTime.minute, mTime.second);
            float halfTextX = mTextPaint.measureText(text) * 0.5f;
            float textY = mTextPaint.getTextSize();
            int textYOffset = yCenter + (int) textY;
            canvas.drawText(text, xCenter-halfTextX, textYOffset, mTextPaint);

            // Only render the day of week and date if there is no peek card, so they do not bleed
            // into each other in ambient mode.
            if (getPeekCardPosition().isEmpty()) {
                // Date
                String dateString = mDateFormat.format(mDate);
                float halfDateX = mDatePaint.measureText(dateString) * 0.5f;
                canvas.drawText(dateString, xCenter-halfDateX,
                        textYOffset + mLineHeight, mDatePaint);
            }
        }

        private void drawWeatherImage(Canvas canvas, Rect bounds) {
            float fractionalHours = (float)mTime.hour + (float)mTime.minute/60f + (float)mTime.second/3600f;

            if (START_TIME < fractionalHours && fractionalHours < END_TIME) {
                float percentRange = (END_TIME - fractionalHours) / (END_TIME - START_TIME);
                float drawnAngleRatio = percentRange * (HIGH_ANGLE - LOW_ANGLE) + LOW_ANGLE;
                float drawnAngleRadians = drawnAngleRatio * (float) Math.PI / 180f;

                int xCenter = bounds.centerX() - mWeatherBitmap.getWidth() / 2;
                int yCenter = bounds.centerY() - mWeatherBitmap.getHeight() / 2;

                int xWeatherPos = (int) (DRAWN_RADIUS * (float) Math.cos(drawnAngleRadians) + xCenter);
                int yWeatherPos = (int) (-1 * DRAWN_RADIUS * (float) Math.sin(drawnAngleRadians) + yCenter);
                canvas.drawBitmap(mWeatherBitmap, xWeatherPos, yWeatherPos, mBackgroundPaint);
            }
        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }

        private void updateConfigDataItemAndUiOnStartup() {
            SunshineWatchFaceUtil.fetchConfigDataMap(mGoogleApiClient,
                    new SunshineWatchFaceUtil.FetchConfigDataMapCallback() {
                        @Override
                        public void onConfigDataMapFetched(DataMap startupConfig) {
                            // If the DataItem hasn't been created yet or some keys are missing,
                            // use the default values.
                            setDefaultValuesForMissingConfigKeys(startupConfig);
                            // TODO: Do we need this??? We just got the config map, so why are we sending it right back?
//                            SunshineWatchFaceUtil.putConfigDataItem(mGoogleApiClient, startupConfig);

                            updateUiForConfigDataMap(startupConfig);
                        }
                    }
            );
        }

        private void setDefaultValuesForMissingConfigKeys(DataMap config) {
            addIntKeyIfMissing(config, SunshineWatchFaceUtil.KEY_WEATHER_IMAGE,
                    SunshineWatchFaceUtil.WeatherBitmap.getDefaultValue());
            addIntKeyIfMissing(config, SunshineWatchFaceUtil.KEY_HIGH_TEMP,
                    SunshineWatchFaceUtil.INT_VALUE_DEFAULT_TEMPERATURE);
            addIntKeyIfMissing(config, SunshineWatchFaceUtil.KEY_LOW_TEMP,
                    SunshineWatchFaceUtil.INT_VALUE_DEFAULT_TEMPERATURE);
        }

        private void addIntKeyIfMissing(DataMap config, String key, int value) {
            if (!config.containsKey(key)) {
                config.putInt(key, value);
            }
        }

        @Override // DataApi.DataListener
        public void onDataChanged(DataEventBuffer dataEventBuffer) {
            for (DataEvent dataEvent : dataEventBuffer) {
                if (dataEvent.getType() != DataEvent.TYPE_CHANGED) {
                    continue;
                }

                DataItem dataItem = dataEvent.getDataItem();
                if (!dataItem.getUri().getPath().equals(
                        SunshineWatchFaceUtil.SUNSHINE_PATH_FEATURE)) {
                    continue;
                }

                DataMapItem dataMapItem = DataMapItem.fromDataItem(dataItem);
                DataMap config = dataMapItem.getDataMap();
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "Config DataItem updated:" + config);
                }
                updateUiForConfigDataMap(config);
            }
        }

        private void updateUiForConfigDataMap(final DataMap config) {
            boolean uiUpdated = false;
            for (String configKey : config.keySet()) {
                if (!config.containsKey(configKey)) {
                    continue;
                }
                int keyId = config.getInt(configKey);
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "Found Sunshine config key: " + configKey + " -> "
                            + Integer.toHexString(keyId));
                }
                if (updateUiForKey(configKey, keyId)) {
                    uiUpdated = true;
                }
            }
            if (uiUpdated) {
                invalidate();
            }
        }

        /**
         * Updates the weather image or temperatures of a UI item according to the given {@code configKey}. Does nothing if
         * {@code configKey} isn't recognized.
         *
         * @return whether UI has been updated
         */
        private boolean updateUiForKey(String configKey, int keyId) {
            if (configKey.equals(SunshineWatchFaceUtil.KEY_WEATHER_IMAGE)) {
                setInteractiveWeatherImage(keyId);
            } else if (configKey.equals(SunshineWatchFaceUtil.KEY_HIGH_TEMP)) {
                setInteractiveHighTemperature(keyId);
            } else if (configKey.equals(SunshineWatchFaceUtil.KEY_LOW_TEMP)) {
                setInteractiveLowTemperature(keyId);
            } else {
                Log.w(TAG, "Ignoring unknown config key: " + configKey);
                return false;
            }
            return true;
        }

        @Override // GoogleApiClient.ConnectionCallbacks
        public void onConnected(@Nullable Bundle connectionHint) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onConnected: " + connectionHint);
            }
            Wearable.DataApi.addListener(mGoogleApiClient, Engine.this);
            updateConfigDataItemAndUiOnStartup();
        }

        @Override  // GoogleApiClient.ConnectionCallbacks
        public void onConnectionSuspended(int cause) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onConnectionSuspended: " + cause);
            }
        }

        @Override  // GoogleApiClient.OnConnectionFailedListener
        public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onConnectionFailed: " + connectionResult);
            }
        }
    }
}
