package com.sky.mapper;

import com.sky.entity.ShoppingCart;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ShoppingCartMapper {
    List<ShoppingCart> list(ShoppingCart shoppingCart);
    @Update("update shopping_cart set number = #{number} where id = #{id}")
    void update(ShoppingCart cart);
    @Insert("insert into shopping_cart (user_id, dish_id, sku_id, flash_sale_activity_id, setmeal_id, dish_flavor, name, amount, image, number, create_time) " +
            "values (#{userId}, #{dishId}, #{skuId}, #{flashSaleActivityId}, #{setmealId}, #{dishFlavor}, #{name}, #{amount}, #{image}, #{number}, #{createTime})")
    void insert(ShoppingCart shoppingCart);

    @Delete("delete from shopping_cart where id = #{id}")
    void delete(Long id);

    @Delete("delete from shopping_cart where user_id = #{currentId}")
    void cleanByUserId(Long currentId);

    /** 清除创建时间早于截止时间的长期遗留购物车。 */
    @Delete("delete from shopping_cart where create_time < #{cutoff}")
    int deleteExpired(java.time.LocalDateTime cutoff);
}
