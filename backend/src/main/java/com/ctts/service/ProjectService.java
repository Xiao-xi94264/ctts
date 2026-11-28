package com.ctts.service;

import com.ctts.entity.Project;

import java.util.List;

public interface ProjectService {
    List<Project> list();

    Project getById(Long id);

    Project add(Project project);

    /** currentUserId：当前登录用户，用于归属校验——只能改自己的项目 */
    Project update(Long id, Project project, Long currentUserId);

    void delete(Long id, Long currentUserId);

}

