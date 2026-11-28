package com.ctts.controller;

import com.ctts.common.RequireRole;
import com.ctts.common.Result;
import com.ctts.common.ValidationGroups;
import com.ctts.entity.ProjectMember;
import com.ctts.service.ProjectMemberService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 成员是项目的子资源，路径挂在 /projects/{projectId}/members 下。
 *
 * <p>这个前缀不只是排版：{@code JwtInterceptor.checkRole} 按 URI 前缀判定写权限，
 * {@code startsWith("/projects")} 命中"写操作 need=2"，所以 POST/DELETE 天然只放行教师，
 * 无需在本类里再判一次角色。若把路径改成 /members 或 /project-members，拦截器会落到
 * else 分支直接放行——那才是真正危险的：任何登录用户都能改分母。
 */
@Tag(name = "项目成员 /projects/{projectId}/members",
        description = "读：任意登录角色；写：仅项目负责人教师。只能加入学生（role=1），教师与管理员不能进成员表"
                + "——否则统计的成员数会把老师算进提交率分母")
@RestController
@RequestMapping("/projects/{projectId}/members")
public class ProjectMemberController {

    @Autowired
    private ProjectMemberService projectMemberService;

    @Operation(summary = "成员列表", description = "项目不存在 404；没有成员时返回空数组")
    @RequireRole({1, 2, 3})
    @GetMapping
    public Result<List<ProjectMember>> list(@PathVariable Long projectId) {
        return Result.ok(projectMemberService.list(projectId));
    }

    //加入成员：projectId 只认路径，请求体里传了也无效
    @Operation(summary = "加入成员",
            description = "userId 必填；只能加学生（role=1），非学生 400；重复加入 400；"
                    + "项目不存在 404；不是自己的项目 403")
    @RequireRole({2})
    @PostMapping
    public Result<ProjectMember> add(@PathVariable Long projectId,
                                     @Validated(ValidationGroups.Create.class) @RequestBody ProjectMember member,
                                     @RequestAttribute("userId") Long operatorId) {
        return Result.ok(projectMemberService.add(projectId, member.getUserId(), operatorId));
    }

    //移出成员：路径里直接给 userId，不占用请求体
    @Operation(summary = "移出成员", description = "该学生不在项目里 404；不是自己的项目 403")
    @RequireRole({2})
    @DeleteMapping("/{userId}")
    public Result<Void> remove(@PathVariable Long projectId, @PathVariable Long userId,
                               @RequestAttribute("userId") Long operatorId) {
        projectMemberService.remove(projectId, userId, operatorId);
        return Result.ok(null);
    }
}
