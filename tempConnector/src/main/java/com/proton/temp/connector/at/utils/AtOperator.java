package com.proton.temp.connector.at.utils;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.util.SerialInputOutputManager;
import com.proton.temp.connector.at.CustomProber;
//import com.proton.temp.connector.at.UsbPermission;
import com.proton.temp.connector.at.instruction.AtInstruction;
import com.proton.temp.connector.at.instruction.HmUUID;
import com.proton.temp.connector.at.instruction.IDeviceInstruction;
import com.proton.temp.connector.at.instruction.InstructionType;
import com.proton.temp.connector.at.instruction.ResultConstant;
import com.proton.temp.connector.at.interfaces.PortConnectListener;
import com.proton.temp.connector.bean.TempDataBean;
import com.proton.temp.connector.bluetooth.utils.BleUtils;
import com.proton.temp.connector.interfaces.ConnectStatusListener;
import com.proton.temp.connector.interfaces.DataListener;
import com.wms.logger.Logger;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Executors;


/**
 * @Description: 指令工具类（发送数据后，所有的回调都在onNewData里面）
 * @function :  1:设置主模式 2：设置自动模式 3：连接设备 4：订阅温度 5：写入数据 （扫描设备放在蓝牙里完成，因为模块的扫描功能有限，抗干扰和扫描能力都很低）
 * @Author: yxf
 * @CreateDate: 2020/4/29 15:00
 * @UpdateUser: yxf
 * @UpdateDate: 2020/4/29 15:00
 */
public class AtOperator implements SerialInputOutputManager.Listener {

    public static final String ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION";

    private Activity activity;
    private Context context;
//    private UsbPermission usbPermission = UsbPermission.Unknown;

    private boolean portConnected = false;
    private int deviceId, portNum, baudRate;
    private boolean withIoManager;

    private static final int WRITE_WAIT_MILLIS = 2000;
    private static final int READ_WAIT_MILLIS = 2000;
    /**
     * 订阅数据时间间隔
     */
    private static final int NOTIFY_INTERVAL = 8000;
    private SerialInputOutputManager usbIoManager;
    private UsbSerialPort usbSerialPort;
    /**
     * 用于接收收到的数据
     */
    private StringBuilder sb = new StringBuilder();

    /**
     * 相关操作指令集
     */
    private IDeviceInstruction atInstruction = new AtInstruction();

    private PortConnectListener portConnectListener;
    private ConnectStatusListener connectStatusListener;
    private DataListener dataListener;

    /**
     * 当前指令
     */
    private String currentInstruction;

    private InstructionType currentInstructionType = InstructionType.NONE;

    private Handler mHandler = new Handler(Looper.getMainLooper());

    /**
     * 0：未连接 1：连接中 2：已连接 3：连接失败
     */
    private int connectStatus = 0;

    /**
     * 订阅温度，首次返回的数据（OK+DATA-OK）
     */
    private boolean isFirstArrive = true;

    /**
     * 当前连接的体温贴mac
     */
    private String patchMac;

    /**
     * disc扫描出的体温贴类型，此处为0
     */
    private int patchType;

    /**
     * 重连标识
     */
    private boolean isReconnect = true;

    /**
     * 是否是主动断开
     */
    private boolean isManualDisconnect;

    /**
     * 是否是主模式
     */
    private boolean isRoleMaster;
    /**
     * 是否是手动模式
     */
    private boolean isImmeManual;


    public AtOperator(Activity activity, Context context, int deviceId, int portNum, int baudRate, boolean withIoManager) {
        this.activity = activity;
        this.context = context;
        this.deviceId = deviceId;
        this.portNum = portNum;
        this.baudRate = baudRate;
        this.withIoManager = withIoManager;
    }

    public AtOperator(Activity activity, Context context, int deviceId, int portNum) {
        this(activity, context, deviceId, portNum, 9600, true);
    }

    public void setPatchMac(String patchMac) {
        this.patchMac = patchMac;
    }

    public void setPatchType(int patchType) {
        this.patchType = patchType;
    }

    /**
     * 设置打开串口的回调
     *
     * @param portConnectListener
     */
    public void setPortConnectListener(PortConnectListener portConnectListener) {
        this.portConnectListener = portConnectListener;
    }


    /**
     * 设置连接蓝牙设备的回调
     *
     * @param connectStatusListener
     */
    public void setConnectStatusListener(ConnectStatusListener connectStatusListener) {
        this.connectStatusListener = connectStatusListener;
    }

    /**
     * 实时数据回调
     *
     * @param dataListener
     */
    public void setDataListener(DataListener dataListener) {
        this.dataListener = dataListener;
    }

    /**
     * 设置成主模式
     */
    private void setRoleMaster() {
        currentInstruction = atInstruction.setRoleMaster();
        currentInstructionType = InstructionType.ROLE;
        send(currentInstruction);
    }

    /**
     * 查看设备当前的模式是否是主模式
     */
    private void isRoleMaster() {
        Logger.w("查看当前role模式 ： ", isRoleMaster ? "主模式" : "从模式");
        currentInstruction = atInstruction.queryRole();
        currentInstructionType = InstructionType.ROLE;
        Logger.w("查询从模式指令:", currentInstruction);
        send(currentInstruction);
    }

    /**
     * 设置成手动模式，默认是自动连接模式，但是自动连接会产生找不到从机的问题
     */
    private void setImmeManual() {
        currentInstruction = atInstruction.setImmeManual();
        currentInstructionType = InstructionType.IMME;
        send(currentInstruction);
    }

    private void isImmeManual() {
        Logger.w("查看当前imme模式 ： ", isImmeManual ? "手动模式" : "自动模式");
        currentInstruction = atInstruction.queryImme();
        currentInstructionType = InstructionType.IMME;
        send(currentInstruction);
    }

    /**
     * 连接设备
     */
    public void connectDevice() {
        Logger.w(String.format("开始连接,patchType: %s ,patchMac : %s ", patchType, patchMac));
        if (!checkPortOpen()) {
            Logger.w("串口未开启");
            return;
        }
        connectStatus = 1;
        currentInstruction = atInstruction.connectDevice(patchType, patchMac);
        currentInstructionType = InstructionType.COON;
        if (!isRoleMaster || !isImmeManual) {
            isRoleMaster();
        } else {
            Logger.w("connect currentInstruction is : ", currentInstruction);
            send(currentInstruction);
        }
    }

    /**
     * 重连：连接失败、异常断开时候重连，主动断开设备不重连
     * ①首先确定断开设备:输入AT指令
     * ②重启串口
     * ③发起连接
     */
    private void reconnectDevice() {
        isReconnect = true;
        connectStatus = 1;
        //先断开连接，输入指令AT
        currentInstruction = atInstruction.disconnectDevice();
        send(currentInstruction);
    }

    /**
     * 重启串口
     */
    private void resetSerialPort() {
        Logger.w("重启串口。。。");
        //关闭串口
        closeSerialPort();
        //打开串口
        mHandler.postDelayed(() -> openSerialPort(), 200);
    }

    /**
     * 获取序列号
     */
    private void fetchSerialNum() {
        if (!checkPortOpen()) {
            return;
        }
        currentInstruction = atInstruction.readCharacteristic(HmUUID.CHARACTOR_SERIAL_NUM.getCharacteristicAlias());
        currentInstructionType = InstructionType.READ;
        send(currentInstruction);
    }

    /**
     * 获取版本号
     */
    private void fetchVersion() {
        if (!checkPortOpen()) {
            return;
        }
        currentInstruction = atInstruction.readCharacteristic(HmUUID.CHARACTOR_VERSION.getCharacteristicAlias());
        currentInstructionType = InstructionType.READ;
        send(currentInstruction);
    }

    /**
     * 订阅温度
     */
    private void subscribeTemp() {
        if (!checkPortOpen()) {
            return;
        }
        isFirstArrive = true;
        currentInstruction = atInstruction.notifyCharacteristic(HmUUID.CHARACTOR_TEMP.getCharacteristicAlias());
        currentInstructionType = InstructionType.NOTIFY;
        send(currentInstruction);
    }

    /**
     * 断开连接
     * 关闭串口
     */
    public void disConnect() {
        Logger.w("手动断开设备。。。");
        isManualDisconnect = true;
        if (portConnected) {
            currentInstruction = atInstruction.disconnectDevice();
            currentInstructionType = InstructionType.AT;
            //清空StringBuilder
            sb.delete(0, sb.length());
            send(currentInstruction);
        }
    }

    /**
     * 串口回调接口
     *
     * @param data
     */
    @Override
    public void onNewData(byte[] data) {
        sb.append(new String(data));
        if (sb == null || sb.toString() == null || TextUtils.isEmpty(sb.toString())) {
            return;
        }
        String newData = sb.toString().replaceAll("\r\n", "");
        Logger.w("newData is : ", newData);

        //当前指令不为设备连接，但是返回了OK+LOST，表示设备异常中断
        if (newData.startsWith(ResultConstant.AT_LOST) || newData.endsWith(ResultConstant.AT_LOST)) {

            //主动断开设备成功
            if (isManualDisconnect) {
                connectStatusListener.onDisconnect(true);
                closeSerialPort();
            }
            //连接失败,重连
            if (currentInstructionType == InstructionType.COON) {
                connectStatus = 3;
                Logger.w("连接失败");
                connectStatusListener.onConnectFaild();
                reconnectDevice();
            }
            return;
        }

        if (newData.startsWith(ResultConstant.COON_FAIL) || newData.endsWith(ResultConstant.COON_FAIL)) {
            connectStatus = 3;
            Logger.w("连接失败");
            if (connectStatusListener != null) {
                connectStatusListener.onConnectFaild();
            }
            reconnectDevice();
            return;
        }

        /**
         * 以下用于判断是否是同一个指令的数据，因为数据都是通过字节来返回的，且不是一起返回，所以需要判断
         */
        switch (currentInstructionType) {
            case AT:
                if (newData.startsWith(ResultConstant.AT)) {
                    if (isConnected()) {
                        Logger.w("断开连接");
                        connectStatusListener.onDisconnect(true);
                    } else {
                        Logger.w("验证串口是否打开 : ", portConnected);
                    }
                    connectStatus = 0;
                    currentInstructionType = InstructionType.NONE;
                }
                break;
            case ROLE://主从模式
                if (newData.startsWith(ResultConstant.GET_ROLE_IMME_0)) {//从模式，需要设置成主模式
                    setRoleMaster();
                    Logger.w("role is : ", newData);
                } else if (newData.startsWith(ResultConstant.GET_ROLE_IMME_1)) {//主模式
                    isRoleMaster = true;
                    isImmeManual();
                    Logger.w("role is : ", newData);
                } else if (newData.startsWith(ResultConstant.SET_ROLE_IMME_1)) {//设置主模式成功
                    isRoleMaster = true;
                    isImmeManual();
                    Logger.w("role is : ", newData);
                }

                break;
            case IMME://手动，自动模式
                if (newData.startsWith(ResultConstant.GET_ROLE_IMME_0)) {//自动模式，需要设置成手动模式
                    setImmeManual();
                    Logger.w("imme is : ", newData);
                } else if (newData.startsWith(ResultConstant.GET_ROLE_IMME_1)) {//手动模式
                    isImmeManual = true;
                    Logger.w("imme is : ", newData);
                    connectDevice();
                } else if (newData.startsWith(ResultConstant.SET_ROLE_IMME_1)) {//设置手动模式成功
                    isImmeManual = true;
                    Logger.w("imme is : ", newData);
                    connectDevice();
                }
                break;
            case COON:
                if (newData.startsWith(ResultConstant.AT_LOST) || newData.endsWith(ResultConstant.AT_LOST)) {
                    Logger.w("连接失败");
                    connectStatus = 3;
                }
                if (newData.startsWith(ResultConstant.COON_START) && newData.endsWith(ResultConstant.COON_END)) {
                    Logger.w("coon is :", newData);
                    currentInstructionType = InstructionType.NONE;
                    mHandler.postDelayed(() -> {
                        Logger.w("开始获取serialNum...");
                        fetchSerialNum();
                    }, 500);
                }

                break;
            case READ://没有标识
                Logger.w("设备连接成功。。。");
                if (!isConnected()) {
                    connectStatus = 2;
                }

                if (isReadSerialNum(currentInstruction) && newData.length() == getSerialNumLength()) {
                    Logger.w("serial is : ", newData);
                    dataListener.receiveSerial(newData);
                    fetchVersion();
                }

                if (isReadVersion(currentInstruction) && newData.length() == getHardVersionLength()) {
                    Logger.w("hardVersion is : ", newData);
                    dataListener.receiveHardVersion(newData);
                    subscribeTemp();
                }

                break;
            case NOTIFY://没有标识
                if (newData.length() == ResultConstant.NOTIFY_SUCCESS.length() && isFirstArrive) {
                    isFirstArrive = false;
                    Logger.w("温度订阅成功 ：", newData);
                    sb.delete(0, sb.length());
                } else if (newData.length() == getTempDataLength()) {
                    List<TempDataBean> tempList = BleUtils.parseTempV1_5(newData);
                    Logger.w("实时温度 size ：", tempList.size());
                    if (dataListener != null) {
                        mHandler.post(() -> dataListener.receiveCurrentTemp(tempList));
                    }
                    sb.delete(0, sb.length());
                } else if (newData.length() > 20) {
                    Logger.w("newData length 大于20，说明上个数据出现丢包，清空临时数据...");
                    sb.delete(0, sb.length());
                }
                break;
            case SET_WAY:
                if (newData.startsWith(ResultConstant.SET_WAY)) {
                    Logger.w(newData);
                    currentInstructionType = InstructionType.NONE;
                }
                break;
        }
    }

    /**
     * 是否是读序列号的指令
     *
     * @param instruction
     * @return
     */
    private boolean isReadSerialNum(String instruction) {
        return instruction.equalsIgnoreCase(atInstruction.readCharacteristic(HmUUID.CHARACTOR_SERIAL_NUM.getCharacteristicAlias()));
    }

    private boolean isReadVersion(String instruction) {
        return instruction.equalsIgnoreCase(atInstruction.readCharacteristic(HmUUID.CHARACTOR_VERSION.getCharacteristicAlias()));
    }

    /**
     * 序列号长度
     *
     * @return
     */
    private int getSerialNumLength() {
        return 11;
    }

    /**
     * 版本号长度
     *
     * @return
     */
    private int getHardVersionLength() {
        return 6;
    }

    /**
     * 温度数据长度
     *
     * @return
     */
    private int getTempDataLength() {
        return 20;
    }

    @Override
    public void onRunError(Exception e) {
        portConnectListener.onConnectFaild(e.getMessage());
        closeSerialPort();
    }

    public boolean isConnected() {
        return connectStatus == 2;
    }

    public boolean isPortConnected() {
        return portConnected;
    }

    /**
     * 检查串口是否打开
     *
     * @return
     */
    public boolean checkPortOpen() {
        if (!isPortConnected()) {
            mHandler.post(() -> openSerialPort());
            return false;
        } else {
            return true;
        }
    }

    /**
     * 连接串口
     */
    public void openSerialPort() {
        Logger.w("正在打开串口 , deviceId is :", deviceId);
            UsbDevice device = null;
            UsbManager usbManager = (UsbManager) activity.getSystemService(Context.USB_SERVICE);
            for (UsbDevice v : usbManager.getDeviceList().values())
                if (v.getDeviceId() == deviceId)
                    device = v;
            if (device == null) {
                portConnectListener.onConnectFaild("connection failed: device not found");
                return;
            }
            UsbSerialDriver driver = UsbSerialProber.getDefaultProber(context).probeDevice(device);
            if (driver == null) {
                driver = CustomProber.getCustomProber().probeDevice(device);
            }
            if (driver == null) {
                portConnectListener.onConnectFaild("connection failed: no driver for device");
                return;
            }
            if (driver.getPorts().size() < portNum) {
                portConnectListener.onConnectFaild("connection failed: not enough ports at device");
                return;
            }
            usbSerialPort = driver.getPorts().get(portNum);

            UsbDeviceConnection usbConnection;
            if (usbManager.hasPermission(driver.getDevice())) {
                Logger.w("已有usb权限");
                usbConnection = usbManager.openDevice(driver.getDevice());
            } else {
                Logger.w("没有usb权限");
                PendingIntent usbPermissionIntent = PendingIntent.getBroadcast(activity, 0, new Intent(ACTION_USB_PERMISSION), 0);
                usbManager.requestPermission(driver.getDevice(), usbPermissionIntent);
                return;
            }

            if (usbConnection == null) {
                if (!usbManager.hasPermission(driver.getDevice()))
                    portConnectListener.onConnectFaild("connection failed: permission denied");
                else
                    portConnectListener.onConnectFaild("connection failed: open failed");
                return;
            }

            try {
                usbSerialPort.open(usbConnection);
                usbSerialPort.setParameters(baudRate, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
                if (withIoManager) {
                    usbIoManager = new SerialInputOutputManager(usbSerialPort, this);
                    Executors.newSingleThreadExecutor().submit(usbIoManager);
                }
                portConnected = true;
                Logger.w("串口打开成功。。。");
                portConnectListener.onConnectSuccess();
            } catch (Exception e) {
                portConnectListener.onConnectFaild("connection failed: " + e.getMessage());
                closeSerialPort();
            }
    }

    /**
     * 关闭串口
     */
    public void closeSerialPort() {
        if (isPortConnected() || usbSerialPort == null) {
            return;
        }
        portConnected = false;
        if (usbIoManager != null)
            usbIoManager.stop();
        usbIoManager = null;
        try {
            usbSerialPort.close();
        } catch (IOException ignored) {
        }
        usbSerialPort = null;
        Logger.w("关闭串口");
    }

    private void send(String str) {
        //清空StringBuilder
        sb.delete(0, sb.length());
        send(str, false);
    }

    private void send(String str, boolean isHexString) {
        if (!portConnected) {
            portConnectListener.onConnectFaild("port not connected");
            return;
        }
        try {
            byte[] data;
            if (isHexString) {
                data = BleUtils.hexStringToBytes(str);
            } else {
                data = (str).getBytes();
            }
            usbSerialPort.write(data, WRITE_WAIT_MILLIS);
        } catch (Exception e) {
            onRunError(e);
        }
    }

}
