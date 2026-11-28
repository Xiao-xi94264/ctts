package com.ctts.service;

import com.ctts.entity.TaskSubmission;

import java.util.List;

public interface TaskSubmissionService {
    /** userId + role：按角色收窄可见范围（管理员全部 / 学生自己的 / 教师自己项目下的） */
    List<TaskSubmission> list(Long userId, Integer role);

    /**
     * userId + role：单条详情与列表共用同一套可见范围。
     * 列表里看不到的行，按 id 单查也不能拿到——否则收窄只是"眼不见"，知道 id 就绕过去了
     */
    TaskSubmission getById(Long id, Long userId, Integer role);

    TaskSubmission add(TaskSubmission submission);

    /** currentUserId：归属校验——只能改自己的提交 */
    TaskSubmission update(Long id, TaskSubmission submission, Long currentUserId);

    void delete(Long id, Long currentUserId);
}
