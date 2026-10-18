package com.sky.controller.dev;

import com.sky.constant.JwtClaimsConstant;
import com.sky.entity.User;
import com.sky.mapper.UserMapper;
import com.sky.properties.JwtProperties;
import com.sky.result.Result;
import com.sky.utils.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 仅用于本地压测准备的开发接口。
 *
 * <p>它必须同时满足 {@code dev} profile 与 {@code sky.performance.enabled=true} 才会注册，
 * 不能作为普通用户注册或生产 Token 签发接口使用。返回值可直接保存为 k6 的
 * {@code vu-data.local.json}，该文件已被 Git 忽略。</p>
 */
@Slf4j
@RestController
@Profile("dev")
@ConditionalOnProperty(prefix = "sky.performance", name = "enabled", havingValue = "true")
@RequestMapping("/_dev/performance")
public class PerformanceTestSupportController {
    private static final int MAX_USERS_PER_REQUEST = 200;
    private static final Pattern RUN_ID_PATTERN = Pattern.compile("[A-Za-z0-9-]{1,32}");
    private static final String OPENID_PREFIX = "perf-k6-";

    private final UserMapper userMapper;
    private final JwtProperties jwtProperties;

    public PerformanceTestSupportController(UserMapper userMapper, JwtProperties jwtProperties) {
        this.userMapper = userMapper;
        this.jwtProperties = jwtProperties;
    }

    /**
     * 创建或复用同一轮压测的专用用户，并签发仅供本地 k6 使用的用户 JWT。
     * 返回的 data 是 k6 {@code vu-data.local.json} 所需数组，不记录 Token 日志。
     */
    @PostMapping("/k6-users")
    public Result<List<Map<String, Object>>> prepareK6Users(
            @RequestParam String runId,
            @RequestParam int count,
            @RequestParam Long dishId,
            @RequestParam Long skuId) {
        if (!RUN_ID_PATTERN.matcher(runId).matches()) {
            throw new IllegalArgumentException("runId 只能包含字母、数字和连字符，长度为 1 到 32");
        }
        if (count < 1 || count > MAX_USERS_PER_REQUEST) {
            throw new IllegalArgumentException("count 必须在 1 到 " + MAX_USERS_PER_REQUEST + " 之间");
        }
        if (dishId == null || skuId == null || dishId <= 0 || skuId <= 0) {
            throw new IllegalArgumentException("dishId 和 skuId 必须为正整数");
        }

        List<Map<String, Object>> users = new ArrayList<>(count);
        for (int index = 1; index <= count; index++) {
            String openid = OPENID_PREFIX + runId + "-" + String.format("%03d", index);
            User user = userMapper.getByOpenid(openid);
            if (user == null) {
                user = User.builder()
                        .openid(openid)
                        .name("压测用户" + index)
                        .createTime(LocalDateTime.now())
                        .build();
                userMapper.insert(user);
            }
            Map<String, Object> claims = new HashMap<>();
            claims.put(JwtClaimsConstant.USER_ID, user.getId());
            Map<String, Object> entry = new HashMap<>();
            entry.put("token", JwtUtil.createJWT(jwtProperties.getUserSecretKey(), jwtProperties.getUserTtl(), claims));
            entry.put("dishId", dishId);
            entry.put("skuId", skuId);
            users.add(entry);
        }
        log.info("已准备 k6 压测用户 runId={}, count={}, dishId={}, skuId={}", runId, count, dishId, skuId);
        return Result.success(users);
    }
}
