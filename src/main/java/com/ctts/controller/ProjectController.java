package com.ctts.controller;

import com.ctts.common.Result;
import com.ctts.common.ValidationGroups;
import com.ctts.entity.Project;
import com.ctts.service.ProjectService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/projects")
public class ProjectController {

    @Autowired
    private ProjectService projectService;

    //查询全部项目
    @GetMapping
    public Result<List<Project>> list() {
        return Result.ok(projectService.list());
    }

    //根据id查询项目
    @GetMapping("/{id}")
    public Result<Project> getById(@PathVariable Long id) {
        return Result.ok(projectService.getById(id));
    }

    //新增项目：只校验 Create 组；teacherId 由 token 决定，不再作为客户端输入校验
    @PostMapping
    public Result<Project> add(@Validated(ValidationGroups.Create.class) @RequestBody Project project,
                               @RequestAttribute("userId") Long userId) {
        project.setTeacherId(userId);
        return Result.ok(projectService.add(project));
    }

    //修改项目：部分更新，只改传了的字段；只能改自己的项目，不是自己的返回 403
    @PutMapping("/{id}")
    public Result<Project> update(@PathVariable Long id, @RequestBody Project project,
                                  @RequestAttribute("userId") Long userId) {
        return Result.ok(projectService.update(id, project, userId));
    }

    //删除指定id项目：同样只允许删自己的
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id, @RequestAttribute("userId") Long userId) {
        projectService.delete(id, userId);
        return Result.ok(null);
    }
}
