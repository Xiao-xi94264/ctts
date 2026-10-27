package com.ctts.service.impl;

import com.ctts.enity.TaskSubmission;
import com.ctts.mapper.TaskSubmissionMapper;
import com.ctts.service.TaskSubmissionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TaskSubmissonServiceImpl implements TaskSubmissionService {

    @Autowired
    private TaskSubmissionMapper taskSubmissionMapper;

    @Override
    public List<TaskSubmission> list() {
        return taskSubmissionMapper.selectAll();
    }

    @Override
    public TaskSubmission getById(Long id) {
        return taskSubmissionMapper.selectById(id);
    }

    @Override
    public TaskSubmission add(TaskSubmission submission) {
        taskSubmissionMapper.insert(submission);
        //需要返回已经插入的提交时间，只在数据库中生成
        return taskSubmissionMapper.selectById(submission.getId());
    }

    @Override
    public TaskSubmission update(Long id, TaskSubmission submission) {
         submission.setId(id);
         taskSubmissionMapper.update(submission);
         return taskSubmissionMapper.selectById(id);
    }

    @Override
    public void delete(Long id) {
        taskSubmissionMapper.delete(id);
    }
}
