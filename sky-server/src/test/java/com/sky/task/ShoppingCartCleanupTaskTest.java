package com.sky.task;

import com.sky.mapper.ShoppingCartMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShoppingCartCleanupTaskTest {
    @Mock
    private ShoppingCartMapper shoppingCartMapper;

    @InjectMocks
    private ShoppingCartCleanupTask shoppingCartCleanupTask;

    @Test
    void shouldDeleteOnlyCartsOlderThanRetentionPeriod() {
        when(shoppingCartMapper.deleteExpired(any(LocalDateTime.class))).thenReturn(2);

        LocalDateTime beforeRun = LocalDateTime.now().minusDays(ShoppingCartCleanupTask.RETENTION_DAYS);
        shoppingCartCleanupTask.cleanExpiredShoppingCarts();
        LocalDateTime afterRun = LocalDateTime.now().minusDays(ShoppingCartCleanupTask.RETENTION_DAYS);

        ArgumentCaptor<LocalDateTime> cutoffCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(shoppingCartMapper).deleteExpired(cutoffCaptor.capture());
        assertTrue(!cutoffCaptor.getValue().isBefore(beforeRun)
                && !cutoffCaptor.getValue().isAfter(afterRun));
    }
}
