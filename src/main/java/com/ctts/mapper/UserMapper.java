package com.ctts.mapper;

import com.ctts.enity.User;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface UserMapper {
    User selectById(Long id);
    List<User> selectAll();
}
