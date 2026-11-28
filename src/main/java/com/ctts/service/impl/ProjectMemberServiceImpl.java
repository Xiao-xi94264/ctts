package com.ctts.service.impl;

import com.ctts.common.BusinessException;
import com.ctts.entity.Project;
import com.ctts.entity.ProjectMember;
import com.ctts.entity.User;
import com.ctts.mapper.ProjectMapper;
import com.ctts.mapper.ProjectMemberMapper;
import com.ctts.mapper.UserMapper;
import com.ctts.service.ProjectMemberService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class ProjectMemberServiceImpl implements ProjectMemberService {

    @Autowired
    private ProjectMemberMapper projectMemberMapper;

    @Autowired
    private ProjectMapper projectMapper;

    @Autowired
    private UserMapper userMapper;

    @Override
    public List<ProjectMember> list(Long projectId) {
        // 项目不存在时报 404 而不是返回空列表：空列表的含义是"这个项目还没有学生"，
        // 与"项目根本不存在"是两件事，混在一起会让调用方以为项目建好了只是没人。
        requireProject(projectId);
        return projectMemberMapper.selectByProjectId(projectId);
    }

    @Override
    public ProjectMember add(Long projectId, Long userId, Long operatorId) {
        checkProjectOwner(requireProject(projectId), operatorId);

        // 加进来的人必须真实存在、且是学生。
        // 教师/管理员如果进了这张表，统计的"成员数"会把老师算进提交率分母，
        // 于是分母虚高、提交率永远偏低——这不是显示问题，是数据含义被污染了。
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(400, "用户不存在：id=" + userId);
        }
        if (!Integer.valueOf(1).equals(user.getRole())) {
            throw new BusinessException(400, "只能把学生加入项目：该用户角色为 " + user.getRole() + "（1 学生 / 2 教师 / 3 管理员）");
        }

        // 重复加入：与 #16 / #29 同一套两层结构。
        // 这一层给可读提示，uk_member_project_user 那层防并发。
        ProjectMember existing = projectMemberMapper.selectByProjectAndUser(projectId, userId);
        if (existing != null) {
            throw new BusinessException(400, "该学生已是项目成员（id=" + existing.getId() + "），无需重复加入");
        }

        ProjectMember member = new ProjectMember();
        member.setProjectId(projectId);
        member.setUserId(userId);
        projectMemberMapper.insert(member);
        // join_time 由数据库默认值生成，不回填 Java 对象；回查一次才拿到完整数据
        return projectMemberMapper.selectByProjectAndUser(projectId, userId);
    }

    @Override
    public void remove(Long projectId, Long userId, Long operatorId) {
        checkProjectOwner(requireProject(projectId), operatorId);

        // 先查后删：delete 影响 0 行时无法区分"项目不是你的"和"这人本来就不在项目里"，
        // 而这两种情况对应 403 与 404，必须分开
        if (projectMemberMapper.selectByProjectAndUser(projectId, userId) == null) {
            throw new BusinessException(404, "该学生不是项目成员：projectId=" + projectId + ", userId=" + userId);
        }
        projectMemberMapper.deleteByProjectAndUser(projectId, userId);
    }

    private Project requireProject(Long projectId) {
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BusinessException(404, "项目不存在");
        }
        return project;
    }

    /** 与项目模块同一条规则：成员管理的权限来自"这个项目是不是你的" */
    private void checkProjectOwner(Project project, Long operatorId) {
        if (!Objects.equals(project.getTeacherId(), operatorId)) {
            throw new BusinessException(403, "只能管理自己项目下的成员");
        }
    }
}
