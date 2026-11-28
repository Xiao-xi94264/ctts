package com.ctts.controller;

import com.ctts.common.Result;
import com.ctts.entity.TaskReview;
import com.ctts.service.TaskReviewService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/reviews")
public class TaskReviewController {

    @Autowired
    private TaskReviewService taskReviewService;

    //查询评审列表：GET /reviews?submissionId=58&reviewerId=6，两个参数都可省略
    //可见范围由角色决定：管理员全部、学生只看自己提交收到的、教师只看自己写的
    @GetMapping
    public Result<List<TaskReview>> list(
            @RequestParam(value = "submissionId", required = false) Long submissionId,
            @RequestParam(value = "reviewerId", required = false) Long reviewerId,
            @RequestAttribute("userId") Long userId,
            @RequestAttribute("role") Integer role) {
        return Result.ok(taskReviewService.list(submissionId, reviewerId, userId, role));
    }

    //根据id查询评审：同样按角色收窄（管理员全部 / 教师自己写的 / 学生自己提交收到的）
    @GetMapping("/{id}")
    public Result<TaskReview> getById(@PathVariable Long id,
                                      @RequestAttribute("userId") Long userId,
                                      @RequestAttribute("role") Integer role) {
        return Result.ok(taskReviewService.getById(id, userId, role));
    }

    //新增评审：评审人只认 token，请求体里的 reviewerId 一律忽略
    //并且只能评审自己项目下的提交（Service 里顺着 提交→任务→项目 校验归属）
    @PostMapping
    public Result<TaskReview> add(@RequestBody TaskReview review,
                                  @RequestAttribute("userId") Long userId) {
        review.setReviewerId(userId);
        return Result.ok(taskReviewService.add(review));
    }

    //修改评审（只改分数和评语）：只能改自己写的
    @PutMapping("/{id}")
    public Result<TaskReview> update(@PathVariable Long id, @RequestBody TaskReview review,
                                     @RequestAttribute("userId") Long userId) {
        return Result.ok(taskReviewService.update(id, review, userId));
    }

    //删除评审：只能删自己写的
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id, @RequestAttribute("userId") Long userId) {
        taskReviewService.delete(id, userId);
        return Result.ok(null);
    }
}
