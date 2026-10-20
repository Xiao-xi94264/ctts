package com.ctts.mapper;

import com.ctts.enity.User;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface UserMapper {
    //根据id查询用户
    User selectById(Long id);
    //返回全部的用户
    List<User> selectAll();
    //插入用户
    int insert(User user);
    //更新用户
    int update(User user);
    //根据id删除员工
    int delete(Long id);
    //根据用户名查询数据库中的密码
    User selectByUsername(String username);

}
