package com.example.demo.mapper;

import com.example.demo.model.entity.Order;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface OrderMapper {

    @Insert("INSERT INTO t_order (order_no, user_id, activity_id, product_id, quantity, total_amount, status, expire_time) " +
            "VALUES (#{orderNo}, #{userId}, #{activityId}, #{productId}, #{quantity}, #{totalAmount}, #{status}, #{expireTime})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Order order);

    @Select("SELECT * FROM t_order WHERE order_no = #{orderNo}")
    Order selectByOrderNo(String orderNo);

    @Select("SELECT * FROM t_order WHERE user_id = #{userId} ORDER BY created_at DESC LIMIT #{offset}, #{limit}")
    List<Order> selectByUserId(@Param("userId") Long userId,
                               @Param("offset") int offset,
                               @Param("limit") int limit);

    @Select("SELECT * FROM t_order WHERE user_id = #{userId} AND status = #{status} ORDER BY created_at DESC LIMIT #{offset}, #{limit}")
    List<Order> selectByUserIdAndStatus(@Param("userId") Long userId,
                                        @Param("status") Integer status,
                                        @Param("offset") int offset,
                                        @Param("limit") int limit);

    @Update("UPDATE t_order SET status = #{newStatus}, updated_at = NOW() WHERE order_no = #{orderNo} AND status = #{oldStatus}")
    int updateStatus(@Param("orderNo") String orderNo,
                     @Param("oldStatus") Integer oldStatus,
                     @Param("newStatus") Integer newStatus);

    @Update("UPDATE t_order SET status = #{newStatus}, pay_time = NOW(), updated_at = NOW() WHERE order_no = #{orderNo} AND status = #{oldStatus}")
    int updateStatusWithPayTime(@Param("orderNo") String orderNo,
                                @Param("oldStatus") Integer oldStatus,
                                @Param("newStatus") Integer newStatus);

    @Select("SELECT COUNT(*) FROM t_order WHERE user_id = #{userId} AND activity_id = #{activityId} AND status IN (0, 1)")
    int countByUserAndActivity(@Param("userId") Long userId, @Param("activityId") Long activityId);

    @Select("SELECT * FROM t_order WHERE status = 0 AND expire_time <= NOW() LIMIT #{limit}")
    List<Order> selectExpiredOrders(@Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM t_order WHERE order_no = #{orderNo}")
    int countByOrderNo(String orderNo);

    @Select("SELECT COUNT(*) FROM t_order WHERE activity_id = #{activityId} AND status IN (0, 1)")
    int countByActivityId(Long activityId);
}
