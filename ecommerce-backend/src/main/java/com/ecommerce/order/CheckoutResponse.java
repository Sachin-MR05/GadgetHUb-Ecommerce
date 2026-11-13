package com.ecommerce.order;

public class CheckoutResponse {

    private Long orderId;
    private String paypalOrderId;
    private String razorpayOrderId;
    private long amount;
    private double totalAmount;
    private String currency;
    private String clientId;
    private String keyId;
    private String approvalUrl;
    private String paymentLink;

    public CheckoutResponse() {
    }

    public CheckoutResponse(Long orderId, String paypalOrderId, long amount,
                            String currency, String clientId, String approvalUrl) {
        this.orderId = orderId;
        this.paypalOrderId = paypalOrderId;
        this.razorpayOrderId = paypalOrderId; // backwards compatibility
        this.amount = amount;
        this.totalAmount = amount / 100.0;
        this.currency = currency;
        this.clientId = clientId;
        this.keyId = clientId; // backwards compatibility
        this.approvalUrl = approvalUrl;
        this.paymentLink = approvalUrl; // backwards compatibility
    }

    public CheckoutResponse(Long orderId, String paypalOrderId, long amount, double totalAmount,
                            String currency, String clientId, String approvalUrl) {
        this.orderId = orderId;
        this.paypalOrderId = paypalOrderId;
        this.razorpayOrderId = paypalOrderId;
        this.amount = amount;
        this.totalAmount = totalAmount;
        this.currency = currency;
        this.clientId = clientId;
        this.keyId = clientId;
        this.approvalUrl = approvalUrl;
        this.paymentLink = approvalUrl;
    }

    public Long getOrderId() { return orderId; }
    public String getPaypalOrderId() { return paypalOrderId != null ? paypalOrderId : razorpayOrderId; }
    public String getRazorpayOrderId() { return razorpayOrderId != null ? razorpayOrderId : paypalOrderId; }
    public long getAmount() { return amount; }
    public double getTotalAmount() { return totalAmount; }
    public String getCurrency() { return currency; }
    public String getClientId() { return clientId != null ? clientId : keyId; }
    public String getKeyId() { return keyId != null ? keyId : clientId; }
    public String getApprovalUrl() { return approvalUrl != null ? approvalUrl : paymentLink; }
    public String getPaymentLink() { return paymentLink != null ? paymentLink : approvalUrl; }
}
