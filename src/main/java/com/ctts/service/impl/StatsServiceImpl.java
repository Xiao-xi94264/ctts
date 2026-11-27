package com.ctts.service.impl;

import com.ctts.mapper.StatsMapper;
import com.ctts.service.StatsService;
import com.ctts.vo.ProjectStatsVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class StatsServiceImpl implements StatsService {

    @Autowired
    private StatsMapper statsMapper;

    @Override
    public List<ProjectStatsVO> statsByProject() {
        // 统计是纯读：没有事务、没有状态变更，直接透传
        return statsMapper.statsByProject();
    }
}
