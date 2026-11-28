package com.ctts.controller;

import com.ctts.common.RequireRole;
import com.ctts.common.Result;
import com.ctts.common.ValidationGroups;
import com.ctts.entity.Project;
import com.ctts.service.ProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "项目模块 /projects", description = "查询任意登录角色可用；写操作仅教师（role=2），且只能动自己创建的项目")
@RestController
@RequestMapping("/projects")
public class ProjectController {

    @Autowired
    private ProjectService projectService;

    //查询全部项目
    @Operation(summary = "项目列表", description = "任意登录角色可看全部项目（当前不做收窄）")
    @RequireRole({1, 2, 3})
    @GetMapping
    public Result<List<Project>> list() {
        return Result.ok(projectService.list());
    }

    //根据id查询项目
    @Operation(summary = "项目详情", description = "项目不存在 404")
    @RequireRole({1, 2, 3})
    @GetMapping("/{id}")
    public Result<Project> getById(@PathVariable Long id) {
        return Result.ok(projectService.getById(id));
    }

    //新增项目：只校验 Create 组；teacherId 由 token 决定，不再作为客户端输入校验
    @Operation(summary = "新增项目",
            description = "name / startDate / endDate 必填；teacherId 只认 token（请求体传了也无效）")
    @RequireRole({2})
    @PostMapping
    public Result<Project> add(@Validated(ValidationGroups.Create.class) @RequestBody Project project,
                               @RequestAttribute("userId") Long userId) {
        project.setTeacherId(userId);
        return Result.ok(projectService.add(project));
    }

    //修改项目：部分更新，只改传了的字段；只能改自己的项目，不是自己的返回 403
    @Operation(summary = "部分更新项目", description = "只改传了的字段；不是自己的项目 403，不存在 404")
    @RequireRole({2})
    @PutMapping("/{id}")
    public Result<Project> update(@PathVariable Long id, @RequestBody Project project,
                                  @RequestAttribute("userId") Long userId) {
        return Result.ok(projectService.update(id, project, userId));
    }

    //删除指定id项目：同样只允许删自己的
    @Operation(summary = "删除项目", description = "只能删自己的，否则 403；项目下仍有任务时 400（Service 计数守卫，不是外键）")
    @RequireRole({2})
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id, @RequestAttribute("userId") Long userId) {
        projectService.delete(id, userId);
        return Result.ok(null);
    }
}
