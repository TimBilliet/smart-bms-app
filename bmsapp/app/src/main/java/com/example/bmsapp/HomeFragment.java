package com.example.bmsapp;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGatt;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;
import com.example.bmsapp.databinding.FragmentHomeBinding;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class HomeFragment extends Fragment{
    private FragmentHomeBinding binding;
    public static final String TAG = "Homefragment";

    private final List<TextView> textViewCellVoltagesList = new ArrayList<>();
    private final List<ProgressBar> progressBarCellList = new ArrayList<>();
    private final List<TextView> textViewCellBalancingStateList = new ArrayList<>();

    private double chargeCurrentA;
    private SharedPreferences sharedPreferences;
    private final Handler handlerToast = new Handler(Looper.getMainLooper());
    private BluetoothLeService bluetoothLeService;
    private boolean onlyBalanceWhileCharging = true;


    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        textViewCellVoltagesList.add(binding.textViewCellVoltage1);
        textViewCellVoltagesList.add(binding.textViewCellVoltage2);
        textViewCellVoltagesList.add(binding.textViewCellVoltage3);
        textViewCellVoltagesList.add(binding.textViewCellVoltage4);
        textViewCellVoltagesList.add(binding.textViewCellVoltage5);
        textViewCellVoltagesList.add(binding.textViewCellVoltage6);
        textViewCellVoltagesList.add(binding.textViewCellVoltage7);
        textViewCellVoltagesList.add(binding.textViewCellVoltage8);
        textViewCellVoltagesList.add(binding.textViewCellVoltage9);
        textViewCellVoltagesList.add(binding.textViewCellVoltage10);

        progressBarCellList.add(binding.progressBarCell1);
        progressBarCellList.add(binding.progressBarCell2);
        progressBarCellList.add(binding.progressBarCell3);
        progressBarCellList.add(binding.progressBarCell4);
        progressBarCellList.add(binding.progressBarCell5);
        progressBarCellList.add(binding.progressBarCell6);
        progressBarCellList.add(binding.progressBarCell7);
        progressBarCellList.add(binding.progressBarCell8);
        progressBarCellList.add(binding.progressBarCell9);
        progressBarCellList.add(binding.progressBarCell10);

        textViewCellBalancingStateList.add(binding.textViewBalancing1);
        textViewCellBalancingStateList.add(binding.textViewBalancing2);
        textViewCellBalancingStateList.add(binding.textViewBalancing3);
        textViewCellBalancingStateList.add(binding.textViewBalancing4);
        textViewCellBalancingStateList.add(binding.textViewBalancing5);
        textViewCellBalancingStateList.add(binding.textViewBalancing6);
        textViewCellBalancingStateList.add(binding.textViewBalancing7);
        textViewCellBalancingStateList.add(binding.textViewBalancing8);
        textViewCellBalancingStateList.add(binding.textViewBalancing9);
        textViewCellBalancingStateList.add(binding.textViewBalancing10);
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext());
        binding.switchBalancing.setChecked(sharedPreferences.getBoolean("balancing_switch", false));
        binding.switchCharging.setChecked(sharedPreferences.getBoolean("charging_switch", false));

        binding.switchBalancing.setOnClickListener(v -> {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            if(bluetoothLeService != null && bluetoothLeService.getConnectionState() == BluetoothAdapter.STATE_CONNECTED) {
                byte[] data = {0};

                if(binding.switchBalancing.isChecked()) {
                    if(onlyBalanceWhileCharging) {
                        if(chargeCurrentA > 0.02) {
                            data[0] = 1;
                            editor.putBoolean("balancing_switch", true);
                            editor.apply();
                        } else {
                            editor.putBoolean("balancing_switch", false);
                            editor.apply();
                            handlerToast.post(() -> Toast.makeText(requireContext(), "Balancing only allowed while charging", Toast.LENGTH_SHORT).show());
                            binding.switchBalancing.setChecked(false);
                        }
                    } else {
                        data[0] = 1;
                        editor.putBoolean("balancing_switch", true);
                        editor.apply();
                    }
                } else {
                    editor.putBoolean("balancing_switch", false);
                    editor.apply();
                }
                bluetoothLeService.writeCharacteristic("3005", data);
            } else {
                handlerToast.post(() -> Toast.makeText(requireContext(), "Not connected", Toast.LENGTH_SHORT).show());
                binding.switchBalancing.setChecked(false);
                editor.putBoolean("balancing_switch", false);
                editor.apply();
            }
        });
        binding.switchCharging.setOnClickListener(v -> {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            if(bluetoothLeService != null && bluetoothLeService.getConnectionState() == BluetoothAdapter.STATE_CONNECTED) {
                byte[] data = {0};
                if(binding.switchCharging.isChecked()) {
                    data[0] = 1;
                    editor.putBoolean("charging_switch", true);
                    editor.apply();
                } else {
                    editor.putBoolean("charging_switch", false);
                    editor.apply();
                }
                bluetoothLeService.writeCharacteristic("3006", data);
            } else {
                handlerToast.post(() -> Toast.makeText(requireContext(), "Not connected", Toast.LENGTH_SHORT).show());
                binding.switchCharging.setChecked(false);
                editor.putBoolean("charging_switch", false);
                editor.apply();
            }
        });
        Intent gattServiceIntent = new Intent(getActivity(), BluetoothLeService.class);
        requireActivity().bindService(gattServiceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
        return binding.getRoot();
    }

    private final BroadcastReceiver bleUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            DecimalFormat df = new DecimalFormat("#.##");
            df.setRoundingMode(RoundingMode.CEILING);
            String action = intent.getAction();
            if(action == null) {
                return;
            }
            byte[] data;
            boolean checked;
            switch (action) {
                case "3001":  // pack voltage and charge current
                    data = intent.getByteArrayExtra("3001");
                    if(data != null) {
                        int batVoltagemv = ((data[1] & 0xFF) << 8) | (data[0] & 0xFF);
                        int chargeCurrentmA = ((data[3] & 0xFF) << 8) | (data[2] & 0xFF);
                        double batVoltagev = batVoltagemv / 1000.0;
                        chargeCurrentA = chargeCurrentmA / 1000.0;
                        binding.textViewCurrent.setText(df.format(chargeCurrentA));
                        binding.textViewBatVoltage.setText(df.format(batVoltagev));
                        SharedPreferences.Editor editor = sharedPreferences.edit();
                        editor.putString("batVoltage", df.format(batVoltagev));
                        editor.putString("chargeCurrent", df.format(chargeCurrentA));
                        editor.apply();
                    }
                    break;
                case "3002":  // cell voltages
                    data = intent.getByteArrayExtra("3002");
                    if(data != null) {
                        int lowestVoltage = Integer.MAX_VALUE;
                        int highestVoltage = Integer.MIN_VALUE;
                        SharedPreferences.Editor editor = sharedPreferences.edit();
                        for (int i = 0; i < 10; i++) {
                            int index = i * 2;
                            byte msb = data[index + 1];
                            byte lsb = data[index];
                            int cellVoltage = ((msb << 8) | (lsb & 0xFF));
                            if (cellVoltage < lowestVoltage) {
                                lowestVoltage = cellVoltage;
                            } else if (cellVoltage > highestVoltage) {
                                highestVoltage = cellVoltage;
                            }
                            progressBarCellList.get(i).setProgress(cellVoltage);
                            String cellVoltageString = String.format(Locale.getDefault(),"%.3f", cellVoltage / 1000.0);
                            textViewCellVoltagesList.get(i).setText(cellVoltageString);

                            editor.putInt("cellProgress" + i, cellVoltage);
                            editor.putString("cellVoltage" + i, cellVoltageString);

                        }
                        int difference = highestVoltage - lowestVoltage;
                        String differenceString = difference + "mV";
                        binding.textViewDifference.setText(differenceString);
                        editor.putString("voltageDifference", differenceString);
                        String voltageRange = String.format("%sV-%sV",
                                df.format(lowestVoltage / 1000.0),
                                df.format(highestVoltage / 1000.0));
                        binding.textViewRange.setText(voltageRange);
                        editor.putString("voltageRange", voltageRange);
                        editor.apply();
                    }

                    break;
                case "3003":  // cell balancing state
                    data = intent.getByteArrayExtra("3003");
                    if(data != null) {
                        SharedPreferences.Editor editor = sharedPreferences.edit();
                        for (int i = 0; i < 10; i++) {
                            int balancingState = data[i];
                            if (balancingState == 1) {
                                textViewCellBalancingStateList.get(i).setText("B");
                                editor.putString("cellBalancingState" + i, "B");
                            } else {
                                textViewCellBalancingStateList.get(i).setText("");
                                editor.putString("cellBalancingState" + i, "");
                            }
                        }
                        editor.apply();
                    }
                    break;
                case "3005":
                    data = intent.getByteArrayExtra("3005");
                    if(data != null) {
                        checked = data[0] != 0;
                        binding.switchBalancing.setChecked(checked);
                        SharedPreferences.Editor editor = sharedPreferences.edit();
                        editor.putBoolean("balancing_switch", checked);
                        editor.apply();
                    }
                    break;
                case "3006":
                    data = intent.getByteArrayExtra("3006");
                    if(data != null) {
                        checked = data[0] != 0;
                        binding.switchCharging.setChecked(checked);
                        SharedPreferences.Editor editor = sharedPreferences.edit();
                        editor.putBoolean("charging_switch", checked);
                        editor.apply();
                    }
                    break;
                 case "4008": // only balance while charging
                    data = intent.getByteArrayExtra("4008");
                    if(data != null) {
                        onlyBalanceWhileCharging = data[0] != 0;
                    }
                    break;
                case "READY_TO_READ_CHARS":
                    if(intent.getBooleanExtra("READY_TO_READ_CHARS", false)) {
                        bluetoothLeService.readCharacteristicsForHomefragment();
                    }
                    break;
                case "CONNECTION_STATE_CHANGED":
                    // on disconnect
                    if(!intent.getBooleanExtra("CONNECTION_STATE_CHANGED", false) && sharedPreferences.getBoolean("clear_on_disconnect", false)) {
                        clearBmsInfo();
                    }
            }
        }
    };

    private void clearBmsInfo() {
        binding.textViewBatVoltage.setText("0");
        binding.textViewCurrent.setText("0");
        binding.textViewRange.setText("0V-4.2V");
        binding.textViewDifference.setText("0mV");
        for (TextView cellState:textViewCellBalancingStateList) {
            cellState.setText("");
        }
        for (TextView cellVoltage:textViewCellVoltagesList) {
            cellVoltage.setText("0");
        }

        for(ProgressBar progressBar: progressBarCellList) {
            progressBar.setProgress(0);
        }
    }
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
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            bluetoothLeService = null;
        }
    };

    public void logQuick(String message) {
        Log.d(TAG, message);
    }
    @Override
    public void onResume() {
        if(bluetoothLeService != null && bluetoothLeService.getConnectionState() == BluetoothGatt.STATE_CONNECTED) {
            bluetoothLeService.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_BALANCED);
        }
        binding.switchCharging.setChecked(sharedPreferences.getBoolean("charging_switch", false));
        binding.switchBalancing.setChecked(sharedPreferences.getBoolean("balancing_switch", false));
        ContextCompat.registerReceiver(requireActivity(),bleUpdateReceiver, new IntentFilter("3001"), ContextCompat.RECEIVER_NOT_EXPORTED);
        ContextCompat.registerReceiver(requireActivity(),bleUpdateReceiver, new IntentFilter("3002"), ContextCompat.RECEIVER_NOT_EXPORTED);
        ContextCompat.registerReceiver(requireActivity(),bleUpdateReceiver, new IntentFilter("3003"), ContextCompat.RECEIVER_NOT_EXPORTED);
        ContextCompat.registerReceiver(requireActivity(),bleUpdateReceiver, new IntentFilter("3005"), ContextCompat.RECEIVER_NOT_EXPORTED);
        ContextCompat.registerReceiver(requireActivity(),bleUpdateReceiver, new IntentFilter("3006"), ContextCompat.RECEIVER_NOT_EXPORTED);
        ContextCompat.registerReceiver(requireActivity(),bleUpdateReceiver, new IntentFilter("4008"), ContextCompat.RECEIVER_NOT_EXPORTED);
        ContextCompat.registerReceiver(requireActivity(),bleUpdateReceiver, new IntentFilter("READY_TO_READ_CHARS"), ContextCompat.RECEIVER_NOT_EXPORTED);
        ContextCompat.registerReceiver(requireActivity(),bleUpdateReceiver, new IntentFilter("CONNECTION_STATE_CHANGED"), ContextCompat.RECEIVER_NOT_EXPORTED);
        super.onResume();
    }
    @Override
    public void onPause() {
        requireActivity().unregisterReceiver(bleUpdateReceiver);
        super.onPause();
    }

    @Override
    public void onAttach(@NonNull Context context) {
        MainActivity activity = (MainActivity) requireActivity();
        if(activity.getBluetoothservice() != null) {
            bluetoothLeService = activity.getBluetoothservice();
        }
        super.onAttach(context);
    }
    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {

        binding.textViewBatVoltage.setText(sharedPreferences.getString("batVoltage", "0"));
        binding.textViewCurrent.setText(sharedPreferences.getString("chargeCurrent", "0"));
        binding.textViewRange.setText(sharedPreferences.getString("voltageRange", "0V-4.2V"));
        binding.textViewDifference.setText(sharedPreferences.getString("voltageDifference", "0mV"));
        for(int i = 0; i < 10; i++) {
            progressBarCellList.get(i).setProgress(sharedPreferences.getInt("cellProgress" + i, 0));
            textViewCellVoltagesList.get(i).setText(sharedPreferences.getString("cellVoltage" + i, "0"));
            textViewCellBalancingStateList.get(i).setText(sharedPreferences.getString("cellBalancingState" + i, ""));
        }
        super.onViewCreated(view, savedInstanceState);
    }
}