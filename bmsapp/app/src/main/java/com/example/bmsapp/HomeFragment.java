package com.example.bmsapp;

import static android.os.Trace.isEnabled;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.example.bmsapp.databinding.FragmentHomeBinding;

import java.util.Objects;
import java.util.UUID;

public class HomeFragment extends Fragment{
    private View view;
    private FragmentHomeBinding binding;
    private BluetoothAdapter bluetoothAdapter;
    public static final String TAG = "Homefragment";
    private TextView textViewBatVoltage;

    private BluetoothLeService bluetoothLeService;
    private boolean isServiceBound = false;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);
        logQuick("HOMEFRAG CREATED OMAGOSH");
        textViewBatVoltage = view.findViewById(R.id.textViewBatVoltage);
        /*
        Button readButton = view.findViewById(R.id.buttonReadVoltage);
        readButton.setOnClickListener(v -> {
            if (isServiceBound && bluetoothLeService != null) {
                BluetoothGattCharacteristic characteristic = getCharacteristics();
                if (characteristic != null) {
                    bluetoothLeService.readCharacteristic(characteristic);
                } else {
                    Log.w(TAG, "Characteristic x3001 not found");
                }
            }
        });

         */
        Intent gattServiceIntent = new Intent(getActivity(), BluetoothLeService.class);
        requireActivity().bindService(gattServiceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
        return view;
    }

    private final BroadcastReceiver bleUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if ("BLE_DATA".equals(action)) {
                String data = intent.getStringExtra("BLE_DATA");
                textViewBatVoltage.setText(data);
                logQuick(data);
            }
        }
    };
    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            BluetoothLeService.LocalBinder binder = (BluetoothLeService.LocalBinder) service;

            MainActivity activity = (MainActivity) requireActivity();

            if(activity.getBluetoothservice() != null) {
                bluetoothLeService = activity.getBluetoothservice();
            } else {
                bluetoothLeService = binder.getService();
            }
            isServiceBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            bluetoothLeService = null;
            isServiceBound = false;
            Log.d(TAG, "Service disconnected");
        }
    };
    public void updatePackVoltage(float newValue) {
        textViewBatVoltage.setText((int) newValue);
        // Perform any additional actions, such as updating the UI
        Log.d("HomeFragment", "Variable updated: " + newValue);
    }
    public void logQuick(String message) {
        Log.d(TAG, message);
    }
    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    @Override
    public void onResume() {
        super.onResume();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            requireActivity().registerReceiver(bleUpdateReceiver, new IntentFilter("BLE_DATA"), Context.RECEIVER_NOT_EXPORTED);
        }
    }
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        logQuick("pipiundkaka");
        if (id == R.id.manual_refresh) {
            // Handle the action here
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    @Override
    public void onPause() {
        super.onPause();
        requireActivity().unregisterReceiver(bleUpdateReceiver);
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.menu_main, menu); // Inflate the menu resource.
        super.onCreateOptionsMenu(menu, inflater);
    }
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
    public BluetoothGattCharacteristic getCharacteristics() {
        if (bluetoothLeService != null && bluetoothLeService.getBluetoothGatt() != null) {
            String shortUuid = "0x3000"; //bat voltage characteristic
            String shortchar = "0x3001";
            // Convert the 16-bit UUID to 128-bit format
            String longUuid = shortUuid.replace("0x", "") + "-0000-1000-8000-00805F9B34FB";
            String longUuidChar = shortchar.replace("0x", "") + "-0000-1000-8000-00805F9B34FB";
            // Create the UUID object
            UUID serviceUuid = UUID.fromString(longUuid);
            UUID charUuid = UUID.fromString(longUuidChar);
            BluetoothGattService service = bluetoothLeService.getBluetoothGatt().getService(serviceUuid);
            if (service != null) {
                return service.getCharacteristic(charUuid);
            } else {
                logQuick("service is null");
            }
        }
        return null;
    }
}