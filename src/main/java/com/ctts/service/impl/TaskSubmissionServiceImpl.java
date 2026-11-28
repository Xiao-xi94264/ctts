package com.ctts.service.impl;

import com.ctts.common.BusinessException;
import com.ctts.entity.TaskSubmission;
import com.ctts.mapper.TaskSubmissionMapper;
import com.ctts.service.TaskSubmissionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class TaskSubmissionServiceImpl implements TaskSubmissionService {

    @Autowired
    private TaskSubmissionMapper taskSubmissionMapper;

    /**
     * 列表按角色收窄可见范围：
     * 3 管理员 → 全部；1 学生 → 只有自己交的；2 教师 → 自己项目下的。
     * 这就是 D9「越权读」的落点：GET 在拦截器里是一律放行的，
     * 所以"谁能看见哪些行"只能在这一层决定。
     */
    @Override
    public List<TaskSubmission> list(Long userId, Integer role) {
        if (Integer.valueOf(3).equals(role)) {
            return taskSubmissionMapper.selectAll();
        }
        if (Integer.valueOf(1).equals(role)) {
            return taskSubmissionMapper.selectByUserId(userId);
        }
        return taskSubmissionMapper.selectByTeacherId(userId);
    }

    /**
     * 单条详情走与列表同一套可见范围：3 管理员全部 / 1 学生只有自己交的 / 2 教师只有自己项目下的。
     * 曾经这里是裸的 selectById —— 列表里看不到的行，知道 id 直接 GET 就能读出来，
     * 那 list() 里那套按角色收窄等于白写。
     */
    @Override
    public TaskSubmission getById(Long id, Long userId, Integer role) {
        TaskSubmission submission = taskSubmissionMapper.selectById(id);
        if (submission == null) {
            throw new BusinessException(404, "提交不存在");
        }
        if (Integer.valueOf(3).equals(role)) {
            return submission;
        }
        if (Integer.valueOf(1).equals(role)) {
            if (!Objects.equals(submission.getUserId(), userId)) {
                throw new BusinessException(403, "只能查看自己的提交");
            }
            return submission;
        }
        // 教师：提交本身没有 teacher_id，顺着 提交→任务→项目 查负责人（只取一个 Long，不整行加载）
        Long ownerTeacherId = taskSubmissionMapper.selectProjectTeacherId(id);
        if (!Objects.equals(ownerTeacherId, userId)) {
            throw new BusinessException(403, "只能查看自己项目下的提交");
        }
        return submission;
    }

    @Override
    public TaskSubmission add(TaskSubmission submission) {
        taskSubmissionMapper.insert(submission);
        //需要返回已经插入的提交时间，只在数据库中生成
        return taskSubmissionMapper.selectById(submission.getId());
    }

    @Override
    public TaskSubmission update(Long id, TaskSubmission submission, Long currentUserId) {
        // 先查一次：记录不存在 → 404；存在但不是自己交的 → 403
        TaskSubmission existing = taskSubmissionMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(404, "提交不存在");
        }
        checkSubmissionOwner(existing, currentUserId);

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
    public void delete(Long id, Long currentUserId) {
        TaskSubmission existing = taskSubmissionMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(404, "提交不存在");
        }
        checkSubmissionOwner(existing, currentUserId);
        taskSubmissionMapper.delete(id);
    }

    /** user_id 不在 update 的 <set> 里，改不了；因此"先查后写"之间没有竞态 */
    private void checkSubmissionOwner(TaskSubmission submission, Long currentUserId) {
        if (!Objects.equals(submission.getUserId(), currentUserId)) {
            throw new BusinessException(403, "只能操作自己的提交");
        }
    }
}
