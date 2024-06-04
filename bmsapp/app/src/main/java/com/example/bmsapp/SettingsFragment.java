package com.example.bmsapp;

import android.app.AlertDialog;
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
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.SwitchPreference;

//import com.example.bmsapp.databinding.FragmentSettingsBinding;


import java.util.Objects;
import java.util.regex.Pattern;

public class SettingsFragment extends PreferenceFragmentCompat {

    private static final String MAC_ADDRESS_PATTERN = "^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$";
    public static final String TAG = "Settingsfragment";
    private static final Pattern pattern = Pattern.compile(MAC_ADDRESS_PATTERN);
    private String macAddress;
    private float updateInterval;
    private boolean isConnected = false;
    private EditTextPreference macAddressPreference;
    private EditTextPreference appUpdateIntervalPreference;
    private SwitchPreference onlyBalanceWhileChargingPreference;
    private EditTextPreference shuntResistorPreference;
    private EditTextPreference overChargeCurrentPreference;
    private EditTextPreference underVoltagePreference;
    private EditTextPreference overVoltagePreference;
    private EditTextPreference minimumBalanceVoltagePreference;
    private EditTextPreference maximumCellVoltageDifferencePreference;
    private EditTextPreference idleCurrentPreference;
    private BluetoothLeService bluetoothLeService;
    private MainActivity mainActivity;
    private SharedPreferences sharedPreferences;

    @Override
    public void onAttach(@NonNull Context context) {

        logQuick("ATTACHED TO SETTINGSFRAG");
        mainActivity = (MainActivity)requireActivity();
        if(mainActivity.getBluetoothservice() != null) {
            bluetoothLeService = mainActivity.getBluetoothservice();
        }
        super.onAttach(context);
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        logQuick("on create preferences");
        setPreferencesFromResource(R.xml.root_preferences, rootKey);
        macAddressPreference = findPreference("mac_address");
        appUpdateIntervalPreference = findPreference("update_interval");
        onlyBalanceWhileChargingPreference = findPreference("only_balance_while_charging");
        shuntResistorPreference = findPreference("shunt_value");
        overChargeCurrentPreference = findPreference("overcharge_current");
        underVoltagePreference = findPreference("undervolt");
        overVoltagePreference = findPreference("overvolt");
        minimumBalanceVoltagePreference = findPreference("min_balance_voltage");
        maximumCellVoltageDifferencePreference = findPreference("max_cell_voltage_diff");
        idleCurrentPreference = findPreference("idle_current_threshold");
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireActivity());
        if (macAddressPreference != null) {
            macAddressPreference.setPositiveButtonText("Connect");
            macAddressPreference.setOnBindEditTextListener(editText -> editText.setHint("AA:AA:AA:AA:AA:AA"));
            macAddressPreference.setOnPreferenceChangeListener((preference, macAddress) -> {
                if (!isValidMacAddress((String) macAddress)) {
                    showDialog("Invalid MAC address");
                } else {
                    this.macAddress = convertToUpperCase((String) macAddress);
                    Intent gattServiceIntent = new Intent(getActivity(), BluetoothLeService.class);
                    requireActivity().bindService(gattServiceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
                    updateInterval = Float.parseFloat(sharedPreferences.getString("update_interval", "1"));
                }
                return true;
            });
        }
        if(appUpdateIntervalPreference != null) {
            appUpdateIntervalPreference.setOnPreferenceChangeListener((preference, interval) -> {
                if(isValidDelay((String) interval)) {
                    if(Float.parseFloat((String) interval) >= 0) {
                        updateInterval = Float.parseFloat((String) interval);
                        appUpdateIntervalPreference.setText((String) interval);
                        //sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireActivity());
                        SharedPreferences.Editor editor = sharedPreferences.edit();
                        editor.putString("update_interval", (String) interval);
                        editor.apply();
                        logQuick( "is connected: " + isConnected);
                        bluetoothLeService.updateInterval(updateInterval);
                    }
                } else {
                    showDialog("Invalid input\nMaximum 3 digits after the decimal point");
                }

                return true;
            });
        }
        if(onlyBalanceWhileChargingPreference != null) {
            onlyBalanceWhileChargingPreference.setOnPreferenceChangeListener(((preference, toggle) -> {
                if(bluetoothLeService != null) {
                    byte[] data = {0};
                    if ((boolean) toggle) {
                        logQuick("ON ON WE OIN");
                        data[0] = 1;
                    } else {
                        logQuick("we off");
                    }
                   bluetoothLeService.writeCharacteristic("4008", data);

                }
                return true;
            }));
        }
        bluetoothLeService.readAllCharacteristics();
    }

    private BroadcastReceiver bleUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            logQuick(action);
            byte[] data;
            switch (Objects.requireNonNull(action)) {
                case "CONNECTION_STATE_CHANGED":
                    if(intent.getBooleanExtra("CONNECTION_STATE_CHANGED", false)) {
                        isConnected = true;
                        bluetoothLeService.runUpdateTimer();
                        //Save mac address so it can be used later to automatically connect
                        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireActivity());
                        SharedPreferences.Editor editor = sharedPreferences.edit();
                        editor.putString("mac_address", macAddress);
                        logQuick(macAddress);
                        editor.apply();
                        showDialog("Connected to: " + macAddress);
                    }
                    break;
                case "SHUNT_RESISTOR":
                    data = intent.getByteArrayExtra("SHUNT_RESISTOR");
                    shuntResistorPreference.setText(String.valueOf(data[0]));
                    break;
                case "OVERCURRENT":
                    data = intent.getByteArrayExtra("OVERCURRENT");
                    int current = ((data[0] & 0xFF) << 8) | (data[1] & 0xFF);
                    overChargeCurrentPreference.setText(String.valueOf(current));
                    break;
                case "UNDERVOLT":
                    data = intent.getByteArrayExtra("UNDERVOLT");
                    int undervolt = ((data[0] & 0xFF) << 8) | (data[1] & 0xFF);
                    underVoltagePreference.setText(String.valueOf(undervolt));
                    break;
                case "OVERVOLT":
                    data = intent.getByteArrayExtra("OVERVOLT");
                    int overvolt = ((data[0] & 0xFF) << 8) | (data[1] & 0xFF);
                    overVoltagePreference.setText(String.valueOf(overvolt));
                    break;
                case "BALANCING_THRESHOLDS":
                    data = intent.getByteArrayExtra("BALANCING_THRESHOLDS");
                    int minBalancingVoltage = ((data[0] & 0xFF) << 8) | (data[1] & 0xFF);
                    int maxVoltageDifference = ((data[2] & 0xFF) << 8) | (data[3] & 0xFF);
                    minimumBalanceVoltagePreference.setText(String.valueOf(minBalancingVoltage));
                    maximumCellVoltageDifferencePreference.setText(String.valueOf(maxVoltageDifference));
                    break;
                case "IDLE_CURRENT":
                    data = intent.getByteArrayExtra("IDLE_CURRENT");
                    int idleCurrent = ((data[0] & 0xFF) << 8) | (data[1] & 0xFF);
                    idleCurrentPreference.setText(String.valueOf(idleCurrent));
                    break;
                case "ONLY_BALANCE_WHEN_CHARGING":
                    data = intent.getByteArrayExtra("ONLY_BALANCE_WHEN_CHARGING");
                    boolean checked = data[0] != 0;
                    onlyBalanceWhileChargingPreference.setChecked(checked);
                    break;
            }
        }
    };
    public boolean isValidMacAddress(String macAddress) {
        final String MAC_ADDRESS_PATTERN = "^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$";
        final Pattern pattern = Pattern.compile(MAC_ADDRESS_PATTERN);
        return pattern.matcher(macAddress).matches();
    }
    private static boolean isValidDelay(String interval) {
        try {
            Float.parseFloat(interval);
        } catch (NumberFormatException e) {
            return false; // Not a valid float
        }

        // Use a regular expression to ensure it has at most three digits after the decimal point
        String regex = "^\\d+\\.\\d{1,3}$|^\\d+$";
        return interval.matches(regex);
    }
    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            logQuick("fat cownk");
            BluetoothLeService.LocalBinder binder = (BluetoothLeService.LocalBinder) service;
            bluetoothLeService = binder.getService();
            MainActivity activity = (MainActivity) requireActivity();
            if(activity.getBluetoothservice() == null) {
                activity.setBluetoothLeService(bluetoothLeService);
            }
            if (!bluetoothLeService.initialize()) {
                Log.e("BLE", "Unable to initialize Bluetooth");
                requireActivity().finish();
            }

            logQuick(String.valueOf(updateInterval));
            bluetoothLeService.connect(macAddress);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            bluetoothLeService = null;
        }
    };
    @Override
    public void onResume() {
        super.onResume();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            requireActivity().registerReceiver(bleUpdateReceiver, new IntentFilter("CONNECTION_STATE_CHANGED"), Context.RECEIVER_NOT_EXPORTED);
            requireActivity().registerReceiver(bleUpdateReceiver, new IntentFilter("SHUNT_RESISTOR"), Context.RECEIVER_NOT_EXPORTED);
            requireActivity().registerReceiver(bleUpdateReceiver, new IntentFilter("OVERCURRENT"), Context.RECEIVER_NOT_EXPORTED);
            requireActivity().registerReceiver(bleUpdateReceiver, new IntentFilter("UNDERVOLT"), Context.RECEIVER_NOT_EXPORTED);
            requireActivity().registerReceiver(bleUpdateReceiver, new IntentFilter("OVERVOLT"), Context.RECEIVER_NOT_EXPORTED);
            requireActivity().registerReceiver(bleUpdateReceiver, new IntentFilter("BALANCING_THRESHOLDS"), Context.RECEIVER_NOT_EXPORTED);
            requireActivity().registerReceiver(bleUpdateReceiver, new IntentFilter("IDLE_CURRENT"), Context.RECEIVER_NOT_EXPORTED);
            requireActivity().registerReceiver(bleUpdateReceiver, new IntentFilter("ONLY_BALANCE_WHEN_CHARGING"), Context.RECEIVER_NOT_EXPORTED);

        }
    }

    @Override
    public void onPause() {
        super.onPause();
        requireActivity().unregisterReceiver(bleUpdateReceiver);
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
