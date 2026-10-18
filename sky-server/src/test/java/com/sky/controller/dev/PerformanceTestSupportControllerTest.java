package com.sky.controller.dev;

import com.sky.entity.User;
import com.sky.mapper.UserMapper;
import com.sky.properties.JwtProperties;
import com.sky.result.Result;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PerformanceTestSupportControllerTest {

    @Test
    void shouldCreateDedicatedUsersAndReturnK6PayloadWithoutLoggingTokens() {
        UserMapper userMapper = mock(UserMapper.class);
        JwtProperties jwtProperties = jwtProperties();
        when(userMapper.getByOpenid("perf-k6-stock100-001")).thenReturn(user(101L));
        when(userMapper.getByOpenid("perf-k6-stock100-002")).thenReturn(null);
        // MyBatis 会在 insert 后回填主键；mock 中模拟同样结果。
        org.mockito.Mockito.doAnswer(invocation -> {
            ((User) invocation.getArgument(0)).setId(102L);
            return null;
        }).when(userMapper).insert(any(User.class));

        PerformanceTestSupportController controller = new PerformanceTestSupportController(userMapper, jwtProperties);
        Result<List<Map<String, Object>>> result = controller.prepareK6Users("stock100", 2, 50L, 37L);

        assertEquals(1, result.getCode());
        assertEquals(2, result.getData().size());
        assertEquals(50L, result.getData().get(0).get("dishId"));
        assertEquals(37L, result.getData().get(0).get("skuId"));
        assertTrue(((String) result.getData().get(0).get("token")).split("\\.").length == 3);
        assertFalse(((String) result.getData().get(1).get("token")).isBlank());
        verify(userMapper).insert(any(User.class));
    }

    @Test
    void shouldRejectUnsafeRunIdBeforeDatabaseAccess() {
        UserMapper userMapper = mock(UserMapper.class);
        PerformanceTestSupportController controller = new PerformanceTestSupportController(userMapper, jwtProperties());

        assertThrows(IllegalArgumentException.class,
                () -> controller.prepareK6Users("../unsafe", 1, 50L, 37L));
        verify(userMapper, times(0)).getByOpenid(any());
    }

    private User user(Long id) {
        return User.builder().id(id).openid("existing-test-user").build();
    }

    private JwtProperties jwtProperties() {
        JwtProperties properties = new JwtProperties();
        properties.setUserSecretKey("test-only-jwt-secret-that-is-long-enough");
        properties.setUserTtl(60_000L);
        return properties;
    }
}
