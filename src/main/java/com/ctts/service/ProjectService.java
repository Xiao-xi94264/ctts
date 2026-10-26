package com.ctts.service;

import com.ctts.enity.Project;

import java.util.List;

public interface ProjectService {
    List<Project> list();

    Project getById(Long id);

    Project add(Project project);

    Project update(Long id, Project project);

    void delete(Long id);

}

