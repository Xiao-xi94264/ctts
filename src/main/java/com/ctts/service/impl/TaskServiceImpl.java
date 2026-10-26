package com.ctts.service.impl;

import com.ctts.enity.Task;
import com.ctts.mapper.TaskMapper;
import com.ctts.service.TaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TaskServiceImpl implements TaskService {

    @Autowired
    private TaskMapper taskMapper;

    @Override
    public List<Task> list(Long projectId) {
        return taskMapper.selectByCondition(projectId);
    }

    @Override
    public Task getById(Long id) {
        return taskMapper.selectById(id);
    }

    @Override
    public Task add(Task task) {
        taskMapper.insert(task);
        return task;
    }

    @Override
    public Task update(Long id, Task task) {
        task.setId(id);
        taskMapper.update(task);
        return task;
    }

    @Override
    public void delete(Long id) {
        taskMapper.delete(id);
    }

    @Override
    public List<Task> page(Long projectId, Integer page, Integer pageSize) {
        if (page == null || page < 1) {
            page = 1;
        }
        if (pageSize == null || pageSize < 1) {
            pageSize = 10;
        }
        int offset = (page - 1) * pageSize;
        return taskMapper.selectByPage(projectId, offset, pageSize);
    }
}
