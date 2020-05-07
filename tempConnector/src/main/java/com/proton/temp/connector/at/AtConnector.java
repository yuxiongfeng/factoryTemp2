package com.proton.temp.connector.at;

import android.app.Activity;
import android.content.Context;
import android.text.TextUtils;

import com.proton.temp.connector.at.callback.OnScanListener;
import com.proton.temp.connector.at.callback.PortConnectListener;
import com.proton.temp.connector.at.utils.AtOperator;
import com.proton.temp.connector.interfaces.ConnectStatusListener;
import com.proton.temp.connector.interfaces.Connector;
import com.proton.temp.connector.interfaces.DataListener;
import com.wms.logger.Logger;

/**
 * @Description:
 * @Author: yxf
 * @CreateDate: 2020/4/29 14:58
 * @UpdateUser: yxf
 * @UpdateDate: 2020/4/29 14:58
 */
public class AtConnector implements Connector {

    private AtOperator atOperator;

    private String patchMac;
    /**
     * 扫描出来的type，用于连接设备的参数,体温贴的type默认是0，心电是1
     */
    private int patchType = 0;

    public AtConnector(Activity activity, Context context, int deviceId, int portNum) {
        this.atOperator = new AtOperator(activity, context, deviceId, portNum);
    }

    /**
     * 连接设备前必须先设置mac地址
     *
     * @param patchMac
     */
    public void setPatchMac(String patchMac) {
        this.patchMac = patchMac;
    }

    /**
     * 打开串口
     */
    public void openSerialPort(PortConnectListener portConnectListener) {
        /**
         * 设置串口状态回调
         */
        atOperator.setPortConnectListener(portConnectListener);
        atOperator.openSerialPort();
    }

    /**
     * 扫描设备
     */
    public void scanDevices(OnScanListener scanListener) {
        atOperator.scanDevice(scanListener);
    }

    @Override
    public void connect() {
        connect(null, null);
    }

    @Override
    public void connect(ConnectStatusListener connectorListener, DataListener dataListener) {

        atOperator.setConnectStatusListener(connectorListener);
        atOperator.setDataListener(dataListener);

        if (TextUtils.isEmpty(patchMac)) {
            Logger.w("patchMac is null");
            throw new NullPointerException("patchMac is null,please set first!!!");
        }
        atOperator.connectDevice(patchType, patchMac);
    }

    @Override
    public void disConnect() {
        atOperator.disConnect();
    }

    @Override
    public boolean isConnected() {
        return false;
    }

    @Override
    public void cancelConnect() {

    }

    @Override
    public void setSampleRate(int sampleRate) {

    }

    @Override
    public void setConnectTimeoutTime(long time) {

    }

    @Override
    public void setDisconnectTimeoutTime(long time) {

    }
}
