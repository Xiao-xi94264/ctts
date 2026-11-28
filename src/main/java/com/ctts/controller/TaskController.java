package com.ctts.controller;

import com.ctts.common.RequireRole;
import com.ctts.common.Result;
import com.ctts.entity.Task;
import com.ctts.service.TaskService;
import com.ctts.vo.PageVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "任务模块 /tasks", description = "查询任意登录角色可用；写操作仅教师（role=2），且只能动自己项目下的任务")
@RestController
@RequestMapping("/tasks")
public class TaskController {

    @Autowired
    private TaskService taskService;

    //查询全部任务
    @Operation(summary = "任务列表", description = "可选 ?projectId=X 按项目过滤；不传则返回全部")
    @RequireRole({1, 2, 3})
    @GetMapping
    public Result<List<Task>> list(@RequestParam(value = "projectId", required = false) Long projectId) {
        return Result.ok(taskService.list(projectId));
    }

    //根据id查询任务
    @Operation(summary = "任务详情", description = "任务不存在 404")
    @RequireRole({1, 2, 3})
    @GetMapping("/{id}")
    public Result<Task> getById(@PathVariable Long id) {
        return Result.ok(taskService.getById(id));
    }

    //新增任务
    @Operation(summary = "新增任务",
            description = "totalScore 不传默认 100；deadline 只接受 yyyy-MM-dd HH:mm:ss（带 T 会 400）；只能挂在自己项目下")
    @RequireRole({2})
    @PostMapping
    public Result<Task> add(@RequestBody Task task) {
        return Result.ok(taskService.add(task));
    }

    //根据指定id修改任务：只能改自己项目下的任务，不是自己的返回 403
    @Operation(summary = "部分更新任务", description = "只改传了的字段；不是自己项目下的任务 403，不存在 404")
    @RequireRole({2})
    @PutMapping("/{id}")
    public Result<Task> update(@PathVariable Long id, @RequestBody Task task,
                               @RequestAttribute("userId") Long userId) {
        return Result.ok(taskService.update(id, task, userId));
    }

    //删除指定id任务：同样只允许删自己项目下的
    @Operation(summary = "删除任务", description = "只能删自己项目下的，否则 403；任务下仍有提交时 400（外键）")
    @RequireRole({2})
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id, @RequestAttribute("userId") Long userId) {
        taskService.delete(id, userId);
        return Result.ok(null);
    }

    //分页查询任务：GET /tasks/page?page=1&pageSize=3&projectId=1
    //返回 { list, total, page, pageSize }，前端靠 total 算总页数
    @Operation(summary = "分页查询任务",
            description = "返回 {list, total, page, pageSize}；page 从 1 开始，pageSize 上限 100（传更大被裁到 100）")
    @RequireRole({1, 2, 3})
    @GetMapping("/page")
    public Result<PageVO<Task>> page(@RequestParam(value = "projectId", required = false) Long projectId,
                                     @RequestParam(value = "page", defaultValue = "1") Integer page,
                                     @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        return Result.ok(taskService.page(projectId, page, pageSize));
    }
}
