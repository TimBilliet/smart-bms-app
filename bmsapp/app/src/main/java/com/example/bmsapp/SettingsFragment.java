package com.example.bmsapp;

import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.preference.EditTextPreference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

//import com.example.bmsapp.databinding.FragmentSettingsBinding;


import java.util.Objects;
import java.util.regex.Pattern;

public class SettingsFragment extends PreferenceFragmentCompat {

    private static final String MAC_ADDRESS_PATTERN = "^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$";
    public static final String TAG = "Settingsfragment";
    private static final Pattern pattern = Pattern.compile(MAC_ADDRESS_PATTERN);
    private String macAddress;
    private boolean isConnected = false;
    private EditTextPreference macAddressPreference;
    private BluetoothLeService bluetoothLeService;


    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey);
        macAddressPreference = findPreference("mac_address");
        if (macAddressPreference != null) {
            macAddressPreference.setPositiveButtonText("Connect");
            macAddressPreference.setOnBindEditTextListener(
                    editText -> editText.setHint("AA:AA:AA:AA:AA:AA"));
            macAddressPreference.setOnPreferenceChangeListener((preference, macAddress) -> {
                if (!isValidMacAddress((String) macAddress)) {
                    showDialog("Invalid MAC address");
                } else {
                    this.macAddress = convertToUpperCase((String) macAddress);
                    Intent gattServiceIntent = new Intent(getActivity(), BluetoothLeService.class);
                    requireActivity().bindService(gattServiceIntent, serviceConnection, Context.BIND_AUTO_CREATE);

                }
                return true;
            });
        }
    }
    private BroadcastReceiver connectionStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Handle the connection state change here
            boolean isConnected = intent.getBooleanExtra("is_connected", false);
            // Update UI or preferences based on the connection state
            if(isConnected) {
                logQuick("CONNECTED OMAGOSH IN SETTINGSFRAG");
                //Save mac address so it can be used later to automatically connect
                SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireActivity());
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putString("mac_address", macAddress);
                logQuick(macAddress);
                editor.apply();
                showDialog("Connected to: " + macAddress);
            } else {
                logQuick("DISCONNECTED OMAGOSH IN SETTINGSFRAG");
                showDialog("Disconnected from: " + macAddress);
            }
        }
    };
    public boolean isValidMacAddress(String macAddress) {
        final String MAC_ADDRESS_PATTERN = "^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$";
        final Pattern pattern = Pattern.compile(MAC_ADDRESS_PATTERN);
        return pattern.matcher(macAddress).matches();
    }
    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            BluetoothLeService.LocalBinder binder = (BluetoothLeService.LocalBinder) service;
            bluetoothLeService = binder.getService();
            if (!bluetoothLeService.initialize()) {
                Log.e("BLE", "Unable to initialize Bluetooth");
                getActivity().finish();
            }

            boolean connectStatus = bluetoothLeService.connect(macAddress);

            if(!connectStatus) {
                showDialog("Connection failed");
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            bluetoothLeService = null;
        }
    };
    @Override
    public void onResume() {
        super.onResume();
        IntentFilter intentFilter = new IntentFilter("connection_state_change");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            requireActivity().registerReceiver(connectionStateReceiver, intentFilter, Context.RECEIVER_NOT_EXPORTED);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        requireActivity().unregisterReceiver(connectionStateReceiver);
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

    private void showDialog(String message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setMessage(message)
                .setPositiveButton("OK", (dialog, which) -> {

                });
        AlertDialog dialog = builder.create();
        dialog.show();
    }
    public void logQuick(String message) {
        Log.d(TAG, message);
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

}
