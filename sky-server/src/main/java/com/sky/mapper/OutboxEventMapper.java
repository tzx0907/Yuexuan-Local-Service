package com.sky.mapper;

import com.sky.entity.OutboxEvent;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface OutboxEventMapper {
    @Insert("insert into outbox_event(event_id,event_type,exchange_name,routing_key,payload,status,retry_count,next_attempt_time,created_time) "
            + "values(#{eventId},#{eventType},#{exchangeName},#{routingKey},#{payload},'PENDING',0,#{nextAttemptTime},now())")
    void insert(OutboxEvent event);

    @Select("select * from outbox_event where status in ('PENDING','RETRY') and next_attempt_time <= now() "
            + "order by id asc limit #{limit}")
    List<OutboxEvent> findSendable(@Param("limit") int limit);

    @Update("update outbox_event set status='SENDING', processing_time=now(), last_error=null "
            + "where id=#{id} and status in ('PENDING','RETRY')")
    int claim(Long id);

    @Update("update outbox_event set status='SENT', sent_time=now(), processing_time=null, last_error=null "
            + "where id=#{id} and status='SENDING'")
    int markSent(Long id);

    @Update("update outbox_event set status='RETRY', retry_count=retry_count+1, next_attempt_time=#{nextAttemptTime}, "
            + "processing_time=null, last_error=#{lastError} where id=#{id} and status='SENDING'")
    int markRetry(@Param("id") Long id, @Param("nextAttemptTime") LocalDateTime nextAttemptTime,
                  @Param("lastError") String lastError);

    @Update("update outbox_event set status='RETRY', processing_time=null, next_attempt_time=now(), "
            + "last_error='投递进程中断，已重新调度' where status='SENDING' and processing_time < #{staleBefore}")
    int recoverStaleSending(@Param("staleBefore") LocalDateTime staleBefore);
}
