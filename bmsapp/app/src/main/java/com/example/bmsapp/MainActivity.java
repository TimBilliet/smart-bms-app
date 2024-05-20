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
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
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

import android.os.IBinder;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;
import androidx.preference.PreferenceManager;

import com.example.bmsapp.databinding.ActivityMainBinding;

import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {

    private AppBarConfiguration appBarConfiguration;
    private ActivityMainBinding binding;
    public static final String TAG = "Mainactivity";
    private boolean hideOverflowMenu = false;

    private BluetoothAdapter bluetoothAdapter;
    private ActivityResultLauncher<Intent> enableBtLauncher;
    private BluetoothLeService bluetoothLeService;

    private boolean isConnected = false;
    NavController navController;



    private static final int MY_PERMISSION_REQUEST_CODE = 420;
    String storedMac;

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
        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        this.bindService(gattServiceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        storedMac = sharedPreferences.getString("mac_address", "default value");
        logQuick(storedMac);

    }

    private void requestBluetoothEnable() {
        if (!bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            enableBtLauncher.launch(enableBtIntent);
        }
    }
    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            BluetoothLeService.LocalBinder binder = (BluetoothLeService.LocalBinder) service;
            bluetoothLeService = binder.getService();
            if (!bluetoothLeService.initialize()) {
                Log.e("BLE", "Unable to initialize Bluetooth");
                finish();
            }
            bluetoothLeService.connect(convertToUpperCase(storedMac));
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            bluetoothLeService = null;
        }
    };

    @RequiresApi(api = Build.VERSION_CODES.S)
    private void requestBluetoothPermission() {
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.BLUETOOTH_CONNECT) !=
                PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH_CONNECT}, MY_PERMISSION_REQUEST_CODE);
        }
    }
    public boolean isValidMacAddress(String macAddress) {
        final String MAC_ADDRESS_PATTERN = "^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$";
        final Pattern pattern = Pattern.compile(MAC_ADDRESS_PATTERN);
        return pattern.matcher(macAddress).matches();
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
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }


    @SuppressLint("RestrictedApi")
    @Override
    public void onBackPressed() {
        logQuick(Objects.requireNonNull(Objects.requireNonNull(navController.getCurrentDestination()).getLabel()).toString());
        if (navController.getCurrentDestination().getLabel().toString().equals("Settings") || navController.getCurrentDestination().getLabel().toString().equals("About")) {
            hideOverflowMenu = false;
            supportInvalidateOptionsMenu();
            navController.navigate(R.id.HomeFragment);
        } else if(navController.getCurrentDestination().getLabel().toString().equals("Home")) {
            moveTaskToBack(true);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here.
        int id = item.getItemId();
        if (id == R.id.action_about) {
            navController.navigate(R.id.AboutFragment);
            hideOverflowMenu = true;
            supportInvalidateOptionsMenu();
            return true;
        } else if (id == R.id.action_settings) {
            navController.navigate(R.id.SettingsFragment);
            hideOverflowMenu = true;
            supportInvalidateOptionsMenu();
            //SettingsFragment.parameterCategory.setVisible(false);
            return true;
        } else if (id == android.R.id.home) {
            hideOverflowMenu = false;
            supportInvalidateOptionsMenu();
            navController.navigate(R.id.HomeFragment);
        }
        return super.onOptionsItemSelected(item);
    }

    private void showDialog(String message, String okButton) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(message)
                .setPositiveButton(okButton, (dialog, which) -> {

                });
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    public void logQuick(String message) {
        Log.d(TAG, message);
    }
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
}