package com.ctts.mapper;

import com.ctts.entity.ProjectMember;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 项目成员表访问层。
 *
 * <p>当前只服务于统计（成员数）与测试造数，**尚无对外接口**——
 * 也就是说成员数据目前只能由 DBA / SQL 维护。成员管理接口（加入、移出、批量导入）
 * 已记入待办，待有了它这张表才算闭环。
 */
@Mapper
public interface ProjectMemberMapper {

    //新增成员。join_time 走数据库默认值，不回写 Java 对象，需要时回查
    int insert(ProjectMember member);

    //查某项目的全部成员
    List<ProjectMember> selectByProjectId(@Param("projectId") Long projectId);

    //移出成员：按 (项目, 用户) 定位，不按主键——调用方手里通常没有 id
    int deleteByProjectAndUser(@Param("projectId") Long projectId,
                               @Param("userId") Long userId);
}
