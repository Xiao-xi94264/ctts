package com.ctts.controller;

import com.ctts.common.Result;
import com.ctts.entity.TaskSubmission;
import com.ctts.service.TaskSubmissionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/submissions")
public class TaskSubmissionController {
    @Autowired
    private TaskSubmissionService taskSubmissionService;

    //查询提交列表：管理员看全部，学生只看自己的，教师只看自己项目下的
    @GetMapping
    public Result<List<TaskSubmission>> list(@RequestAttribute("userId") Long userId,
                                             @RequestAttribute("role") Integer role) {
        return Result.ok(taskSubmissionService.list(userId, role));
    }

    //根据id查询提交：同样按角色收窄（管理员全部 / 学生自己的 / 教师自己项目下的）
    @GetMapping("/{id}")
    public Result<TaskSubmission> getById(@PathVariable Long id,
                                          @RequestAttribute("userId") Long userId,
                                          @RequestAttribute("role") Integer role) {
        return Result.ok(taskSubmissionService.getById(id, userId, role));
    }

    //提交任务：userId 只认 token，请求体里传什么都不作数
    @PostMapping
    public Result<TaskSubmission> add(@RequestBody TaskSubmission submission,
                                      @RequestAttribute("userId") Long userId) {
        submission.setUserId(userId);
        return Result.ok(taskSubmissionService.add(submission));
    }

    //修改提交：只能改自己的，别人的返回 403
    @PutMapping("/{id}")
    public Result<TaskSubmission> update(@PathVariable Long id, @RequestBody TaskSubmission submission,
                                         @RequestAttribute("userId") Long userId) {
        return Result.ok(taskSubmissionService.update(id, submission, userId));
    }

    //删除提交：同样只允许删自己的
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id, @RequestAttribute("userId") Long userId) {
        taskSubmissionService.delete(id, userId);
        return Result.ok(null);
    }
}
