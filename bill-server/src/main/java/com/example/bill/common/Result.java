package com.example.bill.common;

import lombok.Data;

/**
 * 统一接口返回结构
 *
 * @param <T> 业务数据类型
 */
@Data
public class Result<T> {

    /** 0 成功，1 失败 */
    private Integer code;

    /** 提示信息 */
    private String msg;

    /** 业务数据 */
    private T data;

    public Result() {
    }

    public Result(Integer code, String msg, T data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
    }

    /**
     * 成功，无数据
     */
    public static <T> Result<T> ok() {
        return new Result<>(0, "成功", null);
    }

    /**
     * 成功，携带数据
     */
    public static <T> Result<T> ok(T data) {
        return new Result<>(0, "成功", data);
    }

    /**
     * 失败
     */
    public static <T> Result<T> fail(String msg) {
        return new Result<>(1, msg, null);
    }
}
