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
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.preference.PreferenceManager;


import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class HomeFragment extends Fragment{

    public static final String TAG = "Homefragment";
    private TextView textViewBatVoltage;
    private TextView textViewCurrent;
    private Switch enableChargingSwitch;
    private Switch enableBalancingSwitch;
    private List<TextView> textViewCellVoltagesList = new ArrayList<>();
    private List<ProgressBar> progressBarCellList = new ArrayList<>();
    private List<TextView> textViewCellBalancingStateList = new ArrayList<>();
    private TextView textViewVoltageRange;
    private TextView textViewVoltageDifference;
    private double chargeCurrentA;
    private SharedPreferences sharedPreferences;
    private final Handler handlerToast = new Handler(Looper.getMainLooper());
    private BluetoothLeService bluetoothLeService;
    private boolean isConnected;
    private  boolean onlyBalanceWhenCharging = true;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        textViewBatVoltage = view.findViewById(R.id.textViewBatVoltage);
        textViewCurrent = view.findViewById(R.id.textViewCurrent);
        textViewCellVoltagesList.add(view.findViewById(R.id.textViewCellVoltage1));
        textViewCellVoltagesList.add(view.findViewById(R.id.textViewCellVoltage2));
        textViewCellVoltagesList.add(view.findViewById(R.id.textViewCellVoltage3));
        textViewCellVoltagesList.add(view.findViewById(R.id.textViewCellVoltage4));
        textViewCellVoltagesList.add(view.findViewById(R.id.textViewCellVoltage5));
        textViewCellVoltagesList.add(view.findViewById(R.id.textViewCellVoltage6));
        textViewCellVoltagesList.add(view.findViewById(R.id.textViewCellVoltage7));
        textViewCellVoltagesList.add(view.findViewById(R.id.textViewCellVoltage8));
        textViewCellVoltagesList.add(view.findViewById(R.id.textViewCellVoltage9));
        textViewCellVoltagesList.add(view.findViewById(R.id.textViewCellVoltage10));
        progressBarCellList.add(view.findViewById(R.id.progressBarCell1));
        progressBarCellList.add(view.findViewById(R.id.progressBarCell2));
        progressBarCellList.add(view.findViewById(R.id.progressBarCell3));
        progressBarCellList.add(view.findViewById(R.id.progressBarCell4));
        progressBarCellList.add(view.findViewById(R.id.progressBarCell5));
        progressBarCellList.add(view.findViewById(R.id.progressBarCell6));
        progressBarCellList.add(view.findViewById(R.id.progressBarCell7));
        progressBarCellList.add(view.findViewById(R.id.progressBarCell8));
        progressBarCellList.add(view.findViewById(R.id.progressBarCell9));
        progressBarCellList.add(view.findViewById(R.id.progressBarCell10));
        textViewCellBalancingStateList.add(view.findViewById(R.id.textViewBalancing1));
        textViewCellBalancingStateList.add(view.findViewById(R.id.textViewBalancing2));
        textViewCellBalancingStateList.add(view.findViewById(R.id.textViewBalancing3));
        textViewCellBalancingStateList.add(view.findViewById(R.id.textViewBalancing4));
        textViewCellBalancingStateList.add(view.findViewById(R.id.textViewBalancing5));
        textViewCellBalancingStateList.add(view.findViewById(R.id.textViewBalancing6));
        textViewCellBalancingStateList.add(view.findViewById(R.id.textViewBalancing7));
        textViewCellBalancingStateList.add(view.findViewById(R.id.textViewBalancing8));
        textViewCellBalancingStateList.add(view.findViewById(R.id.textViewBalancing9));
        textViewCellBalancingStateList.add(view.findViewById(R.id.textViewBalancing10));
        textViewVoltageRange = view.findViewById(R.id.textViewRange);
        textViewVoltageDifference = view.findViewById(R.id.textViewDifference);
        enableBalancingSwitch = view.findViewById(R.id.switchBalancing);
        enableChargingSwitch = view.findViewById(R.id.switchCharging);
        enableBalancingSwitch.setOnClickListener(v -> {
            if(bluetoothLeService != null) {
                byte[] data = {0};
                if(enableBalancingSwitch.isChecked()) {
                    if(onlyBalanceWhenCharging) {
                        if(chargeCurrentA > 0.02) {
                            data[0] = 1;
                        } else {
                            handlerToast.post(() -> Toast.makeText(requireContext(), "Balancing only allowed while charging", Toast.LENGTH_SHORT).show());
                            enableBalancingSwitch.setChecked(false);
                            data[0] = 0;
                        }
                    } else {
                        data[0] = 1;

                    }
                }
                bluetoothLeService.writeCharacteristic("3005", data);
            }
        });
        enableChargingSwitch.setOnClickListener(v -> {
            if(bluetoothLeService != null) {
                byte[] data = {0};
                if(enableChargingSwitch.isChecked()) {
                    data[0] = 1;
                }
                bluetoothLeService.writeCharacteristic("3006", data);
            } else {
                logQuick("ITSNULLJGBIGQI5G");
            }
        });

        Intent gattServiceIntent = new Intent(getActivity(), BluetoothLeService.class);
        requireActivity().bindService(gattServiceIntent, serviceConnection, Context.BIND_AUTO_CREATE);

        return view;
    }

    private final BroadcastReceiver bleUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            DecimalFormat df = new DecimalFormat("#.##");
            df.setRoundingMode(RoundingMode.CEILING);
            String action = intent.getAction();
            logQuick("action: " + action);
            byte[] data;
            boolean state;
            long start = 0;
            switch (Objects.requireNonNull(action)) {
                case "CONNECTION_STATE_CHANGED":
                    if(intent.getBooleanExtra("CONNECTION_STATE_CHANGED", false)) {
                        isConnected = true;
                        bluetoothLeService.setIsHomefragment(true);
                    }
                    break;
                case "3001":  // pack voltage and charge current
                    data = intent.getByteArrayExtra("3001");
                    int batVoltagemv = ((data[0] & 0xFF) << 8) | (data[1] & 0xFF);
                    int chargeCurrentmA = ((data[2] & 0xFF) << 8) | (data[3] & 0xFF);
                    double batVoltagev = batVoltagemv / 1000.0;
                    chargeCurrentA = chargeCurrentmA / 1000.0;
                    textViewBatVoltage.setText(df.format(batVoltagev));
                    textViewCurrent.setText(df.format(chargeCurrentA));
                    break;
                case "3002":  // cell voltages
                    data = intent.getByteArrayExtra("3002");
                    int lowestVoltage = Integer.MAX_VALUE;
                    int highestVoltage = Integer.MIN_VALUE;
                    for (int i = 0; i < 10; i++) {
                        int index = i * 2;
                        byte msb = data[index];
                        byte lsb = data[index + 1];
                        int cellVoltage = ((msb << 8) | (lsb & 0xFF));
                        if (cellVoltage < lowestVoltage) {
                            lowestVoltage = cellVoltage;
                        } else if (cellVoltage > highestVoltage) {
                            highestVoltage = cellVoltage;
                        }
                        progressBarCellList.get(i).setProgress(cellVoltage);
                        textViewCellVoltagesList.get(i).setText(String.format("%.3f", cellVoltage / 1000.0));
                    }
                    int difference = highestVoltage - lowestVoltage;
                    String differenceString = difference + "mV";
                    textViewVoltageDifference.setText(differenceString);
                    String voltageRange = String.format("%sV-%sV",
                            df.format(lowestVoltage / 1000.0),
                            df.format(highestVoltage / 1000.0));
                    textViewVoltageRange.setText(voltageRange);
                    break;
                case "3003":  // cell balancing state
                    data = intent.getByteArrayExtra("3003");
                    for (int i = 0; i < 10; i++) {
                        int balancingState = data[i];
                        if (balancingState == 1) {
                            textViewCellBalancingStateList.get(i).setText("B");
                        } else {
                            textViewCellBalancingStateList.get(i).setText("");
                        }
                    }
                    break;
                case "3005":  // balancing switch state
                    data = intent.getByteArrayExtra("3005");
                    state = data[0] != 0;
                    enableBalancingSwitch.setChecked(state);
                    break;
                case "3006":  // charging switch state
                    data = intent.getByteArrayExtra("3006");
                     state = data[0] != 0;
                    enableChargingSwitch.setChecked(state);
                    break;
                 case "4008": // only balance when charging
                    data = intent.getByteArrayExtra("4008");
                    boolean checked = data[0] != 0;
                    logQuick("only balance when charging: " + checked);
                    onlyBalanceWhenCharging = checked;
                    break;
                default:
                    // Handle unexpected action
                    break;
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
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            bluetoothLeService = null;
            Log.d(TAG, "Service disconnected");
        }
    };

    public void logQuick(String message) {
        Log.d(TAG, message);
    }
    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    @Override
    public void onResume() {
        logQuick("onresume homefrag");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            requireActivity().registerReceiver(bleUpdateReceiver, new IntentFilter("CONNECTION_STATE_CHANGED"), Context.RECEIVER_NOT_EXPORTED);
            requireActivity().registerReceiver(bleUpdateReceiver, new IntentFilter("3001"), Context.RECEIVER_NOT_EXPORTED);
            requireActivity().registerReceiver(bleUpdateReceiver, new IntentFilter("3002"), Context.RECEIVER_NOT_EXPORTED);
            requireActivity().registerReceiver(bleUpdateReceiver, new IntentFilter("3003"), Context.RECEIVER_NOT_EXPORTED);
            requireActivity().registerReceiver(bleUpdateReceiver, new IntentFilter("3005"), Context.RECEIVER_NOT_EXPORTED);
            requireActivity().registerReceiver(bleUpdateReceiver, new IntentFilter("3006"), Context.RECEIVER_NOT_EXPORTED);
            requireActivity().registerReceiver(bleUpdateReceiver, new IntentFilter("4008"), Context.RECEIVER_NOT_EXPORTED);
        }
        super.onResume();
    }
    @Override
    public void onAttach(@NonNull Context context) {
        logQuick("onattach homefrag");
        MainActivity activity = (MainActivity) requireActivity();
        if(activity.getBluetoothservice() != null) {
            bluetoothLeService = activity.getBluetoothservice();
            logQuick("bluetooth characteristic read from homefrag");
            //bluetoothLeService.readAllCharacteristics();
            bluetoothLeService.readCharacteristicsForHomefragment();
        }
        if(bluetoothLeService != null) {
            bluetoothLeService.setIsHomefragment(true);
            bluetoothLeService.runUpdateTimer();
        }
        super.onAttach(context);
    }
    @Override
    public void onPause() {
        super.onPause();
        requireActivity().unregisterReceiver(bleUpdateReceiver);
    }

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
    }

}