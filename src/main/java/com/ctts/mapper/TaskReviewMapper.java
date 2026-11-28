package com.ctts.mapper;

import com.ctts.entity.TaskReview;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TaskReviewMapper {
    //增加评审
    int insert(TaskReview taskReview);
    //删除评审
    int delete(Long id);
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
