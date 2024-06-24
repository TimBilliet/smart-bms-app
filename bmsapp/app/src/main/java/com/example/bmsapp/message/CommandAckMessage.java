package com.example.bmsapp.message;

import com.example.bmsapp.BluetoothLeService;

public abstract class CommandAckMessage extends BleOTAMessage {
    public final int status;


    public CommandAckMessage(int status) {
        this.status = status;
    }

    public static class Companion {
        public static final int STATUS_ACCEPT = BluetoothLeService.COMMAND_ACK_ACCEPT;
        public static final int STATUS_REFUSE = BluetoothLeService.COMMAND_ACK_REFUSE;
    }
}