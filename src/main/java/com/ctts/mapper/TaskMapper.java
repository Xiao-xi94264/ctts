package com.ctts.mapper;

import com.ctts.entity.Task;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TaskMapper {
    //增加任务
    int insert(Task task);

    //逻辑删除：deleted 置为本行 id
    int softDelete(Long id);

    //物理删除：仅供测试清理与运维
    int hardDelete(Long id);

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

    /** 某项目下还有几个未删除的任务：删除项目前的存在性检查。
     *  软删除后外键不再拦"删父留子"，这条计数就是唯一的守卫 */
    long countByProjectId(@Param("projectId") Long projectId);
}
