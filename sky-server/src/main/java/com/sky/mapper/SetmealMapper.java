package com.sky.mapper;

import com.github.pagehelper.Page;
import com.sky.annotation.AutoFill;
import com.sky.dto.SetmealPageQueryDTO;
import com.sky.entity.Setmeal;
import com.sky.enumeration.OperationType;
import com.sky.vo.DishItemVO;
import com.sky.vo.SetmealVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SetmealMapper {

    @Select("select count(id) from setmeal where category_id = #{categoryId}")
    Integer countByCategoryId(Long id);

    @Select("select count(id) from setmeal_dish where dish_id = #{dishId}")
    Long countByDishId(Long dishId);

    List<Setmeal> list(Setmeal setmeal);

    // 用户端“查看组合内容”接口使用 DishItemVO；旧小程序弹窗只展示 name，
    // 因此在查询阶段把 SKU 规格快照拼进名称，避免规格字段被前端编译产物忽略。
    @Select("select concat(sd.name, '（', coalesce(sd.sku_snapshot, '默认规格'), '）') as name, sd.copies, d.image, d.description from setmeal_dish sd left join dish d on sd.dish_id = d.id where sd.setmeal_id = #{id}")
    List<DishItemVO> getDishItemBySetmealId(Long id);

    @AutoFill(OperationType.INSERT)
    void insert(Setmeal setmeal);

    Page<SetmealVO> pageQuery(SetmealPageQueryDTO setmealPageQueryDTO);

    @Select("select * from setmeal where id = #{id}")
    Setmeal getById(Long id);

    @AutoFill(OperationType.UPDATE)
    void update(Setmeal setmeal);

    void delete(List<Long> ids);
    /**
     * 根据条件统计套餐数量
     * @param status
     * @param categoryId
     * @return
     */
    Integer countByMap(@Param("status") Integer status,
                       @Param("categoryId") Integer categoryId);
}

