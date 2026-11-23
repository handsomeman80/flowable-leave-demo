package com.demo.leave.common;

/**
 * 统一返回体，方便前端/curl 看结果。
 */
public class ApiResult<T> {

    private int code;
    private String msg;
    private T data;

    public ApiResult() {
    }

    public ApiResult(int code, String msg, T data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
    }

    public static <T> ApiResult<T> ok(T data) {
        return new ApiResult<T>(0, "ok", data);
    }

    public static <T> ApiResult<T> ok() {
        return new ApiResult<T>(0, "ok", null);
    }

    public static <T> ApiResult<T> fail(String msg) {
        return new ApiResult<T>(500, msg, null);
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }
}
