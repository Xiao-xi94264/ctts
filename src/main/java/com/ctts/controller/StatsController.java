package com.ctts.controller;

import com.ctts.common.Result;
import com.ctts.service.StatsService;
import com.ctts.vo.ProjectStatsVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/stats")
public class StatsController {

    @Autowired
    private StatsService statsService;

    //按项目统计：任务数、提交数、已评审数、平均分
    @GetMapping("/projects")
    public Result<List<ProjectStatsVO>> statsByProject() {
        return Result.ok(statsService.statsByProject());
    }
}
