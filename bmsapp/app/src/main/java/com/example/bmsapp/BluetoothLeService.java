package com.example.bmsapp;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.preference.PreferenceManager;

import com.example.bmsapp.message.BleOTAMessage;
import com.example.bmsapp.message.EndCommandAckMessage;
import com.example.bmsapp.message.StartCommandAckMessage;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@SuppressLint("MissingPermission")
public class BluetoothLeService extends Service {
    private BluetoothManager bluetoothManager;
    private BluetoothAdapter bluetoothAdapter;
    public static final String TAG = "BluetoothLeService";
    private BluetoothGatt bluetoothGatt;
    private final IBinder binder = new LocalBinder();
    private final Handler handlerToast = new Handler(Looper.getMainLooper());
    private final List<BluetoothGattCharacteristic> bluetoothGattCharacteristicList = new ArrayList<>();
    private final List<BluetoothGattCharacteristic> tempBluetoothGattCharacteristicList = new ArrayList<>();
    private final List<BluetoothGattCharacteristic> homefragmentBluetoothGattCharacteristicList = new ArrayList<>();
    private final List<BluetoothGattCharacteristic> tempHomefragmentBluetoothGattCharacteristicList = new ArrayList<>();
    private final List<BluetoothGattDescriptor> characteristicsToGetNotificationsOnList = new ArrayList<>();
    private final List<BluetoothGattDescriptor> otaCharacteristicsToGetNotificationsOnList = new ArrayList<>();
    private final List<BluetoothGattCharacteristic> parameterCharacteristicList = new ArrayList<>();
    private final List<BluetoothGattCharacteristic> otaCharacteristicList = new ArrayList<>();
    private boolean readingHomefragmentCharacteristics = false;
    private boolean isMinimized = false;
    private SharedPreferences sharedPreferences;
    private BluetoothDevice device;
    private LinkedList<byte[]> packets = new LinkedList<>();
    private AtomicInteger sectorAckIndex = new AtomicInteger(0);
    private byte[] sectorAckMark = new byte[0];
    private int packetSize = 20;
    private byte[] bin;
    private static final int COMMAND_ID_START = 0x0001;
    private static final int COMMAND_ID_END = 0x0002;
    private static final int COMMAND_ID_ACK = 0x0003;

    public static final int COMMAND_ACK_ACCEPT = 0x0000;
    public static final int COMMAND_ACK_REFUSE = 0x0001;

    private static final int BIN_ACK_SUCCESS = 0x0000;
    private static final int BIN_ACK_CRC_ERROR = 0x0001;
    private static final int BIN_ACK_SECTOR_INDEX_ERROR = 0x0002;
    private static final int BIN_ACK_PAYLOAD_LENGTH_ERROR = 0x0003;

    private static final int MTU_SIZE = 517;
    private static final int MTU_STATUS_FAILED = 20000;
    private static final int EXPECT_PACKET_SIZE = 463;

    private static final String CHAR_RECV_FW_UUID = "8020";
    private static final String CHAR_PROGRESS_UUID = "8021";
    private static final String CHAR_COMMAND_UUID = "8022";
    private static final String CHAR_CUSTOMER_UUID = "8023";
    private BluetoothGattCharacteristic recvFwChar = null;
    private BluetoothGattCharacteristic progressChar = null;
    private BluetoothGattCharacteristic commandChar = null;
    private BluetoothGattCharacteristic customerChar = null;
    private static final boolean REQUIRE_CHECKSUM = false;



    public class LocalBinder extends Binder {
        BluetoothLeService getService() {
            return BluetoothLeService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    public boolean initialize() {


        if (bluetoothManager == null) {
            bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (bluetoothManager == null) {
                Log.e("BLE", "Unable to initialize BluetoothManager.");
                return false;
            }
        }

        bluetoothAdapter = bluetoothManager.getAdapter();
        if (bluetoothAdapter == null) {
            Log.e("BLE", "Unable to obtain a BluetoothAdapter.");
            return false;
        }
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        return true;
    }


    public void connect(final String address) {
        if (bluetoothAdapter == null || address == null) {
            handlerToast.post(() -> Toast.makeText(this, "BluetoothAdapter not initialized or unspecified address.", Toast.LENGTH_LONG).show());
            return;
        }

        device = bluetoothAdapter.getRemoteDevice(address);
        if (device == null) {
            handlerToast.post(() -> Toast.makeText(this, "Device not found. Unable to connect.", Toast.LENGTH_LONG).show());
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
        } else if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        if(bluetoothGatt != null) {
            bluetoothGatt.close();
        }
        bluetoothGatt = device.connectGatt(this, false, gattCallback);
    }
    public void disconnect() {
        try {
            bluetoothGatt.disconnect();
        } catch (Exception ex) {
            showDialog(ex.getMessage());
        }
    }

    public int getConnectionState() {
       // if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
          //  if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
         //       return bluetoothManager.getConnectionState(device, BluetoothGatt.GATT);
         //   }
        //} else if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED) {
            return bluetoothManager.getConnectionState(device, BluetoothGatt.GATT);
       // }
       // return 99;
    }

    public BluetoothDevice getConnectedDevice() {
        if(getConnectionState() == BluetoothAdapter.STATE_CONNECTED) {
            return device;
        } else {
            return null;
        }
    }
    public void startOta(Uri uri) {
        new Thread(() -> {
            try {
                InputStream inputStream = getContentResolver().openInputStream(uri);
                if(inputStream != null) {
                    bin = readBytes(inputStream);
                }
            } catch (Exception ex) {
                showDialog("Error while starting OTA update: " + ex.getMessage());
            }
            toggleOtaNotifications(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
        }).start();
    }
    private byte[] readBytes(InputStream inputStream) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int nRead;
        byte[] data = new byte[1024];
        while ((nRead = inputStream.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }
        buffer.flush();
        return buffer.toByteArray();
    }
    private void toggleFaultNotifications() {
        UUID cccdUuid = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
        BluetoothGattCharacteristic faultCharacteristic = getCharacteristicByUUID("3007");
        bluetoothGatt.setCharacteristicNotification(faultCharacteristic, true);
        if (faultCharacteristic != null) {
            BluetoothGattDescriptor faultDescriptor = faultCharacteristic.getDescriptor(cccdUuid);
            faultDescriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            bluetoothGatt.writeDescriptor(faultDescriptor);
        }
    }

    public void toggleOtaNotifications(byte[] value) {
        UUID cccdUuid = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
        BluetoothGattCharacteristic firmwareReceivedCharacteristic = getCharacteristicByUUID(CHAR_RECV_FW_UUID);
        BluetoothGattCharacteristic progressBarCharacteristic = getCharacteristicByUUID(CHAR_PROGRESS_UUID);
        BluetoothGattCharacteristic commandCharacteristic = getCharacteristicByUUID(CHAR_COMMAND_UUID);
        BluetoothGattCharacteristic customerCharacteristic = getCharacteristicByUUID(CHAR_CUSTOMER_UUID);
        bluetoothGatt.setCharacteristicNotification(firmwareReceivedCharacteristic, true);
        bluetoothGatt.setCharacteristicNotification(progressBarCharacteristic, true);
        bluetoothGatt.setCharacteristicNotification(commandCharacteristic, true);
        bluetoothGatt.setCharacteristicNotification(customerCharacteristic, true);
        if(firmwareReceivedCharacteristic != null && progressBarCharacteristic != null && commandCharacteristic != null && customerCharacteristic != null) {
            BluetoothGattDescriptor firmwareReceivedDescriptor = firmwareReceivedCharacteristic.getDescriptor(cccdUuid);
            BluetoothGattDescriptor progressBarDescriptor = progressBarCharacteristic.getDescriptor(cccdUuid);
            BluetoothGattDescriptor commandDescriptor = commandCharacteristic.getDescriptor(cccdUuid);
            BluetoothGattDescriptor customerDescriptor = customerCharacteristic.getDescriptor(cccdUuid);
            otaCharacteristicsToGetNotificationsOnList.add(firmwareReceivedDescriptor);
            otaCharacteristicsToGetNotificationsOnList.add(progressBarDescriptor);
            otaCharacteristicsToGetNotificationsOnList.add(commandDescriptor);
            otaCharacteristicsToGetNotificationsOnList.add(customerDescriptor);

            firmwareReceivedDescriptor.setValue(value);
            progressBarDescriptor.setValue(value);
            commandDescriptor.setValue(value);
            customerDescriptor.setValue(value);
            writeOtaDescriptors();
        }
    }

    public void toggleNotifications(byte[] value) {
        UUID cccdUuid = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

        BluetoothGattCharacteristic voltageCurrentCharacteristic = getCharacteristicByUUID("3001");
        BluetoothGattCharacteristic cellVoltageCharacteristic = getCharacteristicByUUID("3002");
        BluetoothGattCharacteristic cellBalancingStateCharacteristic = getCharacteristicByUUID("3003");
        BluetoothGattCharacteristic balancingCharacteristic = getCharacteristicByUUID("3005");
        BluetoothGattCharacteristic chargingCharacteristic = getCharacteristicByUUID("3006");
        bluetoothGatt.setCharacteristicNotification(voltageCurrentCharacteristic, true);
        bluetoothGatt.setCharacteristicNotification(cellVoltageCharacteristic, true);
        bluetoothGatt.setCharacteristicNotification(cellBalancingStateCharacteristic, true);
        bluetoothGatt.setCharacteristicNotification(balancingCharacteristic, true);
        bluetoothGatt.setCharacteristicNotification(chargingCharacteristic, true);
        if (voltageCurrentCharacteristic != null && cellVoltageCharacteristic != null && cellBalancingStateCharacteristic != null && balancingCharacteristic != null && chargingCharacteristic != null) {
            BluetoothGattDescriptor voltageCurrentDescriptor = voltageCurrentCharacteristic.getDescriptor(cccdUuid);
            BluetoothGattDescriptor cellVoltageDescriptor = cellVoltageCharacteristic.getDescriptor(cccdUuid);
            BluetoothGattDescriptor cellBalancingStateDescriptor = cellBalancingStateCharacteristic.getDescriptor(cccdUuid);
            BluetoothGattDescriptor balancingDescriptor = balancingCharacteristic.getDescriptor(cccdUuid);
            BluetoothGattDescriptor chargingDescriptor = chargingCharacteristic.getDescriptor(cccdUuid);
            characteristicsToGetNotificationsOnList.add(voltageCurrentDescriptor);
            characteristicsToGetNotificationsOnList.add(cellVoltageDescriptor);
            characteristicsToGetNotificationsOnList.add(cellBalancingStateDescriptor);
            characteristicsToGetNotificationsOnList.add(balancingDescriptor);
            characteristicsToGetNotificationsOnList.add(chargingDescriptor);

            voltageCurrentDescriptor.setValue(value);
            cellVoltageDescriptor.setValue(value);
            cellBalancingStateDescriptor.setValue(value);
            balancingDescriptor.setValue(value);
            chargingDescriptor.setValue(value);

            writeDescriptors();
        }
    }

    public void writeDescriptors() {
        if (!characteristicsToGetNotificationsOnList.isEmpty()) {
            bluetoothGatt.writeDescriptor(characteristicsToGetNotificationsOnList.get(characteristicsToGetNotificationsOnList.size() - 1));
        }
    }
    public void writeOtaDescriptors() {
        if(!otaCharacteristicList.isEmpty()) {
            bluetoothGatt.writeDescriptor(otaCharacteristicsToGetNotificationsOnList.get(otaCharacteristicsToGetNotificationsOnList.size() -1 ));
        }
    }
    private void initPackets() throws IOException {
        sectorAckIndex.set(0);
        packets.clear();

        List<byte[]> sectors = new ArrayList<>();
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(bin)) {
            byte[] buf = new byte[4096];
            int read;
            while ((read = inputStream.read(buf)) != -1) {
                byte[] sector = Arrays.copyOf(buf, read);
                sectors.add(sector);
            }
        } catch (IOException ex) {
            showDialog("Error while initializing packets: " + ex.getMessage());
        }
        Log.d(TAG, "initPackets: sectors size = " + sectors.size());
        Intent intent = new Intent("SECTORS_SIZE");
        intent.putExtra("SECTORS_SIZE", sectors.size());
        sendBroadcast(intent);
        byte[] block = new byte[packetSize - 3];
        for (int index = 0; index < sectors.size(); index++) {
            byte[] sector = sectors.get(index);
            ByteArrayInputStream stream = new ByteArrayInputStream(sector);
            int sequence = 0;
            int read;
            while ((read = stream.read(block)) != -1) {
                int crc = 0;
                boolean bLast = stream.available() == 0;
                if (bLast) {
                    sequence = -1;
                    crc = EspCRC16.crc(sector);
                }

                int len = bLast ? read + 5 : read + 3;
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream(len);
                try {
                    outputStream.write(index & 0xff);
                    outputStream.write((index >> 8) & 0xff);
                    outputStream.write(sequence);
                    outputStream.write(block, 0, read);
                    if (bLast) {
                        outputStream.write(crc & 0xff);
                        outputStream.write((crc >> 8) & 0xff);
                    }
                    packets.add(outputStream.toByteArray());
                } catch (Exception ex) {
                    showDialog("Error while writing outputstream: " + ex.getMessage());
                }
                sequence++;
            }
            packets.add(sectorAckMark);
        }

        Log.d(TAG, "initPackets: packets size = " + packets.size());

    }
    private byte[] genCommandPacket(int id, byte[] payload) {
        byte[] packet = new byte[20];
        packet[0] = (byte) (id & 0xff);
        packet[1] = (byte) ((id >> 8) & 0xff);
        System.arraycopy(payload, 0, packet, 2, payload.length);
        int crc = EspCRC16.crc(packet, 0, 18);
        packet[18] = (byte) (crc & 0xff);
        packet[19] = (byte) ((crc >> 8) & 0xff);
        return packet;
    }

    private void postCommandStart() {
        Log.i(TAG, "postCommandStart");

        int binSize = bin.length;
        byte[] payload = new byte[] {
                (byte) (binSize & 0xff),
                (byte) ((binSize >> 8) & 0xff),
                (byte) ((binSize >> 16) & 0xff),
                (byte) ((binSize >> 24) & 0xff)
        };
        byte[] packet = genCommandPacket(COMMAND_ID_START, payload);
        if (commandChar != null) {
            if (bluetoothGatt != null) {
                writeCharacteristic(CHAR_COMMAND_UUID, packet);
            }
        } else {
            logQuick("Command char is null");
            handlerToast.post(() -> Toast.makeText(getApplicationContext(), "Command char is null", Toast.LENGTH_LONG).show());
        }
    }

    private void receiveCommandStartAck(int status) {
        Log.i(TAG, "receiveCommandStartAck: status=" + status);
        switch(status) {
            case COMMAND_ACK_ACCEPT:
                postBinData();
                updateOtaStatus("Starting OTA update...");
                break;
            case COMMAND_ACK_REFUSE:
                updateOtaStatus("Device refused OTA start request, try rebooting the BMS");
                break;
        }
    }

    private void postCommandEnd() {
        Log.i(TAG, "postCommandEnd");
        byte[] payload = new byte[0];
        byte[] packet = genCommandPacket(COMMAND_ID_END, payload);
        if (commandChar != null) {
            commandChar.setValue(packet);
            if (bluetoothGatt != null) {
                bluetoothGatt.writeCharacteristic(commandChar);
            }
        }
    }

    private void receiveCommandEndAck(int status) {
        Log.i(TAG, "receiveCommandEndAck: status=" + status);
        switch (status) {
            case COMMAND_ACK_ACCEPT:
                updateOtaStatus("OTA update complete!");
                handlerToast.post(() -> Toast.makeText(getApplicationContext(), "OTA update complete!", Toast.LENGTH_LONG).show());
                break;
            case COMMAND_ACK_REFUSE:
                updateOtaStatus("Device refuse OTA end request");
                break;
        }
    }

    private void postBinData() {
        new Thread(() -> {
            try {
                initPackets();
            } catch (IOException ex) {
                showDialog("Error while posting packet: " + ex.getMessage());
            }
            postNextPacket();
        }).start();
    }
    private void postNextPacket() {
        byte[] packet = packets.pollFirst();
        if (packet == null) {
            postCommandEnd();
        } else if (packet == sectorAckMark) {
            Log.d(TAG, "postNextPacket: wait for sector ACK");
        } else {
            if (recvFwChar != null && bluetoothGatt != null) {
                recvFwChar.setValue(packet);
                bluetoothGatt.writeCharacteristic(recvFwChar);
            }
        }
    }

    private void parseSectorAck(byte[] data) {
        try {
            int expectIndex = sectorAckIndex.getAndIncrement();
            int ackIndex = ((data[0] & 0xFF) | ((data[1] << 8) & 0xFF00));

            if (ackIndex != expectIndex) {
                Log.w(TAG, "takeSectorAck: Receive error index " + ackIndex + ", expect " + expectIndex);
                updateOtaStatus("takeSectorAck: Receive error index " + ackIndex + ", expect " + expectIndex);
                return;
            }

            int ackStatus = ((data[2] & 0xFF) | ((data[3] << 8) & 0xFF00));

            Log.d(TAG, "takeSectorAck: index=" + ackIndex + ", status=" + ackStatus);

            switch (ackStatus) {
                case BIN_ACK_SUCCESS:
                    postNextPacket();
                    Intent intent = new Intent("OTA_PROGRESS");
                    intent.putExtra("OTA_PROGRESS", ackIndex);
                    logQuick("ackindex: " + ackIndex);
                    sendBroadcast(intent);
                    break;
                case BIN_ACK_CRC_ERROR:
                    onError(2);
                    updateOtaStatus("BIN_ACK_CRC_ERROR");
                    return;
                case BIN_ACK_SECTOR_INDEX_ERROR:
                    int devExpectIndex = ((data[4] & 0xFF) | ((data[5] << 8) & 0xFF00));
                    Log.w(TAG, "parseSectorAck: device expect index = " + devExpectIndex);
                    updateOtaStatus("BIN_ACK_SECTOR_INDEX_ERROR, parseSectorAck: device expect index = " + devExpectIndex);
                    return;
                case BIN_ACK_PAYLOAD_LENGTH_ERROR:
                    updateOtaStatus("BIN_ACK_PAYLOAD_LENGTH_ERROR");
                    return;
                default:
                   updateOtaStatus("Unkown error");
            }
        } catch (Exception e) {
            Log.w(TAG, "parseSectorAck error", e);
            updateOtaStatus("parseSectorAck error, msg: " + e.getMessage());
        }
    }

    private void parseCommandPacket() {
        byte[] packet = commandChar.getValue();

        Log.i(TAG, "parseCommandPacket: " + Arrays.toString(packet));

        if (REQUIRE_CHECKSUM) {
            int crc = ((packet[18] & 0xFF) | ((packet[19] << 8) & 0xFF00));
            int checksum = EspCRC16.crc(packet, 0, 18);
            if (crc != checksum) {
                Log.w(TAG, "parseCommandPacket: Checksum error: " + crc + ", expect " + checksum);
                updateOtaStatus("parseCommandPacket: Checksum error: " + crc + ", expect " + checksum);
                return;
            }
        }

        int id = ((packet[0] & 0xFF) | ((packet[1] << 8) & 0xFF00));
        logQuick("id : " + id);
        if (id == COMMAND_ID_ACK) {
            int ackId = ((packet[2] & 0xFF) | ((packet[3] << 8) & 0xFF00));
            int ackStatus = ((packet[4] & 0xFF) | ((packet[5] << 8) & 0xFF00));
            switch (ackId) {
                case COMMAND_ID_START:
                    logQuick("receive command start");
                    receiveCommandStartAck(ackStatus);
                    break;
                case COMMAND_ID_END:
                    receiveCommandEndAck(ackStatus);
                    break;
                default:
                    // Handle unrecognized ackId
                    updateOtaStatus("Unknown acknowledgement ID");
                    break;
            }

        }
    }
            public void logQuick(String message) {
        Log.d(TAG, message);
    }

    public void setIsMinimized(boolean minimized) {
        isMinimized = minimized;
    }

    public void onError(int error) {
        logQuick("error: " + error);
    }
    public BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            logQuick("CONSTATE CHANGE met status " + status);

            if (newState == BluetoothGatt.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                bluetoothGatt = gatt;
                Log.i(TAG, "Connected to GATT server.");
                handlerToast.post(() -> Toast.makeText(getApplicationContext(), "Connected", Toast.LENGTH_LONG).show());
                Intent intent = new Intent("CONNECTION_STATE_CHANGED");
                intent.putExtra("CONNECTION_STATE_CHANGED", true);
                logQuick("sending broadcast from bleservice");
                sendBroadcast(intent);
                if(!gatt.requestMtu(MTU_SIZE)) {
                    onMtuChanged(gatt, MTU_SIZE, MTU_STATUS_FAILED);
                }
                //no fuckin clue why i get GATT_INSUFFICIENT_AUTHORIZATION on disconnect
            } else if (newState == BluetoothGatt.STATE_DISCONNECTED && (status == BluetoothGatt.GATT_SUCCESS|| status == BluetoothGatt.GATT_INSUFFICIENT_AUTHORIZATION)) {
                Log.i(TAG, "Disconnected from GATT server.");
                handlerToast.post(() -> Toast.makeText(getApplicationContext(), "Disconnected", Toast.LENGTH_LONG).show());
                Intent intent = new Intent("CONNECTION_STATE_CHANGED");
                intent.putExtra("CONNECTION_STATE_CHANGED", false);
                sendBroadcast(intent);
                homefragmentBluetoothGattCharacteristicList.clear();
                tempHomefragmentBluetoothGattCharacteristicList.clear();
               // bluetoothGatt = null;
            }
        }

        // Services discovered
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {

                for (BluetoothGattService gattService : gatt.getServices()) {
                    if ((gattService.getUuid().toString()).startsWith("3000", 4) || (gattService.getUuid().toString()).startsWith("4000", 4)) {
                        for (BluetoothGattCharacteristic mCharacteristic : gattService.getCharacteristics()) {
                            Log.i(TAG, "Found Characteristic: " + mCharacteristic.getUuid().toString());
                            bluetoothGattCharacteristicList.add(mCharacteristic);
                            if (mCharacteristic.getUuid().toString().startsWith("3001", 4) || mCharacteristic.getUuid().toString().startsWith("3002", 4)
                                    || mCharacteristic.getUuid().toString().startsWith("3003", 4) || mCharacteristic.getUuid().toString().startsWith("3005", 4)
                                    || mCharacteristic.getUuid().toString().startsWith("3006", 4) || mCharacteristic.getUuid().toString().startsWith("4008", 4)) {
                                homefragmentBluetoothGattCharacteristicList.add(mCharacteristic);
                            }
                            if(mCharacteristic.getUuid().toString().startsWith("4001", 4) || mCharacteristic.getUuid().toString().startsWith("4002", 4)
                                    || mCharacteristic.getUuid().toString().startsWith("4003", 4) || mCharacteristic.getUuid().toString().startsWith("4004", 4)
                                    || mCharacteristic.getUuid().toString().startsWith("4005", 4) || mCharacteristic.getUuid().toString().startsWith("4006", 4)
                                    || mCharacteristic.getUuid().toString().startsWith("4008", 4)) {
                                parameterCharacteristicList.add(mCharacteristic);
                            }
                        }
                    } else if((gattService.getUuid().toString()).startsWith("8018", 4)) {
                        for (BluetoothGattCharacteristic mCharacteristic : gattService.getCharacteristics()) {
                            Log.i(TAG, "Found Characteristic: " + mCharacteristic.getUuid().toString());
                            if(mCharacteristic.getUuid().toString().startsWith(CHAR_COMMAND_UUID, 4)) {
                                commandChar = mCharacteristic;
                            } else if(mCharacteristic.getUuid().toString().startsWith(CHAR_CUSTOMER_UUID, 4)) {
                                customerChar = mCharacteristic;
                            } else if(mCharacteristic.getUuid().toString().startsWith(CHAR_PROGRESS_UUID, 4)) {
                                progressChar = mCharacteristic;
                            } else if(mCharacteristic.getUuid().toString().startsWith(CHAR_RECV_FW_UUID, 4)) {
                                recvFwChar = mCharacteristic;
                            }
                            otaCharacteristicList.add(mCharacteristic);
                            bluetoothGattCharacteristicList.add(mCharacteristic);
                        }

                    }
                    logQuick("onServicesDiscovered UUID: " + gattService.getUuid().toString());
                }
                tempHomefragmentBluetoothGattCharacteristicList.addAll(homefragmentBluetoothGattCharacteristicList);
                logQuick("homefraglist size: " + tempHomefragmentBluetoothGattCharacteristicList.size());
                tempBluetoothGattCharacteristicList.addAll(bluetoothGattCharacteristicList);
                toggleFaultNotifications();
            }
        }
        @Override
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            super.onMtuChanged(gatt, mtu, status);
            if(status == BluetoothGatt.GATT_SUCCESS) {
                packetSize = EXPECT_PACKET_SIZE;
                logQuick("MTU SUCCESSFUL");
            } else {
                packetSize = 20;
                logQuick("MTU NOT SUCCESSFUL");
            }
            bluetoothGatt.discoverServices();
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            if(descriptor.getCharacteristic().getUuid().toString().startsWith("8", 4)) {
                if(status != BluetoothGatt.GATT_SUCCESS) {
                    updateOtaStatus("Enabling notifications failed, status=" + status + ", char=" + descriptor.getCharacteristic().getUuid().toString());
                }
                otaCharacteristicsToGetNotificationsOnList.remove(otaCharacteristicsToGetNotificationsOnList.get(otaCharacteristicsToGetNotificationsOnList.size() - 1));
                if(!otaCharacteristicsToGetNotificationsOnList.isEmpty()) {
                    writeOtaDescriptors();
                } else {
                    logQuick("all ota descriptors written");
                    updateOtaStatus("Successfully enabled notifications");
                    postCommandStart();
                }
            } else {
                if (!descriptor.getCharacteristic().getUuid().toString().startsWith("3007", 4) && !descriptor.getCharacteristic().getUuid().toString().startsWith("8018", 4)) {
                    characteristicsToGetNotificationsOnList.remove(characteristicsToGetNotificationsOnList.get(characteristicsToGetNotificationsOnList.size() - 1));
                    if (!characteristicsToGetNotificationsOnList.isEmpty()) {
                        writeDescriptors();
                    } else {
                        logQuick("all descriptors written");
                    }
                } else {//always do this
                    writeAllParametersToBMS();
                }
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            if (!isMinimized && !characteristic.getUuid().toString().startsWith("3007", 4) && !characteristic.getUuid().toString().startsWith("8", 4)) {
                String uuid = characteristic.getUuid().toString().substring(4, 8);
                byte[] data = characteristic.getValue();
                Intent intent = new Intent(uuid);
                intent.putExtra(uuid, data);
                sendBroadcast(intent);
            } else if (characteristic.getUuid().toString().startsWith("3007", 4)) {
                String uuid = characteristic.getUuid().toString().substring(4, 8);
                byte[] data = characteristic.getValue();
                Intent intent = new Intent(uuid);
                intent.putExtra(uuid, data);
                sendBroadcast(intent);
            } else if(characteristic.getUuid().toString().startsWith("8", 4)) {
                if (characteristic.equals(recvFwChar)) {
                    //logQuick("receivefwchar");
                    parseSectorAck(characteristic.getValue());
                } else if (characteristic.equals(progressChar)) {
                    // Handle progressChar case (if needed)
                    logQuick("progress " + Arrays.toString(characteristic.getValue()));
                } else if (characteristic.equals(commandChar)) {

                    parseCommandPacket();
                } else if (characteristic.equals(customerChar)) {
                    // Handle customerChar case (if needed)
                }
            }
        }
        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            //logQuick("written to: " + characteristic.getUuid().toString().substring(4, 8));
            if(!characteristic.getUuid().toString().startsWith("8",4)) {
               // logQuick("size: " + parameterCharacteristicList.size());
                if (!parameterCharacteristicList.isEmpty()) {
                    parameterCharacteristicList.remove(parameterCharacteristicList.get(parameterCharacteristicList.size() - 1));
                    if (!parameterCharacteristicList.isEmpty()) {
                        writeAllParametersToBMS();
                    } else {//all parameters were written to the bms, now read the initial battery information
                        logQuick("auto update state: " + sharedPreferences.getBoolean("auto_update", false));
                        Intent intent = new Intent("READY_TO_READ_CHARS");
                        intent.putExtra("READY_TO_READ_CHARS", true);
                        sendBroadcast(intent);
                    }
                }
            } else if(characteristic.getUuid().toString().startsWith(CHAR_RECV_FW_UUID, 4)){
                postNextPacket();
                if(status != BluetoothGatt.GATT_SUCCESS) {
                    Log.e(TAG, "onCharacteristicWrite: status=" + status);

                }
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            String uuid = characteristic.getUuid().toString().substring(4, 8);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                byte[] data = characteristic.getValue();
                Intent intent = new Intent(uuid);
                intent.putExtra(uuid, data);
                sendBroadcast(intent);
                if (readingHomefragmentCharacteristics) {
                    tempHomefragmentBluetoothGattCharacteristicList.remove(tempHomefragmentBluetoothGattCharacteristicList.get(tempHomefragmentBluetoothGattCharacteristicList.size() - 1));
                    if (!tempHomefragmentBluetoothGattCharacteristicList.isEmpty()) {
                        requestHomefragmentCharacteristics();
                    } else {
                        readingHomefragmentCharacteristics = false;
                        tempHomefragmentBluetoothGattCharacteristicList.addAll(homefragmentBluetoothGattCharacteristicList);
                    }
                }
                /*
                if (!tempBluetoothGattCharacteristicList.isEmpty()) {
                    requestCharacteristics();
                } else {
                    logQuick("all chars read");
                    end = System.currentTimeMillis();
                    logQuick("end end"+ end);
                    logQuick("Time taken: " + (end - start) + "ms");
                    tempBluetoothGattCharacteristicList.addAll(bluetoothGattCharacteristicList);
                }

                 */
            }
        }
    };

    public void requestCharacteristics() {
        logQuick("request chars");
        bluetoothGatt.readCharacteristic(tempBluetoothGattCharacteristicList.get(tempBluetoothGattCharacteristicList.size() - 1));
    }

    public void requestHighPriorityConnection() {

        if(bluetoothGatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)) {
            updateOtaStatus("Successfully requested high priority connection");
        } else {
            updateOtaStatus("High priority connection request unsuccessful");
        }

    }
    public void writeAllParametersToBMS() {

        if(!parameterCharacteristicList.isEmpty()) {
            boolean checked;
            byte[] data = new byte[0];
            logQuick("size of params: " + parameterCharacteristicList.size());
            switch (parameterCharacteristicList.get((parameterCharacteristicList.size() - 1)).getUuid().toString().substring(4,8)) {
                case "4008":
                    checked = sharedPreferences.getBoolean("only_balance_while_charging", false);
                    data = new byte[1];
                    data[0] = (byte) (checked ? 1 : 0);
                    logQuick("char is 4008");
                    break;
                case "4001":
                    data = new byte[1];
                    data[0] = Byte.parseByte(sharedPreferences.getString("shunt_value", "5"));
                    break;
                case "4002":
                    data = new byte[2];
                    data[0] = (byte) (Integer.parseInt(sharedPreferences.getString("overcharge_current", "10000") ) & 0xFF);
                    data[1] = (byte) ((Integer.parseInt(sharedPreferences.getString("overcharge_current", "10000")) >> 8) & 0xFF);
                    break;
                case "4003":
                    data = new byte[2];
                    data[0] = (byte) (Integer.parseInt(sharedPreferences.getString("undervolt", "3000")) & 0xFF);
                    data[1] = (byte) ((Integer.parseInt(sharedPreferences.getString("undervolt", "3000")) >> 8) & 0xFF);
                    break;
                case "4004":
                    data = new byte[2];
                    data[0] = (byte) (Integer.parseInt(sharedPreferences.getString("overvolt", "4200")) & 0xFF);
                    data[1] = (byte) ((Integer.parseInt(sharedPreferences.getString("overvolt", "4200")) >> 8) & 0xFF);
                    break;
                case "4005":
                    data = new byte[4];
                    data[0] = (byte) ((Integer.parseInt(sharedPreferences.getString("min_balance_voltage", "3900")) & 0xFF));
                    data[1] = (byte) ((Integer.parseInt(sharedPreferences.getString("min_balance_voltage", "3900")) >> 8) & 0xFF);
                    data[2] = (byte) (Integer.parseInt(sharedPreferences.getString("max_cell_voltage_diff", "15")) & 0xFF);
                    data[3] = (byte) ((Integer.parseInt(sharedPreferences.getString("max_cell_voltage_diff", "15")) >> 8) & 0xFF);
                    break;
                case "4006":
                    data = new byte[1];
                    data[0] = Byte.parseByte(sharedPreferences.getString("idle_current_threshold", "100"));
                    break;
            }
            writeCharacteristic((parameterCharacteristicList.get((parameterCharacteristicList.size() - 1)).getUuid().toString().substring(4,8)), data);
        }
    }
    public void requestHomefragmentCharacteristics() {
        readingHomefragmentCharacteristics = true;
        logQuick("request homefrag chars");
        if (!tempHomefragmentBluetoothGattCharacteristicList.isEmpty()) {
            bluetoothGatt.readCharacteristic(tempHomefragmentBluetoothGattCharacteristicList.get(tempHomefragmentBluetoothGattCharacteristicList.size() - 1));
        }
    }

    private BluetoothGattCharacteristic getCharacteristicByUUID(String uuid) {
        for (BluetoothGattCharacteristic characteristic : bluetoothGattCharacteristicList) {
            if (characteristic.getUuid().toString().startsWith(uuid, 4)) {
                return characteristic;
            }
        }
        return null;
    }

    public void readAllCharacteristics() {
        if (bluetoothAdapter == null || bluetoothGatt == null) {
            logQuick("BluetoothAdapter not initialized");
            return;
        }
        requestCharacteristics();
    }

    public void readCharacteristicsForHomefragment() {
        if (bluetoothAdapter == null || bluetoothGatt == null) {
            logQuick("BluetoothAdapter not initialized");
            return;
        }
        readingHomefragmentCharacteristics = true;
        requestHomefragmentCharacteristics();
    }
    private void showDialog(String message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(message)
                .setPositiveButton("ok", (dialog, which) -> {

                });
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void updateOtaStatus(String message) {
        Intent intent = new Intent("OTA_STATUS");
        intent.putExtra("OTA_STATUS", message);
        sendBroadcast(intent);
    }

    public void writeCharacteristic(String uuid, byte[] value) {
        logQuick("uuid: " + uuid);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (getConnectionState() == BluetoothProfile.STATE_CONNECTED) {
                bluetoothGatt.writeCharacteristic(Objects.requireNonNull(getCharacteristicByUUID(uuid)), value, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
            }
        } else if (getConnectionState() == BluetoothProfile.STATE_CONNECTED) {
            BluetoothGattCharacteristic characteristic = getCharacteristicByUUID(uuid);
            if (characteristic != null) {
                characteristic.setValue(value);
                bluetoothGatt.writeCharacteristic(characteristic);
            }
        }
    }
}
