package com.ctts.service.impl;

import com.ctts.common.BusinessException;
import com.ctts.entity.Project;
import com.ctts.mapper.ProjectMapper;
import com.ctts.service.ProjectService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class ProjectServiceImpl implements ProjectService {

    @Autowired
    private ProjectMapper projectMapper;

    @Override
    public List<Project> list() {
        return projectMapper.selectAll();
    }

    @Override
    public Project getById(Long id) {
        return projectMapper.selectById(id);
    }

    @Override
    public Project add(Project project) {
        projectMapper.insert(project);
        // status / create_time / update_time 由数据库默认值生成，不回填 Java 对象，
        // insert 之后手上这个对象这三个字段还是 null；回查一次才拿到库里的真实状态
        return projectMapper.selectById(project.getId());
    }

    @Override
    public Project update(Long id, Project project, Long currentUserId) {
        // 先查一次同时解决两件事：记录不存在 → 404；存在但不是自己的 → 403。
        // 只看 update 影响行数的话，这两种情况都是 0 行，无法区分。
        Project existing = projectMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(404, "项目不存在");
        }
        checkProjectOwner(existing, currentUserId);

        project.setId(id);
        // 所有可更新字段都没传 → <set> 会生成空的 SET，SQL 语法错误，必须在这里拦住
        if (project.getTitle() == null && project.getDescription() == null
                && project.getStartDate() == null && project.getEndDate() == null) {
            throw new BusinessException(400, "没有需要更新的字段");
        }
        projectMapper.update(project);
        // 部分更新后入参不完整，回查数据库拿真实状态返回
        return projectMapper.selectById(id);
    }

    @Override
    public void delete(Long id, Long currentUserId) {
        Project existing = projectMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(404, "项目不存在");
        }
        checkProjectOwner(existing, currentUserId);
        projectMapper.delete(id);
    }

    /** 项目归属教师；teacher_id 不在 update 的 <set> 里，改不了，所以先查后写无竞态 */
    private void checkProjectOwner(Project project, Long currentUserId) {
        if (!Objects.equals(project.getTeacherId(), currentUserId)) {
            throw new BusinessException(403, "只能操作自己的项目");
        }
    }

}
