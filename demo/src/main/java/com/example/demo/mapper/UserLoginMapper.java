package com.example.demo.mapper;

import com.example.demo.model.entity.UserLogin;
import org.apache.ibatis.annotations.*;

/**
 * 用户登录 Mapper
 */
@Mapper
public interface UserLoginMapper {

    /**
     * 根据用户名查询登录信息
     */
    @Select("SELECT id, user_id, username, password, user_type, salt, status, created_at, updated_at " +
            "FROM t_user_login WHERE username = #{username}")
    @Results(id = "userLoginMap", value = {
            @Result(property = "userId", column = "user_id"),
            @Result(property = "userType", column = "user_type"),
            @Result(property = "createdAt", column = "created_at"),
            @Result(property = "updatedAt", column = "updated_at")
    })
    UserLogin selectByUsername(String username);

    /**
     * 根据用户ID查询登录信息
     */
    @Select("SELECT id, user_id, username, password, user_type, salt, status, created_at, updated_at " +
            "FROM t_user_login WHERE user_id = #{userId}")
    @ResultMap("userLoginMap")
    UserLogin selectByUserId(Long userId);

    /**
     * 插入登录信息
     */
    @Insert("INSERT INTO t_user_login (user_id, username, password, user_type, salt, status) " +
            "VALUES (#{userId}, #{username}, #{password}, #{userType}, #{salt}, #{status})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(UserLogin userLogin);

    /**
     * 更新密码
     */
    @Update("UPDATE t_user_login SET password = #{password}, salt = #{salt}, updated_at = NOW() " +
            "WHERE id = #{id}")
    int updatePassword(@Param("id") Long id, @Param("password") String password, @Param("salt") String salt);

    /**
     * 更新状态
     */
    @Update("UPDATE t_user_login SET status = #{status}, updated_at = NOW() WHERE id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

    /**
     * 根据用户ID删除
     */
    @Delete("DELETE FROM t_user_login WHERE user_id = #{userId}")
    int deleteByUserId(Long userId);
}
