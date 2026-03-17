package com.example.demo.mapper;

import com.example.demo.model.entity.SeckillProduct;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface SeckillProductMapper {

    @Select("SELECT id, product_id, seckill_price, seckill_stock, start_time, end_time, status, created_at " +
            "FROM t_seckill_product WHERE id = #{id}")
    @Results(id = "seckillProductMap", value = {
            @Result(property = "productId", column = "product_id"),
            @Result(property = "seckillPrice", column = "seckill_price"),
            @Result(property = "seckillStock", column = "seckill_stock"),
            @Result(property = "startTime", column = "start_time"),
            @Result(property = "endTime", column = "end_time"),
            @Result(property = "createdAt", column = "created_at")
    })
    SeckillProduct selectById(Long id);

    @Select("SELECT id, product_id, seckill_price, seckill_stock, start_time, end_time, status, created_at " +
            "FROM t_seckill_product WHERE status = 1 ORDER BY start_time DESC")
    @ResultMap("seckillProductMap")
    List<SeckillProduct> selectActiveList();

    @Select("SELECT id, product_id, seckill_price, seckill_stock, start_time, end_time, status, created_at " +
            "FROM t_seckill_product ORDER BY created_at DESC")
    @ResultMap("seckillProductMap")
    List<SeckillProduct> selectAll();

    @Update("UPDATE t_seckill_product SET seckill_stock = seckill_stock - 1 WHERE id = #{id} AND seckill_stock > 0")
    int decrStock(Long id);

    @Insert("INSERT INTO t_seckill_product (product_id, seckill_price, seckill_stock, start_time, end_time, status) " +
            "VALUES (#{productId}, #{seckillPrice}, #{seckillStock}, #{startTime}, #{endTime}, #{status})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(SeckillProduct seckillProduct);
}
