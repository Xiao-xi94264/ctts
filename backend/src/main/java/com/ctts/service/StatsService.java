package com.ctts.service;

import com.ctts.vo.ProjectStatsVO;

import java.util.List;

public interface StatsService {
    List<ProjectStatsVO> statsByProject();
}
