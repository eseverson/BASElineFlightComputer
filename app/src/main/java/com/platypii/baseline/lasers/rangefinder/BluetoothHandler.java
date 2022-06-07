package com.platypii.baseline.lasers.rangefinder;

import static com.platypii.baseline.bluetooth.BluetoothState.BT_CONNECTED;
import static com.platypii.baseline.bluetooth.BluetoothState.BT_CONNECTING;
import static com.platypii.baseline.bluetooth.BluetoothState.BT_SCANNING;
import static com.platypii.baseline.bluetooth.BluetoothState.BT_STARTING;
import static com.platypii.baseline.bluetooth.BluetoothState.BT_STOPPED;
import static com.platypii.baseline.bluetooth.BluetoothState.BT_STOPPING;
import static com.platypii.baseline.bluetooth.BluetoothUtil.byteArrayToHex;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.os.Handler;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.platypii.baseline.bluetooth.BluetoothState;
import com.welie.blessed.BluetoothCentralManager;
import com.welie.blessed.BluetoothCentralManagerCallback;
import com.welie.blessed.BluetoothPeripheral;
import com.welie.blessed.BluetoothPeripheralCallback;
import com.welie.blessed.ConnectionPriority;
import com.welie.blessed.GattStatus;
import com.welie.blessed.HciStatus;
import com.welie.blessed.WriteType;
import java.util.UUID;

/**
 * Thread that reads from bluetooth connection.
 * Autopilot messages are emitted as events.
 */
class BluetoothHandler {
    private static final String TAG = "BluetoothHandler";

    // Autopilot service IDs
    private static final UUID apServiceId = UUID.fromString("ba5e0001-c55f-496f-a444-9855f5f14901");
    private static final UUID apCharacteristicId = UUID.fromString("ba5e0002-9235-47c8-b2f3-916cee33d802");

    // Remote control service IDs
    private static final UUID rcServiceId = UUID.fromString("ba5e0003-ed55-43fa-bb54-8e721e092603");
    private static final UUID rcCharacteristicId = UUID.fromString("ba5e0004-be98-4de9-9e9a-080b5bb41404");

    private final Handler handler = new Handler();
    @NonNull
    private final RangefinderService service;
    @NonNull
    private final BluetoothCentralManager central;
    @Nullable
    private BluetoothPeripheral currentPeripheral;
    @Nullable
    private BluetoothGattCharacteristic currentCharacteristic;
    @Nullable
    private RangefinderProtocol protocol;

    boolean connected_ap = false;
    boolean connected_rc = false;

    BluetoothHandler(@NonNull RangefinderService service, @NonNull Context context) {
        this.service = service;
        central = new BluetoothCentralManager(context, bluetoothCentralManagerCallback, new Handler());
    }

    public void start() {
        if (BluetoothState.started(service.getState())) {
            scan();
        } else if (service.getState() == BT_SCANNING) {
            Log.w(TAG, "Already searching");
        } else if (service.getState() == BT_STOPPING || service.getState() != BT_STOPPED) {
            Log.w(TAG, "Already stopping");
        }
    }

    private void scan() {
        service.setState(BT_SCANNING);
        // Scan for peripherals with a certain service UUIDs
        central.startPairingPopupHack();
        Log.i(TAG, "Scanning for laser rangefinders");
        central.scanForPeripheralsWithServices(new UUID[]{rcServiceId});
    }

    // Callback for peripherals
    private final BluetoothPeripheralCallback peripheralCallback = new BluetoothPeripheralCallback() {
        @Override
        public void onServicesDiscovered(@NonNull BluetoothPeripheral peripheral) {
            Log.i(TAG, "Bluetooth services discovered for " + peripheral.getName());

            // Request a higher MTU, iOS always asks for 185
            peripheral.requestMtu(185);

            // Request a new connection priority
            peripheral.requestConnectionPriority(ConnectionPriority.HIGH);

            protocol.onServicesDiscovered();
        }

        @Override
        public void onNotificationStateUpdate(@NonNull final BluetoothPeripheral peripheral, @NonNull final BluetoothGattCharacteristic characteristic, @NonNull final GattStatus status) {
            if (status == GattStatus.SUCCESS) {
                if (peripheral.isNotifying(characteristic)) {
                    Log.d(TAG, "SUCCESS: Notify set to 'on' for " + characteristic.getUuid());
                } else {
                    Log.d(TAG, "SUCCESS: Notify set to 'off' for " + characteristic.getUuid());
                }
            } else {
                Log.e(TAG, "ERROR: Changing notification state failed for " + characteristic.getUuid());
            }
        }

        @Override
        public void onCharacteristicWrite(@NonNull BluetoothPeripheral peripheral, @NonNull byte[] value, @NonNull BluetoothGattCharacteristic characteristic, @NonNull final GattStatus status) {
            if (status == GattStatus.SUCCESS) {
//                Log.d(TAG, "SUCCESS: Writing " + byteArrayToHex(value) + " to " + characteristic.getUuid());
            } else {
                Log.w(TAG, "ERROR: Failed writing " + byteArrayToHex(value) + " to " + characteristic.getUuid());
            }
        }

        @Override
        public void onCharacteristicUpdate(@NonNull BluetoothPeripheral peripheral, @NonNull byte[] value, @NonNull BluetoothGattCharacteristic characteristic, @NonNull final GattStatus status) {
            if (status != GattStatus.SUCCESS) return;
            if (value.length == 0) return;
            final UUID characteristicUUID = characteristic.getUuid();

//            Log.d(TAG, "onCharacteristicUpdate %s", characteristicUUID);
            if (characteristicUUID.equals(apCharacteristicId)) {
                processBytes(value);
            } else if (characteristicUUID.equals(rcCharacteristicId)) {
                processBytes(value);
            }
        }
    };

    // Callback for central
    private final BluetoothCentralManagerCallback bluetoothCentralManagerCallback = new BluetoothCentralManagerCallback() {

        @Override
        public void onConnectedPeripheral(@NonNull BluetoothPeripheral connectedPeripheral) {
            currentPeripheral = connectedPeripheral;
            Log.i(TAG, "Connected to " + connectedPeripheral.getName());
            if (connectedPeripheral.getService(apServiceId) != null) {
                connected_ap = true;
            } else if (connectedPeripheral.getService(rcServiceId) != null) {
                connected_rc = true;
            } else {
                Log.e(TAG, "Connected to device with no service?");
            }
            service.setState(BT_CONNECTED);
        }

        @Override
        public void onConnectionFailed(@NonNull BluetoothPeripheral peripheral, @NonNull final HciStatus status) {
            Log.e(TAG, "Rangefinder connection " + peripheral.getName() + " failed with status " + status);
            start(); // start over
        }

        @Override
        public void onDisconnectedPeripheral(@NonNull final BluetoothPeripheral peripheral, @NonNull final HciStatus status) {
            Log.i(TAG, "Autopilot disconnected " + peripheral.getName() + " with status " + status);
            if (connected_ap) {
                Log.d(TAG, "Auto reconnecting to AP");
                connected_ap = false;
                if (BluetoothState.started(service.getState())) {
                    autoreconnect();
                }
            } else {
                Log.d(TAG, "Back to searching");
                connected_ap = false;
                connected_rc = false;
                currentPeripheral = null;
                // Go back to searching
                if (BluetoothState.started(service.getState())) {
                    scan();
                }
            }
        }

        private void autoreconnect() {
            // Reconnect to this device when it becomes available again
            service.setState(BT_SCANNING);
            handler.postDelayed(() -> central.autoConnectPeripheral(currentPeripheral, peripheralCallback), 5000);
        }

        @Override
        public void onDiscoveredPeripheral(@NonNull BluetoothPeripheral peripheral, @NonNull ScanResult scanResult) {
            if (service.getState() != BT_SCANNING) {
                Log.e(TAG, "Invalid BT state: " + BluetoothState.BT_STATES[service.getState()]);
                // TODO: return?
            }
            if (peripheral.getName().equals("ParaDrone")) {
                Log.i(TAG, "Autopilot device found, connecting to: " + peripheral.getName() + " " + peripheral.getAddress());
                service.setState(BT_CONNECTING);
                central.stopScan();
                central.connectPeripheral(peripheral, peripheralCallback);
            } else {
                Log.i(TAG, "Wrong device found " + peripheral.getName());
            }
        }

        @Override
        public void onBluetoothAdapterStateChanged(int state) {
            Log.i(TAG, "bluetooth adapter changed state to " + state);
            if (state == BluetoothAdapter.STATE_ON) {
                // Bluetooth is on now, start scanning again
                start();
            }
        }
    };

    private void processBytes(@NonNull byte[] value) {
        protocol.processBytes(value);
    }

    void sendCommand(byte[] value) {
        Log.d(TAG, "phone -> ap: cmd " + (char) value[0]);
        final BluetoothGattCharacteristic ch = getCharacteristic();
        if (ch != null) {
            if (!currentPeripheral.writeCharacteristic(ch, value, WriteType.WITH_RESPONSE)) {
                Log.e(TAG, "Failed to send cmd " + (char) value[0]);
            }
        } else {
            Log.e(TAG, "Failed to get characteristic");
        }
    }

    @Nullable
    private BluetoothGattCharacteristic getCharacteristic() {
        if (currentCharacteristic == null && currentPeripheral != null) {
            if (connected_ap) {
                currentCharacteristic = currentPeripheral.getCharacteristic(apServiceId, apCharacteristicId);
            } else if (connected_rc) {
                currentCharacteristic =  currentPeripheral.getCharacteristic(rcServiceId, rcCharacteristicId);
            }
        }
        return currentCharacteristic;
    }

    /**
     * Terminate an existing connection (because we're switching devices)
     */
    void disconnect() {
        currentCharacteristic = null;
        if (currentPeripheral != null) {
            // will receive callback in onDisconnectedPeripheral
            currentPeripheral.cancelConnection();
        } else if (service.getState() == BT_SCANNING) {
            // Searching for other device, stop and restart search
            Log.d(TAG, "Restarting current scan");
            central.stopScan();
            service.setState(BT_STARTING);
            scan();
        }
    }

    void stop() {
        currentCharacteristic = null;
        service.setState(BT_STOPPING);
        // Stop scanning
        central.stopScan();
        if (currentPeripheral != null) {
            currentPeripheral.cancelConnection();
        }
        // Don't close central because it won't come back if we re-start
//        central.close();
        service.setState(BT_STOPPED);
    }

}