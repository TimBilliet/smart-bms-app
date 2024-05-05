package com.example.bmsapp;

import android.Manifest;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.OnBackPressedDispatcher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.SwitchPreference;
import androidx.preference.SwitchPreferenceCompat;

//import com.example.bmsapp.databinding.FragmentSettingsBinding;


import java.util.Objects;
import java.util.regex.Pattern;

public class SettingsFragment extends PreferenceFragmentCompat {

    private static final String MAC_ADDRESS_PATTERN = "^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$";
    public static final String TAG = "Settingsfragment";
    private static final Pattern pattern = Pattern.compile(MAC_ADDRESS_PATTERN);
    private String macAddress;
    public static String enteredUUID;
    private Context mContext;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothGatt bluetoothGatt;
    private BluetoothGattService gattService;
    private boolean isConnected = false;
    public static PreferenceCategory parameterCategory;
    private EditTextPreference macAddressPreference;
    private EditTextPreference serviceUuidPreference;


    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        mContext = context;
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        MainActivity mainActivity = (MainActivity) getActivity();
        if (mainActivity != null) {
            bluetoothAdapter = mainActivity.getBluetoothAdapter();
        }

        setPreferencesFromResource(R.xml.root_preferences, rootKey);
        macAddressPreference = findPreference("mac_address");
        serviceUuidPreference = findPreference("service_uuid");
        if (macAddressPreference != null && serviceUuidPreference != null) {
            macAddressPreference.setPositiveButtonText("Connect");
            macAddressPreference.setOnBindEditTextListener(
                    editText -> editText.setHint("AA:AA:AA:AA:AA:AA"));
            macAddressPreference.setOnPreferenceChangeListener((preference, macAddress) -> {
                enteredUUID = serviceUuidPreference.getText();
                if (!pattern.matcher(macAddress.toString()).matches()) {
                    showDialog("Invalid MAC address", "OK");
                } else if (!isValidUUID(serviceUuidPreference.getText())) {
                    showDialog("Invalid service UUID", "OK");
                } else {
                    this.macAddress = (String) macAddress;
                    //connectToDevice(this.macAddress);
                    if(mainActivity != null) {
                        mainActivity.connectToDevice(this.macAddress);
                    }
                }
                return true;
            });
            serviceUuidPreference.setOnBindEditTextListener(
                    editText -> editText.setHint("0x0000"));

            serviceUuidPreference.setOnPreferenceChangeListener((preference, serviceUUID) -> {
                if (isValidUUID((String) serviceUUID)) {
                    enteredUUID = (String) serviceUUID;
                    Log.d(TAG, enteredUUID);
                }
                return true;
            });
        }
        parameterCategory = findPreference("param_category");
        if (parameterCategory != null) {
            parameterCategory.setVisible(false);
        }


    }

    public static boolean isValidUUID(String input) {
        if (input == null || input.length() != 6) {
            return false;
        }
        if (!input.startsWith("0x")) {
            return false;
        }
        String hexPattern = "[0-9A-Fa-f]+";
        String hexDigits = input.substring(2);
        return hexDigits.matches(hexPattern);
    }

    private void showDialog(String message, String okButton) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setMessage(message)
                .setPositiveButton(okButton, (dialog, which) -> {

                });
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    public static String convertToUpperCase(String address) {
        if (address == null || address.isEmpty()) {
            return address;
        }
        StringBuilder converted = new StringBuilder();
        for (char c : address.toCharArray()) {
            if (Character.isLetter(c)) {
                converted.append(Character.toUpperCase(c));
            } else {
                converted.append(c);
            }
        }
        return converted.toString();
    }

    public void connectToDevice(String address) {
        address = convertToUpperCase(address);
        BluetoothDevice devicee = bluetoothAdapter.getRemoteDevice(address);
        if (ActivityCompat.checkSelfPermission(requireActivity(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        bluetoothGatt = devicee.connectGatt(getActivity(), false, gattCallback);
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            Log.d(TAG, "connection state changed");
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                // Device connected, discover services
                if (ActivityCompat.checkSelfPermission(requireActivity(), android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    return;
                }

                requireActivity().runOnUiThread(() -> showDialog("Connection to BMS successful", "OK"));
                Log.d(TAG, "CONNECTED");
                isConnected = true;
                parameterCategory.setVisible(true);
                gatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                isConnected = false;
                requireActivity().runOnUiThread(() -> {
                    Log.d(TAG, "DISCONNECTED");
                    showDialog("Disconnected from BMS", "OK");
                });
                parameterCategory.setVisible(false);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            // Services discovered, you can now interact with the BLE server
            Log.d(TAG, "services discovered");
            boolean serviceFound = false;
            Log.d(TAG, enteredUUID.substring(2));
            for (BluetoothGattService gattService : bluetoothGatt.getServices()) {
                if (gattService.getUuid().toString().substring(4, 8).equals(enteredUUID.substring(2))) {
                    serviceFound = true;
                }
            }
            if (serviceFound) {
                Log.d(TAG, "SERVICE UUID MATCH");
                //do stuff
            } else {
                if (ActivityCompat.checkSelfPermission(requireActivity(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    return;
                }
                isConnected = false;
                parameterCategory.setVisible(false);
                bluetoothGatt.close();
                bluetoothGatt = null;
                requireActivity().runOnUiThread(() -> {
                    showDialog("Disconnected from BMS, no service found", "OK");

                });
            }


        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            // Characteristic read callback
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            // Characteristic write callback
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            // Characteristic notification/indication received
        }
    };

}
