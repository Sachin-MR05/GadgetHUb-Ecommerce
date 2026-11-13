package com.ecommerce.order;

import com.ecommerce.model.Role;
import com.ecommerce.security.CurrentUser;
import com.ecommerce.security.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    // POST /orders/checkout  -> creates an order from the current cart (Direct Web App)
    @PostMapping("/checkout")
    public ResponseEntity<CheckoutResponse> checkout(@CurrentUser UserPrincipal user) {
        return ResponseEntity.ok(orderService.checkout(user.getId(), "DIRECT_APP"));
    }

    // POST /orders/verify-payment  -> confirms payment success after PayPal/Razorpay closes
    @PostMapping("/verify-payment")
    public ResponseEntity<OrderResponse> verifyPayment(@CurrentUser UserPrincipal user,
                                                         @Valid @RequestBody VerifyPaymentRequest request,
                                                         HttpServletRequest httpRequest) {
        Long userId = (user != null) ? user.getId() : 1L;
        return ResponseEntity.ok(orderService.verifyPayment(userId, request, getBaseUrl(httpRequest)));
    }

    // GET /orders  -> the logged-in user's order history, or ALL orders if ADMIN
    @GetMapping
    public ResponseEntity<List<OrderResponse>> getOrders(@CurrentUser UserPrincipal user, HttpServletRequest request) {
        if (user != null && user.getRole() == Role.ADMIN) {
            return ResponseEntity.ok(orderService.getAllOrders(getBaseUrl(request)));
        }
        return ResponseEntity.ok(orderService.getOrders(user.getId(), getBaseUrl(request)));
    }

    // GET /orders/all -> explicit admin endpoint to retrieve all orders across users
    @GetMapping("/all")
    public ResponseEntity<List<OrderResponse>> getAllOrders(HttpServletRequest request) {
        return ResponseEntity.ok(orderService.getAllOrders(getBaseUrl(request)));
    }

    // GET /orders/{id}  -> a single order's detail
    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getOrder(@CurrentUser UserPrincipal user, @PathVariable Long id,
                                                    HttpServletRequest request) {
        return ResponseEntity.ok(orderService.getOrder(user.getId(), id, getBaseUrl(request)));
    }

    private String getBaseUrl(HttpServletRequest request) {
        return request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort();
    }
}
