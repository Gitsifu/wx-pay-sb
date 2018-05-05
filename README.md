# 微信支付（Spring Boot）

支付流程步骤：

1）首先调用wx.login方法获取code，通过code获取openid；

2）java后台调用统一下单支付接口（这里会进行第一次签名），用来获取prepay_id；

3）java后台再次调用签名（这里会进行第二次签名），并返回支付需要用使用的参数；

4）小程序前端wx.requestPayment方法发起微信支付；

5）java后台接收来自微信服务器的通知并处理结果。

详细步骤可参考：https://pay.weixin.qq.com/wiki/doc/api/wxa/wxa_api.php?chapter=7_4&index=3

## 一、获取openid，

小程序端代码
```
wx.login({    
  success: function (res) {    
    var service_url = 'https://???/???/weixin/api/login?code=' + res.code;//需要将服务器域名添加到小程序的request合法域名中，而且必须是https开头    
    wx.request({    
      url: l,    
      data: {},    
      method: 'GET',    
      success: function (res) {    
        console.log(res);    
        if (res.data != null && res.data != undefined && res.data != '') {    
          wx.setStorageSync("openid", res.data.openid);//将获取的openid存到缓存中    
        }    
      }    
    });    
  }    
}); 
```
java后端代码
```$xslt
/**  
  * 小程序后台登录，向微信平台发送获取access_token请求，并返回openId  
  * @param code  
  * @return 用户凭证  
  * @throws WeixinException  
  * @throws IOException   
  * @throws JsonMappingException   
  * @throws JsonParseException   
  */    
 @RequestMapping("login")    
 @ResponseBody    
 public Map<String, Object> login(String code, HttpServletRequest request) throws WeixinException, JsonParseException, JsonMappingException, IOException {    
     if (code == null || code.equals("")) {    
         throw new WeixinException("invalid null, code is null.");    
     }    
         
     Map<String, Object> ret = new HashMap<String, Object>();    
     //拼接参数    
     String param = "?grant_type=" + grant_type + "&appid=" + appid + "&secret=" + secret + "&js_code=" + code;    
         
     System.out.println("https://api.weixin.qq.com/sns/jscode2session" + param);    
         
     //创建请求对象    
     HttpsClient http = new HttpsClient();    
     //调用获取access_token接口    
     Response res = http.get("https://api.weixin.qq.com/sns/jscode2session" + param);    
     //根据请求结果判定，是否验证成功    
     JSONObject jsonObj = res.asJSONObject();    
     if (jsonObj != null) {    
         Object errcode = jsonObj.get("errcode");    
         if (errcode != null) {    
             //返回异常信息    
             throw new WeixinException(getCause(Integer.parseInt(errcode.toString())));    
         }    
             
         ObjectMapper mapper = new ObjectMapper();    
         OAuthJsToken oauthJsToken = mapper.readValue(jsonObj.toJSONString(),OAuthJsToken.class);    
         ret.put("openid", oauthJsToken.getOpenid());    
     }    
     return ret;    
 } 
```

## 二、小程序调用java后端接口，生成最终签名和相关参数小程序端代码：
```$xslt
var that = this;  
      wx.request({  
        url: service_url + 'wxPay',  
        data: { },  
        method: 'GET',  
        success: function (res) {  
          console.log(res);  
           that.doWxPay(res.data);  
        }  
      });  
```

java端代码：
```
/** 
     * @Description: 发起微信支付 
     * @param request 
     */  
    public Json wxPay(Integer openid, HttpServletRequest request){  
        try{  
            //生成的随机字符串  
            String nonce_str = StringUtils.getRandomStringByLength(32);  
            //商品名称  
            String body = "测试商品名称";  
            //获取客户端的ip地址  
            String spbill_create_ip = IpUtil.getIpAddr(request);  
              
            //组装参数，用户生成统一下单接口的签名  
            Map<String, String> packageParams = new HashMap<String, String>();  
            packageParams.put("appid", WxPayConfig.appid);  
            packageParams.put("mch_id", WxPayConfig.mch_id);  
            packageParams.put("nonce_str", nonce_str);  
            packageParams.put("body", body);  
            packageParams.put("out_trade_no", "123456789");//商户订单号  
            packageParams.put("total_fee", "1");//支付金额，这边需要转成字符串类型，否则后面的签名会失败  
            packageParams.put("spbill_create_ip", spbill_create_ip);  
            packageParams.put("notify_url", WxPayConfig.notify_url);//支付成功后的回调地址  
            packageParams.put("trade_type", WxPayConfig.TRADETYPE);//支付方式  
            packageParams.put("openid", openid);  
                 
                String prestr = PayUtil.createLinkString(packageParams); // 把数组所有元素，按照“参数=参数值”的模式用“&”字符拼接成字符串   
              
                //MD5运算生成签名，这里是第一次签名，用于调用统一下单接口  
                String mysign = PayUtil.sign(prestr, WxPayConfig.key, "utf-8").toUpperCase();  
              
            //拼接统一下单接口使用的xml数据，要将上一步生成的签名一起拼接进去  
            String xml = "<xml>" + "<appid>" + WxPayConfig.appid + "</appid>"   
                    + "<body><![CDATA[" + body + "]]></body>"   
                    + "<mch_id>" + WxPayConfig.mch_id + "</mch_id>"   
                    + "<nonce_str>" + nonce_str + "</nonce_str>"   
                    + "<notify_url>" + WxPayConfig.notify_url + "</notify_url>"   
                    + "<openid>" + order.getOpenId() + "</openid>"   
                    + "<out_trade_no>" + order.getOrderNo() + "</out_trade_no>"   
                    + "<spbill_create_ip>" + spbill_create_ip + "</spbill_create_ip>"   
                    + "<total_fee>" + order.getPayMoney() + "</total_fee>"  
                    + "<trade_type>" + WxPayConfig.TRADETYPE + "</trade_type>"   
                    + "<sign>" + mysign + "</sign>"  
                    + "</xml>";  
              
            System.out.println("调试模式_统一下单接口 请求XML数据：" + xml);  
  
            //调用统一下单接口，并接受返回的结果  
            String result = PayUtil.httpRequest(WxPayConfig.pay_url, "POST", xml);  
              
            System.out.println("调试模式_统一下单接口 返回XML数据：" + result);  
              
            // 将解析结果存储在HashMap中     
            Map map = PayUtil.doXMLParse(result);  
              
            String return_code = (String) map.get("return_code");//返回状态码  
              
            Map<String, Object> response = new HashMap<String, Object>();//返回给小程序端需要的参数  
            if(return_code=="SUCCESS"||return_code.equals(return_code)){     
                String prepay_id = (String) map.get("prepay_id");//返回的预付单信息     
                response.put("nonceStr", nonce_str);  
                response.put("package", "prepay_id=" + prepay_id);  
                Long timeStamp = System.currentTimeMillis() / 1000;     
                response.put("timeStamp", timeStamp + "");//这边要将返回的时间戳转化成字符串，不然小程序端调用wx.requestPayment方法会报签名错误  
                //拼接签名需要的参数  
                String stringSignTemp = "appId=" + WxPayConfig.appid + "&nonceStr=" + nonce_str + "&package=prepay_id=" + prepay_id+ "&signType=MD5&timeStamp=" + timeStamp;     
                //再次签名，这个签名用于小程序端调用wx.requesetPayment方法  
                String paySign = PayUtil.sign(stringSignTemp, WxPayConfig.key, "utf-8").toUpperCase();  
                  
                response.put("paySign", paySign);  
            }  
              
            response.put("appid", WxPayConfig.appid);  
              
            return response;  
        }catch(Exception e){  
            e.printStackTrace();  
        }  
        return null;  
    }  
    /** 
     * StringUtils工具类方法 
     * 获取一定长度的随机字符串，范围0-9，a-z 
     * @param length：指定字符串长度 
     * @return 一定长度的随机字符串 
     */  
    public static String getRandomStringByLength(int length) {  
        String base = "abcdefghijklmnopqrstuvwxyz0123456789";  
        Random random = new Random();  
        StringBuffer sb = new StringBuffer();  
        for (int i = 0; i < length; i++) {  
            int number = random.nextInt(base.length());  
            sb.append(base.charAt(number));  
        }  
        return sb.toString();  
       }  
    /** 
     * IpUtils工具类方法 
     * 获取真实的ip地址 
     * @param request 
     * @return 
     */  
    public static String getIpAddr(HttpServletRequest request) {  
        String ip = request.getHeader("X-Forwarded-For");  
        if(StringUtils.isNotEmpty(ip) && !"unKnown".equalsIgnoreCase(ip)){  
             //多次反向代理后会有多个ip值，第一个ip才是真实ip  
            int index = ip.indexOf(",");  
            if(index != -1){  
                return ip.substring(0,index);  
            }else{  
                return ip;  
            }  
        }  
        ip = request.getHeader("X-Real-IP");  
        if(StringUtils.isNotEmpty(ip) && !"unKnown".equalsIgnoreCase(ip)){  
           return ip;  
        }  
        return request.getRemoteAddr();  
    }
```

WxPayConfig小程序配置文件:
```$xslt
/** 
 * 小程序微信支付的配置文件 
 * @author  
 * 
 */  
public class WxPayConfig {  
    //小程序appid  
    public static final String appid = "";  
    //微信支付的商户id  
    public static final String mch_id = "";  
    //微信支付的商户密钥  
    public static final String key = "";  
    //支付成功后的服务器回调url,即微信服务器给服务器发送的支付结果通知  
    public static final String notify_url = "https://??/??/weixin/api/wxNotify";  
    //签名方式，固定值  
    public static final String SIGNTYPE = "MD5";  
    //交易类型，小程序支付的固定值为JSAPI  
    public static final String TRADETYPE = "JSAPI";  
    //微信统一下单接口地址  
    public static final String pay_url = "https://api.mch.weixin.qq.com/pay/unifiedorder";  
}  
```

PayUtils工具类:
```$xslt
import java.io.BufferedReader;  
import java.io.ByteArrayInputStream;  
import java.io.IOException;  
import java.io.InputStream;  
import java.io.InputStreamReader;  
import java.io.OutputStream;  
import java.io.UnsupportedEncodingException;  
import java.net.HttpURLConnection;  
import java.net.URL;  
import java.security.SignatureException;  
import java.util.ArrayList;  
import java.util.Collections;  
import java.util.HashMap;  
import java.util.Iterator;  
import java.util.List;  
import java.util.Map;  
  
import org.apache.commons.codec.digest.DigestUtils;  
import org.jdom.Document;  
import org.jdom.Element;  
import org.jdom.JDOMException;  
import org.jdom.input.SAXBuilder;  
  
public class PayUtil {  
     /**   
     * 签名字符串   
     * @param text需要签名的字符串   
     * @param key 密钥   
     * @param input_charset编码格式   
     * @return 签名结果   
     */     
    public static String sign(String text, String key, String input_charset) {     
        text = text + "&key=" + key;     
        return DigestUtils.md5Hex(getContentBytes(text, input_charset));     
    }     
    /**   
     * 签名字符串   
     *  @param text需要签名的字符串   
     * @param sign 签名结果   
     * @param key密钥   
     * @param input_charset 编码格式   
     * @return 签名结果   
     */     
    public static boolean verify(String text, String sign, String key, String input_charset) {     
        text = text + key;     
        String mysign = DigestUtils.md5Hex(getContentBytes(text, input_charset));     
        if (mysign.equals(sign)) {     
            return true;     
        } else {     
            return false;     
        }     
    }     
    /**   
     * @param content   
     * @param charset   
     * @return   
     * @throws SignatureException   
     * @throws UnsupportedEncodingException   
     */     
    public static byte[] getContentBytes(String content, String charset) {     
        if (charset == null || "".equals(charset)) {     
            return content.getBytes();     
        }     
        try {     
            return content.getBytes(charset);     
        } catch (UnsupportedEncodingException e) {     
            throw new RuntimeException("MD5签名过程中出现错误,指定的编码集不对,您目前指定的编码集是:" + charset);     
        }     
    }     
      
    private static boolean isValidChar(char ch) {     
        if ((ch >= '0' && ch <= '9') || (ch >= 'A' && ch <= 'Z') || (ch >= 'a' && ch <= 'z'))     
            return true;     
        if ((ch >= 0x4e00 && ch <= 0x7fff) || (ch >= 0x8000 && ch <= 0x952f))     
            return true;// 简体中文汉字编码     
        return false;     
    }     
    /**   
     * 除去数组中的空值和签名参数   
     * @param sArray 签名参数组   
     * @return 去掉空值与签名参数后的新签名参数组   
     */     
    public static Map<String, String> paraFilter(Map<String, String> sArray) {     
        Map<String, String> result = new HashMap<String, String>();     
        if (sArray == null || sArray.size() <= 0) {     
            return result;     
        }     
        for (String key : sArray.keySet()) {     
            String value = sArray.get(key);     
            if (value == null || value.equals("") || key.equalsIgnoreCase("sign")     
                    || key.equalsIgnoreCase("sign_type")) {     
                continue;     
            }     
            result.put(key, value);     
        }     
        return result;     
    }     
    /**   
     * 把数组所有元素排序，并按照“参数=参数值”的模式用“&”字符拼接成字符串   
     * @param params 需要排序并参与字符拼接的参数组   
     * @return 拼接后字符串   
     */     
    public static String createLinkString(Map<String, String> params) {     
        List<String> keys = new ArrayList<String>(params.keySet());     
        Collections.sort(keys);     
        String prestr = "";     
        for (int i = 0; i < keys.size(); i++) {     
            String key = keys.get(i);     
            String value = params.get(key);     
            if (i == keys.size() - 1) {// 拼接时，不包括最后一个&字符     
                prestr = prestr + key + "=" + value;     
            } else {     
                prestr = prestr + key + "=" + value + "&";     
            }     
        }     
        return prestr;     
    }     
    /**   
     *   
     * @param requestUrl请求地址   
     * @param requestMethod请求方法   
     * @param outputStr参数   
     */     
    public static String httpRequest(String requestUrl,String requestMethod,String outputStr){     
        // 创建SSLContext     
        StringBuffer buffer = null;     
        try{     
            URL url = new URL(requestUrl);     
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();     
            conn.setRequestMethod(requestMethod);     
            conn.setDoOutput(true);     
            conn.setDoInput(true);     
            conn.connect();     
            //往服务器端写内容     
            if(null !=outputStr){     
                OutputStream os=conn.getOutputStream();     
                os.write(outputStr.getBytes("utf-8"));     
                os.close();     
            }     
            // 读取服务器端返回的内容     
            InputStream is = conn.getInputStream();     
            InputStreamReader isr = new InputStreamReader(is, "utf-8");     
            BufferedReader br = new BufferedReader(isr);     
            buffer = new StringBuffer();     
            String line = null;     
            while ((line = br.readLine()) != null) {     
                buffer.append(line);     
            }     
                br.close();  
        }catch(Exception e){     
            e.printStackTrace();     
        }  
        return buffer.toString();  
    }       
    public static String urlEncodeUTF8(String source){     
        String result=source;     
        try {     
            result=java.net.URLEncoder.encode(source, "UTF-8");     
        } catch (UnsupportedEncodingException e) {     
            // TODO Auto-generated catch block     
            e.printStackTrace();     
        }     
        return result;     
    }   
    /** 
     * 解析xml,返回第一级元素键值对。如果第一级元素有子节点，则此节点的值是子节点的xml数据。 
     * @param strxml 
     * @return 
     * @throws JDOMException 
     * @throws IOException 
     */  
    public static Map doXMLParse(String strxml) throws Exception {  
        if(null == strxml || "".equals(strxml)) {  
            return null;  
        }  
          
        Map m = new HashMap();  
        InputStream in = String2Inputstream(strxml);  
        SAXBuilder builder = new SAXBuilder();  
        Document doc = builder.build(in);  
        Element root = doc.getRootElement();  
        List list = root.getChildren();  
        Iterator it = list.iterator();  
        while(it.hasNext()) {  
            Element e = (Element) it.next();  
            String k = e.getName();  
            String v = "";  
            List children = e.getChildren();  
            if(children.isEmpty()) {  
                v = e.getTextNormalize();  
            } else {  
                v = getChildrenText(children);  
            }  
              
            m.put(k, v);  
        }  
          
        //关闭流  
        in.close();  
          
        return m;  
    }  
    /** 
     * 获取子结点的xml 
     * @param children 
     * @return String 
     */  
    public static String getChildrenText(List children) {  
        StringBuffer sb = new StringBuffer();  
        if(!children.isEmpty()) {  
            Iterator it = children.iterator();  
            while(it.hasNext()) {  
                Element e = (Element) it.next();  
                String name = e.getName();  
                String value = e.getTextNormalize();  
                List list = e.getChildren();  
                sb.append("<" + name + ">");  
                if(!list.isEmpty()) {  
                    sb.append(getChildrenText(list));  
                }  
                sb.append(value);  
                sb.append("</" + name + ">");  
            }  
        }  
          
        return sb.toString();  
    }  
    public static InputStream String2Inputstream(String str) {  
        return new ByteArrayInputStream(str.getBytes());  
    }  
} 
```

## 三、小程序端发起最终支付，调用微信付款
```$xslt
doWxPay(param){  
        //小程序发起微信支付  
            wx.requestPayment({  
              timeStamp: param.data.timeStamp,//记住，这边的timeStamp一定要是字符串类型的，不然会报错，我这边在java后端包装成了字符串类型了  
              nonceStr: param.data.nonceStr,  
              package: param.data.package,  
              signType: 'MD5',  
              paySign: param.data.paySign,  
              success: function (event) {  
                // success     
                console.log(event);  
                  
                wx.showToast({  
                  title: '支付成功',  
                  icon: 'success',  
                  duration: 2000  
                });  
              },  
              fail: function (error) {  
                // fail     
                console.log("支付失败")  
                console.log(error)  
              },  
              complete: function () {  
                // complete     
                console.log("pay complete")  
              }  
            });  
    }  
```

## 四、微信服务器通知java后端
```$xslt
/** 
     * @Description:微信支付     
     * @return 
     * @throws Exception  
     */  
    @RequestMapping(value="/wxNotify")  
    @ResponseBody  
    public void wxNotify(HttpServletRequest request,HttpServletResponse response) throws Exception{  
        BufferedReader br = new BufferedReader(new InputStreamReader((ServletInputStream)request.getInputStream()));  
        String line = null;  
        StringBuilder sb = new StringBuilder();  
        while((line = br.readLine()) != null){  
            sb.append(line);  
        }  
        br.close();  
        //sb为微信返回的xml  
        String notityXml = sb.toString();  
        String resXml = "";  
        System.out.println("接收到的报文：" + notityXml);  
      
        Map map = PayUtil.doXMLParse(notityXml);  
          
        String returnCode = (String) map.get("return_code");  
        if("SUCCESS".equals(returnCode)){  
            //验证签名是否正确  
            if(PayUtil.verify(PayUtil.createLinkString(map), (String)map.get("sign"), WxPayConfig.key, "utf-8")){  
                /**此处添加自己的业务逻辑代码start**/  
                  
                  
                /**此处添加自己的业务逻辑代码end**/  
                //通知微信服务器已经支付成功  
                resXml = "<xml>" + "<return_code><![CDATA[SUCCESS]]></return_code>"  
                + "<return_msg><![CDATA[OK]]></return_msg>" + "</xml> ";  
            }  
        }else{  
            resXml = "<xml>" + "<return_code><![CDATA[FAIL]]></return_code>"  
            + "<return_msg><![CDATA[报文为空]]></return_msg>" + "</xml> ";  
        }  
        System.out.println(resXml);  
        System.out.println("微信支付回调数据结束");  
  
  
        BufferedOutputStream out = new BufferedOutputStream(  
                response.getOutputStream());  
        out.write(resXml.getBytes());  
        out.flush();  
        out.close();  
    }  
```

原文链接：[https://blog.csdn.net/zhourenfei17/article/details/77765585](https://blog.csdn.net/zhourenfei17/article/details/77765585)
