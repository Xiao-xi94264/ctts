package com.ctts.enity;

import com.ctts.common.ValidationGroups;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Project {
    private Long id;

    @NotBlank(message = "项目名称不能为空",groups = ValidationGroups.Create.class)
    private String title;

    private String description;

    // teacherId 由 Controller 从 token 覆盖，不再是客户端输入，故不校验必填
    private Long teacherId;

    @NotNull(message = "开始日期不能为空",groups = ValidationGroups.Create.class)
    private LocalDate startDate;

    @NotNull(message = "结束日期不能为空",groups = ValidationGroups.Create.class)
    private LocalDate endDate;

    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
