package com.sky.service;

import com.sky.entity.Orders;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderStateMachineTest {

    @Test
    void shouldAllowTheNormalOrderLifecycle() {
        assertTrue(OrderStateMachine.canTransition(Orders.PENDING_PAYMENT, Orders.TO_BE_CONFIRMED));
        assertTrue(OrderStateMachine.canTransition(Orders.TO_BE_CONFIRMED, Orders.CONFIRMED));
        assertTrue(OrderStateMachine.canTransition(Orders.CONFIRMED, Orders.DELIVERY_IN_PROGRESS));
        assertTrue(OrderStateMachine.canTransition(Orders.DELIVERY_IN_PROGRESS, Orders.COMPLETED));
    }

    @Test
    void shouldAllowCancellationOnlyBeforeDelivery() {
        assertTrue(OrderStateMachine.canTransition(Orders.PENDING_PAYMENT, Orders.CANCELLED));
        assertTrue(OrderStateMachine.canTransition(Orders.TO_BE_CONFIRMED, Orders.CANCELLED));
        assertTrue(OrderStateMachine.canTransition(Orders.CONFIRMED, Orders.CANCELLED));
        assertFalse(OrderStateMachine.canTransition(Orders.DELIVERY_IN_PROGRESS, Orders.CANCELLED));
    }

    @Test
    void shouldRejectIllegalTransitions() {
        assertFalse(OrderStateMachine.canTransition(Orders.PENDING_PAYMENT, Orders.COMPLETED));
        assertFalse(OrderStateMachine.canTransition(Orders.COMPLETED, Orders.DELIVERY_IN_PROGRESS));
        assertFalse(OrderStateMachine.canTransition(Orders.CANCELLED, Orders.CONFIRMED));
    }
}
