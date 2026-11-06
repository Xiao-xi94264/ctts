package com.ctts.controller;

import com.ctts.common.Result;
import com.ctts.enity.Project;
import com.ctts.service.ProjectService;
import org.springframework.beans.factory.annotation.Autowired;
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

    //新增项目
    @PostMapping
    public Result<Project> add(@RequestBody Project project) {
        return Result.ok(projectService.add(project));
    }

    //根据指定id修改项目
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
