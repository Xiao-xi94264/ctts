package com.ctts.service;

import com.ctts.enity.TaskSubmission;

import java.util.List;

public interface TaskSubmissionService {
    List<TaskSubmission> list();

    TaskSubmission getById(Long id);

    TaskSubmission add(TaskSubmission submission);

    TaskSubmission update(Long id, TaskSubmission submission);

    void delete(Long id);
}
