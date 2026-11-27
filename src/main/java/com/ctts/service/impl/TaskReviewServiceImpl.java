package com.ctts.service.impl;


import com.ctts.common.BusinessException;
import com.ctts.enity.TaskReview;
import com.ctts.mapper.TaskReviewMapper;
import com.ctts.mapper.TaskSubmissionMapper;
import com.ctts.service.TaskReviewService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TaskReviewServiceImpl implements TaskReviewService {

    @Autowired
    private TaskReviewMapper taskReviewMapper;
    @Autowired
    private TaskSubmissionMapper taskSubmissionMapper;

    @Override
    public List<TaskReview> list() {
        return taskReviewMapper.selectAll();
    }

    @Override
    public TaskReview getById(Long id) {
        return taskReviewMapper.selectById(id);
    }

    @Transactional
    @Override
    public TaskReview add(TaskReview taskReview) {
        taskReviewMapper.insert(taskReview);
        // review_time 由数据库默认值生成，不回填 Java 对象，回查一次拿完整数据
        //更改评审状态
        taskSubmissionMapper.updateStatus(taskReview.getSubmissionId(),2);
        return taskReviewMapper.selectById(taskReview.getId());
    }

    @Override
    public TaskReview update(Long id, TaskReview taskReview) {
        taskReview.setId(id);
        taskReviewMapper.update(taskReview);
        // update 只写 score/comment，入参里没有 submissionId/reviewerId，
        // 直接返回会看到这两个字段是 null，所以回查
        return taskReviewMapper.selectById(id);
    }

    @Transactional
    @Override
    public void delete(Long id) {
        // 必须先查后删：删掉之后就再也拿不到 submissionId，无法回退状态
        TaskReview taskReview = taskReviewMapper.selectById(id);
        if (taskReview == null) {
            // 不判 null 的话 updateStatus(null, 1) 会静默影响 0 行，什么错都不报
            throw new BusinessException(404, "评审记录不存在");
        }
        taskReviewMapper.delete(id);
        // 状态回退到 1（待评审），status 只有 1/2 两个值。
        // 注意这里传的是 submissionId 而不是 review 的 id ——
        // updateStatus 的 WHERE 条件是 task_submission.id，传错主键会改到别人的提交
        taskSubmissionMapper.updateStatus(taskReview.getSubmissionId(), 1);
    }
}
