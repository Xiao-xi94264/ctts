package com.ctts.vo;

import lombok.Data;

/**
 * 项目统计视图：不是表映射，而是聚合查询的结果载体。
 * 只有 projectId / projectTitle 来自 training_project，
 * 其余四个都是 COUNT / AVG 算出来的，任何一张表都装不下。
 */
@Data
public class ProjectStatsVO {
    private Long projectId;
    private String projectTitle;
    private Integer taskCount;
    private Integer submissionCount;
    private Integer reviewedCount;
    private Double avgScore;
}
