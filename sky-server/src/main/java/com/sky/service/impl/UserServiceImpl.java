package com.sky.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.sky.constant.MessageConstant;
import com.sky.dto.UserLoginDTO;
import com.sky.entity.User;
import com.sky.exception.LoginFailedException;
import com.sky.mapper.UserMapper;
import com.sky.properties.WeChatProperties;
import com.sky.service.UserService;
import com.sky.utils.HttpClientUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class UserServiceImpl implements UserService {
    //微信登录接口
    public static final String WX_LOGIN_URL = "https://api.weixin.qq.com/sns/jscode2session";
    @Autowired
    private WeChatProperties weChatProperties;
    @Autowired
    private UserMapper userMapper;
    /**
     * 微信登录
     * @param loginDTO
     * @return
     */
    @Override
    public User wxLogin(UserLoginDTO loginDTO) {
        log.info("微信登录，参数：{}", loginDTO);
        String openid = getOpenId(loginDTO);
        //判断openid是否为空
        if(openid == null){
            throw new LoginFailedException(MessageConstant.LOGIN_FAILED);
        }
        //判断对系统来说是否是新用户
        User user = userMapper.getByOpenid(openid);
        //如果是新用户，自动完成注册
        if(user == null){
            user = User.builder()
                    .openid(openid)
                    .createTime(LocalDateTime.now())
                    .build();
            userMapper.insert(user);
        }
        //返回用户信息
        return user;
    }

    private String getOpenId(UserLoginDTO loginDTO) {
        if (weChatProperties.isMockLogin()) {
            String openid = weChatProperties.getMockOpenid();
            if (openid == null || openid.isBlank()) {
                log.error("本地模拟登录未配置 sky.wechat.mock-openid");
                return null;
            }
            log.info("使用本地模拟微信登录，openid={}", openid);
            return openid;
        }

        //调用微信接口，获取openid
        Map<String, String> hashmap = new HashMap<>();
        hashmap.put("appid", weChatProperties.getAppid());
        hashmap.put("secret", weChatProperties.getSecret());
        hashmap.put("js_code", loginDTO.getCode());
        hashmap.put("grant_type", "authorization_code");
        
        log.info("调用微信接口，参数：appid={}, secret={}, js_code={}", 
                weChatProperties.getAppid(), weChatProperties.getSecret(), loginDTO.getCode());
        
        String json = HttpClientUtil.doGet(WX_LOGIN_URL, hashmap);
        log.info("微信接口返回：{}", json);
        
        if (json == null || json.isBlank()) {
            log.error("微信接口未返回有效响应");
            return null;
        }
        JSONObject jsonObject = JSONObject.parseObject(json);
        
        // 检查是否有错误信息
        if (jsonObject.containsKey("errcode")) {
            Integer errcode = jsonObject.getInteger("errcode");
            String errmsg = jsonObject.getString("errmsg");
            log.error("微信接口返回错误，errcode={}, errmsg={}", errcode, errmsg);
            return null;
        }
        
        String openid = jsonObject.getString("openid");
        log.info("获取到openid：{}", openid);
        return openid;
    }
}
