package com.ctts.controller;

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
    public List<Project> list(){
        return projectService.list();
    }

    //根据id查询项目
    @GetMapping("/{id}")
    public Project getById(@PathVariable Long id){
        return projectService.getById(id);
    }

    //新增项目
    @PostMapping
    public Project add(@RequestBody Project project){
       return projectService.add(project);
    }

    //根据指定id修改项目
    @PutMapping("/{id}")
    public Project update(@PathVariable Long id,@RequestBody Project project){
        return projectService.update(id,project);
    }

    //删除指定id项目
    @DeleteMapping("/{id}")
    public String delete(@PathVariable Long id){
        projectService.delete(id);
        return "delete" + id;
    }
}
