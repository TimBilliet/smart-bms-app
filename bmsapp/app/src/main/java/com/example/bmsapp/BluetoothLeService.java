package com.example.bmsapp;

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
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.preference.PreferenceManager;

import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

public class BluetoothLeService extends Service {
    private BluetoothManager bluetoothManager;
    private BluetoothAdapter bluetoothAdapter;
    public static final String TAG = "BluetoothLeService";
    private BluetoothGatt bluetoothGatt;
    private long updateInterval;
    private Handler handler;
    private Runnable runnable;
    private float batVoltage;
    private boolean isConnected = false;
    private final IBinder binder = new LocalBinder();
    private final Handler handlerToast = new Handler(Looper.getMainLooper());

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
                if(updateInterval > 0) {
                    logQuick("Timer");
                    logQuick(String.valueOf(updateInterval));
                    BluetoothGattCharacteristic characteristic = getCharacteristic();
                    if (characteristic != null) {
                        readCharacteristic(characteristic);
                    } else {
                        Log.w(TAG, "Characteristic x3001 not found");
                    }
                    handler.postDelayed(this, updateInterval);
                }

            }
        };
        return true;
    }
    public BluetoothGattCharacteristic getCharacteristic() {
        if(isConnected) {
            String shortUuid = "0x3000"; //bat voltage characteristic
            String shortchar = "0x3001";
            // Convert the 16-bit UUID to 128-bit format
            String longUuid = shortUuid.replace("0x", "") + "-0000-1000-8000-00805F9B34FB";
            String longUuidChar = shortchar.replace("0x", "") + "-0000-1000-8000-00805F9B34FB";
            // Create the UUID object
            UUID serviceUuid = UUID.fromString(longUuid);
            UUID charUuid = UUID.fromString(longUuidChar);
            BluetoothGattService service = bluetoothGatt.getService(serviceUuid);
            if (service != null) {
                return service.getCharacteristic(charUuid);
            } else {
                Toast toast = Toast.makeText(this, "No service found.", Toast.LENGTH_LONG);
                toast.show();
                return null;
            }
        } else {
            Toast.makeText(this, "Not connected.", Toast.LENGTH_LONG).show();
            return null;
        }
    }
    public boolean connect(final String address) {
        if (bluetoothAdapter == null || address == null) {
            handlerToast.post(()->Toast.makeText(this, "BluetoothAdapter not initialized or unspecified address.", Toast.LENGTH_LONG).show());
            return false;
        }

        final BluetoothDevice device = bluetoothAdapter.getRemoteDevice(address);
        if (device == null) {
            handlerToast.post(()->Toast.makeText(this, "Device not found. Unable to connect.", Toast.LENGTH_LONG).show());
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
        updateInterval =(long)( 1000L * Float.parseFloat(sharedPreferences.getString("update_interval", "0")));
        //if(updateInterval!= 0) {
            handler.postDelayed(runnable, updateInterval);
       // }
    }
    public void updateInterval(float interval) {
        logQuick("updateinterval ");
        if(interval > 0 && isConnected) {
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
                handlerToast.post(() ->Toast.makeText(getApplicationContext(), "Disconnected.", Toast.LENGTH_LONG).show());
                Intent intent = new Intent("connection_state_change");
                handler.removeCallbacks(runnable);//stop timer
                intent.putExtra("is_connected", false);
                sendBroadcast(intent);
                isConnected = false;
            }
        }
        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            String shortUuid = "0x3001"; //bat voltage characteristic
            String longUuid = shortUuid.replace("0x", "") + "-0000-1000-8000-00805F9B34FB";
            UUID uuid = UUID.fromString(longUuid);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (uuid.equals(characteristic.getUuid())) {
                    byte[] data = characteristic.getValue();
                    int batVoltage = ((data[0] & 0xFF) << 8) | (data[1] & 0xFF);

                    logQuick(String.valueOf(batVoltage));
                    Intent intent = new Intent("BLE_DATA");
                    intent.putExtra("BLE_DATA", String.valueOf(batVoltage));
                    sendBroadcast(intent);
                }
            }
        }
    };

    public BluetoothGatt getBluetoothGatt() {
        return bluetoothGatt;
    }


    public void readCharacteristic(BluetoothGattCharacteristic characteristic) {
        if (bluetoothAdapter == null || bluetoothGatt == null) {
            Log.w("BLESERVICE", "BluetoothAdapter not initialized");
            return;
        }
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        bluetoothGatt.readCharacteristic(characteristic);
    }
}
