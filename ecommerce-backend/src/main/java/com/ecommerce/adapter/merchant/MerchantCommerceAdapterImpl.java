package com.ecommerce.adapter.merchant;

import com.ecommerce.adapter.merchant.dto.InventoryCheckResult;
import com.ecommerce.adapter.merchant.dto.PaymentVerificationResult;
import com.ecommerce.adapter.merchant.dto.PriceInfo;
import com.ecommerce.dto.CartItemRequest;
import com.ecommerce.dto.CartItemResponse;
import com.ecommerce.dto.CartResponse;
import com.ecommerce.dto.ProductResponse;
import com.ecommerce.exception.PaymentException;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.model.Product;
import com.ecommerce.order.CheckoutResponse;
import com.ecommerce.order.OrderResponse;
import com.ecommerce.order.OrderService;
import com.ecommerce.order.PaypalService;
import com.ecommerce.order.RazorpayService;
import com.ecommerce.order.VerifyPaymentRequest;
import com.ecommerce.service.CartService;
import com.ecommerce.service.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Default MerchantCommerceAdapter implementation for this merchant.
 *
 * Every operation delegates to the existing ProductService, CartService,
 * OrderService, and PaypalService - the same services the existing
 * controllers use. No repository/database access happens here, and no
 * product/cart/order/payment business rule is reimplemented here.
 */
@Service
public class MerchantCommerceAdapterImpl implements MerchantCommerceAdapter {

    private final ProductService productService;
    private final CartService cartService;
    private final OrderService orderService;
    private final PaypalService paypalService;
    private final RazorpayService razorpayService;

    @Autowired
    public MerchantCommerceAdapterImpl(ProductService productService,
                                        CartService cartService,
                                        OrderService orderService,
                                        PaypalService paypalService,
                                        @Autowired(required = false) RazorpayService razorpayService) {
        this.productService = productService;
        this.cartService = cartService;
        this.orderService = orderService;
        this.paypalService = paypalService;
        this.razorpayService = razorpayService;
    }

    @Override
    public List<ProductResponse> searchProducts(String keyword, String baseUrl) {
        List<Product> products = (keyword == null || keyword.isBlank())
                ? productService.getAllProducts()
                : productService.searchProducts(keyword);

        String resolvedBaseUrl = resolveBaseUrl(baseUrl);
        return products.stream()
                .map(product -> ProductResponse.fromEntity(product, resolvedBaseUrl))
                .toList();
    }

    @Override
    public ProductResponse getProduct(Long productId, String baseUrl) {
        Product product = productService.getProductById(productId);
        return ProductResponse.fromEntity(product, resolveBaseUrl(baseUrl));
    }

    @Override
    public InventoryCheckResult checkInventory(Long productId, int requestedQuantity) {
        Product product = productService.getProductById(productId);
        int availableQuantity = product.getStock();
        boolean available = requestedQuantity > 0 && requestedQuantity <= availableQuantity;
        return new InventoryCheckResult(productId, requestedQuantity, availableQuantity, available);
    }

    @Override
    public PriceInfo getPrice(Long productId) {
        Product product = productService.getProductById(productId);
        String currency = paypalService != null ? paypalService.getCurrency() :
                (razorpayService != null ? razorpayService.getCurrency() : "USD");
        return new PriceInfo(productId, product.getPrice(), currency);
    }

    @Override
    public CartResponse addToCart(Long userId, Long productId, int quantity) {
        CartItemRequest request = new CartItemRequest();
        request.setProductId(productId);
        request.setQuantity(quantity);
        return cartService.addToCart(userId, request);
    }

    @Override
    public CartResponse updateCart(Long userId, Long cartItemId, int quantity) {
        if (quantity <= 0) {
            return cartService.removeFromCart(userId, cartItemId);
        }

        CartItemResponse existingItem = findOwnedCartItem(userId, cartItemId);
        int delta = quantity - existingItem.getQuantity();

        CartResponse result = null;
        if (delta > 0) {
            for (int i = 0; i < delta; i++) {
                result = cartService.increaseQuantity(userId, cartItemId);
            }
        } else if (delta < 0) {
            for (int i = 0; i < -delta; i++) {
                result = cartService.decreaseQuantity(userId, cartItemId);
            }
        } else {
            result = cartService.getCart(userId);
        }
        return result;
    }

    @Override
    public CartResponse removeFromCart(Long userId, Long cartItemId) {
        return cartService.removeFromCart(userId, cartItemId);
    }

    @Override
    public CheckoutResponse createCheckout(Long userId) {
        // Tag orders created by the AI Agent adapter as AI_AGENT
        return orderService.checkout(userId, "AI_AGENT");
    }

    @Override
    public List<OrderResponse> getOrders(Long userId, String baseUrl) {
        return orderService.getOrders(userId, resolveBaseUrl(baseUrl));
    }

    @Override
    public OrderResponse getOrder(Long userId, Long orderId, String baseUrl) {
        return orderService.getOrder(userId, orderId, resolveBaseUrl(baseUrl));
    }

    @Override
    public PaymentVerificationResult verifyPayment(Long userId, VerifyPaymentRequest request, String baseUrl) {
        try {
            OrderResponse order = orderService.verifyPayment(userId, request, resolveBaseUrl(baseUrl));
            return PaymentVerificationResult.verified(order);
        } catch (ResourceNotFoundException e) {
            return PaymentVerificationResult.failed("Invalid order/payment reference: " + e.getMessage());
        } catch (PaymentException e) {
            return PaymentVerificationResult.failed(e.getMessage());
        }
    }

    private CartItemResponse findOwnedCartItem(Long userId, Long cartItemId) {
        return cartService.getCart(userId).getItems().stream()
                .filter(item -> item.getCartItemId().equals(cartItemId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Cart item not found with id: " + cartItemId));
    }

    private String resolveBaseUrl(String baseUrl) {
        return baseUrl == null ? "" : baseUrl;
    }
}
