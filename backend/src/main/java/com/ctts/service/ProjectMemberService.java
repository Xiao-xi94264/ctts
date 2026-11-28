package com.ctts.service;

import com.ctts.entity.ProjectMember;

import java.util.List;

/**
 * 项目成员管理。成员表是"应交份数"的唯一来源，因此它的写权限必须收紧到
 * 项目负责人本人——任何人往分母里塞一个不存在的学生，全项目的提交率立刻失真。
 */
public interface ProjectMemberService {

    /** 查看成员：任意登录角色可看（与项目列表同样不做收窄） */
    List<ProjectMember> list(Long projectId);

    /**
     * 加入成员。
     *
     * @param operatorId 当前登录用户，用于归属校验——只能给自己创建的项目加人
     */
    ProjectMember add(Long projectId, Long userId, Long operatorId);

    /** 移出成员：同样只能动自己的项目 */
    void remove(Long projectId, Long userId, Long operatorId);
}
