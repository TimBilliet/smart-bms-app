package com.example.bmsapp;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.example.bmsapp.databinding.FragmentOtaBinding;

public class OtaFragment extends Fragment {

    private FragmentOtaBinding binding;
    public static final String TAG = "Otafragment";
    private BluetoothLeService bluetoothLeService;

    @Override public void onStart() {
        super.onStart();
    }
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {

        binding = FragmentOtaBinding.inflate(inflater, container, false);

        return binding.getRoot();

    }
    public void onAttach(@NonNull Context context) {
        MainActivity activity = (MainActivity) requireActivity();
        if (activity.getBluetoothservice() != null) {
            bluetoothLeService = activity.getBluetoothservice();
        }
        super.onAttach(context);
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
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            bluetoothLeService = null;
        }
    };
    public void logQuick(String message) {
        Log.d(TAG, message);
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