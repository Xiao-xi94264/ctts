package com.ctts.controller;

import com.ctts.common.Result;
import com.ctts.enity.TaskSubmission;
import com.ctts.service.TaskSubmissionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/submissions")
public class TaskSubmissionController {
    @Autowired
    private TaskSubmissionService taskSubmissionService;

    //查询全部提交
    @GetMapping
    public Result<List<TaskSubmission>> list() {
        return Result.ok(taskSubmissionService.list());
    }

    //根据id查询提交
    @GetMapping("/{id}")
    public Result<TaskSubmission> getById(@PathVariable Long id) {
        return Result.ok(taskSubmissionService.getById(id));
    }

    //提交任务
    @PostMapping
    public Result<TaskSubmission> add(@RequestBody TaskSubmission submission) {
        return Result.ok(taskSubmissionService.add(submission));
    }

    //修改提交
    @PutMapping("/{id}")
    public Result<TaskSubmission> update(@PathVariable Long id, @RequestBody TaskSubmission submission) {
        return Result.ok(taskSubmissionService.update(id, submission));
    }

    //删除提交
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        taskSubmissionService.delete(id);
        return Result.ok(null);
    }
}
