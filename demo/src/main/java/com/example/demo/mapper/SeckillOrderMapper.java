package com.example.demo.mapper;

import com.example.demo.model.entity.SeckillOrder;
import org.apache.ibatis.annotations.*;

@Mapper
public interface SeckillOrderMapper {

    @Select("SELECT id, user_id, seckill_id, product_id, order_price, status, created_at " +
            "FROM t_seckill_order WHERE user_id = #{userId} AND seckill_id = #{seckillId}")
    @Results(id = "seckillOrderMap", value = {
            @Result(property = "userId", column = "user_id"),
            @Result(property = "seckillId", column = "seckill_id"),
            @Result(property = "productId", column = "product_id"),
            @Result(property = "orderPrice", column = "order_price"),
            @Result(property = "createdAt", column = "created_at")
    })
    SeckillOrder selectByUserAndSeckill(@Param("userId") Long userId, @Param("seckillId") Long seckillId);

    @Insert("INSERT INTO t_seckill_order (user_id, seckill_id, product_id, order_price, status) " +
            "VALUES (#{userId}, #{seckillId}, #{productId}, #{orderPrice}, #{status})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(SeckillOrder order);

    @Insert("INSERT INTO t_seckill_order (id, user_id, seckill_id, product_id, order_price, status) " +
            "VALUES (#{id}, #{userId}, #{seckillId}, #{productId}, #{orderPrice}, #{status})")
    int insertWithId(SeckillOrder order);

    @Select("SELECT id, user_id, seckill_id, product_id, order_price, status, created_at " +
            "FROM t_seckill_order WHERE id = #{orderId}")
    @ResultMap("seckillOrderMap")
    SeckillOrder selectById(@Param("orderId") Long orderId);

    @Select("SELECT id, user_id, seckill_id, product_id, order_price, status, created_at " +
            "FROM t_seckill_order WHERE user_id = #{userId} ORDER BY created_at DESC")
    @ResultMap("seckillOrderMap")
    java.util.List<SeckillOrder> selectByUserId(@Param("userId") Long userId);
}
