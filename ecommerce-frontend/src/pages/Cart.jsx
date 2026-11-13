import React, { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { useCart } from '../context/CartContext';
import { useAuth } from '../context/AuthContext';
import CartItem from '../components/CartItem';
import * as orderService from '../services/orderService';
import { loadPayPalScript } from '../utils/paypal';

export default function Cart() {
  const { cart, loading, error, clear, refreshCart } = useCart();
  const { user } = useAuth();
  const navigate = useNavigate();

  const [paying, setPaying] = useState(false);
  const [paymentError, setPaymentError] = useState(null);
  const [showPayPalButtons, setShowPayPalButtons] = useState(false);
  const [checkoutInfo, setCheckoutInfo] = useState(null);
  const paypalContainerRef = useRef(null);

  const handleStartCheckout = async () => {
    setPaymentError(null);
    setPaying(true);
    try {
      const checkoutData = await orderService.checkout();
      setCheckoutInfo(checkoutData);
      setShowPayPalButtons(true);
    } catch (err) {
      setPaymentError(err.response?.data?.message || err.message);
    } finally {
      setPaying(false);
    }
  };

  useEffect(() => {
    if (!showPayPalButtons || !checkoutInfo || !paypalContainerRef.current) {
      return;
    }

    let isMounted = true;
    const clientId = checkoutInfo.clientId || 'BAA8LQjHKmMFXh4z0PSmTuOfzE1PC124argQshcAWT_7MbUKEwoC4jcqrv72IFcGAQ6xMwlmN5CEETRlaQ';
    const currency = checkoutInfo.currency || 'USD';

    loadPayPalScript({ clientId, currency })
      .then((paypal) => {
        if (!isMounted || !paypalContainerRef.current) return;
        paypalContainerRef.current.innerHTML = '';

        paypal.Buttons({
          style: {
            layout: 'vertical',
            color: 'gold',
            shape: 'rect',
            label: 'paypal',
            height: 44
          },
          createOrder: () => {
            return checkoutInfo.paypalOrderId;
          },
          onApprove: async (data) => {
            setPaying(true);
            try {
              await orderService.verifyPayment({
                paypalOrderId: data.orderID,
                paypalPaymentId: data.paymentID
              });
              await refreshCart();
              navigate('/orders');
            } catch (err) {
              setPaymentError(err.response?.data?.message || err.message);
            } finally {
              setPaying(false);
            }
          },
          onError: (err) => {
            setPaymentError(err.message || 'PayPal Checkout failed');
          },
          onCancel: () => {
            setPaying(false);
          }
        }).render(paypalContainerRef.current);
      })
      .catch((err) => {
        console.warn('Could not load PayPal Smart Buttons, falling back to approval URL:', err);
      });

    return () => {
      isMounted = false;
    };
  }, [showPayPalButtons, checkoutInfo, navigate, refreshCart]);

  if (loading) return <p>Loading cart...</p>;
  if (error) return <p>Error: {error}</p>;

  return (
    <div className="page">
      <div className="page-header">
        <h1>Your Cart</h1>
      </div>

      {cart.items.length === 0 && <p className="empty-state">Cart is empty.</p>}

      <div className="stack">
        {cart.items.map((item) => (
          <CartItem key={item.cartItemId} item={item} />
        ))}
      </div>

      {cart.items.length > 0 && (
        <div className="card summary-card">
          <h3>Total Items: {cart.totalItems}</h3>
          <h3>Total: ${cart.total}</h3>

          {paymentError && (
            <div style={{ background: '#fce8e6', color: '#c5221f', padding: '10px 14px', borderRadius: '6px', margin: '12px 0' }}>
              Error: {paymentError}
            </div>
          )}

          {!showPayPalButtons ? (
            <div className="card-actions">
              <button 
                onClick={handleStartCheckout} 
                disabled={paying}
                style={{ background: '#0070ba', color: '#fff', fontWeight: 600 }}
              >
                {paying ? 'Preparing Checkout...' : 'Checkout with PayPal'}
              </button>
              <button onClick={clear} disabled={paying}>
                Clear Cart
              </button>
            </div>
          ) : (
            <div style={{ marginTop: '16px' }}>
              <p style={{ fontSize: '14px', color: '#444', marginBottom: '12px', textAlign: 'center' }}>
                Complete payment with PayPal or Credit/Debit Card:
              </p>
              <div ref={paypalContainerRef} style={{ minHeight: '100px' }} />
              {checkoutInfo?.approvalUrl && (
                <div style={{ marginTop: '10px', textAlign: 'center' }}>
                  <a
                    href={checkoutInfo.approvalUrl}
                    target="_blank"
                    rel="noopener noreferrer"
                    style={{ fontSize: '13px', color: '#0070ba', textDecoration: 'underline' }}
                  >
                    Having trouble? Click here to open PayPal in a new tab
                  </a>
                </div>
              )}
              <div style={{ marginTop: '14px', textAlign: 'center' }}>
                <button
                  onClick={() => setShowPayPalButtons(false)}
                  style={{ background: 'transparent', border: 'none', color: '#666', cursor: 'pointer', fontSize: '13px' }}
                >
                  Cancel & Back to Cart
                </button>
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
