package com.proton.temp.connector.at;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import com.proton.temp.connector.at.utils.AtOperator;
import com.proton.temp.connector.bean.TempDataBean;
import com.proton.temp.connector.interfaces.ConnectStatusListener;
import com.proton.temp.connector.interfaces.Connector;
import com.proton.temp.connector.interfaces.DataListener;
import com.wms.logger.Logger;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

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
     * 扫描出来的type，用于连接设备的参数
     */
    private int patchType = 0;
    public static final int disconnectTimeout = 20 * 1000;
    private DataListener dataListener;

    private ConnectStatusListener connectStatusListener;

    private DataListener mDataListener = new DataListener() {
        @Override
        public void receiveCurrentTemp(List<TempDataBean> temps) {
            super.receiveCurrentTemp(temps);
            mLastReceiveDataTime = System.currentTimeMillis();
            dataListener.receiveCurrentTemp(temps);
//            checkConnectStatus();
        }

        @Override
        public void receiveSerial(String serial) {
            super.receiveSerial(serial);
            dataListener.receiveSerial(serial);
        }

        @Override
        public void receiveHardVersion(String hardVersion) {
            super.receiveHardVersion(hardVersion);
            dataListener.receiveHardVersion(hardVersion);
        }
    };

    public AtConnector(Activity activity, Context context, int deviceId, int portNum) {
        atOperator = new AtOperator(activity, context, deviceId, portNum);
    }

    public void setPatchMac(String patchMac) {
        String mac = patchMac.toUpperCase().replaceAll(":", "");
        this.patchMac = mac;
    }

    @Override
    public void connect() {
        connect(null, null);
    }

    @Override
    public void connect(ConnectStatusListener connectStatusListener, DataListener dataListener) {
        this.dataListener = dataListener;
        this.connectStatusListener=connectStatusListener;
        atOperator.setConnectStatusListener(connectStatusListener);
        atOperator.setDataListener(mDataListener);
        if (TextUtils.isEmpty(patchMac)) {
            Logger.w("patchMac is null");
            throw new NullPointerException("patchMac is null,please set first!!!");
        }
        Logger.w("connect mac is : ", patchMac);
        atOperator.setPatchType(patchType);
        atOperator.setPatchMac(patchMac);
        if (atOperator.isPortConnected()) {
            atOperator.connectDevice();
        } else {
            atOperator.connectPrepare();
        }
    }

    /**
     * 体温贴关机
     */
    public void closeCarePatch() {
        calibrateTemp("ff");
    }

    /**
     * 校准温度
     */
    public void calibrateTemp(String tHex) {
        atOperator.sendDataPrepare(tHex);
    }

    @Override
    public void disConnect() {
        atOperator.disConnect();
    }

    @Override
    public boolean isConnected() {
        return null == atOperator ? false : atOperator.isConnected();
    }

    @Override
    public void cancelConnect() {
        atOperator.disConnect();
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

    private Timer mConnectStatusTimer;
    /**
     * 上次收到数据的时间
     */
    private long mLastReceiveDataTime;

    private Handler mainHandler = new Handler(Looper.getMainLooper());


    /**
     * 检查连接的状态
     */
    private void checkConnectStatus() {
        mConnectStatusTimer = new Timer();
        mConnectStatusTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (mConnectStatusTimer == null) return;
                Logger.w("AT连接状态定时器");
                if ((mLastReceiveDataTime != 0 && System.currentTimeMillis() - mLastReceiveDataTime >= disconnectTimeout)) {
                    //没收到数据就回调断开
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            clear();
                            connectStatusListener.onDisconnect(false);
                        }
                    });
                }
            }
        }, 0, 5000);
    }

    private void clear() {
        if (mConnectStatusTimer != null) {
            mConnectStatusTimer.cancel();
            mConnectStatusTimer=null;
        }
        mLastReceiveDataTime = 0;
    }

}
