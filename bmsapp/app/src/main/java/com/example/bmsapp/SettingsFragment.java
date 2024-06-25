package com.example.bmsapp;


import android.Manifest;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.preference.EditTextPreference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreference;

import java.util.regex.Pattern;

public class SettingsFragment extends PreferenceFragmentCompat {

    public static final String TAG = "Settingsfragment";
    private String macAddress;
    private SwitchPreference onlyBalanceWhileChargingPreference;
    private EditTextPreference shuntResistorPreference;
    private EditTextPreference overChargeCurrentPreference;
    private EditTextPreference underVoltagePreference;
    private EditTextPreference overVoltagePreference;
    private EditTextPreference minimumBalanceVoltagePreference;
    private EditTextPreference maximumCellVoltageDifferencePreference;
    private EditTextPreference idleCurrentPreference;
    private BluetoothLeService bluetoothLeService;

    private EditTextPreference macAddressPreference;
    private final Handler handlerToast = new Handler(Looper.getMainLooper());

    @Override
    public void onAttach(@NonNull Context context) {
        MainActivity activity = (MainActivity) requireActivity();
        if (activity.getBluetoothservice() != null) {
            bluetoothLeService = activity.getBluetoothservice();
        }
        super.onAttach(context);
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        logQuick("on create preferences");
        setPreferencesFromResource(R.xml.root_preferences, rootKey);
        macAddressPreference = findPreference("mac_address");
        SwitchPreference notificationPreference = findPreference("receive_notifications");
        SwitchPreference autoUpdatePreference = findPreference("auto_update");
        onlyBalanceWhileChargingPreference = findPreference("only_balance_while_charging");
        shuntResistorPreference = findPreference("shunt_value");
        overChargeCurrentPreference = findPreference("overcharge_current");
        underVoltagePreference = findPreference("undervolt");
        overVoltagePreference = findPreference("overvolt");
        minimumBalanceVoltagePreference = findPreference("min_balance_voltage");
        maximumCellVoltageDifferencePreference = findPreference("max_cell_voltage_diff");
        idleCurrentPreference = findPreference("idle_current_threshold");
        if (macAddressPreference != null) {
            macAddressPreference.setPositiveButtonText("Connect");
            macAddressPreference.setOnBindEditTextListener(editText -> editText.setHint("AA:AA:AA:AA:AA:AA"));
            macAddressPreference.setOnPreferenceChangeListener((preference, macAddress) -> {
                if (!isValidMacAddress((String) macAddress)) {
                    showDialog("Invalid MAC address");
                } else if(bluetoothLeService != null && bluetoothLeService.getConnectionState() == BluetoothAdapter.STATE_CONNECTED){// only bind service if there is no active connection
                    showDialog("Already connected");
                } else {
                    this.macAddress = convertToUpperCase((String) macAddress);
                    Intent gattServiceIntent = new Intent(getActivity(), BluetoothLeService.class);
                    requireActivity().bindService(gattServiceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
                    logQuick("binding service");
                }
                return true;
            });
        }
        if (notificationPreference != null) {
            notificationPreference.setOnPreferenceChangeListener((preference, toggle) -> {
                if ((boolean) toggle) {
                    requestNotificationPermission();
                }
                return true;
            });
        }
        if (autoUpdatePreference != null) {
            autoUpdatePreference.setOnPreferenceChangeListener((preference, toggle) -> {
                if (bluetoothLeService.getConnectionState() == BluetoothProfile.STATE_CONNECTED) {
                    if ((boolean) toggle) {
                        logQuick("notifications on");
                        bluetoothLeService.toggleNotifications(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                    } else {
                        logQuick("notifications off");
                        bluetoothLeService.toggleNotifications(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
                    }
                } else {
                    handlerToast.post(() -> Toast.makeText(getContext(), "Not connected.", Toast.LENGTH_LONG).show());
                }
                return true;
            });
        }
        if (onlyBalanceWhileChargingPreference != null) {
            onlyBalanceWhileChargingPreference.setOnPreferenceChangeListener(((preference, toggle) -> {
                if (bluetoothLeService.getConnectionState() == BluetoothProfile.STATE_CONNECTED) {
                    byte[] data = new byte[1];
                    data[0] = (byte)((boolean)toggle ? 1 : 0);
                    bluetoothLeService.writeCharacteristic("4008", data);
                } else {
                    handlerToast.post(() -> Toast.makeText(getContext(), "Not connected.", Toast.LENGTH_LONG).show());
                }
                return true;
            }));
        }
        if (shuntResistorPreference != null) {
            shuntResistorPreference.setOnPreferenceChangeListener(((preference, resistance) -> {
                if (bluetoothLeService.getConnectionState() == BluetoothProfile.STATE_CONNECTED) {
                    if (((String) resistance).matches("\\d+")) {
                        byte[] data = new byte[1];
                        data[0] = Byte.parseByte((String) resistance);
                        bluetoothLeService.writeCharacteristic("4001", data);
                    } else {
                        showDialog("Invalid input");
                    }
                } else {
                    handlerToast.post(() -> Toast.makeText(getContext(), "Not connected.", Toast.LENGTH_LONG).show());
                }
                return true;
            }));
        }
        if (overChargeCurrentPreference != null) {
            overChargeCurrentPreference.setOnPreferenceChangeListener(((preference, current) -> {
                if (bluetoothLeService.getConnectionState() == BluetoothProfile.STATE_CONNECTED) {
                    if (((String) current).matches("\\d+") && ((String) current).length() <= 5) {
                        byte[] data = new byte[2];
                        data[0] = (byte) (Integer.parseInt((String) current) & 0xFF);
                        data[1] = (byte) ((Integer.parseInt((String) current) >> 8) & 0xFF);
                        bluetoothLeService.writeCharacteristic("4002", data);
                    } else {
                        showDialog("Invalid input");
                    }
                } else {
                    handlerToast.post(() -> Toast.makeText(getContext(), "Not connected.", Toast.LENGTH_LONG).show());
                }
                return true;
            }));
        }
        if (underVoltagePreference != null) {
            underVoltagePreference.setOnPreferenceChangeListener(((preference, voltage) -> {
                if (bluetoothLeService.getConnectionState() == BluetoothProfile.STATE_CONNECTED) {
                    if (((String) voltage).matches("\\d+") && ((String) voltage).length() == 4) {
                        byte[] data = new byte[2];
                        data[0] = (byte) (Integer.parseInt((String) voltage) & 0xFF);
                        data[1] = (byte) ((Integer.parseInt((String) voltage) >> 8) & 0xFF);
                        bluetoothLeService.writeCharacteristic("4003", data);
                    } else {
                        showDialog("Invalid input");
                    }
                } else {
                    handlerToast.post(() -> Toast.makeText(getContext(), "Not connected.", Toast.LENGTH_LONG).show());
                }
                return true;
            }));
        }
        if (overVoltagePreference != null) {
            overVoltagePreference.setOnPreferenceChangeListener(((preference, voltage) -> {
                if (bluetoothLeService.getConnectionState() == BluetoothProfile.STATE_CONNECTED) {
                    if (((String) voltage).matches("\\d+") && ((String) voltage).length() == 4) {
                        byte[] data = new byte[2];
                        data[0] = (byte) (Integer.parseInt((String) voltage) & 0xFF);
                        data[1] = (byte) ((Integer.parseInt((String) voltage) >> 8) & 0xFF);
                        bluetoothLeService.writeCharacteristic("4004", data);
                    } else {
                        showDialog("Invalid input");
                    }
                } else {
                    handlerToast.post(() -> Toast.makeText(getContext(), "Not connected.", Toast.LENGTH_LONG).show());
                }
                return true;
            }));
        }
        if (minimumBalanceVoltagePreference != null) {
            minimumBalanceVoltagePreference.setOnPreferenceChangeListener(((preference, voltage) -> {
                if (bluetoothLeService.getConnectionState() == BluetoothProfile.STATE_CONNECTED) {
                    if (((String) voltage).matches("\\d+") && ((String) voltage).length() == 4) {
                        byte[] data = new byte[4];
                        data[0] = (byte) (Integer.parseInt((String) voltage) & 0xFF);
                        data[1] = (byte) ((Integer.parseInt((String) voltage) >> 8) & 0xFF);
                        String maxVoltageDifferenceString = maximumCellVoltageDifferencePreference.getText();
                        if (maxVoltageDifferenceString != null) {
                            int maxDiffI = Integer.parseInt(maxVoltageDifferenceString);
                            data[2] = (byte) (maxDiffI & 0xFF);
                            data[3] = (byte) ((maxDiffI >> 8) & 0xFF);
                            bluetoothLeService.writeCharacteristic("4005", data);
                        }
                    } else {
                        showDialog("Invalid input");
                    }
                } else {
                    handlerToast.post(() -> Toast.makeText(getContext(), "Not connected.", Toast.LENGTH_LONG).show());
                }
                return true;
            }));
        }
        if (maximumCellVoltageDifferencePreference != null) {
            maximumCellVoltageDifferencePreference.setOnPreferenceChangeListener(((preference, voltage) -> {
                if (bluetoothLeService.getConnectionState() == BluetoothProfile.STATE_CONNECTED) {
                    if (((String) voltage).matches("\\d+") && ((String) voltage).length() <= 3) {
                        byte[] data = new byte[4];
                        String minBalanceVoltageS = minimumBalanceVoltagePreference.getText();
                        if (minBalanceVoltageS != null) {
                            int minBalanceVoltageI = Integer.parseInt(minBalanceVoltageS);
                            data[0] = (byte) (minBalanceVoltageI & 0xFF);
                            data[1] = (byte) ((minBalanceVoltageI >> 8) & 0xFF);
                            data[2] = (byte) (Integer.parseInt((String) voltage) & 0xFF);
                            data[3] = (byte) ((Integer.parseInt((String) voltage) >> 8) & 0xFF);
                            bluetoothLeService.writeCharacteristic("4005", data);
                        }
                    } else {
                        showDialog("Invalid input");
                    }
                } else {
                    handlerToast.post(() -> Toast.makeText(getContext(), "Not connected.", Toast.LENGTH_LONG).show());
                }
                return true;
            }));
        }
        if (idleCurrentPreference != null) {
            idleCurrentPreference.setOnPreferenceChangeListener(((preference, voltage) -> {
                if (bluetoothLeService.getConnectionState() == BluetoothProfile.STATE_CONNECTED) {
                    if (((String) voltage).matches("\\d+") && Integer.parseInt((String) voltage) <= 255) {
                        byte[] data = new byte[1];
                        data[0] = (byte) (Integer.parseInt((String) voltage) & 0xFF);
                        bluetoothLeService.writeCharacteristic("4006", data);
                    } else {
                        showDialog("Invalid input");
                    }
                } else {
                    handlerToast.post(() -> Toast.makeText(getContext(), "Not connected.", Toast.LENGTH_LONG).show());
                }
                return true;
            }));
        }
    }

    private void requestNotificationPermission() {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(requireActivity(), Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(requireActivity(), new String[]{Manifest.permission.POST_NOTIFICATIONS}, 0);
        }
    }

    private final BroadcastReceiver bleUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null) {
                return;
            }
            byte[] data;
            switch (action) {
                case "CONNECTION_STATE_CHANGED":
                    if (intent.getBooleanExtra("CONNECTION_STATE_CHANGED", false)) {
                        logQuick("connection state changed to true");
                        if(macAddress == null) {
                            showDialog("Connected to: " + convertToUpperCase(macAddressPreference.getText()));
                        } else {
                            showDialog("Connected to: " + convertToUpperCase(macAddress));
                        }

                    } else {
                       // requireActivity().unregisterReceiver(bleUpdateReceiver);
                    }
                    break;
                case "4001":
                    data = intent.getByteArrayExtra("4001");
                    if (data != null) {
                        shuntResistorPreference.setText(String.valueOf(data[0]));
                    }
                    break;
                case "4002":
                    data = intent.getByteArrayExtra("4002");
                    if (data != null) {
                        int current = ((data[0] & 0xFF) << 8) | (data[1] & 0xFF);
                        overChargeCurrentPreference.setText(String.valueOf(current));
                    }
                    break;
                case "4003":
                    data = intent.getByteArrayExtra("4003");
                    if (data != null) {
                        int undervolt = ((data[0] & 0xFF) << 8) | (data[1] & 0xFF);
                        underVoltagePreference.setText(String.valueOf(undervolt));
                    }
                    break;
                case "4004":
                    data = intent.getByteArrayExtra("4004");
                    if (data != null) {
                        int overvolt = ((data[0] & 0xFF) << 8) | (data[1] & 0xFF);
                        overVoltagePreference.setText(String.valueOf(overvolt));
                    }
                    break;
                case "4005":
                    data = intent.getByteArrayExtra("4005");
                    if (data != null) {
                        int minBalancingVoltage = ((data[0] & 0xFF) << 8) | (data[1] & 0xFF);
                        int maxVoltageDifference = ((data[2] & 0xFF) << 8) | (data[3] & 0xFF);
                        minimumBalanceVoltagePreference.setText(String.valueOf(minBalancingVoltage));
                        maximumCellVoltageDifferencePreference.setText(String.valueOf(maxVoltageDifference));
                    }
                    break;
                case "4006":
                    data = intent.getByteArrayExtra("4006");
                    if (data != null) {
                        int idleCurrent = ((data[0] & 0xFF) << 8) | (data[1] & 0xFF);
                        idleCurrentPreference.setText(String.valueOf(idleCurrent));
                    }
                    break;
                case "4008":
                    data = intent.getByteArrayExtra("4008");
                    if (data != null) {
                        boolean checked = data[0] != 0;
                        onlyBalanceWhileChargingPreference.setChecked(checked);
                    }
                    break;
            }
        }
    };

    private static boolean isValidMacAddress(String macAddress) {
        final String MAC_ADDRESS_PATTERN = "^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$";
        final Pattern pattern = Pattern.compile(MAC_ADDRESS_PATTERN);
        return pattern.matcher(macAddress).matches();
    }

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            logQuick("service connected");
            BluetoothLeService.LocalBinder binder = (BluetoothLeService.LocalBinder) service;
            bluetoothLeService = binder.getService();
            MainActivity activity = (MainActivity) requireActivity();
            if (activity.getBluetoothservice() == null) {
                activity.setBluetoothLeService(bluetoothLeService);
            }
            if (!bluetoothLeService.initialize()) {
                Log.e("BLE", "Unable to initialize Bluetooth");
                requireActivity().finish();
            }
            bluetoothLeService.connect(macAddress);
            logQuick("connecting in settingsfrag");
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            bluetoothLeService = null;
        }
    };
    @Override
    public void onPause() {
        requireActivity().unregisterReceiver(bleUpdateReceiver);
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        ContextCompat.registerReceiver(requireContext(), bleUpdateReceiver, new IntentFilter("CONNECTION_STATE_CHANGED"), ContextCompat.RECEIVER_NOT_EXPORTED);
        ContextCompat.registerReceiver(requireContext(), bleUpdateReceiver, new IntentFilter("4001"), ContextCompat.RECEIVER_NOT_EXPORTED);
        ContextCompat.registerReceiver(requireContext(), bleUpdateReceiver, new IntentFilter("4002"), ContextCompat.RECEIVER_NOT_EXPORTED);
        ContextCompat.registerReceiver(requireContext(), bleUpdateReceiver, new IntentFilter("4003"), ContextCompat.RECEIVER_NOT_EXPORTED);
        ContextCompat.registerReceiver(requireContext(), bleUpdateReceiver, new IntentFilter("4004"), ContextCompat.RECEIVER_NOT_EXPORTED);
        ContextCompat.registerReceiver(requireContext(), bleUpdateReceiver, new IntentFilter("4005"), ContextCompat.RECEIVER_NOT_EXPORTED);
        ContextCompat.registerReceiver(requireContext(), bleUpdateReceiver, new IntentFilter("4006"), ContextCompat.RECEIVER_NOT_EXPORTED);
        ContextCompat.registerReceiver(requireContext(), bleUpdateReceiver, new IntentFilter("4008"), ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    private void showDialog(String message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setMessage(message)
                .setPositiveButton("ok", (dialog, which) -> {

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
