package com.example.bmsapp;

import static android.bluetooth.BluetoothGattCharacteristic.FORMAT_UINT8;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.Manifest;

import androidx.activity.OnBackPressedCallback;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.example.bmsapp.databinding.ActivityMainBinding;

import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private AppBarConfiguration appBarConfiguration;
    private ActivityMainBinding binding;
    public static final String TAG = "Mainactivity";
    private boolean hideOverflowMenu = false;
    public BluetoothGatt bluetoothGatt;
    private BluetoothAdapter bluetoothAdapter;
    private ActivityResultLauncher<Intent> enableBtLauncher;
    private MainActivity mainActivity = this;
    private boolean isConnected = false;
    NavController navController;
    private BluetoothGatt gattIf;
    private BluetoothGattCharacteristic voltageChar;
    private BluetoothGattCharacteristic cellVoltageChar;
    private BluetoothGattCharacteristic currentChar;
    private HomeFragment homeFragment;
    private SettingsFragment settingsFragment;
    private androidx.fragment.app
            .FragmentManager mFragmentManager;
    private androidx.fragment.app
            .FragmentTransaction mFragmentTransaction;
    private static final int MY_PERMISSION_REQUEST_CODE = 420;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);

        navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        appBarConfiguration = new AppBarConfiguration.Builder(navController.getGraph()).build();
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            new AlertDialog.Builder(this)
                    .setTitle("Not supported")
                    .setMessage("Bluetooth is not supported on this device")
                    .setNegativeButton("Exit", (dialog, which) -> finish())
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .show();
        }
        requestBluetoothPermission();
        enableBtLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() != Activity.RESULT_OK) {
                new AlertDialog.Builder(this)
                        .setTitle("Enable bluetooth")
                        .setMessage("Please enable bluetooth for this app to function")
                        .setPositiveButton("Ok", (dialog, which) -> requestBluetoothEnable())
                        .setNegativeButton("Exit", (dialog, which) -> finish())
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .show();
            }
        });
        requestBluetoothEnable();
        OnBackPressedCallback callback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (isEnabled()) {
                    finish();
                }
            }
        };
        homeFragment = new HomeFragment();
        //settingsFragment = new SettingsFragment();
    }

    private void requestBluetoothEnable() {
        if (!bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            enableBtLauncher.launch(enableBtIntent);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.S)
    private void requestBluetoothPermission() {
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.BLUETOOTH_CONNECT) !=
                PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH_CONNECT}, MY_PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // switch (requestCode) {
        // case MY_PERMISSION_REQUEST_CODE:
        if (grantResults.length > 0 &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            requestBluetoothEnable();
        } else {
            new AlertDialog.Builder(this)
                    .setTitle("Permission needed")
                    .setMessage("Bluetooth permission needed for this app to function")
                    .setPositiveButton("Request again", (dialog, which) -> requestBluetoothPermission())
                    .setNegativeButton("Exit", (dialog, which) -> finish())
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .show();
        }
        //}
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    public BluetoothAdapter getBluetoothAdapter() {
        return bluetoothAdapter;
    }

    @SuppressLint("RestrictedApi")
    @Override
    public void onBackPressed() {

        if (navController.getCurrentDestination().getLabel().toString().equals("Settings") || navController.getCurrentDestination().getLabel().toString().equals("About")) {
            hideOverflowMenu = false;
            supportInvalidateOptionsMenu();
            navController.navigate(R.id.HomeFragment);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here.
        int id = item.getItemId();
        //navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        if (id == R.id.action_about) {
            navController.navigate(R.id.AboutFragment);
            hideOverflowMenu = true;
            supportInvalidateOptionsMenu();
            return true;
        } else if (id == R.id.action_settings) {
            navController.navigate(R.id.SettingsFragment);
            hideOverflowMenu = true;
            supportInvalidateOptionsMenu();
            return true;
        } else if (id == android.R.id.home) {
            hideOverflowMenu = false;
            supportInvalidateOptionsMenu();
            navController.navigate(R.id.HomeFragment);
        }
        return super.onOptionsItemSelected(item);
    }

    public void connectToDevice(String address) {
        address = convertToUpperCase(address);
        BluetoothDevice devicee = bluetoothAdapter.getRemoteDevice(address);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        bluetoothGatt = devicee.connectGatt(this, false, gattCallback);
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

    private void showDialog(String message, String okButton) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(message)
                .setPositiveButton(okButton, (dialog, which) -> {

                });
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            Log.d(TAG, "connection state changed IN MAINNNNNNNNNNN");
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                // Device connected, discover services
                runOnUiThread(() -> showDialog("Connection to BMS successful", "OK"));
                Log.d(TAG, "CONNECTED");
                isConnected = true;
                SettingsFragment.parameterCategory.setVisible(true);
                if (ActivityCompat.checkSelfPermission(mainActivity, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    return;
                }
                gatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                isConnected = false;
                runOnUiThread(() -> {
                    Log.d(TAG, "DISCONNECTED");
                    showDialog("Disconnected from BMS", "OK");
                });
                SettingsFragment.parameterCategory.setVisible(false);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            // Services discovered, you can now interact with the BLE server
            BluetoothGattService service = null;
            gattIf = gatt;
            boolean serviceFound = false;
            Log.d(TAG, SettingsFragment.enteredUUID.substring(2));
            for (BluetoothGattService gattService : bluetoothGatt.getServices()) {
                if (gattService.getUuid().toString().substring(4, 8).equals(SettingsFragment.enteredUUID.substring(2))) {
                    serviceFound = true;
                    service = gattService;
                }
            }
            if (serviceFound) {

                Log.d(TAG, "SERVICE UUID MATCH IN MAINACTIVITY");
                //do stuff
                for (BluetoothGattCharacteristic gattCharacteristic : service.getCharacteristics()) {
                    logQuick(gattCharacteristic.getUuid().toString().substring(4, 8));
                    switch (gattCharacteristic.getUuid().toString().substring(4, 8)) {
                        case "3001"://Voltage characteristic
                            voltageChar = gattCharacteristic;
                            if (ActivityCompat.checkSelfPermission(mainActivity, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {

                                return;
                            }
                            //gatt.readCharacteristic(gattCharacteristic);
                            //handlePackVoltage(voltageChar);
                            break;
                        case "3002"://Cell voltage characteristic
                            cellVoltageChar = gattCharacteristic;
                            break;
                        case "3003":
                            currentChar = gattCharacteristic;
                            break;
                    }
                }
                //BluetoothGattCharacteristic voltchar = service.getCharacteristic();
            } else {
                if (ActivityCompat.checkSelfPermission(mainActivity, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    return;
                }
                isConnected = false;
                SettingsFragment.parameterCategory.setVisible(false);
                bluetoothGatt.close();
                bluetoothGatt = null;
                runOnUiThread(() -> {
                    showDialog("Disconnected from BMS, no service found", "OK");

                });
            }

        }

        public void handlePackVoltage(BluetoothGattCharacteristic voltageChar) {

            logQuick(voltageChar.getUuid().toString());
        }

        public void logQuick(String message) {
            Log.d(TAG, message);
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            // Characteristic read callback
            if (status == BluetoothGatt.GATT_SUCCESS) {
                byte[] data = characteristic.getValue();
                // Handle the received data (byte array)
                if (characteristic.equals(voltageChar)) {
                    int batvoltage = ((data[0] & 0xFF) << 8) | (data[1] & 0xFF);
                    logQuick(String.valueOf(batvoltage));

                    homeFragment.setVoltageText(String.valueOf(batvoltage));// werkt niet
                }
            }
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

    @Override
    public boolean onSupportNavigateUp() {
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        return NavigationUI.navigateUp(navController, appBarConfiguration)
                || super.onSupportNavigateUp();
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.setGroupVisible(0, !hideOverflowMenu);
        return false;
    }

    public void onUpdateVoltageClick(View view) {
        Log.d(TAG, String.valueOf(isConnected));
        if (isConnected) {
            Log.d(TAG, "start getting data yipiee");
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            gattIf.readCharacteristic(voltageChar);
        }
    }
}