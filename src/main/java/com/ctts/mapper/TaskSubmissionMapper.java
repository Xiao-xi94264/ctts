package com.ctts.mapper;

import com.ctts.entity.TaskSubmission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TaskSubmissionMapper {
    //增加提交
    int insert(TaskSubmission taskSubmission);
    //逻辑删除：deleted 置为本行 id。删掉后同一学生可重新提交（唯一索引含 deleted）
    int softDelete(Long id);

    //物理删除：仅供测试清理与运维
    int hardDelete(Long id);

    /** 某任务下还有几份未删除的提交：删除任务前的存在性检查。
     *  软删除后外键不再拦"删父留子"，这条计数就是唯一的守卫 */
    long countByTaskId(@Param("taskId") Long taskId);
    //更改提交
    int update(TaskSubmission taskSubmission);
    //查询全部提交（仅管理员视角使用）
    List<TaskSubmission> selectAll();
    //根据id查询提交
    TaskSubmission selectById(Long id);
    //修改评审状态
    int updateStatus(@Param("id")Long id,@Param("status")Integer status);

    //学生视角：只查自己交的
    List<TaskSubmission> selectByUserId(@Param("userId") Long userId);
    //教师视角：只查自己项目下的（提交 → 任务 → 项目 → teacher_id）
    List<TaskSubmission> selectByTeacherId(@Param("teacherId") Long teacherId);
    //查某条提交所属项目的负责教师，用于"只能评审自己项目下的提交"这条校验
    Long selectProjectTeacherId(@Param("submissionId") Long submissionId);
    //查同一学生对同一任务是否已交过：一个任务只收一份，判重用
    TaskSubmission selectByTaskAndUser(@Param("taskId") Long taskId,
                                       @Param("userId") Long userId);
}
