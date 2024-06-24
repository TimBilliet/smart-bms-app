package com.example.bmsapp.message;

public class StartCommandAckMessage extends CommandAckMessage {

    public StartCommandAckMessage(int status) {
        super(status);
    }

}