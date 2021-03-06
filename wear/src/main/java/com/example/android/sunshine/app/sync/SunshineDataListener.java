package com.example.android.sunshine.app.sync;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.support.annotation.NonNull;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import java.io.InputStream;

//  SunshineDataListener communicates with the handheld app in order to fetch the weather data
//  via Wearable Data API

public class SunshineDataListener implements DataApi.DataListener {

    String HIGH = "high";
    String LOW = "low";
    String ICON = "icon";
    String DATA_PATH = "/data/weather/";
    String SYNC_PATH = "/sync/weather/";

    public static final String BLANK_REQUEST_KEY = "";
    public static final int BLANK_REQUEST_VALUE = 0;

    private GoogleApiClient googleApiClient;
    private WeatherInformationListener weatherInformationListener;

    public SunshineDataListener(GoogleApiClient googleApiClient, WeatherInformationListener weatherInformationListener) {
        this.googleApiClient = googleApiClient;
        this.weatherInformationListener = weatherInformationListener;
        googleApiClient.connect();
    }

    @Override
    public void onDataChanged(DataEventBuffer dataEventBuffer) {
        if (dataEventBuffer == null) {
            weatherInformationListener.onWeatherInformationFetchFailure();
            return;
        }
        for (DataEvent dataEvent : dataEventBuffer) {
            if (doesHaveWeatherData(dataEvent)) {
                DataMap weatherDataMap = getWeatherDataMap(dataEvent);
                Double high = weatherDataMap.getDouble(HIGH);
                Double low = weatherDataMap.getDouble(LOW);
                weatherInformationListener.onTemperatureFetchSuccess(high, low);
                fetchWeatherIconAsynchronously(weatherDataMap);
                return;
            }
        }
    }

    public void requestWeatherInformationFromHandheld() {
        PutDataMapRequest putDataMapRequest = PutDataMapRequest.create(SYNC_PATH + System.currentTimeMillis());
        putDataMapRequest.getDataMap().putInt(BLANK_REQUEST_KEY, BLANK_REQUEST_VALUE);
        PutDataRequest putDataRequest = putDataMapRequest.asPutDataRequest();
        putDataRequest.setUrgent();
        Wearable.DataApi.putDataItem(googleApiClient, putDataRequest);
    }

    private boolean doesHaveWeatherData(DataEvent dataEvent) {
        return dataEvent.getDataItem().getUri().toString().contains(DATA_PATH);
    }

    private DataMap getWeatherDataMap(DataEvent dataEvent) {
        DataItem weatherDataItem = dataEvent.getDataItem();
        DataMapItem weatherDataMapItem = DataMapItem.fromDataItem(weatherDataItem);
        return weatherDataMapItem.getDataMap();
    }

    private void fetchWeatherIconAsynchronously(DataMap weatherDataMap) {
        Asset iconAsset = weatherDataMap.getAsset(ICON);
        if (googleApiClient.isConnected()) {
            PendingResult<DataApi.GetFdForAssetResult> fileDescriptorForIconAsset =
                    Wearable.DataApi.getFdForAsset(googleApiClient, iconAsset);
            obtainWeatherIconUsingFileDescriptor(fileDescriptorForIconAsset);
        }
    }

    private void obtainWeatherIconUsingFileDescriptor(PendingResult<DataApi.GetFdForAssetResult> fileDescriptorForIconAsset) {
        fileDescriptorForIconAsset.setResultCallback(new ResultCallback<DataApi.GetFdForAssetResult>() {
            @Override
            public void onResult(@NonNull DataApi.GetFdForAssetResult getFdForAssetResult) {
                InputStream weatherIconInputStream = getFdForAssetResult.getInputStream();
                Bitmap fetchedWeatherIcon = BitmapFactory.decodeStream(weatherIconInputStream);
                if (fetchedWeatherIcon == null) {
                    weatherInformationListener.onWeatherInformationFetchFailure();
                    return;
                }
                fetchedWeatherIcon = Bitmap.createScaledBitmap(fetchedWeatherIcon, 40, 40, false);
                weatherInformationListener.onWeatherIconFetchSuccess(fetchedWeatherIcon);
            }
        });
    }
}
