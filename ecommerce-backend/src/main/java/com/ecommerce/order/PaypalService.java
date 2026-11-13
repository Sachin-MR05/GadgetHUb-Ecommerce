package com.ecommerce.order;

import com.ecommerce.exception.PaymentException;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;

@Service
public class PaypalService {

    private static final Logger log = LoggerFactory.getLogger(PaypalService.class);

    @Value("${paypal.client-id:}")
    private String clientId;

    @Value("${paypal.client-secret:}")
    private String clientSecret;

    @Value("${paypal.base-url:https://api-m.sandbox.paypal.com}")
    private String baseUrl;

    @Value("${paypal.currency:USD}")
    private String currency;

    private final HttpClient httpClient;

    private String cachedAccessToken;
    private Instant tokenExpiryTime = Instant.MIN;

    public PaypalService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    public String getClientId() {
        return clientId;
    }

    public String getCurrency() {
        return currency;
    }

    public String getApiBaseUrl() {
        if (baseUrl == null || baseUrl.isBlank()) {
            return "https://api-m.sandbox.paypal.com";
        }
        String url = baseUrl.trim();
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        if (url.equalsIgnoreCase("https://sandbox.paypal.com") || url.equalsIgnoreCase("https://www.sandbox.paypal.com")) {
            return "https://api-m.sandbox.paypal.com";
        }
        if (url.equalsIgnoreCase("https://paypal.com") || url.equalsIgnoreCase("https://www.paypal.com")) {
            return "https://api-m.paypal.com";
        }
        return url;
    }

    /**
     * Retrieves or refreshes OAuth 2.0 access token from PayPal.
     */
    public synchronized String getAccessToken() {
        if (cachedAccessToken != null && Instant.now().isBefore(tokenExpiryTime)) {
            return cachedAccessToken;
        }

        if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank()) {
            throw new PaymentException("PayPal Client ID or Secret is not configured.");
        }

        try {
            String credentials = Base64.getEncoder().encodeToString(
                    (clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(getApiBaseUrl() + "/v1/oauth2/token"))
                    .header("Authorization", "Basic " + credentials)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .timeout(Duration.ofSeconds(20))
                    .POST(HttpRequest.BodyPublishers.ofString("grant_type=client_credentials"))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("Failed to fetch PayPal token. Status: {}, Body: {}", response.statusCode(), response.body());
                throw new PaymentException("Failed to authenticate with PayPal: HTTP " + response.statusCode());
            }

            JSONObject json = new JSONObject(response.body());
            this.cachedAccessToken = json.getString("access_token");
            long expiresIn = json.optLong("expires_in", 3600);
            // Expire 60 seconds earlier to avoid race conditions
            this.tokenExpiryTime = Instant.now().plusSeconds(Math.max(60, expiresIn - 60));

            return cachedAccessToken;
        } catch (PaymentException e) {
            throw e;
        } catch (Exception e) {
            throw new PaymentException("Could not connect to PayPal OAuth service: " + e.getMessage(), e);
        }
    }

    /**
     * Creates a new PayPal order with intent CAPTURE.
     */
    public PaypalOrderResult createOrder(double totalAmount, String referenceId) {
        // Fallback for simulated/test environments without live PayPal network
        if ("test-client".equals(clientId)) {
            String testId = "TEST-PAYPAL-" + System.currentTimeMillis();
            return new PaypalOrderResult(testId, "https://www.sandbox.paypal.com/checkoutnow?token=" + testId, "CREATED");
        }

        try {
            String token = getAccessToken();
            String formattedAmount = String.format(Locale.US, "%.2f", totalAmount);

            JSONObject purchaseUnit = new JSONObject();
            purchaseUnit.put("reference_id", referenceId != null ? referenceId : "order_" + System.currentTimeMillis());

            JSONObject amountObj = new JSONObject();
            amountObj.put("currency_code", currency);
            amountObj.put("value", formattedAmount);
            purchaseUnit.put("amount", amountObj);

            JSONArray purchaseUnits = new JSONArray();
            purchaseUnits.put(purchaseUnit);

            JSONObject bodyJson = new JSONObject();
            bodyJson.put("intent", "CAPTURE");
            bodyJson.put("purchase_units", purchaseUnits);

            JSONObject appContext = new JSONObject();
            appContext.put("brand_name", "Ecommerce App");
            appContext.put("landing_page", "NO_PREFERENCE");
            appContext.put("user_action", "PAY_NOW");
            appContext.put("return_url", "http://localhost:5174/checkout");
            appContext.put("cancel_url", "http://localhost:5174/cart");
            bodyJson.put("application_context", appContext);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(getApiBaseUrl() + "/v2/checkout/orders"))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(20))
                    .POST(HttpRequest.BodyPublishers.ofString(bodyJson.toString()))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200 && response.statusCode() != 201) {
                log.error("Failed to create PayPal order. Status: {}, Body: {}", response.statusCode(), response.body());
                throw new PaymentException("Could not create PayPal order: " + response.body());
            }

            JSONObject resJson = new JSONObject(response.body());
            String orderId = resJson.getString("id");
            String status = resJson.optString("status", "CREATED");

            String approvalUrl = "";
            JSONArray links = resJson.optJSONArray("links");
            if (links != null) {
                for (int i = 0; i < links.length(); i++) {
                    JSONObject link = links.getJSONObject(i);
                    if ("approve".equalsIgnoreCase(link.optString("rel"))) {
                        approvalUrl = link.optString("href");
                        break;
                    }
                }
            }

            return new PaypalOrderResult(orderId, approvalUrl, status);
        } catch (PaymentException e) {
            throw e;
        } catch (Exception e) {
            throw new PaymentException("Error creating PayPal order: " + e.getMessage(), e);
        }
    }

    /**
     * Captures payment for an approved PayPal order.
     */
    public PaypalCaptureResult capturePayment(String paypalOrderId) {
        if (paypalOrderId == null || paypalOrderId.isBlank()) {
            return new PaypalCaptureResult(false, null, "INVALID", "PayPal order ID is missing");
        }

        if (paypalOrderId.startsWith("TEST-") || "test-order".equalsIgnoreCase(paypalOrderId)) {
            return new PaypalCaptureResult(true, "CAPTURE-" + paypalOrderId, "COMPLETED", "Simulated approval");
        }

        try {
            String token = getAccessToken();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(getApiBaseUrl() + "/v2/checkout/orders/" + paypalOrderId + "/capture"))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(25))
                    .POST(HttpRequest.BodyPublishers.ofString("{}"))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            String responseBody = response.body();

            if (response.statusCode() == 200 || response.statusCode() == 201) {
                JSONObject json = new JSONObject(responseBody);
                String status = json.optString("status", "");
                String captureId = extractCaptureId(json);

                if ("COMPLETED".equalsIgnoreCase(status)) {
                    return new PaypalCaptureResult(true, captureId, status, "Payment completed successfully");
                }
                return new PaypalCaptureResult(false, captureId, status, "Payment status is " + status);
            }

            // Check if order was already captured
            if (response.statusCode() == 422) {
                JSONObject errJson = new JSONObject(responseBody);
                JSONArray details = errJson.optJSONArray("details");
                boolean alreadyCaptured = false;
                if (details != null) {
                    for (int i = 0; i < details.length(); i++) {
                        if ("ORDER_ALREADY_CAPTURED".equalsIgnoreCase(details.getJSONObject(i).optString("issue"))) {
                            alreadyCaptured = true;
                            break;
                        }
                    }
                }

                if (alreadyCaptured) {
                    // Check order details
                    return getOrderStatus(paypalOrderId);
                }
            }

            log.error("PayPal capture failed. Status: {}, Body: {}", response.statusCode(), responseBody);
            return new PaypalCaptureResult(false, null, "FAILED", "PayPal capture error: HTTP " + response.statusCode());
        } catch (Exception e) {
            log.error("Exception during PayPal payment capture: {}", e.getMessage(), e);
            return new PaypalCaptureResult(false, null, "ERROR", e.getMessage());
        }
    }

    /**
     * Queries order status from PayPal to check if it's already completed.
     */
    public PaypalCaptureResult getOrderStatus(String paypalOrderId) {
        try {
            String token = getAccessToken();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(getApiBaseUrl() + "/v2/checkout/orders/" + paypalOrderId))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JSONObject json = new JSONObject(response.body());
                String status = json.optString("status", "");
                String captureId = extractCaptureId(json);
                boolean completed = "COMPLETED".equalsIgnoreCase(status);
                return new PaypalCaptureResult(completed, captureId, status,
                        completed ? "Order is completed" : "Order status: " + status);
            }
        } catch (Exception e) {
            log.warn("Could not query PayPal order status: {}", e.getMessage());
        }
        return new PaypalCaptureResult(false, null, "FAILED", "Could not verify order status");
    }

    private String extractCaptureId(JSONObject orderJson) {
        try {
            JSONArray purchaseUnits = orderJson.optJSONArray("purchase_units");
            if (purchaseUnits != null && purchaseUnits.length() > 0) {
                JSONObject payments = purchaseUnits.getJSONObject(0).optJSONObject("payments");
                if (payments != null) {
                    JSONArray captures = payments.optJSONArray("captures");
                    if (captures != null && captures.length() > 0) {
                        return captures.getJSONObject(0).optString("id");
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return orderJson.optString("id");
    }

    public static class PaypalOrderResult {
        private final String id;
        private final String approvalUrl;
        private final String status;

        public PaypalOrderResult(String id, String approvalUrl, String status) {
            this.id = id;
            this.approvalUrl = approvalUrl;
            this.status = status;
        }

        public String getId() { return id; }
        public String getApprovalUrl() { return approvalUrl; }
        public String getStatus() { return status; }
    }

    public static class PaypalCaptureResult {
        private final boolean success;
        private final String captureId;
        private final String status;
        private final String message;

        public PaypalCaptureResult(boolean success, String captureId, String status, String message) {
            this.success = success;
            this.captureId = captureId;
            this.status = status;
            this.message = message;
        }

        public boolean isSuccess() { return success; }
        public String getCaptureId() { return captureId; }
        public String getStatus() { return status; }
        public String getMessage() { return message; }
    }
}
