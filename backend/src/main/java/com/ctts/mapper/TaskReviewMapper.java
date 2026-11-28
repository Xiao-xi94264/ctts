package com.ctts.mapper;

import com.ctts.entity.TaskReview;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TaskReviewMapper {
    //增加评审
    int insert(TaskReview taskReview);
    //逻辑删除：deleted 置为本行 id。删掉后该提交可重新评审
    int softDelete(Long id);

    //物理删除：仅供测试清理与运维
    int hardDelete(Long id);

    /** 级联逻辑删除：删提交时必须把它下面的评审一起标记掉。
     *  物理删除时代这层由外键拦着（删不掉），软删除不触发外键，
     *  不显式处理就会留下"指向已删提交的评审"——统计里看不见，但 GET /reviews 还在返回 */
    int softDeleteBySubmissionId(@Param("submissionId") Long submissionId);
    //更改评审
    int update(TaskReview taskReview);
    //查询评审列表：可按提交 / 按评审人筛选，两个都不传等价于查全部（管理员视角）
    //（原来的 selectAll 已并入这里，避免"查全部"和"查筛选"两套 SQL 各写一份列清单）
    List<TaskReview> selectByCondition(@Param("submissionId") Long submissionId,
                                       @Param("reviewerId") Long reviewerId);
    //根据id查询评审
    TaskReview selectById(Long id);

    //按提交查评审。返回单个对象而不是 List —— 因为 uk_review_submission
    //保证"一个提交至多一条评审"，类型本身就把这条约束说了出来（返回 List 会暗示可以有多条）
    TaskReview selectBySubmissionId(@Param("submissionId") Long submissionId);

    //学生视角：只看自己提交收到的评审（评审 → 提交 → user_id）
    List<TaskReview> selectByStudent(@Param("userId") Long userId,
                                     @Param("submissionId") Long submissionId);
    //教师视角：只看自己写的评审；仍允许叠加 submissionId 进一步收窄
    List<TaskReview> selectByReviewer(@Param("userId") Long userId,
                                      @Param("submissionId") Long submissionId,
                                      @Param("reviewerId") Long reviewerId);
}
