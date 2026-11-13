package com.ecommerce.order;

import com.ecommerce.exception.PaymentException;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.model.CartItem;
import com.ecommerce.model.Order;
import com.ecommerce.model.OrderItem;
import com.ecommerce.model.OrderStatus;
import com.ecommerce.model.Product;
import com.ecommerce.repository.CartRepository;
import com.ecommerce.repository.OrderRepository;
import com.ecommerce.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final CartRepository cartRepository;
    private final ProductRepository productRepository;
    private final PaypalService paypalService;
    private final RazorpayService razorpayService;

    @Autowired
    public OrderService(OrderRepository orderRepository, CartRepository cartRepository,
                        ProductRepository productRepository, PaypalService paypalService,
                        @Autowired(required = false) RazorpayService razorpayService) {
        this.orderRepository = orderRepository;
        this.cartRepository = cartRepository;
        this.productRepository = productRepository;
        this.paypalService = paypalService;
        this.razorpayService = razorpayService;
    }

    /**
     * Turns the user's current cart into an Order (status = CREATED) and asks
     * PayPal for a matching order id and approval link.
     */
    @Transactional
    public CheckoutResponse checkout(Long userId) {
        return checkout(userId, null);
    }

    @Transactional
    public CheckoutResponse checkout(Long userId, String source) {
        List<CartItem> cartItems = cartRepository.findByUserId(userId);
        if (cartItems.isEmpty()) {
            throw new PaymentException("Your cart is empty");
        }

        Order order = new Order();
        order.setUserId(userId);
        order.setStatus(OrderStatus.CREATED);
        order.setOrderSource(source != null ? source : (userId != null && userId.equals(1L) ? "AI_AGENT" : "DIRECT_APP"));

        double total = 0;
        for (CartItem cartItem : cartItems) {
            Product product = productRepository.findById(cartItem.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Product not found with id: " + cartItem.getProductId()));

            if (cartItem.getQuantity() > product.getStock()) {
                throw new PaymentException(
                        "Only " + product.getStock() + " unit(s) of '" + product.getName() + "' left in stock");
            }

            double lineTotal = product.getPrice() * cartItem.getQuantity();
            total += lineTotal;

            order.addItem(new OrderItem(
                    product.getId(),
                    product.getName(),
                    product.getImage(),
                    product.getPrice(),
                    cartItem.getQuantity()
            ));
        }

        order.setTotalAmount(total);
        Order saved = orderRepository.save(order);

        // Create order on PayPal
        PaypalService.PaypalOrderResult paypalOrder = paypalService.createOrder(total, "order_rcpt_" + saved.getId());
        String paypalOrderId = paypalOrder.getId();

        saved.setPaypalOrderId(paypalOrderId);
        saved.setRazorpayOrderId(paypalOrderId); // maintain compatibility
        orderRepository.save(saved);
        cartRepository.deleteByUserId(userId);

        long amountInSmallestUnit = Math.round(total * 100);
        return new CheckoutResponse(
                saved.getId(),
                paypalOrderId,
                amountInSmallestUnit,
                total,
                paypalService.getCurrency(),
                paypalService.getClientId(),
                paypalOrder.getApprovalUrl()
        );
    }

    /**
     * Verifies and captures the PayPal payment, and if successful: marks the order PAID,
     * decrements stock, and empties the cart.
     */
    @Transactional
    public OrderResponse verifyPayment(Long userId, VerifyPaymentRequest request, String baseUrl) {
        String orderRef = request.getEffectiveOrderId();
        if (orderRef == null || orderRef.isBlank()) {
            throw new PaymentException("Order reference (paypalOrderId) is required for payment verification");
        }

        Order order = orderRepository.findByPaypalOrderId(orderRef)
                .or(() -> orderRepository.findByRazorpayOrderId(orderRef))
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with reference: " + orderRef));

        // For agentic commerce or guest checkout, the order reference (PayPal/Razorpay ID)
        // is unique and validated directly against the payment provider. We do not reject
        // verification if the browser session user differs from the order creator.

        if (order.getStatus() == OrderStatus.PAID) {
            return OrderResponse.fromEntity(order, baseUrl);
        }

        boolean valid = false;
        String captureId = request.getEffectivePaymentId();
        String failureReason = "Payment verification failed - please try again";

        // Check if Razorpay signature verification was requested
        if (request.getRazorpaySignature() != null && !request.getRazorpaySignature().isBlank() && razorpayService != null) {
            valid = razorpayService.verifySignature(orderRef, request.getRazorpayPaymentId(), request.getRazorpaySignature());
            captureId = request.getRazorpayPaymentId();
        } else {
            // PayPal capture verification
            PaypalService.PaypalCaptureResult captureResult = paypalService.capturePayment(orderRef);
            valid = captureResult.isSuccess();
            if (captureResult.getCaptureId() != null) {
                captureId = captureResult.getCaptureId();
            }
            if (!valid && captureResult.getMessage() != null) {
                failureReason = captureResult.getMessage();
            }
        }

        if (!valid) {
            order.setStatus(OrderStatus.FAILED);
            orderRepository.save(order);
            throw new PaymentException(failureReason);
        }

        order.setStatus(OrderStatus.PAID);
        order.setPaypalPaymentId(captureId);
        order.setRazorpayPaymentId(captureId);
        if (request.getRazorpaySignature() != null) {
            order.setRazorpaySignature(request.getRazorpaySignature());
        }

        for (OrderItem item : order.getItems()) {
            productRepository.findById(item.getProductId()).ifPresent(product -> {
                int remaining = Math.max(0, product.getStock() - item.getQuantity());
                product.setStock(remaining);
                productRepository.save(product);
            });
        }

        Order saved = orderRepository.save(order);
        cartRepository.deleteByUserId(userId);

        return OrderResponse.fromEntity(saved, baseUrl);
    }

    public List<OrderResponse> getOrders(Long userId, String baseUrl) {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(order -> OrderResponse.fromEntity(order, baseUrl))
                .toList();
    }

    public List<OrderResponse> getAllOrders(String baseUrl) {
        return orderRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .map(order -> OrderResponse.fromEntity(order, baseUrl))
                .toList();
    }

    public OrderResponse getOrder(Long userId, Long orderId, String baseUrl) {
        Order order = orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));
        return OrderResponse.fromEntity(order, baseUrl);
    }
}
