package com.ctts.entity;

import com.ctts.common.ValidationGroups;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 项目成员：记录"哪个学生属于哪个项目"。
 *
 * <p>这张表补的是此前的一个真实缺口——系统里只有 {@code training_project.teacher_id}
 * 指向教师，学生和项目之间没有任何直接关系，"项目有哪些学生"这个事实从未被存储。
 * 于是统计只能算"已提交数"，算不出"应交份数"，提交率的分母无从谈起。
 *
 * <p>{@code (project_id, user_id)} 上建了唯一索引：同一学生不能重复加入同一项目。
 * 只记学生，教师与管理员不进这张表——否则成员数会把教师也算进分母。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProjectMember {
    private Long id;

    /** 项目 ID：由路径参数决定，请求体里传了也不作数，所以不挂校验 */
    private Long projectId;

    /** 成员（学生）ID，对应 sys_user.id。加入成员时必填，缺失报 400 而不是落库成 500 */
    @NotNull(message = "学生 ID 不能为空", groups = ValidationGroups.Create.class)
    private Long userId;

    /** 加入时间，由数据库默认值生成，Java 侧不回填 */
    private LocalDateTime joinTime;
}

