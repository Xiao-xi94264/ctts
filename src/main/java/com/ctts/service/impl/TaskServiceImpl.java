package com.ctts.service.impl;

import com.ctts.common.BusinessException;
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
        // 所有可更新字段都没传 → <set> 会生成空的 SET，SQL 语法错误，必须在这里拦住
        if (task.getTitle() == null && task.getDescription() == null
                && task.getDeadline() == null && task.getTotalScore() == null) {
            throw new BusinessException(400, "没有需要更新的字段");
        }
        taskMapper.update(task);
        // 部分更新后入参不完整，回查数据库拿真实状态返回
        return taskMapper.selectById(id);
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
