package com.ctts.service;

import com.ctts.enity.Task;

import java.util.List;

public interface TaskService {
    List<Task> list(Long projectId);

    Task getById(Long id);

    Task add(Task task);

    Task update(Long id, Task task);

    void delete(Long id);

    List<Task> page(Long projectId, Integer page, Integer pageSize);
}
