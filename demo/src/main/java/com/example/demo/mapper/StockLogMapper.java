package com.example.demo.mapper;

import com.example.demo.model.entity.StockLog;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface StockLogMapper {

    @Insert("INSERT INTO t_stock_log (activity_id, order_no, user_id, quantity, type) " +
            "VALUES (#{activityId}, #{orderNo}, #{userId}, #{quantity}, #{type})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(StockLog stockLog);

    @Select("SELECT * FROM t_stock_log WHERE activity_id = #{activityId} ORDER BY created_at DESC")
    List<StockLog> selectByActivityId(Long activityId);
}
