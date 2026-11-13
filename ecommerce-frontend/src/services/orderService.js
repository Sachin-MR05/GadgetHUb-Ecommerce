import apiClient from './apiClient';

// POST /orders/checkout -> { orderId, paypalOrderId, amount, totalAmount, currency, clientId, approvalUrl }
export const checkout = () => {
  return apiClient.post('/orders/checkout').then((res) => res.data);
};

// POST /orders/verify-payment  { paypalOrderId, paypalPaymentId } or legacy { razorpayOrderId, ... }
export const verifyPayment = (payload) => {
  return apiClient.post('/orders/verify-payment', payload).then((res) => res.data);
};

// GET /orders
export const getOrders = () => {
  return apiClient.get('/orders').then((res) => res.data);
};

// GET /orders/{id}
export const getOrder = (id) => {
  return apiClient.get(`/orders/${id}`).then((res) => res.data);
};
