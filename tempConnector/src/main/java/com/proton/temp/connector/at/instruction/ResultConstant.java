package com.proton.temp.connector.at.instruction;

/**
 * @Description:
 * @Author: yxf
 * @CreateDate: 2020/5/7 10:11
 * @UpdateUser: yxf
 * @UpdateDate: 2020/5/7 10:11
 */
public class ResultConstant {

    public static final String AT = "OK";
    /**
     * 未连接-》连接时，可能会立马返回OK+LOST
     * 已连接下，输入AT，会返回OK+LOST
     */
    public static final String AT_LOST = "OK+LOST";

    /**
     * 获取是否是主模式，手动模式
     */
    public static final String SET_ROLE_IMME_1 = "OK+Set:1";
    public static final String GET_ROLE_IMME_1 = "OK+Get:1";

    public static final String SET_ROLE_IMME_0 = "OK+Set:0";
    public static final String GET_ROLE_IMME_0 = "OK+Get:0";

    public static final String DISC_START = "OK+DISCS";
    public static final String DISC_END = "OK+DISCE";

    public static final String COON_START = "OK+CONNA";
    public static final String COON_END = "OK+CONN";

    public static final String COON_FAIL = "OK+CONNF";
    public static final String DIS_CONN = "OK+LOST";

    public static final String NOTIFY_SUCCESS = "OK+DATA-OK";
    public static final String NOTIFY_OFF = "OK+DATA-OK";

    public static final String WRITE_DATA_PREPARE = "OK+SEND-OK";

}
