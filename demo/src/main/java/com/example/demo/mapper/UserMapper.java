package com.example.demo.mapper;

import com.example.demo.model.entity.User;
import org.apache.ibatis.annotations.*;

@Mapper
public interface UserMapper {

    @Select("SELECT * FROM t_user WHERE id = #{id}")
    User selectById(Long id);

    @Select("SELECT * FROM t_user WHERE username = #{username}")
    User selectByUsername(String username);

    @Insert("INSERT INTO t_user (username, password_hash, phone, email, status) " +
            "VALUES (#{username}, #{passwordHash}, #{phone}, #{email}, #{status})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(User user);
}
