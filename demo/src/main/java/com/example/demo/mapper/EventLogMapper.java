package com.example.demo.mapper;

import com.example.demo.model.entity.EventLog;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface EventLogMapper {

    @Insert("INSERT INTO t_event_log (event_type, event_data, source, status, retry_count) " +
            "VALUES (#{eventType}, #{eventData}, #{source}, #{status}, #{retryCount})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(EventLog eventLog);

    @Update("UPDATE t_event_log SET status = #{status}, processed_at = NOW() WHERE id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

    @Select("SELECT * FROM t_event_log WHERE status = 0 ORDER BY created_at ASC LIMIT #{limit}")
    List<EventLog> selectPendingEvents(@Param("limit") int limit);
}
