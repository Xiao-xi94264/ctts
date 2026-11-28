package com.ctts.service.impl;

import com.ctts.common.BusinessException;
import com.ctts.entity.Project;
import com.ctts.entity.Task;
import com.ctts.mapper.ProjectMapper;
import com.ctts.mapper.TaskMapper;
import com.ctts.service.TaskService;
import com.ctts.vo.PageVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class TaskServiceImpl implements TaskService {

    /** 单页上限：防止 ?pageSize=1000000 把整表一次拉进内存 */
    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_PAGE_SIZE = 10;

    @Autowired
    private TaskMapper taskMapper;

    // 任务表没有 teacher_id，归属要顺着 project_id 再查一层
    @Autowired
    private ProjectMapper projectMapper;

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
        // task.total_score 列上有 DEFAULT 100，但 insert 是静态列清单：
        // Java 里是 null 就会被显式写成 NULL，撞 NOT NULL（errno 1048），默认值根本不参与。
        // 数据库默认值只在"这一列不出现在 INSERT 语句里"时才生效，所以默认值必须在 Java 侧补。
        // 改这里的数值要同步改 DDL，两处必须一致。
        if (task.getTotalScore() == null) {
            task.setTotalScore(100);
        }
        taskMapper.insert(task);
        // create_time / update_time 由数据库生成，同样不回填 Java 对象，回查一次拿真实值
        return taskMapper.selectById(task.getId());
    }

    @Override
    public Task update(Long id, Task task, Long currentUserId) {
        // 先查一次：同时解决两件事——记录不存在（应 404）与不是自己的（应 403）。
        // 如果直接 update 再看影响行数，"不存在"和"存在但不是我的"会给出同一个信号（都是 0 行），无法区分
        Task existing = taskMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(404, "任务不存在");
        }
        checkTaskOwner(existing.getProjectId(), currentUserId);

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
    public void delete(Long id, Long currentUserId) {
        Task existing = taskMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(404, "任务不存在");
        }
        checkTaskOwner(existing.getProjectId(), currentUserId);
        taskMapper.delete(id);
    }

    @Override
    public PageVO<Task> page(Long projectId, Integer page, Integer pageSize) {
        // 页码与条数都在这里兜底，Controller 只负责接收
        int safePage = (page == null || page < 1) ? 1 : page;
        int safeSize = (pageSize == null || pageSize < 1) ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);

        long total = taskMapper.selectCount(projectId);
        // total 为 0 时不必再查数据页：结果必然是空列表
        List<Task> list = total == 0
                ? List.of()
                : taskMapper.selectByPage(projectId, (safePage - 1) * safeSize, safeSize);
        return PageVO.of(list, total, safePage, safeSize);
    }

    /**
     * 归属校验：任务属于某个项目，项目属于某位教师。
     * 这两层的归属都不可改（task.project_id、project.teacher_id 都不在 update 的 <set> 里），
     * 所以"先查后写"之间不存在竞态窗口。
     */
    private void checkTaskOwner(Long projectId, Long currentUserId) {
        Project project = (projectId == null) ? null : projectMapper.selectById(projectId);
        if (project == null || !Objects.equals(project.getTeacherId(), currentUserId)) {
            throw new BusinessException(403, "只能操作自己项目下的任务");
        }
    }
}
