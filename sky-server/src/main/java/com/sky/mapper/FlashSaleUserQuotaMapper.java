package com.sky.mapper;

import org.apache.ibatis.annotations.*;

@Mapper
public interface FlashSaleUserQuotaMapper {
    @Select("select reserved_quantity from flash_sale_user_quota where activity_id=#{activityId} and user_id=#{userId}")
    Integer getReservedQuantity(@Param("activityId") Long activityId, @Param("userId") Long userId);

    /**
     * INSERT 处理首次参与；重复键时仅在累计数不超过活动限购数时递增。
     * 返回 1=首次占用、2=累加占用、0=超出限购。
     */
    @Insert("insert into flash_sale_user_quota (activity_id,user_id,reserved_quantity,limit_quantity,update_time) "
            + "values (#{activityId},#{userId},#{quantity},#{limit},now()) "
            + "on duplicate key update reserved_quantity=if(reserved_quantity + values(reserved_quantity) <= limit_quantity, reserved_quantity + values(reserved_quantity), reserved_quantity), update_time=now()")
    int tryReserve(@Param("activityId") Long activityId, @Param("userId") Long userId,
                   @Param("quantity") Integer quantity, @Param("limit") Integer limit);

    @Update("update flash_sale_user_quota set reserved_quantity=reserved_quantity-#{quantity}, update_time=now() where activity_id=#{activityId} and user_id=#{userId} and reserved_quantity >= #{quantity}")
    int release(@Param("activityId") Long activityId, @Param("userId") Long userId, @Param("quantity") Integer quantity);
}
