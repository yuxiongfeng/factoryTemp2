package com.proton.temp.connector.at.instruction;

/**
 * @Description: 指令类型
 * @Author: yxf
 * @CreateDate: 2020/5/7 11:17
 * @UpdateUser: yxf
 * @UpdateDate: 2020/5/7 11:17
 */
public enum InstructionType {
    /**
     * 准备工作
     */
    PREPARE,
    /**
     * 选择主从模式（设置成主模式）
     */
    ROLE,
    /**
     * 选择手动或自动连接模式（需要设置成手动模式）
     */
    IMME,
    /**
     * 连接准备
     */
    /**
     * 连接设备
     */
    COON,
    /**
     * 读取serial和version
     */
    READ,
    /**
     * 订阅温度
     */
    NOTIFY,
    /**
     * 写入前准备
     */
    WRITE_DATA_PREPARE,
    /**
     * 断开连接
     */
    DISCONNECT,
    /**
     * 默认值
     */
    NONE
}
