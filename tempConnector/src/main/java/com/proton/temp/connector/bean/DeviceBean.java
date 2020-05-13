package com.proton.temp.connector.bean;

import java.io.Serializable;

public class DeviceBean implements Serializable {
    private String dockerMacaddress;
    private ConnectionType connectionType = ConnectionType.BLUETOOTH;
    private boolean isNeedUpdate;
    private DeviceType deviceType = DeviceType.P03;

    private String macaddress;
    private String hardVersion;
    private int bluetoothRssi;
    private String serialNum;

    public DeviceBean(String macaddress, String hardVersion, int bluetoothRssi, String serialNum) {
        this.macaddress = macaddress;
        this.hardVersion = hardVersion;
        this.bluetoothRssi = bluetoothRssi;
        this.serialNum = serialNum;
    }

    public DeviceBean(String macaddress, DeviceType deviceType) {
        this.macaddress = macaddress;
        this.deviceType = deviceType;
    }

    public DeviceBean(String macaddress, String dockerMacaddress) {
        this.macaddress = macaddress;
        this.dockerMacaddress = dockerMacaddress;
        this.connectionType = ConnectionType.NET;
    }

    public DeviceBean(String macaddress, DeviceType deviceType, String hardVersion,int bluetoothRssi) {
        this.macaddress = macaddress;
        this.deviceType = deviceType;
        this.hardVersion = hardVersion;
        this.bluetoothRssi=bluetoothRssi;
    }

    public DeviceBean(String macaddress, ConnectionType connectionType) {
        this.macaddress = macaddress;
        this.connectionType = connectionType;
    }

    public DeviceBean(String macaddress) {
        this.macaddress = macaddress;
    }

    public DeviceBean() {
    }

    public String getMacaddress() {
        return macaddress;
    }

    public void setMacaddress(String macaddress) {
        this.macaddress = macaddress;
    }

    public DeviceType getDeviceType() {
        return deviceType;
    }

    public void setDeviceType(DeviceType deviceType) {
        this.deviceType = deviceType;
    }

    public boolean isNeedUpdate() {
        return isNeedUpdate;
    }

    public void setNeedUpdate(boolean needUpdate) {
        isNeedUpdate = needUpdate;
    }

    public String getHardVersion() {
        return hardVersion;
    }

    public void setHardVersion(String hardVersion) {
        this.hardVersion = hardVersion;
    }

    public ConnectionType getConnectionType() {
        return connectionType;
    }

    public void setConnectionType(ConnectionType connectionType) {
        this.connectionType = connectionType;
    }

    public String getDockerMacaddress() {
        return dockerMacaddress;
    }

    public void setDockerMacaddress(String dockerMacaddress) {
        this.dockerMacaddress = dockerMacaddress;
    }

    public int getBluetoothRssi() {
        return bluetoothRssi;
    }

    public void setBluetoothRssi(int bluetoothRssi) {
        this.bluetoothRssi = bluetoothRssi;
    }
}