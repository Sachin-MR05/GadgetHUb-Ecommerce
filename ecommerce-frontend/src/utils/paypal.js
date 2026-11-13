// Dynamically loads the PayPal JS SDK and renders standard PayPal buttons

let paypalScriptPromise = null;

export function loadPayPalScript({ clientId, currency = 'USD' }) {
  if (window.paypal) {
    return Promise.resolve(window.paypal);
  }

  if (paypalScriptPromise) {
    return paypalScriptPromise;
  }

  paypalScriptPromise = new Promise((resolve, reject) => {
    const scriptId = 'paypal-sdk-script';
    const existingScript = document.getElementById(scriptId);

    if (existingScript) {
      existingScript.onload = () => resolve(window.paypal);
      existingScript.onerror = (err) => reject(new Error('Failed to load PayPal SDK: ' + err.message));
      return;
    }

    const script = document.createElement('script');
    script.id = scriptId;
    script.src = `https://www.paypal.com/sdk/js?client-id=${encodeURIComponent(clientId)}&currency=${encodeURIComponent(currency)}&intent=capture`;
    script.async = true;

    script.onload = () => {
      if (window.paypal) {
        resolve(window.paypal);
      } else {
        reject(new Error('PayPal SDK script loaded but window.paypal is undefined.'));
      }
    };

    script.onerror = () => {
      reject(new Error('Failed to load PayPal SDK script. Check your internet connection or client ID.'));
    };

    document.body.appendChild(script);
  });

  return paypalScriptPromise;
}

/**
 * Renders PayPal buttons into a target container element.
 * 
 * @param {Object} options
 * @param {HTMLElement|string} options.container - DOM element or selector to render buttons in
 * @param {Function} options.createOrder - Callback that returns Promise<string> (PayPal order ID)
 * @param {Function} options.onApprove - Callback called when user approves payment
 * @param {Function} options.onError - Callback called on error
 * @param {Function} [options.onCancel] - Callback called if user cancels
 */
export async function renderPayPalButtons({
  container,
  clientId,
  currency = 'USD',
  createOrder,
  onApprove,
  onError,
  onCancel
}) {
  const paypal = await loadPayPalScript({ clientId, currency });

  const target = typeof container === 'string' ? document.querySelector(container) : container;
  if (!target) {
    throw new Error('PayPal button container not found');
  }

  // Clear previous button render
  target.innerHTML = '';

  const buttons = paypal.Buttons({
    style: {
      layout: 'vertical',
      color: 'gold',
      shape: 'rect',
      label: 'paypal',
      height: 44
    },
    createOrder: async (data, actions) => {
      try {
        const orderId = await createOrder();
        return orderId;
      } catch (err) {
        if (onError) onError(err);
        throw err;
      }
    },
    onApprove: async (data, actions) => {
      try {
        if (onApprove) {
          await onApprove(data);
        }
      } catch (err) {
        if (onError) onError(err);
      }
    },
    onCancel: (data) => {
      if (onCancel) onCancel(data);
    },
    onError: (err) => {
      if (onError) onError(err);
    }
  });

  await buttons.render(target);
  return buttons;
}
