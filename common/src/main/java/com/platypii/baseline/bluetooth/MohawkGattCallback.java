package com.platypii.baseline.bluetooth;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.util.Log;
import androidx.annotation.NonNull;
import java.util.UUID;

import static com.platypii.baseline.bluetooth.BluetoothState.BT_CONNECTED;

/**
 * GATT callback for LE GPS device
 */
public class MohawkGattCallback extends BluetoothGattCallback {
    private static final String TAG = "MohawkGattCallback";

    // Mohawk service
    private static final UUID mohawkService = UUID.fromString("ba5e0001-da9b-4622-b128-1e4f5022ab01");
    // Mohawk characteristic
    private static final UUID mohawkCharacteristic = UUID.fromString("ba5e0002-ad0c-4fe2-af23-55995ce8eb02");

    // Client Characteristic Configuration (what we subscribe to)
    private static final UUID clientCharacteristicDescriptor = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    @NonNull
    private final BluetoothService service;
    @NonNull
    private final MohawkProtocol proto;
    @NonNull
    private final MohawkRunnable mohawkRunnable;

    MohawkGattCallback(@NonNull MohawkRunnable mohawkRunnable) {
        this.mohawkRunnable = mohawkRunnable;
        this.service = mohawkRunnable.service;
        this.proto = new MohawkProtocol(service);
    }

    @Override
    public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
        if (status == BluetoothGatt.GATT_SUCCESS) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(TAG, "Mohawk connected");
                // TODO: If we have connected to a device before, skip discover services and connect directly.
                gatt.discoverServices();
                service.setState(BT_CONNECTED);
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(TAG, "Mohawk disconnected");
                gatt.close();
                mohawkRunnable.onDisconnect();
            } else {
                // Connecting or disconnecting state
                Log.i(TAG, "Mohawk state " + newState);
            }
        } else {
            gatt.close();
            if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(TAG, "Mohawk remote disconnect");
                mohawkRunnable.onDisconnect();
            } else {
                Log.e(TAG, "Mohawk connection state error " + status + " " + newState);
            }
        }
    }

    @Override
    public void onServicesDiscovered(BluetoothGatt gatt, int status) {
        if (status == BluetoothGatt.GATT_SUCCESS) {
            Log.i(TAG, "Mohawk bluetooth services discovered");
            final BluetoothGattService service = gatt.getService(mohawkService);
            final BluetoothGattCharacteristic ch = service.getCharacteristic(mohawkCharacteristic);
            if (ch != null) {
                // Enables notification locally:
                gatt.setCharacteristicNotification(ch, true);
                // Enables notification on the device
                final BluetoothGattDescriptor descriptor = ch.getDescriptor(clientCharacteristicDescriptor);
                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                gatt.writeDescriptor(descriptor);
            }
        } else {
            Log.i(TAG, "Mohawk service discovery failed");
            // TODO: disconnect
        }
    }

    @Override
    public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic ch) {
        if (ch.getUuid().equals(mohawkCharacteristic)) {
            proto.processBytes(ch.getValue());
        } else {
            Log.i(TAG, "Mohawk onCharacteristicChanged " + ch);
        }
    }
}
