package com.ctts.controller;

import com.ctts.common.Result;
import com.ctts.entity.Task;
import com.ctts.service.TaskService;
import com.ctts.vo.PageVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/tasks")
public class TaskController {

    @Autowired
    private TaskService taskService;

    //查询全部任务
    @GetMapping
    public Result<List<Task>> list(@RequestParam(value = "projectId", required = false) Long projectId) {
        return Result.ok(taskService.list(projectId));
    }

    //根据id查询任务
    @GetMapping("/{id}")
    public Result<Task> getById(@PathVariable Long id) {
        return Result.ok(taskService.getById(id));
    }

    //新增任务
    @PostMapping
    public Result<Task> add(@RequestBody Task task) {
        return Result.ok(taskService.add(task));
    }

    //根据指定id修改任务：只能改自己项目下的任务，不是自己的返回 403
    @PutMapping("/{id}")
    public Result<Task> update(@PathVariable Long id, @RequestBody Task task,
                               @RequestAttribute("userId") Long userId) {
        return Result.ok(taskService.update(id, task, userId));
    }

    //删除指定id任务：同样只允许删自己项目下的
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id, @RequestAttribute("userId") Long userId) {
        taskService.delete(id, userId);
        return Result.ok(null);
    }

    //分页查询任务：GET /tasks/page?page=1&pageSize=3&projectId=1
    //返回 { list, total, page, pageSize }，前端靠 total 算总页数
    @GetMapping("/page")
    public Result<PageVO<Task>> page(@RequestParam(value = "projectId", required = false) Long projectId,
                                     @RequestParam(value = "page", defaultValue = "1") Integer page,
                                     @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        return Result.ok(taskService.page(projectId, page, pageSize));
    }
}
