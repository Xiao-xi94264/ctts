package com.ctts.mapper;

import com.ctts.vo.ProjectStatsVO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface StatsMapper {
    //按项目统计：任务数、提交数、已评审数、平均分
    List<ProjectStatsVO> statsByProject();
}
