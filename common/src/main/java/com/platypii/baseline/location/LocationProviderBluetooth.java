package com.platypii.baseline.location;

import com.platypii.baseline.altimeter.MyAltimeter;
import com.platypii.baseline.bluetooth.BluetoothService;
import com.platypii.baseline.measurements.MLocation;
import com.platypii.baseline.util.Numbers;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;

class LocationProviderBluetooth extends LocationProviderNMEA implements MyLocationListener {
    protected final String TAG = "ProviderBluetooth";

    @NonNull
    private final BluetoothService bluetooth;

    @NonNull
    @Override
    protected String providerName() {
        return TAG;
    }

    @NonNull
    @Override
    protected String dataSource() {
        return "BT " + bluetooth.preferences.preferenceDeviceName;
    }

    LocationProviderBluetooth(@NonNull MyAltimeter alti, @NonNull BluetoothService bluetooth) {
        super(alti);
        this.bluetooth = bluetooth;
    }

    /**
     * Listen for GPPWR command
     */
    @Override
    public void onNmeaReceived(long timestamp, @NonNull String nmea) {
        // Parse PWR command
        if (nmea.startsWith("$GPPWR")) {
            final String[] split = NMEA.splitNmea(nmea);
            bluetooth.powerLevel = NMEA.parsePowerLevel(split);
            bluetooth.charging = Numbers.parseInt(split[5], 0) == 1;
        } else if (nmea.startsWith("$")) {
            super.onNmeaReceived(timestamp, nmea);
        } else {
            Log.w(TAG, "Unexpected bluetooth message: " + nmea);
        }
    }

    @Override
    public void onLocationChanged(@NonNull MLocation loc) {
        updateLocation(loc);
    }

    /**
     * Start location updates
     *
     * @param context The Application context
     */
    @Override
    public void start(@NonNull Context context) throws SecurityException {
        // Start NMEA updates
        bluetooth.addNmeaListener(this);
        bluetooth.addLocationListener(this);
    }

    @Override
    public void stop() {
        super.stop();
        bluetooth.removeNmeaListener(this);
        bluetooth.removeLocationListener(this);
    }
}
