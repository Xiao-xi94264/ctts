package com.ctts.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Result<T> {

    private Integer code;
    private String message;
    private T data;

    // 成功：固定 200 + success
    public static <T> Result<T> ok(T data) {
        return new Result<>(200, "success", data);
    }

    // 失败：错误码和提示由调用方决定
    public static <T> Result<T> fail(Integer code, String message) {
        return new Result<>(code, message, null);
    }
}
