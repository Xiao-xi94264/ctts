package com.ctts.service.impl;


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

    @Override
    public void delete(Long id) {
        taskReviewMapper.delete(id);
    }
}
