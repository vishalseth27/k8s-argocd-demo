package com.example.orderservice.dto;

import com.example.orderservice.model.Order;

public class OrderDTO {
    private Order order;
    private UserDTO user;

    public OrderDTO() {
    }

    public OrderDTO(Order order, UserDTO user) {
        this.order = order;
        this.user = user;
    }

    public Order getOrder() {
        return order;
    }

    public void setOrder(Order order) {
        this.order = order;
    }

    public UserDTO getUser() {
        return user;
    }

    public void setUser(UserDTO user) {
        this.user = user;
    }
}
