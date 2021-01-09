package com.yyl.loginservice.bean;

import com.yyl.loginservice.utils.CommonConstant;
import lombok.Data;

import java.io.Serializable;

/**
 * @program:
 * @description:  接口统一返回结果
 * @author: YYL
 * @create: 2020-11-24 10:49
 */
@Data
public class R implements Serializable{
    private int code;
    private String msg;
    private Object data;

    public R() {
    }

    public R(int code, String msg, Object data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
    }


    public static R ok(){
        return new R(CommonConstant.OK,"OK",null);
    }
    public static R ok(Object data){
        return new R(CommonConstant.OK,"OK",data);
    }
    public static R fail(){
        return new R(CommonConstant.FAIL,"fail",null);
    }
    public static R fail(String msg){
        return new R(CommonConstant.FAIL,msg,null);
    }
    public static R ok(String msg){
        return new R(CommonConstant.OK,msg,null);
    }
}