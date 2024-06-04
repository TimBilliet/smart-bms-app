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
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.example.bmsapp.databinding.FragmentHomeBinding;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class HomeFragment extends Fragment{
    private View view;
    private FragmentHomeBinding binding;
    private BluetoothAdapter bluetoothAdapter;
    public static final String TAG = "Homefragment";
    private TextView textViewBatVoltage;
    private TextView textViewCurrent;
    private List<TextView> textViewCellVoltagesList = new ArrayList<>();
    private List<ProgressBar> progressBarCellList = new ArrayList<>();
    private List<TextView> textViewCellBalancingStateList = new ArrayList<>();
    private TextView textViewVoltageRange;
    private TextView textViewVoltageDifference;
    private BluetoothLeService bluetoothLeService;
    private boolean isServiceBound = false;

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
            long start = 0;
            if ("PACK_VOLTAGE".equals(action)) {
                byte[] data = intent.getByteArrayExtra("PACK_VOLTAGE");
                int batVoltagemv = ((data[0] & 0xFF) << 8) | (data[1] & 0xFF);
                double batVoltagev = batVoltagemv / 1000.0;
                textViewBatVoltage.setText(df.format(batVoltagev));
            } else if("CELL_VOLTAGES".equals(action)) {
                byte[] data = intent.getByteArrayExtra("CELL_VOLTAGES");
                int lowestVoltage = Integer.MAX_VALUE;
                int highestVoltage = Integer.MIN_VALUE;
                for (int i = 0; i < 10; i++) {
                    start = System.currentTimeMillis();
                    int index = i*2;
                    byte msb = data[index];
                    byte lsb = data[index + 1];
                    int cellVoltage = ((msb << 8) | lsb);
                    if(cellVoltage < lowestVoltage) {
                        lowestVoltage = cellVoltage;
                    } else if(cellVoltage > highestVoltage) {
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
                long end = System.currentTimeMillis();
                //logQuick("Time taken: " + (end - start) + "ms");
            } else if("CELL_BALANCING_STATE".equals(action)) {
                byte[] data = intent.getByteArrayExtra("CELL_BALANCING_STATE");
                for(int i = 0; i < 10; i++) {
                    int balancingState = data[i];
                    if(balancingState == 1) {
                        textViewCellBalancingStateList.get(i).setText("B");
                    } else {
                        textViewCellBalancingStateList.get(i).setText("");
                    }
                }
            } else if("CHARGE_CURRENT".equals(action)) {
                byte[] data = intent.getByteArrayExtra("CHARGE_CURRENT");
                int chargeCurrentmA = ((data[0] & 0xFF) << 8) | (data[1] & 0xFF);
                double chargeCurrentA = chargeCurrentmA / 1000.0;
                textViewCurrent.setText(df.format(chargeCurrentA));

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

    public void logQuick(String message) {
        Log.d(TAG, message);
    }
    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    @Override
    public void onResume() {
        super.onResume();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            requireActivity().registerReceiver(bleUpdateReceiver, new IntentFilter("PACK_VOLTAGE"), Context.RECEIVER_NOT_EXPORTED);
            requireActivity().registerReceiver(bleUpdateReceiver, new IntentFilter("CELL_VOLTAGES"), Context.RECEIVER_NOT_EXPORTED);
            requireActivity().registerReceiver(bleUpdateReceiver, new IntentFilter("CELL_BALANCING_STATE"), Context.RECEIVER_NOT_EXPORTED);
            requireActivity().registerReceiver(bleUpdateReceiver, new IntentFilter("CHARGE_CURRENT"), Context.RECEIVER_NOT_EXPORTED);
            requireActivity().registerReceiver(bleUpdateReceiver, new IntentFilter("ENABLE_CHARGING"), Context.RECEIVER_NOT_EXPORTED);
            requireActivity().registerReceiver(bleUpdateReceiver, new IntentFilter("ENABLE_BALANCING"), Context.RECEIVER_NOT_EXPORTED);
        }
        MainActivity activity = (MainActivity) requireActivity();
        if(activity.getBluetoothservice() != null) {
            bluetoothLeService = activity.getBluetoothservice();
            logQuick("bluetooth characteristic read from homefrag");
            bluetoothLeService.readAllCharacteristics();
        }
    }
    @Override
    public void onPause() {
        super.onPause();
        requireActivity().unregisterReceiver(bleUpdateReceiver);
    }

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}