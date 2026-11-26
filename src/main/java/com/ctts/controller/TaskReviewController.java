package com.ctts.controller;

import com.ctts.common.Result;
import com.ctts.enity.TaskReview;
import com.ctts.service.TaskReviewService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/reviews")
public class TaskReviewController {

    @Autowired
    private TaskReviewService taskReviewService;

    //查询全部评审
    @GetMapping
    public Result<List<TaskReview>> list() {
        return Result.ok(taskReviewService.list());
    }

    //根据id查询评审
    @GetMapping("/{id}")
    public Result<TaskReview> getById(@PathVariable Long id) {
        return Result.ok(taskReviewService.getById(id));
    }

    //新增评审：评审人只认 token，请求体里的 reviewerId 一律忽略
    @PostMapping
    public Result<TaskReview> add(@RequestBody TaskReview review,
                                  @RequestAttribute("userId") Long userId) {
        review.setReviewerId(userId);
        return Result.ok(taskReviewService.add(review));
    }

    //修改评审（只改分数和评语）
    @PutMapping("/{id}")
    public Result<TaskReview> update(@PathVariable Long id, @RequestBody TaskReview review) {
        return Result.ok(taskReviewService.update(id, review));
    }

    //删除评审
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        taskReviewService.delete(id);
        return Result.ok(null);
    }
}
