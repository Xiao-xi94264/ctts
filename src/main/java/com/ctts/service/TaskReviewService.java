package com.ctts.service;

import com.ctts.entity.TaskReview;
import com.ctts.entity.TaskSubmission;

import java.util.List;

public interface TaskReviewService {
    /**
     * userId + role：按角色收窄可见范围（管理员全部 / 学生自己提交收到的 / 教师自己写的），
     * submissionId 与 reviewerId 是额外筛选条件，只能在上面的范围内继续收窄
     */
    List<TaskReview> list(Long submissionId, Long reviewerId, Long userId, Integer role);

    /**
     * userId + role：单条详情与列表共用同一套可见范围。
     * 学生的"是不是我的"要跨一层：review.submission_id → task_submission.user_id
     */
    TaskReview getById(Long id, Long userId, Integer role);

    TaskReview add(TaskReview taskReview);

    /** currentUserId：归属校验——只能改自己写的评审 */
    TaskReview update(Long id, TaskReview taskReview, Long currentUserId);

    void delete(Long id, Long currentUserId);
}
