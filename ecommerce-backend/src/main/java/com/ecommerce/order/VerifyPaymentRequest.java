package com.ecommerce.order;

public class VerifyPaymentRequest {

    private String paypalOrderId;
    private String paypalPaymentId;
    private String razorpayOrderId;
    private String razorpayPaymentId;
    private String razorpaySignature;

    public String getEffectiveOrderId() {
        if (paypalOrderId != null && !paypalOrderId.isBlank()) {
            return paypalOrderId;
        }
        return razorpayOrderId;
    }

    public String getEffectivePaymentId() {
        if (paypalPaymentId != null && !paypalPaymentId.isBlank()) {
            return paypalPaymentId;
        }
        return razorpayPaymentId;
    }

    public String getPaypalOrderId() {
        return paypalOrderId != null ? paypalOrderId : razorpayOrderId;
    }

    public void setPaypalOrderId(String paypalOrderId) {
        this.paypalOrderId = paypalOrderId;
    }

    public String getPaypalPaymentId() {
        return paypalPaymentId != null ? paypalPaymentId : razorpayPaymentId;
    }

    public void setPaypalPaymentId(String paypalPaymentId) {
        this.paypalPaymentId = paypalPaymentId;
    }

    public String getRazorpayOrderId() {
        return razorpayOrderId != null ? razorpayOrderId : paypalOrderId;
    }

    public void setRazorpayOrderId(String razorpayOrderId) {
        this.razorpayOrderId = razorpayOrderId;
    }

    public String getRazorpayPaymentId() {
        return razorpayPaymentId != null ? razorpayPaymentId : paypalPaymentId;
    }

    public void setRazorpayPaymentId(String razorpayPaymentId) {
        this.razorpayPaymentId = razorpayPaymentId;
    }

    public String getRazorpaySignature() {
        return razorpaySignature;
    }

    public void setRazorpaySignature(String razorpaySignature) {
        this.razorpaySignature = razorpaySignature;
    }
}
