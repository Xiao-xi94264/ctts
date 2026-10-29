package com.ctts.controller;

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
    public List<TaskReview> list() {
        return taskReviewService.list();
    }

    //根据id查询评审
    @GetMapping("/{id}")
    public TaskReview getById(@PathVariable Long id) {
        return taskReviewService.getById(id);
    }

    //新增评审
    @PostMapping
    public TaskReview add(@RequestBody TaskReview review) {
        return taskReviewService.add(review);
    }

    //修改评审（只改分数和评语）
    @PutMapping("/{id}")
    public TaskReview update(@PathVariable Long id, @RequestBody TaskReview review) {
        return taskReviewService.update(id, review);
    }

    //删除评审
    @DeleteMapping("/{id}")
    public String delete(@PathVariable Long id) {
        taskReviewService.delete(id);
        return "deleted " + id;
    }
}
