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
        List<ProjectStatsVO> list = statsMapper.statsByProject();
        list.forEach(this::fillDerived);
        return list;
    }

    /**
     * 派生值放在 Java 侧算，不进 SQL。
     * 它们只是两个已查出数字的乘除，塞进聚合语句要多一层嵌套，而且没法单独写用例。
     */
    private void fillDerived(ProjectStatsVO vo) {
        int members = vo.getMemberCount() == null ? 0 : vo.getMemberCount();
        int tasks = vo.getTaskCount() == null ? 0 : vo.getTaskCount();
        int pairs = vo.getSubmittedPairCount() == null ? 0 : vo.getSubmittedPairCount();

        int expected = members * tasks;
        vo.setExpectedCount(expected);

        // 分母为 0 时给 null 而不是 0：
        // 一个没有成员的项目报"提交率 0%"是误导——它根本没有提交率这个属性。
        // 与 avgScore 无评审时为 null 是同一条原则：算不出来的量不要用 0 冒充。
        vo.setSubmitRate(expected == 0
                ? null
                : Math.round(pairs * 10000.0 / expected) / 10000.0);
    }
}
