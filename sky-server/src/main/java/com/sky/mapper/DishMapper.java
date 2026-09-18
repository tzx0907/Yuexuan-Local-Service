package com.sky.mapper;

import com.github.pagehelper.Page;
import com.sky.annotation.AutoFill;
import com.sky.dto.DishPageQueryDTO;
import com.sky.entity.Dish;
import com.sky.enumeration.OperationType;
import com.sky.vo.DishVO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DishMapper {

    /**
     * 根据分类id查询菜品数量
     * @param categoryId
     * @return
     */
    @Select("select count(id) from dish where category_id = #{categoryId}")
    Integer countByCategoryId(Long categoryId);

    /**
     * 新增菜品数据
     * @param dish
     */
    @AutoFill(OperationType.INSERT)
    void insert(Dish dish);

    /**
     * 菜品分页查询
     * @param dishPageQueryDTO
     * @return
     */

    Page<DishVO> pageQuery(DishPageQueryDTO dishPageQueryDTO);
    /**
     * 根据id查询菜品和对应的口味数据
     * @param id
     * @return
     */
    @Select("select * from dish where id = #{id}")
    Dish getById(Long id);

    /**
     * 根据id查询菜品数据
     * @param dish
     * @return
     */
    @AutoFill(OperationType.UPDATE)
    void update(Dish dish);

    /**
     * 仅在商品仍上架且库存充足时扣减库存，返回受影响行数。
     */
    int decrementStock(@Param("dishId") Long dishId, @Param("quantity") Integer quantity);

    /**
     * 根据条件查询菜品列表
     * @param dish
     * @return
     */
    List<Dish> list(Dish dish);
    /**
     * 根据id删除菜品数据
     * @param dish
     */
    @Delete("delete from dish where id = #{id}")
    void delete(Dish dish);
    /**
     * 根据条件统计菜品数量
     * @param status
     * @param categoryId
     * @return
     */
    Integer countByMap(@Param("status") Integer status,
                       @Param("categoryId") Integer categoryId);
}
