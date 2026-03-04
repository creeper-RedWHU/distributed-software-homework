package com.example.demo.mapper;

import com.example.demo.model.entity.SeckillActivity;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface SeckillActivityMapper {

    @Select("SELECT * FROM t_seckill_activity WHERE id = #{id}")
    SeckillActivity selectById(Long id);

    @Select("SELECT * FROM t_seckill_activity WHERE status IN (0,1) AND end_time > NOW() ORDER BY start_time ASC")
    List<SeckillActivity> selectActiveActivities();

    @Select("SELECT * FROM t_seckill_activity WHERE status = #{status} ORDER BY start_time ASC LIMIT #{offset}, #{limit}")
    List<SeckillActivity> selectByStatus(@Param("status") Integer status,
                                         @Param("offset") int offset,
                                         @Param("limit") int limit);

    @Select("SELECT * FROM t_seckill_activity WHERE status = 0 AND start_time <= #{time}")
    List<SeckillActivity> selectActivitiesToStart(@Param("time") LocalDateTime time);

    @Select("SELECT * FROM t_seckill_activity WHERE status = 1 AND end_time <= #{time}")
    List<SeckillActivity> selectActivitiesToEnd(@Param("time") LocalDateTime time);

    @Insert("INSERT INTO t_seckill_activity (name, product_id, seckill_price, total_stock, available_stock, " +
            "start_time, end_time, status, limit_per_user, version) " +
            "VALUES (#{name}, #{productId}, #{seckillPrice}, #{totalStock}, #{availableStock}, " +
            "#{startTime}, #{endTime}, #{status}, #{limitPerUser}, 0)")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(SeckillActivity activity);

    @Update("UPDATE t_seckill_activity SET status = #{status} WHERE id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

    @Update("UPDATE t_seckill_activity SET available_stock = available_stock - #{quantity}, " +
            "version = version + 1 WHERE id = #{activityId} AND available_stock >= #{quantity} AND version = #{version}")
    int deductStockOptimistic(@Param("activityId") Long activityId,
                              @Param("quantity") Integer quantity,
                              @Param("version") Integer version);

    @Update("UPDATE t_seckill_activity SET available_stock = available_stock + #{quantity}, " +
            "version = version + 1 WHERE id = #{activityId}")
    int rollbackStock(@Param("activityId") Long activityId, @Param("quantity") Integer quantity);
}
