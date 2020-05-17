package com.proton.carepatchtemp.viewmodel.measure;

import android.app.Activity;
import android.content.Context;
import android.databinding.Observable;
import android.databinding.ObservableBoolean;
import android.databinding.ObservableField;
import android.databinding.ObservableFloat;
import android.databinding.ObservableInt;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.text.TextUtils;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.proton.carepatchtemp.bean.MeasureBean;
import com.proton.carepatchtemp.bean.ReportBean;
import com.proton.carepatchtemp.component.App;
import com.proton.carepatchtemp.fragment.measure.TipFragment;
import com.proton.carepatchtemp.net.bean.MessageEvent;
import com.proton.carepatchtemp.net.callback.NetCallBack;
import com.proton.carepatchtemp.net.callback.ResultPair;
import com.proton.carepatchtemp.net.center.DeviceCenter;
import com.proton.carepatchtemp.net.center.MeasureReportCenter;
import com.proton.carepatchtemp.utils.EventBusManager;
import com.proton.carepatchtemp.utils.MQTTShareManager;
import com.proton.carepatchtemp.utils.Settings;
import com.proton.carepatchtemp.utils.Utils;
import com.proton.carepatchtemp.viewmodel.BaseViewModel;
import com.proton.temp.connector.TempConnectorManager;
import com.proton.temp.connector.at.AtConnector;
import com.proton.temp.connector.at.interfaces.PortConnectListener;
import com.proton.temp.connector.bean.ConnectionType;
import com.proton.temp.connector.bean.DeviceType;
import com.proton.temp.connector.bean.TempDataBean;
import com.proton.temp.connector.interfaces.ConnectStatusListener;
import com.proton.temp.connector.interfaces.ConnectionTypeListener;
import com.proton.temp.connector.interfaces.Connector;
import com.proton.temp.connector.interfaces.DataListener;
import com.wms.logger.Logger;
import com.wms.utils.CommonUtils;


import java.util.List;
import java.util.Timer;
import java.util.concurrent.TimeUnit;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;

/**
 * Created by wangmengsi on 2018/3/22.
 */

public class MeasureViewModel extends BaseViewModel {

    //--------------------------------------体温校准新增变量start------------------
    /**
     * 测温点1 ：  0 未检查  1：稳定  2：不稳定
     */
    public ObservableInt firstStableState = new ObservableInt(0);
    /**
     * 测温点1稳定温度
     */
    public ObservableFloat firstStableTemp = new ObservableFloat(0);
    /**
     * 测温点1：水槽温度
     */
    public ObservableFloat firstSinkTemp = new ObservableFloat(0);
    /**
     * 第一次测温允许误差
     */
    public ObservableFloat firstAllowableError = new ObservableFloat(0);
    /**
     * 测温点2:   0 未检查  1 稳定  2 不稳定
     */
    public ObservableInt secondStableState = new ObservableInt(0);
    /**
     * 温度点2稳定温度
     */
    public ObservableFloat secondStableTemp = new ObservableFloat(0);
    /**
     * 测温点2 ：水槽温度
     */
    public ObservableFloat secondSinkTemp = new ObservableFloat(0);
    /**
     * 第二次温度允许误差
     */
    public ObservableFloat secondAllowableError = new ObservableFloat(0);

    /**
     * 校准前选择是否进行校准,默认进行校准
     */
    public ObservableBoolean calibrateSelect = new ObservableBoolean(true);
    /**
     * 校准状态： 0 未校准   1 已校准  2 校准错误
     */
    public ObservableInt calibrationStatus = new ObservableInt(0);
    /**
     * 校准值
     */
    public ObservableFloat calibration = new ObservableFloat(0);
    /**
     * 合格状态  0 没有进入合格状态  1 已合格  2 未合格
     */
    public ObservableInt qualificationStatus = new ObservableInt(0);

    private Activity mActivity;

    public ObservableInt usbDeviceId = new ObservableInt(0);
    public ObservableInt usbPortNum = new ObservableInt(0);

    //--------------------------------------体温校准新增变量end------------------


    public ObservableField<MeasureBean> measureInfo = new ObservableField<>();
    /**
     * 当前温度,用于显示给用户,为算法温度
     */
    public ObservableFloat currentTemp = new ObservableFloat(0);

    /**
     * 当前算法温度
     */
    public ObservableFloat algorithmTemp = new ObservableFloat(0);

    /**
     * 原始温度
     */
    public ObservableFloat originalTemp = new ObservableFloat(0);

    /**
     * 最高温
     */
    public ObservableFloat highestTemp = new ObservableFloat(0);
    /**
     * 电量
     */
    public ObservableInt battery = new ObservableInt(-1);

    /**
     * 蓝牙信号强度
     */
    public ObservableInt bleRssi = new ObservableInt(0);
    /**
     * wifi信号强度
     */
    public ObservableInt wifiRssi = new ObservableInt(0);

    /**
     * 是否首次连接失败
     */
    public ObservableBoolean isFistConnectFail = new ObservableBoolean(false);

    /**
     * 是否连接上了设备
     * 0 未连接 1连接中 2已连接 3手动断开连接  4,连接失败
     */
    public ObservableInt connectStatus = new ObservableInt();
    /**
     * 连接方式
     */
    public ObservableField<ConnectionType> connectionType = new ObservableField<>();
    /**
     * 是否贴上了设备
     */
    public ObservableBoolean hasStick = new ObservableBoolean(false);

    /**
     * 体温贴是否断裂
     */
    public ObservableBoolean carePatchEnable = new ObservableBoolean(true);
    /**
     * 温度稳定状态
     */
    public ObservableBoolean tempStabled = new ObservableBoolean(false);
    /**
     * 需要跳转到实时测量
     */
    public ObservableBoolean needGoToMeasure = new ObservableBoolean(false);
    /**
     * 是否需要交替显示温度和文字（实时温度+预热中）
     */
    public ObservableBoolean needShowPreheating = new ObservableBoolean(false);
    /**
     * 是否需要交替显示温度和文字（实时温度+手臂张开，温度偏低）
     */
    public ObservableBoolean needShowTempLow = new ObservableBoolean(false);

    /**
     * 需要显示断开连接对话框
     */
    public ObservableBoolean needShowDisconnectDialog = new ObservableBoolean(false);
    /**
     * 保存报告
     */
    public ObservableField<ReportBean> saveReport = new ObservableField<>();
    /**
     * 固件版本
     */
    public ObservableField<String> hardVersion = new ObservableField<>("");
    /**
     * 序列号
     */
    public ObservableField<String> serialNumber = new ObservableField<>("");
    /**
     * 是否充电
     */
    public ObservableBoolean isCharge = new ObservableBoolean(false);
    /**
     * 设备id
     */
    public ObservableField<String> deviceId = new ObservableField<>("");
    /**
     * 报告id
     */
    public ObservableField<String> reportId = new ObservableField<>("");
    /**
     * 贴的mac地址
     */
    public ObservableField<String> patchMacaddress = new ObservableField<>("");
    /**
     * 测量提示文字
     */
    public ObservableField<String> measureTips = new ObservableField<>();

    /**
     * 当前接收数据的序列号和上次不一致
     */
    private ObservableBoolean isNotSameDevice = new ObservableBoolean(false);

    /**
     * 正在添加报告
     */
    private boolean isAddingReport;
    /**
     * 是否是测量准备页
     */
    private boolean isBeforeMeasure = true;
    private Disposable mResetTempDisposed;

    /**
     * 定时保存体温数据，防止闪退，app杀死造成数据丢失
     */
    private Timer mTimer;

    /**
     * 算法版本号
     */
    public String algorithVersion;
    /**
     * 算法状态
     */
    public int algorithStatus;
    /**
     * 算法姿势
     */
    public int algorithGesture;


    private ConnectStatusListener mConnectorListener = new ConnectStatusListener() {

        @Override
        public void onConnectSuccess() {
            needShowDisconnectDialog.set(false);
            connectStatus.set(2);
            addDeviceToServer();
        }

        @Override
        public void onConnectFaild() {
            //连接断开则重连
            connectStatus.set(1);
        }

        @Override
        public void onDisconnect(boolean isManual) {
            connectStatus.set(isManual ? 3 : 0);
            doDisconnect();
        }

        @Override
        public void receiveReconnectTimes(int retryCount, int leftCount, long totalReconnectTime) {
            Logger.w("重连次数:", retryCount, " 剩余重连次数 :", leftCount, String.format(" ,当前重连总时间为%s", totalReconnectTime));

        }

        /**
         * 测量准备 未连接6秒弹框提示
         */
        @Override
        public void showBeforeMeasureDisconnect() {
            if (isBeforeMeasure) {
                needShowDisconnectDialog.set(true);
            }
        }

        @Override
        public void firstConnectFail() {
            super.firstConnectFail();
            isFistConnectFail.set(true);
        }

        @Override
        public void receiveNotSampleDevice(String oldMac, String newMac) {
            connectStatus.set(1);
        }
    };

    /**
     * 数据接收
     */
    private DataListener mDataListener = new DataListener() {
        @Override
        public void receiveHardVersion(String version) {
            super.receiveHardVersion(version);
            Logger.w("固件版本:" + version);
            hardVersion.set(version);
        }

        @Override
        public void receiveSerial(String serial) {
            super.receiveSerial(serial);
            Logger.w("序列号:" + serial);
            if (!TextUtils.isEmpty(serialNumber.get()) && !serial.equalsIgnoreCase(serialNumber.get())) {
                isNotSameDevice.set(true);
                return;
            }
            serialNumber.set(serial);
        }

        @Override
        public void receiveCurrentTemp(List<TempDataBean> temps) {
            if (CommonUtils.listIsEmpty(temps)) return;
            connectStatus.set(2);
            currentTemp.set(temps.get(temps.size() - 1).getAlgorithmTemp());
            algorithmTemp.set(temps.get(temps.size() - 1).getAlgorithmTemp());
            originalTemp.set(temps.get(temps.size() - 1).getTemp());
            Logger.w("原始温度 : ", originalTemp.get(), " 算法温度 ： ", algorithmTemp.get());

            if (!Utils.isTempInRange(currentTemp.get())) {
                if (!isBeforeMeasure) {
                    measureTips.set("不在测量范围内\n" +
                            "（25~45℃）");
                } else if (isBeforeMeasure) {
                    measureTips.set("不在测量范围内（25~45℃）");
                }
            }
            EventBusManager.getInstance().post(new MessageEvent(MessageEvent.EventType.TEMP_CHANGE));
        }


        @Override
        public void receiveBattery(Integer batteryValue) {
            battery.set(batteryValue);
        }

        @Override
        public void receiveCharge(boolean charge) {
            isCharge.set(charge);
        }


        @Override
        public void judgeCarepatchEnable(boolean isEnable) {
            carePatchEnable.set(isEnable);
            Logger.w("体温贴是否可用 isEnable= " + isEnable);
        }

        @Override
        public void receiveBleAndWifiRssi(Integer bleBssi, Integer wifiBssi) {
            bleRssi.set(bleBssi);
            wifiRssi.set(wifiBssi);
            Logger.w("ble信号强度： ", bleBssi, " wifi信号强度: ", wifiBssi);
        }
    };


    private ConnectionTypeListener mConnectionTypeListener = new ConnectionTypeListener() {
        @Override
        public void receiveConnectType(ConnectionType type) {
            Logger.w("当前连接类型:", type.toString());
            connectionType.set(type);
        }
    };

    public MeasureViewModel() {
        measureInfo.addOnPropertyChangedCallback(new Observable.OnPropertyChangedCallback() {
            @Override
            public void onPropertyChanged(Observable sender, int propertyId) {
                patchMacaddress.set(measureInfo.get().getPatchMac());
                hardVersion.set(measureInfo.get().getHardVersion());
                serialNumber.set(measureInfo.get().getSerialNum());
                Logger.w("连接设备的mac地址:" + measureInfo.get().getMacaddress()
                        + ",贴的mac地址:" + patchMacaddress.get()
                        + ",固件版本:" + hardVersion.get()
                        + ",序列号:" + serialNumber.get());
            }
        });
        connectStatus.addOnPropertyChangedCallback(new Observable.OnPropertyChangedCallback() {
            @Override
            public void onPropertyChanged(Observable sender, int propertyId) {
                if (!isConnected()) {
                    if (!isManualDisconnect()) {
                        mResetTempDisposed = io.reactivex.Observable.just(1)
                                .delay(10, TimeUnit.MINUTES)
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(integer -> {
                                    currentTemp.set(0);
                                    algorithmTemp.set(0);
                                    originalTemp.set(0);
                                    highestTemp.set(0);
                                });
                    }
                    measureTips.set("");
                    stopCacheReportTimer();
                } else {
                    if (mResetTempDisposed != null && !mResetTempDisposed.isDisposed()) {
                        mResetTempDisposed.dispose();
                    }
                }
            }

        });

    }

    /**
     * 连接设备
     */
    public void connectDevice() {
        connectDevice(Integer.MAX_VALUE);
    }

    public void connectDevice(int retryCount) {

        if (!getConnectorManager().isConnected()) {
            if (connectStatus.get() == 1) {
                connectStatus.notifyChange();
            } else {
                connectStatus.set(1);
            }
        }
        getConnectorManager().addConnectionTypeListener(mConnectionTypeListener);
        if (getConnectorManager().getConnectionType() == ConnectionType.BLUETOOTH) {
            getConnectorManager().setActivity(mActivity);
            getConnectorManager().setUsbDeviceId(usbDeviceId.get());
            getConnectorManager().setUsbPortNum(usbPortNum.get());
            getConnectorManager().setConnectionType(ConnectionType.AT);
        }

        getConnectorManager()
                .setReconnectCount(retryCount)
                .setEnableCacheTemp(false)
                .connect(mConnectorListener, mDataListener, true);
    }


    public void cancelConnect() {
        getConnectorManager().cancelConnect();
    }

    public List<TempDataBean> getAllTemps() {
        return getConnectorManager().getAllTemps();
    }

    public void disConnect() {
        doDisconnect();
        if (Utils.getPatchMeasureSize(measureInfo.get().getMacaddress()) <= 1) {
            getConnectorManager().disConnect();
        } else {
            getConnectorManager().removeConnectStatusListener(mConnectorListener);
            getConnectorManager().removeDataListener(mDataListener);
            getConnectorManager().removeConnectionTypeListener(mConnectionTypeListener);
        }
    }

    private void doDisconnect() {
        clear();
    }

    /**
     * 添加设备到服务器
     */
    public void addDeviceToServer() {

        String macaddress = patchMacaddress.get();
        if (TextUtils.isEmpty(macaddress) || !App.get().isLogined()) {
            return;
        }

        Logger.w("添加设备:deviecId = "
                + deviceId.get()
                + ",reportId = " + reportId.get()
                + "sn = " + serialNumber.get()
                + "hardversion = " + hardVersion.get());

        DeviceType type = measureInfo.get().getDevice().getDeviceType();
        DeviceCenter.addDevice(type.toString(), serialNumber.get(), macaddress, hardVersion.get(), new NetCallBack<String>() {

            @Override
            public void onSucceed(String data) {
                deviceId.set(data);
                Logger.w("添加设备成功:id = " + deviceId.get());
                if (!TextUtils.isEmpty(serialNumber.get()) && !TextUtils.isEmpty(hardVersion.get())) {
                    EventBusManager.getInstance().post(new MessageEvent(MessageEvent.EventType.DEVICE_CHANGED));
                }
                addReport();
            }

            @Override
            public void onFailed(ResultPair resultPair) {
                super.onFailed(resultPair);
                Logger.w("添加设备失败:" + resultPair.getData());
            }
        });
    }

    private void editShareProfile() {
        DeviceCenter.editShareProfile(String.valueOf(measureInfo.get().getProfile().getProfileId()), deviceId.get(), true, new NetCallBack<Boolean>() {
            @Override
            public void onSucceed(Boolean data) {
                Logger.w("更新分享设备成功");
            }
        });
    }

    /**
     * 添加报告
     */
    private void addReport() {
        if (!TextUtils.isEmpty(reportId.get())
                || isAddingReport
                || measureInfo.get().getProfile().getProfileId() == -1) {
            return;
        }
        isAddingReport = true;
        MeasureReportCenter.addReport(deviceId.get(), String.valueOf(measureInfo.get().getProfile().getProfileId()), System.currentTimeMillis(), new NetCallBack<String>() {

            @Override
            public void noNet() {
                super.noNet();
                isAddingReport = false;
            }

            @Override
            public void onSucceed(String data) {
                Logger.w("添加报告成功:" + data);
                reportId.set(data);
                isAddingReport = false;
                //更新当前正在测量的设备
                editShareProfile();
            }

            @Override
            public void onFailed(ResultPair resultPair) {
                super.onFailed(resultPair);
                Logger.w("添加报告失败:" + resultPair.getData());
                isAddingReport = false;
            }
        });
    }


    private void stopCacheReportTimer() {
        if (mTimer != null) {
            mTimer.cancel();
            mTimer = null;
        }
    }

    public TempConnectorManager getConnectorManager() {
        return TempConnectorManager.getInstance(measureInfo.get().getDevice());
    }


    public void setActivity(Activity activity) {
        this.mActivity = activity;
    }

    /**
     * 清空状态
     */
    private void clear() {
        if (mResetTempDisposed != null && !mResetTempDisposed.isDisposed()) {
            mResetTempDisposed.dispose();
        }
        connectStatus.set(3);
        currentTemp.set(0);
        algorithmTemp.set(0);
        originalTemp.set(0);
        highestTemp.set(0);
        deviceId.set("");
        reportId.set("");
        saveReport.set(null);
        isAddingReport = false;
        //取消订阅
        if (!getConnectorManager().isMQTTConnect()) {
            MQTTShareManager.getInstance().unsubscribe(getShareTopic());
        }
    }

    /**
     * 设备是否连接
     */
    public boolean isConnected() {
        return connectStatus.get() == 2;
    }

    /**
     * 设备是否连接中
     */
    public boolean isConnecting() {
        return connectStatus.get() == 1;
    }

    /**
     * 设备是否自动断开连接
     */
    public boolean isDisconnect() {
        return connectStatus.get() == 0;
    }

    /**
     * 设备是否手动断开连接
     */
    public boolean isManualDisconnect() {
        return connectStatus.get() == 3;
    }


    /**
     * 是否是p02设备
     */
    public boolean isP02() {
        return measureInfo.get().getDevice().getDeviceType() == DeviceType.P02;
    }

    public ConnectionType getConnectType() {
        return getConnectorManager().getConnectionType();
    }

    /**
     * 是否是共享测量
     */
    public boolean isShare() {
        return false;
    }

    private String getShareTopic() {
        if (TextUtils.isEmpty(patchMacaddress.get())) {
            return "";
        }
        return Utils.getShareTopic(patchMacaddress.get());
    }

    /**
     * 打开温馨提示对话框
     */
    public void openRemindTip() {
        TipFragment tipFragment = new TipFragment();
        tipFragment.show(((Activity) getContext()).getFragmentManager(), "tip");
    }

    public void setIsBeforeMeasure(boolean isBeforeMeasure) {
        this.isBeforeMeasure = isBeforeMeasure;
    }

}
