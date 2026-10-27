package com.ctts.controller;

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
    public List<TaskSubmission> list() {
        return taskSubmissionService.list();
    }

    //根据id查询提交
    @GetMapping("/{id}")
    public TaskSubmission getById(@PathVariable Long id) {
        return taskSubmissionService.getById(id);
    }

    //提交任务
    @PostMapping
    public TaskSubmission add(@RequestBody TaskSubmission submission) {
        return taskSubmissionService.add(submission);
    }

    //修改提交
    @PutMapping("/{id}")
    public TaskSubmission update(@PathVariable Long id, @RequestBody TaskSubmission submission) {
        return taskSubmissionService.update(id, submission);
    }

    //删除提交
    @DeleteMapping("/{id}")
    public String delete(@PathVariable Long id) {
        taskSubmissionService.delete(id);
        return "deleted " + id;
    }
}
