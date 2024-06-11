package com.example.bmsapp;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class HomeFragment extends Fragment{

    public static final String TAG = "Homefragment";
    private TextView textViewBatVoltage;
    private TextView textViewCurrent;
    private Switch enableChargingSwitch;
    private Switch enableBalancingSwitch;
    private final List<TextView> textViewCellVoltagesList = new ArrayList<>();
    private final List<ProgressBar> progressBarCellList = new ArrayList<>();
    private final List<TextView> textViewCellBalancingStateList = new ArrayList<>();
    private TextView textViewVoltageRange;
    private TextView textViewVoltageDifference;
    private double chargeCurrentA;
    private final Handler handlerToast = new Handler(Looper.getMainLooper());
    private BluetoothLeService bluetoothLeService;
    private  boolean onlyBalanceWhileCharging = true;

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
            if(bluetoothLeService != null && bluetoothLeService.getConnectionState() == BluetoothAdapter.STATE_CONNECTED) {
                byte[] data = {0};
                if(enableBalancingSwitch.isChecked()) {
                    if(onlyBalanceWhileCharging) {
                        if(chargeCurrentA > 0.02) {
                            data[0] = 1;
                        } else {
                            handlerToast.post(() -> Toast.makeText(requireContext(), "Balancing only allowed while charging", Toast.LENGTH_SHORT).show());
                            enableBalancingSwitch.setChecked(false);
                        }
                    } else {
                        data[0] = 1;
                    }
                }
                bluetoothLeService.writeCharacteristic("3005", data);
            } else {
                handlerToast.post(() -> Toast.makeText(requireContext(), "Not connected", Toast.LENGTH_SHORT).show());
                enableBalancingSwitch.setChecked(false);
            }
        });
        enableChargingSwitch.setOnClickListener(v -> {
            if(bluetoothLeService != null && bluetoothLeService.getConnectionState() == BluetoothAdapter.STATE_CONNECTED) {
                byte[] data = {0};
                if(enableChargingSwitch.isChecked()) {
                    data[0] = 1;
                }
                bluetoothLeService.writeCharacteristic("3006", data);
            } else {
                handlerToast.post(() -> Toast.makeText(requireContext(), "Not connected", Toast.LENGTH_SHORT).show());
                enableChargingSwitch.setChecked(false);
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
            byte[] data;
            switch (Objects.requireNonNull(action)) {
                case "3001":  // pack voltage and charge current
                    data = intent.getByteArrayExtra("3001");
                    int batVoltagemv = ((data[1] & 0xFF) << 8) | (data[0] & 0xFF);
                    int chargeCurrentmA = ((data[3] & 0xFF) << 8) | (data[2] & 0xFF);
                    double batVoltagev = batVoltagemv / 1000.0;
                    chargeCurrentA = chargeCurrentmA / 1000.0;
                    if(chargeCurrentA > 0.01) {
                        textViewCurrent.setText(df.format(chargeCurrentA));
                    }
                    textViewBatVoltage.setText(df.format(batVoltagev));
                    break;
                case "3002":  // cell voltages
                    data = intent.getByteArrayExtra("3002");
                    int lowestVoltage = Integer.MAX_VALUE;
                    int highestVoltage = Integer.MIN_VALUE;
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
                 case "4008": // only balance while charging
                    data = intent.getByteArrayExtra("4008");
                    boolean checked = data[0] != 0;
                    logQuick("only balance while charging: " + checked);
                    onlyBalanceWhileCharging = checked;
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
    @Override
    public void onResume() {
        ContextCompat.registerReceiver(requireActivity(),bleUpdateReceiver, new IntentFilter("3001"), ContextCompat.RECEIVER_NOT_EXPORTED);
        ContextCompat.registerReceiver(requireActivity(),bleUpdateReceiver, new IntentFilter("3002"), ContextCompat.RECEIVER_NOT_EXPORTED);
        ContextCompat.registerReceiver(requireActivity(),bleUpdateReceiver, new IntentFilter("3003"), ContextCompat.RECEIVER_NOT_EXPORTED);
        ContextCompat.registerReceiver(requireActivity(),bleUpdateReceiver, new IntentFilter("4008"), ContextCompat.RECEIVER_NOT_EXPORTED);
        if(bluetoothLeService != null) {
            bluetoothLeService.readCharacteristicsForHomefragment();
        }
        super.onResume();
    }
    @Override
    public void onAttach(@NonNull Context context) {
        logQuick("onattach homefrag");
        MainActivity activity = (MainActivity) requireActivity();
        if(activity.getBluetoothservice() != null) {
            bluetoothLeService = activity.getBluetoothservice();
        }
        super.onAttach(context);
    }

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
    }

}