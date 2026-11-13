import React, { useEffect, useState, useRef } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import * as orderService from '../services/orderService';
import { useAuth } from '../context/AuthContext';
import { loadPayPalScript } from '../utils/paypal';

export default function Checkout() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const { user } = useAuth();
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(true);
  const [paidSuccess, setPaidSuccess] = useState(false);
  const paypalContainerRef = useRef(null);

  // PayPal redirect or URL params
  const token = searchParams.get('token');
  const paypalOrderId = searchParams.get('paypal_order_id') || token || searchParams.get('order_id');
  const clientId = searchParams.get('key') || searchParams.get('clientId') || 'BAA8LQjHKmMFXh4z0PSmTuOfzE1PC124argQshcAWT_7MbUKEwoC4jcqrv72IFcGAQ6xMwlmN5CEETRlaQ';
  const rawAmount = searchParams.get('amount');
  const amount = rawAmount ? (Number(rawAmount) > 10000 ? (Number(rawAmount) / 100).toFixed(2) : Number(rawAmount).toFixed(2)) : null;
  const currency = searchParams.get('currency') || 'USD';
  const isReturnFromPayPal = Boolean(token || searchParams.get('status') === 'success');

  useEffect(() => {
    // If the user was redirected back from PayPal with a token / orderID:
    if (isReturnFromPayPal && paypalOrderId) {
      setLoading(true);
      orderService.verifyPayment({ paypalOrderId })
        .then(() => {
          setPaidSuccess(true);
          if (user) {
            setTimeout(() => navigate('/orders'), 2500);
          }
        })
        .catch((err) => {
          setError(err.response?.data?.message || err.message);
        })
        .finally(() => {
          setLoading(false);
        });
      return;
    }

    if (!paypalOrderId) {
      setError('Invalid payment parameters. PayPal order ID is missing.');
      setLoading(false);
      return;
    }

    setLoading(true);
    let isMounted = true;

    loadPayPalScript({ clientId, currency })
      .then((paypal) => {
        if (!isMounted || !paypalContainerRef.current) return;
        setLoading(false);
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
            return paypalOrderId;
          },
          onApprove: async (data) => {
            setLoading(true);
            try {
              await orderService.verifyPayment({
                paypalOrderId: data.orderID,
                paypalPaymentId: data.paymentID
              });
              setPaidSuccess(true);
              if (user) {
                setTimeout(() => navigate('/orders'), 2000);
              }
            } catch (err) {
              setError(err.response?.data?.message || err.message);
            } finally {
              setLoading(false);
            }
          },
          onError: (err) => {
            setError(err.message || 'Payment failed');
            setLoading(false);
          },
          onCancel: () => {
            setLoading(false);
            setError('Payment cancelled by user.');
          }
        }).render(paypalContainerRef.current);
      })
      .catch((err) => {
        setLoading(false);
        setError('Could not load PayPal checkout: ' + err.message);
      });

    return () => {
      isMounted = false;
    };
  }, [paypalOrderId, clientId, currency, isReturnFromPayPal, user, navigate]);

  if (paidSuccess) {
    return (
      <div style={{ maxWidth: '500px', margin: '80px auto', textAlign: 'center', padding: '30px', border: '1px solid #111', borderRadius: '12px', background: '#fff' }}>
        <div style={{ fontSize: '48px', marginBottom: '10px' }}>🎉</div>
        <h2 style={{ color: '#2ecc71', margin: '0 0 10px 0' }}>Payment Successful!</h2>
        <p style={{ color: '#555', fontSize: '15px' }}>
          Your PayPal payment has been verified. Thank you for your purchase!
        </p>
        <button
          onClick={() => navigate('/orders')}
          style={{ background: '#111', color: '#fff', border: 'none', padding: '10px 24px', fontSize: '14px', borderRadius: '6px', cursor: 'pointer', marginTop: '15px' }}
        >
          View Your Orders
        </button>
      </div>
    );
  }

  return (
    <div style={{ maxWidth: '500px', margin: '80px auto', textAlign: 'center', padding: '20px' }}>
      <h2>PayPal Checkout</h2>
      {amount && <p style={{ fontSize: '16px', color: '#333', fontWeight: 600 }}>Amount: ${amount}</p>}
      {paypalOrderId && <p style={{ fontSize: '13px', color: '#666' }}>Order Reference: <code>{paypalOrderId}</code></p>}

      {loading && <p style={{ color: '#0070ba' }}>Connecting to PayPal Secure Checkout...</p>}

      {error && (
        <div style={{ background: '#fce8e6', color: '#c5221f', padding: '15px', borderRadius: '6px', border: '1px solid #ea4335', marginTop: '20px' }}>
          {error}
        </div>
      )}

      <div ref={paypalContainerRef} style={{ marginTop: '24px', minHeight: '120px' }} />

      <div style={{ marginTop: '20px' }}>
        <button 
          onClick={() => navigate('/cart')} 
          style={{ background: '#eee', color: '#333', border: 'none', padding: '10px 20px', fontSize: '14px', borderRadius: '6px', cursor: 'pointer', marginRight: '10px' }}
        >
          Back to Cart
        </button>
        <button 
          onClick={() => window.location.reload()} 
          style={{ background: '#0070ba', color: 'white', border: 'none', padding: '10px 20px', fontSize: '14px', borderRadius: '6px', cursor: 'pointer' }}
        >
          Retry
        </button>
      </div>
    </div>
  );
}
