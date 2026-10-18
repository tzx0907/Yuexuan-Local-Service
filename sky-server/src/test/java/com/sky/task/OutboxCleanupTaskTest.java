package com.sky.task;

import com.sky.mapper.OutboxEventMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxCleanupTaskTest {
    @Mock
    private OutboxEventMapper outboxEventMapper;

    @InjectMocks
    private OutboxCleanupTask outboxCleanupTask;

    @Test
    void shouldDeleteSentEventsOlderThanRetentionPeriod() {
        when(outboxEventMapper.deleteSentBefore(any(LocalDateTime.class), anyInt())).thenReturn(2);

        LocalDateTime beforeRun = LocalDateTime.now().minusDays(OutboxCleanupTask.RETENTION_DAYS);
        outboxCleanupTask.cleanSentEvents();
        LocalDateTime afterRun = LocalDateTime.now().minusDays(OutboxCleanupTask.RETENTION_DAYS);

        ArgumentCaptor<LocalDateTime> cutoffCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(outboxEventMapper).deleteSentBefore(cutoffCaptor.capture(), anyInt());
        assertTrue(!cutoffCaptor.getValue().isBefore(beforeRun)
                && !cutoffCaptor.getValue().isAfter(afterRun));
    }
}