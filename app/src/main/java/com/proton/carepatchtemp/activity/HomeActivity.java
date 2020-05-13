package com.proton.carepatchtemp.activity;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.widget.DrawerLayout;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.nineoldandroids.view.ViewHelper;
import com.proton.carepatchtemp.R;
import com.proton.carepatchtemp.activity.base.BaseActivity;
import com.proton.carepatchtemp.bean.MeasureBean;
import com.proton.carepatchtemp.bean.MessageBean;
import com.proton.carepatchtemp.component.App;
import com.proton.carepatchtemp.component.NetChangeReceiver;
import com.proton.carepatchtemp.constant.AppConfigs;
import com.proton.carepatchtemp.database.ProfileManager;
import com.proton.carepatchtemp.databinding.ActivityHomeBinding;
import com.proton.carepatchtemp.factory.bean.ListItem;
import com.proton.carepatchtemp.factory.receiver.UsbAttachReceiver;
import com.proton.carepatchtemp.fragment.base.BaseFragment;
import com.proton.carepatchtemp.fragment.devicemanage.DeviceManageFragment;
import com.proton.carepatchtemp.fragment.home.HealthyTipsFragment;
import com.proton.carepatchtemp.fragment.home.SettingFragment;
import com.proton.carepatchtemp.fragment.measure.MeasureContainerFragment;
import com.proton.carepatchtemp.fragment.profile.ProfileFragment;
import com.proton.carepatchtemp.fragment.report.ReportsFragment;
import com.proton.carepatchtemp.net.bean.MessageEvent;
import com.proton.carepatchtemp.net.bean.ProfileBean;
import com.proton.carepatchtemp.net.bean.UpdateFirmwareBean;
import com.proton.carepatchtemp.net.callback.NetCallBack;
import com.proton.carepatchtemp.net.center.DeviceCenter;
import com.proton.carepatchtemp.net.center.MeasureCenter;
import com.proton.carepatchtemp.net.center.MeasureReportCenter;
import com.proton.carepatchtemp.net.center.UserCenter;
import com.proton.carepatchtemp.utils.ActivityManager;
import com.proton.carepatchtemp.utils.IntentUtils;
import com.proton.carepatchtemp.utils.StatusBarUtil;
import com.proton.carepatchtemp.utils.Utils;
import com.proton.carepatchtemp.view.AppNotificationDialog;
import com.proton.temp.connector.TempConnectorManager;
import com.proton.temp.connector.at.CustomProber;
import com.wms.logger.Logger;
import com.wms.utils.CommonUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import cn.pedant.SweetAlert.SweetAlertDialog;
import cn.pedant.SweetAlert.Type;

import static com.proton.temp.connector.at.utils.AtOperator.ACTION_USB_PERMISSION;

public class HomeActivity extends BaseActivity<ActivityHomeBinding> {
    private BaseFragment mCurrentFragment;
    private MeasureContainerFragment mMeasureFragment;
    private ReportsFragment mReportFragment;
    private HealthyTipsFragment mHelthyTipsFragment;
    private ProfileFragment mProfileFragment;
    private DeviceManageFragment mDeviceManagerFragment;
    private SettingFragment mSettingFragment;
    private BroadcastReceiver mNetReceiver = new NetChangeReceiver();
    private BaseFragment mOpenFragment;
    private List<Long> mShowingDialog = new ArrayList<>();

    private UsbAttachReceiver usbAttachReceiver = new UsbAttachReceiver();
    //    private Handler mHandler = new Handler(Looper.getMainLooper());
    /**
     * usb设备加入时候的定时器
     */
    private Timer usbTimer;
    /**
     * 定时器是否已开启
     */
    private boolean isTimerStarted;

    /**
     * 一次性插入设备的最大耗时，单个插入大概需要250ms,总共大概需要5s
     */
    private long usbInsetMaxTime = 250 * 20;

    /**
     * 串口信息
     */
    private ArrayList<ListItem> listItems = new ArrayList<>();

    /**
     * 当前设备下标
     */
    private int currentDeviceIndex;

    @Override
    protected int inflateContentView() {
        return R.layout.activity_home;
    }

    @Override
    protected void init() {
        super.init();
        initDrawerLayout();
        checkNotificationPermission();
        MeasureReportCenter.getAliyunToken();
        MeasureCenter.getAlgorithmConfig(null);
        showMeasureFragment();

        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        mContext.registerReceiver(usbAttachReceiver, filter);

        /**
         * 获取串口信息
         */
        fetchUsbInfo(true);
    }

    private void getFireware() {
        if (!App.get().isLogined()) return;
        DeviceCenter.getUpdatePackage(new NetCallBack<List<UpdateFirmwareBean>>() {
            @Override
            public void onSucceed(List<UpdateFirmwareBean> data) {
                Logger.w("获取固件更新成功:" + data.size());
            }
        });
    }

    @Override
    protected void initData() {
        super.initData();
        UserCenter.getNewestMsg(new NetCallBack<List<MessageBean>>() {
            @Override
            public void onSucceed(List<MessageBean> datas) {
                if (!CommonUtils.listIsEmpty(datas)) {
                    for (MessageBean data : datas) {
                        //正在显示的消息不在显示
                        boolean isExist = false;
                        for (Long messageId : mShowingDialog) {
                            if (data.getMessageId() == messageId) {
                                isExist = true;
                            }
                        }

                        if (isExist) continue;
                        AppNotificationDialog dialog = new AppNotificationDialog(ActivityManager.currentActivity())
                                .setTitle(data.getTitle())
                                .setConfirmText(data.getButtonContent())
                                .setContent(data.getContent())
                                .setCloseable(data.isClosable())
                                .setCloseListener(v -> markMsgAsRead(data))
                                .setDescription(data.getDescription());
                        dialog.setCancelable(false);
                        dialog.setCanceledOnTouchOutside(false);
                        dialog.setConfirmListener(dialog1 -> {
                            mShowingDialog.remove(data.getMessageId());
                            markMsgAsRead(data);
                            if (data.getJumpStatus() == 1) {
                                IntentUtils.goToWeb(mContext, data.getUrl());
                            }
                        });
                        mShowingDialog.add(data.getMessageId());
                        dialog.show();
                    }
                }
            }
        });
    }

    private void markMsgAsRead(MessageBean data) {
        UserCenter.markMsgAsRead(data.getMessageId(), data.getFlag(), new NetCallBack<String>() {
            @Override
            public void onSucceed(String data) {
                Logger.w("标记成功");
            }
        });
    }

    @Override
    protected void initView() {
        super.initView();
        binding.idMenuLeft.idMenuMeasure.setSelect();
        binding.idMenuLeft.idMenuMeasure.setOnClickListener(v -> showMeasureFragment());
        //测量报告s
        binding.idMenuLeft.idMenuReport.setOnClickListener(v -> showReportFragment());
        //健康贴士
        binding.idMenuLeft.idMenuTips.setOnClickListener(v -> showHealthyTipsFragment());
        //设备管理
        binding.idMenuLeft.idMenuDevicemanageCenter.setOnClickListener(v -> showDeviceManagerFragment());
        //我的档案
        binding.idMenuLeft.idMenuMyprofile.setOnClickListener(view -> showProfileFragment());
        //设置中心
        binding.idMenuLeft.idMenuManagerCenter.setOnClickListener(v -> showSettingFragment());
        binding.idMenuLeft.idSetNetwork.setOnClickListener(v -> IntentUtils.goToDockerSetNetwork(mContext));
        //头像默认显示本地档案中第一个
        showTheFirstProfileAvator();
        setMenuProfile();
    }

    /**
     * 显示档案头像
     */
    private void showTheFirstProfileAvator() {
        ProfileBean profile = ProfileManager.getDefaultProfile();
        if (profile != null) {
            String avator = profile.getAvatar();
            if (!TextUtils.isEmpty(avator)) {
                binding.idMenuLeft.idProfileImg.setImageURI(avator);
            }
        }
    }

    private void clearSelect() {
        binding.idMenuLeft.idMenuMeasure.setUnSelect();
        binding.idMenuLeft.idMenuReport.setUnSelect();
        binding.idMenuLeft.idMenuTips.setUnSelect();
        binding.idMenuLeft.idMenuManagerCenter.setUnSelect();
        binding.idMenuLeft.idMenuMyprofile.setUnSelect();
        binding.idMenuLeft.idMenuDevicemanageCenter.setUnSelect();
        binding.idDrawer.closeDrawers();
    }

    private void initDrawerLayout() {
        binding.idDrawer.setScrimColor(0x00ffffff);
        binding.idDrawer.addDrawerListener(new DrawerLayout.SimpleDrawerListener() {
            @Override
            public void onDrawerSlide(View drawerView, float slideOffset) {
                View mContent = binding.idDrawer.getChildAt(0);
                float Scale = 0.5f * (1.0f + slideOffset);
                if (drawerView.getTag().equals("LEFT")) {
                    drawerView.setAlpha(Scale);
                    ViewHelper.setTranslationX(mContent, drawerView.getMeasuredWidth() * slideOffset);
                    mContent.invalidate();
                } else {
                    drawerView.setAlpha(Scale);
                    ViewHelper.setTranslationX(mContent, -drawerView.getMeasuredWidth() * slideOffset);
                    mContent.invalidate();
                }
            }

            @Override
            public void onDrawerClosed(View drawerView) {
                super.onDrawerClosed(drawerView);
                showFragment(mOpenFragment);
            }
        });
    }

    /**
     * 显示测量
     */
    public void showMeasureFragment() {
        clearSelect();
        binding.idMenuLeft.idMenuMeasure.setSelect();
        if (mMeasureFragment == null) {
            mMeasureFragment = MeasureContainerFragment.newInstance(false);
            mMeasureFragment.setOnMeasureContainerListener(new MeasureContainerFragment.OnMeasureContainerListener() {
                @Override
                public void onAddMeasureItem(MeasureBean measureInfo) {
                }

                @Override
                public void onToggleDrawer() {
                    toogleDrawer();
                }

                @Override
                public void onShowMeasuring() {
                    showMeasureFragment();
                }
            });
        }
        mOpenFragment = null;
        showFragment(mMeasureFragment);
    }

    /**
     * 显示报告列表
     */
    private void showReportFragment() {
        clearSelect();
        binding.idMenuLeft.idMenuReport.setSelect();
        if (mReportFragment == null) {
            mReportFragment = ReportsFragment.newInstance();
            mReportFragment.setOnReportsContainerListener(this::toogleDrawer);
        }

        showOrLazyLoadFragment(mReportFragment);
    }

    /**
     * 显示健康贴士
     */
    private void showHealthyTipsFragment() {
        clearSelect();
        binding.idMenuLeft.idMenuTips.setSelect();
        if (mHelthyTipsFragment == null) {
            mHelthyTipsFragment = HealthyTipsFragment.newInstance();
        }

        showOrLazyLoadFragment(mHelthyTipsFragment);
    }

    /**
     * 显示档案
     */
    private void showProfileFragment() {
        clearSelect();
        binding.idMenuLeft.idMenuMyprofile.setSelect();
        if (mProfileFragment == null) {
            mProfileFragment = ProfileFragment.newInstance();
            mProfileFragment.setOnReportOperateListener(this::toogleDrawer);
        }
        showOrLazyLoadFragment(mProfileFragment);
    }

    /**
     * 显示设备管理
     */
    private void showDeviceManagerFragment() {
        clearSelect();
        binding.idMenuLeft.idMenuDevicemanageCenter.setSelect();
        if (mDeviceManagerFragment == null) {
            mDeviceManagerFragment = DeviceManageFragment.newInstance();
            mDeviceManagerFragment.setOnDeviceManageListener(this::toogleDrawer);
        }
        showOrLazyLoadFragment(mDeviceManagerFragment);
    }

    /**
     * 显示设置中心
     */
    private void showSettingFragment() {
        clearSelect();
        binding.idMenuLeft.idMenuManagerCenter.setSelect();
        if (mSettingFragment == null) {
            mSettingFragment = SettingFragment.newInstance();
        }
        showOrLazyLoadFragment(mSettingFragment);
    }

    public void toogleDrawer() {
        if (binding.idDrawer.isDrawerOpen(Gravity.LEFT)) {
            binding.idDrawer.closeDrawer(Gravity.LEFT);
        } else {
            binding.idDrawer.openDrawer(Gravity.LEFT);
        }
    }

    @Override
    protected void setStatusBar() {
        StatusBarUtil.setColorForDrawerLayout(this, binding.idDrawer, ContextCompat.getColor(mContext, R.color.colorPrimary));
    }

    private void showOrLazyLoadFragment(BaseFragment fragment) {
        if (fragment.isHidden()) {
            mOpenFragment = null;
            showFragment(fragment);
        } else {
            mOpenFragment = fragment;
        }
    }

    /**
     * 显示fragment
     */
    private void showFragment(BaseFragment fragment) {
        if (fragment == null || fragment == mCurrentFragment) return;
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        if (mCurrentFragment != null) {
            if (fragment.isAdded()) {
                //fragment已经添加了
                transaction.hide(mCurrentFragment).show(fragment);
            } else {
                transaction.hide(mCurrentFragment).add(R.id.id_main_container, fragment);
            }
        } else {
            if (fragment.isAdded()) {
                //fragment已经添加了
                transaction.show(fragment);
            } else {
                transaction.add(R.id.id_main_container, fragment);
            }
        }
        mCurrentFragment = fragment;
        transaction.commitAllowingStateLoss();
    }

    @Override
    protected boolean isRegistEventBus() {
        return true;
    }

    @Override
    public void onMessageEvent(MessageEvent event) {
        super.onMessageEvent(event);
        MessageEvent.EventType eventType = event.getEventType();
        if (eventType == MessageEvent.EventType.PROFILE_CHANGE || eventType == MessageEvent.EventType.LOGIN) {
            setMenuProfile();
        } else if (eventType == MessageEvent.EventType.HOME_GET_MSG) {
            initData();
        } else if (eventType == MessageEvent.EventType.USB_ATTACH) {
            fetchUsbInfo(true);
        } else if (eventType == MessageEvent.EventType.USB_DETACHED) {
            fetchUsbInfo(false);
        } else if (eventType == MessageEvent.EventType.USB_PERMISSION) {
            boolean granted = (boolean) event.getObject();
            if (granted) {
                currentDeviceIndex++;
            }
            if (currentDeviceIndex < fetchUsbDeviceList().size()) {
                doUsbPermissionRequest();
            } else {
                Logger.w("所有设备usb授权完成");
            }
        }
    }

    /**
     * 开启定时器，当有usb设备加入的时候
     */
    private void startUsbTimer() {
        if (usbTimer == null) {
            usbTimer = new Timer();
            usbTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    dismissDialog();
                    doUsbPermissionRequest();
                    stopUsbTimer();
                }
            }, usbInsetMaxTime);
        }
    }

    /**
     * 停止定时器
     */
    private void stopUsbTimer() {
        if (usbTimer != null) {
            usbTimer.cancel();
            usbTimer = null;
        }
    }

    /**
     * 获取串口信息
     *
     * @param isAttach true：设备加入  false：设备拔掉
     */
    private void fetchUsbInfo(boolean isAttach) {
        UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        UsbSerialProber usaDefaultProbe = UsbSerialProber.getDefaultProber(mContext);
        UsbSerialProber usaCustomProbe = CustomProber.getCustomProber();
        listItems.clear();
        for (UsbDevice device : usbManager.getDeviceList().values()) {
            UsbSerialDriver driver = usaDefaultProbe.probeDevice(device);
            if (driver == null) {
                driver = usaCustomProbe.probeDevice(device);
            }
            if (driver != null) {
                for (int port = 0; port < driver.getPorts().size(); port++)
                    listItems.add(new ListItem(device, port, driver));
            } else {
                listItems.add(new ListItem(device, 0, null));
            }
        }
        Logger.w("usb device size is : ", null == listItems ? 0 : listItems.size());
        if (isAttach) {
            showDialog("正在初始化串口信息。。。");
            startUsbTimer();
        }
    }

    /**
     * usb授权
     */
    private void doUsbPermissionRequest() {
        List<ListItem> deviceList = fetchUsbDeviceList();
        if (deviceList.size() == 0) {
            Logger.w("未找到串口信息");
            return;
        }
        UsbManager usbManager = (UsbManager) this.getSystemService(Context.USB_SERVICE);
        if (currentDeviceIndex >= deviceList.size()) {
            Logger.w("未找到该设备信息。。。");
            return;
        }
        UsbDevice device = deviceList.get(currentDeviceIndex).device;
        if (device == null) {
            Logger.w("connection failed: device not found");
            return;
        }
        UsbSerialDriver driver = UsbSerialProber.getDefaultProber(this).probeDevice(device);
        if (driver == null) {
            driver = CustomProber.getCustomProber().probeDevice(device);
        }
        if (driver == null) {
            Logger.w("connection failed: no driver for device");
            return;
        }
        if (usbManager.hasPermission(driver.getDevice())) {
            Logger.w("已有usb权限");
        } else {
            Logger.w("没有usb权限");
            PendingIntent usbPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);
            usbManager.requestPermission(driver.getDevice(), usbPermissionIntent);
            return;
        }
    }

    /**
     * 获取串口列表,供给外部调用
     *
     * @return
     */
    public List<ListItem> fetchUsbDeviceList() {
        Logger.w("usb device size is : ", null == listItems ? 0 : listItems.size());
        return listItems;
    }

    /**
     * 设置侧边栏头像
     */
    private void setMenuProfile() {
        ProfileBean profile = ProfileManager.getDefaultProfile();
        if (profile != null) {
            binding.idMenuLeft.idProfileImg.setImageURI(profile.getAvatar());
        } else {
            binding.idMenuLeft.idProfileImg.setImageURI(AppConfigs.DEFAULT_AVATOR_URL);
        }
    }

    @Override
    protected void onResume() {
        registerReceiver(mNetReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
        checkGPS();
        super.onResume();
    }

    private void checkGPS() {
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        // 判断GPS模块是否开启，如果没有则开启
        if (locationManager != null
                && !locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)
                && !App.get().hasShowGpsWarm()) {
            SweetAlertDialog sweetAlertDialog = new SweetAlertDialog(mContext, Type.NORMAL_TYPE);
            sweetAlertDialog.setCancelable(false);
            sweetAlertDialog.showCancelButton(true)
                    .setTitleText(getString(R.string.string_warm_tips))
                    .setCancelText(getString(R.string.string_remind_next_time))
                    .setConfirmText(getString(R.string.string_open_gps))
                    .setContentText(getString(R.string.string_open_gps_tips))
                    .setConfirmClickListener(sweetAlertDialog1 -> {
                        // 转到手机设置界面，用户设置GPS
                        Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                        startActivityForResult(intent, 0); // 设置完成后返回到原来的界面
                        sweetAlertDialog.dismiss();
                    })
                    .setCancelClickListener(sweetAlertDialog1 -> {
                        sweetAlertDialog.dismiss();
                        App.get().setHasShowGpsWarm(true);
                    })
                    .show();
        }
    }

    /**
     * 通知权限是否打开
     */
    private void checkNotificationPermission() {
        NotificationManagerCompat manager = NotificationManagerCompat.from(mContext);
        boolean isOpened = manager.areNotificationsEnabled();
        if (!isOpened) {
            // 根据isOpened结果，判断是否需要提醒用户跳转AppInfo页面，去打开App通知权限
            SweetAlertDialog sweetAlertDialog = new SweetAlertDialog(mContext, Type.NORMAL_TYPE);
            sweetAlertDialog.showCancelButton(true)
                    .setTitleText(getString(R.string.string_warm_tips))
                    .setCancelText(getString(R.string.string_refuse))
                    .setConfirmText(getString(R.string.string_go_to_open))
                    .setContentText(getString(R.string.string_check_you_dont_open_notification))
                    .setConfirmClickListener(sweetAlertDialog1 -> {
                        Intent intent = new Intent();
                        intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        Uri uri = Uri.fromParts("package", getPackageName(), null);
                        intent.setData(uri);
                        startActivity(intent);
                        sweetAlertDialog.dismiss();
                    })
                    .setCancelClickListener(sweetAlertDialog1 -> sweetAlertDialog.dismiss())
                    .show();
        }
    }

    @SuppressLint("MissingSuperCall")
    @Override
    protected void onSaveInstanceState(Bundle outState) {
    }

    @Override
    protected void onDestroy() {
        Utils.clearAllMeasureViewModel();
        TempConnectorManager.close();
        unregisterReceiver(mNetReceiver);
        unregisterReceiver(usbAttachReceiver);
        stopUsbTimer();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        moveTaskToBack(true);
    }

    public void closeAllCards() {
        if (mMeasureFragment != null) {
        }
    }
}
