package com.sky.service;

/** 在当前业务事务中保存消息，交由 Outbox 投递器异步发送。 */
public interface OutboxService {
    void saveOrderPaidEvent(Object event);

    void saveOrderCloseEvent(Object event);
}
