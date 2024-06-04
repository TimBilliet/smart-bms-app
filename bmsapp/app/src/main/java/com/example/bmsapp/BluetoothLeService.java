package com.example.bmsapp;

import android.Manifest;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
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

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;
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
    private final ArrayList<BluetoothGattCharacteristic> bluetoothGattCharacteristicList = new ArrayList<>();
    private ArrayList<BluetoothGattCharacteristic> tempBluetoothGattCharacteristicList = new ArrayList<>();

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
        logQuick("bleservice init");
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
        //updateInterval =  (long)(1000L * Float.parseFloat(sharedPreferences.getString("update_interval", "default value")));
        handler = new Handler();
        runnable = new Runnable() {
            @Override
            public void run() {
                if (updateInterval > 0) {
                    logQuick("Timer");
                    logQuick(String.valueOf(updateInterval));
                    readAllCharacteristics();
                    handler.postDelayed(this, updateInterval);
                }

            }
        };
        return true;
    }

    public boolean connect(final String address) {
        if (bluetoothAdapter == null || address == null) {
            handlerToast.post(() -> Toast.makeText(this, "BluetoothAdapter not initialized or unspecified address.", Toast.LENGTH_LONG).show());
            return false;
        }

        final BluetoothDevice device = bluetoothAdapter.getRemoteDevice(address);
        if (device == null) {
            handlerToast.post(() -> Toast.makeText(this, "Device not found. Unable to connect.", Toast.LENGTH_LONG).show());
            return false;
        }

        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return true;
        }
        bluetoothGatt = device.connectGatt(this, false, gattCallback);
        return true;
    }

    public void runUpdateTimer() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        try {
            updateInterval = (long) (1000L * Float.parseFloat(sharedPreferences.getString("update_interval", "0")));

        } catch (Exception e) {

        }
        //if(updateInterval!= 0) {
        handler.postDelayed(runnable, updateInterval);
        // }
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
                Intent intent = new Intent("connection_state_change");
                intent.putExtra("is_connected", true);
                sendBroadcast(intent);
                bluetoothGatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(TAG, "Disconnected from GATT server.");
                //Toast.makeText(getApplicationContext(), "Disconnected.", Toast.LENGTH_LONG).show();
                // Toast.makeText(this, "test, ")
                handlerToast.post(() -> Toast.makeText(getApplicationContext(), "Disconnected.", Toast.LENGTH_LONG).show());
                Intent intent = new Intent("connection_state_change");
                handler.removeCallbacks(runnable);//stop timer
                intent.putExtra("is_connected", false);
                sendBroadcast(intent);
                isConnected = false;
            }
        }

        // New services discovered
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                for (BluetoothGattService gattService : gatt.getServices()) {
                    if ((gattService.getUuid().toString()).startsWith("3000", 4) || (gattService.getUuid().toString()).startsWith("4000", 4)) {
                        for (BluetoothGattCharacteristic mCharacteristic : gattService.getCharacteristics()) {
                            Log.i(TAG, "Found Characteristic: " + mCharacteristic.getUuid().toString());
                            bluetoothGattCharacteristicList.add(mCharacteristic);
                        }
                    }
                    Log.i(TAG, "onServicesDiscovered UUID: " + gattService.getUuid().toString());
                }
                tempBluetoothGattCharacteristicList.addAll(bluetoothGattCharacteristicList);
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            String uuid = characteristic.getUuid().toString().substring(4, 8);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Intent intent;
                byte[] data = characteristic.getValue();
                switch (uuid) {
                    case "3001":  // battery pack voltage read
                        intent = new Intent("PACK_VOLTAGE");
                        intent.putExtra("PACK_VOLTAGE", data);
                        sendBroadcast(intent);
                        break;
                    case "3002":  // cell voltages read
                        intent = new Intent("CELL_VOLTAGES");
                        intent.putExtra("CELL_VOLTAGES", data);
                        sendBroadcast(intent);
                        break;
                    case "3003":  // cell balancing state read
                        intent = new Intent("CELL_BALANCING_STATE");
                        intent.putExtra("CELL_BALANCING_STATE", data);
                        sendBroadcast(intent);
                        break;
                    case "3004": // charge current read
                        intent = new Intent("CHARGE_CURRENT");
                        intent.putExtra("CHARGE_CURRENT", data);
                        sendBroadcast(intent);
                        break;
                    case "3005":
                        break;
                    case "3006":
                        break;
                }
                tempBluetoothGattCharacteristicList.remove(tempBluetoothGattCharacteristicList.get(tempBluetoothGattCharacteristicList.size() - 1));
                if (!tempBluetoothGattCharacteristicList.isEmpty()) {
                    requestCharacteristics(bluetoothGatt);
                } else {
                    logQuick("all chars read");
                    tempBluetoothGattCharacteristicList.addAll(bluetoothGattCharacteristicList);
                }
            }
        }
    };

    public void requestCharacteristics(BluetoothGatt gatt) {
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        gatt.readCharacteristic(tempBluetoothGattCharacteristicList.get(tempBluetoothGattCharacteristicList.size() - 1));
    }

    public BluetoothGatt getBluetoothGatt() {
        return bluetoothGatt;
    }

    private BluetoothGattCharacteristic getCharacteristicByUUID(String uuid) {
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
        requestCharacteristics(bluetoothGatt);
    }

    public void writeCharacteristic(String uuid, byte[] value) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            bluetoothGatt.writeCharacteristic(Objects.requireNonNull(getCharacteristicByUUID(uuid)), value, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
        }
    }
}
