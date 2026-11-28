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

@Tag(name = "统计模块 /stats", description = "任意登录角色可读。只出四个绝对值，不输出提交率（缺 project_member 关系表，分母不存在）")
@RestController
@RequestMapping("/stats")
public class StatsController {

    @Autowired
    private StatsService statsService;

    //按项目统计：任务数、提交数、已评审数、平均分
    @Operation(summary = "按项目统计",
            description = "四表 JOIN + GROUP BY；用 COUNT(DISTINCT) 防 JOIN 行放大。平均分保留两位，无评审的项目为 null")
    @GetMapping("/projects")
    public Result<List<ProjectStatsVO>> statsByProject() {
        return Result.ok(statsService.statsByProject());
    }
}
