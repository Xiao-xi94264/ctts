package com.ctts.controller;

import com.ctts.enity.Task;
import com.ctts.service.TaskService;
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
    public List<Task> list(@RequestParam(value = "projectId",required = false) Long projectId ) {
        return taskService.list(projectId);
    }

    //根据id查询任务
    @GetMapping("/{id}")
    public Task getById(@PathVariable Long id) {
        return taskService.getById(id);
    }

    //新增任务
    @PostMapping
    public Task add(@RequestBody Task task) {
        return taskService.add(task);
    }

    //根据指定id修改任务
    @PutMapping("/{id}")
    public Task update(@PathVariable Long id, @RequestBody Task task) {
        return taskService.update(id, task);
    }

    //删除指定id任务
    @DeleteMapping("/{id}")
    public String delete(@PathVariable Long id) {
        taskService.delete(id);
        return "deleted " + id;
    }

    //分页查询任务：GET /tasks/page?page=1&pageSize=3&projectId=1
    @GetMapping("/page")
    public List<Task> page(@RequestParam(value = "projectId", required = false) Long projectId,
                           @RequestParam(value = "page", defaultValue = "1") Integer page,
                           @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        return taskService.page(projectId, page, pageSize);
    }
}
