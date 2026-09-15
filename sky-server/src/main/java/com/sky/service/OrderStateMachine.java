package com.sky.service;

import com.sky.entity.Orders;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Defines the allowed state transitions for the order lifecycle.
 */
public final class OrderStateMachine {

    private static final Map<Integer, Set<Integer>> ALLOWED_TRANSITIONS;

    static {
        Map<Integer, Set<Integer>> transitions = new HashMap<>();
        transitions.put(Orders.PENDING_PAYMENT, allowedTargets(Orders.TO_BE_CONFIRMED, Orders.CANCELLED));
        transitions.put(Orders.TO_BE_CONFIRMED, allowedTargets(Orders.CONFIRMED, Orders.CANCELLED));
        transitions.put(Orders.CONFIRMED, allowedTargets(Orders.DELIVERY_IN_PROGRESS, Orders.CANCELLED));
        transitions.put(Orders.DELIVERY_IN_PROGRESS, allowedTargets(Orders.COMPLETED));
        ALLOWED_TRANSITIONS = Collections.unmodifiableMap(transitions);
    }

    private OrderStateMachine() {
    }

    private static Set<Integer> allowedTargets(Integer... statuses) {
        return Collections.unmodifiableSet(new HashSet<>(Arrays.asList(statuses)));
    }

    public static boolean canTransition(Integer fromStatus, Integer toStatus) {
        return ALLOWED_TRANSITIONS.getOrDefault(fromStatus, Collections.emptySet()).contains(toStatus);
    }
}
