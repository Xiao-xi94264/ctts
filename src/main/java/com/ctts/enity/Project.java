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

    @NotNull(message = "teacherId 不能为空",groups = ValidationGroups.Create.class)
    private Long teacherId;

    @NotNull(message = "开始日期不能为空",groups = ValidationGroups.Create.class)
    private LocalDate startDate;

    @NotNull(message = "结束日期不能为空",groups = ValidationGroups.Create.class)
    private LocalDate endDate;

    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
