package com.ctts.mapper;

import com.ctts.entity.Task;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TaskMapper {
    //增加任务
    int insert(Task task);

    //删除任务
    int delete(Long id);

    //更新任务
    int update(Task task);

    //查询全部任务
    //List<Task> selectAll();
    //添加新功能，可以根据项目id返回全部的项目任务
    List<Task> selectByCondition(@Param("projectId") Long projectId);

    //根据id查询任务
    Task selectById(Long id);

    //分页查询任务，可按照项目进行筛选
    List<Task> selectByPage(@Param("projectId") Long projectId,
                            @Param("offset") int offset,
                            @Param("pageSize") int pageSize);

    //统计满足条件的任务总数，供分页算总页数用。
    //筛选条件必须与 selectByPage 完全一致，否则 total 与 list 会对不上
    long selectCount(@Param("projectId") Long projectId);
}
