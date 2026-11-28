package com.ctts.common;

/**
 * 业务异常：由业务代码主动抛出，携带"给前端看的"错误码与提示。
 * 继承 RuntimeException 而不是 Exception，是为了不必在每层方法签名上写 throws。
 */
public class BusinessException extends RuntimeException {

    private final Integer code;

    public BusinessException(Integer code, String message) {
        super(message);
        this.code = code;
    }

    public Integer getCode() {
        return code;
    }
}
