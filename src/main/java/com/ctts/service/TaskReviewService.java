package com.ctts.service;

import com.ctts.enity.TaskReview;
import com.ctts.enity.TaskSubmission;

import java.util.List;

public interface TaskReviewService {
    List<TaskReview> list();

    TaskReview getById(Long id);

    TaskReview add(TaskReview taskReview);

    TaskReview update(Long id, TaskReview taskReview);

    void delete(Long id);
}
