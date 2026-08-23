package com.sky.mapper;

import com.sky.entity.User;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Mapper
public interface UserMapper {
    /**
     * 根据openid查询用户
     * @param openid
     * @return
     */
    @Select("select * from user where openid = #{openid}")
    User getByOpenid(String openid);
    /**
     * 插入数据
     * @param user
     */
    void insert(User user);

    @Select("select * from user where id = #{Id}")
    User getById(Long Id);

    Long getUser(
                 @Param("beginTime") LocalDateTime beginTime,
                 @Param("endTime") LocalDateTime endTime);

}
