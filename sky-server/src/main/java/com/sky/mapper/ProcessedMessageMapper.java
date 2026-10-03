package com.sky.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

@Mapper
public interface ProcessedMessageMapper {
    /** @return 1 表示本消费者首次处理该事件，0 表示重复投递。 */
    @Insert("insert ignore into processed_message(event_id, consumer_name, processed_at) "
            + "values(#{eventId}, #{consumerName}, #{processedAt})")
    int insertIgnore(@Param("eventId") String eventId,
                     @Param("consumerName") String consumerName,
                     @Param("processedAt") LocalDateTime processedAt);

    /** @return 大于 0 表示该事件已被本消费者成功处理过，可直接跳过。 */
    @Select("select count(*) from processed_message where event_id = #{eventId} "
            + "and consumer_name = #{consumerName}")
    int exists(@Param("eventId") String eventId,
               @Param("consumerName") String consumerName);
}
