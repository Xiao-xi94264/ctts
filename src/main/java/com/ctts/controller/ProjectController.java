package com.ctts.controller;

import com.ctts.common.Result;
import com.ctts.common.ValidationGroups;
import com.ctts.enity.Project;
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

    //修改项目：部分更新，只改传了的字段，不做必填校验
    @PutMapping("/{id}")
    public Result<Project> update(@PathVariable Long id, @RequestBody Project project) {
        return Result.ok(projectService.update(id, project));
    }

    //删除指定id项目
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        projectService.delete(id);
        return Result.ok(null);
    }
}
