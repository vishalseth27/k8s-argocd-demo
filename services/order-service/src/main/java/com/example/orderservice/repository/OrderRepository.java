package com.example.orderservice.repository;

import com.example.orderservice.model.Order;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Repository
public class OrderRepository {
    private final Map<Long, Order> orders = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    public OrderRepository() {
        // Initialize with some sample data
        Order order1 = new Order(1L, 1L, "Laptop", 1, new BigDecimal("999.99"), "COMPLETED", LocalDateTime.now().minusDays(2));
        Order order2 = new Order(2L, 1L, "Mouse", 2, new BigDecimal("49.98"), "SHIPPED", LocalDateTime.now().minusDays(1));
        Order order3 = new Order(3L, 2L, "Keyboard", 1, new BigDecimal("79.99"), "PENDING", LocalDateTime.now());

        orders.put(order1.getId(), order1);
        orders.put(order2.getId(), order2);
        orders.put(order3.getId(), order3);

        idGenerator.set(4L);
    }

    public List<Order> findAll() {
        return new ArrayList<>(orders.values());
    }

    public Optional<Order> findById(Long id) {
        return Optional.ofNullable(orders.get(id));
    }

    public List<Order> findByUserId(Long userId) {
        return orders.values().stream()
                .filter(order -> order.getUserId().equals(userId))
                .collect(Collectors.toList());
    }

    public Order save(Order order) {
        if (order.getId() == null) {
            order.setId(idGenerator.getAndIncrement());
        }
        if (order.getCreatedAt() == null) {
            order.setCreatedAt(LocalDateTime.now());
        }
        orders.put(order.getId(), order);
        return order;
    }

    public void deleteById(Long id) {
        orders.remove(id);
    }
}
