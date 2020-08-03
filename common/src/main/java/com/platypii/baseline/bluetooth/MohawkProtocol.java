package com.platypii.baseline.bluetooth;

import com.platypii.baseline.location.LocationCheck;
import com.platypii.baseline.location.MyLocationListener;
import com.platypii.baseline.location.NMEAException;
import com.platypii.baseline.measurements.MLocation;
import com.platypii.baseline.util.Exceptions;

import android.util.Log;
import androidx.annotation.NonNull;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class MohawkProtocol {
    private static final String TAG = "MohawkProtocol";

    @NonNull
    private final BluetoothService service;

    MohawkProtocol(@NonNull BluetoothService service) {
        this.service = service;
    }

    void processBytes(final byte[] value) {
        if (value[0] == 'L' && value.length == 20) {
            // Unpack location
            final ByteBuffer buf = ByteBuffer.wrap(value).order(ByteOrder.LITTLE_ENDIAN);
            // Three least significant bytes of tenths since epoch
            final long time1 = ((long) value[1]) & 0xff;
            final long time2 = ((long) value[2]) & 0xff;
            final long time3 = ((long) value[3]) & 0xff;
            final long tenths = (time1 << 16) + (time2 << 8) + time3;
            final long lsb = tenths * 100;
            long now = System.currentTimeMillis();
            // Use most significant bits from system
            // Check for boundary conditions
            long checkbit = 1 << 23;
            if ((now & checkbit) > 0 && (lsb & checkbit) == 0) {
                now += checkbit;
            }
            if ((now & checkbit) == 0 && (lsb & checkbit) > 0) {
                now -= checkbit;
            }
            final long shift = 100 << 24;
            final long millis = now / shift * shift + lsb;
            final double lat = buf.getInt(4) * 1e-6; // microdegrees
            final double lng = buf.getInt(8) * 1e-6; // microdegrees
            final double alt = (buf.getShort(12) & 0xffff) * 0.1; // decimeters // TODO: 0..6553.6m is not enough
            final double vN = buf.getShort(14) * 0.01; // cm/s
            final double vE = buf.getShort(16) * 0.01; // cm/s
            final double climb = buf.getShort(18) * 0.01; // cm/s

            final int locationError = LocationCheck.validate(lat, lng);
            if (locationError == LocationCheck.VALID) {
                final MLocation loc = new MLocation(
                        millis, lat, lng, alt, climb, vN, vE,
                        Float.NaN, Float.NaN, Float.NaN, Float.NaN, -1, -1
                );
                Log.i(TAG, "mohawk -> app: gps " + loc);
                // Update listeners
                for (MyLocationListener listener : service.locationListeners) {
                    listener.onLocationChanged(loc);
                }
            } else {
                Log.w(TAG, LocationCheck.message[locationError] + ": " + lat + "," + lng);
                Exceptions.report(new NMEAException(LocationCheck.message[locationError] + ": " + lat + "," + lng));
            }
        } else {
            Log.w(TAG, "mohawk -> app: unknown " + new String(value));
        }
    }
}
