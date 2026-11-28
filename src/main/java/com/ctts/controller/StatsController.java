package com.ctts.controller;

import com.ctts.common.Result;
import com.ctts.service.StatsService;
import com.ctts.vo.ProjectStatsVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "统计模块 /stats",
        description = "任意登录角色可读。项目成员数来自 project_member（只含学生），"
                + "提交率 = 提交份数 / (成员数 × 任务数)，分母为 0 时返回 null 而非 0")
@RestController
@RequestMapping("/stats")
public class StatsController {

    @Autowired
    private StatsService statsService;

    //按项目统计：任务数、成员数、提交条数/份数、已评审数、应交份数、提交率、平均分
    @Operation(summary = "按项目统计",
            description = "四表 JOIN + 成员数标量子查询；用 COUNT(DISTINCT) 防 JOIN 行放大。"
                    + "submissionCount 是条数、submittedPairCount 是份数（同一学生对同一任务去重），提交率的分子用后者。"
                    + "submitRate 与 avgScore 在没有分母/没有评审时为 null 而不是 0")
    @GetMapping("/projects")
    public Result<List<ProjectStatsVO>> statsByProject() {
        return Result.ok(statsService.statsByProject());
    }
}
