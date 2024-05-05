package com.example.bmsapp;

import static android.os.Trace.isEnabled;

import android.bluetooth.BluetoothAdapter;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.example.bmsapp.databinding.FragmentHomeBinding;

public class HomeFragment extends Fragment{
    private View view;
    private FragmentHomeBinding binding;
    private BluetoothAdapter bluetoothAdapter;
    public static final String TAG = "Homefragment";
    private TextView textViewBatVoltage;
    private TextView textV;
    Button myButton;

    /*
    @Override
    public View onCreateView( @NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        MainActivity mainActivity = (MainActivity) getActivity();
        View myView = inflater.inflate(R.layout.fragment_home, container, false);
        //myButton = (Button) myView.findViewById(R.id.buttonReadVoltage);
       // myButton.setOnClickListener(this);
        if (mainActivity != null) {
            bluetoothAdapter = mainActivity.getBluetoothAdapter();
        }
        //textViewPackVoltage = view.findViewById(R.id.textViewPackVoltage);

        binding = FragmentHomeBinding.inflate(inflater, container, false);
        //textV = view.findViewById(R.id.textViewPackVoltage);
        return binding.getRoot();
    }

     */
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);
        MainActivity mainActivity = (MainActivity) getActivity();

        // Find the TextView within the fragment layout
        textViewBatVoltage = view.findViewById(R.id.textViewBatVoltage);
        if (mainActivity != null) {
            bluetoothAdapter = mainActivity.getBluetoothAdapter();
        }
        return view;
    }
    void setVoltageText(String voltage) {
        Log.d(TAG, voltage);
        Log.d(TAG, "VOLTAGE IN HOMEFRAG");
        //textV.setText(voltage);
        //binding.textViewPackVoltage.setText(voltage);
        textViewBatVoltage.setText(voltage);
    }

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        //textViewPackVoltage = binding.textViewPackVoltage;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}