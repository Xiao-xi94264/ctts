package com.ctts.mapper;

import com.ctts.enity.TaskReview;
import com.ctts.enity.TaskSubmission;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface TaskReviewMapper {
    //增加提交
    int insert(TaskReview taskReview);
    //删除提交
    int delete(Long id);
    //更改提交
    int update(TaskReview taskReview);
    //查询全部提交
    List<TaskReview> selectAll();
    //根据id查询全部提交
    TaskReview selectById(Long id);

}
