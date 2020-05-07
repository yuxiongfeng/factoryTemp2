package com.proton.temp.connector.at.instruction;

import com.proton.temp.connector.at.constant.InstructionConstant;

/**
 * @Description: 蓝牙模块通讯流程指令集
 * @Author: yxf
 * @CreateDate: 2020/4/29 15:02
 * @UpdateUser: yxf
 * @UpdateDate: 2020/4/29 15:02
 */
public class AtInstruction implements IDeviceInstruction {

    @Override
    public String queryRole() {
        return InstructionConstant.QUERY_ROLE;
    }

    @Override
    public String setRoleMaster() {
        return InstructionConstant.SET_ROLE_MASTER;
    }

    @Override
    public String queryImme() {
        return InstructionConstant.QUERY_IMME;
    }

    @Override
    public String setImmeManual() {
        return InstructionConstant.SET_IMME_MANUAL;
    }

    @Override
    public String scanDevices() {
        return InstructionConstant.SCAN_DEVICES;
    }

    @Override
    public String connectDevice(int type, String mac) {
        return InstructionConstant.CONN_PREFIX + type + mac;
    }

    @Override
    public String disconnectDevice() {
        return InstructionConstant.DISCONNECT_DEVICE;
    }

    @Override
    public String findAllCharacteristics() {
        return InstructionConstant.FIND_ALL_CHARACTERISTIC;
    }

    @Override
    public String notifyCharacteristic(String characteristicSerial) {
        return InstructionConstant.NOTIFY_CHARACTERISTIC_PREFIX + characteristicSerial;
    }

    @Override
    public String notifyOffCharacteristic(String characteristicSerial) {
        return InstructionConstant.NOTIFY_OFF_CHARACTERISTIC_PREFIX + characteristicSerial;
    }

    @Override
    public String readCharacteristic(String characteristicSerial) {
        return InstructionConstant.READ_CHARACTERISTIC_PREFIX + characteristicSerial;
    }

    @Override
    public String sendDataPrepare(String type, String characteristicSerial) {
        return InstructionConstant.SEND_DATA_PREPARE_PREFIX + type + characteristicSerial;
    }

}
