package com.ctts.service.impl;


import com.ctts.common.BusinessException;
import com.ctts.entity.TaskReview;
import com.ctts.entity.TaskSubmission;
import com.ctts.mapper.TaskReviewMapper;
import com.ctts.mapper.TaskSubmissionMapper;
import com.ctts.service.TaskReviewService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Slf4j
@Service
public class TaskReviewServiceImpl implements TaskReviewService {

    @Autowired
    private TaskReviewMapper taskReviewMapper;
    @Autowired
    private TaskSubmissionMapper taskSubmissionMapper;

    /**
     * 列表按角色收窄：
     * 3 管理员 → 无限制；1 学生 → 自己提交收到的评审；2 教师 → 自己写的评审。
     * 筛选参数仍可叠加，但永远在角色允许的范围内再收窄，不会因此越界。
     */
    @Override
    public List<TaskReview> list(Long submissionId, Long reviewerId, Long userId, Integer role) {
        if (Integer.valueOf(3).equals(role)) {
            return taskReviewMapper.selectByCondition(submissionId, reviewerId);
        }
        if (Integer.valueOf(1).equals(role)) {
            return taskReviewMapper.selectByStudent(userId, submissionId);
        }
        return taskReviewMapper.selectByReviewer(userId, submissionId, reviewerId);
    }

    /**
     * 单条详情与 list() 同一套范围：3 管理员全部 / 2 教师只有自己写的 / 1 学生只有针对自己提交的。
     * 学生的判定跨一层：review.submission_id → task_submission.user_id。
     */
    @Override
    public TaskReview getById(Long id, Long userId, Integer role) {
        TaskReview review = taskReviewMapper.selectById(id);
        if (review == null) {
            throw new BusinessException(404, "评审记录不存在");
        }
        if (Integer.valueOf(3).equals(role)) {
            return review;
        }
        if (Integer.valueOf(2).equals(role)) {
            if (!Objects.equals(review.getReviewerId(), userId)) {
                throw new BusinessException(403, "只能查看自己写的评审");
            }
            return review;
        }
        // 学生：这条评审是不是针对我交的那份
        TaskSubmission submission = taskSubmissionMapper.selectById(review.getSubmissionId());
        if (submission == null || !Objects.equals(submission.getUserId(), userId)) {
            throw new BusinessException(403, "只能查看自己提交收到的评审");
        }
        return review;
    }

    @Transactional
    @Override
    public TaskReview add(TaskReview taskReview) {
        // reviewerId 已由 Controller 用 token 覆盖，这里校验的是"这条提交是不是归我管"。
        // 提交 → 任务 → 项目 → teacher_id，一条 JOIN 查上来；查不到说明提交不存在或链条断裂。
        Long ownerTeacherId = taskSubmissionMapper.selectProjectTeacherId(taskReview.getSubmissionId());
        if (ownerTeacherId == null) {
            throw new BusinessException(400, "提交不存在，或该提交未归属任何项目");
        }
        if (!Objects.equals(ownerTeacherId, taskReview.getReviewerId())) {
            log.warn("教师尝试评审他人项目的提交: submissionId={} reviewerId={} ownerTeacherId={}",
                    taskReview.getSubmissionId(), taskReview.getReviewerId(), ownerTeacherId);
            throw new BusinessException(403, "只能评审自己项目下的提交");
        }

        // 一个提交只评一次。两层都要有，但职责不同：
        // 这一层给的是能读懂的提示（"已有评审，改分请走 PUT"）；
        // 数据库 uk_review_submission 那一层防的是并发——两个请求同时通过上面的判空，
        // 只有一个能插入成功，另一个撞唯一索引落成 400，绝不会出现两条互相矛盾的评分。
        TaskReview reviewed = taskReviewMapper.selectBySubmissionId(taskReview.getSubmissionId());
        if (reviewed != null) {
            throw new BusinessException(400,
                    "该提交已有评审（id=" + reviewed.getId() + "），修改评分请调用 PUT /reviews/" + reviewed.getId());
        }

        taskReviewMapper.insert(taskReview);
        // review_time 由数据库默认值生成，不回填 Java 对象，回查一次拿完整数据
        //更改评审状态
        taskSubmissionMapper.updateStatus(taskReview.getSubmissionId(), 2);
        return taskReviewMapper.selectById(taskReview.getId());
    }

    @Override
    public TaskReview update(Long id, TaskReview taskReview, Long currentUserId) {
        TaskReview existing = taskReviewMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(404, "评审记录不存在");
        }
        checkReviewOwner(existing, currentUserId);

        taskReview.setId(id);
        taskReviewMapper.update(taskReview);
        // update 只写 score/comment，入参里没有 submissionId/reviewerId，
        // 直接返回会看到这两个字段是 null，所以回查
        return taskReviewMapper.selectById(id);
    }

    @Transactional
    @Override
    public void delete(Long id, Long currentUserId) {
        // 必须先查后删：删掉之后就再也拿不到 submissionId，无法回退状态
        TaskReview taskReview = taskReviewMapper.selectById(id);
        if (taskReview == null) {
            // 不判 null 的话 updateStatus(null, 1) 会静默影响 0 行，什么错都不报
            throw new BusinessException(404, "评审记录不存在");
        }
        checkReviewOwner(taskReview, currentUserId);
        taskReviewMapper.softDelete(id);
        // 状态回退到 1（待评审），status 只有 1/2 两个值。
        // 注意这里传的是 submissionId 而不是 review 的 id ——
        // updateStatus 的 WHERE 条件是 task_submission.id，传错主键会改到别人的提交
        taskSubmissionMapper.updateStatus(taskReview.getSubmissionId(), 1);
    }

    /** reviewer_id 不在 update 的 SET 里，改不了，所以"先查后写"无竞态 */
    private void checkReviewOwner(TaskReview review, Long currentUserId) {
        if (!Objects.equals(review.getReviewerId(), currentUserId)) {
            throw new BusinessException(403, "只能操作自己写的评审");
        }
    }
}
