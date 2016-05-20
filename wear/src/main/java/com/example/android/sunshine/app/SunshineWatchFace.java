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

package com.example.android.sunshine.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.Time;
import android.util.Log;
import android.view.SurfaceHolder;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Analog watch face with a ticking second hand. In ambient mode, the second hand isn't shown. On
 * devices with low-bit ambient mode, the hands are drawn without anti-aliasing in ambient mode.
 */
public class SunshineWatchFace extends CanvasWatchFaceService {
    /**
     * Update rate in milliseconds for interactive mode. We update once a second to advance the
     * second hand.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    double min_Temp = 0;
    double max_Temp = 0;
    Bitmap mBitmap = null;
    String mWeatherDesc = null ;

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
            GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener{
        final Handler mUpdateTimeHandler = new EngineHandler(this);
        boolean mRegisteredTimeZoneReceiver = false;
        Paint mBackgroundPaint;
        Paint mHandPaint;

        Paint mDigitalPaint;
        Paint mDigitalPaintOuter;

        boolean mAmbient;
        Time mTime;

        GoogleApiClient mGoogleApiClient ;

        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mTime.clear(intent.getStringExtra("time-zone"));
                mTime.setToNow();
            }
        };
        int mTapCount;

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(SunshineWatchFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_SHORT)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .setAcceptsTapEvents(true)
                    .build());

            Resources resources = SunshineWatchFace.this.getResources();

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(resources.getColor(R.color.background));

            mHandPaint = new Paint();
            mHandPaint.setColor(resources.getColor(R.color.analog_hands));
            mHandPaint.setStrokeWidth(resources.getDimension(R.dimen.analog_hand_stroke));
            mHandPaint.setAntiAlias(true);
            mHandPaint.setStrokeCap(Paint.Cap.ROUND);

            mTime = new Time();

            mDigitalPaint = new Paint();
            mDigitalPaint.setARGB(255, 255, 255, 255);
            mDigitalPaint.setStrokeWidth(5.f);
            mDigitalPaint.setTextSize(24);
            mDigitalPaint.setStyle(Paint.Style.FILL);
            mDigitalPaint.setAntiAlias(true);

            mDigitalPaintOuter = new Paint();
            mDigitalPaintOuter.setARGB(255, 0, 0, 0);
            mDigitalPaintOuter.setStrokeWidth(5.f);
            mDigitalPaintOuter.setTextSize(24);
            mDigitalPaintOuter.setStyle(Paint.Style.FILL);
            mDigitalPaintOuter.setAntiAlias(true);

            mGoogleApiClient = new GoogleApiClient.Builder(SunshineWatchFace.this)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(Wearable.API)
                    .build();
            mGoogleApiClient.connect();
           syncWeatherData();
        }

        private void syncWeatherData(){

            Log.d("SunshineWatchFace", "syncWeatherData " );

            PutDataMapRequest dataSyncRequest = PutDataMapRequest.create("/Sync");

            dataSyncRequest.getDataMap().putLong("time", System.currentTimeMillis());

            PutDataRequest dataPutRequest = dataSyncRequest.asPutDataRequest();


            Wearable.DataApi.putDataItem(mGoogleApiClient, dataPutRequest);

        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
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
                    mHandPaint.setAntiAlias(!inAmbientMode);
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
                    mTapCount++;
                    mBackgroundPaint.setColor(resources.getColor(mTapCount % 2 == 0 ?
                            R.color.background : R.color.background2));
                    break;
            }
            invalidate();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            mTime.setToNow();

            // Draw the background.
            if (isInAmbientMode()) {
                canvas.drawColor(Color.BLACK);
            } else {
                canvas.drawRect(0, 0, canvas.getWidth(), canvas.getHeight(), mBackgroundPaint);
            }

            // Find the center. Ignore the window insets so that, on round watches with a
            // "chin", the watch face is centered on the entire screen, not just the usable
            // portion.
            float centerX = bounds.width() / 2f;
            float centerY = bounds.height() / 2f;

            String ts1 = String.format("%02d:%02d:%02d %s",
                    mTime.hour % 12, mTime.minute,
                    mTime.second, (mTime.hour < 12) ? "am" : "pm");

            float tw1 = mDigitalPaint.measureText(ts1);
            float tx1 = (bounds.width() - tw1) / 2 ;
            float ty1 = bounds.height() / 2 - 100;
            canvas.drawText(ts1, tx1 - 1, ty1 - 1, mDigitalPaintOuter);
            canvas.drawText(ts1, tx1 + 1, ty1 - 1, mDigitalPaintOuter);
            canvas.drawText(ts1, tx1 - 1, ty1 + 1, mDigitalPaintOuter);
            canvas.drawText(ts1, tx1 + 1, ty1 + 1, mDigitalPaintOuter);
            canvas.drawText(ts1, tx1, ty1, mDigitalPaint);

            // draw the date
           /* String ts2 = String.format("%02d:%02d:%02d %s",
                    mTime.hour % 12, mTime.minute,
                    mTime.second, (mTime.hour < 12) ? "am" : "pm");*/
            String ts2 = String.format( "%1.0f - %1.0f ",min_Temp, max_Temp );
            float tw2 = mDigitalPaint.measureText(ts2);
            float tx2 = (bounds.width() - tw2) / 2 + 50;
            float ty2 = bounds.height() / 2 - 20;
            canvas.drawText(ts2, tx2 - 1, ty2 - 1, mDigitalPaintOuter);
            canvas.drawText(ts2, tx2 + 1, ty2 - 1, mDigitalPaintOuter);
            canvas.drawText(ts2, tx2 - 1, ty2 + 1, mDigitalPaintOuter);
            canvas.drawText(ts2, tx2 + 1, ty2 + 1, mDigitalPaintOuter);
            canvas.drawText(ts2, tx2, ty2, mDigitalPaint);

            float tx3 = bounds.left + 5;
            float ty3 = bounds.height() / 2 - 80;

            if( mBitmap != null ) {
                canvas.drawBitmap(mBitmap, tx3, ty3, new Paint());

                float tw3 = mDigitalPaint.measureText(mWeatherDesc);
                float tx4 = (bounds.width() - tw3) / 2 ;

                float ty4 = ty3 + mBitmap.getHeight() + 5;

                if (mWeatherDesc != null) {
                    canvas.drawText(mWeatherDesc, tx4 - 1, ty4 - 1, mDigitalPaintOuter);
                    canvas.drawText(mWeatherDesc, tx4 + 1, ty4 - 1, mDigitalPaintOuter);
                    canvas.drawText(mWeatherDesc, tx4 - 1, ty4 + 1, mDigitalPaintOuter);
                    canvas.drawText(mWeatherDesc, tx4 + 1, ty4 + 1, mDigitalPaintOuter);
                    canvas.drawText(mWeatherDesc, tx4, ty4, mDigitalPaint);
                }
            }

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

        @Override
        public void onConnected(Bundle bundle) {

            Log.d("SunshineWatchFace", "onConnected " );
            Wearable.DataApi.addListener(mGoogleApiClient, Engine.this);
        }

        @Override
        public void onConnectionSuspended(int i) {

        }

        @Override
        public void onDataChanged(DataEventBuffer dataEventBuffer) {

            Log.d("SunshineWatchFace", "onDataChanged ");

            for( DataEvent dataEvent:dataEventBuffer )
            {
                if( dataEvent.getType() == DataEvent.TYPE_CHANGED )
                {
                    DataMap dataMap = DataMapItem.fromDataItem(dataEvent.getDataItem()).getDataMap();
                    String path = dataEvent.getDataItem().getUri().getPath();

                    if( path.equals("/weather-data"))
                    {
                        min_Temp = dataMap.getDouble("INDEX_MIN_TEMP");
                        max_Temp = dataMap.getDouble("INDEX_MAX_TEMP");
                        Asset profileAsset = dataMap.getAsset("WEATHER_IMAGE");
                        new LoadImage().execute(profileAsset);
                        mWeatherDesc = dataMap.getString("WEATHER_DESCRIPTION");
                    }
                }
                Log.d("SunshineWatchFace", String.format( "%02f - %02f ",min_Temp, max_Temp ) );
            }
        }
        class LoadImage extends AsyncTask<Asset, Void, Bitmap> {

            @Override
            protected Bitmap doInBackground(Asset... params) {
               return loadBitmapFromAsset(params[0]);
            }
            @Override
            protected void onPostExecute(Bitmap result) {
                mBitmap = result;
            }
            private Bitmap loadBitmapFromAsset(Asset asset) {


                if (asset == null) {
                    throw new IllegalArgumentException("Asset must be non-null");
                }
                ConnectionResult result =
                        mGoogleApiClient.blockingConnect(30, TimeUnit.SECONDS);
                if (!result.isSuccess()) {
                    return null;
                }
                // convert asset into a file descriptor and block until it's ready
                InputStream assetInputStream = Wearable.DataApi.getFdForAsset(
                        mGoogleApiClient, asset).await().getInputStream();
                mGoogleApiClient.disconnect();

                if (assetInputStream == null) {
                    Log.w("loadBitmapFromAsset", "Requested an unknown Asset.");
                    return null;
                }
                // decode the stream into a bitmap
                return BitmapFactory.decodeStream(assetInputStream);
            }

        }

        @Override
        public void onConnectionFailed(ConnectionResult connectionResult) {

        }
    }
}
