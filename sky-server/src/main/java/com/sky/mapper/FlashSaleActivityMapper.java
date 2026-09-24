package com.sky.mapper;

import com.sky.entity.FlashSaleActivity;
import com.sky.vo.FlashSaleActivityVO;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface FlashSaleActivityMapper {
    @Insert("insert into flash_sale_activity (sku_id,sale_price,activity_stock,sold_stock,per_user_limit,status,start_time,end_time,create_time,update_time) "
            + "values (#{skuId},#{salePrice},#{activityStock},0,#{perUserLimit},#{status},#{startTime},#{endTime},now(),now())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(FlashSaleActivity activity);

    @Update("update flash_sale_activity set sale_price=#{salePrice}, activity_stock=#{activityStock}, per_user_limit=#{perUserLimit}, status=#{status}, start_time=#{startTime}, end_time=#{endTime}, update_time=now() where id=#{id}")
    int update(FlashSaleActivity activity);

    @Select("select * from flash_sale_activity where id=#{id}")
    FlashSaleActivity getById(Long id);

    @Select("select f.*, (f.activity_stock - f.sold_stock) as remainingStock, p.dish_id as dishId, d.name as productName, d.image, p.spec_name as specName, p.spec_value as specValue, p.price as originalPrice, p.stock as skuStock "
            + "from flash_sale_activity f join product_sku p on p.id=f.sku_id join dish d on d.id=p.dish_id "
            + "where f.status=1 and f.start_time <= now() and f.end_time > now() and f.sold_stock < f.activity_stock and p.status=1 and d.status=1 order by f.start_time asc")
    List<FlashSaleActivityVO> listActive();

    @Select("select f.*, (f.activity_stock - f.sold_stock) as remainingStock, p.dish_id as dishId, d.name as productName, d.image, p.spec_name as specName, p.spec_value as specValue, p.price as originalPrice, p.stock as skuStock "
            + "from flash_sale_activity f join product_sku p on p.id=f.sku_id join dish d on d.id=p.dish_id order by f.create_time desc")
    List<FlashSaleActivityVO> listAll();

    @Update("update flash_sale_activity set status=#{status}, update_time=now() where id=#{id}")
    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

    /** 只在活动仍有效且有剩余配额时售出；这一条条件更新是活动库存防超卖的最终保障。 */
    @Update("update flash_sale_activity set sold_stock=sold_stock+#{quantity}, update_time=now() where id=#{activityId} and status=1 and start_time <= now() and end_time > now() and sold_stock + #{quantity} <= activity_stock")
    int decrementStock(@Param("activityId") Long activityId, @Param("quantity") Integer quantity);

    @Update("update flash_sale_activity set sold_stock=sold_stock-#{quantity}, update_time=now() where id=#{activityId} and sold_stock >= #{quantity}")
    int incrementStock(@Param("activityId") Long activityId, @Param("quantity") Integer quantity);
}
