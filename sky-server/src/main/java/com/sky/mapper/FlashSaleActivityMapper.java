package com.sky.mapper;

import com.sky.entity.FlashSaleActivity;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface FlashSaleActivityMapper {
    @Insert("insert into flash_sale_activity (sku_id,sale_price,activity_stock,sold_stock,per_user_limit,status,start_time,end_time,create_time,update_time) "
            + "values (#{skuId},#{salePrice},#{activityStock},0,#{perUserLimit},#{status},#{startTime},#{endTime},now(),now())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(FlashSaleActivity activity);

    @Update("update flash_sale_activity set sale_price=#{salePrice}, activity_stock=#{activityStock}, per_user_limit=#{perUserLimit}, status=#{status}, start_time=#{startTime}, end_time=#{endTime}, update_time=now() where id=#{id}")
    int update(FlashSaleActivity activity);

    @Select("select * from flash_sale_activity where id=#{id}")
    FlashSaleActivity getById(Long id);

    @Select("select * from flash_sale_activity where status=1 and start_time <= now() and end_time > now() and sold_stock < activity_stock order by start_time asc")
    List<FlashSaleActivity> listActive();

    /** 只在活动仍有效且有剩余配额时售出；这一条条件更新是活动库存防超卖的最终保障。 */
    @Update("update flash_sale_activity set sold_stock=sold_stock+#{quantity}, update_time=now() where id=#{activityId} and status=1 and start_time <= now() and end_time > now() and sold_stock + #{quantity} <= activity_stock")
    int decrementStock(@Param("activityId") Long activityId, @Param("quantity") Integer quantity);

    @Update("update flash_sale_activity set sold_stock=sold_stock-#{quantity}, update_time=now() where id=#{activityId} and sold_stock >= #{quantity}")
    int incrementStock(@Param("activityId") Long activityId, @Param("quantity") Integer quantity);
}
