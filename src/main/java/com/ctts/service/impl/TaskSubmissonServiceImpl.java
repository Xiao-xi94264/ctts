package com.ctts.service.impl;

import com.ctts.common.BusinessException;
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
        // 所有可更新字段都没传 → <set> 会生成空的 SET，SQL 语法错误，必须在这里拦住
        if (submission.getContent() == null && submission.getFileUrl() == null) {
            throw new BusinessException(400, "没有需要更新的字段");
        }
        taskSubmissionMapper.update(submission);
        // 部分更新后入参不完整，回查数据库拿真实状态返回
        return taskSubmissionMapper.selectById(id);
    }

    @Override
    public void delete(Long id) {
        taskSubmissionMapper.delete(id);
    }
}
