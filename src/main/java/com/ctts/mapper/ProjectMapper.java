package com.ctts.mapper;

import com.ctts.enity.Project;
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

    //删除项目
    int delete(Long id);

}
