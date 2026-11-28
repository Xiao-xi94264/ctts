package com.ctts.mapper;

import com.ctts.entity.ProjectMember;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 项目成员表访问层。
 *
 * <p>服务于两处：统计的成员数（分母），以及成员管理接口（加入 / 移出 / 查看）。
 * 成员只能由项目负责人教师维护——这条规则写在 Service，不写在 Mapper。
 */
@Mapper
public interface ProjectMemberMapper {

    //新增成员。join_time 走数据库默认值，不回写 Java 对象，需要时回查
    int insert(ProjectMember member);

    //查某项目的全部成员
    List<ProjectMember> selectByProjectId(@Param("projectId") Long projectId);

    //查某学生是否已在项目里。返回单对象：业务上最多一条（uk_member_project_user 保证）
    ProjectMember selectByProjectAndUser(@Param("projectId") Long projectId,
                                         @Param("userId") Long userId);

    //移出成员：按 (项目, 用户) 定位，不按主键——调用方手里通常没有 id
    int deleteByProjectAndUser(@Param("projectId") Long projectId,
                               @Param("userId") Long userId);
}
