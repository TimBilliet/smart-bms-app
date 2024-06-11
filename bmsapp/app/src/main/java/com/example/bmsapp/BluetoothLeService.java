package com.example.bmsapp;

import android.Manifest;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.preference.PreferenceManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class BluetoothLeService extends Service {
    private BluetoothManager bluetoothManager;
    private BluetoothAdapter bluetoothAdapter;
    public static final String TAG = "BluetoothLeService";
    private BluetoothGatt bluetoothGatt;
    private long updateInterval = 0;
    private Handler handler;
    private Runnable runnable;
    private boolean isConnected = false;
    private final IBinder binder = new LocalBinder();
    private final Handler handlerToast = new Handler(Looper.getMainLooper());
    private final List<BluetoothGattCharacteristic> bluetoothGattCharacteristicList = new ArrayList<>();
    private List<BluetoothGattCharacteristic> tempBluetoothGattCharacteristicList = new ArrayList<>();
    private List<BluetoothGattCharacteristic> homefragmentBluetoothGattCharacteristicList = new ArrayList<>();
    private List<BluetoothGattCharacteristic> tempHomefragmentBluetoothGattCharacteristicList = new ArrayList<>();
    private List<BluetoothGattDescriptor> characteristicsToGetNotificationsOnList = new ArrayList<>();
    private boolean readingHomefragmentCharacteristics = false;
    private boolean isHomefragment = false;
    public class LocalBinder extends Binder {
        BluetoothLeService getService() {
            return BluetoothLeService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    public boolean initialize() {
        if (bluetoothManager == null) {
            bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (bluetoothManager == null) {
                Log.e("BLE", "Unable to initialize BluetoothManager.");
                return false;
            }
        }

        bluetoothAdapter = bluetoothManager.getAdapter();
        if (bluetoothAdapter == null) {
            Log.e("BLE", "Unable to obtain a BluetoothAdapter.");
            return false;
        }
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        logQuick(sharedPreferences.getString("update_interval", "1"));
        handler = new Handler();
        runnable = new Runnable() {
            @Override
            public void run() {
                if (updateInterval > 0 && isHomefragment) {
                    logQuick(String.valueOf(updateInterval));
                    requestHomefragmentCharacteristics();
                    handler.postDelayed(this, updateInterval);
                }
            }
        };
        return true;
    }

    public void setIsHomefragment(boolean isHomefragment) {
        this.isHomefragment = isHomefragment;
    }

    public void connect(final String address) {
        if (bluetoothAdapter == null || address == null) {
            handlerToast.post(() -> Toast.makeText(this, "BluetoothAdapter not initialized or unspecified address.", Toast.LENGTH_LONG).show());
            return;
        }

        final BluetoothDevice device = bluetoothAdapter.getRemoteDevice(address);
        if (device == null) {
            handlerToast.post(() -> Toast.makeText(this, "Device not found. Unable to connect.", Toast.LENGTH_LONG).show());
            return;
        }

        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        bluetoothGatt = device.connectGatt(this, false, gattCallback);
    }

    public void runUpdateTimer() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        try {
            updateInterval = (long) (1000L * Float.parseFloat(sharedPreferences.getString("update_interval", "0")));

        } catch (Exception ignored) {

        }
        handler.postDelayed(runnable, updateInterval);
    }

    public void updateInterval(float interval) {
        logQuick("updateinterval ");
        if (interval > 0 && isConnected) {
            updateInterval = (long) (interval * 1000L);
            //Restart timer
            logQuick(String.valueOf(updateInterval));
            handler.removeCallbacks(runnable);
            handler.postDelayed(runnable, updateInterval);
        } else {
            handler.removeCallbacks(runnable);
        }
    }
    private void toggleFaultNotifications() {
        if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        UUID cccdUuid = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
        BluetoothGattCharacteristic faultCharacterisitc = getCharacteristicByUUID("3007");
        bluetoothGatt.setCharacteristicNotification(faultCharacterisitc, true);
        BluetoothGattDescriptor faultDescriptor = faultCharacterisitc.getDescriptor(cccdUuid);
        faultDescriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        bluetoothGatt.writeDescriptor(faultDescriptor);
    }
    public void toggleNotifications(byte[] value) {
        if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        UUID cccdUuid = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

        BluetoothGattCharacteristic voltageCurrentCharacteristic = getCharacteristicByUUID("3001");
        BluetoothGattCharacteristic cellVoltageCharacteristic = getCharacteristicByUUID("3002");
        BluetoothGattCharacteristic cellBalancingStateCharacterisitc = getCharacteristicByUUID("3003");
        BluetoothGattCharacteristic balancingCharacteristic = getCharacteristicByUUID("3005");
        BluetoothGattCharacteristic chargingCharacteristic = getCharacteristicByUUID("3006");
        bluetoothGatt.setCharacteristicNotification(voltageCurrentCharacteristic, true);
        bluetoothGatt.setCharacteristicNotification(cellVoltageCharacteristic, true);
        bluetoothGatt.setCharacteristicNotification(cellBalancingStateCharacterisitc, true);
        bluetoothGatt.setCharacteristicNotification(balancingCharacteristic, true);
        bluetoothGatt.setCharacteristicNotification(chargingCharacteristic, true);

        BluetoothGattDescriptor voltageCurrentDescriptor = voltageCurrentCharacteristic.getDescriptor(cccdUuid);
        BluetoothGattDescriptor cellVoltageDescriptor = cellVoltageCharacteristic.getDescriptor(cccdUuid);
        BluetoothGattDescriptor cellBalancingStateDescriptor = cellBalancingStateCharacterisitc.getDescriptor(cccdUuid);
        BluetoothGattDescriptor balancingDescriptor = balancingCharacteristic.getDescriptor(cccdUuid);
        BluetoothGattDescriptor chargingDescriptor = chargingCharacteristic.getDescriptor(cccdUuid);
        characteristicsToGetNotificationsOnList.add(voltageCurrentDescriptor);
        characteristicsToGetNotificationsOnList.add(cellVoltageDescriptor);
        characteristicsToGetNotificationsOnList.add(cellBalancingStateDescriptor);
        characteristicsToGetNotificationsOnList.add(balancingDescriptor);
        characteristicsToGetNotificationsOnList.add(chargingDescriptor);

        voltageCurrentDescriptor.setValue(value);
        cellVoltageDescriptor.setValue(value);
        cellBalancingStateDescriptor.setValue(value);
        balancingDescriptor.setValue(value);
        chargingDescriptor.setValue(value);

        writeDescriptors();
    }


    public void writeDescriptors() {
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        if(!characteristicsToGetNotificationsOnList.isEmpty()) {
            bluetoothGatt.writeDescriptor(characteristicsToGetNotificationsOnList.get(characteristicsToGetNotificationsOnList.size() -1 ));
        }
    }
    public void logQuick(String message) {
        Log.d(TAG, message);
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(TAG, "Connected to GATT server.");
                if (ActivityCompat.checkSelfPermission(getApplicationContext(), android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    return;
                }
                isConnected = true;
                Intent intent = new Intent("CONNECTION_STATE_CHANGED");
                intent.putExtra("CONNECTION_STATE_CHANGED", true);
                sendBroadcast(intent);
                bluetoothGatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(TAG, "Disconnected from GATT server.");
                handlerToast.post(() -> Toast.makeText(getApplicationContext(), "Disconnected.", Toast.LENGTH_LONG).show());
                Intent intent = new Intent("CONNECTION_STATE_CHANGED");
                handler.removeCallbacks(runnable);//stop timer
                intent.putExtra("CONNECTION_STATE_CHANGED", false);
                sendBroadcast(intent);
                isConnected = false;
            }
        }

        // Services discovered
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                for (BluetoothGattService gattService : gatt.getServices()) {
                    if ((gattService.getUuid().toString()).startsWith("3000", 4) || (gattService.getUuid().toString()).startsWith("4000", 4)) {
                        for (BluetoothGattCharacteristic mCharacteristic : gattService.getCharacteristics()) {
                            Log.i(TAG, "Found Characteristic: " + mCharacteristic.getUuid().toString());
                            bluetoothGattCharacteristicList.add(mCharacteristic);
                            if (mCharacteristic.getUuid().toString().startsWith("3001", 4) || mCharacteristic.getUuid().toString().startsWith("3002", 4)
                                    || mCharacteristic.getUuid().toString().startsWith("3003", 4) || mCharacteristic.getUuid().toString().startsWith("4008", 4)) {
                                homefragmentBluetoothGattCharacteristicList.add(mCharacteristic);
                            }
                        }
                    }
                    logQuick("onServicesDiscovered UUID: " + gattService.getUuid().toString());
                }
                tempHomefragmentBluetoothGattCharacteristicList.addAll(homefragmentBluetoothGattCharacteristicList);
                tempBluetoothGattCharacteristicList.addAll(bluetoothGattCharacteristicList);
                toggleFaultNotifications();

            }
        }
        @Override
        public void onDescriptorWrite (BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            logQuick("descriptor write");
            logQuick(descriptor.getUuid().toString().substring(4, 8));
            logQuick(descriptor.getCharacteristic().getUuid().toString().substring(4, 8));
            if(!descriptor.getCharacteristic().getUuid().toString().startsWith("3007", 4)) {
                characteristicsToGetNotificationsOnList.remove(characteristicsToGetNotificationsOnList.get(characteristicsToGetNotificationsOnList.size() - 1));
                if (!characteristicsToGetNotificationsOnList.isEmpty()) {
                    writeDescriptors();
                } else {
                    logQuick("all descriptors written");
                }
            }
        }
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            logQuick("notify received from: " + characteristic.getUuid().toString());
            String uuid = characteristic.getUuid().toString().substring(4, 8);
            byte[] data = characteristic.getValue();
            Intent intent = new Intent(uuid);
            intent.putExtra(uuid, data);
            sendBroadcast(intent);
        }
        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            String uuid = characteristic.getUuid().toString().substring(4, 8);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                byte[] data = characteristic.getValue();
                Intent intent = new Intent(uuid);
                intent.putExtra(uuid, data);
                sendBroadcast(intent);
                if (readingHomefragmentCharacteristics) {
                    tempHomefragmentBluetoothGattCharacteristicList.remove(tempHomefragmentBluetoothGattCharacteristicList.get(tempHomefragmentBluetoothGattCharacteristicList.size() - 1));
                    if (!tempHomefragmentBluetoothGattCharacteristicList.isEmpty()) {
                        requestHomefragmentCharacteristics();
                    } else {
                        readingHomefragmentCharacteristics = false;
                        tempHomefragmentBluetoothGattCharacteristicList.addAll(homefragmentBluetoothGattCharacteristicList);
                    }
                }
                /*
                if (!tempBluetoothGattCharacteristicList.isEmpty()) {
                    requestCharacteristics();
                } else {
                    logQuick("all chars read");
                    end = System.currentTimeMillis();
                    logQuick("end end"+ end);
                    logQuick("Time taken: " + (end - start) + "ms");
                    tempBluetoothGattCharacteristicList.addAll(bluetoothGattCharacteristicList);
                }

                 */
            }
        }
    };

    public void requestCharacteristics() {
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        logQuick("request chars");
        bluetoothGatt.readCharacteristic(tempBluetoothGattCharacteristicList.get(tempBluetoothGattCharacteristicList.size() - 1));
    }

    public void requestHomefragmentCharacteristics() {
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        readingHomefragmentCharacteristics = true;
        logQuick("request homefrag chars");
        if(!tempHomefragmentBluetoothGattCharacteristicList.isEmpty()) {
            bluetoothGatt.readCharacteristic(tempHomefragmentBluetoothGattCharacteristicList.get(tempHomefragmentBluetoothGattCharacteristicList.size() - 1));
        }
    }

    public void readCharacteristic(String uuid) {
        BluetoothGattCharacteristic characteristic = getCharacteristicByUUID(uuid);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        bluetoothGatt.readCharacteristic(characteristic);
    }
    public boolean getConnectionStatus() {
        return isConnected;
    }

    public BluetoothGatt getBluetoothGatt() {
        return bluetoothGatt;
    }

    private BluetoothGattCharacteristic getCharacteristicByUUID(String uuid) {
        logQuick("char list size: " + bluetoothGattCharacteristicList.size());
        for (BluetoothGattCharacteristic characteristic : bluetoothGattCharacteristicList) {
            if (characteristic.getUuid().toString().startsWith(uuid, 4)) {
                return characteristic;
            }
        }
        return null;
    }

    public void readAllCharacteristics() {
        if (bluetoothAdapter == null || bluetoothGatt == null) {
            logQuick("BluetoothAdapter not initialized");
            return;
        }
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        requestCharacteristics();
    }

    public void readCharacteristicsForHomefragment() {
        if (bluetoothAdapter == null || bluetoothGatt == null) {
            logQuick("BluetoothAdapter not initialized");
            return;
        }
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        readingHomefragmentCharacteristics = true;
        requestHomefragmentCharacteristics();
    }

    public void writeCharacteristic(String uuid, byte[] value) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        logQuick("uuid: " + uuid);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && isConnected) {
            bluetoothGatt.writeCharacteristic(Objects.requireNonNull(getCharacteristicByUUID(uuid)), value, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
        }
    }
}
