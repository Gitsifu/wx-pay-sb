package com.example.wepaysb.utils.weixin.config;

/**
 * @Description:
 * @Date: 2018/4/8
 * @Author: wcf
 */
public class WxPayConfig {

    /**
     * 小程序appid
     */
    public static final String appid = "";

    /**
     * 微信支付的商户id
     */
    public static final String mch_id = "";

    /**
     * 微信支付的商户密钥
     */
    public static final String key = "";

    /**
     * 支付成功后的服务器回调url,即微信服务器给服务器发送的支付结果通知
     */
    public static final String notify_url = "https://??/weixin/wxNotify";

    /**
     * 签名方式
     */
    public static final String SIGNTYPE = "MD5";

    /**
     * 交易类型,小程序JSAPI--小程序支付统一下单接口trade_type的传参，
     * 更多参考：
     * https://pay.weixin.qq.com/wiki/doc/api/wxa/wxa_api.php?chapter=9_1
     * https://pay.weixin.qq.com/wiki/doc/api/wxa/wxa_api.php?chapter=4_2
     */
    public static final String TRADETYPE = "JSAPI";

    /**
     * 微信统一下单接口地址
     * 更多参考：
     * https://pay.weixin.qq.com/wiki/doc/api/wxa/wxa_api.php?chapter=9_1
     */
    public static final String pay_url = "https://api.mch.weixin.qq.com/pay/unifiedorder";
}
