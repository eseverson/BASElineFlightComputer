package com.platypii.baseline.bluetooth;

import com.platypii.baseline.util.Exceptions;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.os.Build;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import java.util.Collections;
import java.util.List;

import static com.platypii.baseline.bluetooth.BluetoothState.BT_CONNECTED;
import static com.platypii.baseline.bluetooth.BluetoothState.BT_CONNECTING;
import static com.platypii.baseline.bluetooth.BluetoothState.BT_STARTING;
import static com.platypii.baseline.bluetooth.BluetoothState.BT_STATES;
import static com.platypii.baseline.bluetooth.BluetoothState.BT_STOPPED;
import static com.platypii.baseline.bluetooth.BluetoothState.BT_STOPPING;

/**
 * Thread that reads from bluetooth
 */
class MohawkRunnable implements Stoppable {
    private static final String TAG = "MohawkRunnable";

    @NonNull
    final BluetoothService service;
    @NonNull
    private final BluetoothAdapter bluetoothAdapter;
    private Context context;
    @Nullable
    private BluetoothGatt bluetoothGatt;
    @Nullable
    private BluetoothLeScanner bluetoothScanner;
    @Nullable
    private ScanCallback scanCallback;
    @Nullable
    private BluetoothDevice mohawkDevice;

    @NonNull
    private final BluetoothGattCallback mohawkCallback;

    MohawkRunnable(@NonNull BluetoothService service, @NonNull BluetoothAdapter bluetoothAdapter, Context context) {
        this.service = service;
        this.bluetoothAdapter = bluetoothAdapter;
        this.context = context;
        mohawkCallback = new MohawkGattCallback(this);
    }

    @Override
    public void run() {
        Log.i(TAG, "Mohawk thread starting");
        if (!bluetoothAdapter.isEnabled()) {
            Log.e(TAG, "Bluetooth is not enabled");
            return;
        }
        // Scan for rangefinders
        Log.i(TAG, "Scanning for mohawk");
        service.setState(BT_STARTING);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // Why scan instead of connect? Seems like we can't subscribe without scan?
            scan();
        } else {
            // Connect directly (not sure this works)
            mohawkDevice = bluetoothAdapter.getRemoteDevice(service.preferences.preferenceDeviceId);
            connect();
        }
        // TODO: this whole run() is fast, probably shouldn't even be a Runnable
        Log.i(TAG, "MohawkRunnable finished");
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void scan() {
        bluetoothScanner = bluetoothAdapter.getBluetoothLeScanner();
        if (bluetoothScanner == null) {
            Log.e(TAG, "Failed to get bluetooth LE scanner");
            return;
        }
        final ScanFilter scanFilter = new ScanFilter.Builder().build();
        final List<ScanFilter> scanFilters = Collections.singletonList(scanFilter);
        final ScanSettings scanSettings = new ScanSettings.Builder().build();
        scanCallback = new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, @NonNull ScanResult result) {
                super.onScanResult(callbackType, result);
                if (service.getState() == BT_STARTING) {
                    final BluetoothDevice device = result.getDevice();
//                    Log.d(TAG, "Got device: " + device.getAddress() + " " + device.getName());
//                    final ScanRecord record = result.getScanRecord();
                    // TODO: Check for characteristic
                    // TODO: Check for address
                    if (service.preferences.preferenceDeviceId != null && service.preferences.preferenceDeviceId.equals(device.getAddress())) {
                        Log.i(TAG, "Mohawk found, connecting to: " + device.getAddress());
                        mohawkDevice = device;
                        connect();
                    }
                }
            }
        };
        Log.i(TAG, "Starting mohawk scan for address: " + service.preferences.preferenceDeviceId);
        bluetoothScanner.startScan(scanFilters, scanSettings, scanCallback);
    }

    /**
     * Connect to gps receiver.
     * Precondition: bluetooth enabled and preferenceDeviceId != null
     */
    private void connect() {
        if (mohawkDevice != null) {
            service.setState(BT_CONNECTING);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) { // minsdk 23
                bluetoothGatt = mohawkDevice.connectGatt(context, true, mohawkCallback, BluetoothDevice.TRANSPORT_LE);
            } else {
                bluetoothGatt = mohawkDevice.connectGatt(context, true, mohawkCallback);
            }
        } else {
            Log.e(TAG, "Cannot connect to null device");
        }
    }

    /**
     * Called when connection is broken, but we should probably reconnect
     */
    void onDisconnect() {
        final int state = service.getState();
        if (state == BT_CONNECTED) {
            // Try to reconnect (scanning doesn't seem to work?)
            connect();
        } else if (state == BT_STOPPING || state == BT_STOPPED) {
            Log.d(TAG, "Mohawk disconnected on stop");
        } else {
            Log.e(TAG, "Unexpected mohawk state: " + BT_STATES[state]);
        }
    }

    private void stopScan() {
        if (service.getState() != BT_STARTING) {
            Exceptions.report(new IllegalStateException("Scanner shouldn't exist in state " + service.getState()));
        }
        // Stop scanning
        if (bluetoothScanner != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            bluetoothScanner.stopScan(scanCallback);
        }
    }

    @Override
    public void stop() {
        Log.i(TAG, "Stopping mohawk connection");
        // Close bluetooth connection
        if (bluetoothGatt != null) {
            bluetoothGatt.disconnect();
            bluetoothGatt = null;
        }
        // Stop scanning
        if (service.getState() == BT_STARTING) {
            stopScan();
        }
        service.setState(BT_STOPPING);
    }
}
