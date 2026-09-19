package com.sky.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface ProcessedMessageMapper {
    /** @return 1 表示本消费者首次处理该事件，0 表示重复投递。 */
    @Insert("insert ignore into processed_message(event_id, consumer_name, processed_at) "
            + "values(#{eventId}, #{consumerName}, #{processedAt})")
    int insertIgnore(@Param("eventId") String eventId,
                     @Param("consumerName") String consumerName,
                     @Param("processedAt") LocalDateTime processedAt);
}
