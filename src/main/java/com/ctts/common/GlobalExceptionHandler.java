package com.ctts.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.sql.SQLException;

/**
 * 全局异常处理器：把各类异常统一翻译成 Result 结构。
 * 这里不设 HTTP 状态码，统一返回 200 + body 里的 code，前端只看 code。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // 1. 业务异常：code 与 message 由业务代码决定
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusiness(BusinessException e) {
        log.warn("业务异常: code={}, message={}", e.getCode(), e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    // 2. 唯一索引冲突：如 sys_user.username 重复
    @ExceptionHandler(DuplicateKeyException.class)
    public Result<Void> handleDuplicateKey(DuplicateKeyException e) {
        log.warn("唯一索引冲突: {}", e.getMessage());
        return Result.fail(400, "数据已存在，请勿重复提交");
    }

    // 3. 数据完整性约束：errno 1452（引用的记录不存在）/ 1451（仍被引用，不能删）
    @ExceptionHandler(DataIntegrityViolationException.class)
    public Result<Void> handleDataIntegrity(DataIntegrityViolationException e) {
        Throwable cause = e.getMostSpecificCause();
        int errno = cause instanceof SQLException sqlEx ? sqlEx.getErrorCode() : 0;
        log.warn("数据完整性约束失败 errno={}: {}", errno, cause.getMessage());
        if (errno == 1452) {
            return Result.fail(400, "关联数据不存在，请检查外键字段（teacherId / projectId / taskId / userId 等）");
        }
        if (errno == 1451) {
            return Result.fail(400, "该记录仍被其他数据引用，无法删除");
        }
        return Result.fail(400, "数据约束校验失败");
    }

    // 4. 请求体解析失败：JSON 语法错误、日期格式错误（如 deadline 少了 T）
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleNotReadable(HttpMessageNotReadableException e) {
        log.warn("请求体解析失败: {}", e.getMessage());
        return Result.fail(400, "请求体格式错误，请检查 JSON 语法与日期格式（如 2026-03-10T18:00:00）");
    }

    // 5. 路径不存在：兜底处理器会把 404 变成 500，所以单独接住
    @ExceptionHandler(NoResourceFoundException.class)
    public Result<Void> handleNotFound(NoResourceFoundException e) {
        return Result.fail(404, "接口不存在: " + e.getResourcePath());
    }

    // 6. 兜底：未预料的异常，堆栈只进日志，不返回给前端
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("未预期异常", e);
        return Result.fail(500, "服务器内部错误");
    }
}
