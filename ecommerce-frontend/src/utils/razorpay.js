// Legacy Razorpay compatibility wrapper - now delegates to PayPal
export * from './paypal';

export function openRazorpayCheckout({ checkoutData, user, onSuccess, onFailure, onDismiss }) {
  if (checkoutData?.approvalUrl) {
    window.location.href = checkoutData.approvalUrl;
    return;
  }
  if (typeof window.Razorpay !== 'undefined') {
    const options = {
      key: checkoutData.keyId,
      amount: checkoutData.amount,
      currency: checkoutData.currency,
      name: 'Ecommerce App',
      description: `Order #${checkoutData.orderId}`,
      order_id: checkoutData.razorpayOrderId,
      prefill: {
        name: user?.username || '',
        email: user?.email || ''
      },
      handler: function (response) {
        onSuccess({
          paypalOrderId: response.razorpay_order_id,
          razorpayOrderId: response.razorpay_order_id,
          razorpayPaymentId: response.razorpay_payment_id,
          razorpaySignature: response.razorpay_signature
        });
      },
      modal: {
        ondismiss: function () {
          if (onDismiss) onDismiss();
        }
      }
    };
    const razorpay = new window.Razorpay(options);
    razorpay.open();
  } else if (onFailure) {
    onFailure(new Error('Please use PayPal checkout.'));
  }
}
