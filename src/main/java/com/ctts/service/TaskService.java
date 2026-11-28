package com.ctts.service;

import com.ctts.entity.Task;
import com.ctts.vo.PageVO;

import java.util.List;

public interface TaskService {
    List<Task> list(Long projectId);

    Task getById(Long id);

    Task add(Task task);

    /** currentUserId：当前登录用户，用于归属校验——只能改自己项目下的任务 */
    Task update(Long id, Task task, Long currentUserId);

    void delete(Long id, Long currentUserId);

    /** 分页返回结构里带 total，前端才能算出总页数 */
    PageVO<Task> page(Long projectId, Integer page, Integer pageSize);
}
