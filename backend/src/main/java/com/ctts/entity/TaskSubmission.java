package com.ctts.entity;

import com.ctts.common.ValidationGroups;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TaskSubmission {
    private Long id;

    // 只在 POST /submissions 校验：交的是哪个任务必须明说。
    // 不校验的话 task_id 为 null 会直达数据库，被 NOT NULL 拒成 500，
    // 而"少传一个字段"属于客户端错误，应该是 400。
    // userId 不挂校验——它由 Controller 从 token 覆盖写入，请求体里传不传都不作数，
    // 若在这里要求必填，@Validated 会在覆盖之前就把合法请求拒掉。
    @NotNull(message = "任务 ID 不能为空", groups = ValidationGroups.Create.class)
    private Long taskId;

    private Long userId;
    private String content;
    private String fileUrl;
    private LocalDateTime submitTime;
    private Integer status;
}
