package com.ctts.mapper;

import com.ctts.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface UserMapper {
    //根据id查询用户
    User selectById(Long id);
    //按角色查用户；role 为 null 即查全部（一条 SQL 覆盖两种情况，不再 selectAll 后内存过滤）
    List<User> selectByRole(@Param("role") Integer role);
    //插入用户
    int insert(User user);
    //更新用户（刻意不含 status——启停走独立的 updateStatus）
    int update(User user);
    //根据id删除员工
    int delete(Long id);
    //根据用户名查询数据库中的密码
    User selectByUsername(String username);
    //账号启停：单独一条语句，与 update 的部分更新 <set> 分开，避免"改资料顺手改了状态"
    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

}
