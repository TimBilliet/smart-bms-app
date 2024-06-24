package com.example.bmsapp;

import android.Manifest;
import android.annotation.SuppressLint;
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
@SuppressLint("MissingPermission")
public class BluetoothLeService extends Service {
    private BluetoothManager bluetoothManager;
    private BluetoothAdapter bluetoothAdapter;
    public static final String TAG = "BluetoothLeService";
    private BluetoothGatt bluetoothGatt;
    private final IBinder binder = new LocalBinder();
    private final Handler handlerToast = new Handler(Looper.getMainLooper());
    private final List<BluetoothGattCharacteristic> bluetoothGattCharacteristicList = new ArrayList<>();
    private final List<BluetoothGattCharacteristic> tempBluetoothGattCharacteristicList = new ArrayList<>();
    private final List<BluetoothGattCharacteristic> homefragmentBluetoothGattCharacteristicList = new ArrayList<>();
    private final List<BluetoothGattCharacteristic> tempHomefragmentBluetoothGattCharacteristicList = new ArrayList<>();
    private final List<BluetoothGattDescriptor> characteristicsToGetNotificationsOnList = new ArrayList<>();
    private final List<BluetoothGattCharacteristic> parameterCharacteristicList = new ArrayList<>();
    private boolean readingHomefragmentCharacteristics = false;
    private boolean isMinimized = false;
    private SharedPreferences sharedPreferences;
    private BluetoothDevice device;

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
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        return true;
    }


    public void connect(final String address) {
        if (bluetoothAdapter == null || address == null) {
            handlerToast.post(() -> Toast.makeText(this, "BluetoothAdapter not initialized or unspecified address.", Toast.LENGTH_LONG).show());
            return;
        }

        device = bluetoothAdapter.getRemoteDevice(address);
        if (device == null) {
            handlerToast.post(() -> Toast.makeText(this, "Device not found. Unable to connect.", Toast.LENGTH_LONG).show());
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
        } else if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        bluetoothGatt = device.connectGatt(this, false, gattCallback);
    }

    public int getConnectionState() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                return bluetoothManager.getConnectionState(device, BluetoothGatt.GATT);
            }
        } else if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED) {
            return bluetoothManager.getConnectionState(device, BluetoothGatt.GATT);
        }
        return 99;
    }

    private void toggleFaultNotifications() {
        UUID cccdUuid = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
        BluetoothGattCharacteristic faultCharacteristic = getCharacteristicByUUID("3007");
        bluetoothGatt.setCharacteristicNotification(faultCharacteristic, true);
        if (faultCharacteristic != null) {
            BluetoothGattDescriptor faultDescriptor = faultCharacteristic.getDescriptor(cccdUuid);
            faultDescriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            bluetoothGatt.writeDescriptor(faultDescriptor);
        }
    }

    public void toggleNotifications(byte[] value) {
        UUID cccdUuid = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

        BluetoothGattCharacteristic voltageCurrentCharacteristic = getCharacteristicByUUID("3001");
        BluetoothGattCharacteristic cellVoltageCharacteristic = getCharacteristicByUUID("3002");
        BluetoothGattCharacteristic cellBalancingStateCharacteristic = getCharacteristicByUUID("3003");
        BluetoothGattCharacteristic balancingCharacteristic = getCharacteristicByUUID("3005");
        BluetoothGattCharacteristic chargingCharacteristic = getCharacteristicByUUID("3006");
        bluetoothGatt.setCharacteristicNotification(voltageCurrentCharacteristic, true);
        bluetoothGatt.setCharacteristicNotification(cellVoltageCharacteristic, true);
        bluetoothGatt.setCharacteristicNotification(cellBalancingStateCharacteristic, true);
        bluetoothGatt.setCharacteristicNotification(balancingCharacteristic, true);
        bluetoothGatt.setCharacteristicNotification(chargingCharacteristic, true);
        if (voltageCurrentCharacteristic != null && cellVoltageCharacteristic != null && cellBalancingStateCharacteristic != null && balancingCharacteristic != null && chargingCharacteristic != null) {
            BluetoothGattDescriptor voltageCurrentDescriptor = voltageCurrentCharacteristic.getDescriptor(cccdUuid);
            BluetoothGattDescriptor cellVoltageDescriptor = cellVoltageCharacteristic.getDescriptor(cccdUuid);
            BluetoothGattDescriptor cellBalancingStateDescriptor = cellBalancingStateCharacteristic.getDescriptor(cccdUuid);
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
    }

    public void writeDescriptors() {
        if (!characteristicsToGetNotificationsOnList.isEmpty()) {
            bluetoothGatt.writeDescriptor(characteristicsToGetNotificationsOnList.get(characteristicsToGetNotificationsOnList.size() - 1));
        }
    }

    public void logQuick(String message) {
        Log.d(TAG, message);
    }

    public void setIsMinimized(boolean minimized) {
        isMinimized = minimized;
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(TAG, "Connected to GATT server.");
                handlerToast.post(() -> Toast.makeText(getApplicationContext(), "Connected", Toast.LENGTH_LONG).show());
                Intent intent = new Intent("CONNECTION_STATE_CHANGED");
                intent.putExtra("CONNECTION_STATE_CHANGED", true);
                logQuick("sending broadcast from bleservice");
                sendBroadcast(intent);
                bluetoothGatt.discoverServices();

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(TAG, "Disconnected from GATT server.");
                handlerToast.post(() -> Toast.makeText(getApplicationContext(), "Disconnected", Toast.LENGTH_LONG).show());
                Intent intent = new Intent("CONNECTION_STATE_CHANGED");
                intent.putExtra("CONNECTION_STATE_CHANGED", false);
                sendBroadcast(intent);
                homefragmentBluetoothGattCharacteristicList.clear();
                tempHomefragmentBluetoothGattCharacteristicList.clear();
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
                                    || mCharacteristic.getUuid().toString().startsWith("3003", 4) || mCharacteristic.getUuid().toString().startsWith("3005", 4)
                                    || mCharacteristic.getUuid().toString().startsWith("3006", 4) || mCharacteristic.getUuid().toString().startsWith("4008", 4)) {
                                homefragmentBluetoothGattCharacteristicList.add(mCharacteristic);
                            }
                            if(mCharacteristic.getUuid().toString().startsWith("4001", 4) || mCharacteristic.getUuid().toString().startsWith("4002", 4)
                                    || mCharacteristic.getUuid().toString().startsWith("4003", 4) || mCharacteristic.getUuid().toString().startsWith("4004", 4)
                                    || mCharacteristic.getUuid().toString().startsWith("4005", 4) || mCharacteristic.getUuid().toString().startsWith("4006", 4)
                                    || mCharacteristic.getUuid().toString().startsWith("4008", 4)) {
                                parameterCharacteristicList.add(mCharacteristic);
                            }
                        }
                    }
                    logQuick("onServicesDiscovered UUID: " + gattService.getUuid().toString());
                }
                tempHomefragmentBluetoothGattCharacteristicList.addAll(homefragmentBluetoothGattCharacteristicList);
                logQuick("homefraglist size: " + tempHomefragmentBluetoothGattCharacteristicList.size());
                tempBluetoothGattCharacteristicList.addAll(bluetoothGattCharacteristicList);
                toggleFaultNotifications();

            }
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            if (!descriptor.getCharacteristic().getUuid().toString().startsWith("3007", 4)) {
                characteristicsToGetNotificationsOnList.remove(characteristicsToGetNotificationsOnList.get(characteristicsToGetNotificationsOnList.size() - 1));
                if (!characteristicsToGetNotificationsOnList.isEmpty()) {
                    writeDescriptors();
                } else {
                    logQuick("all descriptors written");

                }
            } else {//always do this
                writeAllParametersToBMS();
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            if (!isMinimized && !characteristic.getUuid().toString().startsWith("3007", 4)) {
                String uuid = characteristic.getUuid().toString().substring(4, 8);
                byte[] data = characteristic.getValue();
                Intent intent = new Intent(uuid);
                intent.putExtra(uuid, data);
                sendBroadcast(intent);
            } else if (characteristic.getUuid().toString().startsWith("3007", 4)) {
                String uuid = characteristic.getUuid().toString().substring(4, 8);
                byte[] data = characteristic.getValue();
                Intent intent = new Intent(uuid);
                intent.putExtra(uuid, data);
                sendBroadcast(intent);
            }
        }
        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            logQuick("written to: " + characteristic.getUuid().toString().substring(4, 8));
            logQuick("size: " + parameterCharacteristicList.size());
            if(!parameterCharacteristicList.isEmpty()) {
                parameterCharacteristicList.remove(parameterCharacteristicList.get(parameterCharacteristicList.size() - 1));
                if(!parameterCharacteristicList.isEmpty()) {
                    writeAllParametersToBMS();
                } else {//all parameters were written to the bms, now read the initial battery information
                    logQuick("auto update state: " + sharedPreferences.getBoolean("auto_update", false));
                    Intent intent = new Intent("READY_TO_READ_CHARS");
                    intent.putExtra("READY_TO_READ_CHARS", true);
                    sendBroadcast(intent);
                }
            }
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
        logQuick("request chars");
        bluetoothGatt.readCharacteristic(tempBluetoothGattCharacteristicList.get(tempBluetoothGattCharacteristicList.size() - 1));
    }

    public void writeAllParametersToBMS() {

        if(!parameterCharacteristicList.isEmpty()) {
            boolean checked;
            byte[] data = new byte[0];
            logQuick("size of params: " + parameterCharacteristicList.size());
            switch (parameterCharacteristicList.get((parameterCharacteristicList.size() - 1)).getUuid().toString().substring(4,8)) {
                case "4008":
                    checked = sharedPreferences.getBoolean("only_balance_while_charging", false);
                    data = new byte[1];
                    data[0] = (byte) (checked ? 1 : 0);
                    logQuick("char is 4008");
                    break;
                case "4001":
                    data = new byte[1];
                    data[0] = Byte.parseByte(sharedPreferences.getString("shunt_value", "5"));
                    break;
                case "4002":
                    data = new byte[2];
                    data[0] = (byte) (Integer.parseInt(sharedPreferences.getString("overcharge_current", "10000") ) & 0xFF);
                    data[1] = (byte) ((Integer.parseInt(sharedPreferences.getString("overcharge_current", "10000")) >> 8) & 0xFF);
                    break;
                case "4003":
                    data = new byte[2];
                    data[0] = (byte) (Integer.parseInt(sharedPreferences.getString("undervolt", "3000")) & 0xFF);
                    data[1] = (byte) ((Integer.parseInt(sharedPreferences.getString("undervolt", "3000")) >> 8) & 0xFF);
                    break;
                case "4004":
                    data = new byte[2];
                    data[0] = (byte) (Integer.parseInt(sharedPreferences.getString("overvolt", "4200")) & 0xFF);
                    data[1] = (byte) ((Integer.parseInt(sharedPreferences.getString("overvolt", "4200")) >> 8) & 0xFF);
                    break;
                case "4005":
                    data = new byte[4];
                    //data[0] = Byte.parseByte(sharedPreferences.getString("min_balance_voltage", "5"));
                    //datargernge

                    data[0] = (byte) ((Integer.parseInt(sharedPreferences.getString("min_balance_voltage", "3900")) & 0xFF));
                    data[1] = (byte) ((Integer.parseInt(sharedPreferences.getString("min_balance_voltage", "3900")) >> 8) & 0xFF);
                    data[2] = (byte) (Integer.parseInt(sharedPreferences.getString("max_cell_voltage_diff", "15")) & 0xFF);
                    data[3] = (byte) ((Integer.parseInt(sharedPreferences.getString("max_cell_voltage_diff", "15")) >> 8) & 0xFF);
                    break;
                case "4006":
                    data = new byte[1];
                    data[0] = Byte.parseByte(sharedPreferences.getString("idle_current_threshold", "100"));
                    break;
            }
            writeCharacteristic((parameterCharacteristicList.get((parameterCharacteristicList.size() - 1)).getUuid().toString().substring(4,8)), data);
        }
    }
    public void requestHomefragmentCharacteristics() {
        readingHomefragmentCharacteristics = true;
        logQuick("request homefrag chars");
        if (!tempHomefragmentBluetoothGattCharacteristicList.isEmpty()) {
            bluetoothGatt.readCharacteristic(tempHomefragmentBluetoothGattCharacteristicList.get(tempHomefragmentBluetoothGattCharacteristicList.size() - 1));
        }
    }

    public void readCharacteristic(String uuid) {
        BluetoothGattCharacteristic characteristic = getCharacteristicByUUID(uuid);
        bluetoothGatt.readCharacteristic(characteristic);
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
        requestCharacteristics();
    }

    public void readCharacteristicsForHomefragment() {
        if (bluetoothAdapter == null || bluetoothGatt == null) {
            logQuick("BluetoothAdapter not initialized");
            return;
        }
        readingHomefragmentCharacteristics = true;
        requestHomefragmentCharacteristics();
    }

    public void writeCharacteristic(String uuid, byte[] value) {
        logQuick("uuid: " + uuid);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (getConnectionState() == BluetoothProfile.STATE_CONNECTED) {
                bluetoothGatt.writeCharacteristic(Objects.requireNonNull(getCharacteristicByUUID(uuid)), value, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
            }
        } else if (getConnectionState() == BluetoothProfile.STATE_CONNECTED) {
            BluetoothGattCharacteristic characteristic = getCharacteristicByUUID(uuid);
            if (characteristic != null) {
                characteristic.setValue(value);
                bluetoothGatt.writeCharacteristic(characteristic);
            }
        }
    }
}
