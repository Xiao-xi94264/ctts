package com.ctts.controller;

import com.ctts.common.RequireRole;
import com.ctts.common.Result;
import com.ctts.common.ValidationGroups;
import com.ctts.entity.TaskSubmission;
import com.ctts.service.TaskSubmissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "提交模块 /submissions", description = "读：按角色收窄（学生→自己的 / 教师→自己项目下的 / 管理员→全部）；写：仅学生（role=1）且只能动自己的")
@RestController
@RequestMapping("/submissions")
public class TaskSubmissionController {
    @Autowired
    private TaskSubmissionService taskSubmissionService;

    //查询提交列表：管理员看全部，学生只看自己的，教师只看自己项目下的
    @Operation(summary = "提交列表", description = "按角色收窄，不返回 403 而是只给可见的那部分")
    @RequireRole({1, 2, 3})
    @GetMapping
    public Result<List<TaskSubmission>> list(@RequestAttribute("userId") Long userId,
                                             @RequestAttribute("role") Integer role) {
        return Result.ok(taskSubmissionService.list(userId, role));
    }

    //根据id查询提交：同样按角色收窄（管理员全部 / 学生自己的 / 教师自己项目下的）
    @Operation(summary = "提交详情", description = "不在可见范围内 403；不存在 404")
    @RequireRole({1, 2, 3})
    @GetMapping("/{id}")
    public Result<TaskSubmission> getById(@PathVariable Long id,
                                          @RequestAttribute("userId") Long userId,
                                          @RequestAttribute("role") Integer role) {
        return Result.ok(taskSubmissionService.getById(id, userId, role));
    }

    //提交任务：userId 只认 token，请求体里传什么都不作数
    @Operation(summary = "提交任务",
            description = "taskId 必填；userId 只认 token（请求体传了也无效）；"
                    + "同一任务只能交一份，重复提交 400 并给出已有提交 id 与改内容的 PUT 路径")
    @RequireRole({1})
    @PostMapping
    public Result<TaskSubmission> add(@Validated(ValidationGroups.Create.class) @RequestBody TaskSubmission submission,
                                      @RequestAttribute("userId") Long userId) {
        submission.setUserId(userId);
        return Result.ok(taskSubmissionService.add(submission));
    }

    //修改提交：只能改自己的，别人的返回 403
    @Operation(summary = "部分更新提交", description = "只能改自己的，否则 403；不存在 404")
    @RequireRole({1})
    @PutMapping("/{id}")
    public Result<TaskSubmission> update(@PathVariable Long id, @RequestBody TaskSubmission submission,
                                         @RequestAttribute("userId") Long userId) {
        return Result.ok(taskSubmissionService.update(id, submission, userId));
    }

    //删除提交：同样只允许删自己的
    @Operation(summary = "删除提交", description = "只能删自己的，否则 403")
    @RequireRole({1})
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id, @RequestAttribute("userId") Long userId) {
        taskSubmissionService.delete(id, userId);
        return Result.ok(null);
    }
}
