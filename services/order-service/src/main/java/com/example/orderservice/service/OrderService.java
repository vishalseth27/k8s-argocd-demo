package com.example.orderservice.service;

import com.example.orderservice.client.UserServiceClient;
import com.example.orderservice.dto.OrderDTO;
import com.example.orderservice.dto.UserDTO;
import com.example.orderservice.model.Order;
import com.example.orderservice.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class OrderService {
    private static final Logger log = LoggerFactory.getLogger(OrderService.class);
    private final OrderRepository orderRepository;
    private final UserServiceClient userServiceClient;

    public OrderService(OrderRepository orderRepository, UserServiceClient userServiceClient) {
        this.orderRepository = orderRepository;
        this.userServiceClient = userServiceClient;
    }

    public List<Order> getAllOrders() {
        log.info("Fetching all orders");
        return orderRepository.findAll();
    }

    public Optional<Order> getOrderById(Long id) {
        log.info("Fetching order with id: {}", id);
        return orderRepository.findById(id);
    }

    public Optional<OrderDTO> getOrderWithUserById(Long id) {
        log.info("Fetching order with user details for id: {}", id);

        return orderRepository.findById(id)
                .map(order -> {
                    OrderDTO orderDTO = new OrderDTO();
                    orderDTO.setOrder(order);

                    // Fetch user details from user-service
                    userServiceClient.getUserById(order.getUserId())
                            .ifPresentOrElse(
                                    orderDTO::setUser,
                                    () -> log.warn("User not found for userId: {}", order.getUserId())
                            );

                    return orderDTO;
                });
    }

    public List<OrderDTO> getAllOrdersWithUsers() {
        log.info("Fetching all orders with user details");

        return orderRepository.findAll().stream()
                .map(order -> {
                    OrderDTO orderDTO = new OrderDTO();
                    orderDTO.setOrder(order);

                    userServiceClient.getUserById(order.getUserId())
                            .ifPresent(orderDTO::setUser);

                    return orderDTO;
                })
                .collect(Collectors.toList());
    }

    public List<Order> getOrdersByUserId(Long userId) {
        log.info("Fetching orders for userId: {}", userId);
        return orderRepository.findByUserId(userId);
    }

    public Order createOrder(Order order) {
        log.info("Creating new order for userId: {}", order.getUserId());

        // Validate user exists
        Optional<UserDTO> user = userServiceClient.getUserById(order.getUserId());
        if (user.isEmpty()) {
            log.error("Cannot create order: User not found with id: {}", order.getUserId());
            throw new IllegalArgumentException("User not found with id: " + order.getUserId());
        }

        if (order.getStatus() == null) {
            order.setStatus("PENDING");
        }

        return orderRepository.save(order);
    }

    public Optional<Order> updateOrder(Long id, Order orderDetails) {
        log.info("Updating order with id: {}", id);

        return orderRepository.findById(id)
                .map(order -> {
                    order.setProductName(orderDetails.getProductName());
                    order.setQuantity(orderDetails.getQuantity());
                    order.setTotalAmount(orderDetails.getTotalAmount());
                    order.setStatus(orderDetails.getStatus());
                    return orderRepository.save(order);
                });
    }

    public boolean deleteOrder(Long id) {
        log.info("Deleting order with id: {}", id);

        if (orderRepository.findById(id).isPresent()) {
            orderRepository.deleteById(id);
            return true;
        }
        return false;
    }
}
