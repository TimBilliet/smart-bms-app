package com.example.bmsapp;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import android.app.Activity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.bmsapp.databinding.FragmentOtaBinding;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class OtaFragment extends Fragment {

    private FragmentOtaBinding binding;
    public static final String TAG = "Otafragment";
    private BluetoothLeService bluetoothLeService;
    private final Handler handlerToast = new Handler(Looper.getMainLooper());
    private static final int PICK_FILE_REQUEST_CODE = 1;
    private Uri selectedFile;
    private int sectorsSize;
    StatusRecyclerViewAdapter recyclerViewAdapter;
    private final List<String> statusList = new ArrayList<>();
    private long otaStartTime;
    private long otaEndTime;

    @Override public void onStart() {
        super.onStart();
    }
    @SuppressLint("SetTextI18n")
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentOtaBinding.inflate(inflater, container, false);
        disableUpgradeButton();
        if(bluetoothLeService.getConnectionState()== BluetoothAdapter.STATE_CONNECTED) {
            binding.textViewMac.setText("Connected to: " + bluetoothLeService.getConnectedDevice().getAddress());
        } else {
            binding.textViewMac.setText("Connected to: NOT CONNECTED");
        }

        binding.buttonChooseFile.setOnClickListener(v -> {
            if(bluetoothLeService != null && bluetoothLeService.getConnectionState() == BluetoothProfile.STATE_CONNECTED) {
                logQuick("clickity");
                Intent chooseFile = new Intent(Intent.ACTION_GET_CONTENT);
                chooseFile.setType("*/*");
                chooseFile = Intent.createChooser(chooseFile,"Pick file");
                startActivityForResult(chooseFile, PICK_FILE_REQUEST_CODE);

            } else {
                handlerToast.post(() -> Toast.makeText(getContext(), "Not connected.", Toast.LENGTH_LONG).show());
            }
        });
        binding.buttonUpload.setOnClickListener(v -> {
            logQuick("upload");
            if(bluetoothLeService != null && bluetoothLeService.getConnectionState() == BluetoothProfile.STATE_CONNECTED) {
                bluetoothLeService.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH);
                bluetoothLeService.startOta(selectedFile);
            } else {
                handlerToast.post(() -> Toast.makeText(getContext(), "Not connected.", Toast.LENGTH_LONG).show());
            }
        });
        recyclerViewAdapter = new StatusRecyclerViewAdapter(statusList);
        binding.recyclerViewOTA.setAdapter(recyclerViewAdapter);
        return binding.getRoot();
    }
    public void onAttach(@NonNull Context context) {
        MainActivity activity = (MainActivity) requireActivity();
        if (activity.getBluetoothservice() != null && bluetoothLeService == null) {
            bluetoothLeService = activity.getBluetoothservice();
        }
        super.onAttach(context);
    }
    private void enableUpgradeButton() {
        binding.buttonUpload.setAlpha(1);
        binding.buttonUpload.setEnabled(true);
    }
    private void disableUpgradeButton() {
        binding.buttonUpload.setAlpha(.6f);
        binding.buttonUpload.setEnabled(false);
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode == PICK_FILE_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            if(data!=null && data.getData() != null) {
                selectedFile= data.getData();
                String filename = getFileName(selectedFile);
                if(filename.endsWith(".bin")) {
                    binding.textViewFileName.setText("File name: " + filename);
                    binding.textViewOTAProgress.setText("0%");
                    binding.progressBarOTA.setProgress(0);
                    Cursor cursor = requireActivity().getContentResolver().query(selectedFile, null, null, null, null);
                    if (cursor != null && cursor.moveToFirst()) {
                        int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
                        long fileSize = cursor.getLong(sizeIndex);
                        cursor.close();
                        binding.textViewFileSize.setText("Size: " + fileSize/1000 + " kB");
                    }
                    enableUpgradeButton();
                } else {
                    AlertDialog.Builder builder = new AlertDialog.Builder(requireActivity());
                    builder.setMessage("Invalid file type")
                            .setPositiveButton("ok", (dialog, which) -> {
                            });
                    AlertDialog dialog = builder.create();
                    dialog.show();
                }
            }
        }
    }
    @SuppressLint("Range")
    private String getFileName(Uri uri) {
        String fileName = null;
        if (Objects.equals(uri.getScheme(), "content")) {
            try (Cursor cursor = requireActivity().getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    fileName = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                }
            } catch (Exception ex) {
                handlerToast.post(() -> Toast.makeText(getContext(), "Error while getting filename: " + ex.getMessage(), Toast.LENGTH_LONG).show());
            }
        } else if (Objects.equals(uri.getScheme(), "file")) {
            fileName = uri.getLastPathSegment();
        }
        return fileName;
    }
    private final BroadcastReceiver bleUpdateReceiver = new BroadcastReceiver() {
        @SuppressLint("SetTextI18n")
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null || binding == null) {
                return;
            }
            switch(action) {
                case "CONNECTION_STATE_CHANGED":
                    if (intent.getBooleanExtra("CONNECTION_STATE_CHANGED", false)) {
                        binding.textViewMac.setText("Connected to: " + bluetoothLeService.getConnectedDevice().getAddress());
                        binding.progressBarOTA.setProgress(0);
                        binding.textViewOTAProgress.setText("0%");
                    } else {
                        binding.textViewMac.setText("Connected to: NOT CONNECTED");
                        disableUpgradeButton();
                        binding.textViewFileName.setText("File name:");
                        binding.textViewFileSize.setText("Size:");
                    }
                    break;
                case "SECTORS_SIZE":
                    logQuick("val " + intent.getIntExtra("SECTORS_SIZE", 0));
                    binding.progressBarOTA.setMax(intent.getIntExtra("SECTORS_SIZE", 0) - 1);
                    sectorsSize = intent.getIntExtra("SECTORS_SIZE", 0);
                    break;
                case "OTA_PROGRESS":
                    int progress = intent.getIntExtra("OTA_PROGRESS", 0);
                    binding.progressBarOTA.setProgress(progress);
                    double percent = ( (double) progress / sectorsSize) * 100.0;
                    binding.textViewOTAProgress.setText(Math.round(percent) + "%");
                    break;
                case "OTA_STATUS":
                    String statusMessage = intent.getStringExtra("OTA_STATUS");
                    if(Objects.equals(statusMessage, "Starting OTA update...")) {
                        otaStartTime = System.currentTimeMillis();
                    } else if (Objects.equals(statusMessage, "OTA update complete!")) {
                        otaEndTime = System.currentTimeMillis();
                        long diff = otaEndTime - otaStartTime;
                        statusMessage = "OTA update complete! (" + diff/1000 + "s elapsed)";
                    }
                    statusList.add(statusMessage);
                    binding.recyclerViewOTA.scrollToPosition(statusList.size() - 1);
                    break;
            }
        }
    };

    public void logQuick(String message) {
        Log.d(TAG, message);
    }
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
    }
    @Override
    public void onResume() {
        super.onResume();
        ContextCompat.registerReceiver(requireContext(), bleUpdateReceiver, new IntentFilter("CONNECTION_STATE_CHANGED"), ContextCompat.RECEIVER_NOT_EXPORTED);
        ContextCompat.registerReceiver(requireContext(), bleUpdateReceiver, new IntentFilter("SECTORS_SIZE"), ContextCompat.RECEIVER_NOT_EXPORTED);
        ContextCompat.registerReceiver(requireContext(), bleUpdateReceiver, new IntentFilter("OTA_PROGRESS"), ContextCompat.RECEIVER_NOT_EXPORTED);
        ContextCompat.registerReceiver(requireContext(), bleUpdateReceiver, new IntentFilter("OTA_STATUS"), ContextCompat.RECEIVER_NOT_EXPORTED);
    }
    @Override
    public void onPause() {
        super.onPause();
    }
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}