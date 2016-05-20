package com.example.android.sunshine.app;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import com.example.android.sunshine.app.sync.SunshineSyncAdapter;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.WearableListenerService;

public class SunshineWearableListener extends WearableListenerService {
    @Override
    public void onDataChanged(DataEventBuffer dataEventBuffer) {
        Log.d("SunshineWearableListene", "onDataChanged");

        final String WATCH_FACE_SYNC_REQUEST = "/Sync";

        for(int i = 0; i < dataEventBuffer.getCount(); i++){
            DataEvent event = dataEventBuffer.get(i);

            if(event.getType() == DataEvent.TYPE_CHANGED && event.getDataItem().getUri().getPath().equals(WATCH_FACE_SYNC_REQUEST)) {
                SunshineSyncAdapter.syncImmediately(getApplicationContext());
            }
        }
    }
}
