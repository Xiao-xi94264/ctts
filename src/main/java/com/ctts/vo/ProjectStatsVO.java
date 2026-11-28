package com.ctts.vo;

import lombok.Data;

/**
 * 项目统计视图：不是表映射，而是聚合查询的结果载体。
 * 只有 projectId / projectTitle 来自 training_project，
 * 其余都是 COUNT / AVG 算出来的，任何一张表都装不下。
 *
 * <p>「条数」与「份数」是两个不同口径，不能混用：
 * <ul>
 *   <li>{@code submissionCount} —— 提交条数（一条记录算一次，含重复提交）</li>
 *   <li>{@code submittedPairCount} —— 提交份数（同一学生对同一任务只算一份）</li>
 * </ul>
 * 提交率的分子必须是份数，否则重复提交会让比率超过 100%。
 *
 * <p>{@code expectedCount} 与 {@code submitRate} 不在 SQL 里算：
 * 它们只是两个已有数字的除法，塞进聚合语句要多一层嵌套，
 * 放在 Service 里则可以单独写单测。它们是派生值，不是聚合结果。
 */
@Data
public class ProjectStatsVO {
    private Long projectId;
    private String projectTitle;

    /** 任务数 */
    private Integer taskCount;
    /** 项目成员数（只含学生） */
    private Integer memberCount;

    /** 提交条数 */
    private Integer submissionCount;
    /** 提交份数：COUNT(DISTINCT task_id, user_id) */
    private Integer submittedPairCount;
    /** 已评审数 */
    private Integer reviewedCount;

    /** 应交份数 = memberCount × taskCount */
    private Integer expectedCount;
    /** 提交率 = submittedPairCount / expectedCount；分母为 0 时为 null（不是 0） */
    private Double submitRate;

    /** 平均分；无评审时为 null */
    private Double avgScore;
}
