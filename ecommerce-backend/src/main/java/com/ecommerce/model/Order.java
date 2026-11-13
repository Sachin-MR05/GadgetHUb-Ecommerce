package com.ecommerce.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * A checked-out order, paid for through Razorpay or PayPal.
 * Created in CREATED state when the user hits "Checkout", then flips
 * to PAID (or FAILED) once the payment is verified.
 */
@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private double totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Column(name = "order_source")
    private String orderSource;

    @Column(name = "paypal_order_id")
    private String paypalOrderId;

    @Column(name = "paypal_payment_id")
    private String paypalPaymentId;

    @Column(name = "razorpay_order_id")
    private String razorpayOrderId;

    @Column(name = "razorpay_payment_id")
    private String razorpayPaymentId;

    @Column(name = "razorpay_signature")
    private String razorpaySignature;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<OrderItem> items = new ArrayList<>();

    public Order() {
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null) {
            status = OrderStatus.CREATED;
        }
        if (orderSource == null) {
            orderSource = (userId != null && userId.equals(1L)) ? "AI_AGENT" : "DIRECT_APP";
        }
    }

    public void addItem(OrderItem item) {
        item.setOrder(this);
        items.add(item);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public double getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(double totalAmount) {
        this.totalAmount = totalAmount;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public String getOrderSource() {
        if (orderSource != null && !orderSource.isBlank()) {
            return orderSource;
        }
        return (userId != null && userId.equals(1L)) ? "AI_AGENT" : "DIRECT_APP";
    }

    public void setOrderSource(String orderSource) {
        this.orderSource = orderSource;
    }

    public String getPaypalOrderId() {
        return paypalOrderId != null ? paypalOrderId : razorpayOrderId;
    }

    public void setPaypalOrderId(String paypalOrderId) {
        this.paypalOrderId = paypalOrderId;
        if (this.razorpayOrderId == null) {
            this.razorpayOrderId = paypalOrderId;
        }
    }

    public String getPaypalPaymentId() {
        return paypalPaymentId != null ? paypalPaymentId : razorpayPaymentId;
    }

    public void setPaypalPaymentId(String paypalPaymentId) {
        this.paypalPaymentId = paypalPaymentId;
        if (this.razorpayPaymentId == null) {
            this.razorpayPaymentId = paypalPaymentId;
        }
    }

    public String getRazorpayOrderId() {
        return paypalOrderId != null ? paypalOrderId : razorpayOrderId;
    }

    public void setRazorpayOrderId(String razorpayOrderId) {
        this.razorpayOrderId = razorpayOrderId;
        if (this.paypalOrderId == null) {
            this.paypalOrderId = razorpayOrderId;
        }
    }

    public String getRazorpayPaymentId() {
        return paypalPaymentId != null ? paypalPaymentId : razorpayPaymentId;
    }

    public void setRazorpayPaymentId(String razorpayPaymentId) {
        this.razorpayPaymentId = razorpayPaymentId;
        if (this.paypalPaymentId == null) {
            this.paypalPaymentId = razorpayPaymentId;
        }
    }

    public String getRazorpaySignature() {
        return razorpaySignature;
    }

    public void setRazorpaySignature(String razorpaySignature) {
        this.razorpaySignature = razorpaySignature;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public List<OrderItem> getItems() {
        return items;
    }

    public void setItems(List<OrderItem> items) {
        this.items = items;
    }
}
