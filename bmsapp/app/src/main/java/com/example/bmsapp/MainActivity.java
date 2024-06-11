package com.example.bmsapp;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.Manifest;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import android.os.IBinder;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;
import androidx.preference.PreferenceManager;

import com.example.bmsapp.databinding.ActivityMainBinding;

import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import java.util.Objects;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {

    private AppBarConfiguration appBarConfiguration;
    private ActivityMainBinding binding;
    public static final String TAG = "Mainactivity";
    private boolean hideOverflowMenu = false;

    private BluetoothAdapter bluetoothAdapter;
    private ActivityResultLauncher<Intent> enableBtLauncher;
    private BluetoothLeService bluetoothLeService;
    private MainActivity thisActivity;
    NavController navController;
    private boolean isConnected = false;
    private SharedPreferences sharedPreferences;
    private Menu menu;
    private static final int NOTIF_PERMISSION_REQUEST_CODE = 69;
    private static final int BT_PERMISSION_REQUEST_CODE = 420;
    private static final String CHANNEL_ID = "1";
    String storedMac;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        thisActivity = this;
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        PreferenceManager.setDefaultValues(this, R.xml.root_preferences, false);
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requestBluetoothPermission();
        }
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
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            requestBluetoothEnable();
        }
        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        this.bindService(gattServiceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        storedMac = sharedPreferences.getString("mac_address", "AA:AA:AA:AA:AA:AA");
        logQuick(storedMac);
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "channel1", importance);
            channel.setDescription("main notification channel");
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    private void requestBluetoothEnable() {
        if (!bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            enableBtLauncher.launch(enableBtIntent);
        }
    }
    public void setBluetoothLeService(BluetoothLeService bluetoothLeService) {
        this.bluetoothLeService = bluetoothLeService;
    }
    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            BluetoothLeService.LocalBinder binder = (BluetoothLeService.LocalBinder) service;
            bluetoothLeService = binder.getService();
            if (!bluetoothLeService.initialize()) {
                Log.e("BLE", "Unable to initialize Bluetooth");
                showDialog("Unable to initialize Bluetooth");
                finish();
            }
            bluetoothLeService.runUpdateTimer();
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
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH_CONNECT}, BT_PERMISSION_REQUEST_CODE);
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
        logQuick("permission code: " + requestCode);
        if (requestCode == 420) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                requestBluetoothEnable();
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    new AlertDialog.Builder(this)
                            .setTitle("Permission needed")
                            .setMessage("Bluetooth permission needed for this app to function")
                            .setPositiveButton("Request again", (dialog, which) -> requestBluetoothPermission())
                            .setNegativeButton("Exit", (dialog, which) -> finish())
                            .setIcon(android.R.drawable.ic_dialog_alert)
                            .show();
                }
            }
        }
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
    public BluetoothLeService getBluetoothservice() {
        return bluetoothLeService;
    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }
    private void showDialog(String message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(message)
                .setPositiveButton("OK", (dialog, which) -> {

                });
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    @SuppressLint("RestrictedApi")
    @Override
    public void onBackPressed() {
        //TODO fix this, doesn't work properly
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

    private BroadcastReceiver bleUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            isConnected = intent.getBooleanExtra("CONNECTION_STATE_CHANGED", false);
            if(Objects.equals(intent.getAction(), "3007")) {
                byte[] faultCode = intent.getByteArrayExtra("3007");
                String faultMessage = "";
                switch (Objects.requireNonNull(faultCode)[0]) {
                    case 1:
                        faultMessage = "Over current in discharge fault";
                        break;
                    case 2:
                        faultMessage = "Short circuit in discharge fault";
                        break;
                    case 4:
                        faultMessage = "Overvoltage fault";
                        break;
                    case 8:
                        faultMessage = "Undervoltage fault";
                        break;
                    case 16:
                        faultMessage = "Alert fault";
                        break;
                    case 32:
                        faultMessage = "Internal chip fault";
                        break;
                }
                if(sharedPreferences.getBoolean("receive_pop_up_dialog", false) && faultCode[0] != 0) {
                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle("Fault occured")
                            .setMessage(faultMessage)
                            .setPositiveButton("ok", null)
                            .setIcon(android.R.drawable.ic_dialog_alert)
                            .show();
                }
                if(sharedPreferences.getBoolean("receive_notifications", false) && faultCode[0] != 0
                        && ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                    NotificationCompat.Builder builder = new NotificationCompat.Builder(MainActivity.this, CHANNEL_ID)
                            .setSmallIcon(android.R.drawable.ic_dialog_alert)
                            .setContentTitle("Fault occured!")
                            .setContentText(faultMessage)
                            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                            .setAutoCancel(true);
                    NotificationManager notificationManager = (NotificationManager) MainActivity.this.getSystemService(Context.NOTIFICATION_SERVICE);
                    notificationManager.notify(0, builder.build());
                    //TODO make notifications work when app is running in the background
                }
            }
            if(isConnected) {
                Toast.makeText(getApplicationContext(), "Connected.", Toast.LENGTH_LONG).show();
            }
        }
    };

    @Override
    public void onResume() {
        super.onResume();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            registerReceiver(bleUpdateReceiver, new IntentFilter("CONNECTION_STATE_CHANGED"), Context.RECEIVER_NOT_EXPORTED);
            registerReceiver(bleUpdateReceiver, new IntentFilter("3007"), Context.RECEIVER_NOT_EXPORTED);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        unregisterReceiver(bleUpdateReceiver);
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
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
        } else if(id == R.id.manual_refresh){
            if(bluetoothLeService != null && isConnected) {
                bluetoothLeService.readCharacteristicsForHomefragment();
            } else {
                Toast.makeText(getApplicationContext(), "Not connected.", Toast.LENGTH_LONG).show();
            }
        } else if(id == R.id.power_off) {
            if(bluetoothLeService != null && isConnected) {
                showPowerOffDialog();
            } else {
                Toast.makeText(getApplicationContext(), "Not connected.", Toast.LENGTH_LONG).show();
            }
        }
        return super.onOptionsItemSelected(item);
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
    private void showPowerOffDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage("Are you sure you want to power off the bms?");
        builder.setPositiveButton("yes", (dialog, which) -> shutdownBms());
        builder.setNegativeButton("cancel", null);
        builder.create();
        builder.show();

    }
    private void shutdownBms() {
        byte[] value = {0};
        bluetoothLeService.writeCharacteristic("4007", value);
        isConnected = false;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.setGroupVisible(0, !hideOverflowMenu);
        MenuItem item1 = menu.findItem(R.id.action_settings);
        MenuItem item2 = menu.findItem(R.id.action_about);
        item1.setVisible(!hideOverflowMenu);
        item2.setVisible(!hideOverflowMenu);
        super.onPrepareOptionsMenu(menu);
        return false;
    }
}