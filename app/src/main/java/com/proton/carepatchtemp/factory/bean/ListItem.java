package com.proton.carepatchtemp.factory.bean;

import android.hardware.usb.UsbDevice;

import com.hoho.android.usbserial.driver.UsbSerialDriver;

/**
 * @Description:
 * @Author: yxf
 * @CreateDate: 2020/5/12 14:23
 * @UpdateUser: yxf
 * @UpdateDate: 2020/5/12 14:23
 */
public class ListItem {
    public UsbDevice device;
    public int port;
    public UsbSerialDriver driver;

    /**
     * 串口是否空闲，true:空闲可用，false：表示已经被连接
     */
    private boolean isIdle;

    public ListItem(UsbDevice device, int port, UsbSerialDriver driver) {
        this.device = device;
        this.port = port;
        this.driver = driver;
    }

}
