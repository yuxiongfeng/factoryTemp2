package com.proton.carepatchtemp.factory.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;

import com.proton.carepatchtemp.net.bean.MessageEvent;
import com.proton.carepatchtemp.utils.EventBusManager;
import com.wms.logger.Logger;

import static com.proton.temp.connector.at.utils.AtOperator.ACTION_USB_PERMISSION;

/**
 * @Description:
 * @Author: yxf
 * @CreateDate: 2020/5/12 18:11
 * @UpdateUser: yxf
 * @UpdateDate: 2020/5/12 18:11
 */
public class UsbAttachReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (ACTION_USB_PERMISSION.equals(action)) {
            // 获取权限结果的广播，打开串口的时候必须要有此权限不然会报错
            synchronized (this) {
                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (device != null) {
                    //call method to set up device communication
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        Logger.w("USBReceiver", "获取权限成功：" + device.getDeviceName());
                        EventBusManager.getInstance().post(new MessageEvent(MessageEvent.EventType.USB_PERMISSION, true));
                    } else {
                        Logger.w("USBReceiver", "获取权限失败：" + device.getDeviceName());
                        EventBusManager.getInstance().post(new MessageEvent(MessageEvent.EventType.USB_PERMISSION, false));
                    }
                }
            }
        } else if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
            // 有新的设备插入了，在这里一般会判断这个设备是不是我们想要的，是的话就去请求权限
            Logger.w("设备插入。。。");
            EventBusManager.getInstance().post(new MessageEvent(MessageEvent.EventType.USB_ATTACH));
        } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
            // 有设备拔出了
            Logger.w("设备拔除。。。");
            EventBusManager.getInstance().post(new MessageEvent(MessageEvent.EventType.USB_DETACHED));
        }
    }

}
