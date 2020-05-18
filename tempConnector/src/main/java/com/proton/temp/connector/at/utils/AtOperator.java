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
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
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
     * 正在连接的标志，防止重复发指令
     */
    private boolean isConnecting = false;

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
     * 是否是主模式
     */
    private boolean isRoleMaster;
    /**
     * 是否是手动模式
     */
    private boolean isImmeManual;

    /**
     * 连接准备，发送“AT”用于验证串口是否可用，或者为了断掉上次的连接
     */
    private boolean prepare;

    /**
     * 要写入的数据
     */
    private String tHex;

    /**
     * 温度的缓存数据,大小为20个字节
     */
    private ByteBuffer byteBuffer = ByteBuffer.allocate(getTempDataLength());

    /**
     * 检测准备串口是否可用的定时器，不可用则重复发送“AT”
     */
    private Timer mCheckPrepareTimer;

    /**
     * 检测AT结果
     */
//    private boolean isOk;
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
     * 连接前准备,打开串口
     */

    public void connectPrepare() {
        if (isConnecting) {
            Logger.w("正在连接中,请稍后。。。");
            return;
        }
        isConnecting = true;
        openSerialPort(new PortConnectListener() {
            @Override
            public void onConnectSuccess() {
                super.onConnectSuccess();
                connectDevice();
            }

            @Override
            public void onConnectFaild(String msg) {
                super.onConnectFaild(msg);
                isConnecting = false;
                connectPrepare();
            }
        });
    }

    /**
     * 连接设备
     * 1.判断串口是否打开
     * 2.连接准备是否完成（成功发送"AT"指令）
     * 3.开始建立连接
     */
    public void connectDevice() {
        //连接之前确保先输入AT，也可以说是connectPrepared
        if (!prepare) {
//            if (isOk) {
//                isOk = false;
//            }
            Logger.w("AT初始化准备。。。");
            currentInstruction = atInstruction.connectPrepare();
            currentInstructionType = InstructionType.PREPARE;
            send(currentInstruction);
            checkPrepareStatus();
            return;
        }

        //验证串口是否是:"主模式"和"手动连接模式"
        if (!isRoleMaster || !isImmeManual) {
            isRoleMaster();
        } else {
            Logger.w(String.format("开始连接,patchType: %s ,patchMac : %s ", patchType, patchMac));
            connectStatus = 1;
            currentInstruction = atInstruction.connectDevice(patchType, patchMac);
            currentInstructionType = InstructionType.COON;
            Logger.w("连接指令:", currentInstruction);
            send(currentInstruction);
            //重置连接准备状态，方便重连时候调用
            prepare = false;
        }
    }

    /**
     * 获取序列号
     */
    private void fetchSerialNum() {
        currentInstruction = atInstruction.readCharacteristic(HmUUID.CHARACTOR_SERIAL_NUM.getCharacteristicAlias());
        currentInstructionType = InstructionType.READ;
        send(currentInstruction);
    }

    /**
     * 获取版本号
     */
    private void fetchVersion() {
        currentInstruction = atInstruction.readCharacteristic(HmUUID.CHARACTOR_VERSION.getCharacteristicAlias());
        currentInstructionType = InstructionType.READ;
        send(currentInstruction);
    }

    /**
     * 订阅温度
     */
    private void subscribeTemp() {
        isFirstArrive = true;
        currentInstruction = atInstruction.notifyCharacteristic(HmUUID.CHARACTOR_TEMP.getCharacteristicAlias());
        currentInstructionType = InstructionType.NOTIFY;
        Logger.w("开始订阅温度。。。");
        send(currentInstruction);
    }

    /**
     * 写入前准备
     */
    public void sendDataPrepare(String tHex) {
        this.tHex = tHex;
        currentInstruction = atInstruction.sendDataPrepare("WR", HmUUID.CHARACTOR_CACHE_TEMP_SEND.getCharacteristicAlias());
        currentInstructionType = InstructionType.WRITE_DATA_PREPARE;
        Logger.w("写入前准备。。。");
        send(currentInstruction);
    }

    /**
     * 写入数据,没有返回值
     * 1.测试断开设备
     */
    private void writeData(String tHex) {
        Logger.w("写入数据," + tHex);
        send(tHex, true);
    }

    /**
     * 断开连接
     * 关闭串口
     */
    public void disConnect() {
        Logger.w("手动断开设备。。。");
        if (portConnected) {
            currentInstruction = atInstruction.disconnectDevice();
            currentInstructionType = InstructionType.DISCONNECT;
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
        Logger.w("newData is : ", newData, " ,instructionType is :", currentInstructionType.name());

        if (newData.startsWith(ResultConstant.COON_FAIL) || newData.endsWith(ResultConstant.COON_FAIL)) {
            connectStatus = 3;
            portConnected = false;
            Logger.w("连接失败");
            isConnecting = false;
            if (connectStatusListener != null) {
                connectStatusListener.onConnectFaild();
            }
            return;
        }

        /**
         * 注意判断条件，因为一个指令的数据是分多次返回的
         */
        switch (currentInstructionType) {

            case PREPARE://准备工作(验证串口是否可用，以及断开连接,每次连接前都需要先输入AT，并且有成功返回后才可进行下一步，否则串口可能出现断电情况)
                if (newData.startsWith(ResultConstant.AT) || newData.startsWith(ResultConstant.AT_LOST)) {
                    prepare = true;
                    currentInstructionType = InstructionType.NONE;
                    Logger.w("连接准备完成 , newData is : ", newData);
                    //关掉检测AT是否发送成功的定时器
                    clearTimer();
                    connectDevice();
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
                } else if (newData.startsWith(ResultConstant.AT_LOST) || newData.endsWith(ResultConstant.AT_LOST)) {//连接失败
                    Logger.w("连接失败");
                    connectStatus = 3;
                    isConnecting = false;
                    connectStatusListener.onConnectFaild();
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
                } else if (newData.startsWith(ResultConstant.AT_LOST) || newData.endsWith(ResultConstant.AT_LOST)) {//连接失败
                    Logger.w("连接失败");
                    connectStatus = 3;
                    isConnecting = false;
                    connectStatusListener.onConnectFaild();
                }
                break;

            case COON://连接设备
                if (newData.startsWith(ResultConstant.AT_LOST) || newData.endsWith(ResultConstant.AT_LOST)) {
                    Logger.w("连接失败");
                    connectStatus = 3;
                    isConnecting = false;
                    connectStatusListener.onConnectFaild();
                }
                if (newData.startsWith(ResultConstant.COON_START) && newData.endsWith(ResultConstant.COON_END)) {
                    currentInstructionType = InstructionType.NONE;
                    //获取序列号，验证是否连接成功,此处必须做延时操作，否则获取不到序列号
                    mHandler.postDelayed(() -> fetchSerialNum(), 500);
                }
                break;

            case READ://读取序列号和版本号
                //获取序列号
                if (isReadSerialNum(currentInstruction) && newData.length() == getSerialNumLength()) {
                    if (!isConnected()) {
                        Logger.w("设备连接成功");
                        connectStatus = 2;
                        connectStatusListener.onConnectSuccess();
                    }
                    Logger.w("serial is : ", newData);
                    dataListener.receiveSerial(newData);
                    fetchVersion();
                }

                //获取版本号
                if (isReadVersion(currentInstruction) && newData.length() == getHardVersionLength()) {
                    Logger.w("hardVersion is : ", newData);
                    dataListener.receiveHardVersion(newData);
                    //订阅温度
                    subscribeTemp();
                }

                if (newData.startsWith(ResultConstant.AT_LOST) || newData.endsWith(ResultConstant.AT_LOST)) {
                    Logger.w("设备连接失败");
                    connectStatus = 3;
                    isConnecting = false;
                    connectStatusListener.onConnectFaild();
                }
                break;

            case NOTIFY://订阅温度
                int position = byteBuffer.position();
                int length = data.length;

                Logger.w("byteBuffer position : ", position, " ,data length is :", length);

                //只能循环加入byteBuffer，因为每个温度数据最后都有一个“ln”
                for (int i = 0; i < data.length; i++) {
                    if (byteBuffer.position() < byteBuffer.capacity()) {
                        byteBuffer.put(data[i]);
                    }
                }

                if (newData.length() == ResultConstant.NOTIFY_SUCCESS.length() && isFirstArrive) {
                    isFirstArrive = false;
                    Logger.w("温度订阅成功 ：", newData);
                    sb.delete(0, sb.length());
                    byteBuffer.clear();
                } else if (newData.length() == getTempDataLength()) {
                    for (int i = 0; i < byteBuffer.array().length; i++) {
                        Logger.w("打印byte值：", byteBuffer.get(i));
                    }
                    List<TempDataBean> tempList = BleUtils.parseTempV1_5(byteBuffer.array());
                    Logger.w("实时温度 size ：", tempList.size());
                    if (dataListener != null) {
                        mHandler.post(() -> dataListener.receiveCurrentTemp(tempList));
                    }
                    sb.delete(0, sb.length());
                    byteBuffer.clear();
                } else if (newData.startsWith(ResultConstant.AT_LOST)) {//设备断电或异常断开
                    Logger.w(String.format("设备异常%s", newData));
                    connectStatus = 1;
                    isConnecting = false;
                    connectStatusListener.onDisconnect(false);
                } else if (newData.length() > 20) {
                    Logger.w("newData length 大于20，说明上个数据出现丢包，清空临时数据...");
                    sb.delete(0, sb.length());
                    byteBuffer.clear();
                }
                break;
            case WRITE_DATA_PREPARE://写入数据前准备
                if (newData.startsWith(ResultConstant.WRITE_DATA_PREPARE)) {
                    Logger.w("写入准备成功，开始写入数据:", newData);
                    currentInstructionType = InstructionType.NONE;
                    writeData(tHex);
                }
                break;

            case DISCONNECT://断开连接
                if (newData.startsWith(ResultConstant.AT_LOST)) {//断开成功
                    connectStatus = 0;
                    portConnected = false;
                    isConnecting = false;
                    connectStatusListener.onDisconnect(true);
                    currentInstructionType = InstructionType.NONE;
                }
                break;

            case NONE:
                if (newData.startsWith(ResultConstant.AT_LOST) || newData.endsWith(ResultConstant.AT_LOST) || newData.startsWith(ResultConstant.SEND_INSTRUCTION_EOR)) {
                    Logger.w(String.format("设备异常%s", newData));
                    connectStatus = 1;
                    isConnecting = false;
                    connectStatusListener.onDisconnect(false);//设备异常断开连接
                }
                break;
        }
    }

    /**
     * 检查串口输入"AT"后的状态，必须要做延时操作，否则容易导致串口异常关闭
     */
    private void checkPrepareStatus() {
        Logger.w("开启检测At是否发送成功的定时器");
        if (mCheckPrepareTimer == null) {
            mCheckPrepareTimer = new Timer();
            mCheckPrepareTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    if (mCheckPrepareTimer == null) return;
//                    if (isOk) {
//                        clearTimer();
//                    } else {
//                        connectDevice();
//                    }
                    connectDevice();
                }
            }, 5000, 5000);
        }
    }

    private void clearTimer() {
        if (mCheckPrepareTimer != null) {
            Logger.w("关闭检测AT状态定时器");
            mCheckPrepareTimer.cancel();
            mCheckPrepareTimer = null;
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
     * 温度数据字节数组长度
     *
     * @return
     */
    private int getTempDataLength() {
        return 20;
    }

    @Override
    public void onRunError(Exception e) {
        Logger.w("串口异常：", e.getMessage());
    }

    public boolean isConnected() {
        return connectStatus == 2;
    }

    public boolean isPortConnected() {
        return portConnected;
    }

    /**
     * 连接串口
     */
    public boolean openSerialPort(PortConnectListener portConnectListener) {
        Logger.w("正在打开串口 , deviceId is :", deviceId);
        UsbDevice device = null;
        UsbManager usbManager = (UsbManager) activity.getSystemService(Context.USB_SERVICE);
        for (UsbDevice v : usbManager.getDeviceList().values())
            if (v.getDeviceId() == deviceId)
                device = v;
        if (device == null) {
            portConnectListener.onConnectFaild("connection failed: device not found");
            return false;
        }
        UsbSerialDriver driver = UsbSerialProber.getDefaultProber(context).probeDevice(device);
        if (driver == null) {
            driver = CustomProber.getCustomProber().probeDevice(device);
        }
        if (driver == null) {
            portConnectListener.onConnectFaild("connection failed: no driver for device");
            return false;
        }
        if (driver.getPorts().size() < portNum) {
            portConnectListener.onConnectFaild("connection failed: not enough ports at device");
            return false;
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
            return false;
        }

        if (usbConnection == null) {
            if (!usbManager.hasPermission(driver.getDevice()))
                portConnectListener.onConnectFaild("connection failed: permission denied");
            else
                portConnectListener.onConnectFaild("connection failed: open failed");
            return false;
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
            return true;
        } catch (Exception e) {
            portConnected = false;
            portConnectListener.onConnectFaild("connection failed: " + e.getMessage());
            return false;
        }
    }

    private void send(String str) {
        //清空StringBuilder
        sb.delete(0, sb.length());
        send(str, false);
    }

    private void send(String str, boolean isHexString) {
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

    /**
     * 关闭串口,关闭之后需要重新插拔上电,不需要这个方法
     */
    private void closeSerialPort() {
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


}
