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
    /**
     * 逻辑删除：把 deleted 置为本行 id（不是 1）。
     *
     * <p>为什么用 id 而不是 1：唯一索引 {@code uk_user_username(username, deleted)} 里，
     * 未删的行 deleted=0 保证 username 唯一；若删除后写固定值 1，
     * 同一个用户名「删两次」就会撞索引——第一次删的那行已经占着 (name, 1)。
     * 用 id 则每条被删记录占一个不同的位置，历史记录可以无限堆积。
     */
    int softDelete(Long id);

    /** 物理删除：<b>仅供测试清理与运维</b>，业务代码一律用 {@link #softDelete}。
     *  保留它是为了让测试能真正清掉造的数据，不让库里留下 deleted!=0 的垃圾行。 */
    int hardDelete(Long id);
    //根据用户名查询数据库中的密码
    User selectByUsername(String username);
    //账号启停：单独一条语句，与 update 的部分更新 <set> 分开，避免"改资料顺手改了状态"
    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

}
