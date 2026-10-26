package com.ctts.service.impl;

import com.ctts.enity.Project;
import com.ctts.mapper.ProjectMapper;
import com.ctts.service.ProjectService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProjectServiceImpl implements ProjectService {

    @Autowired
    private ProjectMapper projectMapper;

    @Override
    public List<Project> list() {
        return projectMapper.selectAll();
    }

    @Override
    public Project getById(Long id) {
        return projectMapper.selectById(id);
    }

    @Override
    public Project add(Project project) {
        projectMapper.insert(project);
        return project;
    }

    @Override
    public Project update(Long id, Project project) {
        project.setId(id);
        projectMapper.update(project);
        return project;
    }

    @Override
    public void delete(Long id) {
        projectMapper.delete(id);
    }

}
