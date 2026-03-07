package com.example.demo.mapper;

import com.example.demo.model.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 用户 Mapper 接口
 * 使用 XML 方式配置 SQL（推荐，便于维护复杂SQL）
 */
@Mapper
public interface UserMapper {

    /**
     * 根据ID查询用户
     */
    User selectById(@Param("id") Long id);

    /**
     * 根据用户名查询用户
     */
    User selectByUsername(@Param("username") String username);

    /**
     * 根据手机号查询用户
     */
    User selectByPhone(@Param("phone") String phone);

    /**
     * 根据邮箱查询用户
     */
    User selectByEmail(@Param("email") String email);

    /**
     * 分页查询用户列表
     * @param offset 偏移量
     * @param limit 每页大小
     */
    List<User> selectList(@Param("offset") Integer offset, @Param("limit") Integer limit);

    /**
     * 根据条件查询用户列表
     * @param username 用户名（模糊查询）
     * @param phone 手机号
     * @param status 状态
     */
    List<User> selectByCondition(@Param("username") String username,
                                   @Param("phone") String phone,
                                   @Param("status") Integer status);

    /**
     * 查询用户总数
     */
    Long countAll();

    /**
     * 插入用户
     * @return 影响行数
     */
    int insert(User user);

    /**
     * 更新用户信息
     * @return 影响行数
     */
    int update(User user);

    /**
     * 更新用户登录信息
     */
    int updateLoginInfo(@Param("id") Long id,
                        @Param("lastLoginTime") String lastLoginTime,
                        @Param("lastLoginIp") String lastLoginIp);

    /**
     * 更新用户状态
     */
    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

    /**
     * 根据ID删除用户（逻辑删除，设置状态为0）
     */
    int deleteById(@Param("id") Long id);

    /**
     * 根据ID物理删除用户（慎用）
     */
    int deleteByIdPhysical(@Param("id") Long id);
}
