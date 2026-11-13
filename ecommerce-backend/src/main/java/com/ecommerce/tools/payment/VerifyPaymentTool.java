package com.ecommerce.tools.payment;

import com.ecommerce.adapter.merchant.MerchantCommerceAdapter;
import com.ecommerce.adapter.merchant.dto.PaymentVerificationResult;
import com.ecommerce.order.VerifyPaymentRequest;
import com.ecommerce.tools.AbstractAgentTool;
import com.ecommerce.tools.model.ToolRequest;
import org.springframework.stereotype.Component;

/**
 * Server-side verification of a Razorpay payment. Delegates to
 * MerchantCommerceAdapter.verifyPayment(), which reuses the existing
 * OrderService/RazorpayService signature verification - this tool never
 * trusts a client-reported "payment successful" state and never marks
 * anything paid itself.
 *
 * Note this always returns success at the ToolResponse level (the tool
 * executed correctly); the business outcome - whether the payment actually
 * verified - is carried in the "verified" field of the returned data, so
 * the agent can branch on it without treating a failed verification as a
 * broken tool call.
 */
@Component
public class VerifyPaymentTool extends AbstractAgentTool {

    private final MerchantCommerceAdapter merchantCommerceAdapter;

    public VerifyPaymentTool(MerchantCommerceAdapter merchantCommerceAdapter) {
        this.merchantCommerceAdapter = merchantCommerceAdapter;
    }

    @Override
    public String getName() {
        return "verify_payment";
    }

    @Override
    public String getDescription() {
        return "Verify a payment server-side using the PayPal or Razorpay order id and payment reference. " +
                "Only marks the order PAID when verification actually succeeds - check the 'verified' field " +
                "of the result rather than assuming success from a prior client-side step.";
    }

    @Override
    public String getInputSchema() {
        return """
                {
                  "type": "object",
                  "properties": {
                    "paypalOrderId": { "type": "string", "description": "The PayPal order id from checkout." },
                    "paypalPaymentId": { "type": "string", "description": "Optional PayPal payment/capture id." },
                    "razorpayOrderId": { "type": "string", "description": "Legacy Razorpay order id from checkout." },
                    "razorpayPaymentId": { "type": "string", "description": "Legacy Razorpay payment id returned after payment." },
                    "razorpaySignature": { "type": "string", "description": "Legacy Razorpay signature returned after payment." }
                  }
                }
                """;
    }

    @Override
    public String getOutputSchema() {
        return """
                {
                  "type": "object",
                  "properties": {
                    "verified": { "type": "boolean" },
                    "message": { "type": "string" },
                    "order": {
                      "type": "object",
                      "description": "Present only when verified is true.",
                      "properties": {
                        "id": { "type": "integer" },
                        "status": { "type": "string", "enum": ["CREATED", "PAID", "FAILED"] },
                        "totalAmount": { "type": "number" }
                      }
                    }
                  }
                }
                """;
    }

    @Override
    protected Object handle(ToolRequest request) {
        Long userId = requireUserId(request);

        VerifyPaymentRequest verifyPaymentRequest = new VerifyPaymentRequest();
        String paypalOrderId = request.getOptionalString("paypalOrderId");
        String razorpayOrderId = request.getOptionalString("razorpayOrderId");

        if (paypalOrderId != null && !paypalOrderId.isBlank()) {
            verifyPaymentRequest.setPaypalOrderId(paypalOrderId);
            verifyPaymentRequest.setPaypalPaymentId(request.getOptionalString("paypalPaymentId"));
        } else if (razorpayOrderId != null && !razorpayOrderId.isBlank()) {
            verifyPaymentRequest.setRazorpayOrderId(razorpayOrderId);
            verifyPaymentRequest.setRazorpayPaymentId(request.getOptionalString("razorpayPaymentId"));
            verifyPaymentRequest.setRazorpaySignature(request.getOptionalString("razorpaySignature"));
        } else {
            throw new IllegalArgumentException("Either paypalOrderId or razorpayOrderId must be provided");
        }

        PaymentVerificationResult result = merchantCommerceAdapter.verifyPayment(userId, verifyPaymentRequest, null);
        return result;
    }
}
