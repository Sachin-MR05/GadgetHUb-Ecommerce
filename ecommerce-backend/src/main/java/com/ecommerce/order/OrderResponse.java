package com.ecommerce.order;

import com.ecommerce.model.Order;

import java.time.LocalDateTime;
import java.util.List;

public class OrderResponse {

    private Long id;
    private Long userId;
    private String orderSource;
    private String status;
    private double totalAmount;
    private LocalDateTime createdAt;
    private String paypalOrderId;
    private String paypalPaymentId;
    private String razorpayOrderId;
    private String razorpayPaymentId;
    private List<OrderItemResponse> items;

    public OrderResponse() {
    }

    public OrderResponse(Long id, String status, double totalAmount, LocalDateTime createdAt,
                         String paypalOrderId, String paypalPaymentId, List<OrderItemResponse> items) {
        this.id = id;
        this.status = status;
        this.totalAmount = totalAmount;
        this.createdAt = createdAt;
        this.paypalOrderId = paypalOrderId;
        this.paypalPaymentId = paypalPaymentId;
        this.razorpayOrderId = paypalOrderId;
        this.razorpayPaymentId = paypalPaymentId;
        this.items = items;
    }

    public OrderResponse(Long id, String status, double totalAmount, LocalDateTime createdAt,
                         String paypalOrderId, String paypalPaymentId,
                         String razorpayOrderId, String razorpayPaymentId,
                         List<OrderItemResponse> items) {
        this.id = id;
        this.status = status;
        this.totalAmount = totalAmount;
        this.createdAt = createdAt;
        this.paypalOrderId = paypalOrderId;
        this.paypalPaymentId = paypalPaymentId;
        this.razorpayOrderId = razorpayOrderId != null ? razorpayOrderId : paypalOrderId;
        this.razorpayPaymentId = razorpayPaymentId != null ? razorpayPaymentId : paypalPaymentId;
        this.items = items;
    }

    public static OrderResponse fromEntity(Order order, String baseUrl) {
        List<OrderItemResponse> items = order.getItems().stream()
                .map(item -> OrderItemResponse.fromEntity(item, baseUrl))
                .toList();

        OrderResponse resp = new OrderResponse(
                order.getId(),
                order.getStatus().name(),
                order.getTotalAmount(),
                order.getCreatedAt(),
                order.getPaypalOrderId(),
                order.getPaypalPaymentId(),
                order.getRazorpayOrderId(),
                order.getRazorpayPaymentId(),
                items
        );
        resp.setUserId(order.getUserId());
        resp.setOrderSource(order.getOrderSource());
        return resp;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getOrderSource() {
        return orderSource;
    }

    public void setOrderSource(String orderSource) {
        this.orderSource = orderSource;
    }

    public String getStatus() {
        return status;
    }

    public double getTotalAmount() {
        return totalAmount;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public String getPaypalOrderId() {
        return paypalOrderId != null ? paypalOrderId : razorpayOrderId;
    }

    public String getPaypalPaymentId() {
        return paypalPaymentId != null ? paypalPaymentId : razorpayPaymentId;
    }

    public String getRazorpayOrderId() {
        return razorpayOrderId != null ? razorpayOrderId : paypalOrderId;
    }

    public String getRazorpayPaymentId() {
        return razorpayPaymentId != null ? razorpayPaymentId : razorpayPaymentId;
    }

    public List<OrderItemResponse> getItems() {
        return items;
    }
}
