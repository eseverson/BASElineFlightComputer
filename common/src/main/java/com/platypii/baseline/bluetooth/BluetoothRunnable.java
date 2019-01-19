package com.platypii.baseline.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.location.GpsStatus;
import android.os.ParcelUuid;
import android.support.annotation.NonNull;
import android.util.Log;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.UUID;

import static com.platypii.baseline.bluetooth.BluetoothState.*;

/**
 * Thread that reads from bluetooth input stream, and turns into NMEA sentences
 */
class BluetoothRunnable implements Runnable {
    private static final String TAG = "BluetoothRunnable";

    private static final UUID DEFAULT_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private static final int reconnectDelay = 500; // milliseconds

    @NonNull
    private final BluetoothService bluetooth;
    @NonNull
    private final BluetoothAdapter bluetoothAdapter;
    private BluetoothSocket bluetoothSocket;

    BluetoothRunnable(@NonNull BluetoothService bluetooth, @NonNull BluetoothAdapter bluetoothAdapter) {
        this.bluetooth = bluetooth;
        this.bluetoothAdapter = bluetoothAdapter;
    }

    @Override
    public void run() {
        Log.i(TAG, "Bluetooth thread starting");

        // Reconnect loop
        while (bluetooth.getState() != BT_STOPPING) {
            // Connect to bluetooth GPS
            bluetooth.setState(BT_CONNECTING);
            final boolean isConnected = connect();
            if (bluetooth.getState() == BT_CONNECTING && isConnected) {
                bluetooth.setState(BT_CONNECTED);

                // Start processing NMEA sentences
                processSentences();
            }
            // Are we restarting or stopping?
            if (bluetooth.getState() != BT_STOPPING) {
                bluetooth.setState(BT_DISCONNECTED);
                // Sleep before reconnect
                try {
                    Thread.sleep(reconnectDelay);
                } catch (InterruptedException ie) {
                    Log.e(TAG, "Bluetooth thread interrupted");
                }
                Log.w(TAG, "Reconnecting to bluetooth device");
            } else {
                Log.i(TAG, "Bluetooth thread about to stop");
            }
        }

        // Bluetooth service stopped
        bluetooth.setState(BT_STOPPED);
    }

    /**
     * Connect to gps receiver.
     * Precondition: bluetooth enabled and preferenceDeviceId != null
     * @return true iff bluetooth socket was connect successfully
     */
    private boolean connect() {
        if (!bluetoothAdapter.isEnabled()) {
            Log.w(TAG, "Bluetooth is not enabled");
            return false;
        } else if (bluetooth.preferences.preferenceDeviceId == null) {
            Log.w(TAG, "Cannot connect: bluetooth device not selected");
            return false;
        }
        // Get bluetooth device
        final BluetoothDevice bluetoothDevice = bluetoothAdapter.getRemoteDevice(bluetooth.preferences.preferenceDeviceId);
        UUID uuid = DEFAULT_UUID;
        final ParcelUuid[] uuids = bluetoothDevice.getUuids();
        if (uuids != null && uuids.length > 0) {
            uuid = uuids[0].getUuid();
        }
        // Connect to bluetooth device
        Log.i(TAG, "Connecting to bluetooth device: " + bluetoothDevice.getName());
        try {
            bluetoothSocket = bluetoothDevice.createRfcommSocketToServiceRecord(uuid);
            bluetoothSocket.connect();

            // Connected to bluetooth device
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Failed to connect to bluetooth device: " + e.getMessage());
            return false;
        }
    }

    /**
     * Pipe bluetooth socket into nmea listeners
     */
    private void processSentences() {
        try {
            final InputStream is = bluetoothSocket.getInputStream();
            final BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            String line;
            while (bluetooth.getState() == BT_CONNECTED && (line = reader.readLine()) != null) {
                final String nmea = line.trim();
                // Log.v(TAG, "Got line: " + nmea);
                // Update listeners
                for (GpsStatus.NmeaListener listener : bluetooth.listeners) {
                    listener.onNmeaReceived(System.currentTimeMillis(), nmea);
                }
            }
        } catch (IOException e) {
            if (bluetooth.getState() == BT_CONNECTED) {
                Log.e(TAG, "Error reading from bluetooth socket", e);
            }
        } finally {
            Log.d(TAG, "Bluetooth thread shutting down");
        }
    }

    void stop() {
        bluetooth.setState(BT_STOPPING);

        // Close bluetooth socket
        if (bluetoothSocket != null) {
            try {
                bluetoothSocket.close();
            } catch (IOException e) {
                Log.w(TAG, "Exception closing bluetooth socket", e);
            }
        }
    }

}
