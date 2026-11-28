package com.ctts.mapper;

import com.ctts.entity.Project;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ProjectMapper {
    //根据id查询项目
    Project selectById(Long id);

    //查询全部项目
    List<Project> selectAll();

    //插入新项目
    int insert(Project project);

    //修改项目
    int update(Project project);

    //逻辑删除：deleted 置为本行 id，行还在（历史与统计口径都依赖它）
    int softDelete(Long id);

    //物理删除：仅供测试清理与运维，业务代码不得调用
    int hardDelete(Long id);

}
